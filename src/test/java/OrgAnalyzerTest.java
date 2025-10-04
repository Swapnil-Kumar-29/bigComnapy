

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

import org.junit.jupiter.api.*;
import orgReport.Employee;
import orgReport.OrgAnalyzer;
import orgReport.OrgReport;

class OrgAnalyzerTest {

    private OrgAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new OrgAnalyzer();
    }

    @Test
    void testReadData_validAndInvalidLines() throws Exception {
        String data = ""
                + "# comment\n"
                + "\n"
                + "1,John,Smith,100000,\n"
                + "2,Jane,Doe,80000,1\n"
                + "3,Bob,Lee,notNum,1\n"
                + "4,Alice,Wong,90000,1,extra\n"
                + "5,Tom,Hanks,60000, \n";

        BufferedReader br = new BufferedReader(new StringReader(data));
        analyzer.readData(br);

        Map<Long, Employee> map = getEmployeeMap(analyzer);
        // Should parse lines: id=1, id=2, id=5 (5 has managerStr empty -> Optional.empty)
        assertTrue(map.containsKey(1L));
        assertTrue(map.containsKey(2L));
        assertTrue(map.containsKey(5L));
        assertEquals(3, map.size());

        Employee e1 = map.get(1L);
        assertEquals("John", e1.getFirstName());
        assertEquals("Smith", e1.getLastName());
        assertEquals(100000.0, e1.getSalary());
        assertFalse(e1.getManagerId().isPresent());

        Employee e2 = map.get(2L);
        assertTrue(e2.getManagerId().isPresent());
        assertEquals(1L, e2.getManagerId().get());

        Employee e5 = map.get(5L);
        assertFalse(e5.getManagerId().isPresent());
    }

    @Test
    void testReadEmployeesFromFile_fallbackToFilesystem() throws Exception {
        // Create a temporary employees.csv in working dir
        Path temp = Files.createTempFile("employees", ".csv");
        String content = "1,John,Smith,50000,\n";
        Files.write(temp, content.getBytes());
        Path dest = Paths.get("").toAbsolutePath().resolve("employees.csv");
        Files.copy(temp, dest, StandardCopyOption.REPLACE_EXISTING);

        analyzer.readEmployeesFromFile();

        Map<Long, Employee> map = getEmployeeMap(analyzer);
        assertTrue(map.containsKey(1L));

        // cleanup
        Files.deleteIfExists(dest);
        Files.deleteIfExists(temp);
    }

    @Test
    void testBuildHierarchy_andCEODetection_andMissingManager() {
        // Create employees
        Employee e1 = new Employee(1, "John", "Smith", 100000.0, Optional.empty());
        Employee e2 = new Employee(2, "Jane", "Doe", 80000.0, Optional.of(1L));
        Employee e3 = new Employee(3, "Bob", "Lee", 60000.0, Optional.of(99L));  // manager missing

        putEmployee(analyzer, e1);
        putEmployee(analyzer, e2);
        putEmployee(analyzer, e3);

        analyzer.buildHierarchy();

        Employee ceo = getCEO(analyzer);
        assertNotNull(ceo);
        assertEquals(1L, ceo.getId());

        // e2 should be subordinate of e1
        assertTrue(e1.getSubordinates().contains(e2));

        // e3 has manager = 99L, which is not in map => should not be subordinate anywhere
        assertTrue(e3.getSubordinates().isEmpty());  // or at least not in any manager’s list
    }

    @Test
    void testCheckSalaryCompliance_variousCases() {
        OrgReport report = new OrgReport();

        // Case: manager with no subordinates => nothing added
        Employee m0 = new Employee(10, "No", "Mgr", 100000.0, Optional.empty());
        analyzer.checkSalaryCompliance(m0, report);
        assertTrue(report.lowEarningManagers.isEmpty());
        assertTrue(report.highEarningManagers.isEmpty());

        // Case: manager with subordinates, salary too high
        Employee m1 = new Employee(20, "Mgr", "High", 120000.0, Optional.empty());
        Employee s1 = new Employee(21, "Sub1", "A", 40000.0, Optional.of(20L));
        Employee s2 = new Employee(22, "Sub2", "B", 60000.0, Optional.of(20L));
        m1.addSubordinates(s1);
        m1.addSubordinates(s2);

        // average = 50,000 => min=60,000, max=75,000 -> manager =120,000 > 75,000
        analyzer.checkSalaryCompliance(m1, report);
        assertEquals(1, report.highEarningManagers.size());
        OrgAnalyzer.ManagerSalaryIssue issue = report.highEarningManagers.get(0);
        assertEquals(20L, issue.managerId);
        assertTrue(issue.difference > 0);

        // Case: manager salary OK (in between)
        OrgReport report2 = new OrgReport();
        Employee m2 = new Employee(30, "Mgr2", "Ok", 60000.0, Optional.empty());
        Employee s3 = new Employee(31, "Sub3", "C", 40000.0, Optional.of(30L));
        m2.addSubordinates(s3);
        // avg 40,000 => min 48,000, max 60,000 => manager = 60,000 => at upper bound, should not trigger
        analyzer.checkSalaryCompliance(m2, report2);
        assertTrue(report2.lowEarningManagers.isEmpty());
        assertTrue(report2.highEarningManagers.isEmpty());

        // Case: manager too low
        OrgReport report3 = new OrgReport();
        Employee m3 = new Employee(40, "Mgr3", "Low", 45000.0, Optional.empty());
        Employee s4 = new Employee(41, "Sub4", "D", 40000.0, Optional.of(40L));
        m3.addSubordinates(s4);
        // avg = 40,000 => min = 48,000 => manager 45,000 < 48,000
        analyzer.checkSalaryCompliance(m3, report3);
        assertEquals(1, report3.lowEarningManagers.size());
        OrgAnalyzer.ManagerSalaryIssue low = report3.lowEarningManagers.get(0);
        assertEquals(40L, low.managerId);
        assertTrue(low.difference > 0);
    }

    @Test
    void testCheckReportingLineLength_normalBrokenTooLong() {
        OrgReport report = new OrgReport();

        // Setup chain: 1 (CEO) → 2 → 3 → 4 → 5 → 6 → 7
        Employee e1 = new Employee(1, "CEO", "X", 200000.0, Optional.empty());
        Employee e2 = new Employee(2, "M1", "A", 120000.0, Optional.of(1L));
        Employee e3 = new Employee(3, "M2", "B", 80000.0, Optional.of(2L));
        Employee e4 = new Employee(4, "M3", "C", 60000.0, Optional.of(3L));
        Employee e5 = new Employee(5, "M4", "D", 50000.0, Optional.of(4L));
        Employee e6 = new Employee(6, "Emp1", "E", 40000.0, Optional.of(5L));
        Employee e7 = new Employee(7, "Emp2", "F", 30000.0, Optional.of(6L));

        putEmployee(analyzer, e1);
        putEmployee(analyzer, e2);
        putEmployee(analyzer, e3);
        putEmployee(analyzer, e4);
        putEmployee(analyzer, e5);
        putEmployee(analyzer, e6);
        putEmployee(analyzer, e7);

        analyzer.buildHierarchy();

        // Test broken chain: set someone with manager not in map
        Employee broken = new Employee(8, "Broken", "G", 25000.0, Optional.of(99L));
        putEmployee(analyzer, broken);
        analyzer.checkReportingLineLength(broken, report);
        // Should not throw; no issue added
        assertTrue(report.longReportingLines.isEmpty());

        // Test exactly allowed chain length (MAX = 5): e6 is at level 5 managers above? Let's count:
        // e6 → 5 → 4 → 3 → 2 → 1 => that's 5 steps, so should NOT be flagged.
        analyzer.checkReportingLineLength(e6, report);
        assertTrue(report.longReportingLines.isEmpty());

        // Test too long: e7 has 6 → should flag
        analyzer.checkReportingLineLength(e7, report);
        assertEquals(1, report.longReportingLines.size());
        OrgAnalyzer.ReportingLineIssue issue = report.longReportingLines.get(0);
        assertEquals(7L, issue.employeeId);
        assertEquals(issue.excessiveManagers, issue.actualLength - OrgAnalyzer.MAX_REPORTING_LINE_LENGTH);
    }

    @Test
    void testAnalyzeStructure_combined() {
        // Build a small org
        Employee ceo = new Employee(1, "CEO", "X", 300000.0, Optional.empty());
        Employee mgr = new Employee(2, "Mgr", "Y", 120000.0, Optional.of(1L));
        Employee sub1 = new Employee(3, "Sub1", "A", 40000.0, Optional.of(2L));
        Employee sub2 = new Employee(4, "Sub2", "B", 50000.0, Optional.of(2L));

        putEmployee(analyzer, ceo);
        putEmployee(analyzer, mgr);
        putEmployee(analyzer, sub1);
        putEmployee(analyzer, sub2);

        analyzer.buildHierarchy();
        OrgReport report = analyzer.analyzeStructure();

        assertEquals(2, report.highEarningManagers.size());
        assertTrue(report.lowEarningManagers.isEmpty());
        assertTrue(report.longReportingLines.isEmpty());

        // Now extend to exceed reporting chain
        Employee e5 = new Employee(5, "E5", "C", 30000.0, Optional.of(4L));
        Employee e6 = new Employee(6, "E6", "D", 20000.0, Optional.of(5L));
        putEmployee(analyzer, e5);
        putEmployee(analyzer, e6);

        // Reset / rebuild
        OrgAnalyzer analyzer2 = new OrgAnalyzer();
        putEmployee(analyzer2, ceo);
        putEmployee(analyzer2, mgr);
        putEmployee(analyzer2, sub1);
        putEmployee(analyzer2, sub2);
        putEmployee(analyzer2, e5);
        putEmployee(analyzer2, e6);

        analyzer2.buildHierarchy();
        OrgReport report2 = analyzer2.analyzeStructure();
        assertEquals(3, report2.highEarningManagers.size());
        assertEquals(0, report2.longReportingLines.size());
    }

    @Test
    void testMain_noThrow() {
        assertDoesNotThrow(() -> OrgAnalyzer.main(new String[0]));
    }

    // --- Helpers using reflection ---

    @SuppressWarnings("unchecked")
    private Map<Long, Employee> getEmployeeMap(OrgAnalyzer a) {
        try {
            Field f = OrgAnalyzer.class.getDeclaredField("employeeMap");
            f.setAccessible(true);
            return (Map<Long, Employee>) f.get(a);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void putEmployee(OrgAnalyzer a, Employee e) {
        getEmployeeMap(a).put(e.getId(), e);
    }

    private Employee getCEO(OrgAnalyzer a) {
        try {
            Field f = OrgAnalyzer.class.getDeclaredField("ceo");
            f.setAccessible(true);
            return (Employee) f.get(a);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
