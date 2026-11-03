package com.example.slotmind.engine;

import com.example.slotmind.entity.Appointment;
import com.example.slotmind.entity.Provider;
import com.example.slotmind.entity.Slot;
import com.example.slotmind.entity.TriggerReason;
import com.example.slotmind.repository.ProviderRepository;
import com.example.slotmind.repository.SlotRepository;
import com.example.slotmind.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Entry point for both the HTTP path and the async loop. It runs the active strategy, validates the
 * output against real data (a strategy, least of all an LLM, is never trusted), and falls back to the
 * rule strategy when the active one fails or returns nothing usable. Call it inside a transaction:
 * strategies read lazily loaded relations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulingEngine {

    public record Result(String strategy, List<Recommendation> recommendations) {}

    private final StrategyRegistry registry;
    private final RuleBasedStrategy ruleBased;
    private final SlotRepository slotRepository;
    private final ProviderRepository providerRepository;
    private final SuggestionRepository suggestionRepository;

    public Result recommend(Appointment appointment, TriggerReason trigger) {
        List<Slot> openSlots = slotRepository.findOpenAfter(LocalDateTime.now());
        List<Provider> providers = providerRepository.findAll();
        var rejected = new HashSet<>(suggestionRepository.findRejectedSlotIds(appointment.getId()));
        var context = new SchedulingContext(appointment, openSlots, providers, trigger, rejected);

        SchedulingStrategy active = registry.active();
        try {
            List<Recommendation> valid = validate(active.recommend(context), openSlots, providers);
            if (!valid.isEmpty() || active == ruleBased) {
                return new Result(active.name(), valid);
            }
            log.warn("Strategy '{}' returned nothing usable for {}, falling back to rules",
                    active.name(), appointment.getId());
        } catch (RuntimeException e) {
            if (active == ruleBased) {
                throw e;
            }
            log.warn("Strategy '{}' failed for {} ({}), falling back to rules",
                    active.name(), appointment.getId(), e.toString());
        }
        return new Result(active.name() + "->rule-fallback",
                validate(ruleBased.recommend(context), openSlots, providers));
    }

    private List<Recommendation> validate(List<Recommendation> recommendations, List<Slot> openSlots,
                                          List<Provider> providers) {
        Map<String, Slot> slotsById = openSlots.stream().collect(Collectors.toMap(Slot::getId, Function.identity()));
        Map<String, Provider> providersById = providers.stream()
                .collect(Collectors.toMap(Provider::getId, Function.identity()));
        return recommendations.stream()
                .filter(r -> {
                    Slot slot = slotsById.get(r.slotId());
                    if (slot == null || !slot.isOpen() || slot.getProvider().isOffline()) {
                        return false;
                    }
                    if (r.alternateProviderId() == null) {
                        return true;
                    }
                    return providersById.containsKey(r.alternateProviderId())
                            && slot.getProvider().getId().equals(r.alternateProviderId());
                })
                .toList();
    }
}
