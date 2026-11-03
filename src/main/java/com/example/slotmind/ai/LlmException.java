package com.example.slotmind.ai;

/** Any failure of the AI path (no key, timeout, HTTP error, unusable answer). The engine falls back to rules. */
public class LlmException extends RuntimeException {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
