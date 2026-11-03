package com.example.slotmind.service;

import com.example.slotmind.dto.request.CreateProviderModel;
import com.example.slotmind.dto.request.UpdateProviderStatusModel;
import com.example.slotmind.dto.response.ProviderView;
import com.example.slotmind.entity.Provider;
import com.example.slotmind.entity.ProviderStatus;
import com.example.slotmind.exception.NotFoundException;
import com.example.slotmind.repository.ProviderRepository;
import com.example.slotmind.util.IdGenerator;
import com.example.slotmind.event.ProviderWentOfflineEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderService {

    private final ProviderRepository providerRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<ProviderView> list() {
        return ProviderView.from(providerRepository.findAll());
    }

    @Transactional
    public ProviderView create(CreateProviderModel req) {
        ProviderStatus status = req.status() == null ? ProviderStatus.AVAILABLE : req.status();
        Provider provider = new Provider(IdGenerator.next("PRV"), req.name(), req.specialty(), status);
        return ProviderView.from(providerRepository.save(provider));
    }

    @Transactional
    public ProviderView updateStatus(String id, UpdateProviderStatusModel req) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Provider", id));
        ProviderStatus previous = provider.getStatus();
        provider.changeStatus(req.status(), req.reason());
        if (req.status() == ProviderStatus.OFFLINE && previous != ProviderStatus.OFFLINE) {
            // Announced inside the transaction; the loop only runs once it has committed.
            eventPublisher.publishEvent(new ProviderWentOfflineEvent(id));
        }
        return ProviderView.from(provider);
    }
}
