package com.eldercare.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 护工修改本人公开资料的请求。 */
public class EmployeeProfileRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不能超过50个字")
    private String name;

    @NotNull(message = "年龄不能为空")
    @Min(value = 18, message = "护工年龄不能小于18岁")
    @Max(value = 100, message = "年龄不能超过100岁")
    private Integer age;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "\\d{6,20}", message = "请输入6至20位数字联系电话")
    private String phone;

    @Size(max = 100, message = "擅长服务不能超过100个字")
    private String specialty;

    /** 管理员服务项目中由护工确认可以胜任的项目 ID。null 兼容旧客户端。 */
    @Size(max = 100, message = "可胜任服务项目数量过多")
    private List<Integer> serviceIds;

    @Size(max = 200, message = "从业经历不能超过200个字")
    private String experience;

    @Size(max = 500, message = "个人简介不能超过500个字")
    private String bio;

    /** null 表示不修改头像，空字符串表示清除头像。 */
    @Size(max = 800000, message = "头像数据过大，请重新选择图片")
    private String avatarData;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public List<Integer> getServiceIds() { return serviceIds; }
    public void setServiceIds(List<Integer> serviceIds) { this.serviceIds = serviceIds; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getAvatarData() { return avatarData; }
    public void setAvatarData(String avatarData) { this.avatarData = avatarData; }
}
