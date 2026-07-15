package com.eldercare.repository;

import com.eldercare.entity.Employee;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collections;

/** 员工表数据访问。 */
@Repository
public class EmployeeRepository {

    private static final String EMPLOYEE_SELECT =
            "SELECT e.*, " +
            "COALESCE(t.training_completed, FALSE) AS training_completed, " +
            "COALESCE(t.quiz_passed, FALSE) AS quiz_passed, " +
            "COALESCE(t.quiz_score, 0) AS quiz_score, " +
            "p.avatar_data, p.specialty, p.experience, p.bio " +
            "FROM employees e " +
            "LEFT JOIN employee_training t ON t.employee_id = e.id " +
            "LEFT JOIN employee_profiles p ON p.employee_id = e.id ";

    private final JdbcTemplate jdbc;

    public EmployeeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Employee> findByUsername(String username) {
        List<Employee> list = jdbc.query(
                EMPLOYEE_SELECT + "WHERE e.username = ?", RowMappers.EMPLOYEE, username);
        return list.stream().findFirst();
    }

    public Optional<Employee> findById(int id) {
        List<Employee> list = jdbc.query(
                EMPLOYEE_SELECT + "WHERE e.id = ?", RowMappers.EMPLOYEE, id);
        return list.stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE username = ?", Integer.class, username);
        return c != null && c > 0;
    }

    public int insert(Employee e) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO employees (username, password, name, age, phone, salary, is_working) " +
                            "VALUES (?, ?, ?, ?, ?, 0, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, e.getUsername());
            ps.setString(2, e.getPassword());
            ps.setString(3, e.getName());
            ps.setInt(4, e.getAge());
            ps.setString(5, e.getPhone());
            ps.setBoolean(6, e.isWorking());
            return ps;
        }, kh);
        return kh.getKey().intValue();
    }

    public void updatePassword(int id, String hashedPassword) {
        jdbc.update("UPDATE employees SET password = ? WHERE id = ?", hashedPassword, id);
    }

    public void updateWorkingStatus(String username, boolean working) {
        jdbc.update("UPDATE employees SET is_working = ? WHERE username = ?", working, username);
    }

    /** 更新员工表中的实名、年龄和联系电话。 */
    public void updateProfileBasics(int employeeId, String name, int age, String phone) {
        jdbc.update(
                "UPDATE employees SET name = ?, age = ?, phone = ? WHERE id = ?",
                name, age, phone, employeeId);
    }

    /** 保存护工扩展资料；avatarData 为 null 时保留原头像。 */
    public void upsertProfile(int employeeId, String avatarData, String specialty,
                              String experience, String bio) {
        jdbc.update(
                "INSERT INTO employee_profiles " +
                        "(employee_id, avatar_data, specialty, experience, bio) VALUES (?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "avatar_data = COALESCE(VALUES(avatar_data), avatar_data), " +
                        "specialty = VALUES(specialty), experience = VALUES(experience), " +
                        "bio = VALUES(bio), updated_at = CURRENT_TIMESTAMP",
                employeeId, avatarData, specialty, experience, bio);
    }

    public List<Employee> findAll() {
        return jdbc.query(EMPLOYEE_SELECT + "ORDER BY e.id", RowMappers.EMPLOYEE);
    }

    /**
     * 查询在指定日期和全部所选时段均可预约的员工。
     * 员工必须已通过培训、正在接单、当天排班包含全部时段，且这些时段没有未取消预约。
     */
    public List<Employee> findAvailable(LocalDate date, int weekday, List<String> timePeriods) {
        String placeholders = String.join(",", Collections.nCopies(timePeriods.size(), "?"));
        String sql =
                EMPLOYEE_SELECT +
                "WHERE e.is_working = TRUE AND e.is_active = TRUE " +
                "AND COALESCE(t.quiz_passed, FALSE) = TRUE " +
                "AND (SELECT COUNT(DISTINCT ea.time_period) FROM employee_availability ea " +
                "     WHERE ea.employee_id = e.id AND ea.weekday = ? " +
                "     AND ea.time_period IN (" + placeholders + ")) = ? " +
                "AND NOT EXISTS ( " +
                "  SELECT 1 FROM appointments a " +
                "  WHERE a.employee_id = e.id AND a.appointment_date = ? AND a.status <> 'CANCELLED' " +
                "  AND (NOT EXISTS (SELECT 1 FROM appointment_time_slots old_slot WHERE old_slot.appointment_id = a.id) " +
                "       OR EXISTS (SELECT 1 FROM appointment_time_slots booked_slot " +
                "                  WHERE booked_slot.appointment_id = a.id " +
                "                  AND booked_slot.time_period IN (" + placeholders + "))) " +
                ") ORDER BY e.id";

        List<Object> args = new ArrayList<>();
        args.add(weekday);
        args.addAll(timePeriods);
        args.add(timePeriods.size());
        args.add(date);
        args.addAll(timePeriods);
        return jdbc.query(sql, RowMappers.EMPLOYEE, args.toArray());
    }

    /** 判断护工在某星期的全部所选时段是否都设置为可工作。 */
    public boolean hasAvailability(int employeeId, int weekday, List<String> timePeriods) {
        String placeholders = String.join(",", Collections.nCopies(timePeriods.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(employeeId);
        args.add(weekday);
        args.addAll(timePeriods);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT time_period) FROM employee_availability " +
                        "WHERE employee_id = ? AND weekday = ? AND time_period IN (" + placeholders + ")",
                Integer.class, args.toArray());
        return count != null && count == timePeriods.size();
    }

    public void setActive(int id, boolean active) {
        jdbc.update("UPDATE employees SET is_active = ? WHERE id = ?", active, id);
    }

    public void deleteById(int id) {
        jdbc.update("DELETE FROM employees WHERE id = ?", id);
    }
}
