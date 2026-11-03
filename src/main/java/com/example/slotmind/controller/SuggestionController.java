package com.example.slotmind.controller;

import com.example.slotmind.dto.request.SuggestionDecisionModel;
import com.example.slotmind.dto.response.SuggestionView;
import com.example.slotmind.entity.SuggestionStatus;
import com.example.slotmind.service.SuggestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;

    @GetMapping
    public List<SuggestionView> list(@RequestParam(required = false) SuggestionStatus status) {
        return suggestionService.list(status);
    }

    /** The human checkpoint: staff accept or reject a proposed slot after talking to the patient. */
    @PatchMapping("/{id}")
    public SuggestionView decide(@PathVariable String id, @Valid @RequestBody SuggestionDecisionModel body) {
        return suggestionService.decide(id, body.decision(), body.note());
    }
}
