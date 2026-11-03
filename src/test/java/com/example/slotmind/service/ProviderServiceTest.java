package com.example.slotmind.service;

import com.example.slotmind.dto.request.CreateProviderModel;
import com.example.slotmind.dto.request.UpdateProviderStatusModel;
import com.example.slotmind.dto.response.ProviderView;
import com.example.slotmind.entity.Provider;
import com.example.slotmind.entity.ProviderStatus;
import com.example.slotmind.entity.Specialty;
import com.example.slotmind.exception.NotFoundException;
import com.example.slotmind.repository.ProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock ProviderRepository providerRepository;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @InjectMocks ProviderService service;

    @Test
    void updateStatusRecordsReasonAndTime() {
        var p = new Provider("PRV-1", "Dr. A", Specialty.GENERAL, ProviderStatus.BUSY);
        when(providerRepository.findById("PRV-1")).thenReturn(Optional.of(p));
        ProviderView view = service.updateStatus("PRV-1", new UpdateProviderStatusModel(ProviderStatus.OFFLINE, "sick leave"));
        assertEquals(ProviderStatus.OFFLINE, view.status());
        assertEquals("sick leave", view.statusReason());
        assertNotNull(view.statusChangedAt());
    }

    @Test
    void unknownProviderIsNotFound() {
        when(providerRepository.findById("X")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.updateStatus("X", new UpdateProviderStatusModel(ProviderStatus.OFFLINE, null)));
    }

    @Test
    void createDefaultsToAvailable() {
        when(providerRepository.save(any(Provider.class))).thenAnswer(i -> i.getArgument(0));
        ProviderView view = service.create(new CreateProviderModel("Dr. New", Specialty.PEDIATRIC, null));
        assertEquals(ProviderStatus.AVAILABLE, view.status());
        assertTrue(view.id().startsWith("PRV-"));
    }
}
