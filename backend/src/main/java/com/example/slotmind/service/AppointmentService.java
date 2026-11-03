package com.example.slotmind.service;

import com.example.slotmind.dto.request.BookAppointmentModel;
import com.example.slotmind.dto.response.AppointmentView;
import com.example.slotmind.dto.response.SuggestionView;
import com.example.slotmind.entity.Appointment;
import com.example.slotmind.entity.AppointmentStatus;
import com.example.slotmind.entity.Provider;
import com.example.slotmind.entity.Slot;
import com.example.slotmind.exception.BadRequestException;
import com.example.slotmind.exception.NotFoundException;
import com.example.slotmind.repository.AppointmentRepository;
import com.example.slotmind.repository.ProviderRepository;
import com.example.slotmind.repository.SlotRepository;
import com.example.slotmind.repository.SuggestionRepository;
import com.example.slotmind.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ProviderRepository providerRepository;
    private final SlotRepository slotRepository;
    private final SuggestionRepository suggestionRepository;

    /**
     * The slot's own rules decide whether it can be booked; a concurrent booking of the same slot is
     * rejected by its version check or the unique slot_id index.
     */
    @Transactional
    public AppointmentView book(BookAppointmentModel req) {
        Provider provider = providerRepository.findById(req.providerId())
                .orElseThrow(() -> new NotFoundException("Provider", req.providerId()));
        Slot slot = slotRepository.findById(req.slotId())
                .orElseThrow(() -> new NotFoundException("Slot", req.slotId()));
        if (!slot.getProvider().getId().equals(provider.getId())) {
            throw new BadRequestException("Slot " + slot.getId() + " does not belong to provider " + provider.getId());
        }
        slot.book();
        Appointment appointment = new Appointment(IdGenerator.next("APT"), req.patientName(), req.type(),
                req.patientPhone(), req.notes(), slot);
        return AppointmentView.from(appointmentRepository.saveAndFlush(appointment));
    }

    @Transactional(readOnly = true)
    public List<AppointmentView> list(AppointmentStatus status) {
        List<Appointment> appointments = appointmentRepository.search(status);
        Map<String, SuggestionView> pending = pendingSuggestions(appointments);
        return appointments.stream().map(a -> AppointmentView.from(a, pending.get(a.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentView get(String id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Appointment", id));
        return AppointmentView.from(appointment, pendingSuggestions(List.of(appointment)).get(id));
    }

    /** One query for all waiting appointments; the newest pending suggestion wins. */
    private Map<String, SuggestionView> pendingSuggestions(List<Appointment> appointments) {
        List<String> waiting = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.RESCHEDULE_PENDING)
                .map(Appointment::getId)
                .toList();
        if (waiting.isEmpty()) {
            return Map.of();
        }
        return suggestionRepository.findPendingFor(waiting).stream()
                .map(SuggestionView::from)
                .collect(Collectors.toMap(SuggestionView::appointmentId, Function.identity(), (newest, older) -> newest));
    }
}
