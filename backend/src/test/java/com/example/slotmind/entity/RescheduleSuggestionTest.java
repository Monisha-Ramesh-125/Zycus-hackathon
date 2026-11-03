package com.example.slotmind.entity;

import com.example.slotmind.exception.InvalidStateException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RescheduleSuggestionTest {

    private RescheduleSuggestion suggestion() {
        var appt = EntityFixtures.confirmedAppointment();
        var slot = EntityFixtures.slot("S2", EntityFixtures.provider(ProviderStatus.AVAILABLE), SlotStatus.OPEN);
        return new RescheduleSuggestion("SUG-T1", appt, slot, null, 0.9, "reason", "rule", TriggerReason.INITIAL);
    }

    @Test
    void startsPending() {
        assertTrue(suggestion().isPending());
    }

    @Test
    void acceptRecordsDecision() {
        var s = suggestion();
        s.accept("patient agreed");
        assertEquals(SuggestionStatus.ACCEPTED, s.getStatus());
        assertEquals("patient agreed", s.getDecisionNote());
        assertNotNull(s.getDecidedAt());
    }

    @Test
    void rejectRecordsDecision() {
        var s = suggestion();
        s.reject("wants mornings");
        assertEquals(SuggestionStatus.REJECTED, s.getStatus());
        assertNotNull(s.getDecidedAt());
    }

    @Test
    void secondDecisionThrows() {
        var s = suggestion();
        s.accept(null);
        assertThrows(InvalidStateException.class, () -> s.reject(null));
        assertThrows(InvalidStateException.class, () -> s.accept(null));
    }
}
