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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingServiceTest {

    private FakeEmployeeRepository employeeRepo;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        employeeRepo = new FakeEmployeeRepository();
        bookingService = new BookingService(null, employeeRepo, null, null);
    }

    @Test
    void dateOnlyQueryReturnsEmployeesWithAtLeastOneFreePeriod() {
        LocalDate date = LocalDate.now().plusDays(2);
        Employee employee = availableEmployee(8);
        employeeRepo.dateEmployees = List.of(employee);

        List<Employee> result = bookingService.availableEmployees(date, null);

        assertEquals(List.of(employee), result);
        assertEquals(date, employeeRepo.queriedDate);
        assertEquals(date.getDayOfWeek().getValue(), employeeRepo.queriedWeekday);
    }

    @Test
    void selectedServiceIsForwardedToAvailableEmployeeQuery() {
        LocalDate date = LocalDate.now().plusDays(2);

        bookingService.availableEmployees(date, null, 5);

        assertEquals(5, employeeRepo.queriedServiceId);
    }

    @Test
    void legacyPeriodQueryStillNormalizesPeriodOrder() {
        LocalDate date = LocalDate.now().plusDays(3);

        bookingService.availableEmployees(date, List.of("EVENING", "MORNING"));

        assertEquals(List.of("MORNING", "EVENING"), employeeRepo.queriedPeriods);
    }

    @Test
    void selectedEmployeeQueryReturnsRemainingPeriodsInRepositoryOrder() {
        LocalDate date = LocalDate.now().plusDays(1);
        employeeRepo.employee = availableEmployee(12);
        employeeRepo.availablePeriods = List.of("NOON", "AFTERNOON");

        List<String> result = bookingService.availableTimePeriodsForEmployee(12, date);

        assertEquals(List.of("NOON", "AFTERNOON"), result);
        assertEquals(12, employeeRepo.queriedEmployeeId);
        assertEquals(date, employeeRepo.queriedDate);
    }

    @Test
    void availableEmployeePageClampsPageAndUsesFiveRowOffset() {
        LocalDate date = LocalDate.now().plusDays(2);
        Employee employee = availableEmployee(16);
        employeeRepo.availableEmployeeTotal = 12;
        employeeRepo.availableEmployeePage = List.of(employee);

        PageResult<Employee> result = bookingService.availableEmployeesPage(
                date, null, 5, 99, 5);

        assertEquals(3, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(12, result.getTotal());
        assertEquals(List.of(employee), result.getItems());
        assertEquals(5, employeeRepo.queriedLimit);
        assertEquals(10, employeeRepo.queriedOffset);
    }

    @Test
    void bookingIsRejectedWhenGlobalRecordLimitIsReached() {
        User user = new User();
        user.setId(3);
        user.setUsername("elder");
        Employee employee = availableEmployee(12);
        employeeRepo.employee = employee;
        ServiceItem serviceItem = new ServiceItem();
        serviceItem.setId(5);
        serviceItem.setName("陪诊服务");
        serviceItem.setReferencePrice(BigDecimal.valueOf(120));
        serviceItem.setActive(true);
        FakeAppointmentRepository appointmentRepo = new FakeAppointmentRepository();
        appointmentRepo.totalForUpdate = 9999;
        BookingService limitedService = new BookingService(
                new FakeUserRepository(user), employeeRepo,
                new FakeServiceRepository(serviceItem), appointmentRepo);
        BookingRequest request = new BookingRequest();
        request.setEmployeeId(12);
        request.setServiceId(5);
        request.setAppointmentDate(LocalDate.now().plusDays(1));
        request.setTimePeriods(List.of("MORNING"));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> limitedService.book("elder", request));

        assertEquals("系统预约记录已达到9999条上限，请联系管理员清理历史记录", error.getMessage());
        assertFalse(appointmentRepo.insertCalled);
    }

    @Test
    void userAppointmentPageClampsPageAndUsesFiveRowOffset() {
        User user = new User();
        user.setId(3);
        user.setUsername("elder");
        Appointment appointment = new Appointment();
        appointment.setId(21);
        FakeAppointmentRepository appointmentRepo = new FakeAppointmentRepository();
        appointmentRepo.userAppointmentTotal = 12;
        appointmentRepo.userAppointmentPage = List.of(appointment);
        BookingService pagedService = new BookingService(
                new FakeUserRepository(user), employeeRepo, null, appointmentRepo);

        PageResult<Appointment> result = pagedService.myAppointmentsAsUserPage(
                "elder", "PENDING", 99, 5);

        assertEquals(3, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(12, result.getTotal());
        assertEquals(List.of(appointment), result.getItems());
        assertEquals(5, appointmentRepo.queriedLimit);
        assertEquals(10, appointmentRepo.queriedOffset);
        assertEquals("PENDING", appointmentRepo.queriedStatus);
    }

    @Test
    void employeeTaskPageClampsPageAndUsesFiveRowOffset() {
        Employee employee = availableEmployee(12);
        employee.setUsername("worker");
        employeeRepo.employee = employee;
        Appointment appointment = new Appointment();
        appointment.setId(31);
        FakeAppointmentRepository appointmentRepo = new FakeAppointmentRepository();
        appointmentRepo.employeeAppointmentTotal = 11;
        appointmentRepo.employeeAppointmentPage = List.of(appointment);
        BookingService pagedService = new BookingService(
                null, employeeRepo, null, appointmentRepo);

        PageResult<Appointment> result = pagedService.myAppointmentsAsEmployeePage(
                "worker", "COMPLETED", 99, 5);

        assertEquals(3, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(11, result.getTotal());
        assertEquals(List.of(appointment), result.getItems());
        assertEquals(5, appointmentRepo.queriedLimit);
        assertEquals(10, appointmentRepo.queriedOffset);
        assertEquals("COMPLETED", appointmentRepo.queriedStatus);
    }

    private Employee availableEmployee(int id) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setActive(true);
        employee.setWorking(true);
        employee.setQuizPassed(true);
        return employee;
    }

    private static class FakeEmployeeRepository extends EmployeeRepository {
        private Employee employee;
        private List<Employee> dateEmployees = List.of();
        private List<String> availablePeriods = List.of();
        private Integer queriedEmployeeId;
        private LocalDate queriedDate;
        private Integer queriedWeekday;
        private List<String> queriedPeriods;
        private Integer queriedServiceId;
        private long availableEmployeeTotal;
        private List<Employee> availableEmployeePage = List.of();
        private int queriedLimit;
        private int queriedOffset;

        FakeEmployeeRepository() {
            super(null);
        }

        @Override
        public Optional<Employee> findByUsername(String username) {
            return Optional.ofNullable(employee);
        }

        @Override
        public List<Employee> findAvailableOnDate(LocalDate date, int weekday) {
            queriedDate = date;
            queriedWeekday = weekday;
            return dateEmployees;
        }

        @Override
        public List<Employee> findAvailableOnDate(LocalDate date, int weekday, Integer serviceId) {
            queriedServiceId = serviceId;
            return findAvailableOnDate(date, weekday);
        }

        @Override
        public long countAvailableOnDate(LocalDate date, int weekday, Integer serviceId) {
            queriedDate = date;
            queriedWeekday = weekday;
            queriedServiceId = serviceId;
            return availableEmployeeTotal;
        }

        @Override
        public List<Employee> findAvailableOnDatePage(LocalDate date, int weekday, Integer serviceId,
                                                       int limit, int offset) {
            queriedDate = date;
            queriedWeekday = weekday;
            queriedServiceId = serviceId;
            queriedLimit = limit;
            queriedOffset = offset;
            return availableEmployeePage;
        }

        @Override
        public List<Employee> findAvailable(LocalDate date, int weekday, List<String> timePeriods) {
            queriedDate = date;
            queriedWeekday = weekday;
            queriedPeriods = timePeriods;
            return List.of();
        }

        @Override
        public List<Employee> findAvailable(LocalDate date, int weekday, List<String> timePeriods,
                                            Integer serviceId) {
            queriedServiceId = serviceId;
            return findAvailable(date, weekday, timePeriods);
        }

        @Override
        public Optional<Employee> findById(int id) {
            return Optional.ofNullable(employee);
        }

        @Override
        public List<String> findAvailableTimePeriods(int employeeId, LocalDate date, int weekday) {
            queriedEmployeeId = employeeId;
            queriedDate = date;
            queriedWeekday = weekday;
            return availablePeriods;
        }

        @Override
        public boolean hasServiceCapability(int employeeId, int serviceId) {
            return true;
        }

        @Override
        public boolean hasAvailability(int employeeId, int weekday, List<String> timePeriods) {
            return true;
        }
    }

    private static class FakeUserRepository extends UserRepository {
        private final User user;

        FakeUserRepository(User user) {
            super(null);
            this.user = user;
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.of(user);
        }
    }

    private static class FakeServiceRepository extends ServiceRepository {
        private final ServiceItem serviceItem;

        FakeServiceRepository(ServiceItem serviceItem) {
            super(null);
            this.serviceItem = serviceItem;
        }

        @Override
        public Optional<ServiceItem> findById(int id) {
            return Optional.of(serviceItem);
        }
    }

    private static class FakeAppointmentRepository extends AppointmentRepository {
        private long totalForUpdate;
        private boolean insertCalled;
        private long userAppointmentTotal;
        private List<Appointment> userAppointmentPage = List.of();
        private int queriedLimit;
        private int queriedOffset;
        private String queriedStatus;
        private long employeeAppointmentTotal;
        private List<Appointment> employeeAppointmentPage = List.of();

        FakeAppointmentRepository() {
            super(null);
        }

        @Override
        public void lockBookingOwners(int userId, int employeeId) {
        }

        @Override
        public boolean existsActiveForUserSlots(int userId, LocalDate date, List<String> timePeriods) {
            return false;
        }

        @Override
        public boolean existsActiveForEmployeeSlots(int employeeId, LocalDate date, List<String> timePeriods) {
            return false;
        }

        @Override
        public long countAllForUpdate() {
            return totalForUpdate;
        }

        @Override
        public long countByUserId(int userId, String status) {
            queriedStatus = status;
            return userAppointmentTotal;
        }

        @Override
        public List<Appointment> findByUserIdPage(int userId, String status, int limit, int offset) {
            queriedStatus = status;
            queriedLimit = limit;
            queriedOffset = offset;
            return userAppointmentPage;
        }

        @Override
        public long countByEmployeeId(int employeeId, String status) {
            queriedStatus = status;
            return employeeAppointmentTotal;
        }

        @Override
        public List<Appointment> findByEmployeeIdPage(int employeeId, String status,
                                                       int limit, int offset) {
            queriedStatus = status;
            queriedLimit = limit;
            queriedOffset = offset;
            return employeeAppointmentPage;
        }

        @Override
        public int insert(int userId, int employeeId, int serviceId, LocalDate date) {
            insertCalled = true;
            return 1;
        }
    }
}
