package orgReport;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class OrgAnalyzer {
    public static final String FILE_PATH = "employees.csv"; //
    // Max chain length: Employee -> M1 -> M2 -> M3 -> M4 -> CEO (5 hops)
    public static final int MAX_REPORTING_LINE_LENGTH = 5;
    public static final double MIN_SALARY_FACTOR = 1.20; // Must earn at least 20% more
    public static final double MAX_SALARY_FACTOR = 1.50; // Must earn no more than 50% more


    public static class ManagerSalaryIssue {
        public String managerName;
        public long managerId;
        public double expectedMin;
        public double expectedMax;
        public double difference; // difference from the required bound

        @Override
        public String toString() {
            return String.format("%s (ID: %d)", managerName, managerId);
        }
    }

    /**
     * Details an employee whose reporting line is too long.
     */
    public static class ReportingLineIssue {
        public String employeeName;
        public long employeeId;
        public int actualLength;
        public int excessiveManagers;

        @Override
        public String toString() {
            return String.format("%s (ID: %d)", employeeName, employeeId);
        }
    }

    // --- Internal State ---
    public final Map<Long, Employee> employeeMap = new HashMap<>();
    public Employee ceo = null;

    /**
     * Reads the CSV file and populates the employee map.
     */
    public void readEmployeesFromFile() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(FILE_PATH);
        boolean classpathSuccess = (is != null); // --- ATTEMPT 1: CLASS PATH LOADING ---
        if (classpathSuccess) {
            System.out.println("Reading file from Classpath (src/main/resources)...");
            readData(new BufferedReader(new InputStreamReader(is)));
            return;
        } // --- ATTEMPT 2: FILE SYSTEM FALLBACK ---
        try {
            System.out.println("Classpath failed. Falling back to direct File System reading (e.g., project root)..."); // Tries to read the file directly from the current working directory
            readData(new BufferedReader(new FileReader(FILE_PATH)));
        } catch (
                IOException e) {
            // If both fail, throw a comprehensive error message
            throw new IOException("--- FATAL ERROR: File Not Found ---\n" + "Could not read file: " + FILE_PATH + "\n" +
                    "Ensure the file is either:\n" + "1. In the classpath (src/main/resources) AND correctly marked as a Resource Root.\n" + "2. In the project root directory where the application is being run from.");
        }
    }

    /**
     * Helper method to process the data from a given BufferedReader.
     */
    public void readData(BufferedReader br) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            // Ensure lines are not empty or comments
            if (line.trim().isEmpty() || line.trim().startsWith("#"))
                continue;
            // Split line, respecting the structure: id, firstName, lastName, salary, managerId
            String[] values = line.split(",", -1); // We expect at least 4 fields (id, name, name, salary) and at most 5 (including managerId)
            if (values.length < 4 || values.length > 5) {
                System.err.println("Warning: Skipping malformed line with incorrect number of fields: " + line);
                continue;
            }
            try {
                long id = Long.parseLong(values[0].trim());
                String firstName = values[1].trim();
                String lastName = values[2].trim();
                double salary = Long.parseLong(values[3].trim());
                String managerIdStr = values.length == 5 ? values[4].trim() : "";
                Optional<Long> managerId = managerIdStr.isEmpty()
                        ? Optional.empty()
                        : Optional.of(Long.parseLong(managerIdStr));

                Employee employee = new Employee(id, firstName, lastName, salary, managerId);
                employeeMap.put(id, employee);

            } catch (NumberFormatException e) {
                System.err.println("Warning: Skipping line due to invalid number format: " + line);
            }
        }
    }

    /**
     * Builds the organizational hierarchy by linking managers to their subordinates. * Also identifies the CEO (employee with no manager).
     */
    public void buildHierarchy() {
        for (Employee employee : employeeMap.values()) {
            employee.getManagerId().ifPresent(managerId -> {
                Employee manager = employeeMap.get(managerId);
                if (manager != null) {
                    manager.addSubordinates(employee);
                } else {
                    System.err.println("Warning: Manager ID " + managerId + " for employee " + employee.getId() + " not found in the dataset. Employee excluded from manager analysis.");
                }
            }); // Identify CEO: employee without a managerId
            if (!employee.getManagerId().isPresent()) {
                if (ceo != null) {
                    System.err.println("Warning: Multiple potential CEOs found! Using the first one encountered: " + ceo.toString());
                } else {
                    ceo = employee;
                }
            }
        }
        if (ceo == null && !employeeMap.isEmpty()) {
            System.err.println("Error: No CEO found (no employee has a null/empty managerId). Reporting line analysis will be skipped.");
        }
    }

    /**
     * Performs all organizational structure analysis and returns the report.
     */
    public OrgReport analyzeStructure() {
        OrgReport report = new OrgReport();
        // 1. Salary Compliance Check: Filter to only check employees who are managers.
        employeeMap.values().stream().filter(Employee::isManager).forEach(manager -> checkSalaryCompliance(manager, report));
        // 2. Reporting Line Length Check
        if (ceo != null) {
            employeeMap.values().stream().filter(e -> e.getId() != ceo.getId()).forEach(employee -> checkReportingLineLength(employee, report));
        }
        return report;
    }

    /**
     * Checks if a manager's salary complies with the 20%-50% rule relative to their subordinates.
     */
    public void checkSalaryCompliance(Employee manager, OrgReport report) {
        if (manager.getSubordinates().isEmpty()) {
            return;
        }
        double averageSubordinateSalary = manager.getSubordinates().stream().mapToDouble(Employee::getSalary).average().orElse(0.0);
        if (averageSubordinateSalary == 0.0)
            return;
        // Avoid division by zero if all subordinates earn 0
        double managerSalary = manager.getSalary();
        double minRequiredSalary = averageSubordinateSalary * MIN_SALARY_FACTOR;
        double maxAllowedSalary = averageSubordinateSalary * MAX_SALARY_FACTOR;
        ManagerSalaryIssue issue = new ManagerSalaryIssue();
        issue.managerName = manager.getFirstName() + " " + manager.getLastName();
        issue.managerId = manager.getId();
        issue.expectedMin = minRequiredSalary;
        issue.expectedMax = maxAllowedSalary; // Managers earning less than they should (less than 120%)
        if (managerSalary < minRequiredSalary) {
            issue.difference = minRequiredSalary - managerSalary;
            report.lowEarningManagers.add(issue);
        }
        // Managers earning more than they should (more than 150%)
        else if (managerSalary > maxAllowedSalary) {
            issue.difference = managerSalary - maxAllowedSalary;
            report.highEarningManagers.add(issue);
        }
    }

    /**
     * Checks the reporting line length for an employee. * Length is the number of managers in the chain (Employee -> M1 -> ... -> CEO).
     */
    public void checkReportingLineLength(Employee employee, OrgReport report) {
        int length = 0; // Number of managers up to the CEO (inclusive)
        Employee current = employee; // Traverse up the chain until the CEO is reached (CEO has no managerId)
        while (current.getManagerId().isPresent()) {
            length++;
            Employee manager = employeeMap.get(current.getManagerId().get());
            if (manager == null) {
                // Broken chain
                System.err.println("Warning: Reporting chain for " + employee.getId() + " is broken.");
                return;
            }
            current = manager;
        } // The chain length is the number of managers traversed. Max allowed is 5.
        if (length > MAX_REPORTING_LINE_LENGTH) {
            ReportingLineIssue issue = new ReportingLineIssue();
            issue.employeeName = employee.getFirstName() + " " + employee.getLastName();
            issue.employeeId = employee.getId();
            issue.actualLength = length;
            issue.excessiveManagers = length - MAX_REPORTING_LINE_LENGTH;
            report.longReportingLines.add(issue);
        }
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        OrgAnalyzer analyzer = new OrgAnalyzer();
        try { // 1. Read Data and Build Hierarchy
            System.out.println("Reading data from " + FILE_PATH + "...");
            analyzer.readEmployeesFromFile();
            analyzer.buildHierarchy(); // 2. Analyze Structure
            OrgReport report = analyzer.analyzeStructure(); //
            // 3. Print Report
            report.print();
        } catch (IOException e) {
            System.err.println("\n--- FATAL ERROR ---");
            System.err.println("Could not read file: " + FILE_PATH);
            System.err.println("Please ensure '" + FILE_PATH + "' is in the classpath/project root.");
        } catch (Exception e) {
            System.err.println("\n--- UNEXPECTED ERROR ---");
            e.printStackTrace();
        }
    }
}