package com.eldercare.service;

import com.eldercare.common.BusinessException;
import com.eldercare.dto.LoginRequest;
import com.eldercare.dto.LoginResponse;
import com.eldercare.dto.RegisterRequest;
import com.eldercare.entity.Admin;
import com.eldercare.entity.Employee;
import com.eldercare.entity.User;
import com.eldercare.repository.AdminRepository;
import com.eldercare.repository.EmployeeRepository;
import com.eldercare.repository.LoginSessionRepository;
import com.eldercare.repository.UserRepository;
import com.eldercare.security.CurrentUser;
import com.eldercare.security.JwtUtil;
import com.eldercare.util.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** 注册与登录服务。登录支持三角色统一入口,并自动把旧明文密码升级为哈希。 */
@Service
public class AuthService {

    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final AdminRepository adminRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginSessionRepository sessionRepo;

    public AuthService(UserRepository userRepo, EmployeeRepository employeeRepo, AdminRepository adminRepo,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       LoginSessionRepository sessionRepo) {
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.adminRepo = adminRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.sessionRepo = sessionRepo;
    }

    /** 注册用户或员工(管理员不开放注册)。 */
    public void register(RegisterRequest req) {
        String role = req.getRole();
        String username = req.getUsername().trim();

        // 用户名全局唯一(跨用户/员工/管理员)
        if (userRepo.existsByUsername(username)
                || employeeRepo.existsByUsername(username)
                || adminRepo.findByUsername(username).isPresent()) {
            throw new BusinessException(1001, "用户名已存在");
        }

        if (CurrentUser.ROLE_USER.equals(role)) {
            if (req.getAddress() == null || req.getAddress().isBlank()) {
                throw new BusinessException("请填写居住地址");
            }
            User u = new User();
            u.setUsername(username);
            u.setPassword(passwordEncoder.hash(req.getPassword()));
            u.setName(req.getName());
            u.setPhone(req.getPhone());
            u.setAddress(req.getAddress());
            u.setAge(req.getAge());
            userRepo.insert(u);
        } else if (CurrentUser.ROLE_EMPLOYEE.equals(role)) {
            if (req.getAge() < 18) {
                throw new BusinessException("护工年龄需满18岁");
            }
            Employee e = new Employee();
            e.setUsername(username);
            e.setPassword(passwordEncoder.hash(req.getPassword()));
            e.setName(req.getName());
            e.setAge(req.getAge());
            e.setPhone(req.getPhone());
            e.setWorking(false);
            employeeRepo.insert(e);
        } else {
            throw new BusinessException("非法的注册角色");
        }
    }

    /**
     * 登录:依次尝试 用户 -> 员工 -> 管理员。
     * 修复了原 JavaFX 版本"空用户名/密码未 return"的逻辑缺陷:这里校验由 DTO 保证非空,
     * 且任一匹配成功立即返回,不会继续误判。
     */
    public LoginResponse login(LoginRequest req) {
        String username = req.getUsername().trim();
        String password = req.getPassword();

        // 用户
        Optional<User> user = userRepo.findByUsername(username);
        if (user.isPresent() && passwordEncoder.matches(password, user.get().getPassword())) {
            User u = user.get();
            if (!u.isActive()) {
                throw new BusinessException(1003, "该账户已被禁用");
            }
            upgradeIfPlain(u.getPassword(), password, () -> userRepo.updatePassword(u.getId(), passwordEncoder.hash(password)));
            return createSession(username, CurrentUser.ROLE_USER, u.getName());
        }

        // 员工
        Optional<Employee> emp = employeeRepo.findByUsername(username);
        if (emp.isPresent() && passwordEncoder.matches(password, emp.get().getPassword())) {
            Employee e = emp.get();
            if (!e.isActive()) {
                throw new BusinessException(1003, "该账户已被禁用");
            }
            upgradeIfPlain(e.getPassword(), password, () -> employeeRepo.updatePassword(e.getId(), passwordEncoder.hash(password)));
            return createSession(username, CurrentUser.ROLE_EMPLOYEE, e.getName());
        }

        // 管理员
        Optional<Admin> admin = adminRepo.findByUsername(username);
        if (admin.isPresent() && passwordEncoder.matches(password, admin.get().getPassword())) {
            Admin a = admin.get();
            upgradeIfPlain(a.getPassword(), password, () -> adminRepo.updatePassword(a.getId(), passwordEncoder.hash(password)));
            return createSession(username, CurrentUser.ROLE_ADMIN, a.getName());
        }

        throw new BusinessException(1002, "用户名或密码错误");
    }

    /** 签发新会话前先覆盖数据库中的旧会话，实现后登录设备挤下线旧设备。 */
    private LoginResponse createSession(String username, String role, String name) {
        String sessionId = UUID.randomUUID().toString();
        sessionRepo.replace(username, role, sessionId);
        String token = jwtUtil.generate(username, role, sessionId);
        return new LoginResponse(token, role, name);
    }

    /** 若存储的是旧明文密码,登录成功后顺手升级为哈希。 */
    private void upgradeIfPlain(String stored, String rawPassword, Runnable upgrade) {
        if (!passwordEncoder.isHashed(stored)) {
            upgrade.run();
        }
    }
}
