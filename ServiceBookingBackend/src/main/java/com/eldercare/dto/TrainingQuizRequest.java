package com.eldercare.dto;

import java.util.Map;

/** 护工培训答题提交内容，键为 q1-q4，值为选项 A-C。 */
public class TrainingQuizRequest {
    private Map<String, String> answers;

    public Map<String, String> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<String, String> answers) {
        this.answers = answers;
    }
}
