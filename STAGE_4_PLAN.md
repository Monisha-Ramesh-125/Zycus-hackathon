# Stage 4 — Transactional accept

Goal: the human checkpoint. Front-desk staff accept or reject a pending suggestion. Accepting moves the appointment atomically and can never double book, even when two people click at once.

Brief reference: T-1 (`PATCH /suggestions/{id}`, "transactional accept") and design-depth question AGT-3 (no double booking on accept).

## 1. Endpoint

`PATCH /api/suggestions/{id}`
```json
{ "decision": "ACCEPT", "note": "Patient agreed by phone" }
```
`decision` is `ACCEPT` or `REJECT`; `note` is optional (the front desk's record of the patient call). Returns the updated `SuggestionView`.

| Result | HTTP |
|---|---|
| Decision applied | 200 |
| Unknown suggestion | 404 |
| Suggestion already accepted or rejected | 409 `INVALID_STATE` |
| Appointment is no longer waiting for a reschedule | 409 `INVALID_STATE` |
| Recommended slot was taken, cancelled, or its provider went offline | 409 `SLOT_UNAVAILABLE` |
| Lost a race and retries ran out | 409 `CONCURRENT_UPDATE` |
| Missing or unknown `decision` | 400 |

## 2. Accept: one transaction, four changes

`SuggestionService.decide(id, decision, note)` is `@Transactional`. For `ACCEPT`:

1. Load the suggestion; it must be `PENDING`.
2. Load its appointment; it must be `RESCHEDULE_PENDING`.
3. `newSlot.book()`: the entity rule (must be `OPEN`, provider online).
4. `oldSlot.release()`: frees the old slot (`BOOKED` -> `OPEN`; a `CANCELLED` slot stays cancelled).
5. `appointment.moveTo(newSlot)`: appointment becomes `RESCHEDULED`, provider follows the new slot, `rescheduleCount` goes up.
6. `suggestion.accept(note)`: records `decidedAt` and the note.
7. Any other `PENDING` suggestion for the same appointment is rejected with the note "Superseded by <id>".

All of it commits together or none of it does. The booking rule runs first, so a refused accept changes nothing.

`REJECT`: `suggestion.reject(note)` only. The appointment stays `RESCHEDULE_PENDING` so staff can ask for a new suggestion.

## 3. How double booking is prevented (the AGT-3 answer)

Three layers, from the entities built in Stage 1:

| Layer | What it stops |
|---|---|
| `Slot.book()` | Booking a slot that is not `OPEN` (the normal case: a second accept arrives after the first finished) |
| `@Version` on `Slot`, `Appointment`, `RescheduleSuggestion` | Two accepts running at the same instant, both having read the slot as `OPEN`: the second one's update fails because the version changed |
| Unique index on `appointments.slot_id` | Last line of defence if everything above were bypassed |

**Retry:** when the version check fails, or MariaDB picks the request as the loser of a deadlock between two simultaneous accepts, the whole accept is retried (3 attempts, 50 ms apart) using Spring's `@Retryable`, as in the inventory project. On the retry the slot reads as `BOOKED`, so the loser gets a clean 409 `SLOT_UNAVAILABLE` instead of a vague error. If retries run out, the error handler returns 409 `CONCURRENT_UPDATE`, for the version failure and for a deadlock alike.

Why optimistic and not pessimistic locking: conflicts are rare and short, so nothing waits on a lock, and the loser just gets a clear message.

Setup note: `@EnableResilientMethods` goes back on `SlotmindApplication` (I removed it when the project was renamed). The retry has to wrap the transaction, not sit inside it; the concurrency test below proves it. If Spring's ordering does not give that, the fallback is a tiny wrapper bean that retries and calls the transactional method.

## 4. Stale suggestions (a small change to Stage 3's `createSuggestion`)

Two appointments can be offered the same slot (the seed does exactly this). After one is accepted, the other's pending suggestion points at a slot that is gone. Accepting it correctly fails with 409, but asking for a new suggestion must not just return the same dead one.

So `createSuggestion` now checks the existing pending suggestion: if its slot is no longer `OPEN` or its provider is offline, it is rejected with the note "Slot no longer available" and a fresh suggestion is created. In the demo this has a nice effect: Emily's stale suggestion is replaced by one for the slot John just freed.

## 5. Optional improvement: do not re-offer a slot the patient already refused

If a suggestion is rejected, the next suggestion for that appointment would otherwise offer the same slot again. The engine context gets the appointment's previously rejected slot ids and the rule strategy skips them. Small change (one field on `SchedulingContext`, one filter, one test). Recommended; see open point 3.

## 6. Files

```
service/     SuggestionService          + decide(id, decision, note); stale handling in createSuggestion
controller/  SuggestionController       + PATCH /api/suggestions/{id}
dto/request/ SuggestionDecisionModel
dto/response SuggestionView             (unchanged)
repository/  SuggestionRepository       + a query for rejected slot ids of an appointment (if point 3 is accepted)
engine/      SchedulingContext, RuleBasedStrategy   (only if point 3 is accepted)
SlotmindApplication                     + @EnableResilientMethods
src/test/resources/application-test.properties      separate test schema, see section 7
```

## 7. Tests

**Unit tests (Mockito, no database):**
- accept moves everything: new slot `BOOKED`, old slot `OPEN`, appointment `RESCHEDULED` on the new slot with count 1, suggestion `ACCEPTED` with time and note
- accept frees the old slot only if it was booked (a cancelled old slot stays cancelled)
- accept rejects the other pending suggestions of that appointment
- accept of a taken, cancelled or offline-provider slot throws and changes nothing
- accept of an already decided suggestion, or of an appointment that is not `RESCHEDULE_PENDING`, throws
- reject only changes the suggestion
- unknown id gives 404
- stale pending suggestion is replaced on the next `createSuggestion`

**Concurrency integration test (real MariaDB, the heart of this stage):**
- Runs against a separate schema `slotmind_test` on the existing Docker MariaDB (`create-drop`, no seed, test profile), so it never touches demo data. It needs that database running, like the existing context-load test.
- Case A: two threads accept two different suggestions that point at the same slot, released together by a latch. Exactly one succeeds, the other gets `SlotUnavailableException`; the slot is `BOOKED` once; exactly one appointment holds it.
- Case B: eight suggestions for one slot, eight threads. Exactly one winner.
- Case C: the same suggestion accepted twice at once: one success, one `InvalidStateException`; the appointment's `rescheduleCount` is 1.

**Controller test:** bad or missing `decision` gives 400; the 409 cases map to the right codes.

## 8. How we verify (definition of done)

Fresh seed, app running:
1. Suggest for `APT-001` and `APT-002`: both point at `SLT-005` (Dr. Verma).
2. Accept `APT-001`'s suggestion: 200, status `ACCEPTED`. `SLT-001` is `OPEN`, `SLT-005` is `BOOKED`, `APT-001` is `RESCHEDULED` on `SLT-005` with Dr. Verma and `rescheduleCount` 1.
3. Accept `APT-002`'s suggestion: 409 `SLOT_UNAVAILABLE`. `APT-002` is unchanged (`RESCHEDULE_PENDING`, still on `SLT-002`).
4. Accept `APT-001`'s suggestion again: 409 `INVALID_STATE`.
5. Suggest again for `APT-002`: the stale suggestion is rejected and a new one offers `SLT-001` (just freed by John), same provider, confidence 0.90.
6. Reject that new suggestion with a note: `REJECTED`, appointment still `RESCHEDULE_PENDING`.
7. Concurrency by hand: two parallel `PATCH` requests accepting two suggestions that share a slot: one 200, one 409.
8. `./mvnw test` passes, including the concurrency cases.

Steps 1 to 6 depend on the seed's slot times still being in the future (they are set at first start, +60 minutes onwards), so run them soon after a fresh start. I reset the demo data afterwards.

## 9. Out of scope

Events and the async loop (Stage 5), UI (Stage 6), AI (Stage 7), notifying the patient, reopening cancelled slots.

## 10. Open points for your review

1. **A second accept on a taken slot returns 409** and leaves its suggestion `PENDING` (it is only cleaned up when someone asks for a new suggestion, see section 4). The alternative is to auto-reject every other suggestion pointing at a slot the moment it is booked. Recommended: keep the 409, because it is exactly the behaviour the brief's walkthrough describes.
2. **Reject leaves the appointment `RESCHEDULE_PENDING`**, waiting for a new suggestion. OK?
3. **Skip slots the patient already refused** when suggesting again (section 5). Recommended: yes.
4. **Retry policy:** 3 attempts, 50 ms apart. OK?
5. **Concurrency tests use a separate `slotmind_test` schema** on the existing Docker MariaDB rather than Testcontainers (AssistQ's way), to avoid new infrastructure. OK?
6. **The `note` is optional.** The brief says the front desk confirms with the patient first; making the note mandatory would enforce a record of that. Recommended: optional.
