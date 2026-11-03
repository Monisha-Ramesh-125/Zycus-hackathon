package com.example.slotmind.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** note is the front desk's record of the patient call; optional. */
public record SuggestionDecisionModel(@NotNull Decision decision, @Size(max = 500) String note) {

    public enum Decision { ACCEPT, REJECT }
}
