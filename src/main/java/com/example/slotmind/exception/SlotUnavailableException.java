package com.example.slotmind.exception;

import com.example.slotmind.entity.SlotStatus;
import org.springframework.http.HttpStatus;

public class SlotUnavailableException extends BusinessException {
    public SlotUnavailableException(String slotId, SlotStatus status) {
        super("Slot " + slotId + " is not available (status " + status + ")", HttpStatus.CONFLICT, "SLOT_UNAVAILABLE");
    }
}
