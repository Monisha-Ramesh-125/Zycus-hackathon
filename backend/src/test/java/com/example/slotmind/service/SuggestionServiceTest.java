package com.example.slotmind.service;

import com.example.slotmind.dto.response.SuggestionView;
import com.example.slotmind.engine.Recommendation;
import com.example.slotmind.engine.SchedulingEngine;
import com.example.slotmind.entity.*;
import com.example.slotmind.exception.InvalidStateException;
import com.example.slotmind.exception.NoSlotAvailableException;
import com.example.slotmind.exception.NotFoundException;
import com.example.slotmind.repository.AppointmentRepository;
import com.example.slotmind.repository.ProviderRepository;
import com.example.slotmind.repository.SlotRepository;
import com.example.slotmind.repository.SuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

    @Mock SuggestionRepository suggestionRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock SlotRepository slotRepository;
    @Mock ProviderRepository providerRepository;
    @Mock SchedulingEngine engine;
    @InjectMocks SuggestionService service;

    Provider chen, verma;
    Slot current, target;
    Appointment appointment;

    @BeforeEach
    void setUp() {
        chen = new Provider("PRV-1", "Dr. Chen", Specialty.GENERAL, ProviderStatus.AVAILABLE);
        verma = new Provider("PRV-2", "Dr. Verma", Specialty.GENERAL, ProviderStatus.AVAILABLE);
        current = new Slot("SLT-1", chen, LocalDateTime.now().plusHours(1), 30, SlotStatus.BOOKED);
        target = new Slot("SLT-2", verma, LocalDateTime.now().plusHours(2), 30, SlotStatus.OPEN);
        appointment = new Appointment("APT-1", "Jane", AppointmentType.ROUTINE, null, null, current);
    }

    @Test
    void createsPendingSuggestionAndMarksAppointmentPending() {
        when(appointmentRepository.findById("APT-1")).thenReturn(Optional.of(appointment));
        when(suggestionRepository.findFirstByAppointmentIdAndTriggerReasonAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(engine.recommend(appointment, TriggerReason.INITIAL)).thenReturn(new SchedulingEngine.Result("rule",
                List.of(new Recommendation("SLT-2", "PRV-2", 0.75, "Move to Dr. Verma"))));
        when(slotRepository.findById("SLT-2")).thenReturn(Optional.of(target));
        when(providerRepository.findById("PRV-2")).thenReturn(Optional.of(verma));
        when(suggestionRepository.save(any(RescheduleSuggestion.class))).thenAnswer(i -> i.getArgument(0));

        SuggestionView view = service.createSuggestion("APT-1", TriggerReason.INITIAL);

        assertEquals(SuggestionStatus.PENDING, view.status());
        assertEquals("rule", view.strategy());
        assertEquals("PRV-2", view.alternateProvider().id());
        assertEquals("SLT-2", view.recommendedSlot().id());
        assertEquals(AppointmentStatus.RESCHEDULE_PENDING, appointment.getStatus());
        assertEquals(SlotStatus.OPEN, target.getStatus());
        assertEquals("SLT-1", appointment.getSlot().getId());
    }

    @Test
    void existingPendingSuggestionIsReturnedNotDuplicated() {
        var existing = new RescheduleSuggestion("SUG-1", appointment, target, null, 0.9, "r", "rule", TriggerReason.INITIAL);
        when(appointmentRepository.findById("APT-1")).thenReturn(Optional.of(appointment));
        when(suggestionRepository.findFirstByAppointmentIdAndTriggerReasonAndStatus(
                "APT-1", TriggerReason.INITIAL, SuggestionStatus.PENDING)).thenReturn(Optional.of(existing));

        SuggestionView view = service.createSuggestion("APT-1", TriggerReason.INITIAL);

        assertEquals("SUG-1", view.id());
        verify(suggestionRepository, never()).save(any());
        verifyNoInteractions(engine);
    }

    @Test
    void noRecommendationIsConflictAndChangesNothing() {
        when(appointmentRepository.findById("APT-1")).thenReturn(Optional.of(appointment));
        when(suggestionRepository.findFirstByAppointmentIdAndTriggerReasonAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(engine.recommend(any(), any())).thenReturn(new SchedulingEngine.Result("rule", List.of()));

        assertThrows(NoSlotAvailableException.class, () -> service.createSuggestion("APT-1", TriggerReason.INITIAL));
        assertEquals(AppointmentStatus.CONFIRMED, appointment.getStatus());
        verify(suggestionRepository, never()).save(any());
    }

    @Test
    void completedAppointmentIsRejected() {
        appointment.complete();
        when(appointmentRepository.findById("APT-1")).thenReturn(Optional.of(appointment));
        assertThrows(InvalidStateException.class, () -> service.createSuggestion("APT-1", TriggerReason.INITIAL));
    }

    @Test
    void unknownAppointmentIsNotFound() {
        when(appointmentRepository.findById("X")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.createSuggestion("X", TriggerReason.INITIAL));
    }

    @Test
    void sameProviderSuggestionHasNoAlternate() {
        var sameProviderSlot = new Slot("SLT-3", chen, LocalDateTime.now().plusHours(3), 30, SlotStatus.OPEN);
        when(appointmentRepository.findById("APT-1")).thenReturn(Optional.of(appointment));
        when(suggestionRepository.findFirstByAppointmentIdAndTriggerReasonAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(engine.recommend(any(), any())).thenReturn(new SchedulingEngine.Result("rule",
                List.of(new Recommendation("SLT-3", null, 0.9, "same doctor"))));
        when(slotRepository.findById("SLT-3")).thenReturn(Optional.of(sameProviderSlot));
        when(suggestionRepository.save(any(RescheduleSuggestion.class))).thenAnswer(i -> i.getArgument(0));

        assertNull(service.createSuggestion("APT-1", TriggerReason.SLOT_CANCELLED).alternateProvider());
        verify(providerRepository, never()).findById(eq("PRV-2"));
    }
}
