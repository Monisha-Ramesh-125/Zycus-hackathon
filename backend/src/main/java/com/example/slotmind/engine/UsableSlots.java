package com.example.slotmind.engine;

import com.example.slotmind.entity.Slot;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** The slots any strategy may offer for an appointment, nearest first. */
public final class UsableSlots {

    private UsableSlots() {}

    /**
     * Open, in the future, with an online provider, not the appointment's current slot, not a slot staff
     * already declined for it, and at least as long as the current one.
     */
    public static List<Slot> of(SchedulingContext ctx) {
        Slot current = ctx.appointment().getSlot();
        LocalDateTime now = LocalDateTime.now();
        return ctx.openSlots().stream()
                .filter(Slot::isOpen)
                .filter(s -> !s.getId().equals(current.getId()))
                .filter(s -> !ctx.rejectedSlotIds().contains(s.getId()))
                .filter(s -> s.getStartTime().isAfter(now))
                .filter(s -> !s.getProvider().isOffline())
                .filter(s -> s.getDurationMinutes() >= current.getDurationMinutes())
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();
    }
}
