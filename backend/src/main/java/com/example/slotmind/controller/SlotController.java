package com.example.slotmind.controller;

import com.example.slotmind.dto.request.CreateSlotModel;
import com.example.slotmind.dto.request.UpdateSlotStatusModel;
import com.example.slotmind.dto.response.SlotView;
import com.example.slotmind.entity.SlotStatus;
import com.example.slotmind.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @GetMapping
    public List<SlotView> search(@RequestParam(required = false) SlotStatus status,
                                 @RequestParam(required = false) String providerId) {
        return slotService.search(status, providerId);
    }

    @PostMapping
    public ResponseEntity<SlotView> create(@Valid @RequestBody CreateSlotModel body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(slotService.create(body));
    }

    @PatchMapping("/{id}/status")
    public SlotView updateStatus(@PathVariable String id, @Valid @RequestBody UpdateSlotStatusModel body) {
        return slotService.updateStatus(id, body);
    }
}
