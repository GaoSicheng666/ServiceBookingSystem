package com.eldercare.service;

import com.eldercare.entity.Employee;
import com.eldercare.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        FakeEmployeeRepository() {
            super(null);
        }

        @Override
        public List<Employee> findAvailableOnDate(LocalDate date, int weekday) {
            queriedDate = date;
            queriedWeekday = weekday;
            return dateEmployees;
        }

        @Override
        public List<Employee> findAvailable(LocalDate date, int weekday, List<String> timePeriods) {
            queriedDate = date;
            queriedWeekday = weekday;
            queriedPeriods = timePeriods;
            return List.of();
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
    }
}
