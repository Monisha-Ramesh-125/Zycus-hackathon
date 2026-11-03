package com.example.slotmind.service;

import com.example.slotmind.dto.response.StrategyView;
import com.example.slotmind.engine.StrategyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SchedulingConfigService {

    private final StrategyRegistry registry;

    public StrategyView current() {
        return new StrategyView(registry.activeName(), registry.names());
    }

    public StrategyView switchTo(String strategy) {
        registry.activate(strategy);
        return current();
    }
}
