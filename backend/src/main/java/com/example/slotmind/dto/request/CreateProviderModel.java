package com.example.slotmind.dto.request;

import com.example.slotmind.entity.ProviderStatus;
import com.example.slotmind.entity.Specialty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** status is optional and defaults to AVAILABLE. */
public record CreateProviderModel(@NotBlank @Size(max = 120) String name, @NotNull Specialty specialty,
                                  ProviderStatus status) {}
