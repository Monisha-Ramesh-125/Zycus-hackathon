package com.example.slotmind.entity;

import com.example.slotmind.exception.InvalidStateException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentTest {

    @Test
    void constructorRequiresBookedSlot() {
        var open = EntityFixtures.slot("S1", EntityFixtures.provider(ProviderStatus.AVAILABLE), SlotStatus.OPEN);
        assertThrows(InvalidStateException.class,
                () -> new Appointment("A1", "P", AppointmentType.ROUTINE, null, null, open));
    }

    @Test
    void newAppointmentIsConfirmedWithSlotProvider() {
        var appt = EntityFixtures.confirmedAppointment();
        assertEquals(AppointmentStatus.CONFIRMED, appt.getStatus());
        assertEquals(appt.getSlot().getProvider(), appt.getProvider());
        assertEquals(0, appt.getRescheduleCount());
    }

    @Test
    void fullLifecycleAndRescheduleCount() {
        var appt = EntityFixtures.confirmedAppointment();
        var newSlot = EntityFixtures.slot("S2", EntityFixtures.provider(ProviderStatus.AVAILABLE), SlotStatus.BOOKED);
        appt.markReschedulePending();
        appt.moveTo(newSlot);
        assertEquals(AppointmentStatus.RESCHEDULED, appt.getStatus());
        assertEquals(newSlot, appt.getSlot());
        assertEquals(1, appt.getRescheduleCount());
        appt.complete();
        assertEquals(AppointmentStatus.COMPLETED, appt.getStatus());
    }

    @Test
    void moveToOutsidePendingThrows() {
        var appt = EntityFixtures.confirmedAppointment();
        var newSlot = EntityFixtures.slot("S2", EntityFixtures.provider(ProviderStatus.AVAILABLE), SlotStatus.BOOKED);
        assertThrows(InvalidStateException.class, () -> appt.moveTo(newSlot));
    }

    @Test
    void completeFromPendingThrows() {
        var appt = EntityFixtures.confirmedAppointment();
        appt.markReschedulePending();
        assertThrows(InvalidStateException.class, appt::complete);
    }

    @Test
    void completedAppointmentCannotBeRescheduled() {
        var appt = EntityFixtures.confirmedAppointment();
        appt.complete();
        assertThrows(InvalidStateException.class, appt::markReschedulePending);
    }
}
