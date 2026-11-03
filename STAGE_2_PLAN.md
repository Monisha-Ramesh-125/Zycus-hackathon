# Stage 2 — Core API

Goal: the HTTP layer for providers, slots and appointments. After this stage you can book an appointment, list things and change provider and slot status with curl. Suggestions, the engine and the async loop come later (Stages 3 to 5).

Brief reference: T-1 (API half).

## 1. Endpoints

Paths are prefixed with `/api` (the brief lists them without it). The Angular dev proxy already forwards `/api/*` to the backend, so this avoids any CORS setup.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/appointments` | Book an appointment on a provider and slot |
| `GET` | `/api/appointments?status=` | List, optionally filtered by status |
| `GET` | `/api/appointments/{id}` | One appointment |
| `GET` | `/api/providers` | List providers with status |
| `PATCH` | `/api/providers/{id}/status` | Change status (`AVAILABLE`, `BUSY`, `OFFLINE`) with optional reason |
| `GET` | `/api/slots?status=&providerId=` | List slots, filterable |
| `PATCH` | `/api/slots/{id}/status` | Cancel a slot, with optional reason |
| `POST` | `/api/providers` | Create a provider (small extra, for demo data) |
| `POST` | `/api/slots` | Create an open slot (small extra, for demo data) |

Not in this stage: `POST /appointments/{id}/suggest` (Stage 3), `PATCH /suggestions/{id}` (Stage 4), the async reaction to `OFFLINE` and `CANCELLED` (Stage 5). In Stage 2 those two PATCH calls only save the new status. The service method that saves it is where Stage 5 will publish its event.

### Request and response shapes

Booking request:
```json
{ "patientName": "Jane Roe", "type": "ROUTINE", "providerId": "PRV-004",
  "slotId": "SLT-005", "patientPhone": "+1-555-0199", "notes": "first visit" }
```
Booking response (`AppointmentView`): id, patientName, type, status, patientPhone, notes, rescheduleCount, createdAt, nested `provider` (id, name, specialty, status) and `slot` (id, startTime, durationMinutes, status).

Status change request: `{ "status": "OFFLINE", "reason": "sick leave" }`. The response is the updated provider or slot view.

Error response (same shape as the inventory project): `{ "errorCode": "SLOT_UNAVAILABLE", "message": "..." }`.

## 2. Business rules in the services

**Booking** (`AppointmentService.book`, one transaction):
1. Load the provider and the slot; unknown ids give 404.
2. The slot must belong to the given provider, otherwise 400.
3. `slot.book()` (entity rule): the slot must be `OPEN` and its provider must not be offline, otherwise 409.
4. Create the `Appointment` (status `CONFIRMED`) with a generated id and save it.
5. If two requests race for one slot, the `@Version` check or the unique `slot_id` index rejects the loser with 409.

**Provider status change:** any change to a valid status is allowed and records the reason and time. (Stage 5 adds the event for `OFFLINE`.)

**Slot status change:** only `CANCELLED` is accepted (`slot.cancel(reason)`). Setting `OPEN` or `BOOKED` directly returns 400, because booking goes through the appointment endpoint and reopening a slot that still has an appointment attached could double book it. Cancelling a booked slot leaves its appointment `CONFIRMED`; Stage 5 is what reacts to it.

**Create provider / slot:** validated input; a new slot starts `OPEN`, must be in the future and have a positive duration.

**Ids for new records:** prefix plus 8 random characters, e.g. `APT-7F3K9Q2X`, `SLT-...`, `PRV-...` (one small `IdGenerator`).

## 3. Structure

Following the inventory project's layering, with DTO naming borrowed from AssistQ:

```
controller/  AppointmentController, ProviderController, SlotController
service/     AppointmentService, ProviderService, SlotService
dto/request/ BookAppointmentModel, UpdateProviderStatusModel, UpdateSlotStatusModel,
             CreateProviderModel, CreateSlotModel
dto/response/ AppointmentView, ProviderView, SlotView, ErrorResponse
exception/   NotFoundException (404), GlobalExceptionHandler
util/        IdGenerator
```

- Controllers are thin: validate input with `@Valid`, call one service method, return the view.
- Services are `@Transactional` and build the views inside the transaction (the app runs with `open-in-view=false`, so lazy relations must be read there).
- Views have a static `from(entity)` method, like AssistQ's `...View` classes.
- The booking rules stay on the entities; the services load, coordinate and save.

`GlobalExceptionHandler` maps:

| Cause | HTTP |
|---|---|
| `BusinessException` (not found, slot unavailable, invalid state) | its own status (404 / 409) |
| Bean validation failure | 400 `VALIDATION_ERROR` with field messages |
| Unreadable JSON or an unknown enum value | 400 `BAD_REQUEST` |
| Optimistic-lock failure, unique-constraint violation | 409 `CONCURRENT_UPDATE` |

## 4. Small entity changes needed

None to the rules. Optional: switch `createdAt` and `updatedAt` to a shared `BaseEntity` (see open point 1).

## 5. Tests

- **Service unit tests** (Mockito, no database): booking succeeds; unknown provider or slot gives 404; slot not belonging to the provider gives 400; booked, cancelled or offline-provider slot gives 409; slot cancel stores the reason; only `CANCELLED` is accepted for slots.
- **Controller tests** (`@WebMvcTest` with mocked services): validation errors return 400 in the standard error shape; domain exceptions return the right status; enum typos return 400.
- Plus the manual curl checklist below against the real database.

## 6. How we verify (definition of done)

With the app running and seeded:
1. `GET /api/providers` returns 5 providers; `GET /api/slots?status=OPEN` returns 2 slots; `GET /api/appointments` returns 4.
2. `POST /api/appointments` on open slot `SLT-005` with provider `PRV-004` returns the new appointment; the slot is now `BOOKED`.
3. The same booking again returns 409 `SLOT_UNAVAILABLE`.
4. Booking `SLT-004` with provider `PRV-001` (mismatch) returns 400.
5. `PATCH /api/providers/PRV-001/status` to `OFFLINE` with a reason returns the updated provider; `GET /api/providers` shows it and the reason.
6. Booking an open slot of an offline provider returns 409.
7. `PATCH /api/slots/SLT-004/status` to `CANCELLED` works; to `OPEN` returns 400.
8. A request with a missing `patientName` or a bad `type` returns 400 with a readable message.
9. `./mvnw test` passes.
10. The Angular page still shows "Backend: connected".

Note: step 5 changes the demo data (Dr. Chen offline). Reset by setting her back to `BUSY`, or by emptying the tables and restarting.

## 7. Out of scope

Suggestions and their endpoints, the scheduling engine, events and async handling, authentication, pagination (the data set is tiny), Angular screens.

## 8. Open points for your review

1. **BaseEntity:** introduce AssistQ-style `BaseEntity` (`createdAt`, `modifiedAt` via Hibernate annotations) and remove the hand-written timestamp code from the entities? It is a small refactor of Stage 1. Recommended: yes.
2. **DTO naming:** `...Model` for requests and `...View` for responses, as in AssistQ. Alternative: the inventory project's `...Request` / `...Response`. Recommended: AssistQ style.
3. **`/api` prefix** on every path, differing from the brief's bare paths. Recommended: yes.
4. **Extras:** `POST /api/providers`, `POST /api/slots` and `GET /api/appointments/{id}` are not in the brief. Keep them?
5. **Slot reopen:** deferred (only cancel in this stage), for the double-booking reason in section 2. OK?
