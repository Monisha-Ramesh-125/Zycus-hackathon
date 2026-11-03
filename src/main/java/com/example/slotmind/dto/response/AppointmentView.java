package com.example.slotmind.dto.response;

import com.example.slotmind.entity.Appointment;
import com.example.slotmind.entity.AppointmentStatus;
import com.example.slotmind.entity.AppointmentType;

import java.time.LocalDateTime;
import java.util.List;

public record AppointmentView(String id, String patientName, AppointmentType type, AppointmentStatus status,
                              String patientPhone, String notes, int rescheduleCount, LocalDateTime createdAt,
                              LocalDateTime modifiedAt, ProviderView provider, SlotView slot,
                              SuggestionView pendingSuggestion) {

    public static AppointmentView from(Appointment a) {
        return from(a, null);
    }

    /** pendingSuggestion is filled in for appointments waiting for a reschedule, so the UI needs one call. */
    public static AppointmentView from(Appointment a, SuggestionView pendingSuggestion) {
        return new AppointmentView(a.getId(), a.getPatientName(), a.getType(), a.getStatus(), a.getPatientPhone(),
                a.getNotes(), a.getRescheduleCount(), a.getCreatedAt(), a.getModifiedAt(),
                ProviderView.from(a.getProvider()), SlotView.from(a.getSlot()), pendingSuggestion);
    }

}
