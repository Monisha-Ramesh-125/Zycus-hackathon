package com.example.slotmind.dto.request;

import com.example.slotmind.entity.SlotStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSlotStatusModel(@NotNull SlotStatus status, @Size(max = 255) String reason) {}
