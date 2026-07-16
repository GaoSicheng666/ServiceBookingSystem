package com.eldercare.service;

import com.eldercare.common.BusinessException;
import com.eldercare.dto.AvailabilityRequest;
import com.eldercare.dto.EmployeeProfileRequest;
import com.eldercare.dto.ServiceCapabilityRequest;
import com.eldercare.dto.TrainingQuizRequest;
import com.eldercare.entity.Employee;
import com.eldercare.entity.ServiceItem;
import com.eldercare.repository.EmployeeRepository;
import com.eldercare.repository.EmployeeTrainingRepository;
import com.eldercare.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeServiceTest {

    private FakeEmployeeRepository employeeRepo;
    private FakeTrainingRepository trainingRepo;
    private FakeServiceRepository serviceRepo;
    private EmployeeService employeeService;
    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(7);
        employee.setUsername("worker");
        employeeRepo = new FakeEmployeeRepository(employee);
        trainingRepo = new FakeTrainingRepository();
        serviceRepo = new FakeServiceRepository();
        employeeService = new EmployeeService(employeeRepo, trainingRepo, serviceRepo);
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
        request.setSlots(List.of("8_MORNING"));

        assertThrows(
                BusinessException.class,
                () -> employeeService.updateAvailability("worker", request));
        assertNull(trainingRepo.savedSlots);
    }

    @Test
    void employeeProfileIsTrimmedAndSaved() {
        EmployeeProfileRequest request = new EmployeeProfileRequest();
        request.setName(" 王护工 ");
        request.setAge(38);
        request.setPhone("13800000000");
        request.setSpecialty(" 陪诊、日常照护 ");
        request.setExperience(" 五年居家照护经验 ");
        request.setBio(" 做事耐心 ");
        request.setAvatarData("data:image/webp;base64,AAAA");

        employeeService.updateProfile("worker", request);

        assertEquals("王护工", employeeRepo.savedName);
        assertEquals(38, employeeRepo.savedAge);
        assertEquals("13800000000", employeeRepo.savedPhone);
        assertEquals("陪诊、日常照护", employeeRepo.savedSpecialty);
        assertEquals("五年居家照护经验", employeeRepo.savedExperience);
        assertEquals("做事耐心", employeeRepo.savedBio);
        assertEquals("data:image/webp;base64,AAAA", employeeRepo.savedAvatarData);
    }

    @Test
    void invalidAvatarDataIsRejected() {
        EmployeeProfileRequest request = new EmployeeProfileRequest();
        request.setName("王护工");
        request.setAge(38);
        request.setPhone("13800000000");
        request.setAvatarData("data:text/html;base64,PHNjcmlwdD4=");

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> employeeService.updateProfile("worker", request));

        assertEquals("头像格式不正确，请选择 JPG、PNG 或 WebP 图片", error.getMessage());
        assertNull(employeeRepo.savedName);
    }

    @Test
    void employeeCapabilitiesUseActiveAdministratorServices() {
        serviceRepo.activeServices = List.of(service(1, "陪诊服务"), service(2, "助浴服务"));
        EmployeeProfileRequest request = new EmployeeProfileRequest();
        request.setName("王护工");
        request.setAge(38);
        request.setPhone("13800000000");
        request.setServiceIds(List.of(2, 1, 2));

        employeeService.updateProfile("worker", request);

        assertEquals(List.of(1, 2), employeeRepo.savedCapabilityIds);
        assertEquals("陪诊服务、助浴服务", employeeRepo.savedSpecialty);
    }

    @Test
    void employeeCanSaveCapabilitiesSeparatelyFromProfile() {
        employee.setQuizPassed(true);
        serviceRepo.activeServices = List.of(service(1, "陪诊服务"), service(2, "助浴服务"));
        ServiceCapabilityRequest request = new ServiceCapabilityRequest();
        request.setServiceIds(List.of(2, 1, 2));

        employeeService.updateServiceCapabilities("worker", request);

        assertEquals(List.of(1, 2), employeeRepo.savedCapabilityIds);
        assertEquals("陪诊服务、助浴服务", employeeRepo.savedSpecialty);
    }

    private ServiceItem service(int id, String name) {
        ServiceItem item = new ServiceItem();
        item.setId(id);
        item.setName(name);
        item.setActive(true);
        return item;
    }

    private static class FakeEmployeeRepository extends EmployeeRepository {
        private final Employee employee;
        private boolean statusUpdated;
        private String savedName;
        private Integer savedAge;
        private String savedPhone;
        private String savedAvatarData;
        private String savedSpecialty;
        private String savedExperience;
        private String savedBio;
        private List<Integer> savedCapabilityIds;

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

        @Override
        public void updateProfileBasics(int employeeId, String name, int age, String phone) {
            savedName = name;
            savedAge = age;
            savedPhone = phone;
        }

        @Override
        public void upsertProfile(int employeeId, String avatarData, String specialty,
                                  String experience, String bio) {
            savedAvatarData = avatarData;
            savedSpecialty = specialty;
            savedExperience = experience;
            savedBio = bio;
        }

        @Override
        public void replaceServiceCapabilities(int employeeId, List<Integer> serviceIds) {
            savedCapabilityIds = serviceIds;
        }

        @Override
        public void upsertSpecialty(int employeeId, String specialty) {
            savedSpecialty = specialty;
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

    private static class FakeServiceRepository extends ServiceRepository {
        private List<ServiceItem> activeServices = List.of();

        FakeServiceRepository() {
            super(null);
        }

        @Override
        public List<ServiceItem> findActive() {
            return activeServices;
        }
    }
}
