package orgReport;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to aggregate and format the analysis results for console output.
 */
public class OrgReport {
    public final List<OrgAnalyzer.ManagerSalaryIssue> lowEarningManagers = new ArrayList<OrgAnalyzer.ManagerSalaryIssue>();
    public final List<OrgAnalyzer.ManagerSalaryIssue> highEarningManagers = new ArrayList<OrgAnalyzer.ManagerSalaryIssue>();
    public final List<OrgAnalyzer.ReportingLineIssue> longReportingLines = new ArrayList<>();

    public void print() {
        System.out.println("\n=======================================================");
        System.out.println("     ORGANIZATIONAL STRUCTURE ANALYSIS REPORT            ");
        System.out.println("=======================================================\n");

        // --------------------------------------------------------------------
        // 1. SALARY VIOLATIONS
        // --------------------------------------------------------------------
        System.out.println("--- 1. SALARY COMPLIANCE VIOLATIONS (Min 20%, Max 50% more than average subordinate salary) ---\n");

        boolean salaryCompliant = lowEarningManagers.isEmpty() && highEarningManagers.isEmpty();
        if (salaryCompliant) {
            System.out.println("  ✓ All managers comply with the 20% - 50% salary rules.");
        } else {
            // Managers Earning Less
            System.out.println("A) MANAGERS EARNING LESS THAN REQUIRED:");
            if (lowEarningManagers.isEmpty()) {
                System.out.println("  - None.");
            } else {
                for (OrgAnalyzer.ManagerSalaryIssue issue : lowEarningManagers) {
                    System.out.printf("  - %s: Earns $%,.2f less than the minimum required salary of $%,.2f.\n",
                            issue.toString(), issue.difference, issue.expectedMin);
                }
            }

            // Managers Earning More
            System.out.println("\nB) MANAGERS EARNING MORE THAN ALLOWED:");
            if (highEarningManagers.isEmpty()) {
                System.out.println("  - None.");
            } else {
                for (OrgAnalyzer.ManagerSalaryIssue issue : highEarningManagers) {
                    System.out.printf("  - %s: Earns $%,.2f more than the maximum allowed salary of $%,.2f.\n",
                            issue.toString(), issue.difference, issue.expectedMax);
                }
            }
        }


        // --------------------------------------------------------------------
        // 2. REPORTING LINE VIOLATIONS
        // --------------------------------------------------------------------
        System.out.println("\n--- 2. REPORTING LINE LENGTH VIOLATIONS (Max chain length: 5) ---\n");

        if (longReportingLines.isEmpty()) {
            System.out.println("  ✓ All employees have a reporting line of 5 managers or less to the CEO.");
        } else {
            System.out.println("Employees with more than 4 managers between them and the CEO:");
            for (OrgAnalyzer.ReportingLineIssue issue : longReportingLines) {
                System.out.printf("  - %s: Has %d managers in the chain, which is %d too many.\n",
                        issue.toString(), issue.actualLength, issue.excessiveManagers);
            }
        }

        System.out.println("\n=======================================================");
    }
}

