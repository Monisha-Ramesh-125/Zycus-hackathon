package com.example.slotmind.service;

import com.example.slotmind.dto.request.CreateSlotModel;
import com.example.slotmind.dto.request.UpdateSlotStatusModel;
import com.example.slotmind.dto.response.SlotView;
import com.example.slotmind.entity.Provider;
import com.example.slotmind.entity.Slot;
import com.example.slotmind.entity.SlotStatus;
import com.example.slotmind.exception.BadRequestException;
import com.example.slotmind.exception.NotFoundException;
import com.example.slotmind.repository.ProviderRepository;
import com.example.slotmind.repository.SlotRepository;
import com.example.slotmind.util.IdGenerator;
import com.example.slotmind.event.SlotCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final SlotRepository slotRepository;
    private final ProviderRepository providerRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<SlotView> search(SlotStatus status, String providerId) {
        return SlotView.from(slotRepository.search(status, providerId));
    }

    @Transactional
    public SlotView create(CreateSlotModel req) {
        Provider provider = providerRepository.findById(req.providerId())
                .orElseThrow(() -> new NotFoundException("Provider", req.providerId()));
        Slot slot = new Slot(IdGenerator.next("SLT"), provider, req.startTime(), req.durationMinutes(), SlotStatus.OPEN);
        return SlotView.from(slotRepository.save(slot));
    }

    /**
     * Only cancelling is allowed here. Booking goes through the appointment endpoint, and reopening a
     * slot that still has an appointment attached could double book it.
     */
    @Transactional
    public SlotView updateStatus(String id, UpdateSlotStatusModel req) {
        if (req.status() != SlotStatus.CANCELLED) {
            throw new BadRequestException("Only CANCELLED can be set on a slot; book it through POST /api/appointments");
        }
        Slot slot = slotRepository.findById(id).orElseThrow(() -> new NotFoundException("Slot", id));
        SlotStatus previous = slot.getStatus();
        slot.cancel(req.reason());
        if (previous != SlotStatus.CANCELLED) {
            eventPublisher.publishEvent(new SlotCancelledEvent(id));
        }
        return SlotView.from(slot);
    }
}
