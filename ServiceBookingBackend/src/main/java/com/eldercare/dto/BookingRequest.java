package com.eldercare.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

/** 下预约单请求:选员工 + 服务项目 + 日期(精确到天)。 */
public class BookingRequest {

    @NotNull(message = "请选择员工")
    private Integer employeeId;

    @NotNull(message = "请选择服务项目")
    private Integer serviceId;

    @NotNull(message = "请选择预约日期")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate appointmentDate;

    public Integer getEmployeeId() { return employeeId; }
    public void setEmployeeId(Integer employeeId) { this.employeeId = employeeId; }
    public Integer getServiceId() { return serviceId; }
    public void setServiceId(Integer serviceId) { this.serviceId = serviceId; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
}
