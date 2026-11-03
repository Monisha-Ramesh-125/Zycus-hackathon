package com.example.slotmind.engine;

import com.example.slotmind.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StrategyRegistryTest {

    SchedulingStrategy other = new SchedulingStrategy() {
        public String name() { return "other"; }
        public List<Recommendation> recommend(SchedulingContext c) { return List.of(); }
    };

    @Test
    void unknownConfiguredNameFailsFast() {
        assertThrows(NotFoundException.class, () -> new StrategyRegistry(List.of(new RuleBasedStrategy()), "nope"));
    }

    @Test
    void activateSwitchesAndRejectsUnknownNames() {
        var registry = new StrategyRegistry(List.of(new RuleBasedStrategy(), other), "rule");
        assertEquals("rule", registry.activeName());
        registry.activate("other");
        assertEquals("other", registry.active().name());
        assertThrows(NotFoundException.class, () -> registry.activate("nope"));
        assertEquals("other", registry.activeName());
        assertEquals(java.util.Set.of("rule", "other"), registry.names());
    }
}
