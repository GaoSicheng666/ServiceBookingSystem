package com.eldercare.service;

import com.eldercare.common.BusinessException;
import com.eldercare.entity.Employee;
import com.eldercare.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

/** 员工自助服务:查看自己信息、切换工作状态。 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepo;

    public EmployeeService(EmployeeRepository employeeRepo) {
        this.employeeRepo = employeeRepo;
    }

    public Employee getSelf(String username) {
        Employee e = employeeRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("员工不存在"));
        e.setPassword(null);     // 不返回密码
        return e;
    }

    public void updateWorkingStatus(String username, boolean working) {
        employeeRepo.updateWorkingStatus(username, working);
    }
}
