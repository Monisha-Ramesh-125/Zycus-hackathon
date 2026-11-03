package com.example.slotmind.engine;

/** One option a strategy proposes. alternateProviderId is null when the patient keeps their provider. */
public record Recommendation(String slotId, String alternateProviderId, double confidence, String reasoning) {}
