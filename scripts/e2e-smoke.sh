#!/usr/bin/env bash
# End-to-end smoke test of the running Mini IAM stack (spec §12.3), across services.
#
# Usage:
#   ./scripts/e2e-smoke.sh                       # security and correlation checks (no user token needed)
#   read -rs ADMIN_TOKEN && export ADMIN_TOKEN && ./scripts/e2e-smoke.sh
#                                                # + full admin scenario with audit and log checks
#                                                #   (paste the token; read -s keeps it out of shell history)
#
# ADMIN_TOKEN: access token of a user with role ADMIN (Postman "Get New Access Token" or browser DevTools after
# signing in to the frontend). It lives 5 minutes. The frontend client has no password grant, so the script
# cannot obtain it on its own.
#
# Requires: docker compose stack up (docker compose up -d), curl, node. Works in Git Bash, Linux and macOS.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() { sed -n '2,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }
case "${1:-}" in -h|--help) usage; exit 0 ;; "") ;; *) usage; exit 2 ;; esac

[ -f "$ROOT_DIR/.env" ] || { echo "Missing .env (cp .env.example .env)" >&2; exit 2; }
command -v node >/dev/null 2>&1 || { echo "node is required (JSON parsing)" >&2; exit 2; }
# Read only the values this script needs; .env is never printed.
env_value() { sed -n "s/^$1=//p" "$ROOT_DIR/.env" | tail -n 1 | tr -d '\r'; }

KEYCLOAK_PORT="$(env_value KEYCLOAK_PORT)"; KEYCLOAK_PORT="${KEYCLOAK_PORT:-8080}"
USER_PORT="$(env_value USER_SERVICE_PORT)"; USER_PORT="${USER_PORT:-8081}"
AUTH_PORT="$(env_value AUTH_SERVICE_PORT)"; AUTH_PORT="${AUTH_PORT:-8082}"
AUDIT_PORT="$(env_value AUDIT_SERVICE_PORT)"; AUDIT_PORT="${AUDIT_PORT:-8083}"
SERVICE_CLIENT_SECRET="$(env_value MINI_IAM_SERVICE_CLIENT_SECRET)"
ADMIN_CLIENT_SECRET="$(env_value MINI_IAM_ADMIN_CLIENT_SECRET)"

USER_API="http://localhost:$USER_PORT"
AUTH_API="http://localhost:$AUTH_PORT"
AUDIT_API="http://localhost:$AUDIT_PORT"
TOKEN_URL="http://localhost:$KEYCLOAK_PORT/realms/mini-iam/protocol/openid-connect/token"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
PASSED=0; FAILED=0; SKIPPED=0
RUN_ID="smoke-$(date +%s)"
START_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

pass() { PASSED=$((PASSED + 1)); printf '\033[32mPASS\033[0m %s\n' "$1"; }
fail() { FAILED=$((FAILED + 1)); printf '\033[31mFAIL\033[0m %s%s\n' "$1" "${2:+ ($2)}"; }
skip() { SKIPPED=$((SKIPPED + 1)); printf '\033[33mSKIP\033[0m %s\n' "$1"; }
section() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }

# call METHOD URL TOKEN CORRELATION_ID [JSON_BODY] -> sets STATUS, BODY, CID (X-Correlation-Id of the response)
call() {
  local method="$1" url="$2" token="$3" cid="$4" body="${5:-}"
  local args=(-s -X "$method" -o "$TMP_DIR/body" -D "$TMP_DIR/headers" -w '%{http_code}')
  # Header from a file, so the token is not visible in the process list (curl argv).
  if [ -n "$token" ]; then
    printf 'Authorization: Bearer %s\n' "$token" > "$TMP_DIR/auth"
    args+=(-H "@$TMP_DIR/auth")
  fi
  [ -n "$cid" ] && args+=(-H "X-Correlation-Id: $cid")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' --data "$body")
  STATUS="$(curl "${args[@]}" "$url")" || STATUS="000"
  BODY="$(cat "$TMP_DIR/body" 2>/dev/null)"
  CID="$(sed -n 's/^[Xx]-[Cc]orrelation-[Ii]d: *//p' "$TMP_DIR/headers" | tr -d '\r')"
}

# json EXPRESSION < input   (d = parsed JSON), e.g. json 'd.id'
json() {
  node -e 'let s="";process.stdin.on("data",c=>s+=c).on("end",()=>{try{const d=JSON.parse(s);const v=eval(process.argv[1]);process.stdout.write(v===undefined||v===null?"":String(v))}catch(e){}})' "$1"
}

expect_status() { # NAME EXPECTED
  if [ "$STATUS" = "$2" ]; then pass "$1 -> $STATUS"; else fail "$1" "expected $2, got $STATUS"; fi
}

client_token() { # CLIENT_ID SECRET
  # Secret via stdin, not argv.
  printf '%s' "$2" | curl -s -d grant_type=client_credentials -d "client_id=$1" --data-urlencode client_secret@- "$TOKEN_URL" \
    | json 'd.access_token'
}

service_logs() { (cd "$ROOT_DIR" && docker compose logs --no-color --since "$START_TIME" "$@" 2>/dev/null); }

logs_contain() { # TEXT SERVICE...
  local text="$1"; shift
  local service
  for service in "$@"; do
    # No pipe into grep -q: with pipefail an early grep exit (SIGPIPE upstream) would flip the result.
    local output; output="$(service_logs "$service")"
    grep -qF -- "$text" <<<"$output" || return 1
  done
}

# ---------------------------------------------------------------------------------------------------------------
section "Availability"
for url in "$USER_API" "$AUTH_API" "$AUDIT_API"; do
  call GET "$url/actuator/health" "" ""
  expect_status "health $url" 200
done
if [ "$FAILED" -gt 0 ]; then echo "Stack is not running - start it with: docker compose up -d --build" >&2; exit 1; fi

# ---------------------------------------------------------------------------------------------------------------
section "Security: 401 without token, correlation ID echoed (§12.3 #1, #14)"
for url in "$USER_API/api/users/me" "$AUTH_API/api/auth/me" "$AUDIT_API/api/audit-events"; do
  call GET "$url" "" "$RUN_ID-anon"
  expect_status "anonymous GET $url" 401
  if [ "$CID" = "$RUN_ID-anon" ]; then pass "X-Correlation-Id echoed on 401"; else fail "X-Correlation-Id echoed on 401" "got '$CID'"; fi
done

call GET "$USER_API/api/users/me" "" ""
GENERATED_CID="$CID"
if [[ "$GENERATED_CID" =~ ^[0-9a-f-]{36}$ ]]; then pass "missing X-Correlation-Id -> generated UUID $GENERATED_CID"
else fail "missing X-Correlation-Id -> generated UUID" "got '$GENERATED_CID'"; fi
sleep 1
if logs_contain "$GENERATED_CID" user-service; then pass "generated correlation ID is in user-service logs"
else fail "generated correlation ID is in user-service logs"; fi

call GET "$USER_API/api/users" "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.invalid-signature" ""
expect_status "forged token rejected" 401

# ---------------------------------------------------------------------------------------------------------------
section "Technical clients: least privilege (§5.1, §11.1)"
SERVICE_TOKEN="$(client_token mini-iam-service "$SERVICE_CLIENT_SECRET")"
if [ -z "$SERVICE_TOKEN" ]; then
  fail "service token (mini-iam-service)" "check MINI_IAM_SERVICE_CLIENT_SECRET in .env / realm import"
else
  pass "service token (mini-iam-service) issued"
  call GET "$USER_API/api/users" "$SERVICE_TOKEN" ""; expect_status "service token on GET /api/users (no ADMIN/MANAGER)" 403
  call POST "$AUTH_API/api/users/00000000-0000-0000-0000-000000000000/roles" "$SERVICE_TOKEN" "" '{"roleName":"ADMIN"}'
  expect_status "service token cannot assign roles" 403
  call GET "$AUDIT_API/api/audit-events" "$SERVICE_TOKEN" ""; expect_status "service token cannot read audit log" 403
fi
ADMIN_CLIENT_TOKEN="$(client_token mini-iam-admin "$ADMIN_CLIENT_SECRET")"
if [ -z "$ADMIN_CLIENT_TOKEN" ]; then
  fail "admin API token (mini-iam-admin)" "check MINI_IAM_ADMIN_CLIENT_SECRET in .env / realm import"
else
  call GET "$AUTH_API/api/auth/me" "$ADMIN_CLIENT_TOKEN" ""
  expect_status "Keycloak Admin API token rejected by Mini IAM APIs (no audience)" 401
fi

# ---------------------------------------------------------------------------------------------------------------
section "Admin scenario across services (§12.3 #3-#10, #13, #14)"
if [ -z "${ADMIN_TOKEN:-}" ]; then
  skip "ADMIN_TOKEN not set - admin scenario skipped (see --help)"
else
  call GET "$AUTH_API/api/auth/me" "$ADMIN_TOKEN" ""
  ROLES="$(printf '%s' "$BODY" | json 'd.roles.join(",")')"
  if [ "$STATUS" != 200 ] || [[ ",$ROLES," != *",ADMIN,"* ]]; then
    fail "ADMIN_TOKEN is valid and has role ADMIN" "status $STATUS, roles '$ROLES' - token expired (5 min)?"
  else
    pass "ADMIN_TOKEN is valid and has role ADMIN"
    USERNAME="smoke.$(date +%s)"
    PASSWORD="Smoke-$(date +%s)-Pw"   # temporary, only sent to Keycloak; must not appear in any log

    call POST "$USER_API/api/users" "$ADMIN_TOKEN" "$RUN_ID-create" \
      "{\"username\":\"$USERNAME\",\"email\":\"$USERNAME@example.com\",\"firstName\":\"Smoke\",\"lastName\":\"Test\",\"temporaryPassword\":\"$PASSWORD\"}"
    expect_status "create user $USERNAME" 201
    USER_ID="$(printf '%s' "$BODY" | json 'd.id')"
    KEYCLOAK_ID="$(printf '%s' "$BODY" | json 'd.keycloakId')"
    if grep -qF -- "$PASSWORD" <<<"$BODY"; then fail "response does not contain the password"; else pass "response does not contain the password"; fi

    if [ -n "$KEYCLOAK_ID" ]; then
      call GET "$AUTH_API/api/users/$KEYCLOAK_ID/roles" "$ADMIN_TOKEN" "$RUN_ID-roles"
      expect_status "read roles of the new user" 200
      [[ "$BODY" == *'"USER"'* ]] && pass "new user has role USER" || fail "new user has role USER" "$BODY"

      call POST "$AUTH_API/api/users/$KEYCLOAK_ID/roles" "$ADMIN_TOKEN" "$RUN_ID-assign" '{"roleName":"MANAGER"}'
      expect_status "assign MANAGER" 200
      call POST "$AUTH_API/api/users/$KEYCLOAK_ID/roles" "$ADMIN_TOKEN" "$RUN_ID-dup" '{"roleName":"MANAGER"}'
      expect_status "assign MANAGER again (conflict)" 409
      call POST "$AUTH_API/api/users/$KEYCLOAK_ID/roles" "$ADMIN_TOKEN" "$RUN_ID-bad" '{"roleName":"SERVICE"}'
      expect_status "assign SERVICE (not assignable)" 400
      call DELETE "$AUTH_API/api/users/$KEYCLOAK_ID/roles/MANAGER" "$ADMIN_TOKEN" "$RUN_ID-remove"
      expect_status "remove MANAGER" 204

      call PATCH "$USER_API/api/users/$USER_ID/status" "$ADMIN_TOKEN" "$RUN_ID-deactivate" '{"enabled":false}'
      expect_status "deactivate user" 200
      call PATCH "$USER_API/api/users/$USER_ID/status" "$ADMIN_TOKEN" "$RUN_ID-activate" '{"enabled":true}'
      expect_status "activate user" 200

      call GET "$USER_API/api/users/not-a-uuid" "$ADMIN_TOKEN" "$RUN_ID-badid"
      expect_status "invalid id" 400
      if grep -qE 'Exception|\bat [a-z]+\.[a-z]+\.|"trace"' <<<"$BODY"; then
        fail "error response has no technical details" "$BODY"
      else pass "error response has no technical details"; fi

      call DELETE "$USER_API/api/users/$USER_ID" "$ADMIN_TOKEN" "$RUN_ID-delete"
      expect_status "delete user" 204

      # Audit trail: every operation recorded with the correlation ID of the request that caused it.
      call GET "$AUDIT_API/api/audit-events?targetUserId=$KEYCLOAK_ID&sort=occurredAt,asc&size=50" "$ADMIN_TOKEN" "$RUN_ID-audit"
      expect_status "read audit events of the user" 200
      EVENTS="$(printf '%s' "$BODY" | json 'd.content.map(e=>e.eventType+"="+e.correlationId).join(" ")')"
      for pair in USER_CREATED=create ROLE_ASSIGNED=assign ROLE_REMOVED=remove \
                  USER_DEACTIVATED=deactivate USER_ACTIVATED=activate USER_DELETED=delete; do
        expected="${pair%%=*}=$RUN_ID-${pair#*=}"
        if [[ " $EVENTS " == *" $expected "* ]]; then pass "audit: $expected"; else fail "audit: $expected" "got: $EVENTS"; fi
      done
      if [[ "$EVENTS" == *"$RUN_ID-dup"* || "$EVENTS" == *"$RUN_ID-bad"* ]]; then
        fail "rejected operations are not audited" "$EVENTS"
      else pass "rejected operations are not audited"; fi

      # One correlation ID across services and their logs.
      sleep 1
      if logs_contain "$RUN_ID-create" user-service audit-service; then pass "$RUN_ID-create in user-service and audit-service logs"
      else fail "$RUN_ID-create in user-service and audit-service logs"; fi
      if logs_contain "$RUN_ID-assign" auth-service audit-service; then pass "$RUN_ID-assign in auth-service and audit-service logs"
      else fail "$RUN_ID-assign in auth-service and audit-service logs"; fi
    fi

    # Log hygiene: no password and no token (or its signature) in any service log.
    LOGS="$(service_logs user-service auth-service audit-service)"
    for secret_name in PASSWORD ADMIN_TOKEN SERVICE_TOKEN; do
      value="${!secret_name:-}"
      [ -z "$value" ] && continue
      needle="$value"
      [[ "$secret_name" == *TOKEN ]] && needle="${value##*.}"   # signature part of the JWT
      if grep -qF -- "$needle" <<<"$LOGS"; then fail "logs do not contain $secret_name"
      else pass "logs do not contain $secret_name"; fi
    done
    if grep -qiE 'authorization:|bearer [A-Za-z0-9]' <<<"$LOGS"; then fail "logs do not contain Authorization headers"
    else pass "logs do not contain Authorization headers"; fi
  fi
fi

# ---------------------------------------------------------------------------------------------------------------
printf '\n\033[1mResult:\033[0m %d passed, %d failed, %d skipped (run id %s)\n' "$PASSED" "$FAILED" "$SKIPPED" "$RUN_ID"
echo "Kibana (Discover, data view Mini IAM logs): correlationId.keyword : $RUN_ID-*"
[ "$FAILED" -eq 0 ]
