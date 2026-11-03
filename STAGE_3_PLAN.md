# Stage 3 — Scheduling engine

Goal: a pluggable engine that, given an appointment, returns ordered slot recommendations, plus the endpoint that turns the best one into a `PENDING` suggestion. Only the rule-based strategy exists in this stage; the AI strategy plugs into the same contract in Stage 7.

Brief reference: T-2 (and the HTTP half of T-1's `POST /appointments/{id}/suggest`).

## 1. The design in one picture

```
POST /api/appointments/{id}/suggest
        |
   SuggestionService.createSuggestion(appointmentId, trigger)      <- also what Stage 5's loop calls
        |
   SchedulingEngine.recommend(appointment, trigger)
        |-- StrategyRegistry.active()  ->  RuleBasedStrategy   (later: AiSchedulingStrategy)
        |-- validate every recommendation against real data
        |-- on failure or nothing usable  ->  fall back to RuleBasedStrategy
        v
   best recommendation  ->  RescheduleSuggestion (PENDING)  ->  saved
```

The HTTP path and the async loop (Stage 5) use the same `createSuggestion`, so they share one contract.

## 2. Components (package `engine/`, plus service/controller/dto)

| Class | Job |
|---|---|
| `SchedulingStrategy` (interface) | `String name()` and `List<Recommendation> recommend(SchedulingContext)`. Implement it, annotate `@Component`, and it is registered. A future `RoomConstraintStrategy` is just that. |
| `SchedulingContext` (record) | The appointment, the open future slots, all providers, and the trigger reason. Strategies never touch repositories. |
| `Recommendation` (record) | `slotId`, optional `alternateProviderId`, `confidence`, patient-friendly `reasoning`. Mirrors the LLM output shape in the brief. |
| `RuleBasedStrategy` | The rules in section 3. |
| `StrategyRegistry` | Collects all strategies by name; holds the active one (from config, switchable at runtime); fails fast on an unknown configured name. |
| `SchedulingEngine` | Runs the active strategy, validates its output, falls back to rules. Returns `Result(strategyName, recommendations)`. |
| `SuggestionService` | `createSuggestion(appointmentId, trigger)` and `list(status)`. |
| `SuggestionView` | Response DTO for a suggestion (slot, alternate provider, confidence, reasoning, strategy, status, trigger, timestamps). |
| `SchedulingController` | `GET/PUT /api/scheduling/strategy`. |
| `SuggestionController` | `GET /api/suggestions?status=`. |
| `NoSlotAvailableException` | 409 `NO_SLOT_AVAILABLE`. |

Endpoint additions to `AppointmentController`: `POST /api/appointments/{id}/suggest`.

## 3. The rule-based strategy

A slot is **usable** when all of these hold: it is `OPEN`, starts in the future, its provider is not `OFFLINE`, it is not the appointment's current slot, and it is at least as long as the current one.

Recommendations, in order (each tier sorted by start time, at most 3 in total):

| Tier | Slots | Confidence | Alternate provider |
|---|---|---|---|
| 1 | Same provider as the appointment | 0.90 | none |
| 2 | A different provider with the same specialty | 0.75 | that provider |
| 3 | `URGENT` appointments only: a provider with a different specialty | 0.40 | that provider |

Reasoning is written for staff to read aloud, for example: "Dr. Sarah Chen is not available, so we found Dr. Rahul Verma, who offers the same kind of care, on Tuesday 3 Nov at 4:07 PM." Tier 3 adds "please confirm with the patient".

Because offline providers are filtered out, the same rules serve both triggers: an offline provider is never tier 1, and a cancelled slot normally finds the same provider's next slot.

Known behavior, on purpose: two appointments can be offered the same slot (the demo does this). The engine does not reserve slots; the race is settled at accept time (Stage 4).

## 4. Validation and fallback (in the engine, for every strategy)

A recommendation is dropped unless its slot is in the current open-slot list, is `OPEN`, its provider is online, and a named alternate provider exists and actually owns that slot.

| Situation | Result |
|---|---|
| Active strategy returns valid options | Used; strategy label is its name, e.g. `rule` |
| Active strategy throws, or nothing valid remains | Falls back to rules; label `ai->rule-fallback` (name of the failed strategy + `->rule-fallback`) |
| The rule strategy itself throws | The error propagates (nothing left to fall back to) |

## 5. Runtime switching

`slotmind.scheduling.strategy=rule` in `application.properties` sets the active strategy at startup (an unknown name stops the app). `GET /api/scheduling/strategy` returns `{ "active": "rule", "available": ["rule"] }`. `PUT /api/scheduling/strategy` with `{ "strategy": "rule" }` switches it; an unknown name returns 404. The switch is in memory, so a restart goes back to the configured value. Both the HTTP path and the loop read the same registry. Until Stage 7 only `rule` exists.

## 6. The suggest endpoint

`POST /api/appointments/{id}/suggest` (no body), in one transaction:
1. Unknown appointment: 404. `COMPLETED` appointment: 409.
2. If a `PENDING` suggestion already exists for this appointment with trigger `INITIAL`, return it instead of creating a duplicate (the same guard Stage 5 needs).
3. Ask the engine. If there are no recommendations: 409 `NO_SLOT_AVAILABLE` and nothing is changed.
4. Otherwise mark the appointment `RESCHEDULE_PENDING`, save a `PENDING` suggestion from the best recommendation (trigger `INITIAL`, strategy label from the engine), and return the `SuggestionView`.

Nothing is booked: the slot stays `OPEN` and the appointment keeps its old slot until a human accepts (Stage 4).

## 7. Files

```
engine/      SchedulingStrategy, SchedulingContext, Recommendation, RuleBasedStrategy,
             StrategyRegistry, SchedulingEngine
service/     SuggestionService
controller/  SuggestionController, SchedulingController   (+ one method on AppointmentController)
dto/request/ SwitchStrategyModel
dto/response/ SuggestionView, StrategyView
exception/   NoSlotAvailableException
resources    application.properties: slotmind.scheduling.strategy=rule
```
`SuggestionRepository` and the `RescheduleSuggestion` entity already exist from Stage 1.

## 8. Tests

- `RuleBasedStrategyTest`: each tier; offline provider excluded; past and shorter slots excluded; the current slot excluded; tier 3 only for urgent; at most 3 results; ordering by time; reasoning is non-empty.
- `SchedulingEngineTest` (with a fake strategy): invalid slot id dropped; non-open slot dropped; alternate provider that does not own the slot dropped; strategy exception falls back and labels it; empty result falls back; the rule strategy's own failure propagates.
- `StrategyRegistryTest`: unknown configured name fails fast; `activate` switches; unknown name is rejected.
- `SuggestionServiceTest` (Mockito): creates a suggestion and marks the appointment pending; returns the existing pending one instead of a duplicate; no recommendation gives 409 and changes nothing; completed appointment gives 409; unknown id gives 404.
- Controller error-mapping test for the new 409.

## 9. How we verify (definition of done)

On freshly seeded data (provider ids and slot ids from the seed), with the app running:
1. `GET /api/scheduling/strategy` returns `rule` as active and available.
2. `POST /api/appointments/APT-001/suggest` (routine, general): recommends `SLT-005` with Dr. Rahul Verma, confidence 0.75, with readable reasoning; the appointment becomes `RESCHEDULE_PENDING`; `SLT-005` stays `OPEN`.
3. Calling it again returns the same suggestion (same id), not a new one.
4. `APT-004` (routine, specialist, 60-minute slot) and `APT-003` (urgent, pediatric, 45-minute slot) return 409 `NO_SLOT_AVAILABLE` on the seed alone: the only open slots are 30 minutes long, and a replacement must be at least as long as the current slot.
5. After creating a 60-minute open slot for Dr. Verma (`PRV-004`) and one for Dr. Okonkwo (`PRV-002`): `APT-004` gets Dr. Okonkwo at 0.75 (same specialty), and `APT-003` gets Dr. Verma at 0.40 with the "please confirm with the patient" note (urgent, other specialty).
6. `GET /api/suggestions?status=PENDING` lists them.
7. Cancel `SLT-004` and `SLT-005`, then suggest again for a fresh appointment: 409 `NO_SLOT_AVAILABLE`.
8. `PUT /api/scheduling/strategy` with an unknown name returns 404.
9. `./mvnw test` passes.

Steps 2 to 5 change demo data; I reset it afterwards by dropping the tables so the next start reseeds.

## 10. Out of scope

Accept and reject (Stage 4), events and the async loop (Stage 5), the AI strategy and any LLM call (Stage 7), UI work. Alternatives beyond the best option are returned by the engine but only the best is stored.

## 11. Open points for your review

1. **Only the best recommendation is saved** as the suggestion; the other ranked options are not stored or shown. OK, or should the response include the alternatives?
2. **Manual suggest marks the appointment `RESCHEDULE_PENDING`**, and does nothing at all when no slot exists. (Stage 5's loop will mark appointments pending even when no slot is found, so staff can see them; we'll settle that there.) OK?
3. **Strategy switch is in memory only** and resets on restart. OK?
4. **Tier 3** (another specialty) only for urgent appointments, flagged with low confidence. OK?
5. **Slot length rule:** a replacement slot must be at least as long as the current one. OK?
6. **Max 3 options** considered per request. OK?
