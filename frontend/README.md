# SlotMind front desk (Angular 17)

The UI for the SlotMind backend: a reschedule queue where staff accept or reject proposed slots, plus
appointment, provider and slot management.

## Run the UI

1. Start the backend first (project root): `./mvnw spring-boot:run` (dev profile, port 8082).
2. In this folder: `npm install` once, then `npx ng serve`.
3. Open http://localhost:4200.

The dev server forwards every `/api/*` request to the backend using `proxy.conf.json`
(target `http://localhost:8082`), so the code contains no host names. If your backend runs on another
port, edit `target` in `proxy.conf.json`, or run `npx ng serve --proxy-config <your-file>.json` with a copy.

If `npm` fails with `EACCES` on `~/.npm`, some cache files are owned by root. Fix it once with
`sudo chown -R $(id -u):$(id -g) ~/.npm`.

Build for production with `npx ng build` (output in `dist/`).

## Pages

| Route | What it does |
|---|---|
| `/queue` | Appointments waiting for a reschedule with their proposal: confidence, reasoning to read to the patient, trigger and strategy badges. Accept or reject with a note; "Find a slot" when nothing fits. Refreshes every 5 s while the tab is visible. |
| `/appointments` | All bookings with status filter, type colour coding, "Suggest new slot", and a booking form that offers only the chosen provider's open future slots. |
| `/providers` | Provider cards with status and reason, a load view, and status changes. Going offline asks for confirmation because the queue is then filled automatically. |
| `/slots` | Day timeline per provider, filterable slot list, create slot, cancel slot with confirmation. |

The header shows backend connectivity and switches the suggestion strategy between Rules and AI.

## Code layout

```
src/app/core/      api service, error handling, queue polling, notifications, date helpers
src/app/models/    typed request (…Model) and response (…View) shapes mirroring the backend
src/app/shared/    status badge, confidence bar, confirm dialog, date pipe
src/app/layout/    strategy switcher
src/app/pages/     queue, appointments, providers, slots
```

Backend datetimes carry no time zone (`2026-11-03T17:26:29`), so they are read field by field in
`core/local-datetime.ts` and never shifted. Backend errors arrive as `{errorCode, message}`; the HTTP
interceptor turns them into an `ApiError` and `core/api-error.ts` maps the common ones to plain language.
