public class Employee {
    private String username;
    private String password;
    private String name;
    private int age;
    private String phone;
    private double salary;
    private boolean isWorking;
    private String assignedUser;

    public Employee(String username, String password, String name, int age, String phone, double salary) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.age = age;
        this.phone = phone;
        this.salary = salary;
        this.isWorking = false;
        this.assignedUser = "";
    }

    // Getters and Setters
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getPhone() { return phone; }
    public double getSalary() { return salary; }
    public boolean isWorking() { return isWorking; }
    public String getAssignedUser() { return assignedUser; }
    public void setWorking(boolean working) { this.isWorking = working; }
    public void setAssignedUser(String user) { this.assignedUser = user; }
}