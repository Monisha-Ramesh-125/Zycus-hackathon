package com.example.slotmind.dto.request;

import com.example.slotmind.entity.ProviderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProviderStatusModel(@NotNull ProviderStatus status, @Size(max = 255) String reason) {}
