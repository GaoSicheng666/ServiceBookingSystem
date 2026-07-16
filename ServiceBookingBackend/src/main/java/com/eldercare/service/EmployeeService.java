package com.eldercare.service;

import com.eldercare.common.BusinessException;
import com.eldercare.dto.AvailabilityRequest;
import com.eldercare.dto.EmployeeProfileRequest;
import com.eldercare.dto.TrainingQuizRequest;
import com.eldercare.entity.Employee;
import com.eldercare.entity.ServiceItem;
import com.eldercare.repository.EmployeeRepository;
import com.eldercare.repository.EmployeeTrainingRepository;
import com.eldercare.repository.ServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/** 护工自助服务：培训、答题、可工作时段、资料和接单状态。 */
@Service
public class EmployeeService {

    private static final Map<String, String> QUIZ_ANSWERS = Map.of(
            "q1", "B",
            "q2", "C",
            "q3", "A",
            "q4", "B"
    );
    private static final Pattern SLOT_PATTERN =
            Pattern.compile("^[1-7]_(MORNING|NOON|AFTERNOON|EVENING)$");

    private final EmployeeRepository employeeRepo;
    private final EmployeeTrainingRepository trainingRepo;
    private final ServiceRepository serviceRepo;

    public EmployeeService(EmployeeRepository employeeRepo,
                           EmployeeTrainingRepository trainingRepo,
                           ServiceRepository serviceRepo) {
        this.employeeRepo = employeeRepo;
        this.trainingRepo = trainingRepo;
        this.serviceRepo = serviceRepo;
    }

    public Employee getSelf(String username) {
        Employee employee = requireEmployee(username);
        employee.setPassword(null);
        return employee;
    }

    public void completeTraining(String username) {
        Employee employee = requireEmployee(username);
        trainingRepo.completeTraining(employee.getId());
    }

    /** 四题答对至少三题视为通过，未通过时允许重新作答。 */
    public Map<String, Object> submitQuiz(String username, TrainingQuizRequest request) {
        Employee employee = requireEmployee(username);
        if (!employee.isTrainingCompleted()) {
            throw new BusinessException("请先完成培训学习");
        }
        Map<String, String> answers = request == null ? null : request.getAnswers();
        if (answers == null || !answers.keySet().containsAll(QUIZ_ANSWERS.keySet())) {
            throw new BusinessException("请完成全部题目后再提交");
        }

        int score = 0;
        for (Map.Entry<String, String> answer : QUIZ_ANSWERS.entrySet()) {
            if (answer.getValue().equalsIgnoreCase(answers.get(answer.getKey()))) {
                score++;
            }
        }
        boolean passed = score >= 3;
        trainingRepo.saveQuizResult(employee.getId(), score, passed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("total", QUIZ_ANSWERS.size());
        result.put("passed", passed);
        return result;
    }

    public List<String> getAvailability(String username) {
        Employee employee = requireEmployee(username);
        if (!employee.isQuizPassed()) {
            throw new BusinessException("通过培训答题后才能设置可工作时间");
        }
        return trainingRepo.findAvailability(employee.getId());
    }

    @Transactional
    public void updateAvailability(String username, AvailabilityRequest request) {
        Employee employee = requireEmployee(username);
        if (!employee.isQuizPassed()) {
            throw new BusinessException("通过培训答题后才能设置可工作时间");
        }

        List<String> requested = request == null || request.getSlots() == null
                ? List.of() : request.getSlots();
        Set<String> slots = Set.copyOf(requested);
        if (slots.size() > 28 || slots.stream().anyMatch(slot -> !SLOT_PATTERN.matcher(slot).matches())) {
            throw new BusinessException("可工作时间格式不正确");
        }
        trainingRepo.replaceAvailability(employee.getId(), List.copyOf(slots));
    }

    public void updateWorkingStatus(String username, boolean working) {
        Employee employee = requireEmployee(username);
        if (working && !employee.isQuizPassed()) {
            throw new BusinessException("请先完成培训并通过答题");
        }
        if (working && !employeeRepo.hasAnyServiceCapability(employee.getId())) {
            throw new BusinessException("请先在个人信息中选择至少一项可胜任服务");
        }
        employeeRepo.updateWorkingStatus(username, working);
    }

    /** 当前护工已经选择的管理员服务项目 ID。 */
    public List<Integer> getServiceCapabilities(String username) {
        Employee employee = requireEmployee(username);
        return employeeRepo.findServiceCapabilityIds(employee.getId());
    }

    /** 修改护工本人基本资料和老人端可见的服务介绍。 */
    @Transactional
    public void updateProfile(String username, EmployeeProfileRequest request) {
        Employee employee = requireEmployee(username);
        String avatarData = request.getAvatarData();
        if (avatarData != null && !avatarData.isBlank()
                && !avatarData.matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=\\r\\n]+$")) {
            throw new BusinessException("头像格式不正确，请选择 JPG、PNG 或 WebP 图片");
        }

        List<Integer> serviceIds = normalizeServiceIds(request.getServiceIds());
        String specialty = normalizeOptional(request.getSpecialty());
        if (serviceIds != null) {
            List<ServiceItem> activeServices = serviceRepo.findActive();
            Set<Integer> selectedIds = new LinkedHashSet<>(serviceIds);
            List<ServiceItem> selectedServices = activeServices.stream()
                    .filter(item -> selectedIds.contains(item.getId()))
                    .toList();
            if (selectedServices.size() != selectedIds.size()) {
                throw new BusinessException("存在已删除或已下架的服务项目，请重新选择");
            }
            specialty = selectedServices.stream()
                    .map(ServiceItem::getName)
                    .collect(Collectors.joining("、"));
            serviceIds = selectedServices.stream().map(ServiceItem::getId).toList();
        }

        employeeRepo.updateProfileBasics(
                employee.getId(), request.getName().trim(), request.getAge(), request.getPhone().trim());
        employeeRepo.upsertProfile(
                employee.getId(), avatarData,
                specialty,
                normalizeOptional(request.getExperience()),
                normalizeOptional(request.getBio()));
        if (serviceIds != null) {
            employeeRepo.replaceServiceCapabilities(employee.getId(), serviceIds);
        }
    }

    private List<Integer> normalizeServiceIds(List<Integer> requested) {
        if (requested == null) return null;
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        for (Integer serviceId : requested) {
            if (serviceId == null || serviceId <= 0) {
                throw new BusinessException("服务项目选择不正确");
            }
            ids.add(serviceId);
        }
        return List.copyOf(ids);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Employee requireEmployee(String username) {
        return employeeRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("护工不存在"));
    }
}
