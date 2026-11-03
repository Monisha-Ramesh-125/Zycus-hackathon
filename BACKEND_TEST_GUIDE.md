# SlotMind backend — test and understanding guide

Backend = Stages 1 to 5 and 7. Everything below is plain `curl` against the running app.

## 1. Start it

```bash
cd "/Users/terzo/Downloads/inventoryservice 2"

# optional, for the AI strategy (key stays in your shell, never in the project)
export GEMINI_API_KEY="$(cat ~/.slotmind-gemini-key)"

cd backend && ./mvnw spring-boot:run
# if port 8082 is busy:  ./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8090
```

- Database: the Docker MariaDB on `localhost:3306`, database `slotmind`, user `root` / password `root`. The app creates the tables and loads the demo data on first start.
- Examples below use `U=localhost:8082/api`. Set `U=localhost:8090/api` if you used another port.

```bash
U=localhost:8082/api
H='Content-Type: application/json'
```

## 2. Reset the demo data (do this before each full run)

The seed slots are timed relative to the first start (+60 min to +240 min), so after a while they are in the past. To start fresh:

```bash
mariadb -uroot -proot -h127.0.0.1 --skip-ssl slotmind -e "SET FOREIGN_KEY_CHECKS=0; drop table if exists reschedule_suggestions, appointments, slots, providers; SET FOREIGN_KEY_CHECKS=1;"
```
Then restart the app; it recreates and reseeds everything.

## 3. The demo data

| Provider | Specialty | Status |
|---|---|---|
| PRV-001 Dr. Sarah Chen | GENERAL | BUSY |
| PRV-002 Dr. James Okonkwo | SPECIALIST | AVAILABLE |
| PRV-003 Dr. Priya Sharma | PEDIATRIC | BUSY |
| PRV-004 Dr. Rahul Verma | GENERAL | AVAILABLE |
| PRV-005 Dr. Ananya Iyer | SPECIALIST | BUSY |

Appointments: `APT-001` John (routine) and `APT-002` Emily (follow-up) both with Dr. Chen; `APT-003` Aisha (urgent, pediatric, 45 min); `APT-004` Robert (routine, specialist, 60 min).
Open slots: `SLT-004` (Dr. Okonkwo) and `SLT-005` (Dr. Verma), both 30 minutes.

## 4. All endpoints

| Method | Path | What it does |
|---|---|---|
| GET | `/api/ping` | Health check |
| GET | `/api/providers` | List providers |
| POST | `/api/providers` | Create a provider |
| PATCH | `/api/providers/{id}/status` | Change status; `OFFLINE` starts the background loop |
| GET | `/api/slots?status=&providerId=` | List slots |
| POST | `/api/slots` | Create an open slot |
| PATCH | `/api/slots/{id}/status` | Cancel a slot (only `CANCELLED` allowed); starts the loop |
| POST | `/api/appointments` | Book an appointment |
| GET | `/api/appointments?status=` | List; waiting ones carry `pendingSuggestion` inline |
| GET | `/api/appointments/{id}` | One appointment |
| POST | `/api/appointments/{id}/suggest` | Ask for a suggestion on demand |
| GET | `/api/suggestions?status=` | List suggestions |
| PATCH | `/api/suggestions/{id}` | Accept or reject: `{"decision":"ACCEPT\|REJECT","note":"..."}` |
| GET / PUT | `/api/scheduling/strategy` | See or switch the strategy (`rule` or `ai`) |

## 5. The main demo (the path the brief asks for)

```bash
# 1. Look at the starting state
curl -s $U/appointments | python3 -m json.tool | head -40

# 2. Dr. Chen goes offline: returns immediately, the loop runs in the background
curl -s -X PATCH $U/providers/PRV-001/status -H "$H" -d '{"status":"OFFLINE","reason":"sick leave"}'

# 3. About a second later, suggestions exist for APT-001 and APT-002 (trigger PROVIDER_OFFLINE)
sleep 2
curl -s "$U/suggestions?status=PENDING" | python3 -m json.tool

# 4. The waiting appointments, with the suggestion inline
curl -s "$U/appointments?status=RESCHEDULE_PENDING" | python3 -m json.tool

# 5. Accept the first one (replace SUG-... with a real id from step 3)
curl -s -X PATCH $U/suggestions/SUG-XXXXXXXX -H "$H" -d '{"decision":"ACCEPT","note":"patient agreed by phone"}'

# 6. Accept the other one, which points at the same slot: expect 409 SLOT_UNAVAILABLE
curl -s -X PATCH $U/suggestions/SUG-YYYYYYYY -H "$H" -d '{"decision":"ACCEPT"}'
```

What to notice: step 2 does not wait for the suggestions; step 5 books the new slot, frees the old one and moves the appointment in one go; step 6 shows the double-booking guard.

## 6. More things worth trying

```bash
# Slot cancelled instead of provider offline (use a fresh reset)
curl -s -X PATCH $U/slots/SLT-001/status -H "$H" -d '{"status":"CANCELLED","reason":"room flooded"}'

# No slot fits (APT-003 needs 45 minutes, only 30-minute slots are open):
# the appointment is flagged RESCHEDULE_PENDING with no suggestion
curl -s -X PATCH $U/slots/SLT-003/status -H "$H" -d '{"status":"CANCELLED"}'
curl -s "$U/appointments/APT-003"

# Manual suggestion
curl -s -X POST $U/appointments/APT-001/suggest

# Reject one with a note, then ask again: the refused slot is not offered twice
curl -s -X PATCH $U/suggestions/SUG-XXXXXXXX -H "$H" -d '{"decision":"REJECT","note":"wants a later time"}'

# Book a new appointment (slot must be OPEN and belong to that provider)
curl -s -X POST $U/appointments -H "$H" -d '{"patientName":"Jane Roe","type":"ROUTINE","providerId":"PRV-004","slotId":"SLT-005","patientPhone":"+1-555-0199"}'

# Errors
curl -s -X POST $U/appointments -H "$H" -d '{"type":"NOPE"}'          # 400
curl -s $U/appointments/APT-NOPE                                      # 404
```

## 7. Switch to the AI strategy

```bash
curl -s $U/scheduling/strategy                                  # {"active":"rule","available":["ai","rule"]}
curl -s -X PUT $U/scheduling/strategy -H "$H" -d '{"strategy":"ai"}'
# now repeat the demo; suggestions show "strategy":"ai" and a plain-language reasoning
```
Without `GEMINI_API_KEY`, or if Gemini fails, the result is still returned by the rules and labelled `"strategy":"ai->rule-fallback"`.

## 8. Where each idea lives in the code (`backend/src/main/java/com/example/slotmind`)

| Idea | Where |
|---|---|
| Rules of each object (state machines) | `entity/Slot.java`, `Appointment.java`, `RescheduleSuggestion.java` |
| Double-booking protection | `@Version` on the entities, unique `slot_id` on `appointments`, `SuggestionService.decide` |
| Pluggable strategies | `engine/SchedulingStrategy`, `RuleBasedStrategy`, `StrategyRegistry`, `ai/AiSchedulingStrategy` |
| Validation and fallback | `engine/SchedulingEngine` |
| Background loop | `event/RescheduleEventHandler`, events published from `ProviderService` and `SlotService` |
| Suggestion logic (dedupe, stale, accept) | `service/SuggestionService` |
| LLM call and prompts | `ai/LLMGateway`, `ai/SchedulingPromptFactory` |
| Error responses | `exception/GlobalExceptionHandler` |
| Settings | `backend/src/main/resources/application*.properties` |

Stage plans with the reasoning behind each choice: `STAGE_1_PLAN.md` to `STAGE_7_PLAN.md`; progress in `STAGES.md`.

## 9. Known limits (good to know for questions)

- Events are delivered at most once: if the app dies between the commit and the background handler, that run is lost. The manual suggest endpoint is the safety net.
- Two appointments can be offered the same slot; the conflict is settled when someone accepts (409).
- The strategy switch is in memory only and resets to `rule` on restart.
- No automated tests were written for Stages 4, 5 and 7 (by request); Stages 1 to 3 have unit tests.
