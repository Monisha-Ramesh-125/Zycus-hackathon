package com.example.slotmind.engine;

import com.example.slotmind.entity.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.example.slotmind.engine.EngineFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class RuleBasedStrategyTest {

    RuleBasedStrategy strategy = new RuleBasedStrategy();

    Provider chen = provider("PRV-1", Specialty.GENERAL, ProviderStatus.AVAILABLE);
    Provider verma = provider("PRV-2", Specialty.GENERAL, ProviderStatus.AVAILABLE);
    Provider iyer = provider("PRV-3", Specialty.SPECIALIST, ProviderStatus.AVAILABLE);

    private List<Recommendation> run(Appointment appt, Slot... open) {
        return strategy.recommend(new SchedulingContext(appt, List.of(open), List.of(chen, verma, iyer),
                TriggerReason.INITIAL));
    }

    @Test
    void sameProviderComesFirstWithHighConfidenceAndNoAlternate() {
        var recs = run(appointment(chen, AppointmentType.ROUTINE),
                slot("A", verma, 60, 30, SlotStatus.OPEN), slot("B", chen, 90, 30, SlotStatus.OPEN));
        assertEquals("B", recs.get(0).slotId());
        assertNull(recs.get(0).alternateProviderId());
        assertEquals(0.9, recs.get(0).confidence());
    }

    @Test
    void sameSpecialtyOtherProviderIsSecondTierWithAlternate() {
        var recs = run(appointment(chen, AppointmentType.ROUTINE), slot("A", verma, 60, 30, SlotStatus.OPEN));
        assertEquals(1, recs.size());
        assertEquals("PRV-2", recs.get(0).alternateProviderId());
        assertEquals(0.75, recs.get(0).confidence());
        assertFalse(recs.get(0).reasoning().isBlank());
    }

    @Test
    void otherSpecialtyOnlyOfferedForUrgent() {
        var routine = run(appointment(chen, AppointmentType.ROUTINE), slot("A", iyer, 60, 30, SlotStatus.OPEN));
        var urgent = run(appointment(chen, AppointmentType.URGENT), slot("A", iyer, 60, 30, SlotStatus.OPEN));
        assertTrue(routine.isEmpty());
        assertEquals(1, urgent.size());
        assertEquals(0.4, urgent.get(0).confidence());
        assertTrue(urgent.get(0).reasoning().contains("confirm"));
    }

    @Test
    void offlineProviderSlotsAreSkipped() {
        var offline = provider("PRV-9", Specialty.GENERAL, ProviderStatus.OFFLINE);
        assertTrue(run(appointment(chen, AppointmentType.ROUTINE), slot("A", offline, 60, 30, SlotStatus.OPEN)).isEmpty());
    }

    @Test
    void pastShorterNonOpenAndCurrentSlotsAreSkipped() {
        var appt = appointment(chen, AppointmentType.ROUTINE);
        var recs = run(appt,
                slot("PAST", chen, -10, 30, SlotStatus.OPEN),
                slot("SHORT", chen, 60, 15, SlotStatus.OPEN),
                slot("BOOKED", chen, 70, 30, SlotStatus.BOOKED),
                slot("CUR", chen, 80, 30, SlotStatus.OPEN),
                slot("GOOD", chen, 90, 30, SlotStatus.OPEN));
        assertEquals(List.of("GOOD"), recs.stream().map(Recommendation::slotId).toList());
    }

    @Test
    void slotsInATierAreOrderedByTime() {
        var recs = run(appointment(chen, AppointmentType.ROUTINE),
                slot("LATE", chen, 200, 30, SlotStatus.OPEN), slot("EARLY", chen, 100, 30, SlotStatus.OPEN));
        assertEquals(List.of("EARLY", "LATE"), recs.stream().map(Recommendation::slotId).toList());
    }

    @Test
    void atMostThreeOptions() {
        var recs = run(appointment(chen, AppointmentType.ROUTINE),
                slot("1", chen, 60, 30, SlotStatus.OPEN), slot("2", chen, 70, 30, SlotStatus.OPEN),
                slot("3", chen, 80, 30, SlotStatus.OPEN), slot("4", chen, 90, 30, SlotStatus.OPEN));
        assertEquals(3, recs.size());
    }
}
