package com.eldercare.entity;

import java.time.LocalDateTime;

/** 员工(护工)实体,对应 employees 表。 */
public class Employee {
    private Integer id;
    private String username;
    private String password;
    private String name;
    private Integer age;
    private String phone;
    private boolean working = false;
    private boolean active = true;
    private boolean trainingCompleted = false;
    private boolean quizPassed = false;
    private Integer quizScore = 0;
    private String avatarData;
    private String specialty;
    private String experience;
    private String bio;
    private LocalDateTime createdAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isWorking() { return working; }
    public void setWorking(boolean working) { this.working = working; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isTrainingCompleted() { return trainingCompleted; }
    public void setTrainingCompleted(boolean trainingCompleted) { this.trainingCompleted = trainingCompleted; }
    public boolean isQuizPassed() { return quizPassed; }
    public void setQuizPassed(boolean quizPassed) { this.quizPassed = quizPassed; }
    public Integer getQuizScore() { return quizScore; }
    public void setQuizScore(Integer quizScore) { this.quizScore = quizScore; }
    public String getAvatarData() { return avatarData; }
    public void setAvatarData(String avatarData) { this.avatarData = avatarData; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
