package com.example.slotmind.event;

/** Carries only the id: the handler runs on another thread and reloads what it needs. */
public record ProviderWentOfflineEvent(String providerId) {}
