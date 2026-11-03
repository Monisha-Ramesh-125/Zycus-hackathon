package com.example.slotmind.entity;

import com.example.slotmind.exception.InvalidStateException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reschedule_suggestions", indexes = {
        @Index(name = "idx_suggestion_status", columnList = "status"),
        @Index(name = "idx_suggestion_appointment", columnList = "appointment_id")
})
@Getter
@NoArgsConstructor
public class RescheduleSuggestion extends BaseEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommended_slot_id")
    private Slot recommendedSlot;

    /** Set only when the suggestion moves the patient to a different provider. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alternate_provider_id")
    private Provider alternateProvider;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false, length = 1000)
    private String reasoning;

    /** Which strategy produced it, e.g. "rule" or "ai->rule-fallback". */
    @Column(nullable = false)
    private String strategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerReason triggerReason;

    private LocalDateTime decidedAt;

    @Column(length = 500)
    private String decisionNote;

    /** True when the system closed it (superseded or stale) rather than a person declining it. */
    private Boolean systemClosed;

    @Version
    private Long version;

    public RescheduleSuggestion(String id, Appointment appointment, Slot recommendedSlot, Provider alternateProvider,
                                double confidence, String reasoning, String strategy, TriggerReason triggerReason) {
        this.id = id;
        this.appointment = appointment;
        this.recommendedSlot = recommendedSlot;
        this.alternateProvider = alternateProvider;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.strategy = strategy;
        this.triggerReason = triggerReason;
        this.status = SuggestionStatus.PENDING;
    }

    public void accept(String note) {
        decide(SuggestionStatus.ACCEPTED, note);
    }

    public void reject(String note) {
        decide(SuggestionStatus.REJECTED, note);
    }

    public void closeBySystem(String note) {
        decide(SuggestionStatus.REJECTED, note);
        this.systemClosed = true;
    }

    public boolean isPending() {
        return status == SuggestionStatus.PENDING;
    }

    private void decide(SuggestionStatus outcome, String note) {
        if (status != SuggestionStatus.PENDING) {
            throw new InvalidStateException("Suggestion " + id + " is already " + status);
        }
        this.status = outcome;
        this.decisionNote = note;
        this.decidedAt = LocalDateTime.now();
    }
}
