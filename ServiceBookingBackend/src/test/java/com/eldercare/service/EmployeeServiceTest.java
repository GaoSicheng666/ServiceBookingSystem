package com.eldercare.service;

import com.eldercare.common.BusinessException;
import com.eldercare.dto.AvailabilityRequest;
import com.eldercare.dto.TrainingQuizRequest;
import com.eldercare.entity.Employee;
import com.eldercare.repository.EmployeeRepository;
import com.eldercare.repository.EmployeeTrainingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeServiceTest {

    private FakeEmployeeRepository employeeRepo;
    private FakeTrainingRepository trainingRepo;
    private EmployeeService employeeService;
    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(7);
        employee.setUsername("worker");
        employeeRepo = new FakeEmployeeRepository(employee);
        trainingRepo = new FakeTrainingRepository();
        employeeService = new EmployeeService(employeeRepo, trainingRepo);
    }

    @Test
    void quizPassesWhenAtLeastThreeAnswersAreCorrect() {
        employee.setTrainingCompleted(true);
        TrainingQuizRequest request = new TrainingQuizRequest();
        request.setAnswers(Map.of("q1", "B", "q2", "C", "q3", "A", "q4", "A"));

        Map<String, Object> result = employeeService.submitQuiz("worker", request);

        assertEquals(3, result.get("score"));
        assertEquals(true, result.get("passed"));
        assertEquals(7, trainingRepo.savedEmployeeId);
        assertEquals(3, trainingRepo.savedScore);
        assertTrue(trainingRepo.savedPassed);
    }

    @Test
    void quizCannotBeSubmittedBeforeTraining() {
        TrainingQuizRequest request = new TrainingQuizRequest();
        request.setAnswers(Map.of("q1", "B", "q2", "C", "q3", "A", "q4", "B"));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> employeeService.submitQuiz("worker", request));

        assertEquals("请先完成培训学习", error.getMessage());
        assertNull(trainingRepo.savedEmployeeId);
    }

    @Test
    void employeeCannotStartTakingOrdersBeforePassingQuiz() {
        employee.setQuizPassed(false);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> employeeService.updateWorkingStatus("worker", true));

        assertEquals("请先完成培训并通过答题", error.getMessage());
        assertFalse(employeeRepo.statusUpdated);
    }

    @Test
    void invalidAvailabilitySlotIsRejected() {
        employee.setQuizPassed(true);
        AvailabilityRequest request = new AvailabilityRequest();
        request.setSlots(List.of("6_MORNING"));

        assertThrows(
                BusinessException.class,
                () -> employeeService.updateAvailability("worker", request));
        assertNull(trainingRepo.savedSlots);
    }

    private static class FakeEmployeeRepository extends EmployeeRepository {
        private final Employee employee;
        private boolean statusUpdated;

        FakeEmployeeRepository(Employee employee) {
            super(null);
            this.employee = employee;
        }

        @Override
        public Optional<Employee> findByUsername(String username) {
            return Optional.of(employee);
        }

        @Override
        public void updateWorkingStatus(String username, boolean working) {
            statusUpdated = true;
        }
    }

    private static class FakeTrainingRepository extends EmployeeTrainingRepository {
        private Integer savedEmployeeId;
        private Integer savedScore;
        private boolean savedPassed;
        private List<String> savedSlots;

        FakeTrainingRepository() {
            super(null);
        }

        @Override
        public void saveQuizResult(int employeeId, int score, boolean passed) {
            savedEmployeeId = employeeId;
            savedScore = score;
            savedPassed = passed;
        }

        @Override
        public void replaceAvailability(int employeeId, List<String> slots) {
            savedSlots = slots;
        }
    }
}
