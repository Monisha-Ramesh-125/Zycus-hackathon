package com.example.slotmind.engine;

import com.example.slotmind.entity.*;
import com.example.slotmind.repository.ProviderRepository;
import com.example.slotmind.repository.SlotRepository;
import com.example.slotmind.repository.SuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static com.example.slotmind.engine.EngineFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchedulingEngineTest {

    record FakeStrategy(String name, Function<SchedulingContext, List<Recommendation>> fn) implements SchedulingStrategy {
        @Override
        public List<Recommendation> recommend(SchedulingContext context) {
            return fn.apply(context);
        }
    }

    SlotRepository slotRepository = mock(SlotRepository.class);
    ProviderRepository providerRepository = mock(ProviderRepository.class);
    RuleBasedStrategy rule = new RuleBasedStrategy();

    Provider chen = provider("PRV-1", Specialty.GENERAL, ProviderStatus.AVAILABLE);
    Provider verma = provider("PRV-2", Specialty.GENERAL, ProviderStatus.AVAILABLE);
    Slot open = slot("OPEN-1", verma, 60, 30, SlotStatus.OPEN);
    Appointment appointment;

    @BeforeEach
    void setUp() {
        appointment = appointment(chen, AppointmentType.ROUTINE);
        when(slotRepository.findOpenAfter(any())).thenReturn(List.of(open));
        when(providerRepository.findAll()).thenReturn(List.of(chen, verma));
    }

    private SchedulingEngine engineWith(SchedulingStrategy active) {
        return new SchedulingEngine(new StrategyRegistry(List.of(rule, active), active.name()), rule,
                slotRepository, providerRepository, mock(SuggestionRepository.class));
    }

    private SchedulingEngine ruleEngine() {
        return new SchedulingEngine(new StrategyRegistry(List.of(rule), "rule"), rule, slotRepository, providerRepository, mock(SuggestionRepository.class));
    }

    @Test
    void ruleStrategyResultIsLabelledRule() {
        var result = ruleEngine().recommend(appointment, TriggerReason.INITIAL);
        assertEquals("rule", result.strategy());
        assertEquals("OPEN-1", result.recommendations().get(0).slotId());
    }

    @Test
    void validRecommendationsFromActiveStrategyAreUsed() {
        var ai = new FakeStrategy("ai", c -> List.of(new Recommendation("OPEN-1", "PRV-2", 0.88, "because")));
        var result = engineWith(ai).recommend(appointment, TriggerReason.PROVIDER_OFFLINE);
        assertEquals("ai", result.strategy());
        assertEquals(0.88, result.recommendations().get(0).confidence());
    }

    @Test
    void unknownSlotIdIsDroppedAndTriggersFallback() {
        var ai = new FakeStrategy("ai", c -> List.of(new Recommendation("GHOST", null, 0.9, "x")));
        var result = engineWith(ai).recommend(appointment, TriggerReason.INITIAL);
        assertEquals("ai->rule-fallback", result.strategy());
        assertEquals("OPEN-1", result.recommendations().get(0).slotId());
    }

    @Test
    void slotThatIsNotOpenIsDropped() {
        var booked = slot("B-1", verma, 70, 30, SlotStatus.BOOKED);
        when(slotRepository.findOpenAfter(any())).thenReturn(List.of(open, booked));
        var ai = new FakeStrategy("ai", c -> List.of(new Recommendation("B-1", null, 0.9, "x")));
        assertEquals("ai->rule-fallback", engineWith(ai).recommend(appointment, TriggerReason.INITIAL).strategy());
    }

    @Test
    void alternateProviderThatDoesNotOwnTheSlotIsDropped() {
        var ai = new FakeStrategy("ai", c -> List.of(new Recommendation("OPEN-1", "PRV-1", 0.9, "wrong owner")));
        assertEquals("ai->rule-fallback", engineWith(ai).recommend(appointment, TriggerReason.INITIAL).strategy());
    }

    @Test
    void unknownAlternateProviderIsDropped() {
        var ai = new FakeStrategy("ai", c -> List.of(new Recommendation("OPEN-1", "PRV-404", 0.9, "x")));
        assertEquals("ai->rule-fallback", engineWith(ai).recommend(appointment, TriggerReason.INITIAL).strategy());
    }

    @Test
    void strategyExceptionFallsBackToRules() {
        var ai = new FakeStrategy("ai", c -> { throw new IllegalStateException("LLM down"); });
        var result = engineWith(ai).recommend(appointment, TriggerReason.INITIAL);
        assertEquals("ai->rule-fallback", result.strategy());
        assertFalse(result.recommendations().isEmpty());
    }

    @Test
    void emptyResultFromActiveStrategyFallsBack() {
        var ai = new FakeStrategy("ai", c -> List.of());
        assertEquals("ai->rule-fallback", engineWith(ai).recommend(appointment, TriggerReason.INITIAL).strategy());
    }

    @Test
    void ruleStrategyFailureIsNotSwallowed() {
        var broken = new RuleBasedStrategy() {
            @Override
            public List<Recommendation> recommend(SchedulingContext ctx) {
                throw new IllegalStateException("rules broke");
            }
        };
        var engine = new SchedulingEngine(new StrategyRegistry(List.of(broken), "rule"), broken, slotRepository, providerRepository, mock(SuggestionRepository.class));
        assertThrows(IllegalStateException.class, () -> engine.recommend(appointment, TriggerReason.INITIAL));
    }
}
