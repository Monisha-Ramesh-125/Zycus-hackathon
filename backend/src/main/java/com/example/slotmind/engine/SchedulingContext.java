package com.example.slotmind.engine;

import com.example.slotmind.entity.Appointment;
import com.example.slotmind.entity.Provider;
import com.example.slotmind.entity.Slot;
import com.example.slotmind.entity.TriggerReason;

import java.util.List;
import java.util.Set;

/**
 * Everything a strategy may look at. Strategies never touch repositories themselves.
 * rejectedSlotIds are slots staff already declined for this appointment and must not be offered again.
 */
public record SchedulingContext(Appointment appointment, List<Slot> openSlots, List<Provider> providers,
                                TriggerReason trigger, Set<String> rejectedSlotIds) {

    public SchedulingContext(Appointment appointment, List<Slot> openSlots, List<Provider> providers,
                             TriggerReason trigger) {
        this(appointment, openSlots, providers, trigger, Set.of());
    }
}
