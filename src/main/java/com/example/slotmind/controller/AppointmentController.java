package com.example.slotmind.controller;

import com.example.slotmind.dto.request.BookAppointmentModel;
import com.example.slotmind.dto.response.AppointmentView;
import com.example.slotmind.dto.response.SuggestionView;
import com.example.slotmind.entity.TriggerReason;
import com.example.slotmind.service.SuggestionService;
import com.example.slotmind.entity.AppointmentStatus;
import com.example.slotmind.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final SuggestionService suggestionService;

    @PostMapping
    public ResponseEntity<AppointmentView> book(@Valid @RequestBody BookAppointmentModel body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointmentService.book(body));
    }

    @GetMapping
    public List<AppointmentView> list(@RequestParam(required = false) AppointmentStatus status) {
        return appointmentService.list(status);
    }

    @GetMapping("/{id}")
    public AppointmentView get(@PathVariable String id) {
        return appointmentService.get(id);
    }

    /** On-demand reschedule: proposes a new slot without booking anything. */
    @PostMapping("/{id}/suggest")
    public SuggestionView suggest(@PathVariable String id) {
        return suggestionService.createSuggestion(id, TriggerReason.INITIAL);
    }
}
