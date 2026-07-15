package com.eldercare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 护工取消预约时提交的原因。 */
public class CancelAppointmentRequest {

    @NotBlank(message = "请选择或填写取消原因")
    @Size(max = 200, message = "取消原因不能超过200个字")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
