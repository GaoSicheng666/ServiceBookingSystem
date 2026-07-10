package com.eldercare.repository;

import com.eldercare.entity.Appointment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 预约单数据访问。
 * 下单逻辑放在 Service 层用事务包裹;这里提供带 FOR UPDATE 的行锁查询与写入方法。
 */
@Repository
public class AppointmentRepository {

    private final JdbcTemplate jdbc;

    public AppointmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 关联查询用的 RowMapper,带上用户/员工/服务的展示字段。 */
    private static final RowMapper<Appointment> DETAIL = (rs, i) -> {
        Appointment a = new Appointment();
        a.setId(rs.getInt("id"));
        a.setUserId(rs.getInt("user_id"));
        a.setEmployeeId(rs.getInt("employee_id"));
        a.setServiceId(rs.getInt("service_id"));
        a.setAppointmentDate(rs.getObject("appointment_date", LocalDate.class));
        a.setStatus(rs.getString("status"));
        a.setUserName(rs.getString("user_name"));
        a.setUserPhone(rs.getString("user_phone"));
        a.setUserAddress(rs.getString("user_address"));
        a.setEmployeeName(rs.getString("employee_name"));
        a.setEmployeePhone(rs.getString("employee_phone"));
        a.setServiceName(rs.getString("service_name"));
        return a;
    };

    private static final String DETAIL_SELECT =
            "SELECT a.id, a.user_id, a.employee_id, a.service_id, a.appointment_date, a.status, " +
            "u.name AS user_name, u.phone AS user_phone, u.address AS user_address, " +
            "e.name AS employee_name, e.phone AS employee_phone, s.name AS service_name " +
            "FROM appointments a " +
            "JOIN users u ON a.user_id = u.id " +
            "JOIN employees e ON a.employee_id = e.id " +
            "JOIN services s ON a.service_id = s.id ";

    /** 行锁:锁住某员工在某天的未取消预约(在事务中调用)。返回是否已存在冲突。 */
    public boolean existsActiveForEmployeeOnDate(int employeeId, LocalDate date) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments " +
                        "WHERE employee_id = ? AND appointment_date = ? AND status <> 'CANCELLED' FOR UPDATE",
                Integer.class, employeeId, date);
        return c != null && c > 0;
    }

    /** 行锁:锁住某用户在某天的未取消预约。返回是否已存在。 */
    public boolean existsActiveForUserOnDate(int userId, LocalDate date) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments " +
                        "WHERE user_id = ? AND appointment_date = ? AND status <> 'CANCELLED' FOR UPDATE",
                Integer.class, userId, date);
        return c != null && c > 0;
    }

    public void insert(int userId, int employeeId, int serviceId, LocalDate date) {
        jdbc.update(
                "INSERT INTO appointments (user_id, employee_id, service_id, appointment_date, status) " +
                        "VALUES (?, ?, ?, ?, 'PENDING')",
                userId, employeeId, serviceId, date);
    }

    /** 用户的预约列表(可按状态过滤;status 为 null 时返回全部,含历史)。 */
    public List<Appointment> findByUserId(int userId, String status) {
        if (status == null || status.isBlank()) {
            return jdbc.query(DETAIL_SELECT + "WHERE a.user_id = ? ORDER BY a.appointment_date DESC, a.id DESC",
                    DETAIL, userId);
        }
        return jdbc.query(DETAIL_SELECT + "WHERE a.user_id = ? AND a.status = ? ORDER BY a.appointment_date DESC, a.id DESC",
                DETAIL, userId, status);
    }

    /** 员工的预约列表。 */
    public List<Appointment> findByEmployeeId(int employeeId, String status) {
        if (status == null || status.isBlank()) {
            return jdbc.query(DETAIL_SELECT + "WHERE a.employee_id = ? ORDER BY a.appointment_date DESC, a.id DESC",
                    DETAIL, employeeId);
        }
        return jdbc.query(DETAIL_SELECT + "WHERE a.employee_id = ? AND a.status = ? ORDER BY a.appointment_date DESC, a.id DESC",
                DETAIL, employeeId, status);
    }

    /** 管理员:全部预约。 */
    public List<Appointment> findAll(String status) {
        if (status == null || status.isBlank()) {
            return jdbc.query(DETAIL_SELECT + "ORDER BY a.appointment_date DESC, a.id DESC", DETAIL);
        }
        return jdbc.query(DETAIL_SELECT + "WHERE a.status = ? ORDER BY a.appointment_date DESC, a.id DESC",
                DETAIL, status);
    }

    /** 查单条(含归属校验用的 user_id/employee_id)。 */
    public Appointment findById(int id) {
        List<Appointment> list = jdbc.query(DETAIL_SELECT + "WHERE a.id = ?", DETAIL, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public int updateStatus(int id, String status) {
        return jdbc.update("UPDATE appointments SET status = ? WHERE id = ?", status, id);
    }
}
