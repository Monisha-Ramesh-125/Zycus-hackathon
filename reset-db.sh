#!/usr/bin/env bash
# Puts the SlotMind database back to its initial demo state.
#   1. drops the app's four tables (only the slotmind database is touched)
#   2. starts a short-lived backend on a spare port so it recreates the tables and reloads db/seed.sql
#   3. stops that backend again
# Safe to run while your own backend is up: it keeps working once the tables are back.
# Usage: ./reset-db.sh            (env overrides: DB_HOST DB_USER DB_PASS DB_NAME RESET_PORT)

set -u
cd "$(dirname "$0")"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-root}"
DB_NAME="${DB_NAME:-slotmind}"
PORT="${RESET_PORT:-8099}"
LOG="$(mktemp -t slotmind-reset)"

sql() { mariadb -u"$DB_USER" -p"$DB_PASS" -h"$DB_HOST" --skip-ssl "$DB_NAME" -e "$1"; }

if ! command -v mariadb >/dev/null 2>&1; then echo "mariadb client not found in PATH"; exit 1; fi
if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port $PORT is busy. Run again with another one, e.g.  RESET_PORT=8098 ./reset-db.sh"; exit 1
fi
sql "select 1" >/dev/null 2>&1 || { echo "Cannot connect to MariaDB at $DB_HOST as $DB_USER. Is the Docker container running?"; exit 1; }

echo "1/3 Dropping the four tables in '$DB_NAME'..."
sql "SET FOREIGN_KEY_CHECKS=0; DROP TABLE IF EXISTS reschedule_suggestions, appointments, slots, providers; SET FOREIGN_KEY_CHECKS=1;" || exit 1

echo "2/3 Recreating tables and demo data (temporary backend on port $PORT)..."
( env -u GEMINI_API_KEY ./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--server.port=$PORT" >"$LOG" 2>&1 ) &
for _ in $(seq 1 60); do
  sleep 2
  grep -q -E "Started SlotmindApplication|APPLICATION FAILED|BUILD FAILURE" "$LOG" && break
done

STARTED=0; grep -q "Started SlotmindApplication" "$LOG" && STARTED=1
echo "3/3 Stopping the temporary backend..."
pkill -f "server.port=$PORT" 2>/dev/null
sleep 2

if [ "$STARTED" -ne 1 ]; then
  echo "The temporary backend did not start. Last log lines:"; tail -15 "$LOG"; exit 1
fi
rm -f "$LOG"

echo
echo "Done. Current data:"
sql "SELECT (SELECT COUNT(*) FROM providers) AS providers, (SELECT COUNT(*) FROM slots) AS slots,
            (SELECT COUNT(*) FROM appointments) AS appointments, (SELECT COUNT(*) FROM reschedule_suggestions) AS suggestions;
     SELECT id AS slot, status, start_time FROM slots ORDER BY start_time;"
echo "Slot times run from +60 to +240 minutes from now; reset again when they have passed."
