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

/** 员工表数据访问。 */
@Repository
public class EmployeeRepository {

    private final JdbcTemplate jdbc;

    public EmployeeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Employee> findByUsername(String username) {
        List<Employee> list = jdbc.query(
                "SELECT * FROM employees WHERE username = ?", RowMappers.EMPLOYEE, username);
        return list.stream().findFirst();
    }

    public Optional<Employee> findById(int id) {
        List<Employee> list = jdbc.query(
                "SELECT * FROM employees WHERE id = ?", RowMappers.EMPLOYEE, id);
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
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, e.getUsername());
            ps.setString(2, e.getPassword());
            ps.setString(3, e.getName());
            ps.setInt(4, e.getAge());
            ps.setString(5, e.getPhone());
            ps.setBigDecimal(6, e.getSalary());
            ps.setBoolean(7, e.isWorking());
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

    public List<Employee> findAll() {
        return jdbc.query("SELECT * FROM employees ORDER BY id", RowMappers.EMPLOYEE);
    }

    /**
     * 查询在指定日期"可预约"的员工:在职、启用、且当天没有未取消的预约。
     * 若传入 serviceId 仅用于前端过滤展示,这里不强绑定员工与服务(任何在职员工可提供任意服务)。
     */
    public List<Employee> findAvailable(LocalDate date) {
        String sql =
                "SELECT e.* FROM employees e " +
                "WHERE e.is_working = TRUE AND e.is_active = TRUE " +
                "AND NOT EXISTS ( " +
                "  SELECT 1 FROM appointments a " +
                "  WHERE a.employee_id = e.id AND a.appointment_date = ? AND a.status <> 'CANCELLED' " +
                ") ORDER BY e.id";
        return jdbc.query(sql, RowMappers.EMPLOYEE, date);
    }

    public void setActive(int id, boolean active) {
        jdbc.update("UPDATE employees SET is_active = ? WHERE id = ?", active, id);
    }

    public void deleteById(int id) {
        jdbc.update("DELETE FROM employees WHERE id = ?", id);
    }
}
