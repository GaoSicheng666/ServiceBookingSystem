package com.eldercare.repository;

import com.eldercare.entity.ServiceItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 服务项目表数据访问(由管理员维护)。 */
@Repository
public class ServiceRepository {

    private final JdbcTemplate jdbc;

    public ServiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 上架的服务项目(给普通用户看)。 */
    public List<ServiceItem> findActive() {
        return jdbc.query("SELECT * FROM services WHERE is_active = TRUE ORDER BY id", RowMappers.SERVICE);
    }

    public List<ServiceItem> findActivePage(int limit, int offset) {
        return jdbc.query(
                "SELECT * FROM services WHERE is_active = TRUE ORDER BY id LIMIT ? OFFSET ?",
                RowMappers.SERVICE, limit, offset);
    }

    public long countActive() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM services WHERE is_active = TRUE", Long.class);
        return count == null ? 0 : count;
    }

    /** 所有服务项目(给管理员看)。 */
    public List<ServiceItem> findAll() {
        return jdbc.query("SELECT * FROM services ORDER BY id", RowMappers.SERVICE);
    }

    public List<ServiceItem> findPage(int limit, int offset) {
        return jdbc.query(
                "SELECT * FROM services ORDER BY id LIMIT ? OFFSET ?",
                RowMappers.SERVICE, limit, offset);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM services", Long.class);
        return count == null ? 0 : count;
    }

    public Optional<ServiceItem> findById(int id) {
        List<ServiceItem> list = jdbc.query("SELECT * FROM services WHERE id = ?", RowMappers.SERVICE, id);
        return list.stream().findFirst();
    }

    public void insert(ServiceItem s) {
        jdbc.update("INSERT INTO services (name, description, reference_price, is_active) VALUES (?, ?, ?, ?)",
                s.getName(), s.getDescription(), s.getReferencePrice(), s.isActive());
    }

    public void update(ServiceItem s) {
        jdbc.update("UPDATE services SET name = ?, description = ?, reference_price = ?, is_active = ? WHERE id = ?",
                s.getName(), s.getDescription(), s.getReferencePrice(), s.isActive(), s.getId());
    }

    public void deleteById(int id) {
        jdbc.update("DELETE FROM services WHERE id = ?", id);
    }
}
