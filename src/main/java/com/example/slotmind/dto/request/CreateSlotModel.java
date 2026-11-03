package com.example.slotmind.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record CreateSlotModel(@NotBlank String providerId, @NotNull @Future LocalDateTime startTime,
                              @Positive int durationMinutes) {}
