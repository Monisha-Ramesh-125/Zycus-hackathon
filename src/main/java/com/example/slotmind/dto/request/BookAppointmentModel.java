package com.example.slotmind.dto.request;

import com.example.slotmind.entity.AppointmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookAppointmentModel(
        @NotBlank @Size(max = 120) String patientName,
        @NotNull AppointmentType type,
        @NotBlank String providerId,
        @NotBlank String slotId,
        @Size(max = 40) String patientPhone,
        @Size(max = 500) String notes) {}
