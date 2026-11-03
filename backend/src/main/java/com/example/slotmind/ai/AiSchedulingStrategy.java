package com.example.slotmind.ai;

import com.example.slotmind.engine.Recommendation;
import com.example.slotmind.engine.SchedulingContext;
import com.example.slotmind.engine.SchedulingStrategy;
import com.example.slotmind.engine.UsableSlots;
import com.example.slotmind.entity.Slot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Asks the LLM to pick one slot from the candidates the rules would also consider. The answer is checked
 * here and again by the engine; any failure throws, and the engine falls back to the rule strategy.
 */
@Slf4j
@Component
public class AiSchedulingStrategy implements SchedulingStrategy {

    private static final int MAX_REASONING_LENGTH = 500;

    private final LLMGateway gateway;
    private final SchedulingPromptFactory promptFactory;
    private final int maxCandidates;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public AiSchedulingStrategy(LLMGateway gateway, SchedulingPromptFactory promptFactory,
                                @Value("${slotmind.ai.max-candidates:15}") int maxCandidates) {
        this.gateway = gateway;
        this.promptFactory = promptFactory;
        this.maxCandidates = maxCandidates;
    }

    @Override
    public String name() {
        return "ai";
    }

    @Override
    public List<Recommendation> recommend(SchedulingContext ctx) {
        List<Slot> candidates = UsableSlots.of(ctx).stream().limit(maxCandidates).toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        String prompt = promptFactory.build(ctx, candidates);
        log.debug("LLM prompt for {}:\n{}", ctx.appointment().getId(), prompt);

        String reply = gateway.callLLM(prompt);
        log.debug("LLM reply for {}: {}", ctx.appointment().getId(), reply);
        return List.of(toRecommendation(reply, ctx, candidates));
    }

    private Recommendation toRecommendation(String reply, SchedulingContext ctx, List<Slot> candidates) {
        JsonNode json = parse(reply);
        String slotId = json.path("slotId").asString("");
        Slot chosen = candidates.stream().filter(s -> s.getId().equals(slotId)).findFirst()
                .orElseThrow(() -> new LlmException("LLM chose a slot that was not offered: '" + slotId + "'"));

        String reasoning = json.path("reasoning").asString("").trim();
        if (reasoning.isEmpty()) {
            throw new LlmException("LLM gave no reasoning");
        }
        if (!json.path("confidence").isNumber()) {
            throw new LlmException("LLM gave no numeric confidence");
        }
        double confidence = Math.max(0.0, Math.min(1.0, json.path("confidence").asDouble()));

        // The slot's owner is known, so the alternate provider is derived instead of trusted.
        boolean sameProvider = chosen.getProvider().getId().equals(ctx.appointment().getProvider().getId());
        String alternateProviderId = sameProvider ? null : chosen.getProvider().getId();

        if (reasoning.length() > MAX_REASONING_LENGTH) {
            reasoning = reasoning.substring(0, MAX_REASONING_LENGTH);
        }
        return new Recommendation(chosen.getId(), alternateProviderId, confidence, reasoning);
    }

    /** Models sometimes wrap the JSON in code fences or prose; take the outermost object. */
    private JsonNode parse(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmException("LLM reply contained no JSON object");
        }
        try {
            return jsonMapper.readTree(reply.substring(start, end + 1));
        } catch (RuntimeException e) {
            throw new LlmException("LLM reply was not valid JSON", e);
        }
    }
}
