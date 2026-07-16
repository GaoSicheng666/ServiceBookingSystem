package com.eldercare.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** 护工保存本人可胜任服务项目。 */
public class ServiceCapabilityRequest {

    @Size(max = 100, message = "可胜任服务项目数量过多")
    private List<Integer> serviceIds;

    public List<Integer> getServiceIds() { return serviceIds; }
    public void setServiceIds(List<Integer> serviceIds) { this.serviceIds = serviceIds; }
}
