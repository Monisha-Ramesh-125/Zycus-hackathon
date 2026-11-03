# Stage 1 — Domain model and seed data

Goal: the four entities with their state rules, their repositories, and demo data loaded on startup. No endpoints yet (Stage 2). When this stage is done the app starts, MariaDB has the tables, and the seed rows are there.

Brief reference: T-1 (domain half).

## 1. What gets built

### 1.1 Package layout (base: `com.example.slotmind`)

```
entity/       Provider, Slot, Appointment, RescheduleSuggestion + 7 enums
repository/   ProviderRepository, SlotRepository, AppointmentRepository, SuggestionRepository
exception/    BusinessException, InvalidStateException, SlotUnavailableException
```

Same conventions as the inventory project: Lombok `@Getter` and `@NoArgsConstructor`, state changes as methods on the entity, exceptions extend an abstract `BusinessException` carrying an HTTP status and error code. No `GlobalExceptionHandler` yet, since there are no endpoints (Stage 2).

### 1.2 Enums (one file each, in `entity/`)

| Enum | Values |
|---|---|
| `Specialty` | `GENERAL`, `SPECIALIST`, `PEDIATRIC` |
| `ProviderStatus` | `AVAILABLE`, `BUSY`, `OFFLINE` |
| `SlotStatus` | `OPEN`, `BOOKED`, `CANCELLED` |
| `AppointmentType` | `ROUTINE`, `URGENT`, `FOLLOW_UP` |
| `AppointmentStatus` | `CONFIRMED`, `RESCHEDULE_PENDING`, `RESCHEDULED`, `COMPLETED` |
| `SuggestionStatus` | `PENDING`, `ACCEPTED`, `REJECTED` |
| `TriggerReason` | `INITIAL`, `PROVIDER_OFFLINE`, `SLOT_CANCELLED` |

All stored with `@Enumerated(EnumType.STRING)`.

### 1.3 Entities

**`Provider`** (table `providers`)
- `id` String (e.g. `PRV-001`), `name`, `specialty`, `status`
- **added:** `statusReason` (optional, e.g. "sick leave"), `statusChangedAt` (when it went offline or came back)
- `changeStatus(newStatus, reason)` (records reason and time) and `isOffline()`

**`Slot`** (table `slots`)
- `id` String, `provider` (many-to-one, lazy), `startTime` LocalDateTime, `durationMinutes` int, `status`, `@Version version`
- **added:** `statusReason` (optional, why it was cancelled)
- `book()`: only from `OPEN`, and only if the provider is not offline. Otherwise throws `SlotUnavailableException` or `InvalidStateException`.
- `release()`: `BOOKED` -> `OPEN`. A `CANCELLED` slot stays cancelled.
- `cancel(reason)`: any state -> `CANCELLED`, stores the reason.
- `isOpen()`

**`Appointment`** (table `appointments`)
- `id` String, `patientName`, `type`, `provider` (many-to-one, lazy), `slot` (one-to-one, lazy, **unique** `slot_id`), `status`, `@Version version`
- **added:** `patientPhone` (optional; the front desk calls the patient before accepting), `notes` (optional), `rescheduleCount` (how many times it has been moved), `createdAt`, `updatedAt`
- Constructor takes a booked slot and sets provider from it, status `CONFIRMED`.
- `markReschedulePending()`: from `CONFIRMED` or `RESCHEDULED`; rejected from `COMPLETED`.
- `moveTo(newSlot)`: only from `RESCHEDULE_PENDING`; sets slot and provider, status `RESCHEDULED`, increments `rescheduleCount`.
- Every state change refreshes `updatedAt`.
- `complete()`: only from `CONFIRMED` or `RESCHEDULED`.

**`RescheduleSuggestion`** (table `reschedule_suggestions`)
- `id` String, `appointment`, `recommendedSlot`, `alternateProvider` (nullable), `confidence` double, `reasoning` (length 1000), `strategy` String (which strategy produced it), `status`, `triggerReason`, `createdAt`, `@Version version`
- **added:** `decidedAt` (when staff accepted or rejected), `decisionNote` (optional, e.g. "patient declined, wants mornings")
- `accept(note)` and `reject(note)`: only from `PENDING`, otherwise `InvalidStateException`; they set `decidedAt` and the note.
- Indexes on `status` and `appointment_id`.

### 1.4 Repositories

Plain `JpaRepository<Entity, String>` interfaces, annotated `@Repository` like the inventory project. Stage 1 adds only the queries Stage 2 and 3 will clearly need, so we don't rework them later:
- `SlotRepository`: `search(status, providerId)` and `findOpenAfter(time)`, both with the provider fetched.
- `AppointmentRepository`: `search(status)` with provider and slot fetched; `findIdsByProvider(providerId, statuses)`; `findIdsBySlot(slotId, statuses)`.
- `SuggestionRepository`: `search(status)`; find pending by appointment and trigger reason.

### 1.5 Seed data

The seed is a SQL script, `src/main/resources/db/seed.sql`, taken from Addendum A. Slot times use `DATE_ADD(NOW(), INTERVAL N MINUTE)`, because plain `NOW() + 60` in MariaDB adds 60 seconds, not minutes.

How it loads (dev profile only):
- `spring.sql.init.mode=always` and `spring.sql.init.data-locations=classpath:db/seed.sql`
- `spring.jpa.defer-datasource-initialization=true`, so Hibernate creates the tables first and the script runs after
- Every statement is `INSERT IGNORE`, so restarts do not duplicate rows or fail
- The dev JDBC URL sets `connectionTimeZone=LOCAL&forceConnectionTimeZoneToSession=true`, so `NOW()` uses the app's time zone, not the database server's (the Docker MariaDB runs in UTC)
- qa and prod set `spring.sql.init.mode=never`

Data (minutes from startup):

| Providers | Specialty | Status |
|---|---|---|
| PRV-001 Dr. Sarah Chen | GENERAL | BUSY |
| PRV-002 Dr. James Okonkwo | SPECIALIST | AVAILABLE |
| PRV-003 Dr. Priya Sharma | PEDIATRIC | BUSY |
| PRV-004 Dr. Rahul Verma | GENERAL | AVAILABLE |
| PRV-005 Dr. Ananya Iyer | SPECIALIST | BUSY |

| Slot | Provider | Start | Min | Status |
|---|---|---|---|---|
| SLT-001 | PRV-001 | +60 | 30 | BOOKED |
| SLT-002 | PRV-001 | +120 | 30 | BOOKED |
| SLT-003 | PRV-003 | +90 | 45 | BOOKED |
| SLT-004 | PRV-002 | +180 | 30 | OPEN |
| SLT-005 | PRV-004 | +150 | 30 | OPEN |
| SLT-006 | PRV-005 | +240 | 60 | BOOKED |

| Appointment | Patient | Type | Provider / Slot |
|---|---|---|---|
| APT-001 | John Martinez | ROUTINE | PRV-001 / SLT-001 |
| APT-002 | Emily Watson | FOLLOW_UP | PRV-001 / SLT-002 |
| APT-003 | Aisha Patel | URGENT | PRV-003 / SLT-003 |
| APT-004 | Robert Kim | ROUTINE | PRV-005 / SLT-006 |

Because `INSERT IGNORE` skips existing ids, the slot times are fixed at the first startup. After a long idle period they are in the past; to refresh, empty the four tables and restart. `created_at` and `updated_at` are set with `NOW()` in the script; the new optional fields stay null except `patient_phone`, which gets sample numbers. The SQL bypasses entity rules, so the script itself must keep the data consistent (every `BOOKED` slot has its appointment).

## 2. Files touched or created

| File | Action |
|---|---|
| `entity/*` (4 entities, 7 enums) | create |
| `repository/*` (4) | create |
| `exception/BusinessException`, `InvalidStateException`, `SlotUnavailableException` | create |
| `src/main/resources/db/seed.sql` | create |
| `application-dev.properties` | add the `spring.sql.init.*` and `defer-datasource-initialization` settings |
| `application-qa.properties`, `application-prod.properties` | add `spring.sql.init.mode=never` |
| `src/test/.../entity/*Test` | create (see §3) |

No changes to `pom.xml` and nothing in `frontend/`.

## 3. Tests

Unit tests with no Spring context and no Docker, on the entity rules:
- `SlotTest`: book from `OPEN` works; cancel stores its reason; book from `BOOKED` or `CANCELLED` throws; book with an offline provider throws; release frees only `BOOKED`; cancel always cancels.
- `AppointmentTest`: legal transitions work; `moveTo` increments `rescheduleCount`; `moveTo` outside `RESCHEDULE_PENDING` throws; `complete` from `RESCHEDULE_PENDING` throws.
- `RescheduleSuggestionTest`: accept and reject only from `PENDING`; a second decision throws.

Plus the existing `SlotmindApplicationTests` context-load smoke test (it also exercises the seed script).

## 4. How we verify (definition of done)

1. `./mvnw test` passes.
2. `./mvnw spring-boot:run` starts without errors (use a free port if 8082 is busy).
3. In MariaDB (`slotmind` database): tables `providers`, `slots`, `appointments`, `reschedule_suggestions` exist.
4. Row counts are 5, 6, 4 and 0. The unique index on `appointments.slot_id` and the `version` columns are present.
5. Restarting the app does not add duplicate rows.

## 5. Out of scope for this stage

Controllers, DTOs, `GlobalExceptionHandler`, scheduling engine, events, UI changes. The only visible result is the database content.

## 6. Open points for your review

1. **Ids:** strings (`PRV-001`) as in the seed. New records created through the API in Stage 2 get a prefix plus a short random suffix. Confirmed.
2. **Seed:** a SQL script (`db/seed.sql`) using `DATE_ADD(NOW(), INTERVAL N MINUTE)`. Confirmed.
3. **Suggestion and extra fields:** kept `strategy` and `createdAt`, and added the useful extras listed under each entity (`statusReason`, `statusChangedAt`, `patientPhone`, `notes`, `rescheduleCount`, `createdAt`/`updatedAt`, `decidedAt`, `decisionNote`). Confirmed. Stage 2 exposes the optional ones in the API.
4. **Optimistic locking** on `Slot`, `Appointment` and `RescheduleSuggestion` from the start, so Stage 4 doesn't need a schema change. Confirmed.
