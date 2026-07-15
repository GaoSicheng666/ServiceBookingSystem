package com.eldercare.dto;

import java.util.List;

/** 护工星期一至星期日的可工作时段，例如 1_MORNING 表示星期一 06:00-10:00。 */
public class AvailabilityRequest {
    private List<String> slots;

    public List<String> getSlots() {
        return slots;
    }

    public void setSlots(List<String> slots) {
        this.slots = slots;
    }
}
