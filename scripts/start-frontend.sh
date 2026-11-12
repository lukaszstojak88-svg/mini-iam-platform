#!/usr/bin/env bash
# Starts the Angular dev server of Mini IAM (http://localhost:4200).
#
# Usage:
#   ./scripts/start-frontend.sh                 # check backend, install deps if needed, run ng serve
#   ./scripts/start-frontend.sh --with-backend  # additionally start Docker Compose and wait until healthy
#   ./scripts/start-frontend.sh --skip-check    # do not check whether Keycloak / auth-service are up
#
# Works in Git Bash (Windows), Linux and macOS.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend/mini-iam-ui"

# Must match frontend/mini-iam-ui/src/environments/environment.ts
KEYCLOAK_URL="http://localhost:8080/realms/mini-iam/.well-known/openid-configuration"
AUTH_SERVICE_HEALTH_URL="http://localhost:8082/actuator/health"

WITH_BACKEND=false
SKIP_CHECK=false

usage() {
  sed -n '2,9p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

info() { printf '\033[1;34m[frontend]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[frontend]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m[frontend]\033[0m %s\n' "$*" >&2; exit 1; }

for arg in "$@"; do
  case "$arg" in
    --with-backend) WITH_BACKEND=true ;;
    --skip-check) SKIP_CHECK=true ;;
    -h|--help) usage; exit 0 ;;
    *) usage; fail "Unknown option: $arg" ;;
  esac
done

# --- Node.js -----------------------------------------------------------------
command -v node >/dev/null 2>&1 || fail "Node.js not found. Install Node 22.22+, 24.15+ or 26+."
command -v npm >/dev/null 2>&1 || fail "npm not found."
NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]')"
[ "$NODE_MAJOR" -ge 22 ] || fail "Node $(node -v) is too old for Angular 22 (need 22.22+, 24.15+ or 26+)."

# Use the OS certificate store so npm works behind TLS-intercepting proxies/antivirus (Node 22.15+/23.8+).
if node --use-system-ca -e '' >/dev/null 2>&1; then
  case " ${NODE_OPTIONS:-} " in
    *" --use-system-ca "*) ;;
    *) export NODE_OPTIONS="${NODE_OPTIONS:+$NODE_OPTIONS }--use-system-ca" ;;
  esac
fi
export NG_CLI_ANALYTICS=false

# --- Backend (Docker Compose) ------------------------------------------------
is_up() {
  curl -fsS -o /dev/null --max-time 3 "$1" 2>/dev/null
}

wait_for() {
  local name="$1" url="$2" timeout_s="$3" waited=0
  until is_up "$url"; do
    if [ "$waited" -ge "$timeout_s" ]; then
      fail "$name is not reachable at $url after ${timeout_s}s. Check: docker compose ps / docker compose logs"
    fi
    sleep 5
    waited=$((waited + 5))
  done
  info "$name is up."
}

if [ "$WITH_BACKEND" = true ]; then
  command -v docker >/dev/null 2>&1 || fail "Docker not found."
  [ -f "$ROOT_DIR/.env" ] || fail "Missing .env - copy .env.example to .env and set your own secrets (see README)."
  info "Starting backend: docker compose up -d --build"
  (cd "$ROOT_DIR" && docker compose up -d --build)
  wait_for "Keycloak" "$KEYCLOAK_URL" 240
  wait_for "auth-service" "$AUTH_SERVICE_HEALTH_URL" 180
elif [ "$SKIP_CHECK" = false ]; then
  if is_up "$KEYCLOAK_URL" && is_up "$AUTH_SERVICE_HEALTH_URL"; then
    info "Keycloak and auth-service are up."
  else
    warn "Keycloak (localhost:8080) or auth-service (localhost:8082) is not reachable."
    warn "Login and the dashboard will not work. Start the backend with:"
    warn "  docker compose up -d --build      (or rerun this script with --with-backend)"
  fi
fi

# --- Dependencies ------------------------------------------------------------
[ -f "$FRONTEND_DIR/package.json" ] || fail "No package.json in $FRONTEND_DIR - is the frontend checked out?"
cd "$FRONTEND_DIR"
info "Frontend directory: $FRONTEND_DIR"
if [ ! -f package-lock.json ]; then
  # npm ci requires a lockfile; generate it once (commit package-lock.json afterwards).
  warn "package-lock.json is missing - running npm install to create it (commit it to Git)."
  npm install --no-audit --no-fund
elif [ ! -f node_modules/.package-lock.json ] || [ package-lock.json -nt node_modules/.package-lock.json ]; then
  # Reinstall when node_modules is missing or package-lock.json changed since the last install.
  info "Installing dependencies (npm ci)..."
  npm ci --no-audit --no-fund || fail "npm ci failed. If the error is EBUSY/EPERM, stop running 'ng serve' / close the IDE and retry."
else
  info "Dependencies are up to date."
fi

# --- Dev server --------------------------------------------------------------
info "Starting dev server: http://localhost:4200  (Ctrl+C to stop)"
info "Log in as 'admin' with MINI_IAM_ADMIN_PASSWORD from .env"
info "Test users 'manager', 'auditor', 'user': MINI_IAM_DEMO_PASSWORD from .env (changed at first login)"
exec npm start
