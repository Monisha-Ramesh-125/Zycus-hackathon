# Stage 7 — AI scheduling strategy

> **As built (simplified at your request):** one Gemini gateway class, one strategy class, a prompt factory and an exception; no provider interface, no Groq or Ollama classes, no circuit breaker, no properties class. Settings are the `slotmind.ai.*` keys in `application.properties`. Sections 6 and 7 below describe the larger design that was **not** built; sections 3 to 5 and 9 still apply in spirit. Prompts and answer checks are as described in sections 4 and 5. The common "usable slots" filter was extracted into `engine/UsableSlots` so the rule and AI strategies share it.

Goal: an LLM-backed strategy that plugs into the engine from Stage 3 without changing it. The loop (Stage 5) and the accept flow (Stage 4) already work on rules; this stage adds a smarter advisor that explains its choice in words a front-desk person can read aloud, and that can never do harm: every answer is validated and any failure falls back to the rules.

Brief reference: T-3 (AI scheduling strategy), Addendum B (`LLMGateway.callLLM(prompt)` for Gemini, Groq or Ollama), design-depth question AGT-2 (patient-friendly output).

Stage order note: the UI (Stage 6) is postponed, so this runs before it. Nothing here depends on the UI.

## 1. What I took from AssistQ (reference only)

| AssistQ | SlotMind version |
|---|---|
| One provider class per vendor (`GeminiApiService`, `GroqApiService`, ...), selected by configuration; a provider counts as "configured" only when its key is non-empty | One small gateway class per vendor (Gemini, Groq, Ollama) behind one interface; the active one is chosen by a property, and an unconfigured one simply fails over to rules |
| Whole-call deadline per task, and a cut call falls back instead of failing the turn | A single call timeout (15 s default); a timeout falls back to the rule strategy |
| Answers that wrap JSON in prose are parsed by extracting the embedded JSON | Same: the parser strips code fences and pulls out the first JSON object |
| Keys never in plain source; encrypted or kept in the gateway | Keys only from environment variables, never committed, never logged |
| LiteLLM gateway, model catalog YAML, Retrofit clients, hedged calls | Not copied: too heavy for this project. Plain Spring `RestClient` calls |

## 2. How it fits

```
SchedulingEngine  ->  StrategyRegistry.active()  ->  AiSchedulingStrategy          (name "ai")
                                                          |
                          SchedulingPromptFactory: two prompts (offline vs cancelled)
                                                          |
                          LLMGateway.callLLM(prompt) -> Gemini | Groq | Ollama     (slotmind.ai.provider)
                                                          |
                          AiResponseParser -> checks -> one Recommendation
                                                          |
   engine validates again against live data;  any failure  ->  rule strategy, labelled "ai->rule-fallback"
```

`AiSchedulingStrategy` implements the same `SchedulingStrategy` contract, so the HTTP endpoint and the async loop both get it for free. It is switched on by config (`slotmind.scheduling.strategy=ai`) or at runtime (`PUT /api/scheduling/strategy`). The default stays `rule`, so the app keeps working with no key at all.

## 3. Pieces (package `ai/`)

| Class | Job |
|---|---|
| `LLMGateway` (interface) | `String callLLM(String prompt)`, `String name()`, `boolean isConfigured()`. The brief's Addendum B, under our base package `com.example.slotmind.ai` |
| `GeminiGateway`, `GroqGateway`, `OllamaGateway` | One REST call each (see section 6). Ask the model for JSON-only output |
| `LlmException` | One unchecked exception for every provider failure (not configured, timeout, HTTP error, empty reply) |
| `SchedulingPromptFactory` | Builds the two prompts (section 4) from the `SchedulingContext` |
| `AiResponseParser` | Extracts and parses the JSON reply into slot id, alternate provider id, confidence, reasoning |
| `AiSchedulingStrategy` | Pre-filters candidates, calls the gateway, checks the answer, returns the recommendation |
| `LlmCircuitBreaker` | Stops calling a failing LLM for a cooldown (section 7) |
| `AiProperties` | The `slotmind.ai.*` settings |

## 4. The two prompts (AGT-2, T-3)

Both prompts share a base: the model's role, the appointment, the candidate slots, the provider roster, and the required reply format. They differ in the situation and the guidance.

**What is sent (structured facts only):** appointment type (`ROUTINE`, `URGENT`, `FOLLOW_UP`), the current provider and specialty, the current slot time and length, the candidate slots (id, provider id, start time, length), the provider roster (id, name, specialty, status), the trigger, and how many times this appointment was already moved.

**What is deliberately not sent:** the patient's name, phone number and free-text notes. The reply is for staff, so none of it is needed. It also keeps personal data away from a third-party service, and keeps free text (a prompt-injection route) out of the prompt.

**Prompt A, `PROVIDER_OFFLINE`:** the patient's doctor is unavailable, possibly for a long time. Prefer another provider of the same specialty; consider a different specialty only for urgent cases and say so; prefer earlier slots for `URGENT`. Name the alternate provider.

**Prompt B, `SLOT_CANCELLED`:** the doctor is still available, only this time is gone. Prefer the same provider's next suitable slot (continuity of care, especially for `FOLLOW_UP`); use another provider only if the same provider has nothing sensible.

**Reply format, in both:** a single JSON object, the shape from the brief:
```json
{"slotId":"...","alternateProviderId":"... or null","confidence":0.88,"reasoning":"..."}
```
The model must choose a slot id from the list only, set `alternateProviderId` to `null` when the patient keeps their provider, give confidence between 0 and 1, and write `reasoning` as one or two plain sentences that staff can read aloud: who, when, and why, with no ids, no jargon and no mention of "the system" or "the model".

**Candidates sent:** the same "usable slots" filter the rule strategy uses (open, in the future, provider online, not the current slot, not already refused, long enough), nearest first, at most 15. Fewer slots means a cheaper, faster call and fewer ways to go wrong.

## 5. Checks on the answer (never trust an LLM)

Rejected, so the engine falls back, if any of these fail:
- not valid JSON after extraction
- `slotId` is not one of the candidates we sent (made up, or filtered out as too short, taken or offline)
- `alternateProviderId` is set but is not the provider who owns that slot, or is `null` when the slot belongs to a different provider than the patient's current one (corrected rather than rejected, since the slot's owner is known)
- `reasoning` is empty
- `confidence` is missing or not a number (a number outside 0 to 1 is clamped)

Then the engine's own validation runs a second time against live data (Stage 3), so a slot that changed during the call is still caught. `reasoning` is cut to 500 characters before it is stored.

## 6. Providers

| Provider | Endpoint | Auth | Settings |
|---|---|---|---|
| Gemini | Google's Generative Language API, `generateContent`, with a JSON response type | API key header | `slotmind.ai.gemini.api-key` (from `GEMINI_API_KEY`), `.model`, `.base-url` |
| Groq | OpenAI-compatible chat completions with JSON mode | Bearer key | `slotmind.ai.groq.api-key` (from `GROQ_API_KEY`), `.model` (default `llama-3.3-70b-versatile`), `.base-url` |
| Ollama | Local `/api/generate`, non-streaming, JSON format | none | `slotmind.ai.ollama.base-url` (default `http://localhost:11434`), `.model` |

`slotmind.ai.provider` picks one. A provider is "configured" only when its key (or, for Ollama, its URL) is non-empty. Exact Gemini and Groq model ids change over time, so I will confirm the default ids with one real call before finishing, and they stay plain properties.

## 7. Failure behaviour

| Situation | What happens |
|---|---|
| No key, or provider not configured | `LlmException`, so engine falls back, label `ai->rule-fallback` |
| Timeout (default 15 s), HTTP error, rate limit, connection refused | Same |
| Garbage or invalid answer | Same |
| LLM returns a valid answer | Label `ai` |

**Circuit breaker.** One provider-offline event can mean many appointments in a row. If the LLM is down, waiting 15 seconds for each would stall the loop. After 3 consecutive failures the breaker opens for 60 seconds and calls go straight to the rules, then it tries again. (Both numbers are settings.) A success closes it immediately.

No retries in this stage: the fallback is instant and always correct, which beats waiting for a second attempt.

## 8. Files

```
ai/          LLMGateway, GeminiGateway, GroqGateway, OllamaGateway, LlmException,
             SchedulingPromptFactory, AiResponseParser, AiSchedulingStrategy,
             LlmCircuitBreaker, AiProperties
application.properties   slotmind.ai.* defaults (no keys; keys come from the environment)
```

No schema change, no change to the engine, loop, or controllers: the registry finds `AiSchedulingStrategy` by itself, and `GET /api/scheduling/strategy` will then list `ai` and `rule`.

## 9. How we verify (live; no tests, as you asked)

1. **No key at all.** Switch to `ai`, request a suggestion: it succeeds with the rule result, labelled `ai->rule-fallback`. This proves the safety net before any key exists.
2. **With a real key** (you set the key in your own terminal, so it never passes through this chat): switch to `ai`, `POST /api/appointments/APT-001/suggest`: returns strategy `ai`, a valid open slot, a confidence, and readable reasoning with no ids.
3. **Both prompts.** Set Dr. Chen `OFFLINE`: the loop's suggestions come back as strategy `ai`, trigger `PROVIDER_OFFLINE`, naming another provider. Cancel `SLT-001`: a `SLOT_CANCELLED` suggestion from prompt B, and I show the prompt text sent for each in the log at debug level.
4. **Comparison.** The same appointment under `rule` and under `ai`, side by side.
5. **Wrong key** (deliberately): fallback label appears; after three failures the log shows the breaker opening and later calls skip the LLM.
6. **Ollama not running** (provider set to `ollama`): connection refused, instant fallback.
7. The existing 60 tests still pass.

I reset the demo data afterwards, as before.

## 10. Needed from you

- Which provider do you want to use, and do you have a key (or Ollama installed)?
- Put the key in your own shell (for example `export GROQ_API_KEY=...` in the terminal, or with the `!` prefix in this prompt, not pasted into chat) before step 2. Without a key, I can still finish and verify everything except the live AI calls (steps 1, 5 and 6).

## 11. Out of scope

The SSE reasoning stream (Stage 8, the bonus), multi-option ranking from the LLM, retries, response caching, token and cost tracking, provider failover chains, prompt tuning beyond the two prompts, the UI.

## 12. Open points for your review

1. **Provider:** Gemini (what AssistQ itself uses, free key from Google AI Studio), Groq (fast, free tier, OpenAI-compatible) or Ollama (local, no key, needs a model downloaded)? All three are built either way; this decides which one I verify live and the default. Recommended: Gemini, matching AssistQ.
2. **One recommendation per call** (the brief's JSON shape), not a ranked list. Recommended: yes.
3. **No patient name, phone or notes** are sent to the LLM. Recommended: yes.
4. **Circuit breaker:** 3 failures, 60 second cooldown. OK?
5. **Timeout:** 15 seconds per call. OK?
6. **No retries.** OK?
7. **Default strategy stays `rule`;** you switch to `ai` by config or by the PUT endpoint. OK?
