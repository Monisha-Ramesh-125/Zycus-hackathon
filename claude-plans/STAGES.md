# SlotMind — Stages

One stage at a time. Each stage ends with a check that must pass before the next begins.
Full context is in `PROJECT_OVERVIEW.md`. Per-stage plans are written just before each stage is implemented.

| # | Stage | What gets built | Done when | Brief ref | Plan file | Status |
|---|---|---|---|---|---|---|
| 0 | Skeleton | Spring Boot project on MariaDB, Angular 17 app, `/api` dev proxy, `/api/ping` | Page shows "Backend: connected" | | | Done |
| 1 | Domain and seed | 4 entities, status enums, state rules, repositories, SQL seed script | App starts, tables created, seed rows present | T-1 | `STAGE_1_PLAN.md` | Done |
| 2 | Core API | `POST/GET /appointments`, `PATCH /providers/{id}/status`, `PATCH /slots/{id}/status`, `GET /providers`, `GET /slots`, DTOs, error handling | Book, list and status changes work in curl; bad input returns clean errors | T-1 | `STAGE_2_PLAN.md` | Done |
| 3 | Scheduling engine | Strategy interface, rule strategy, registry, validation and fallback, `POST /appointments/{id}/suggest`, runtime strategy switch | On-demand suggestion returns the right slot for each tier | T-2 | `STAGE_3_PLAN.md` | Done |
| 4 | Transactional accept | `PATCH /suggestions/{id}`, atomic accept, optimistic lock and retry, 409 on conflict | Concurrent accepts on one slot: exactly one succeeds | T-1, AGT-3 | `STAGE_4_PLAN.md` | Done (no new tests written, by request) |
| 5 | Agentic loop | Events, async handler, duplicate guard, per-appointment error isolation | PATCH `PRV-001` offline returns at once; two suggestions appear | T-4 | `STAGE_5_PLAN.md` | Done (no new tests written, by request) |
| 6 | Angular UI | Pending list with inline suggestion, accept/reject, trigger badges, provider/slot/appointment lists, polling, loading and error states | Full demo path works in the browser | T-5 | | Done. Verified in headless Chrome against a live backend: full demo click-through (offline, proposals, accept, friendly 409), booking, slot create/cancel, mobile width, 5 s polling pause when hidden. Production build clean. No unit tests, by request |
| 7 | AI strategy | `LLMGateway`, `AiSchedulingStrategy`, two prompts (offline vs cancelled), JSON validation, timeout, rule fallback | AI suggestions appear; killing the LLM still yields rule suggestions | T-3 | `STAGE_7_PLAN.md` | Done: live Gemini (gemini-3.5-flash-lite) verified, fallback verified (no new tests, by request) |
| 8 | SSE stream (bonus) | `POST /appointments/{id}/suggest/stream` | Reasoning streams in the UI | T-3 bonus | | Not started |
| 9 | ADR and demo | `ADR.md` (Context, Options, Decision, Tradeoffs) for the 5 decisions, README with run steps, 5-minute demo script | Demo runs clean from a fresh start | T-6 | | Not started |

## Notes

- Stages 1 to 6 give a complete working product on rules alone. The LLM arrives in Stage 7 behind the strategy registry, so earlier stages do not change.
- Stage 8 is optional bonus (+5 pts).
- Update the Status column as each stage is finished.
