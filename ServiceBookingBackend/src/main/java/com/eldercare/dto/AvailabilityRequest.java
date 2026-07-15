package com.eldercare.dto;

import java.util.List;

/** 护工每周可工作时段，例如 1_MORNING 表示星期一上午。 */
public class AvailabilityRequest {
    private List<String> slots;

    public List<String> getSlots() {
        return slots;
    }

    public void setSlots(List<String> slots) {
        this.slots = slots;
    }
}
