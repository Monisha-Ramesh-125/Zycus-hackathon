package com.example.slotmind.controller;

import com.example.slotmind.dto.request.SwitchStrategyModel;
import com.example.slotmind.dto.response.StrategyView;
import com.example.slotmind.service.SchedulingConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scheduling/strategy")
@RequiredArgsConstructor
public class SchedulingController {

    private final SchedulingConfigService schedulingConfigService;

    @GetMapping
    public StrategyView current() {
        return schedulingConfigService.current();
    }

    @PutMapping
    public StrategyView switchTo(@Valid @RequestBody SwitchStrategyModel body) {
        return schedulingConfigService.switchTo(body.strategy());
    }
}
