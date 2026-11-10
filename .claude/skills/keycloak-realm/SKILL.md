---
name: keycloak-realm
description: Zasady zmian w konfiguracji Keycloak realmu mini-iam (infrastructure/keycloak/realm-import/mini-iam-realm.json) - klienci, role, scope mappings, audience, uprawnienia service account, sekrety jako placeholdery, ponowny import i testy. Używaj przy każdej zmianie realm importu lub problemach z tokenami/Admin API ("dodaj klienta", "nowa rola", "401 z Admin API", "brak roli w tokenie").
---

# Zmiany w realmie `mini-iam`

Plik: `infrastructure/keycloak/realm-import/mini-iam-realm.json` - jedyne źródło konfiguracji realmu.
Używa go Docker Compose oraz test `auth-service/.../KeycloakJwtValidationIT.java` (kopiowany przez `testResources`).

## Zasady

1. **Sekrety tylko jako placeholdery** `${NAZWA}` (Keycloak podstawia zmienne środowiskowe przy imporcie).
   Każdą nową zmienną dodaj w trzech miejscach: `.env.example` (`change-me`), sekcja `keycloak.environment`
   w `infrastructure/docker-compose.yml`, `withEnv(...)` w `KeycloakJwtValidationIT`.
2. **`fullScopeAllowed: false`** na klientach - token zawiera tylko role z mapowań. Nowa rola realmu, która ma trafić
   do tokena klienta, wymaga wpisu w `scopeMappings` (realm roles) lub `clientScopeMappings` (client roles).
3. **Admin API (Keycloak >= 26.8.2):** klient wołający `/admin/realms/...` musi mieć `kc-admin-api` w
   `defaultClientScopes`, inaczej dostaje 401 („Expected audience not available”). Przy jawnym
   `defaultClientScopes` wypisz też domyślne: `basic, roles, profile, email, web-origins, acr`.
4. **Nie dodawaj `clientScopes` na poziomie realmu** - gdy ta lista jest podana, Keycloak nie tworzy scope'ów
   domyślnych (m.in. `roles`, który daje `realm_access`). Mappery dodawaj na kliencie (`protocolMappers`).
5. **Audience:** tokeny dla API muszą mieć `aud` = `mini-iam-api` (mapper `oidc-audience-mapper` na kliencie);
   backend odrzuca tokeny bez niego.
6. **Minimalne uprawnienia:** service account `mini-iam-admin` ma `SERVICE` + `realm-management`:
   `view-users`, `query-users`, `manage-users`, `view-realm`. Nowe uprawnienie tylko z uzasadnieniem (spec §5.4).
7. **Role:** aplikacyjne `ADMIN`, `MANAGER`, `USER`, `AUDITOR` (stałe, spec §2); `USER` w composite
   `default-roles-mini-iam`; `SERVICE` wyłącznie dla service accounts. Bez ról tworzonych „z UI”.
8. **Frontend client** `mini-iam-frontend`: public, PKCE `S256`, bez direct access grants; redirect/web origins
   tylko `http://localhost:4200`.
9. Issuer jest stały (`KC_HOSTNAME=http://localhost:8080`); serwisy w Dockerze pobierają JWKS z `keycloak:8080`.

## Zastosowanie zmian

Realm importuje się tylko, gdy nie istnieje w bazie Keycloak:

```bash
docker compose down -v && docker compose up -d --build     # kasuje też dane PostgreSQL!
```

Uprzedź użytkownika, że `down -v` usuwa dane z wolumenów.

## Weryfikacja

```bash
./mvnw verify -pl auth-service        # KeycloakJwtValidationIT na tym samym pliku realmu
set -a; . ./.env; set +a
TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=mini-iam-admin \
  -d "client_secret=$MINI_IAM_ADMIN_CLIENT_SECRET" \
  http://localhost:8080/realms/mini-iam/protocol/openid-connect/token | sed 's/.*"access_token":"\([^"]*\)".*/\1/')
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | grep -oE '"(aud|scope|realm_access|resource_access)":[^}]*\}?'
curl -s -o /dev/null -w 'admin api: %{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/realms/mini-iam/roles                # 200
```

Przy problemach z tokenem: tymczasowo `KC_LOG_LEVEL=INFO,org.keycloak.services:debug` (override compose
w katalogu scratchpad, nie w repo) i szukaj `Failed to verify` w `docker compose logs keycloak`.
