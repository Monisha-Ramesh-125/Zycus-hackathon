package com.example.slotmind.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {
    public NotFoundException(String entity, String id) {
        super(entity + " not found: " + id, HttpStatus.NOT_FOUND, entity.toUpperCase() + "_NOT_FOUND");
    }
}
