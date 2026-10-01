#!/usr/bin/env bash
# =====================================================================
# Simple Bank - Step 3 (JWT security) live demo
#
# 1. Start the backend in another window:  cd backend && ./mvnw spring-boot:run
# 2. Run this from the repo root:           bash scripts/demo-step3.sh
#
# It pauses after each step, so you can explain what happened.
# Uses new emails every run, so it can be run again and again.
# =====================================================================

BASE="${BASE:-http://localhost:8080/api}"
J="Content-Type: application/json"
STAMP=$(date +%s)
ANA_EMAIL="ana.$STAMP@example.com"
BEN_EMAIL="ben.$STAMP@example.com"
ADDR='"address":{"street":"1 Harbor St","city":"Baltimore","state":"MD","zip":"21202"}'

title() {
  echo
  echo "======================================================================"
  echo "  $1"
  echo "======================================================================"
}
expect() { echo "  Expected: $1"; echo; }
pause() { echo; read -r -p "  [Press Enter for the next step] " _; }
token_of() { grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4; }
first_number() { grep -o "\"$1\":[0-9]*" | head -1 | cut -d: -f2; }

# Prints the response body, then the HTTP status on its own line
call() { curl -s -w "\n  -> HTTP %{http_code}\n" "$@"; }

# The middle part of a JWT is Base64url-encoded JSON: anyone can read it
decode_payload() {
  local p
  p=$(echo "$1" | cut -d. -f2 | tr '_-' '/+')
  while [ $(( ${#p} % 4 )) -ne 0 ]; do p="$p="; done
  echo "$p" | base64 -d
  echo
}
encode_base64url() { printf '%s' "$1" | base64 -w0 | tr '+/' '-_' | tr -d '='; }

# ---------------------------------------------------------------------
title "0. Is the backend running?"
status=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/auth/me")
if [ "$status" = "000" ]; then
  echo "  The backend isn't answering at $BASE."
  echo "  Start it first:  cd backend && ./mvnw spring-boot:run"
  exit 1
fi
echo "  Backend is up (answered HTTP $status)."
pause

# ---------------------------------------------------------------------
title "1. Without logging in, everything is locked"
expect "401 Unauthorized, with a message explaining how to log in"
call "$BASE/accounts/1"
pause

# ---------------------------------------------------------------------
title "2. Ana registers. She is a CUSTOMER and gets a token right away"
expect "201 Created, an accessToken, role CUSTOMER, and no password in the response"
ANA_JSON=$(curl -s -X POST "$BASE/auth/register" -H "$J" \
  -d "{\"name\":\"Ana Lopez\",\"email\":\"$ANA_EMAIL\",\"password\":\"Secret123\",$ADDR}")
echo "$ANA_JSON"
ANA=$(echo "$ANA_JSON" | token_of)
ANA_ID=$(echo "$ANA_JSON" | first_number userId)
echo
echo "  Ana is user $ANA_ID."
pause

# ---------------------------------------------------------------------
title "3. What's inside a JWT? (header.payload.signature)"
expect "Readable JSON: who she is (sub), her email, her role, and when it expires (exp)"
echo "  Token: ${ANA:0:60}..."
echo
echo "  Decoded payload:"
echo -n "  "; decode_payload "$ANA"
echo
echo "  Anyone can READ this. Nobody can CHANGE it without breaking the signature (step 8)."
pause

# ---------------------------------------------------------------------
title "4. Ana uses her token: who am I?"
expect "200 OK with Ana's profile"
call "$BASE/auth/me" -H "Authorization: Bearer $ANA"
pause

# ---------------------------------------------------------------------
title "5. Ana opens an account and deposits \$100"
expect "201 for the new account, then 200 with a balance of 100.00"
ACC_JSON=$(curl -s -X POST "$BASE/accounts" -H "$J" -H "Authorization: Bearer $ANA" \
  -d "{\"userId\":$ANA_ID,\"accountType\":\"SAVINGS\"}")
echo "$ACC_JSON"
ACC=$(echo "$ACC_JSON" | first_number accountId)
echo
call -X POST "$BASE/accounts/$ACC/deposit" -H "$J" -H "Authorization: Bearer $ANA" -d '{"amount":100}'
pause

# ---------------------------------------------------------------------
title "6. Ben registers, then tries to read and rob Ana's account #$ACC"
expect "403 Forbidden both times: 'You can only access your own accounts'"
BEN=$(curl -s -X POST "$BASE/auth/register" -H "$J" \
  -d "{\"name\":\"Ben Carter\",\"email\":\"$BEN_EMAIL\",\"password\":\"Secret456\",$ADDR}" | token_of)
echo "  Ben reads Ana's account:"
call "$BASE/accounts/$ACC" -H "Authorization: Bearer $BEN"
echo
echo "  Ben withdraws \$50 from Ana's account:"
call -X POST "$BASE/accounts/$ACC/withdraw" -H "$J" -H "Authorization: Bearer $BEN" -d '{"amount":50}'
echo
echo "  Ana's balance afterwards (still 100.00):"
call "$BASE/accounts/$ACC" -H "Authorization: Bearer $ANA"
pause

# ---------------------------------------------------------------------
title "7. Ana tries the bank staff's pages"
expect "403 Forbidden: 'only available to bank staff (ADMIN)'"
echo "  All customers:"
call "$BASE/users" -H "Authorization: Bearer $ANA"
echo
echo "  The audit log:"
call "$BASE/audit" -H "Authorization: Bearer $ANA"
pause

# ---------------------------------------------------------------------
title "8. Ana edits her own token to say she's an ADMIN"
expect "401 Unauthorized: the signature no longer matches the changed payload"
HEADER=$(echo "$ANA" | cut -d. -f1)
SIGNATURE=$(echo "$ANA" | cut -d. -f3)
FORGED_PAYLOAD=$(decode_payload "$ANA" | sed 's/"role":"CUSTOMER"/"role":"ADMIN"/')
echo "  Forged payload: $FORGED_PAYLOAD"
FORGED="$HEADER.$(encode_base64url "$FORGED_PAYLOAD").$SIGNATURE"
echo
call "$BASE/audit" -H "Authorization: Bearer $FORGED"
pause

# ---------------------------------------------------------------------
title "9. Mallory registers and sends \"role\":\"ADMIN\" herself"
expect "201 Created, but role is still CUSTOMER (the field is ignored)"
call -X POST "$BASE/auth/register" -H "$J" \
  -d "{\"name\":\"Mallory\",\"email\":\"mallory.$STAMP@example.com\",\"password\":\"Secret789\",\"role\":\"ADMIN\",$ADDR}"
pause

# ---------------------------------------------------------------------
title "10. Wrong password vs. an email that doesn't exist"
expect "The SAME 401 message both times, so attackers can't tell which emails exist"
echo "  Ana, wrong password:"
call -X POST "$BASE/auth/login" -H "$J" -d "{\"email\":\"$ANA_EMAIL\",\"password\":\"WrongGuess1\"}"
echo
echo "  An email that doesn't exist:"
call -X POST "$BASE/auth/login" -H "$J" -d '{"email":"nobody.at.all@example.com","password":"Secret123"}'
pause

# ---------------------------------------------------------------------
title "11. Bank staff log in and read the audit log"
expect "role ADMIN, then the latest events: who did what, and what was refused"
if [ -z "$ADMIN_EMAIL" ] || [ -z "$ADMIN_PASSWORD" ]; then
  echo "  ADMIN_EMAIL / ADMIN_PASSWORD aren't set in this terminal. Run: source ~/.bashrc"
  exit 1
fi
ADMIN_JSON=$(curl -s -X POST "$BASE/auth/login" -H "$J" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
ADMIN=$(echo "$ADMIN_JSON" | token_of)
echo "  Logged in as: $(echo "$ADMIN_JSON" | grep -o '"role":"[A-Z]*"')"
echo
echo "  Latest audit events (newest first):"
curl -s "$BASE/audit?size=12" -H "Authorization: Bearer $ADMIN" \
  | grep -o '"actor":"[^"]*","action":"[A-Z_]*","outcome":"[A-Z]*","reason":[^,]*' \
  | sed 's/^/    /'
echo
echo "  Look for: Ben's ACCESS_DENIED on account #$ACC, Ana's forged-token attempt is NOT here"
echo "  (it never got past the signature check), the REJECTED logins, and Ana's DEPOSIT."
pause

title "Done. That's Step 3: login, roles, ownership checks, and an audit trail of who did what."
echo
