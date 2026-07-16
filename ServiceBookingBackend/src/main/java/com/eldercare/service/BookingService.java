package com.eldercare.service;

import com.eldercare.common.BusinessException;
import com.eldercare.dto.BookingRequest;
import com.eldercare.dto.PageResult;
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
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;

/** 预约业务:下单(事务+行锁防并发)、查询、取消、完成。 */
@Service
public class BookingService {

    private static final int APPOINTMENT_RECORD_LIMIT = 9999;

    private static final List<String> TIME_PERIOD_ORDER =
            List.of("MORNING", "NOON", "AFTERNOON", "EVENING");

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

    /**
     * 查询指定日期可预约的员工。
     * 未指定时段时返回当天至少还有一个空闲时段的员工；旧客户端传入时段时保留原筛选方式。
     */
    public List<Employee> availableEmployees(LocalDate date, List<String> timePeriods) {
        return availableEmployees(date, timePeriods, null);
    }

    public List<Employee> availableEmployees(LocalDate date, List<String> timePeriods,
                                             Integer serviceId) {
        date = validateQueryDate(date);
        if (timePeriods == null || timePeriods.isEmpty()) {
            return employeeRepo.findAvailableOnDate(
                    date, date.getDayOfWeek().getValue(), serviceId);
        }
        List<String> normalizedPeriods = normalizeTimePeriods(timePeriods);
        return employeeRepo.findAvailable(
                date, date.getDayOfWeek().getValue(), normalizedPeriods, serviceId);
    }

    /** 查询指定员工在某天仍可被老人选择的具体时段。 */
    public List<String> availableTimePeriodsForEmployee(int employeeId, LocalDate date) {
        LocalDate queryDate = validateQueryDate(date);
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new BusinessException(2002, "员工不存在或已停用"));
        if (!employee.isActive()) {
            throw new BusinessException(2002, "员工不存在或已停用");
        }
        if (!employee.isWorking() || !employee.isQuizPassed()) {
            throw new BusinessException(2003, "该员工当前不可预约");
        }
        return employeeRepo.findAvailableTimePeriods(
                employeeId, queryDate, queryDate.getDayOfWeek().getValue());
    }

    /** 统一处理可预约查询日期，未传日期时默认今天。 */
    private LocalDate validateQueryDate(LocalDate date) {
        LocalDate queryDate = date == null ? LocalDate.now() : date;
        if (queryDate.isBefore(LocalDate.now())) {
            throw new BusinessException(2005, "不能查询过去日期的可预约员工");
        }
        return queryDate;
    }

    /**
     * 下预约单。整个方法在一个事务里,用 FOR UPDATE 行锁防止并发重复预约:
     * - 同一员工同一天的同一时段只能被预约一次(未取消的)
     * - 同一用户同一天的同一时段只能下一单(未取消的)
     */
    @Transactional
    public void book(String username, BookingRequest req) {
        List<String> timePeriods = normalizeTimePeriods(req.getTimePeriods());
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        Employee emp = employeeRepo.findById(req.getEmployeeId()).orElse(null);
        if (emp == null || !emp.isActive()) {
            throw new BusinessException(2002, "员工不存在或已停用");
        }
        if (!emp.isWorking()) {
            throw new BusinessException(2003, "该员工当前不接单");
        }
        if (!emp.isQuizPassed()) {
            throw new BusinessException(2003, "该员工尚未通过岗前培训");
        }

        ServiceItem service = serviceRepo.findById(req.getServiceId())
                .orElseThrow(() -> new BusinessException(2004, "服务项目不存在"));
        if (!service.isActive()) {
            throw new BusinessException(2004, "该服务项目已下架");
        }
        if (!employeeRepo.hasServiceCapability(emp.getId(), service.getId())) {
            throw new BusinessException(2003, "该员工未选择此项服务能力");
        }

        LocalDate date = req.getAppointmentDate();
        if (date.isBefore(LocalDate.now())) {
            throw new BusinessException(2005, "不能预约过去的日期");
        }

        int weekday = date.getDayOfWeek().getValue();
        if (!employeeRepo.hasAvailability(emp.getId(), weekday, timePeriods)) {
            throw new BusinessException(2003, "该员工在所选日期和时段不可预约");
        }

        // 固定顺序锁住老人和护工后再检查时段，防止并发请求同时通过冲突校验。
        appointmentRepo.lockBookingOwners(user.getId(), emp.getId());
        if (appointmentRepo.existsActiveForUserSlots(user.getId(), date, timePeriods)) {
            throw new BusinessException(2006, "您在所选日期和时段已有预约");
        }
        if (appointmentRepo.existsActiveForEmployeeSlots(emp.getId(), date, timePeriods)) {
            throw new BusinessException(2001, "该员工在所选日期和时段已被预约");
        }
        if (appointmentRepo.countAllForUpdate() >= APPOINTMENT_RECORD_LIMIT) {
            throw new BusinessException(2009, "系统预约记录已达到9999条上限，请联系管理员清理历史记录");
        }

        int appointmentId = appointmentRepo.insert(
                user.getId(), emp.getId(), service.getId(), date);
        appointmentRepo.insertTimeSlots(appointmentId, timePeriods);

        BigDecimal unitPrice = service.getReferencePrice() == null
                ? BigDecimal.ZERO : service.getReferencePrice();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(timePeriods.size()));
        appointmentRepo.insertBilling(
                appointmentId, unitPrice, timePeriods.size(), totalAmount);
    }

    /** 去重、校验并按一天内的先后顺序排列前端提交的时段。 */
    private List<String> normalizeTimePeriods(List<String> timePeriods) {
        if (timePeriods == null || timePeriods.isEmpty()) {
            throw new BusinessException(2005, "请至少选择一个服务时段");
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String period : timePeriods) {
            String normalized = period == null ? "" : period.trim().toUpperCase();
            if (!TIME_PERIOD_ORDER.contains(normalized)) {
                throw new BusinessException(2005, "存在无效的服务时段");
            }
            unique.add(normalized);
        }

        return TIME_PERIOD_ORDER.stream()
                .filter(unique::contains)
                .toList();
    }

    /** 用户查看自己的预约与历史。 */
    public List<Appointment> myAppointmentsAsUser(String username, String status) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return appointmentRepo.findByUserId(user.getId(), status);
    }

    /** 老人预约记录服务端分页，前端固定每页 5 条，接口最大允许 20 条。 */
    public PageResult<Appointment> myAppointmentsAsUserPage(String username, String status,
                                                            int requestedPage, int requestedSize) {
        if (requestedSize < 1 || requestedSize > 20) {
            throw new BusinessException("每页预约记录数必须在1至20之间");
        }
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        long total = appointmentRepo.countByUserId(user.getId(), status);
        int totalPages = Math.max(1, (int) Math.ceil((double) total / requestedSize));
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        List<Appointment> items = appointmentRepo.findByUserIdPage(
                user.getId(), status, requestedSize, (page - 1) * requestedSize);
        return new PageResult<>(items, page, requestedSize, total, APPOINTMENT_RECORD_LIMIT);
    }

    /** 员工查看分配给自己的预约。 */
    public List<Appointment> myAppointmentsAsEmployee(String username, String status) {
        Employee emp = employeeRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("员工不存在"));
        return appointmentRepo.findByEmployeeId(emp.getId(), status);
    }

    /** 护工累计服务收入，只统计已经完成的预约。 */
    public BigDecimal completedEarningsAsEmployee(String username) {
        Employee employee = employeeRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("员工不存在"));
        return appointmentRepo.sumCompletedEarnings(employee.getId());
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
        appointmentRepo.recordCancellation(appointmentId, "USER", "老人主动取消");
    }

    /** 员工取消分配给自己的预约。 */
    @Transactional
    public void cancelByEmployee(String username, int appointmentId, String reason) {
        Employee emp = employeeRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("员工不存在"));
        Appointment a = appointmentRepo.findById(appointmentId);
        if (a == null || !a.getEmployeeId().equals(emp.getId())) {
            throw new BusinessException(2007, "预约不存在或无权操作");
        }
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessException(2008, "该预约当前状态不可取消");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new BusinessException(2008, "请选择或填写取消原因");
        }
        if (normalizedReason.length() > 200) {
            throw new BusinessException(2008, "取消原因不能超过200个字");
        }
        appointmentRepo.updateStatus(appointmentId, "CANCELLED");
        appointmentRepo.recordCancellation(appointmentId, "EMPLOYEE", normalizedReason);
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
