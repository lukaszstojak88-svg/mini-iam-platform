---
name: verify-stack
description: Uruchomienie i weryfikacja end-to-end lokalnego środowiska Mini IAM - testy Maven i Angular, Docker Compose z healthcheckami, smoke testy bezpieczeństwa (401/200, token serwisowy, zły issuer, CORS), logowanie PKCE jako admin i opcjonalnie logi w ELK. Używaj po istotnej zmianie, przy zamykaniu etapu albo gdy użytkownik prosi "odpal", "sprawdź środowisko", "zweryfikuj", "smoke test".
---

# Weryfikacja środowiska

Wszystkie komendy w Git Bash z katalogu głównego repo. Sekrety czytaj z `.env`; nie wypisuj ich w odpowiedzi,
chyba że użytkownik o nie prosi.

## 0. Pułapki tej maszyny

- Maven: `export MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` (proxy TLS -> PKIX).
- npm/npx: `export NODE_OPTIONS=--use-system-ca`; komendy npm tylko w `frontend/mini-iam-ui` (inaczej `EUSAGE`).
- Docker Desktop musi działać (`docker info`), inaczej IT z Testcontainers i Compose nie ruszą.
- `npm ci` przy działającym `ng serve`/IDE może skończyć się `EBUSY` i zostawić niepełne `node_modules` -
  zatrzymaj serwer i powtórz.

## 1. Testy

```bash
./mvnw verify                                             # unit + @WebMvcTest + IT (PostgreSQL, Keycloak)
(cd frontend/mini-iam-ui && npx ng test --watch=false && npx ng build)
```

## 2. Środowisko Docker

```bash
[ -f .env ] || echo "brak .env - skopiuj .env.example"
docker compose up -d --build                              # dodaj --profile elk, jeśli sprawdzasz logi
for i in $(seq 1 60); do
  s=$(docker compose ps --format '{{.Service}} {{.Health}}')
  echo "$s" | grep -qE 'starting|unhealthy' || break; sleep 5
done; echo "$s"
```

Realm importuje się tylko przy pustej bazie Keycloak: po zmianie realmu lub haseł w `.env` - `docker compose down -v`.

## 3. Smoke testy API

```bash
set -a; . ./.env; set +a
KC=http://localhost:8080/realms/mini-iam/protocol/openid-connect
for p in 8081 8082 8083; do curl -s -i -H 'X-Correlation-Id: smoke-1' localhost:$p/actuator/health | grep -iE '^HTTP|x-correlation'; done
curl -s -i localhost:8082/api/auth/me | grep -iE '^HTTP|www-auth|content-type'        # 401 + problem+json
TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=mini-iam-service \
  -d "client_secret=$MINI_IAM_SERVICE_CLIENT_SECRET" $KC/token | sed 's/.*"access_token":"\([^"]*\)".*/\1/')
AT=$(curl -s -d grant_type=client_credentials -d client_id=mini-iam-admin \
  -d "client_secret=$MINI_IAM_ADMIN_CLIENT_SECRET" $KC/token | sed 's/.*"access_token":"\([^"]*\)".*/\1/')
curl -s -o /dev/null -w 'admin-api token on app API: %{http_code}\n' -H "Authorization: Bearer $AT" localhost:8082/api/auth/me  # 401
curl -s -w ' [%{http_code}]\n' -H "Authorization: Bearer $TOKEN" localhost:8082/api/auth/me   # 200
MT=$(curl -s -d grant_type=password -d client_id=admin-cli -d "username=$KEYCLOAK_ADMIN_USERNAME" \
  -d "password=$KEYCLOAK_ADMIN_PASSWORD" http://localhost:8080/realms/master/protocol/openid-connect/token \
  | sed 's/.*"access_token":"\([^"]*\)".*/\1/')
curl -s -o /dev/null -w 'master token: %{http_code}\n' -H "Authorization: Bearer $MT" localhost:8082/api/auth/me  # 401
curl -s -o /dev/null -w 'CORS ok origin: %{http_code}\n' -X OPTIONS localhost:8082/api/auth/me \
  -H 'Origin: http://localhost:4200' -H 'Access-Control-Request-Method: GET'          # 200
curl -s -o /dev/null -w 'CORS bad origin: %{http_code}\n' -X OPTIONS localhost:8082/api/auth/me \
  -H 'Origin: http://evil.example' -H 'Access-Control-Request-Method: GET'            # 403
```

Dodaj kontrole nowych endpointów z bieżącego etapu (401 / 403 dla roli bez uprawnień / sukces).

## 4. Logowanie użytkownika (Authorization Code + PKCE) bez przeglądarki

```bash
J=$(mktemp); RED=http://localhost:4200/dashboard
VER=$(openssl rand -hex 32); CH=$(printf %s "$VER" | openssl dgst -sha256 -binary | base64 | tr '+/' '-_' | tr -d '=')
PAGE=$(curl -s -c $J -b $J "$KC/auth?client_id=mini-iam-frontend&response_type=code&scope=openid&redirect_uri=$RED&code_challenge=$CH&code_challenge_method=S256&state=s1")
ACTION=$(echo "$PAGE" | grep -o 'action="[^"]*"' | head -1 | sed 's/action="//; s/"$//; s/&amp;/\&/g')
LOC=$(curl -s -o /dev/null -w '%{redirect_url}' -c $J -b $J --data-urlencode username=admin \
  --data-urlencode "password=$MINI_IAM_ADMIN_PASSWORD" "$ACTION")
CODE=$(echo "$LOC" | grep -o 'code=[^&]*' | cut -d= -f2)
AT=$(curl -s -d grant_type=authorization_code -d client_id=mini-iam-frontend -d code=$CODE \
  -d redirect_uri=$RED -d code_verifier=$VER $KC/token | sed 's/.*"access_token":"\([^"]*\)".*/\1/')
echo "$AT" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | grep -oE '"(aud|azp|realm_access)":[^}]*'
curl -s -H "Authorization: Bearer $AT" localhost:8082/api/auth/me; rm -f $J
```

Oczekiwane: `aud` zawiera `mini-iam-api`, `realm_access.roles` = `ADMIN`, `USER`.

## 5. Logi w ELK (opcjonalnie, `--profile elk`)

```bash
curl -s -o /dev/null -H 'X-Correlation-Id: smoke-elk-1' localhost:8082/api/auth/me
sleep 15
curl -s -u "elastic:$ELASTIC_PASSWORD" "localhost:9200/mini-iam-logs-*/_count?q=correlationId:smoke-elk-1"
```

## 6. Frontend w przeglądarce

`./scripts/start-frontend.sh` -> http://localhost:4200 -> login `admin`. Jeśli nie da się sprawdzić w przeglądarce,
napisz wprost, że UI nie było weryfikowane wizualnie.

## Raport

Krótko: co przeszło, co nie (z wyjściem błędu), czego nie dało się sprawdzić.
