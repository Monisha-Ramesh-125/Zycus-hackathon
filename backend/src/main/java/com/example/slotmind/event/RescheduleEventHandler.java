package com.example.slotmind.event;

import com.example.slotmind.entity.AppointmentStatus;
import com.example.slotmind.entity.TriggerReason;
import com.example.slotmind.exception.NoSlotAvailableException;
import com.example.slotmind.repository.AppointmentRepository;
import com.example.slotmind.service.SuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * The re-schedule loop. Runs after the status change has committed, on a background thread, so the
 * endpoint that changed the status has already returned. The two triggers differ only in which
 * appointments they affect; both go through the same processing step.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RescheduleEventHandler {

    private static final List<AppointmentStatus> AFFECTED =
            List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.RESCHEDULED);

    private final AppointmentRepository appointmentRepository;
    private final SuggestionService suggestionService;

    @Async("rescheduleExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProviderOffline(ProviderWentOfflineEvent event) {
        process(appointmentRepository.findIdsByProvider(event.providerId(), AFFECTED),
                TriggerReason.PROVIDER_OFFLINE, "provider " + event.providerId());
    }

    @Async("rescheduleExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSlotCancelled(SlotCancelledEvent event) {
        process(appointmentRepository.findIdsBySlot(event.slotId(), AFFECTED),
                TriggerReason.SLOT_CANCELLED, "slot " + event.slotId());
    }

    /** One appointment failing must not stop the others, so each runs in its own transaction and try block. */
    private void process(List<String> appointmentIds, TriggerReason trigger, String source) {
        int suggested = 0;
        int withoutSlot = 0;
        int failed = 0;
        for (String appointmentId : appointmentIds) {
            try {
                suggestionService.createSuggestion(appointmentId, trigger);
                suggested++;
            } catch (NoSlotAvailableException e) {
                withoutSlot++;
                flagForStaff(appointmentId);
            } catch (RuntimeException e) {
                failed++;
                log.error("Re-schedule failed for appointment {} ({})", appointmentId, trigger, e);
            }
        }
        log.info("Reschedule loop finished for {}: {} affected, {} suggested, {} without slot, {} failed",
                source, appointmentIds.size(), suggested, withoutSlot, failed);
    }

    /** No slot to offer: still surface the appointment so staff see it and can retry later. */
    private void flagForStaff(String appointmentId) {
        try {
            suggestionService.markNeedsReschedule(appointmentId);
        } catch (RuntimeException e) {
            log.error("Could not flag appointment {} as needing a reschedule", appointmentId, e);
        }
    }
}
