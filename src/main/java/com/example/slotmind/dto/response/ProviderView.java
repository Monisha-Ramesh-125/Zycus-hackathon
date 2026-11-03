package com.example.slotmind.dto.response;

import com.example.slotmind.entity.Provider;
import com.example.slotmind.entity.ProviderStatus;
import com.example.slotmind.entity.Specialty;

import java.time.LocalDateTime;
import java.util.List;

public record ProviderView(String id, String name, Specialty specialty, ProviderStatus status,
                           String statusReason, LocalDateTime statusChangedAt) {

    public static ProviderView from(Provider p) {
        return new ProviderView(p.getId(), p.getName(), p.getSpecialty(), p.getStatus(),
                p.getStatusReason(), p.getStatusChangedAt());
    }

    public static List<ProviderView> from(List<Provider> providers) {
        return providers.stream().map(ProviderView::from).toList();
    }
}
