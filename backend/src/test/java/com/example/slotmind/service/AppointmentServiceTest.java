package com.example.slotmind.service;

import com.example.slotmind.dto.request.BookAppointmentModel;
import com.example.slotmind.dto.response.AppointmentView;
import com.example.slotmind.entity.*;
import com.example.slotmind.exception.BadRequestException;
import com.example.slotmind.exception.NotFoundException;
import com.example.slotmind.exception.InvalidStateException;
import com.example.slotmind.exception.SlotUnavailableException;
import com.example.slotmind.repository.AppointmentRepository;
import com.example.slotmind.repository.ProviderRepository;
import com.example.slotmind.repository.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock ProviderRepository providerRepository;
    @Mock SlotRepository slotRepository;
    @InjectMocks AppointmentService service;

    Provider provider;

    @BeforeEach
    void setUp() {
        provider = new Provider("PRV-1", "Dr. A", Specialty.GENERAL, ProviderStatus.AVAILABLE);
    }

    private Slot slot(String id, Provider p, SlotStatus status) {
        return new Slot(id, p, LocalDateTime.now().plusHours(2), 30, status);
    }

    private BookAppointmentModel request(String providerId, String slotId) {
        return new BookAppointmentModel("Jane", AppointmentType.ROUTINE, providerId, slotId, "+1-555", "n");
    }

    @Test
    void booksOpenSlotAndMarksItBooked() {
        Slot slot = slot("SLT-1", provider, SlotStatus.OPEN);
        when(providerRepository.findById("PRV-1")).thenReturn(Optional.of(provider));
        when(slotRepository.findById("SLT-1")).thenReturn(Optional.of(slot));
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        AppointmentView view = service.book(request("PRV-1", "SLT-1"));

        assertEquals(AppointmentStatus.CONFIRMED, view.status());
        assertEquals(SlotStatus.BOOKED, slot.getStatus());
        assertTrue(view.id().startsWith("APT-"));
        assertEquals("PRV-1", view.provider().id());
    }

    @Test
    void unknownProviderIsNotFound() {
        when(providerRepository.findById("X")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.book(request("X", "SLT-1")));
    }

    @Test
    void unknownSlotIsNotFound() {
        when(providerRepository.findById("PRV-1")).thenReturn(Optional.of(provider));
        when(slotRepository.findById("X")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.book(request("PRV-1", "X")));
    }

    @Test
    void slotOfAnotherProviderIsBadRequest() {
        Provider other = new Provider("PRV-2", "Dr. B", Specialty.GENERAL, ProviderStatus.AVAILABLE);
        when(providerRepository.findById("PRV-1")).thenReturn(Optional.of(provider));
        when(slotRepository.findById("SLT-1")).thenReturn(Optional.of(slot("SLT-1", other, SlotStatus.OPEN)));
        assertThrows(BadRequestException.class, () -> service.book(request("PRV-1", "SLT-1")));
        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void bookedOrCancelledSlotIsConflict() {
        when(providerRepository.findById("PRV-1")).thenReturn(Optional.of(provider));
        when(slotRepository.findById("SLT-1")).thenReturn(Optional.of(slot("SLT-1", provider, SlotStatus.BOOKED)));
        assertThrows(SlotUnavailableException.class, () -> service.book(request("PRV-1", "SLT-1")));
        when(slotRepository.findById("SLT-2")).thenReturn(Optional.of(slot("SLT-2", provider, SlotStatus.CANCELLED)));
        assertThrows(SlotUnavailableException.class, () -> service.book(request("PRV-1", "SLT-2")));
    }

    @Test
    void offlineProviderSlotIsConflict() {
        Provider offline = new Provider("PRV-3", "Dr. C", Specialty.GENERAL, ProviderStatus.OFFLINE);
        when(providerRepository.findById("PRV-3")).thenReturn(Optional.of(offline));
        when(slotRepository.findById("SLT-3")).thenReturn(Optional.of(slot("SLT-3", offline, SlotStatus.OPEN)));
        assertThrows(InvalidStateException.class, () -> service.book(request("PRV-3", "SLT-3")));
    }

    @Test
    void getUnknownAppointmentIsNotFound() {
        when(appointmentRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.get("nope"));
    }
}
