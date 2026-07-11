-- Replace the password before execution. Run as a MySQL administrator.
CREATE USER IF NOT EXISTS 'booking_app'@'localhost' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON service_booking_system.* TO 'booking_app'@'localhost';
FLUSH PRIVILEGES;

