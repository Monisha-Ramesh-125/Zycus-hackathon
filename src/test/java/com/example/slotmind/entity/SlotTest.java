package com.example.slotmind.entity;

import com.example.slotmind.exception.InvalidStateException;
import com.example.slotmind.exception.SlotUnavailableException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlotTest {

    @Test
    void bookFromOpenWorks() {
        var slot = EntityFixtures.slot("S1", EntityFixtures.provider(ProviderStatus.AVAILABLE), SlotStatus.OPEN);
        slot.book();
        assertEquals(SlotStatus.BOOKED, slot.getStatus());
    }

    @Test
    void bookFromBookedOrCancelledThrows() {
        var p = EntityFixtures.provider(ProviderStatus.AVAILABLE);
        assertThrows(SlotUnavailableException.class, () -> EntityFixtures.slot("S1", p, SlotStatus.BOOKED).book());
        assertThrows(SlotUnavailableException.class, () -> EntityFixtures.slot("S2", p, SlotStatus.CANCELLED).book());
    }

    @Test
    void bookWithOfflineProviderThrows() {
        var slot = EntityFixtures.slot("S1", EntityFixtures.provider(ProviderStatus.OFFLINE), SlotStatus.OPEN);
        assertThrows(InvalidStateException.class, slot::book);
        assertEquals(SlotStatus.OPEN, slot.getStatus());
    }

    @Test
    void releaseFreesOnlyBookedSlots() {
        var p = EntityFixtures.provider(ProviderStatus.AVAILABLE);
        var booked = EntityFixtures.slot("S1", p, SlotStatus.BOOKED);
        var cancelled = EntityFixtures.slot("S2", p, SlotStatus.CANCELLED);
        booked.release();
        cancelled.release();
        assertEquals(SlotStatus.OPEN, booked.getStatus());
        assertEquals(SlotStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void cancelStoresReason() {
        var slot = EntityFixtures.slot("S1", EntityFixtures.provider(ProviderStatus.AVAILABLE), SlotStatus.BOOKED);
        slot.cancel("room flooded");
        assertEquals(SlotStatus.CANCELLED, slot.getStatus());
        assertEquals("room flooded", slot.getStatusReason());
    }
}
