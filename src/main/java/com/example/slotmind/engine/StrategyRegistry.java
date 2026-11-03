package com.example.slotmind.engine;

import com.example.slotmind.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The one place that knows which strategy is active. The HTTP path and the async loop both go through
 * SchedulingEngine to here, so a runtime switch applies to both.
 */
@Component
public class StrategyRegistry {

    private final Map<String, SchedulingStrategy> strategies;
    private final AtomicReference<String> active;

    public StrategyRegistry(List<SchedulingStrategy> all, @Value("${slotmind.scheduling.strategy:rule}") String initial) {
        this.strategies = all.stream().collect(Collectors.toMap(
                SchedulingStrategy::name, Function.identity(), (a, b) -> a, TreeMap::new));
        this.active = new AtomicReference<>(initial);
        get(initial); // fail fast on a bad configured name
    }

    public SchedulingStrategy active() {
        return get(active.get());
    }

    public SchedulingStrategy get(String name) {
        SchedulingStrategy strategy = strategies.get(name);
        if (strategy == null) {
            throw new NotFoundException("Strategy", name);
        }
        return strategy;
    }

    public void activate(String name) {
        get(name);
        active.set(name);
    }

    public String activeName() {
        return active.get();
    }

    public Set<String> names() {
        return strategies.keySet();
    }
}
