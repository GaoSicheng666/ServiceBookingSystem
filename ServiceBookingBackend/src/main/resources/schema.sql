-- ============================================================
-- 老年人服务预约系统 —— 后端数据库建表脚本(增强版)
-- 每次应用启动自动执行;全部使用 IF NOT EXISTS,可安全重复运行。
-- 对应技术方案 v1.2 第 6 章。
-- ============================================================

-- 用户(老人)表
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    age TINYINT UNSIGNED NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,       -- 管理员可禁用
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 员工(护工)表
CREATE TABLE IF NOT EXISTS employees (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    age TINYINT UNSIGNED NOT NULL,
    phone VARCHAR(20) NOT NULL,
    salary DECIMAL(10,2) NOT NULL DEFAULT 0,          -- 兼容旧版数据，新注册固定写入 0
    is_working BOOLEAN NOT NULL DEFAULT FALSE,      -- 是否接单
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_employees_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 护工培训与答题状态。独立建表可以兼容系统中已经存在的护工账号。
CREATE TABLE IF NOT EXISTS employee_training (
    employee_id INT PRIMARY KEY,
    training_completed BOOLEAN NOT NULL DEFAULT FALSE,
    quiz_passed BOOLEAN NOT NULL DEFAULT FALSE,
    quiz_score TINYINT UNSIGNED NOT NULL DEFAULT 0,
    training_completed_at TIMESTAMP NULL DEFAULT NULL,
    quiz_passed_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_training_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 护工公开个人资料。头像保存为前端压缩后的 data URL，独立建表兼容已有员工数据。
CREATE TABLE IF NOT EXISTS employee_profiles (
    employee_id INT PRIMARY KEY,
    avatar_data MEDIUMTEXT DEFAULT NULL,
    specialty VARCHAR(100) DEFAULT NULL,
    experience VARCHAR(200) DEFAULT NULL,
    bio VARCHAR(500) DEFAULT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_profile_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 护工每周可工作时段。weekday 使用 1-7 表示星期一至星期日。
CREATE TABLE IF NOT EXISTS employee_availability (
    id INT PRIMARY KEY AUTO_INCREMENT,
    employee_id INT NOT NULL,
    weekday TINYINT UNSIGNED NOT NULL,
    time_period VARCHAR(20) NOT NULL,
    CONSTRAINT uk_employee_availability UNIQUE (employee_id, weekday, time_period),
    CONSTRAINT fk_availability_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    INDEX idx_availability_employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 管理员表
CREATE TABLE IF NOT EXISTS admins (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_admins_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 每个账号只保留一个有效登录会话。新登录会覆盖旧 session_id，使旧 JWT 立即失效。
CREATE TABLE IF NOT EXISTS account_sessions (
    role VARCHAR(20) NOT NULL,
    username VARCHAR(50) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (role, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 服务项目表(由管理员维护)
CREATE TABLE IF NOT EXISTS services (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255) DEFAULT NULL,
    reference_price DECIMAL(10,2) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,         -- 是否上架
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_services_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 护工可胜任的服务项目。服务项目由管理员维护，护工只能从当前上架项目中选择。
CREATE TABLE IF NOT EXISTS employee_service_capabilities (
    employee_id INT NOT NULL,
    service_id INT NOT NULL,
    PRIMARY KEY (employee_id, service_id),
    CONSTRAINT fk_capability_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_capability_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE,
    INDEX idx_capability_service (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 预约单表:从"一对一绑定"升级为"带日期和项目的预约单"
CREATE TABLE IF NOT EXISTS appointments (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    employee_id INT NOT NULL,
    service_id INT NOT NULL,
    appointment_date DATE NOT NULL,                  -- 预约日期(精确到天)
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING/COMPLETED/CANCELLED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_appointments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_appointments_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_appointments_service FOREIGN KEY (service_id) REFERENCES services(id),
    INDEX idx_appointments_user (user_id),
    INDEX idx_appointments_employee (employee_id),
    INDEX idx_appointments_emp_date (employee_id, appointment_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 预约取消记录。独立建表可兼容线上已经存在、没有取消原因字段的 appointments 表。
CREATE TABLE IF NOT EXISTS appointment_cancellations (
    appointment_id INT PRIMARY KEY,
    cancelled_by VARCHAR(20) NOT NULL,              -- USER / EMPLOYEE
    reason VARCHAR(200) NOT NULL,
    cancelled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cancellation_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 一个预约可以包含当天的一个或多个服务时段。
CREATE TABLE IF NOT EXISTS appointment_time_slots (
    appointment_id INT NOT NULL,
    time_period VARCHAR(20) NOT NULL,
    PRIMARY KEY (appointment_id, time_period),
    CONSTRAINT fk_time_slots_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
    INDEX idx_time_slots_period (time_period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 下单时固定单时段价格和总金额，避免管理员以后改价影响历史订单。
CREATE TABLE IF NOT EXISTS appointment_billing (
    appointment_id INT PRIMARY KEY,
    unit_price DECIMAL(10,2) NOT NULL DEFAULT 0,
    slot_count TINYINT UNSIGNED NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_billing_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 初始数据(INSERT IGNORE 保证可重复执行)
-- ------------------------------------------------------------

-- 初始管理员(密码为明文 admin123,首次登录时后端会自动升级为 PBKDF2 哈希)
-- ⚠️ 上线前请务必修改此密码!
INSERT IGNORE INTO admins (username, password, name)
VALUES ('admin', 'admin123', '系统管理员');

-- 示例服务项目
INSERT IGNORE INTO services (name, description, reference_price) VALUES
    ('助浴服务', '协助老人洗澡、清洁', 80.00),
    ('陪诊服务', '陪同老人就医、取药', 120.00),
    ('做饭服务', '上门为老人做饭', 60.00),
    ('保洁服务', '打扫居家卫生', 70.00),
    ('陪聊服务', '陪伴聊天、心理疏导', 50.00);
