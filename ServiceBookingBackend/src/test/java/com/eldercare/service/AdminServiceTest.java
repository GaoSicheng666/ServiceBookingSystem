package com.eldercare.service;

import com.eldercare.common.BusinessException;
import com.eldercare.dto.PageResult;
import com.eldercare.entity.Appointment;
import com.eldercare.entity.ServiceItem;
import com.eldercare.repository.AppointmentRepository;
import com.eldercare.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminServiceTest {

    private FakeAppointmentRepository appointmentRepo;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        appointmentRepo = new FakeAppointmentRepository();
        adminService = new AdminService(null, null, null, null, appointmentRepo);
    }

    @Test
    void pageRequestBeyondLastPageIsClamped() {
        appointmentRepo.total = 23;
        appointmentRepo.pageItems = List.of(appointment(21), appointment(22), appointment(23));

        PageResult<Appointment> result = adminService.pageAppointments("", 99, 10);

        assertEquals(3, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(23, result.getTotal());
        assertEquals(20, appointmentRepo.requestedOffset);
        assertEquals(10, appointmentRepo.requestedLimit);
        assertEquals(9999, result.getMaxTotal());
    }

    @Test
    void pageSizeCannotExceedBackendLimit() {
        assertThrows(BusinessException.class, () -> adminService.pageAppointments(null, 1, 51));
    }

    @Test
    void servicePageClampsPageAndUsesFiveRowOffset() {
        FakeServiceRepository serviceRepo = new FakeServiceRepository();
        ServiceItem serviceItem = new ServiceItem();
        serviceItem.setId(12);
        serviceRepo.total = 12;
        serviceRepo.pageItems = List.of(serviceItem);
        AdminService service = new AdminService(
                serviceRepo, null, null, null, appointmentRepo);

        PageResult<ServiceItem> result = service.pageServices(99, 5);

        assertEquals(3, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(12, result.getTotal());
        assertEquals(5, serviceRepo.requestedLimit);
        assertEquals(10, serviceRepo.requestedOffset);
    }

    @Test
    void deletingMissingAppointmentReturnsBusinessError() {
        appointmentRepo.deleteResult = 0;

        assertThrows(BusinessException.class, () -> adminService.deleteAppointment(404));
    }

    @Test
    void batchDeleteRemovesDistinctSelectedAppointments() {
        appointmentRepo.batchDeleteResult = 2;

        int deleted = adminService.deleteAppointments(List.of(3, 3, 8));

        assertEquals(2, deleted);
        assertEquals(List.of(3, 8), appointmentRepo.batchDeletedIds);
    }

    private Appointment appointment(int id) {
        Appointment appointment = new Appointment();
        appointment.setId(id);
        return appointment;
    }

    private static class FakeAppointmentRepository extends AppointmentRepository {
        private long total;
        private List<Appointment> pageItems = List.of();
        private int requestedLimit;
        private int requestedOffset;
        private int deleteResult = 1;
        private int batchDeleteResult;
        private List<Integer> batchDeletedIds = List.of();

        FakeAppointmentRepository() {
            super(null);
        }

        @Override
        public long count(String status) {
            return total;
        }

        @Override
        public List<Appointment> findPage(String status, int limit, int offset) {
            requestedLimit = limit;
            requestedOffset = offset;
            return pageItems;
        }

        @Override
        public int deleteById(int id) {
            return deleteResult;
        }

        @Override
        public int deleteByIds(List<Integer> ids) {
            batchDeletedIds = ids;
            return batchDeleteResult;
        }
    }

    private static class FakeServiceRepository extends ServiceRepository {
        private long total;
        private List<ServiceItem> pageItems = List.of();
        private int requestedLimit;
        private int requestedOffset;

        FakeServiceRepository() {
            super(null);
        }

        @Override
        public long count() {
            return total;
        }

        @Override
        public List<ServiceItem> findPage(int limit, int offset) {
            requestedLimit = limit;
            requestedOffset = offset;
            return pageItems;
        }
    }
}
