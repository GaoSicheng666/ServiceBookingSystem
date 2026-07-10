package com.eldercare.repository;

import com.eldercare.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/** 用户表数据访问。 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByUsername(String username) {
        List<User> list = jdbc.query(
                "SELECT * FROM users WHERE username = ?", RowMappers.USER, username);
        return list.stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
        return c != null && c > 0;
    }

    public int insert(User u) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (username, password, name, phone, address, age) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, u.getUsername());
            ps.setString(2, u.getPassword());
            ps.setString(3, u.getName());
            ps.setString(4, u.getPhone());
            ps.setString(5, u.getAddress());
            ps.setInt(6, u.getAge());
            return ps;
        }, kh);
        return kh.getKey().intValue();
    }

    public void updatePassword(int id, String hashedPassword) {
        jdbc.update("UPDATE users SET password = ? WHERE id = ?", hashedPassword, id);
    }

    public List<User> findAll() {
        return jdbc.query("SELECT * FROM users ORDER BY id", RowMappers.USER);
    }

    public void setActive(int id, boolean active) {
        jdbc.update("UPDATE users SET is_active = ? WHERE id = ?", active, id);
    }

    public void deleteById(int id) {
        jdbc.update("DELETE FROM users WHERE id = ?", id);
    }
}
