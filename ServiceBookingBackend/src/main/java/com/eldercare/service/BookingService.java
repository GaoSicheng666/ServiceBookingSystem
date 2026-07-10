package com.eldercare.service;

import com.eldercare.common.BusinessException;
import com.eldercare.dto.BookingRequest;
import com.eldercare.entity.Appointment;
import com.eldercare.entity.Employee;
import com.eldercare.entity.ServiceItem;
import com.eldercare.entity.User;
import com.eldercare.repository.AppointmentRepository;
import com.eldercare.repository.EmployeeRepository;
import com.eldercare.repository.ServiceRepository;
import com.eldercare.repository.UserRepository;
import com.eldercare.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** 预约业务:下单(事务+行锁防并发)、查询、取消、完成。 */
@Service
public class BookingService {

    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final ServiceRepository serviceRepo;
    private final AppointmentRepository appointmentRepo;

    public BookingService(UserRepository userRepo, EmployeeRepository employeeRepo,
                          ServiceRepository serviceRepo, AppointmentRepository appointmentRepo) {
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.serviceRepo = serviceRepo;
        this.appointmentRepo = appointmentRepo;
    }

    /** 可预约的服务项目(上架的)。 */
    public List<ServiceItem> availableServices() {
        return serviceRepo.findActive();
    }

    /** 指定日期可预约的员工。 */
    public List<Employee> availableEmployees(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return employeeRepo.findAvailable(date);
    }

    /**
     * 下预约单。整个方法在一个事务里,用 FOR UPDATE 行锁防止并发重复预约:
     * - 同一员工同一天只能被预约一次(未取消的)
     * - 同一用户同一天只能下一单(未取消的)
     */
    @Transactional
    public void book(String username, BookingRequest req) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        Employee emp = employeeRepo.findById(req.getEmployeeId()).orElse(null);
        if (emp == null || !emp.isActive()) {
            throw new BusinessException(2002, "员工不存在或已停用");
        }
        if (!emp.isWorking()) {
            throw new BusinessException(2003, "该员工当前不接单");
        }

        ServiceItem service = serviceRepo.findById(req.getServiceId())
                .orElseThrow(() -> new BusinessException(2004, "服务项目不存在"));
        if (!service.isActive()) {
            throw new BusinessException(2004, "该服务项目已下架");
        }

        LocalDate date = req.getAppointmentDate();
        if (date.isBefore(LocalDate.now())) {
            throw new BusinessException(2005, "不能预约过去的日期");
        }

        // 行锁 + 冲突校验
        if (appointmentRepo.existsActiveForUserOnDate(user.getId(), date)) {
            throw new BusinessException(2006, "您在该日期已有预约,请先取消");
        }
        if (appointmentRepo.existsActiveForEmployeeOnDate(emp.getId(), date)) {
            throw new BusinessException(2001, "该员工在所选日期已被预约");
        }

        appointmentRepo.insert(user.getId(), emp.getId(), service.getId(), date);
    }

    /** 用户查看自己的预约与历史。 */
    public List<Appointment> myAppointmentsAsUser(String username, String status) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return appointmentRepo.findByUserId(user.getId(), status);
    }

    /** 员工查看分配给自己的预约。 */
    public List<Appointment> myAppointmentsAsEmployee(String username, String status) {
        Employee emp = employeeRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("员工不存在"));
        return appointmentRepo.findByEmployeeId(emp.getId(), status);
    }

    /** 用户取消预约(仅能取消自己的、且尚未完成的)。 */
    @Transactional
    public void cancelByUser(String username, int appointmentId) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        Appointment a = appointmentRepo.findById(appointmentId);
        if (a == null || !a.getUserId().equals(user.getId())) {
            throw new BusinessException(2007, "预约不存在或无权操作");
        }
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessException(2008, "该预约当前状态不可取消");
        }
        appointmentRepo.updateStatus(appointmentId, "CANCELLED");
    }

    /** 员工取消分配给自己的预约。 */
    @Transactional
    public void cancelByEmployee(String username, int appointmentId) {
        Employee emp = employeeRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("员工不存在"));
        Appointment a = appointmentRepo.findById(appointmentId);
        if (a == null || !a.getEmployeeId().equals(emp.getId())) {
            throw new BusinessException(2007, "预约不存在或无权操作");
        }
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessException(2008, "该预约当前状态不可取消");
        }
        appointmentRepo.updateStatus(appointmentId, "CANCELLED");
    }

    /** 员工标记预约为已完成。 */
    @Transactional
    public void completeByEmployee(String username, int appointmentId) {
        Employee emp = employeeRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("员工不存在"));
        Appointment a = appointmentRepo.findById(appointmentId);
        if (a == null || !a.getEmployeeId().equals(emp.getId())) {
            throw new BusinessException(2007, "预约不存在或无权操作");
        }
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessException(2008, "只有进行中的预约才能标记完成");
        }
        appointmentRepo.updateStatus(appointmentId, "COMPLETED");
    }
}
