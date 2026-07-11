CREATE DATABASE IF NOT EXISTS service_booking_system
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE service_booking_system;

CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    age TINYINT UNSIGNED NOT NULL,
    assigned_employee_id INT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT chk_users_age CHECK (age BETWEEN 0 AND 120)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS employees (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    age TINYINT UNSIGNED NOT NULL,
    phone VARCHAR(20) NOT NULL,
    salary DECIMAL(10,2) NOT NULL,
    is_working BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_user_id INT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_employees_username UNIQUE (username),
    CONSTRAINT chk_employees_age CHECK (age BETWEEN 18 AND 100),
    CONSTRAINT chk_employees_salary CHECK (salary >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS appointments (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    employee_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_appointments_user UNIQUE (user_id),
    CONSTRAINT uk_appointments_employee UNIQUE (employee_id),
    CONSTRAINT fk_appointments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_appointments_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER //

DROP PROCEDURE IF EXISTS ensure_column//
CREATE PROCEDURE ensure_column(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN column_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND COLUMN_NAME = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', table_name_value, ' ADD COLUMN ', column_name_value, ' ', column_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS ensure_unique_key//
CREATE PROCEDURE ensure_unique_key(
    IN table_name_value VARCHAR(64),
    IN key_name_value VARCHAR(64),
    IN columns_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND INDEX_NAME = key_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', table_name_value, ' ADD CONSTRAINT ', key_name_value, ' UNIQUE (', columns_value, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS ensure_index//
CREATE PROCEDURE ensure_index(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN columns_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND INDEX_NAME = index_name_value
    ) THEN
        SET @ddl = CONCAT('CREATE INDEX ', index_name_value, ' ON ', table_name_value, ' (', columns_value, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS ensure_foreign_key//
CREATE PROCEDURE ensure_foreign_key(
    IN table_name_value VARCHAR(64),
    IN key_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN target_table_value VARCHAR(64),
    IN target_column_value VARCHAR(64),
    IN delete_rule_value VARCHAR(32)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND CONSTRAINT_NAME = key_name_value
          AND CONSTRAINT_TYPE = 'FOREIGN KEY'
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE ', table_name_value,
            ' ADD CONSTRAINT ', key_name_value,
            ' FOREIGN KEY (', column_name_value, ') REFERENCES ',
            target_table_value, '(', target_column_value, ') ON DELETE ', delete_rule_value
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS ensure_check_constraint//
CREATE PROCEDURE ensure_check_constraint(
    IN table_name_value VARCHAR(64),
    IN constraint_name_value VARCHAR(64),
    IN expression_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND CONSTRAINT_NAME = constraint_name_value
          AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', table_name_value, ' ADD CONSTRAINT ', constraint_name_value, ' CHECK (', expression_value, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS migrate_legacy_assignments//
CREATE PROCEDURE migrate_legacy_assignments()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'assigned_employee_id'
    ) THEN
        INSERT IGNORE INTO appointments (user_id, employee_id)
        SELECT id, assigned_employee_id
        FROM users
        WHERE assigned_employee_id IS NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'employees'
          AND COLUMN_NAME = 'assigned_user_id'
    ) THEN
        INSERT IGNORE INTO appointments (user_id, employee_id)
        SELECT assigned_user_id, id
        FROM employees
        WHERE assigned_user_id IS NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'assigned_employee_username'
    ) THEN
        INSERT IGNORE INTO appointments (user_id, employee_id)
        SELECT u.id, e.id
        FROM users u
        JOIN employees e ON e.username = u.assigned_employee_username
        WHERE u.assigned_employee_username IS NOT NULL
          AND u.assigned_employee_username <> '';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'employees'
          AND COLUMN_NAME = 'assigned_user_username'
    ) THEN
        INSERT IGNORE INTO appointments (user_id, employee_id)
        SELECT u.id, e.id
        FROM employees e
        JOIN users u ON u.username = e.assigned_user_username
        WHERE e.assigned_user_username IS NOT NULL
          AND e.assigned_user_username <> '';
    END IF;
END//

DELIMITER ;

CALL ensure_column('users', 'assigned_employee_id', 'INT DEFAULT NULL');
CALL ensure_column('employees', 'assigned_user_id', 'INT DEFAULT NULL');

CALL ensure_unique_key('users', 'uk_users_username', 'username');
CALL ensure_unique_key('employees', 'uk_employees_username', 'username');
CALL ensure_unique_key('appointments', 'uk_appointments_user', 'user_id');
CALL ensure_unique_key('appointments', 'uk_appointments_employee', 'employee_id');

CALL ensure_foreign_key('appointments', 'fk_appointments_user', 'user_id', 'users', 'id', 'CASCADE');
CALL ensure_foreign_key('appointments', 'fk_appointments_employee', 'employee_id', 'employees', 'id', 'CASCADE');

UPDATE users SET age = 0 WHERE age < 0;
UPDATE users SET age = 120 WHERE age > 120;
UPDATE employees SET age = 18 WHERE age < 18;
UPDATE employees SET age = 100 WHERE age > 100;
UPDATE employees SET salary = 0 WHERE salary < 0;

CALL ensure_check_constraint('users', 'chk_users_age', 'age BETWEEN 0 AND 120');
CALL ensure_check_constraint('employees', 'chk_employees_age', 'age BETWEEN 18 AND 100');
CALL ensure_check_constraint('employees', 'chk_employees_salary', 'salary >= 0');

CALL ensure_index('users', 'idx_users_username_password', 'username, password');
CALL ensure_index('employees', 'idx_employees_username_password', 'username, password');
CALL ensure_index('employees', 'idx_employees_available', 'is_working');

CALL migrate_legacy_assignments();

DROP TRIGGER IF EXISTS trg_users_username_insert_unique;
DROP TRIGGER IF EXISTS trg_users_username_update_unique;
DROP TRIGGER IF EXISTS trg_employees_username_insert_unique;
DROP TRIGGER IF EXISTS trg_employees_username_update_unique;

DELIMITER //

CREATE TRIGGER trg_users_username_insert_unique
BEFORE INSERT ON users
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM employees WHERE username = NEW.username) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '用户名已被员工账户使用';
    END IF;
END//

CREATE TRIGGER trg_users_username_update_unique
BEFORE UPDATE ON users
FOR EACH ROW
BEGIN
    IF NEW.username <> OLD.username AND EXISTS (SELECT 1 FROM employees WHERE username = NEW.username) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '用户名已被员工账户使用';
    END IF;
END//

CREATE TRIGGER trg_employees_username_insert_unique
BEFORE INSERT ON employees
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE username = NEW.username) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '用户名已被用户账户使用';
    END IF;
END//

CREATE TRIGGER trg_employees_username_update_unique
BEFORE UPDATE ON employees
FOR EACH ROW
BEGIN
    IF NEW.username <> OLD.username AND EXISTS (SELECT 1 FROM users WHERE username = NEW.username) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '用户名已被用户账户使用';
    END IF;
END//

DELIMITER ;

DROP PROCEDURE IF EXISTS ensure_column;
DROP PROCEDURE IF EXISTS ensure_unique_key;
DROP PROCEDURE IF EXISTS ensure_index;
DROP PROCEDURE IF EXISTS ensure_foreign_key;
DROP PROCEDURE IF EXISTS ensure_check_constraint;
DROP PROCEDURE IF EXISTS migrate_legacy_assignments;

INSERT INTO users (username, password, name, phone, address, age)
VALUES
    ('1', '1', '1', '1', '1', 1),
    ('3', '3', '3', '3', '3', 3)
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    name = VALUES(name),
    phone = VALUES(phone),
    address = VALUES(address),
    age = VALUES(age);

INSERT INTO employees (username, password, name, age, phone, salary, is_working)
VALUES
    ('2', '2', '2', 18, '2', 2.00, TRUE),
    ('4', '4', '4', 18, '4', 4.00, FALSE)
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    name = VALUES(name),
    age = VALUES(age),
    phone = VALUES(phone),
    salary = VALUES(salary),
    is_working = VALUES(is_working);
