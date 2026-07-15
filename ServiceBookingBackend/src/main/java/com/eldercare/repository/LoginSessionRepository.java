package com.eldercare.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 保存每个账号当前唯一有效的登录会话编号。 */
@Repository
public class LoginSessionRepository {

    private final JdbcTemplate jdbc;

    public LoginSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 新登录覆盖旧会话，因此同一账号始终只有一个 sessionId 有效。 */
    public void replace(String username, String role, String sessionId) {
        jdbc.update(
                "INSERT INTO account_sessions (role, username, session_id) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE session_id = VALUES(session_id)",
                role, username, sessionId);
    }

    /** 每次认证请求都核对 JWT 中的会话编号是否仍是数据库中的当前值。 */
    public boolean isCurrent(String username, String role, String sessionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_sessions " +
                        "WHERE role = ? AND username = ? AND session_id = ?",
                Integer.class, role, username, sessionId);
        return count != null && count > 0;
    }
}
