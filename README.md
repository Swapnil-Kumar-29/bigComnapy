Organizational Structure Analyzer
This application is designed to analyze an organization's employee data to ensure management structure compliance and identify potential issues related to salary fairness and reporting line efficiency.

Functionality
The application performs two primary checks:

Salary Compliance: Checks if every manager's salary is between 120% and 150% of the average salary of their direct subordinates.

Reports managers earning too little or too much, and by how much.

Reporting Line Length: Identifies employees whose reporting chain is too long (more than 4 managers between the employee and the CEO).

Data Source
The application reads employee data from a file named employees.csv. This file must be placed in the project's resource directory (src/main/resources) or the project root.

The expected format for each line is: id,firstName,lastName,salary,managerId (The CEO has an empty managerId).

How to Run
This is a standard Java SE console application. After compiling, run the OrgAnalyzer class's main method. Output reports will be printed directly to the console.
