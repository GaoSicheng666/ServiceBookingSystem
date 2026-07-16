package com.eldercare.repository;

import com.eldercare.entity.Appointment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
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
        String periods = rs.getString("time_periods");
        a.setTimePeriods(periods == null || periods.isBlank() ? List.of() : List.of(periods.split(",")));
        a.setTotalAmount(rs.getBigDecimal("total_amount"));
        a.setCancelledBy(rs.getString("cancelled_by"));
        a.setCancellationReason(rs.getString("cancellation_reason"));
        return a;
    };

    private static final String DETAIL_SELECT =
            "SELECT a.id, a.user_id, a.employee_id, a.service_id, a.appointment_date, a.status, " +
            "u.name AS user_name, u.phone AS user_phone, u.address AS user_address, " +
            "e.name AS employee_name, e.phone AS employee_phone, s.name AS service_name, " +
            "(SELECT GROUP_CONCAT(slot.time_period ORDER BY " +
            " FIELD(slot.time_period, 'MORNING', 'NOON', 'AFTERNOON', 'EVENING')) " +
            " FROM appointment_time_slots slot WHERE slot.appointment_id = a.id) AS time_periods, " +
            "COALESCE((SELECT billing.total_amount FROM appointment_billing billing " +
            " WHERE billing.appointment_id = a.id), s.reference_price) AS total_amount, " +
            "c.cancelled_by, c.reason AS cancellation_reason " +
            "FROM appointments a " +
            "JOIN users u ON a.user_id = u.id " +
            "JOIN employees e ON a.employee_id = e.id " +
            "JOIN services s ON a.service_id = s.id " +
            "LEFT JOIN appointment_cancellations c ON c.appointment_id = a.id ";

    /**
     * 下单前按固定顺序锁定老人和护工账号行。
     * 即使所选日期还没有预约记录，也能串行化同一老人或同一护工的并发下单请求。
     */
    public void lockBookingOwners(int userId, int employeeId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Integer.class, userId);
        jdbc.queryForObject("SELECT id FROM employees WHERE id = ? FOR UPDATE", Integer.class, employeeId);
    }

    /** 查询某员工在指定日期、所选时段内是否已有未取消预约。 */
    public boolean existsActiveForEmployeeSlots(int employeeId, LocalDate date, List<String> timePeriods) {
        return existsActiveSlotConflict("employee_id", employeeId, date, timePeriods);
    }

    /** 查询某老人用户在指定日期、所选时段内是否已有未取消预约。 */
    public boolean existsActiveForUserSlots(int userId, LocalDate date, List<String> timePeriods) {
        return existsActiveSlotConflict("user_id", userId, date, timePeriods);
    }

    private boolean existsActiveSlotConflict(String ownerColumn, int ownerId, LocalDate date,
                                             List<String> timePeriods) {
        String placeholders = String.join(",", Collections.nCopies(timePeriods.size(), "?"));
        String sql =
                "SELECT COUNT(*) FROM appointments a " +
                "WHERE a." + ownerColumn + " = ? AND a.appointment_date = ? " +
                "AND a.status <> 'CANCELLED' " +
                "AND (NOT EXISTS (SELECT 1 FROM appointment_time_slots old_slot " +
                "                 WHERE old_slot.appointment_id = a.id) " +
                "     OR EXISTS (SELECT 1 FROM appointment_time_slots booked_slot " +
                "                WHERE booked_slot.appointment_id = a.id " +
                "                AND booked_slot.time_period IN (" + placeholders + "))) FOR UPDATE";
        List<Object> args = new ArrayList<>();
        args.add(ownerId);
        args.add(date);
        args.addAll(timePeriods);
        Integer count = jdbc.queryForObject(sql, Integer.class, args.toArray());
        return count != null && count > 0;
    }

    public int insert(int userId, int employeeId, int serviceId, LocalDate date) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO appointments (user_id, employee_id, service_id, appointment_date, status) " +
                            "VALUES (?, ?, ?, ?, 'PENDING')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, userId);
            statement.setInt(2, employeeId);
            statement.setInt(3, serviceId);
            statement.setObject(4, date);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public void insertTimeSlots(int appointmentId, List<String> timePeriods) {
        List<Object[]> rows = timePeriods.stream()
                .map(period -> new Object[]{appointmentId, period})
                .toList();
        jdbc.batchUpdate(
                "INSERT INTO appointment_time_slots (appointment_id, time_period) VALUES (?, ?)",
                rows);
    }

    public void insertBilling(int appointmentId, BigDecimal unitPrice, int slotCount,
                              BigDecimal totalAmount) {
        jdbc.update(
                "INSERT INTO appointment_billing " +
                        "(appointment_id, unit_price, slot_count, total_amount) VALUES (?, ?, ?, ?)",
                appointmentId, unitPrice, slotCount, totalAmount);
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

    /** 老人端分页查询本人的预约记录。 */
    public List<Appointment> findByUserIdPage(int userId, String status, int limit, int offset) {
        if (status == null || status.isBlank()) {
            return jdbc.query(
                    DETAIL_SELECT +
                            "WHERE a.user_id = ? ORDER BY a.appointment_date DESC, a.id DESC LIMIT ? OFFSET ?",
                    DETAIL, userId, limit, offset);
        }
        return jdbc.query(
                DETAIL_SELECT +
                        "WHERE a.user_id = ? AND a.status = ? " +
                        "ORDER BY a.appointment_date DESC, a.id DESC LIMIT ? OFFSET ?",
                DETAIL, userId, status, limit, offset);
    }

    public long countByUserId(int userId, String status) {
        Long count;
        if (status == null || status.isBlank()) {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM appointments WHERE user_id = ?", Long.class, userId);
        } else {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM appointments WHERE user_id = ? AND status = ?",
                    Long.class, userId, status);
        }
        return count == null ? 0 : count;
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

    /** 护工端分页查询任务，保持最新预约日期和记录编号排在前面。 */
    public List<Appointment> findByEmployeeIdPage(int employeeId, String status,
                                                   int limit, int offset) {
        if (status == null || status.isBlank()) {
            return jdbc.query(
                    DETAIL_SELECT +
                            "WHERE a.employee_id = ? " +
                            "ORDER BY a.appointment_date DESC, a.id DESC LIMIT ? OFFSET ?",
                    DETAIL, employeeId, limit, offset);
        }
        return jdbc.query(
                DETAIL_SELECT +
                        "WHERE a.employee_id = ? AND a.status = ? " +
                        "ORDER BY a.appointment_date DESC, a.id DESC LIMIT ? OFFSET ?",
                DETAIL, employeeId, status, limit, offset);
    }

    public long countByEmployeeId(int employeeId, String status) {
        Long count;
        if (status == null || status.isBlank()) {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM appointments WHERE employee_id = ?",
                    Long.class, employeeId);
        } else {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM appointments WHERE employee_id = ? AND status = ?",
                    Long.class, employeeId, status);
        }
        return count == null ? 0 : count;
    }

    public long countByEmployeeIdAndDate(int employeeId, String status, LocalDate date) {
        Long count;
        if (status == null || status.isBlank()) {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM appointments WHERE employee_id = ? AND appointment_date = ?",
                    Long.class, employeeId, date);
        } else {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM appointments " +
                            "WHERE employee_id = ? AND appointment_date = ? AND status = ?",
                    Long.class, employeeId, date, status);
        }
        return count == null ? 0 : count;
    }

    /** 返回今天及以后最近的一项待服务任务，用于护工首页的优先任务卡。 */
    public Appointment findNextPendingByEmployeeId(int employeeId, LocalDate fromDate) {
        List<Appointment> list = jdbc.query(
                DETAIL_SELECT +
                        "WHERE a.employee_id = ? AND a.status = 'PENDING' " +
                        "AND a.appointment_date >= ? " +
                        "ORDER BY a.appointment_date ASC, a.id ASC LIMIT 1",
                DETAIL, employeeId, fromDate);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 管理员:全部预约。 */
    public List<Appointment> findAll(String status) {
        if (status == null || status.isBlank()) {
            return jdbc.query(DETAIL_SELECT + "ORDER BY a.appointment_date DESC, a.id DESC", DETAIL);
        }
        return jdbc.query(DETAIL_SELECT + "WHERE a.status = ? ORDER BY a.appointment_date DESC, a.id DESC",
                DETAIL, status);
    }

    /** 管理员分页查询预约，始终保持预约日期和创建顺序由新到旧。 */
    public List<Appointment> findPage(String status, int limit, int offset) {
        if (status == null || status.isBlank()) {
            return jdbc.query(
                    DETAIL_SELECT + "ORDER BY a.appointment_date DESC, a.id DESC LIMIT ? OFFSET ?",
                    DETAIL, limit, offset);
        }
        return jdbc.query(
                DETAIL_SELECT +
                        "WHERE a.status = ? ORDER BY a.appointment_date DESC, a.id DESC LIMIT ? OFFSET ?",
                DETAIL, status, limit, offset);
    }

    /** 统计全部或指定状态的预约数量。 */
    public long count(String status) {
        Long count;
        if (status == null || status.isBlank()) {
            count = jdbc.queryForObject("SELECT COUNT(*) FROM appointments", Long.class);
        } else {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM appointments WHERE status = ?", Long.class, status);
        }
        return count == null ? 0 : count;
    }

    /** 下单事务中锁定预约记录范围并统计总数，用于执行全局容量限制。 */
    public long countAllForUpdate() {
        return jdbc.queryForList(
                "SELECT id FROM appointments ORDER BY id FOR UPDATE", Integer.class).size();
    }

    /**
     * 删除写入时间最早的若干预约，为容量已满时的新预约腾出空间。
     * 附属时段、金额和取消原因由数据库 ON DELETE CASCADE 一并清理。
     */
    public int deleteOldest(int count) {
        if (count <= 0) return 0;
        List<Integer> ids = jdbc.queryForList(
                "SELECT id FROM appointments ORDER BY id ASC LIMIT ?",
                Integer.class, count);
        return deleteByIds(ids);
    }

    /** 查单条(含归属校验用的 user_id/employee_id)。 */
    public Appointment findById(int id) {
        List<Appointment> list = jdbc.query(DETAIL_SELECT + "WHERE a.id = ?", DETAIL, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public int updateStatus(int id, String status) {
        return jdbc.update("UPDATE appointments SET status = ? WHERE id = ?", status, id);
    }

    /** 子表均配置 ON DELETE CASCADE，删除主记录会同步清理时段、金额和取消原因。 */
    public int deleteById(int id) {
        return jdbc.update("DELETE FROM appointments WHERE id = ?", id);
    }

    /** 使用参数占位符批量删除管理员选中的预约，避免拼接外部输入。 */
    public int deleteByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbc.update(
                "DELETE FROM appointments WHERE id IN (" + placeholders + ")",
                ids.toArray());
    }

    /** 保存取消发起方与原因；同一预约再次写入时以最后一次记录为准。 */
    public void recordCancellation(int appointmentId, String cancelledBy, String reason) {
        jdbc.update(
                "INSERT INTO appointment_cancellations (appointment_id, cancelled_by, reason) " +
                        "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE " +
                        "cancelled_by = VALUES(cancelled_by), reason = VALUES(reason), " +
                        "cancelled_at = CURRENT_TIMESTAMP",
                appointmentId, cancelledBy, reason);
    }

    /** 按已完成预约的服务参考价汇总护工累计收入。 */
    public BigDecimal sumCompletedEarnings(int employeeId) {
        BigDecimal total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(COALESCE(b.total_amount, s.reference_price)), 0) " +
                        "FROM appointments a JOIN services s ON s.id = a.service_id " +
                        "LEFT JOIN appointment_billing b ON b.appointment_id = a.id " +
                        "WHERE a.employee_id = ? AND a.status = 'COMPLETED'",
                BigDecimal.class, employeeId);
        return total == null ? BigDecimal.ZERO : total;
    }
}
