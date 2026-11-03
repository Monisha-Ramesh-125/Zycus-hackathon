package com.example.slotmind.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** One plain call to Gemini: prompt in, the model's text out. */
@Slf4j
@Component
public class LLMGateway {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public LLMGateway(@Value("${slotmind.ai.api-key:}") String apiKey,
                      @Value("${slotmind.ai.model}") String model,
                      @Value("${slotmind.ai.base-url}") String baseUrl,
                      @Value("${slotmind.ai.timeout-seconds:15}") long timeoutSeconds) {
        this.apiKey = apiKey;
        this.model = model;
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Returns the model's JSON text. Throws LlmException on any failure. */
    public String callLLM(String prompt) {
        if (!isConfigured()) {
            throw new LlmException("LLM API key is not set (GEMINI_API_KEY)");
        }
        long started = System.currentTimeMillis();
        try {
            JsonNode response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                            "generationConfig", Map.of("temperature", 0.2, "responseMimeType", "application/json")))
                    .retrieve()
                    .body(JsonNode.class);
            String text = response == null ? ""
                    : response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asString("");
            if (text.isBlank()) {
                throw new LlmException("LLM returned an empty answer");
            }
            log.info("LLM call ok (model {}, {} ms)", model, System.currentTimeMillis() - started);
            return text;
        } catch (LlmException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LlmException("LLM call failed after " + (System.currentTimeMillis() - started) + " ms: "
                    + e.getClass().getSimpleName() + " " + e.getMessage(), e);
        }
    }
}
