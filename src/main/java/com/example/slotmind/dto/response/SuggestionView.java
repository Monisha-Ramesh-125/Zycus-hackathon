package com.example.slotmind.dto.response;

import com.example.slotmind.entity.RescheduleSuggestion;
import com.example.slotmind.entity.SuggestionStatus;
import com.example.slotmind.entity.TriggerReason;

import java.time.LocalDateTime;
import java.util.List;

public record SuggestionView(String id, String appointmentId, String patientName, SlotView recommendedSlot,
                             ProviderView alternateProvider, double confidence, String reasoning, String strategy,
                             SuggestionStatus status, TriggerReason triggerReason, LocalDateTime createdAt,
                             LocalDateTime decidedAt, String decisionNote) {

    public static SuggestionView from(RescheduleSuggestion s) {
        return new SuggestionView(s.getId(), s.getAppointment().getId(), s.getAppointment().getPatientName(),
                SlotView.from(s.getRecommendedSlot()),
                s.getAlternateProvider() == null ? null : ProviderView.from(s.getAlternateProvider()),
                s.getConfidence(), s.getReasoning(), s.getStrategy(), s.getStatus(), s.getTriggerReason(),
                s.getCreatedAt(), s.getDecidedAt(), s.getDecisionNote());
    }

    public static List<SuggestionView> from(List<RescheduleSuggestion> suggestions) {
        return suggestions.stream().map(SuggestionView::from).toList();
    }
}
