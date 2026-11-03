#!/usr/bin/env bash
# Runs the main demo against a running SlotMind backend.
# Usage: ./demo.sh [base-url]      default: http://localhost:8082/api
# Start from fresh data (see BACKEND_TEST_GUIDE.md, section 2).
U="${1:-http://localhost:8082/api}"
H='Content-Type: application/json'
py() { python3 -c "$1"; }

step() { printf '\n\033[1m== %s\033[0m\n' "$1"; }

step "0. Backend reachable?"
curl -sf "$U/ping" || { echo "Backend not reachable at $U"; exit 1; }; echo

step "1. Starting state: appointments"
curl -s "$U/appointments" | py "
import sys,json
for a in json.load(sys.stdin): print(' ',a['id'],a['patientName'].ljust(14),a['status'].ljust(12),'slot',a['slot']['id'],'with',a['provider']['name'])"

step "2. Dr. Sarah Chen (PRV-001) goes OFFLINE (returns immediately)"
curl -s -o /dev/null -w "  HTTP %{http_code} in %{time_total}s\n" -X PATCH "$U/providers/PRV-001/status" -H "$H" -d '{"status":"OFFLINE","reason":"sick leave"}'

echo "  ...waiting 3 seconds for the background loop..."; sleep 3

step "3. Suggestions created automatically (no one clicked anything)"
curl -s "$U/suggestions?status=PENDING" | py "
import sys,json
for s in json.load(sys.stdin):
    print(' ',s['id'],'for',s['appointmentId'],'('+s['patientName']+')')
    print('     trigger:',s['triggerReason'],'| strategy:',s['strategy'],'| confidence:',s['confidence'])
    print('     proposed:',s['recommendedSlot']['id'],'with',s['recommendedSlot']['providerName'])
    print('     reasoning:',s['reasoning'])"

step "4. Waiting appointments, suggestion inline"
curl -s "$U/appointments?status=RESCHEDULE_PENDING" | py "
import sys,json
for a in json.load(sys.stdin): print(' ',a['id'],a['status'],'-> suggestion',a['pendingSuggestion']['id'] if a['pendingSuggestion'] else 'NONE (no slot found)')"

IDS=$(curl -s "$U/suggestions?status=PENDING" | py "import sys,json;print(' '.join(s['id'] for s in json.load(sys.stdin)))")
FIRST=$(echo $IDS | cut -d' ' -f1); SECOND=$(echo $IDS | cut -d' ' -f2)
if [ -z "$FIRST" ]; then echo "No suggestions found; reset the data and try again."; exit 1; fi

step "5. Front desk accepts the first suggestion ($FIRST) after phoning the patient"
curl -s -w "\n  HTTP %{http_code}\n" -X PATCH "$U/suggestions/$FIRST" -H "$H" -d '{"decision":"ACCEPT","note":"patient agreed by phone"}' | py "
import sys
t=sys.stdin.read(); import json
body,code=t.rsplit('\n  HTTP',1); d=json.loads(body)
print('  status:',d.get('status') or d.get('errorCode'),'| HTTP',code.strip())"

if [ -n "$SECOND" ]; then
step "6. Accepting the second ($SECOND) onto the SAME slot must be refused"
curl -s -w "\n  HTTP %{http_code}\n" -X PATCH "$U/suggestions/$SECOND" -H "$H" -d '{"decision":"ACCEPT"}' | py "
import sys,json
t=sys.stdin.read(); body,code=t.rsplit('\n  HTTP',1); d=json.loads(body)
print(' ',d.get('errorCode') or d.get('status'),'-',d.get('message',''),'| HTTP',code.strip())"
fi

step "7. Final state"
curl -s "$U/appointments" | py "
import sys,json
for a in json.load(sys.stdin): print(' ',a['id'],a['patientName'].ljust(14),a['status'].ljust(18),'slot',a['slot']['id'],'with',a['provider']['name'],'| moved',a['rescheduleCount'],'time(s)')"
curl -s "$U/slots" | py "
import sys,json
print('  slots:',{s['id']:s['status'] for s in json.load(sys.stdin)})"
echo
