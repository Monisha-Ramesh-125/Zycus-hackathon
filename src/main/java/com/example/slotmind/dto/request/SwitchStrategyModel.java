package com.example.slotmind.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SwitchStrategyModel(@NotBlank String strategy) {}
