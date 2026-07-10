public class User {
    private String username;
    private String password;
    private String name;
    private String phone;
    private String address;
    private int age;
    private String assignedEmployee;

    public User(String username, String password, String name, String phone, String address, int age) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.age = age;
        this.assignedEmployee = "";
    }

    // Getters and Setters
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public int getAge() { return age; }
    public String getAssignedEmployee() { return assignedEmployee; }
    public void setAssignedEmployee(String employee) { this.assignedEmployee = employee; }
}