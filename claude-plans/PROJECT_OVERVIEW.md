# SlotMind — AI Re-Scheduling Engine

Source: `slotmind-brief.html` (solo hackathon brief, 5 hours, 115 pts).
This file is the overview and the staged build plan. We build one stage at a time; each stage ends with a check that must pass before the next begins.

## 1. The problem

CareBridge clinics book patients with providers in time slots. When a provider calls in sick or a slot is cancelled, a coordinator has to phone patients and rebook them by hand. That is slow, depends on who is at the desk, and causes double bookings.

**SlotMind reacts automatically.** When a provider goes `OFFLINE` or a slot is `CANCELLED`, the system finds the affected appointments, proposes a new slot for each one (rules or AI), and queues the proposals for front-desk approval. Nothing is rebooked until a human accepts.

Out of scope for this sprint: patient portal, billing, waitlist auto-fill, notifications, room and equipment constraints. The design must leave room for them (see §8).

## 2. Stack

| Part | Choice |
|---|---|
| Backend | Spring Boot 4.1.1, Java 21, Spring Data JPA, Lombok |
| Database | MariaDB (Docker container on port 3306, database `slotmind`) |
| Frontend | Angular 17 in `frontend/`, dev proxy `/api` -> backend |
| LLM | Gemini, Groq or Ollama behind a gateway (added in Stage 7) |
| Backend port | 8082 (dev profile) |

Backend layout follows the inventory project: `controller`, `service`, `repository`, `entity`, `dto`, `exception`, plus `engine`, `event` and `ai`. Every class ends in a role suffix. Settings live in `application*.properties`, with dev, qa and prod profiles.

## 3. Domain model

| Entity | Fields | Status values |
|---|---|---|
| `Provider` | id, name, specialty (`GENERAL`, `SPECIALIST`, `PEDIATRIC`) | `AVAILABLE`, `BUSY`, `OFFLINE` |
| `Slot` | id, provider, startTime, durationMinutes | `OPEN`, `BOOKED`, `CANCELLED` |
| `Appointment` | id, patientName, type (`ROUTINE`, `URGENT`, `FOLLOW_UP`), provider, slot | `CONFIRMED` -> `RESCHEDULE_PENDING` -> `RESCHEDULED` -> `COMPLETED` |
| `RescheduleSuggestion` | id, appointment, recommendedSlot, alternateProvider (optional), confidence, reasoning, triggerReason (`INITIAL`, `PROVIDER_OFFLINE`, `SLOT_CANCELLED`), strategy used | `PENDING` -> `ACCEPTED` or `REJECTED` |

Rules live on the entities, not in controllers:
- A slot can only be booked while `OPEN`, and never with an `OFFLINE` provider.
- Slots and appointments carry `@Version`. One slot can carry only one appointment (unique constraint).
- State changes are methods on the entity that reject illegal transitions.

## 4. Scheduling engine

One contract: `SchedulingStrategy.recommend(context) -> ordered recommendations`. A `StrategyRegistry` holds every strategy by name. The active one is set by config and can be switched at runtime. The HTTP path and the async loop both call the same `SchedulingEngine`.

Rule-based strategy, in order:
1. The same provider's nearest open slot.
2. The nearest open slot with a provider of the same specialty.
3. For `URGENT` appointments only, the nearest open slot with anyone.

The engine validates every recommendation against real data (slot exists, is `OPEN`, provider is not offline, alternate provider owns the slot). If the active strategy throws or returns nothing usable, the engine falls back to the rules, so a failure is never a silent drop. A future `RoomConstraintStrategy` is "implement the interface and register it".

## 5. The re-schedule loop

```
PATCH provider OFFLINE  ->  ProviderWentOfflineEvent  \
                                                        >  one async handler  ->  per appointment:
PATCH slot CANCELLED    ->  SlotCancelledEvent        /      engine -> suggestion (PENDING)
```

- The PATCH endpoints return immediately. Events fire after the transaction commits, and the handler runs on another thread.
- Offline: all `CONFIRMED` appointments of that provider. Cancelled: the appointment on that slot.
- A `PENDING` suggestion for the same appointment and trigger reason is never duplicated.
- One failing appointment does not stop the others.
- Affected appointments move to `RESCHEDULE_PENDING`. Nothing is booked yet.

## 6. Accepting a suggestion

`PATCH /suggestions/{id}` (accept or reject) is the human checkpoint. Accept runs in one transaction: book the new slot, free the old slot, move the appointment, mark the suggestion `ACCEPTED`, close other pending suggestions for that appointment.

Race protection: two suggestions can point at the same slot (the demo seed does exactly this). The `@Version` check plus the unique slot constraint make the second accept fail with HTTP 409. A retry on optimistic-lock failure re-reads the slot and returns a clean "slot no longer available".

## 7. Front-desk UI (Angular)

- Appointments in `RESCHEDULE_PENDING` with the suggestion inline: new time, provider, confidence, patient-friendly reasoning.
- Accept and Reject buttons.
- Badge per trigger: `PROVIDER_OFFLINE`, `SLOT_CANCELLED`, manual.
- Provider list with status (toggle OFFLINE), slot list, appointment list.
- Polling, loading and error states.
- Ceiling extras if time allows: day view, colour by appointment type, provider load.

## 8. Extensibility (sprint 2 and 3, not built now)

Room and equipment constraints plug in as another strategy. Patient preferences, duration per type and daily provider limits extend the engine context. Waitlist fill, notifications and multi-clinic routing subscribe to the same events.

## 9. Demo path (must work end to end)

1. Book an appointment for a provider and slot.
2. `PATCH PRV-001` (Dr. Sarah Chen) to `OFFLINE`.
3. Suggestions for `APT-001` and `APT-002` appear without further action, badged `PROVIDER_OFFLINE`.
4. Accept one: slots and appointment update atomically.
5. Accept the other onto the same slot: 409, no double booking.

Seed data (Addendum A): 5 providers, 6 slots, 4 appointments, with slot times as minutes from now (`NOW() + N minutes`). It is loaded from `db/seed.sql` in dev, using `DATE_ADD(NOW(), INTERVAL N MINUTE)`.

## 10. Stages

Each stage lists what we build and how we know it works.

| Stage | Scope | Done when | Brief ref |
|---|---|---|---|
| **0. Skeleton** (done) | Spring Boot project on MariaDB, Angular app, `/api` proxy, `/api/ping` | Page shows "Backend: connected" | |
| **1. Domain and seed** | 4 entities, status enums, state rules, repositories, SQL seed script | App starts, tables created, seed rows present | T-1 |
| **2. Core API** | `POST/GET /appointments`, `PATCH /providers/{id}/status`, `PATCH /slots/{id}/status`, `GET /providers`, `GET /slots`, DTOs, error handling | Book, list and status changes work in curl; bad input returns clean errors | T-1 |
| **3. Scheduling engine** | Strategy interface, rule strategy, registry, validation and fallback, `POST /appointments/{id}/suggest`, runtime strategy switch | On-demand suggestion returns the right slot for each tier | T-2 |
| **4. Transactional accept** | `PATCH /suggestions/{id}`, atomic accept, optimistic lock and retry, 409 on conflict | Concurrent accepts on one slot: exactly one succeeds | T-1, AGT-3 |
| **5. Agentic loop** | Events, async handler, duplicate guard, per-appointment error isolation | PATCH `PRV-001` offline returns at once; two suggestions appear | T-4 |
| **6. Angular UI** | Screens from §7 | Full demo path works in the browser | T-5 |
| **7. AI strategy** (on your word) | `LLMGateway`, `AiSchedulingStrategy`, two prompts (offline vs cancelled), JSON validation, timeout, rule fallback | AI suggestions appear; killing the LLM still yields rule suggestions | T-3 |
| **8. SSE stream** (bonus) | `POST /appointments/{id}/suggest/stream` | Reasoning streams in the UI | T-3 bonus |
| **9. ADR and demo** | `ADR.md` (Context, Options, Decision, Tradeoffs) for the 5 decisions; README with run steps; 5-minute demo script | Demo runs clean from a fresh start | T-6 |

Order notes: Stages 1 to 6 give a complete working product on rules alone. The LLM arrives in Stage 7 behind the registry, so nothing before it changes.

## 11. Scoring map

| Item | Points | Stage |
|---|---|---|
| Domain and API | 20 | 1, 2, 4 |
| Scheduling engine | 25 | 3 |
| AI integration | 25 | 7 |
| Agentic loop | 15 | 5 |
| UI floor / ceiling | 12 / +8 | 6 |
| ADR and walkthrough | 20 | 9 |
| SSE bonus | +5 | 8 |

## 12. Decisions to confirm before Stage 1

1. Seed times are minutes from now (the brief gives no unit).
2. Offline trigger covers `CONFIRMED` and `RESCHEDULED` appointments (the brief says only `CONFIRMED`).
3. Schema by Hibernate `ddl-auto=update` in dev. Flyway can come later if wanted.
4. Ids are strings such as `PRV-001`, as in the seed. New records get generated ids with a prefix.
