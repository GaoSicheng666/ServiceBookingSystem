-- Service Booking System - C/S server database schema (MySQL 8.0+)
-- Run as a database administrator. Application code must use a restricted account.

CREATE DATABASE IF NOT EXISTS service_booking_system
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE service_booking_system;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password VARCHAR(255) NOT NULL COMMENT 'PBKDF2 encoded value',
  name VARCHAR(50) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  address VARCHAR(255) NOT NULL,
  age TINYINT UNSIGNED NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  KEY idx_users_active (is_active),
  CONSTRAINT chk_users_age CHECK (age BETWEEN 0 AND 120)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS employees (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password VARCHAR(255) NOT NULL COMMENT 'PBKDF2 encoded value',
  name VARCHAR(50) NOT NULL,
  age TINYINT UNSIGNED NOT NULL,
  phone VARCHAR(20) NOT NULL,
  salary DECIMAL(10,2) NOT NULL DEFAULT 0,
  is_working BOOLEAN NOT NULL DEFAULT FALSE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_employees_username (username),
  KEY idx_employees_available (is_active, is_working),
  CONSTRAINT chk_employees_age CHECK (age BETWEEN 18 AND 100),
  CONSTRAINT chk_employees_salary CHECK (salary >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS admins (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password VARCHAR(255) NOT NULL COMMENT 'PBKDF2 encoded value',
  name VARCHAR(50) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admins_username (username)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS services (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  reference_price DECIMAL(10,2) NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_services_name (name),
  KEY idx_services_active (is_active),
  CONSTRAINT chk_services_price CHECK (reference_price >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS employee_services (
  employee_id BIGINT UNSIGNED NOT NULL,
  service_id BIGINT UNSIGNED NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (employee_id, service_id),
  KEY idx_employee_services_service (service_id, employee_id),
  CONSTRAINT fk_employee_services_employee FOREIGN KEY (employee_id)
    REFERENCES employees(id) ON DELETE CASCADE,
  CONSTRAINT fk_employee_services_service FOREIGN KEY (service_id)
    REFERENCES services(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS appointments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  employee_id BIGINT UNSIGNED NOT NULL,
  service_id BIGINT UNSIGNED NOT NULL,
  appointment_date DATE NOT NULL,
  status ENUM('PENDING','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  notes VARCHAR(500) NULL,
  cancelled_by ENUM('USER','EMPLOYEE','ADMIN','SYSTEM') NULL,
  cancelled_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- MySQL permits many NULL values in a unique index. Thus only active appointments
  -- participate in collision detection; cancelled history is retained safely.
  active_marker TINYINT GENERATED ALWAYS AS (
    CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END
  ) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_appointments_active_employee_date (employee_id, appointment_date, active_marker),
  KEY idx_appointments_user_date (user_id, appointment_date DESC),
  KEY idx_appointments_employee_date (employee_id, appointment_date DESC),
  KEY idx_appointments_status_date (status, appointment_date),
  KEY idx_appointments_service_date (service_id, appointment_date),
  CONSTRAINT fk_appointments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_appointments_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
  CONSTRAINT fk_appointments_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

INSERT INTO services (name, description, reference_price, is_active) VALUES
  ('助浴', '协助老人完成安全洗浴', 80.00, TRUE),
  ('陪诊', '陪同就医、取号及取药', 120.00, TRUE),
  ('助餐', '上门备餐或协助进餐', 50.00, TRUE)
ON DUPLICATE KEY UPDATE description = VALUES(description);
