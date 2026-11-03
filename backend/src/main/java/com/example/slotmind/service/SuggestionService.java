package com.example.slotmind.service;

import com.example.slotmind.dto.request.SuggestionDecisionModel.Decision;
import com.example.slotmind.dto.response.SuggestionView;
import com.example.slotmind.engine.Recommendation;
import com.example.slotmind.engine.SchedulingEngine;
import com.example.slotmind.entity.*;
import com.example.slotmind.exception.InvalidStateException;
import com.example.slotmind.exception.NoSlotAvailableException;
import com.example.slotmind.exception.NotFoundException;
import com.example.slotmind.repository.AppointmentRepository;
import com.example.slotmind.repository.ProviderRepository;
import com.example.slotmind.repository.SlotRepository;
import com.example.slotmind.repository.SuggestionRepository;
import com.example.slotmind.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final ProviderRepository providerRepository;
    private final SchedulingEngine schedulingEngine;

    /**
     * Shared by the HTTP endpoint and the async loop. A pending suggestion for the same appointment and
     * trigger is returned as is, never duplicated. Nothing is booked: the slot stays open until a
     * human accepts the suggestion.
     */
    @Transactional(noRollbackFor = NoSlotAvailableException.class)
    public SuggestionView createSuggestion(String appointmentId, TriggerReason trigger) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment", appointmentId));
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new InvalidStateException("Appointment " + appointmentId + " is already completed");
        }

        var existing = suggestionRepository.findFirstByAppointmentIdAndTriggerReasonAndStatus(
                appointmentId, trigger, SuggestionStatus.PENDING);
        if (existing.isPresent()) {
            Slot offered = existing.get().getRecommendedSlot();
            if (offered.isOpen() && !offered.getProvider().isOffline()) {
                return SuggestionView.from(existing.get());
            }
            // Someone else took the slot meanwhile: close the dead suggestion and propose a fresh one.
            existing.get().closeBySystem("Slot no longer available");
        }

        SchedulingEngine.Result result = schedulingEngine.recommend(appointment, trigger);
        if (result.recommendations().isEmpty()) {
            throw new NoSlotAvailableException(appointmentId);
        }
        Recommendation best = result.recommendations().get(0);

        Slot slot = slotRepository.findById(best.slotId())
                .orElseThrow(() -> new NotFoundException("Slot", best.slotId()));
        Provider alternate = best.alternateProviderId() == null ? null
                : providerRepository.findById(best.alternateProviderId())
                        .orElseThrow(() -> new NotFoundException("Provider", best.alternateProviderId()));

        appointment.markReschedulePending();
        RescheduleSuggestion suggestion = new RescheduleSuggestion(IdGenerator.next("SUG"), appointment, slot,
                alternate, best.confidence(), best.reasoning(), result.strategy(), trigger);
        return SuggestionView.from(suggestionRepository.save(suggestion));
    }

    /**
     * Accepting books the new slot, frees the old one and moves the appointment in one transaction.
     * The slot's own rules refuse a slot that is no longer open; its version check catches two accepts
     * running at the same instant, and a database deadlock between them is the same kind of lost race.
     * Either way the retry re-reads the slot, so the loser gets a clear SLOT_UNAVAILABLE instead of a
     * vague conflict.
     */
    @Retryable(includes = {ObjectOptimisticLockingFailureException.class, CannotAcquireLockException.class},
            maxRetries = 3, delay = 50)
    @Transactional
    public SuggestionView decide(String suggestionId, Decision decision, String note) {
        RescheduleSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new NotFoundException("Suggestion", suggestionId));
        if (!suggestion.isPending()) {
            throw new InvalidStateException("Suggestion " + suggestionId + " is already " + suggestion.getStatus());
        }
        if (decision == Decision.ACCEPT) {
            accept(suggestion, note);
        } else {
            suggestion.reject(note);
        }
        suggestionRepository.flush();
        return SuggestionView.from(suggestion);
    }

    private void accept(RescheduleSuggestion suggestion, String note) {
        Appointment appointment = suggestion.getAppointment();
        if (appointment.getStatus() != AppointmentStatus.RESCHEDULE_PENDING) {
            throw new InvalidStateException("Appointment " + appointment.getId() + " is "
                    + appointment.getStatus() + ", not waiting for a reschedule");
        }
        Slot newSlot = suggestion.getRecommendedSlot();
        Slot oldSlot = appointment.getSlot();

        newSlot.book();
        oldSlot.release();
        appointment.moveTo(newSlot);
        suggestion.accept(note);

        suggestionRepository.findByAppointmentIdAndStatus(appointment.getId(), SuggestionStatus.PENDING).stream()
                .filter(other -> !other.getId().equals(suggestion.getId()))
                .forEach(other -> other.closeBySystem("Superseded by " + suggestion.getId()));
    }

    /** Used by the loop when no slot could be found: the appointment still shows up for staff. */
    @Transactional
    public void markNeedsReschedule(String appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment", appointmentId));
        appointment.markReschedulePending();
    }

    @Transactional(readOnly = true)
    public List<SuggestionView> list(SuggestionStatus status) {
        return SuggestionView.from(suggestionRepository.search(status));
    }
}
