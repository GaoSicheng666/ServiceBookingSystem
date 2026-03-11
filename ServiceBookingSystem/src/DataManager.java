import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DataManager {
    private static final String DATA_FILE = "users_data.txt";
    private List<User> users;
    private List<Employee> employees;

    public DataManager() {
        users = new ArrayList<>();
        employees = new ArrayList<>();
        loadData();
    }

    public void loadData() {
        // 清空现有数据，避免重复加载
        users.clear();
        employees.clear();

        try (BufferedReader reader = new BufferedReader(new FileReader(DATA_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 3) continue;

                String type = parts[0];
                String username = parts[1];
                String password = parts[2];

                if (type.equals("USER")) {
                    if (parts.length >= 7) {
                        User user = new User(username, password, parts[3], parts[4], parts[5], Integer.parseInt(parts[6]));
                        // 修复：正确处理分配的员工信息
                        if (parts.length >= 8 && !parts[7].isEmpty()) {
                            user.setAssignedEmployee(parts[7]);
                        }
                        users.add(user);
                    }
                } else if (type.equals("EMPLOYEE")) {
                    if (parts.length >= 7) {
                        Employee employee = new Employee(username, password, parts[3],
                                Integer.parseInt(parts[4]), parts[5], Double.parseDouble(parts[6]));
                        // 修复：正确处理工作状态
                        if (parts.length >= 8) {
                            employee.setWorking(Boolean.parseBoolean(parts[7]));
                        }
                        // 修复：正确处理分配的用户信息
                        if (parts.length >= 9 && !parts[8].isEmpty()) {
                            employee.setAssignedUser(parts[8]);
                        }
                        employees.add(employee);
                    }
                }
            }
        } catch (IOException e) {
            // 文件不存在，第一次运行
            System.out.println("数据文件不存在，将创建新文件");
        }
    }

    public void saveData() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(DATA_FILE))) {
            for (User user : users) {
                // 修复：确保所有字段都被正确保存，包括空的分配信息
                writer.println("USER," + user.getUsername() + "," + user.getPassword() + "," +
                        user.getName() + "," + user.getPhone() + "," + user.getAddress() + "," +
                        user.getAge() + "," +
                        (user.getAssignedEmployee() != null ? user.getAssignedEmployee() : ""));
            }
            for (Employee employee : employees) {
                // 修复：确保所有字段都被正确保存
                writer.println("EMPLOYEE," + employee.getUsername() + "," + employee.getPassword() + "," +
                        employee.getName() + "," + employee.getAge() + "," + employee.getPhone() + "," +
                        employee.getSalary() + "," + employee.isWorking() + "," +
                        (employee.getAssignedUser() != null ? employee.getAssignedUser() : ""));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean usernameExists(String username) {
        return users.stream().anyMatch(u -> u.getUsername().equals(username)) ||
                employees.stream().anyMatch(e -> e.getUsername().equals(username));
    }

    public void addUser(User user) {
        users.add(user);
        saveData();
    }

    public void addEmployee(Employee employee) {
        employees.add(employee);
        saveData();
    }

    public User authenticateUser(String username, String password) {
        return users.stream()
                .filter(u -> u.getUsername().equals(username) && u.getPassword().equals(password))
                .findFirst()
                .orElse(null);
    }

    public Employee authenticateEmployee(String username, String password) {
        return employees.stream()
                .filter(e -> e.getUsername().equals(username) && e.getPassword().equals(password))
                .findFirst()
                .orElse(null);
    }

    public List<Employee> getAvailableEmployees() {
        List<Employee> available = new ArrayList<>();
        for (Employee emp : employees) {
            if (emp.isWorking() && (emp.getAssignedUser() == null || emp.getAssignedUser().isEmpty())) {
                available.add(emp);
            }
        }
        return available;
    }

    public void assignEmployeeToUser(String employeeUsername, String userUsername) {
        for (Employee emp : employees) {
            if (emp.getUsername().equals(employeeUsername)) {
                emp.setAssignedUser(userUsername);
                break;
            }
        }
        for (User user : users) {
            if (user.getUsername().equals(userUsername)) {
                user.setAssignedEmployee(employeeUsername);
                break;
            }
        }
        saveData();
    }

    // 修复解约方法：不再调用 loadData()，避免重复数据
    public void cancelAssignment(String userUsername) {
        for (User user : users) {
            if (user.getUsername().equals(userUsername) && user.getAssignedEmployee() != null && !user.getAssignedEmployee().isEmpty()) {
                String employeeUsername = user.getAssignedEmployee();
                user.setAssignedEmployee("");

                // 同时更新员工的分配信息
                for (Employee emp : employees) {
                    if (emp.getUsername().equals(employeeUsername)) {
                        emp.setAssignedUser("");
                        break;
                    }
                }
                break;
            }
        }
        saveData();
    }

    // 新增方法：根据用户名获取最新的用户对象
    public User getUpdatedUser(String username) {
        return users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    // 新增方法：根据用户名获取最新的员工对象
    public Employee getUpdatedEmployee(String username) {
        return employees.stream()
                .filter(e -> e.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    public void updateEmployeeStatus(String username, boolean isWorking) {
        for (Employee emp : employees) {
            if (emp.getUsername().equals(username)) {
                emp.setWorking(isWorking);
                break;
            }
        }
        saveData();
    }

    public List<Employee> getEmployees() {
        return employees;
    }

    public List<User> getUsers() {
        return users;
    }
}