package com.eldercare.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.NotEmpty;

/** 下预约单请求：员工、服务项目、日期及当天一个或多个服务时段。 */
public class BookingRequest {

    @NotNull(message = "请选择员工")
    private Integer employeeId;

    @NotNull(message = "请选择服务项目")
    private Integer serviceId;

    @NotNull(message = "请选择预约日期")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate appointmentDate;

    @NotEmpty(message = "请至少选择一个服务时段")
    private List<String> timePeriods;

    public Integer getEmployeeId() { return employeeId; }
    public void setEmployeeId(Integer employeeId) { this.employeeId = employeeId; }
    public Integer getServiceId() { return serviceId; }
    public void setServiceId(Integer serviceId) { this.serviceId = serviceId; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public List<String> getTimePeriods() { return timePeriods; }
    public void setTimePeriods(List<String> timePeriods) { this.timePeriods = timePeriods; }
}
