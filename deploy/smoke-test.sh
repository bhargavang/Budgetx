#!/usr/bin/env bash
# Smoke-test a running BudgetX through the gateway.   Usage: bash deploy/smoke-test.sh http://<host>:8080
set -uo pipefail
BASE="${1:-http://localhost:8080}"; BASE="${BASE%/}"
PASS=0; FAIL=0; BODY=$(mktemp); trap 'rm -f "$BODY"' EXIT
check() { if [ "$2" = "$3" ]; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 (expected $3, got $2)"; FAIL=$((FAIL+1)); fi; }
code() { curl -s -o "$BODY" -w '%{http_code}' --max-time 20 "$@"; }

check "gateway health"                 "$(code "$BASE/actuator/health")" 200
grep -q '"UP"' "$BODY" && echo "      $(cat "$BODY")"
check "web app"                        "$(code "$BASE/")" 200
EMAIL="smoke$(date +%s)$RANDOM@example.com"; PW='Sm0ke-test-pass'
check "register"                       "$(code -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"fullName\":\"Smoke Test\",\"email\":\"$EMAIL\",\"password\":\"$PW\",\"monthlySalary\":50000}")" 201
check "login"                          "$(code -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PW\"}")" 200
TOKEN=$(sed -n 's/.*"\(accessToken\|token\)":"\([^"]*\)".*/\2/p' "$BODY")
check "profile without token is 401"   "$(code "$BASE/api/users/me")" 401
check "profile with token"             "$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/users/me")" 200
check "emi-service via gateway"        "$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/emis")" 200
check "expense-service via gateway"    "$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/expenses")" 200
check "internal path blocked"          "$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/emis/total")" 403
echo; echo "$PASS passed, $FAIL failed"; [ "$FAIL" -eq 0 ]
