package com.example.slotmind.service;

import com.example.slotmind.dto.request.UpdateSlotStatusModel;
import com.example.slotmind.dto.response.SlotView;
import com.example.slotmind.entity.*;
import com.example.slotmind.exception.BadRequestException;
import com.example.slotmind.exception.NotFoundException;
import com.example.slotmind.repository.ProviderRepository;
import com.example.slotmind.repository.SlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    @Mock SlotRepository slotRepository;
    @Mock ProviderRepository providerRepository;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @InjectMocks SlotService service;

    private Slot bookedSlot() {
        var p = new Provider("PRV-1", "Dr. A", Specialty.GENERAL, ProviderStatus.AVAILABLE);
        return new Slot("SLT-1", p, LocalDateTime.now().plusHours(1), 30, SlotStatus.BOOKED);
    }

    @Test
    void cancelStoresReason() {
        when(slotRepository.findById("SLT-1")).thenReturn(Optional.of(bookedSlot()));
        SlotView view = service.updateStatus("SLT-1", new UpdateSlotStatusModel(SlotStatus.CANCELLED, "room flooded"));
        assertEquals(SlotStatus.CANCELLED, view.status());
        assertEquals("room flooded", view.statusReason());
    }

    @Test
    void settingOpenOrBookedIsRejected() {
        assertThrows(BadRequestException.class, () -> service.updateStatus("SLT-1", new UpdateSlotStatusModel(SlotStatus.OPEN, null)));
        assertThrows(BadRequestException.class, () -> service.updateStatus("SLT-1", new UpdateSlotStatusModel(SlotStatus.BOOKED, null)));
    }

    @Test
    void unknownSlotIsNotFound() {
        when(slotRepository.findById("X")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.updateStatus("X", new UpdateSlotStatusModel(SlotStatus.CANCELLED, null)));
    }
}
