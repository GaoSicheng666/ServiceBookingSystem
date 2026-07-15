package com.eldercare.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/** 护工培训、答题结果和每周可工作时段的数据访问。 */
@Repository
public class EmployeeTrainingRepository {

    private final JdbcTemplate jdbc;

    public EmployeeTrainingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 完成学习或由管理员开放权限后，护工即可进入答题。 */
    public void completeTraining(int employeeId) {
        jdbc.update(
                "INSERT INTO employee_training " +
                        "(employee_id, training_completed, training_completed_at) " +
                        "VALUES (?, TRUE, CURRENT_TIMESTAMP) " +
                        "ON DUPLICATE KEY UPDATE training_completed = TRUE, " +
                        "training_completed_at = COALESCE(training_completed_at, CURRENT_TIMESTAMP)",
                employeeId);
    }

    /** 保存最近一次分数；答题一旦通过，不会被后续数据改回未通过。 */
    public void saveQuizResult(int employeeId, int score, boolean passed) {
        jdbc.update(
                "INSERT INTO employee_training " +
                        "(employee_id, training_completed, quiz_passed, quiz_score, " +
                        "training_completed_at, quiz_passed_at) " +
                        "VALUES (?, TRUE, ?, ?, CURRENT_TIMESTAMP, IF(?, CURRENT_TIMESTAMP, NULL)) " +
                        "ON DUPLICATE KEY UPDATE training_completed = TRUE, " +
                        "quiz_score = VALUES(quiz_score), " +
                        "quiz_passed = IF(quiz_passed, TRUE, VALUES(quiz_passed)), " +
                        "quiz_passed_at = IF(VALUES(quiz_passed), CURRENT_TIMESTAMP, quiz_passed_at)",
                employeeId, passed, score, passed);
    }

    public List<String> findAvailability(int employeeId) {
        return jdbc.query(
                "SELECT weekday, time_period FROM employee_availability " +
                        "WHERE employee_id = ? ORDER BY weekday, " +
                        "FIELD(time_period, 'MORNING', 'AFTERNOON', 'EVENING')",
                (rs, rowNum) -> rs.getInt("weekday") + "_" + rs.getString("time_period"),
                employeeId);
    }

    public void replaceAvailability(int employeeId, List<String> slots) {
        jdbc.update("DELETE FROM employee_availability WHERE employee_id = ?", employeeId);
        if (slots.isEmpty()) {
            return;
        }

        List<Object[]> rows = new ArrayList<>();
        for (String slot : slots) {
            String[] parts = slot.split("_", 2);
            rows.add(new Object[]{employeeId, Integer.parseInt(parts[0]), parts[1]});
        }
        jdbc.batchUpdate(
                "INSERT INTO employee_availability (employee_id, weekday, time_period) VALUES (?, ?, ?)",
                rows);
    }
}
