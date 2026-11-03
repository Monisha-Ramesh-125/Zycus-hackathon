package com.example.slotmind.engine;

import com.example.slotmind.entity.*;

import java.time.LocalDateTime;

final class EngineFixtures {

    private EngineFixtures() {}

    static Provider provider(String id, Specialty specialty, ProviderStatus status) {
        return new Provider(id, "Dr. " + id, specialty, status);
    }

    static Slot slot(String id, Provider provider, int minutesFromNow, int duration, SlotStatus status) {
        return new Slot(id, provider, LocalDateTime.now().plusMinutes(minutesFromNow), duration, status);
    }

    /** A confirmed 30-minute appointment held by the given provider. */
    static Appointment appointment(Provider provider, AppointmentType type) {
        return new Appointment("APT-1", "Patient", type, null, null, slot("CUR", provider, 30, 30, SlotStatus.BOOKED));
    }
}
