package com.example.slotmind.exception;

import org.springframework.http.HttpStatus;

public class NoSlotAvailableException extends BusinessException {
    public NoSlotAvailableException(String appointmentId) {
        super("No suitable open slot was found for appointment " + appointmentId,
                HttpStatus.CONFLICT, "NO_SLOT_AVAILABLE");
    }
}
