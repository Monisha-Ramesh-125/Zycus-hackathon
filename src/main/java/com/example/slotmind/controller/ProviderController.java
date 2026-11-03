package com.example.slotmind.controller;

import com.example.slotmind.dto.request.CreateProviderModel;
import com.example.slotmind.dto.request.UpdateProviderStatusModel;
import com.example.slotmind.dto.response.ProviderView;
import com.example.slotmind.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    @GetMapping
    public List<ProviderView> list() {
        return providerService.list();
    }

    @PostMapping
    public ResponseEntity<ProviderView> create(@Valid @RequestBody CreateProviderModel body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(providerService.create(body));
    }

    @PatchMapping("/{id}/status")
    public ProviderView updateStatus(@PathVariable String id, @Valid @RequestBody UpdateProviderStatusModel body) {
        return providerService.updateStatus(id, body);
    }
}
