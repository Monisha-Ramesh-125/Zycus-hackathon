package com.example.slotmind.entity;

import java.time.LocalDateTime;

final class EntityFixtures {

    private EntityFixtures() {}

    static Provider provider(ProviderStatus status) {
        return new Provider("PRV-T1", "Dr. Test", Specialty.GENERAL, status);
    }

    static Slot slot(String id, Provider provider, SlotStatus status) {
        return new Slot(id, provider, LocalDateTime.now().plusHours(1), 30, status);
    }

    static Appointment confirmedAppointment() {
        return new Appointment("APT-T1", "Test Patient", AppointmentType.ROUTINE, null, null,
                slot("SLT-T1", provider(ProviderStatus.AVAILABLE), SlotStatus.BOOKED));
    }
}
