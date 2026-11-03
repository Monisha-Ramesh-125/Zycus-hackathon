# Stage 5 — Agentic re-schedule loop

Goal: nobody clicks a button. When a provider goes `OFFLINE` or a slot is `CANCELLED`, the system finds the affected appointments in the background and queues a suggestion for each one for front-desk approval. This is the core of the brief and of the demo.

Brief reference: T-4, design-depth questions AGT-1 (two triggers, one loop?) and ADR question 4 (loop decoupling).

## 1. How it works

```
PATCH /api/providers/{id}/status  OFFLINE ----+
                                              |  event published inside the transaction
PATCH /api/slots/{id}/status      CANCELLED --+            |
        |                                                  v
   returns at once                          after the commit, on a background thread:
   (status saved)                           RescheduleEventHandler
                                              |-- which appointments are affected?
                                              |-- for each one, in its own transaction:
                                              |      SuggestionService.createSuggestion(id, trigger)
                                              |      -> engine -> strategy (rule, later AI) -> fallback
                                              v
                                       PENDING suggestions + appointments RESCHEDULE_PENDING
                                       (the front desk then accepts or rejects: Stage 4)
```

Everything after the commit reuses what already exists: `createSuggestion` (duplicate guard, stale handling, engine, fallback) from Stages 3 and 4. Stage 5 adds the trigger, the background execution and the per-appointment error handling.

## 2. Pieces

| Piece | Job |
|---|---|
| `ProviderWentOfflineEvent(providerId)` and `SlotCancelledEvent(slotId)` | Two small events carrying ids only (the handler runs on another thread, so it reloads what it needs) |
| `ProviderService.updateStatus` | Publishes the event only when the status actually changes to `OFFLINE` (setting `OFFLINE` on an offline provider does nothing) |
| `SlotService.updateStatus` | Publishes the event only when a slot actually becomes `CANCELLED` |
| `RescheduleEventHandler` | `@TransactionalEventListener(AFTER_COMMIT)` plus `@Async`: runs only if the status change really committed, and never on the request thread |
| `AsyncConfig` | `@EnableAsync` and a bounded thread pool for this work |
| `SuggestionService.markNeedsReschedule(id)` | Marks an appointment `RESCHEDULE_PENDING` when no slot could be found, so staff can see it |
| `AppointmentView.pendingSuggestion` | The appointment list now carries its pending suggestion inline, as the UI floor in the brief asks |

## 3. Which appointments are affected

| Trigger | Appointments | Trigger reason saved |
|---|---|---|
| Provider goes `OFFLINE` | That provider's appointments in `CONFIRMED` or `RESCHEDULED` | `PROVIDER_OFFLINE` |
| Slot becomes `CANCELLED` | The appointment on that slot, if `CONFIRMED` or `RESCHEDULED` | `SLOT_CANCELLED` |

(The brief says `CONFIRMED` for the offline case; I also include `RESCHEDULED`, because a patient we already moved is just as stranded. Appointments already waiting for a reschedule are left alone, and completed ones are never touched.)

## 4. The rules the loop follows

- **Returns immediately.** The PATCH response is built from the saved status; the re-scheduling happens afterwards.
- **No duplicates.** A pending suggestion for the same appointment and trigger reason is never created twice (the guard in `createSuggestion`). Toggling a provider offline, online, offline again does not pile up suggestions.
- **One bad appointment does not stop the rest.** Each appointment runs in its own transaction inside a try/catch; failures are logged and counted, and the loop moves on.
- **A failing strategy is not a silent drop.** The engine falls back to the rule strategy (Stage 3). That already covers the AI strategy when it arrives in Stage 7.
- **No slot found is not a silent drop either.** The appointment is marked `RESCHEDULE_PENDING` with no suggestion, so it shows up for staff, who can request a suggestion later with `POST /api/appointments/{id}/suggest` once capacity exists.
- **Human checkpoint stays.** Nothing is booked by the loop. Slots stay `OPEN` until someone accepts.
- **A summary line per run** is logged: how many appointments were affected, how many suggestions created, how many had no slot, how many failed.

## 5. Design decisions (the ADR material)

**Two triggers, one loop (AGT-1, ADR 4).** Two event types, because "a provider went offline" (many appointments) and "a slot was cancelled" (usually one) are different facts with different trigger reasons. One handler class with two thin methods, each only answering "which appointments?", and both feed the same shared processing step. Adding a third trigger later (for example a room closing in sprint 2) means one new event and one new method.

**Decoupling.** The services that change status know nothing about suggestions; they only announce what happened. The handler knows nothing about HTTP.

**After-commit, not before.** The event fires only once the status change is safely stored, so the loop never acts on a change that was rolled back, and it sees the committed data.

**Trade-off, stated honestly.** If the app crashes between the commit and the handler running, the event is lost (at most once delivery). A transactional outbox would fix that but is out of scope. The safety net is the manual `POST /api/appointments/{id}/suggest`.

**Thread pool.** A bounded pool (2 core, 4 max, queue of 500) so a burst of status changes cannot exhaust the server; settings go in `application.properties`.

## 6. Files

```
event/       ProviderWentOfflineEvent, SlotCancelledEvent, RescheduleEventHandler
config/      AsyncConfig
service/     ProviderService, SlotService (publish events), SuggestionService (+markNeedsReschedule),
             AppointmentService (attach pending suggestion)
dto/response AppointmentView (+ pendingSuggestion)
repository/  SuggestionRepository (+ pending suggestions for a set of appointments, one query)
application.properties   async pool settings
```

No entity or schema changes.

## 7. Tests

You said tests are not needed for now, so none are written. Two existing service tests (`ProviderServiceTest`, `SlotServiceTest`) will need one extra mock for the event publisher so they keep passing; that is a mechanical edit. Verification is live, below.

## 8. How we verify (definition of done)

Fresh seed, app running:
1. `PATCH /api/providers/PRV-001/status` with `OFFLINE` and a reason. The response comes back at once (I time it) and shows the provider offline.
2. Within about a second, `GET /api/suggestions?status=PENDING` shows two suggestions for `APT-001` and `APT-002`, both trigger `PROVIDER_OFFLINE`, strategy `rule`, pointing at `SLT-005` (Dr. Verma, 0.75), with readable reasoning.
3. `GET /api/appointments?status=RESCHEDULE_PENDING` shows both with the suggestion inline.
4. Setting Dr. Chen `OFFLINE` again, then `AVAILABLE` and `OFFLINE` once more: still only two pending suggestions (no duplicates).
5. Cancel `SLT-001` on a fresh seed: a `SLOT_CANCELLED` suggestion appears for `APT-001`.
6. Cancel `SLT-003` (`APT-003` is urgent, pediatric, 45 minutes, and only 30-minute slots are open): `APT-003` becomes `RESCHEDULE_PENDING` with no suggestion, and is visible in the list. After creating a longer slot for Dr. Sharma, `POST /api/appointments/APT-003/suggest` produces one.
7. The full demo path through the API: offline, suggestions appear, accept `APT-001`'s (200), then accept `APT-002`'s onto the same slot (409). Slots and appointments end up consistent.
8. The app log shows one summary line per run, and `cd backend && ./mvnw test` still passes.

I reset the demo data afterwards, as before.

## 9. Out of scope

The UI (Stage 6), the AI strategy (Stage 7), the SSE stream (Stage 8), an outbox or retry sweeper for lost events, notifying patients, reacting to a provider coming back online.

## 10. Open points for your review

1. **At most once delivery of events** (no outbox), with manual suggest as the safety net. OK?
2. **No slot found:** the appointment is marked `RESCHEDULE_PENDING` without a suggestion so staff see it. This also settles open point 2 from Stage 3. Recommended: yes.
3. **Affected statuses:** `CONFIRMED` and `RESCHEDULED` for both triggers (the brief says `CONFIRMED`). OK?
4. **Two events, one handler class** with shared processing, as described in section 5. OK?
5. **Pool size:** 2 core, 4 max, queue 500. OK?
6. **`pendingSuggestion` inline on appointments** now, ahead of the UI. Recommended: yes.
7. **No new tests**, live verification only, as you asked.
