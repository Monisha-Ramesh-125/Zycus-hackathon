package com.example.slotmind.dto.response;

import com.example.slotmind.entity.Slot;
import com.example.slotmind.entity.SlotStatus;

import java.time.LocalDateTime;
import java.util.List;

public record SlotView(String id, String providerId, String providerName, LocalDateTime startTime,
                       int durationMinutes, SlotStatus status, String statusReason) {

    public static SlotView from(Slot s) {
        return new SlotView(s.getId(), s.getProvider().getId(), s.getProvider().getName(), s.getStartTime(),
                s.getDurationMinutes(), s.getStatus(), s.getStatusReason());
    }

    public static List<SlotView> from(List<Slot> slots) {
        return slots.stream().map(SlotView::from).toList();
    }
}
