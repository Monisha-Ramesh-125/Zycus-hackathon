package com.example.slotmind.ai;

import com.example.slotmind.engine.SchedulingContext;
import com.example.slotmind.entity.Appointment;
import com.example.slotmind.entity.Provider;
import com.example.slotmind.entity.Slot;
import com.example.slotmind.entity.TriggerReason;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds the two prompts. Only structured facts go in: never the patient's name, phone or free-text
 * notes (they are not needed, they are personal data, and free text is a prompt-injection route).
 */
@Component
public class SchedulingPromptFactory {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMM yyyy, HH:mm", Locale.ENGLISH);

    public String build(SchedulingContext ctx, List<Slot> candidates) {
        Appointment appointment = ctx.appointment();
        boolean providerUnavailable = ctx.trigger() == TriggerReason.PROVIDER_OFFLINE
                || appointment.getProvider().isOffline();
        StringBuilder sb = new StringBuilder();
        sb.append("You help a clinic front desk move a patient's appointment to a new time slot.\n\n");
        sb.append(situation(ctx, providerUnavailable)).append("\n\n");

        Provider current = appointment.getProvider();
        sb.append("APPOINTMENT\n");
        sb.append("- type: ").append(appointment.getType()).append(urgencyNote(appointment)).append('\n');
        sb.append("- current provider: ").append(describe(current)).append('\n');
        sb.append("- current time: ").append(WHEN.format(appointment.getSlot().getStartTime()))
                .append(", ").append(appointment.getSlot().getDurationMinutes()).append(" minutes\n");
        sb.append("- times already moved: ").append(appointment.getRescheduleCount()).append("\n");
        sb.append("- now: ").append(WHEN.format(LocalDateTime.now())).append("\n\n");

        sb.append("PROVIDERS\n");
        for (Provider p : ctx.providers()) {
            sb.append("- ").append(p.getId()).append(": ").append(describe(p)).append(", status ").append(p.getStatus()).append('\n');
        }

        sb.append("\nCANDIDATE SLOTS (choose only from these, nearest first)\n");
        for (Slot s : candidates) {
            sb.append("- slotId ").append(s.getId()).append(": provider ").append(s.getProvider().getId())
                    .append(" (").append(s.getProvider().getName()).append("), ")
                    .append(WHEN.format(s.getStartTime())).append(", ").append(s.getDurationMinutes()).append(" minutes\n");
        }

        sb.append("\n").append(providerUnavailable ? OFFLINE_GUIDANCE : CANCELLED_GUIDANCE).append("\n\n");
        sb.append(REPLY_FORMAT);
        return sb.toString();
    }

    private static String situation(SchedulingContext ctx, boolean providerUnavailable) {
        if (providerUnavailable) {
            return OFFLINE_SITUATION;
        }
        return ctx.trigger() == TriggerReason.SLOT_CANCELLED ? CANCELLED_SITUATION : REQUESTED_SITUATION;
    }

    private static String describe(Provider p) {
        return p.getName() + " (" + p.getSpecialty() + ")";
    }

    private static String urgencyNote(Appointment a) {
        return switch (a.getType()) {
            case URGENT -> " (needs to be seen as soon as possible)";
            case FOLLOW_UP -> " (continuity with the same doctor matters)";
            case ROUTINE -> " (not time critical)";
        };
    }

    private static final String OFFLINE_SITUATION =
            "SITUATION: the patient's provider has gone offline and cannot see patients. The patient needs a new slot, "
                    + "and it will probably be with a different provider.";

    private static final String CANCELLED_SITUATION =
            "SITUATION: the patient's appointment slot was cancelled, but their provider is still working. "
                    + "The patient needs a new slot.";

    private static final String REQUESTED_SITUATION =
            "SITUATION: a front-desk staff member asked to move this appointment to a different slot. The patient's "
                    + "provider is still working and nothing was cancelled. Do not say that anything was cancelled.";

    private static final String OFFLINE_GUIDANCE =
            "GUIDANCE: pick another provider with the same specialty if one is offered. Use a different specialty only for "
                    + "an urgent appointment, and then say so. For urgent appointments prefer the earliest slot. "
                    + "Set alternateProviderId to the provider who owns the chosen slot.";

    private static final String CANCELLED_GUIDANCE =
            "GUIDANCE: prefer the same provider's next suitable slot, especially for follow-ups. Use another provider "
                    + "only if the same provider has nothing sensible, preferring the same specialty. "
                    + "For urgent appointments prefer the earliest slot. "
                    + "Set alternateProviderId to null when the patient keeps their provider, otherwise to the new provider.";

    private static final String REPLY_FORMAT =
            "REPLY with a single JSON object and nothing else:\n"
                    + "{\"slotId\": \"<one slotId from the candidate list>\", \"alternateProviderId\": \"<provider id or null>\", "
                    + "\"confidence\": <number from 0 to 1>, \"reasoning\": \"<text>\"}\n"
                    + "The reasoning is read aloud by front-desk staff to the patient: one or two plain sentences saying "
                    + "which doctor, which day and time, and why. Phrase it as an offer the patient still has to agree to (\"we can offer\"), never as already done. "
                    + "Do not use ids, technical words, or mention a system or model.";
}
