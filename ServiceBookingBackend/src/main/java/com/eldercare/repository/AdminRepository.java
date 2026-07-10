package com.eldercare.repository;

import com.eldercare.entity.Admin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 管理员表数据访问。 */
@Repository
public class AdminRepository {

    private final JdbcTemplate jdbc;

    public AdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Admin> findByUsername(String username) {
        List<Admin> list = jdbc.query(
                "SELECT * FROM admins WHERE username = ?", RowMappers.ADMIN, username);
        return list.stream().findFirst();
    }

    public void updatePassword(int id, String hashedPassword) {
        jdbc.update("UPDATE admins SET password = ? WHERE id = ?", hashedPassword, id);
    }
}
