-- Migrate the legacy one-to-one schema to schema v2. MySQL 8.0+.
-- 1. Back up the database first.
-- 2. Run schema-v2.sql first (it creates missing tables).
-- 3. Run this file once. Existing assignments become today's PENDING appointments.
USE service_booking_system;

ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Remove legacy unique indexes so users/employees may have appointment history.
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='appointments' AND index_name='uk_appointments_user'),
 'ALTER TABLE appointments DROP INDEX uk_appointments_user', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='appointments' AND index_name='uk_appointments_employee'),
 'ALTER TABLE appointments DROP INDEX uk_appointments_employee', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

ALTER TABLE appointments
  ADD COLUMN IF NOT EXISTS service_id BIGINT UNSIGNED NULL AFTER employee_id,
  ADD COLUMN IF NOT EXISTS appointment_date DATE NULL AFTER service_id,
  ADD COLUMN IF NOT EXISTS status ENUM('PENDING','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PENDING' AFTER appointment_date,
  ADD COLUMN IF NOT EXISTS notes VARCHAR(500) NULL AFTER status,
  ADD COLUMN IF NOT EXISTS cancelled_by ENUM('USER','EMPLOYEE','ADMIN','SYSTEM') NULL AFTER notes,
  ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP NULL AFTER cancelled_by,
  ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP NULL AFTER cancelled_at;

SET @default_service_id = (SELECT id FROM services WHERE name='其他服务' LIMIT 1);
INSERT INTO services(name, description, reference_price, is_active)
SELECT '其他服务', '由旧版一对一分配记录迁移', 0, TRUE
WHERE @default_service_id IS NULL;
SET @default_service_id = (SELECT id FROM services WHERE name='其他服务' LIMIT 1);

UPDATE appointments
SET service_id = COALESCE(service_id, @default_service_id),
    appointment_date = COALESCE(appointment_date, DATE(created_at));

ALTER TABLE appointments
  MODIFY service_id BIGINT UNSIGNED NOT NULL,
  MODIFY appointment_date DATE NOT NULL;

-- Add constraints/indexes only when absent.
SET @sql = IF(NOT EXISTS(SELECT 1 FROM information_schema.table_constraints
 WHERE constraint_schema=DATABASE() AND table_name='appointments' AND constraint_name='fk_appointments_service'),
 'ALTER TABLE appointments ADD CONSTRAINT fk_appointments_service FOREIGN KEY(service_id) REFERENCES services(id) ON DELETE RESTRICT', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(NOT EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='appointments' AND column_name='active_marker'),
 'ALTER TABLE appointments ADD COLUMN active_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status=''PENDING'' THEN 1 ELSE NULL END) STORED', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(NOT EXISTS(SELECT 1 FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='appointments' AND index_name='uk_appointments_active_employee_date'),
 'ALTER TABLE appointments ADD UNIQUE INDEX uk_appointments_active_employee_date(employee_id, appointment_date, active_marker)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
