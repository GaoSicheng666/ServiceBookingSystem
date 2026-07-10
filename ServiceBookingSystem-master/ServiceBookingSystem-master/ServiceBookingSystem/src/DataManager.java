import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class DataManager {
    private static final String DB_URL = getConfig("SERVICE_DB_URL",
            "jdbc:mysql://localhost:3306/service_booking_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai");
    private static final String DB_USER = getConfig("SERVICE_DB_USER", "root");
    private static final String DB_PASSWORD = getConfig("SERVICE_DB_PASSWORD", "");
    private static final int HASH_ITERATIONS = 120000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final String HASH_PREFIX = "pbkdf2";

    private final List<User> users;
    private final List<Employee> employees;

    public DataManager() {
        users = new ArrayList<>();
        employees = new ArrayList<>();
        initializeDatabase();
        loadData();
    }

    private static String getConfig(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            value = System.getProperty(key);
        }
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private void initializeDatabase() {
        try (Connection connection = getConnection()) {
            createTables(connection);
            migrateLegacyColumns(connection);
            createAppointmentsTable(connection);
            migrateLegacyAssignments(connection);
            normalizeDataForConstraints(connection);
            addConstraints(connection);
            addIndexes(connection);
            addUsernameTriggers(connection);
            upgradePlainPasswords(connection);
        } catch (SQLException e) {
            showDatabaseError("初始化数据库失败", e);
        }
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "id INT PRIMARY KEY AUTO_INCREMENT," +
                            "username VARCHAR(50) NOT NULL," +
                            "password VARCHAR(255) NOT NULL," +
                            "name VARCHAR(50) NOT NULL," +
                            "phone VARCHAR(20) NOT NULL," +
                            "address VARCHAR(255) NOT NULL," +
                            "age TINYINT UNSIGNED NOT NULL," +
                            "assigned_employee_id INT DEFAULT NULL," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "CONSTRAINT uk_users_username UNIQUE (username)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS employees (" +
                            "id INT PRIMARY KEY AUTO_INCREMENT," +
                            "username VARCHAR(50) NOT NULL," +
                            "password VARCHAR(255) NOT NULL," +
                            "name VARCHAR(50) NOT NULL," +
                            "age TINYINT UNSIGNED NOT NULL," +
                            "phone VARCHAR(20) NOT NULL," +
                            "salary DECIMAL(10,2) NOT NULL," +
                            "is_working BOOLEAN NOT NULL DEFAULT FALSE," +
                            "assigned_user_id INT DEFAULT NULL," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "CONSTRAINT uk_employees_username UNIQUE (username)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
        }
    }

    private void createAppointmentsTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS appointments (" +
                            "id INT PRIMARY KEY AUTO_INCREMENT," +
                            "user_id INT NOT NULL," +
                            "employee_id INT NOT NULL," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "CONSTRAINT uk_appointments_user UNIQUE (user_id)," +
                            "CONSTRAINT uk_appointments_employee UNIQUE (employee_id)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
        }
    }

    private void migrateLegacyColumns(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "users", "assigned_employee_id", "INT DEFAULT NULL");
        addColumnIfMissing(connection, "employees", "assigned_user_id", "INT DEFAULT NULL");
    }

    private void migrateLegacyAssignments(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT IGNORE INTO appointments (user_id, employee_id) " +
                            "SELECT id, assigned_employee_id FROM users WHERE assigned_employee_id IS NOT NULL"
            );
            statement.executeUpdate(
                    "INSERT IGNORE INTO appointments (user_id, employee_id) " +
                            "SELECT assigned_user_id, id FROM employees WHERE assigned_user_id IS NOT NULL"
            );
        }

        if (columnExists(connection, "users", "assigned_employee_username")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT IGNORE INTO appointments (user_id, employee_id) " +
                                "SELECT u.id, e.id FROM users u " +
                                "JOIN employees e ON e.username = u.assigned_employee_username " +
                                "WHERE u.assigned_employee_username IS NOT NULL AND u.assigned_employee_username <> ''"
                );
            }
        }

        if (columnExists(connection, "employees", "assigned_user_username")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT IGNORE INTO appointments (user_id, employee_id) " +
                                "SELECT u.id, e.id FROM employees e " +
                                "JOIN users u ON u.username = e.assigned_user_username " +
                                "WHERE e.assigned_user_username IS NOT NULL AND e.assigned_user_username <> ''"
                );
            }
        }
    }

    private void normalizeDataForConstraints(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE users SET age = 0 WHERE age < 0");
            statement.executeUpdate("UPDATE users SET age = 120 WHERE age > 120");
            statement.executeUpdate("UPDATE employees SET age = 18 WHERE age < 18");
            statement.executeUpdate("UPDATE employees SET age = 100 WHERE age > 100");
            statement.executeUpdate("UPDATE employees SET salary = 0 WHERE salary < 0");
        }
    }

    private void addConstraints(Connection connection) throws SQLException {
        addUniqueKeyIfMissing(connection, "users", "uk_users_username", "username");
        addUniqueKeyIfMissing(connection, "employees", "uk_employees_username", "username");
        addUniqueKeyIfMissing(connection, "appointments", "uk_appointments_user", "user_id");
        addUniqueKeyIfMissing(connection, "appointments", "uk_appointments_employee", "employee_id");
        addForeignKeyIfMissing(connection, "appointments", "fk_appointments_user",
                "user_id", "users", "id", "CASCADE");
        addForeignKeyIfMissing(connection, "appointments", "fk_appointments_employee",
                "employee_id", "employees", "id", "CASCADE");
        addCheckConstraintIfMissing(connection, "users", "chk_users_age", "age BETWEEN 0 AND 120");
        addCheckConstraintIfMissing(connection, "employees", "chk_employees_age", "age BETWEEN 18 AND 100");
        addCheckConstraintIfMissing(connection, "employees", "chk_employees_salary", "salary >= 0");
    }

    private void addIndexes(Connection connection) throws SQLException {
        addIndexIfMissing(connection, "users", "idx_users_username_password", "username, password");
        addIndexIfMissing(connection, "employees", "idx_employees_username_password", "username, password");
        addIndexIfMissing(connection, "employees", "idx_employees_available", "is_working");
        addIndexIfMissing(connection, "appointments", "idx_appointments_pair", "user_id, employee_id");
    }

    private void addUsernameTriggers(Connection connection) throws SQLException {
        executeTrigger(connection, "trg_users_username_insert_unique",
                "CREATE TRIGGER trg_users_username_insert_unique " +
                        "BEFORE INSERT ON users FOR EACH ROW " +
                        "BEGIN " +
                        "IF EXISTS (SELECT 1 FROM employees WHERE username = NEW.username) THEN " +
                        "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '用户名已被员工账户使用'; " +
                        "END IF; " +
                        "END");
        executeTrigger(connection, "trg_users_username_update_unique",
                "CREATE TRIGGER trg_users_username_update_unique " +
                        "BEFORE UPDATE ON users FOR EACH ROW " +
                        "BEGIN " +
                        "IF NEW.username <> OLD.username AND EXISTS (SELECT 1 FROM employees WHERE username = NEW.username) THEN " +
                        "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '用户名已被员工账户使用'; " +
                        "END IF; " +
                        "END");
        executeTrigger(connection, "trg_employees_username_insert_unique",
                "CREATE TRIGGER trg_employees_username_insert_unique " +
                        "BEFORE INSERT ON employees FOR EACH ROW " +
                        "BEGIN " +
                        "IF EXISTS (SELECT 1 FROM users WHERE username = NEW.username) THEN " +
                        "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '用户名已被用户账户使用'; " +
                        "END IF; " +
                        "END");
        executeTrigger(connection, "trg_employees_username_update_unique",
                "CREATE TRIGGER trg_employees_username_update_unique " +
                        "BEFORE UPDATE ON employees FOR EACH ROW " +
                        "BEGIN " +
                        "IF NEW.username <> OLD.username AND EXISTS (SELECT 1 FROM users WHERE username = NEW.username) THEN " +
                        "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '用户名已被用户账户使用'; " +
                        "END IF; " +
                        "END");
    }

    private void executeTrigger(Connection connection, String triggerName, String createSql) throws SQLException {
        if (!triggerExists(connection, triggerName)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createSql);
            }
        }
    }

    private void addColumnIfMissing(Connection connection, String tableName, String columnName, String definition) throws SQLException {
        if (!columnExists(connection, tableName, columnName)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
            }
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void addUniqueKeyIfMissing(Connection connection, String tableName, String keyName, String columns) throws SQLException {
        if (!keyExists(connection, tableName, keyName)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + tableName + " ADD CONSTRAINT " + keyName + " UNIQUE (" + columns + ")");
            }
        }
    }

    private void addForeignKeyIfMissing(Connection connection, String tableName, String keyName,
                                       String columnName, String targetTable, String targetColumn,
                                       String deleteRule) throws SQLException {
        if (!constraintExists(connection, tableName, keyName)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + tableName +
                        " ADD CONSTRAINT " + keyName +
                        " FOREIGN KEY (" + columnName + ") REFERENCES " + targetTable + "(" + targetColumn + ")" +
                        " ON DELETE " + deleteRule);
            }
        }
    }

    private void addCheckConstraintIfMissing(Connection connection, String tableName,
                                             String constraintName, String expression) throws SQLException {
        if (!constraintExists(connection, tableName, constraintName)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + tableName +
                        " ADD CONSTRAINT " + constraintName + " CHECK (" + expression + ")");
            }
        }
    }

    private void addIndexIfMissing(Connection connection, String tableName, String indexName, String columns) throws SQLException {
        if (!keyExists(connection, tableName, indexName)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE INDEX " + indexName + " ON " + tableName + " (" + columns + ")");
            }
        }
    }

    private boolean keyExists(Connection connection, String tableName, String keyName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, keyName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean constraintExists(Connection connection, String tableName, String constraintName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.TABLE_CONSTRAINTS " +
                "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean triggerExists(Connection connection, String triggerName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.TRIGGERS " +
                "WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, triggerName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void upgradePlainPasswords(Connection connection) throws SQLException {
        upgradePlainPasswords(connection, "users");
        upgradePlainPasswords(connection, "employees");
    }

    private void upgradePlainPasswords(Connection connection, String tableName) throws SQLException {
        String selectSql = "SELECT id, password FROM " + tableName + " WHERE password NOT LIKE ?";
        String updateSql = "UPDATE " + tableName + " SET password = ? WHERE id = ?";
        try (PreparedStatement select = connection.prepareStatement(selectSql);
             PreparedStatement update = connection.prepareStatement(updateSql)) {
            select.setString(1, HASH_PREFIX + "$%");
            try (ResultSet resultSet = select.executeQuery()) {
                while (resultSet.next()) {
                    update.setString(1, hashPassword(resultSet.getString("password")));
                    update.setInt(2, resultSet.getInt("id"));
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }

    public void loadData() {
        users.clear();
        employees.clear();

        try (Connection connection = getConnection()) {
            loadUsers(connection);
            loadEmployees(connection);
        } catch (SQLException e) {
            showDatabaseError("读取数据库失败", e);
        }
    }

    private void loadUsers(Connection connection) throws SQLException {
        String sql = "SELECT u.username, u.password, u.name, u.phone, u.address, u.age, e.username AS assigned_employee_username " +
                "FROM users u " +
                "LEFT JOIN appointments a ON a.user_id = u.id " +
                "LEFT JOIN employees e ON a.employee_id = e.id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(mapUser(resultSet));
            }
        }
    }

    private void loadEmployees(Connection connection) throws SQLException {
        String sql = "SELECT e.username, e.password, e.name, e.age, e.phone, e.salary, e.is_working, u.username AS assigned_user_username " +
                "FROM employees e " +
                "LEFT JOIN appointments a ON a.employee_id = e.id " +
                "LEFT JOIN users u ON a.user_id = u.id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                employees.add(mapEmployee(resultSet));
            }
        }
    }

    public void saveData() {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                clearTables(connection);
                insertUsers(connection);
                insertEmployees(connection);
                restoreAppointments(connection);
                connection.commit();
                loadData();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            showDatabaseError("保存数据库失败", e);
        }
    }

    private void clearTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM appointments");
            statement.executeUpdate("DELETE FROM users");
            statement.executeUpdate("DELETE FROM employees");
        }
    }

    private void insertUsers(Connection connection) throws SQLException {
        String sql = "INSERT INTO users (username, password, name, phone, address, age) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (User user : users) {
                statement.setString(1, user.getUsername());
                statement.setString(2, normalizePasswordForStorage(user.getPassword()));
                statement.setString(3, user.getName());
                statement.setString(4, user.getPhone());
                statement.setString(5, user.getAddress());
                statement.setInt(6, user.getAge());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertEmployees(Connection connection) throws SQLException {
        String sql = "INSERT INTO employees (username, password, name, age, phone, salary, is_working) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Employee employee : employees) {
                statement.setString(1, employee.getUsername());
                statement.setString(2, normalizePasswordForStorage(employee.getPassword()));
                statement.setString(3, employee.getName());
                statement.setInt(4, employee.getAge());
                statement.setString(5, employee.getPhone());
                statement.setDouble(6, employee.getSalary());
                statement.setBoolean(7, employee.isWorking());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void restoreAppointments(Connection connection) throws SQLException {
        String sql = "INSERT IGNORE INTO appointments (user_id, employee_id) " +
                "SELECT u.id, e.id FROM users u JOIN employees e ON e.username = ? WHERE u.username = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (User user : users) {
                if (user.getAssignedEmployee() != null && !user.getAssignedEmployee().isEmpty()) {
                    statement.setString(1, user.getAssignedEmployee());
                    statement.setString(2, user.getUsername());
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    public boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ? UNION SELECT 1 FROM employees WHERE username = ? LIMIT 1";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            showDatabaseError("检查用户名失败", e);
            return false;
        }
    }

    public void addUser(User user) {
        String sql = "INSERT INTO users (username, password, name, phone, address, age) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getUsername());
            statement.setString(2, hashPassword(user.getPassword()));
            statement.setString(3, user.getName());
            statement.setString(4, user.getPhone());
            statement.setString(5, user.getAddress());
            statement.setInt(6, user.getAge());
            statement.executeUpdate();
            loadData();
        } catch (SQLException e) {
            showDatabaseError("新增用户失败", e);
        }
    }

    public void addEmployee(Employee employee) {
        String sql = "INSERT INTO employees (username, password, name, age, phone, salary, is_working) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, employee.getUsername());
            statement.setString(2, hashPassword(employee.getPassword()));
            statement.setString(3, employee.getName());
            statement.setInt(4, employee.getAge());
            statement.setString(5, employee.getPhone());
            statement.setDouble(6, employee.getSalary());
            statement.setBoolean(7, employee.isWorking());
            statement.executeUpdate();
            loadData();
        } catch (SQLException e) {
            showDatabaseError("新增员工失败", e);
        }
    }

    public User authenticateUser(String username, String password) {
        String sql = "SELECT u.id, u.username, u.password, u.name, u.phone, u.address, u.age, e.username AS assigned_employee_username " +
                "FROM users u " +
                "LEFT JOIN appointments a ON a.user_id = u.id " +
                "LEFT JOIN employees e ON a.employee_id = e.id " +
                "WHERE u.username = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && verifyPassword(password, resultSet.getString("password"))) {
                    migratePasswordIfNeeded(connection, "users", resultSet.getInt("id"), resultSet.getString("password"));
                    User user = mapUser(resultSet);
                    loadData();
                    return user;
                }
            }
        } catch (SQLException e) {
            showDatabaseError("用户登录验证失败", e);
        }
        return null;
    }

    public Employee authenticateEmployee(String username, String password) {
        String sql = "SELECT e.id, e.username, e.password, e.name, e.age, e.phone, e.salary, e.is_working, u.username AS assigned_user_username " +
                "FROM employees e " +
                "LEFT JOIN appointments a ON a.employee_id = e.id " +
                "LEFT JOIN users u ON a.user_id = u.id " +
                "WHERE e.username = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && verifyPassword(password, resultSet.getString("password"))) {
                    migratePasswordIfNeeded(connection, "employees", resultSet.getInt("id"), resultSet.getString("password"));
                    Employee employee = mapEmployee(resultSet);
                    loadData();
                    return employee;
                }
            }
        } catch (SQLException e) {
            showDatabaseError("员工登录验证失败", e);
        }
        return null;
    }

    public List<Employee> getAvailableEmployees() {
        String sql = "SELECT e.username, e.password, e.name, e.age, e.phone, e.salary, e.is_working, " +
                "u.username AS assigned_user_username " +
                "FROM employees e " +
                "LEFT JOIN appointments a ON a.employee_id = e.id " +
                "LEFT JOIN users u ON a.user_id = u.id " +
                "WHERE e.is_working = TRUE AND a.id IS NULL";
        List<Employee> available = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                available.add(mapEmployee(resultSet));
            }
            loadData();
        } catch (SQLException e) {
            showDatabaseError("读取可用员工失败", e);
        }
        return available;
    }

    public void assignEmployeeToUser(String employeeUsername, String userUsername) {
        String lockUserSql = "SELECT id FROM users WHERE username = ? FOR UPDATE";
        String lockEmployeeSql = "SELECT id, is_working FROM employees WHERE username = ? FOR UPDATE";
        String lockUserAppointmentSql = "SELECT id FROM appointments WHERE user_id = ? FOR UPDATE";
        String lockEmployeeAppointmentSql = "SELECT id FROM appointments WHERE employee_id = ? FOR UPDATE";
        String insertAppointmentSql = "INSERT INTO appointments (user_id, employee_id) VALUES (?, ?)";

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                int userId = lockUser(connection, lockUserSql, userUsername);
                EmployeeLock employeeLock = lockEmployee(connection, lockEmployeeSql, employeeUsername);

                if (appointmentExists(connection, lockUserAppointmentSql, userId)) {
                    throw new SQLException("当前用户已经预约了员工");
                }
                if (!employeeLock.isWorking || appointmentExists(connection, lockEmployeeAppointmentSql, employeeLock.employeeId)) {
                    throw new SQLException("当前员工不可预约");
                }

                try (PreparedStatement statement = connection.prepareStatement(insertAppointmentSql)) {
                    statement.setInt(1, userId);
                    statement.setInt(2, employeeLock.employeeId);
                    statement.executeUpdate();
                }

                connection.commit();
                loadData();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            showDatabaseError("分配员工失败", e);
        }
    }

    public void cancelAssignment(String userUsername) {
        String lockUserSql = "SELECT id FROM users WHERE username = ? FOR UPDATE";
        String lockAppointmentSql = "SELECT id, employee_id FROM appointments WHERE user_id = ? FOR UPDATE";
        String lockEmployeeSql = "SELECT id FROM employees WHERE id = ? FOR UPDATE";
        String deleteAppointmentSql = "DELETE FROM appointments WHERE id = ?";

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                int userId = lockUser(connection, lockUserSql, userUsername);
                Integer appointmentId = null;
                Integer employeeId = null;
                try (PreparedStatement statement = connection.prepareStatement(lockAppointmentSql)) {
                    statement.setInt(1, userId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            appointmentId = resultSet.getInt("id");
                            employeeId = resultSet.getInt("employee_id");
                        }
                    }
                }

                if (employeeId != null) {
                    try (PreparedStatement statement = connection.prepareStatement(lockEmployeeSql)) {
                        statement.setInt(1, employeeId);
                        statement.executeQuery().close();
                    }
                }

                if (appointmentId != null) {
                    try (PreparedStatement statement = connection.prepareStatement(deleteAppointmentSql)) {
                        statement.setInt(1, appointmentId);
                        statement.executeUpdate();
                    }
                }

                connection.commit();
                loadData();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            showDatabaseError("取消预约失败", e);
        }
    }

    private int lockUser(Connection connection, String sql, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("用户不存在: " + username);
                }
                return resultSet.getInt("id");
            }
        }
    }

    private EmployeeLock lockEmployee(Connection connection, String sql, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("员工不存在: " + username);
                }
                return new EmployeeLock(resultSet.getInt("id"), resultSet.getBoolean("is_working"));
            }
        }
    }

    private boolean appointmentExists(Connection connection, String sql, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public User getUpdatedUser(String username) {
        loadData();
        return users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    public Employee getUpdatedEmployee(String username) {
        loadData();
        return employees.stream()
                .filter(e -> e.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    public void updateEmployeeStatus(String username, boolean isWorking) {
        String sql = "UPDATE employees SET is_working = ? WHERE username = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, isWorking);
            statement.setString(2, username);
            statement.executeUpdate();
            loadData();
        } catch (SQLException e) {
            showDatabaseError("更新员工工作状态失败", e);
        }
    }

    public List<Employee> getEmployees() {
        loadData();
        return employees;
    }

    public List<User> getUsers() {
        loadData();
        return users;
    }

    private User mapUser(ResultSet resultSet) throws SQLException {
        User user = new User(
                resultSet.getString("username"),
                resultSet.getString("password"),
                resultSet.getString("name"),
                resultSet.getString("phone"),
                resultSet.getString("address"),
                resultSet.getInt("age")
        );
        String assignedEmployee = resultSet.getString("assigned_employee_username");
        if (assignedEmployee != null && !assignedEmployee.isEmpty()) {
            user.setAssignedEmployee(assignedEmployee);
        }
        return user;
    }

    private Employee mapEmployee(ResultSet resultSet) throws SQLException {
        Employee employee = new Employee(
                resultSet.getString("username"),
                resultSet.getString("password"),
                resultSet.getString("name"),
                resultSet.getInt("age"),
                resultSet.getString("phone"),
                resultSet.getDouble("salary")
        );
        employee.setWorking(resultSet.getBoolean("is_working"));
        String assignedUser = resultSet.getString("assigned_user_username");
        if (assignedUser != null && !assignedUser.isEmpty()) {
            employee.setAssignedUser(assignedUser);
        }
        return employee;
    }

    private String normalizePasswordForStorage(String password) {
        return isHashedPassword(password) ? password : hashPassword(password);
    }

    private boolean verifyPassword(String candidate, String storedPassword) {
        if (!isHashedPassword(storedPassword)) {
            return candidate.equals(storedPassword);
        }

        try {
            String[] parts = storedPassword.split("\\$");
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(candidate.toCharArray(), salt, iterations, expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void migratePasswordIfNeeded(Connection connection, String tableName, int id, String storedPassword) throws SQLException {
        if (!isHashedPassword(storedPassword)) {
            String sql = "UPDATE " + tableName + " SET password = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, hashPassword(storedPassword));
                statement.setInt(2, id);
                statement.executeUpdate();
            }
        }
    }

    private boolean isHashedPassword(String password) {
        return password != null && password.startsWith(HASH_PREFIX + "$");
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, HASH_ITERATIONS, HASH_BYTES);
        return HASH_PREFIX + "$" + HASH_ITERATIONS + "$" +
                Base64.getEncoder().encodeToString(salt) + "$" +
                Base64.getEncoder().encodeToString(hash);
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bytes * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("密码哈希失败", e);
        }
    }

    private void showDatabaseError(String message, SQLException e) {
        System.err.println(message + ": " + e.getMessage());
        e.printStackTrace();
    }

    private static class EmployeeLock {
        private final int employeeId;
        private final boolean isWorking;

        private EmployeeLock(int employeeId, boolean isWorking) {
            this.employeeId = employeeId;
            this.isWorking = isWorking;
        }
    }
}
