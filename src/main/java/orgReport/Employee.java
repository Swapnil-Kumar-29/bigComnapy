package orgReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

;
public class Employee {
    private final long id;
    private final String firstName;
    private final String lastName;
    private final Double salary;
    private final Optional<Long> managerId;

    private final List<Employee> subordinates = new ArrayList<>();


    public Employee(long Id, String FirstName, String LastName, Double  Salary, Optional<Long> ManagerId) {
        this.id = Id;
        this.firstName = FirstName;
        this.lastName = LastName;
        this.salary = Salary;
        this.managerId = ManagerId;
    }

    public long getId() {
        return id;
    }
    public String getFirstName() {
        return firstName;
    }
    public String getLastName() {
        return lastName;
    }

    public Double getSalary() {
        return salary;
    }

    public Optional<Long> getManagerId() {
        return managerId;
    }

    public List<Employee> getSubordinates() {
        return subordinates;
    }

    public boolean isManager() {
        return !this.subordinates.isEmpty();
    }

    public void addSubordinates(Employee subordinate) {
        this.subordinates.add(subordinate);
    }

    @Override
    public String toString() {
        return firstName + " " + lastName + " (ID: " + id + ")";
    }
}
