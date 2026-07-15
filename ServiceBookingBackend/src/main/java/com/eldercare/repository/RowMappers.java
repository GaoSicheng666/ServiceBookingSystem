package com.eldercare.repository;

import com.eldercare.entity.*;
import org.springframework.jdbc.core.RowMapper;

/** 集中存放各实体的 RowMapper(ResultSet -> 实体)。 */
public final class RowMappers {

    private RowMappers() {}

    public static final RowMapper<User> USER = (rs, i) -> {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setName(rs.getString("name"));
        u.setPhone(rs.getString("phone"));
        u.setAddress(rs.getString("address"));
        u.setAge(rs.getInt("age"));
        u.setActive(rs.getBoolean("is_active"));
        return u;
    };

    public static final RowMapper<Employee> EMPLOYEE = (rs, i) -> {
        Employee e = new Employee();
        e.setId(rs.getInt("id"));
        e.setUsername(rs.getString("username"));
        e.setPassword(rs.getString("password"));
        e.setName(rs.getString("name"));
        e.setAge(rs.getInt("age"));
        e.setPhone(rs.getString("phone"));
        e.setSalary(rs.getBigDecimal("salary"));
        e.setWorking(rs.getBoolean("is_working"));
        e.setActive(rs.getBoolean("is_active"));
        e.setTrainingCompleted(rs.getBoolean("training_completed"));
        e.setQuizPassed(rs.getBoolean("quiz_passed"));
        e.setQuizScore(rs.getInt("quiz_score"));
        return e;
    };

    public static final RowMapper<Admin> ADMIN = (rs, i) -> {
        Admin a = new Admin();
        a.setId(rs.getInt("id"));
        a.setUsername(rs.getString("username"));
        a.setPassword(rs.getString("password"));
        a.setName(rs.getString("name"));
        return a;
    };

    public static final RowMapper<ServiceItem> SERVICE = (rs, i) -> {
        ServiceItem s = new ServiceItem();
        s.setId(rs.getInt("id"));
        s.setName(rs.getString("name"));
        s.setDescription(rs.getString("description"));
        s.setReferencePrice(rs.getBigDecimal("reference_price"));
        s.setActive(rs.getBoolean("is_active"));
        return s;
    };
}
