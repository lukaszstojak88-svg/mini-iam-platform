# Mini IAM Platform

Edukacyjny projekt portfolio: zarządzanie użytkownikami, rolami i audytem operacji administracyjnych
w architekturze mikroserwisowej (Spring Boot 3.5 + Keycloak 26 + PostgreSQL + ELK).

- Specyfikacja: [`docs/project-spec.md`](docs/project-spec.md)
- Plan etapów: [`docs/implementation-plan.md`](docs/implementation-plan.md)
- Architektura: [`docs/architecture.md`](docs/architecture.md)
- Kolekcja Postman z requestami API: [`docs/postman/`](docs/postman/README.md)

> **Status:** Etap 6 (Angular UI). Logowanie przez Keycloak (OIDC + PKCE), weryfikacja JWT w każdym serwisie,
> zarządzanie użytkownikami w Keycloak i lokalnym profilu (`/api/users`), odczyt i przypisywanie ról
> (`/api/roles`, `/api/users/{keycloakId}/roles`), audyt operacji administracyjnych (`/api/audit-events`)
> i interfejs Angular do wszystkich tych operacji (Dashboard, Users, Roles, Audit log).

## 1. Wymagania lokalne

| Narzędzie | Wersja |
|---|---|
| JDK | 21+ (kod kompilowany z `release 21`) |
| Docker + Docker Compose | Docker Desktop z Compose ≥ 2.20 (wymagane `include`) |
| RAM dla Dockera | ~4 GB z profilem `elk`, ~2 GB bez |
| Maven | nie jest wymagany — używaj `./mvnw` |
| Node.js | 22.22+, 24.15+ lub 26+ (frontend Angular 22) |

## 2. Konfiguracja

```bash
cp .env.example .env
```

Ustaw w `.env` własne hasła i secrety (wszystkie wartości `change-me`). Plik `.env` jest w `.gitignore`.
Szybkie wygenerowanie losowych wartości (Git Bash / Linux / macOS):

```bash
while IFS= read -r l; do case "$l" in *=change-me) echo "${l%=*}=$(openssl rand -hex 16)";; *) echo "$l";; esac; done < .env.example > .env
```

## 3. Uruchomienie

Z katalogu głównego repozytorium (`compose.yaml` dołącza `infrastructure/docker-compose.yml` i `.env`):

```bash
docker compose up -d --build                  # PostgreSQL, Keycloak, user/auth/audit-service
docker compose --profile elk up -d --build    # to samo + Elasticsearch, Logstash, Kibana, Filebeat
docker compose ps                             # wszystkie usługi powinny być "healthy"
docker compose --profile elk down             # zatrzymanie (dane zostają w wolumenach)
docker compose --profile elk down -v          # zatrzymanie + usunięcie danych (czysty start)
```

Serwisy backendowe są budowane i uruchamiane przez Compose. Uruchomienie pojedynczego serwisu poza Dockerem
(przy działającym PostgreSQL z Compose):

```bash
USER_SERVICE_DB_PASSWORD=<z .env> ./mvnw -pl user-service spring-boot:run
```

Poza Dockerem logi są w czytelnym formacie tekstowym; w kontenerze — JSON/ECS (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`).

Frontend (Angular SPA) uruchamiany osobno — najprościej skryptem (Git Bash / Linux / macOS):

```bash
./scripts/start-frontend.sh                 # sprawdza backend, w razie potrzeby npm ci, potem ng serve
./scripts/start-frontend.sh --with-backend  # dodatkowo docker compose up -d --build i czekanie na Keycloak
```

lub ręcznie, przy działającym Compose:

```bash
cd frontend/mini-iam-ui
npm ci
npm start          # http://localhost:4200
```

Otwórz http://localhost:4200 — aplikacja przekieruje do logowania w Keycloak (Authorization Code + PKCE).
Interfejs jest po angielsku. Widoki i role (menu pokazuje tylko dostępne pozycje; backend i tak sprawdza uprawnienia):

| Widok | Adres | Role | Co robi |
|---|---|---|---|
| Dashboard | `/dashboard` | wszyscy | dane i role z tokena, profil, edycja własnego e-maila, imienia i nazwiska |
| Users | `/users` | ADMIN, MANAGER | lista z wyszukiwaniem, sortowaniem i stronicowaniem (stan w URL), „Add user” |
| User details | `/users/:id` | ADMIN, MANAGER | profil, „Edit”; ADMIN: role, aktywacja/dezaktywacja, usuwanie (z potwierdzeniem) |
| New / Edit user | `/users/new`, `/users/:id/edit` | ADMIN, MANAGER | formularz z walidacją jak w backendzie, hasło tymczasowe tylko przy tworzeniu |
| Roles | `/roles` | ADMIN | role realmu, wybór użytkownika, nadawanie i odbieranie `ADMIN`/`MANAGER`/`AUDITOR` |
| Audit log | `/audit-logs` | ADMIN, AUDITOR | zdarzenia audytu z filtrami (typ, aktor, cel, zakres dat) i szczegółami |

Główny scenariusz (jako `admin`): **Users → Add user** → szczegóły → **Edit** → **Manage roles** (nadaj
`MANAGER`, odbierz) → **Deactivate** / **Activate** → **Delete** → **Audit log** pokazuje wszystkie operacje
z tym samym `correlationId` co w logach (Kibana).

## 4. Adresy

| Usługa | Adres |
|---|---|
| Frontend (Angular, `npm start`) | http://localhost:4200 |
| user-service | http://localhost:8081 (health: `/actuator/health`) |
| auth-service | http://localhost:8082 |
| audit-service | http://localhost:8083 |
| Keycloak — konsola admina | http://localhost:8080/admin (login: `KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD`) |
| Keycloak — realm `mini-iam` | http://localhost:8080/realms/mini-iam |
| Kibana (profil `elk`) | http://localhost:5601 (login: `elastic` / `ELASTIC_PASSWORD`) |
| Elasticsearch (profil `elk`) | http://localhost:9200 |
| PostgreSQL | localhost:5432, baza `mini_iam` (schematy `users`, `audit`) |

Wszystkie porty są publikowane tylko na `127.0.0.1`.

### Połączenie z bazą PostgreSQL

Klient SQL (DBeaver, IntelliJ Database, pgAdmin): host `localhost`, port `5432` (`POSTGRES_PORT` z `.env`),
JDBC `jdbc:postgresql://localhost:5432/mini_iam`. Każdy serwis ma własne konto i schemat (spec §8.4):

| Użytkownik | Hasło (z `.env`) | Dostęp |
|---|---|---|
| `postgres` | `POSTGRES_SUPERUSER_PASSWORD` | wszystko (superuser) — wygodny do przeglądania |
| `user_service` | `USER_SERVICE_DB_PASSWORD` | schemat `users` w bazie `mini_iam` |
| `audit_service` | `AUDIT_SERVICE_DB_PASSWORD` | schemat `audit` w bazie `mini_iam` |
| `keycloak` | `KEYCLOAK_DB_PASSWORD` | baza `keycloak` |

Tabele są w schematach `users` i `audit` (nie w `public`) — w kliencie włącz widoczność wszystkich schematów,
w zapytaniach używaj pełnych nazw, np. `SELECT * FROM audit.audit_events;`.

Bez instalowania klienta:

```bash
docker compose exec postgres psql -U postgres -d mini_iam   # \dn — schematy, \dt users.* / \dt audit.* — tabele
```

## 5. Konto administratora aplikacji

Realm import (`infrastructure/keycloak/realm-import/mini-iam-realm.json`) tworzy użytkownika **`admin`**
z rolami `ADMIN` i `USER`. Hasło pochodzi z `MINI_IAM_ADMIN_PASSWORD` w `.env` (placeholder rozwiązywany
przez Keycloak przy imporcie).

Konta testowe do logowania na każdą rolę (też z realm importu):

| Login | Role | Hasło |
|---|---|---|
| `manager` | `MANAGER`, `USER` | `MINI_IAM_DEMO_PASSWORD` z `.env`, tymczasowe — Keycloak wymusza zmianę przy pierwszym logowaniu |
| `auditor` | `AUDITOR`, `USER` | jw. |
| `user` | `USER` | jw. |

Profil w user-service powstaje przy pierwszym logowaniu (just-in-time). To konta deweloperskie — nie do
środowisk innych niż lokalne.

Realm jest importowany **tylko przy pierwszym starcie** (gdy nie istnieje w bazie Keycloak). Zmiana pliku
realmu lub haseł w `.env` wymaga czystego startu: `docker compose down -v`.

Konfiguracja realmu:

- role realmu: `ADMIN`, `MANAGER`, `USER`, `AUDITOR` + techniczna `SERVICE`; `USER` w `default-roles-mini-iam`;
- `mini-iam-frontend` — public client, Authorization Code + PKCE S256, `http://localhost:4200`;
- dwa confidential clients z service account (Client Credentials), rozdzielone wg uprawnień:
  - `mini-iam-admin` — **tylko Keycloak Admin API** (user-service, auth-service): role `realm-management`
    `view-users`, `query-users`, `manage-users`, `view-realm`; scope `kc-admin-api` (wymagany od Keycloak
    26.8.2); bez audience `mini-iam-api`, więc jego token nie działa na API Mini IAM;
  - `mini-iam-service` — **tylko wywołania serwis–serwis** (Feign → audit-service): realm role `SERVICE`
    i audience `mini-iam-api`, bez ról `realm-management` (token nie działa na Admin API);
- oba konta techniczne mają przypisaną rolę `SERVICE` (znacznik konta serwisowego — auth-service nie pozwala
  zmieniać ich ról), ale tylko `mini-iam-service` ma ją w scope mappingu, więc tylko jego token ją zawiera;
- audience `mini-iam-api` w tokenach `mini-iam-frontend` i `mini-iam-service`; `fullScopeAllowed=false`
  (tokeny zawierają tylko role przypisane w scope mappings);
- czas życia access tokena: 5 minut.

### Dodatkowe konta testowe (inne role)

Konsola Keycloak → realm `mini-iam` → **Users** → *Create user* (np. `manager1`) → zakładka **Credentials**
(ustaw hasło, `Temporary: off`) → **Role mapping** → *Assign role* → *Filter by realm roles* → `MANAGER`
lub `AUDITOR`. Rola `USER` jest nadawana automatycznie. Alternatywnie przez API: konto tworzy user-service
(`POST /api/users`, sekcja 7), a rolę nadaje auth-service (`POST /api/users/{keycloakId}/roles`, sekcja 8).

## 6. Bezpieczeństwo API

- Każdy serwis to OAuth2 Resource Server: weryfikuje podpis (JWKS z Keycloak), `iss`
  (`http://localhost:8080/realms/mini-iam`), `exp` i `aud` (`mini-iam-api`).
- Role z `realm_access.roles` są jawnie mapowane na `ROLE_<NAZWA>`; wbudowane role Keycloak są pomijane.
- Reguły: `/actuator/health` publiczny, `/internal/api/**` tylko `ROLE_SERVICE` (token Client Credentials
  klienta `mini-iam-service`), `/api/**` wymaga uwierzytelnienia (szczegółowe reguły ról — `@PreAuthorize`
  przy endpointach), pozostałe ścieżki są blokowane.
- 401/403 zwracane jako `application/problem+json` (RFC 9457) z nagłówkiem `X-Correlation-Id`.
- CORS: tylko originy z `CORS_ALLOWED_ORIGINS` (domyślnie `http://localhost:4200`).

Sprawdzenie z linii poleceń (token serwisowy):

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=mini-iam-service \
  -d "client_secret=$MINI_IAM_SERVICE_CLIENT_SECRET" \
  http://localhost:8080/realms/mini-iam/protocol/openid-connect/token | sed 's/.*"access_token":"\([^"]*\)".*/\1/')
curl -i http://localhost:8082/api/auth/me                                  # 401
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/auth/me   # 200
```

## 7. Zarządzanie użytkownikami (user-service)

API `http://localhost:8081/api/users` (spec §6.1). Uprawnienia wg macierzy spec §2.1:

| Endpoint | Role |
|---|---|
| `GET /api/users?page&size&sort&search` | ADMIN, MANAGER |
| `GET /api/users/{id}` | ADMIN, MANAGER (odpowiedź zawiera `editable` — czy wywołujący może edytować profil) |
| `POST /api/users` | ADMIN, MANAGER |
| `PUT /api/users/{id}` | ADMIN, MANAGER (MANAGER nie edytuje kont z rolą ADMIN) |
| `PATCH /api/users/{id}/status` | ADMIN |
| `DELETE /api/users/{id}` | ADMIN |
| `GET /api/users/me`, `PUT /api/users/me` | każdy zalogowany użytkownik |

Przykład (token użytkownika z rolą ADMIN w zmiennej `$TOKEN`, np. skopiowany z narzędzi deweloperskich
przeglądarki po zalogowaniu do frontendu; token Client Credentials nie ma ról aplikacyjnych):

```bash
curl -X POST http://localhost:8081/api/users -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json'   -d '{"username":"john.doe","email":"john.doe@example.com","firstName":"John","lastName":"Doe","temporaryPassword":"Temp-Pass-123"}'
curl "http://localhost:8081/api/users?search=john&size=20&sort=username,asc" -H "Authorization: Bearer $TOKEN"
curl -X PATCH http://localhost:8081/api/users/<id>/status -H "Authorization: Bearer $TOKEN"   -H 'Content-Type: application/json' -d '{"enabled":false}'
curl -X DELETE http://localhost:8081/api/users/<id> -H "Authorization: Bearer $TOKEN"
```

Zachowanie:

- **Lista:** `size` domyślnie 20; `size` > 100 → 400 `INVALID_REQUEST` (bez cichego przycinania); `sort` tylko po
  `username`, `email`, `firstName`, `lastName`, `enabled`, `createdAt`, `updatedAt`.
- **Nowe konto** powstaje w Keycloak z hasłem tymczasowym — przy pierwszym logowaniu Keycloak wymusza zmianę
  hasła. Hasło trafia tylko do Keycloak (nie jest zapisywane, logowane ani zwracane). Rola `USER` jest nadawana
  automatycznie (default roles). `username`/`email` zapisywane małymi literami; `username` jest niezmienny.
- **`id` vs `keycloakId`:** API user-service używa lokalnego `id` (UUID profilu); `keycloakId` (zwracany w odpowiedzi)
  identyfikuje konto w Keycloak i będzie używany przez auth-service.
- **Ostatni aktywny administrator** nie może zostać dezaktywowany ani usunięty (409 `LAST_ADMIN`). Liczone są konta
  z rolą ADMIN przypisaną bezpośrednio (tak przypisuje auth-service), nie przez grupy ani role złożone.
- **Zmiana e-maila** ustawia `emailVerified=false` w Keycloak (nowy adres nie jest zweryfikowany).
- **Profil just-in-time:** konta spoza aplikacji (np. `admin` z realm importu) dostają profil przy pierwszym
  `GET /api/users/me`; pojawiają się na liście dopiero po tym wywołaniu.
- **Częściowe awarie:** zmiana idzie najpierw do Keycloak, potem do bazy; gdy zapis lokalny się nie uda, zmiana
  w Keycloak jest wycofywana. Jeśli wycofanie też się nie uda, w logu jest ERROR z `keycloakId` i identyfikatorem
  profilu (do ręcznego uzgodnienia), a API zwraca 500 `USER_SYNC_FAILED`. `DELETE` można bezpiecznie ponowić.
- **Status:** źródłem prawdy jest Keycloak — `GET /api/users/{id}` wyrównuje lokalny `enabled` i loguje rozbieżność.
- **Własne konto:** ADMIN nie dezaktywuje ani nie usuwa własnego konta (403 `SELF_ACCOUNT_PROTECTED`); UI nie
  pokazuje tych akcji na własnym profilu.
- **Błędy:** ProblemDetail z polem `code` (np. `USERNAME_OR_EMAIL_TAKEN`, `VALIDATION_FAILED` + `errors`,
  `KEYCLOAK_UNAVAILABLE` → 503).
- **Audyt:** utworzenie, edycja, zmiana statusu i usunięcie konta trafiają do audit-service (sekcja 9).
- **Znane ograniczenia:** reguła ostatniego administratora nie jest chroniona blokadą — dwie równoczesne dezaktywacje dwóch ostatnich adminów
  mogą obie przejść; konto utworzone poza aplikacją (konsola Keycloak) bez imienia/nazwiska może zostać poproszone
  przez Keycloak o ich uzupełnienie przy logowaniu. Przez API imię i nazwisko są wymagane.

## 8. Role (auth-service)

API `http://localhost:8082` (spec §6.2). Role są realm roles w Keycloak — auth-service nie ma bazy ani cache,
każdy odczyt i zmiana idzie do Keycloak Admin API (klient `mini-iam-admin`). `{userId}` to **`keycloakId`**
(pole `keycloakId` z user-service), nie lokalne `id` profilu.

| Endpoint | Role | Odpowiedź |
|---|---|---|
| `GET /api/roles` | ADMIN, MANAGER | `[{name, description, assignable}]` — `ADMIN`, `MANAGER`, `USER` (`assignable=false`), `AUDITOR`; bez `SERVICE` |
| `GET /api/users/{userId}/roles` | ADMIN; pozostali tylko własne | `{userId, roles}` — efektywne role aplikacyjne |
| `POST /api/users/{userId}/roles` `{"roleName":"MANAGER"}` | ADMIN | 200 + aktualne `{userId, roles}` |
| `DELETE /api/users/{userId}/roles/{roleName}` | ADMIN | 204 |
| `GET /api/auth/me` | każdy zalogowany | dane i role z tokena |

```bash
curl -X POST http://localhost:8082/api/users/<keycloakId>/roles -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"roleName":"MANAGER"}'
curl http://localhost:8082/api/users/<keycloakId>/roles -H "Authorization: Bearer $TOKEN"
curl -X DELETE http://localhost:8082/api/users/<keycloakId>/roles/MANAGER -H "Authorization: Bearer $TOKEN"
```

Reguły i kody błędów (ProblemDetail z polem `code`):

| Sytuacja | HTTP / `code` |
|---|---|
| rola spoza `ADMIN`/`MANAGER`/`AUDITOR` (`USER`, `SERVICE`, nieznana; nazwy wielkimi literami) | 400 `ROLE_NOT_ASSIGNABLE` |
| niepoprawny `userId` / puste `roleName` | 400 `VALIDATION_FAILED` |
| zmiana własnych ról | 403 `SELF_ROLE_CHANGE` |
| konto serwisowe (`service-account-mini-iam-admin`, `service-account-mini-iam-service`) | 403 `SERVICE_ACCOUNT_PROTECTED` |
| użytkownik nie istnieje / rola nie istnieje w realmie | 404 `USER_NOT_FOUND` / `ROLE_NOT_FOUND` |
| odebranie roli, której użytkownik nie ma bezpośrednio | 404 `ROLE_NOT_ASSIGNED` |
| rola już przypisana | 409 `ROLE_ALREADY_ASSIGNED` |
| odebranie `ADMIN` ostatniemu aktywnemu administratorowi | 409 `LAST_ADMIN` |
| Keycloak niedostępny / odrzuca klienta technicznego | 503 `KEYCLOAK_UNAVAILABLE` / 502 `KEYCLOAK_INTEGRATION_ERROR` |

- Zmiana ról jest widoczna w tokenie użytkownika dopiero po jego odświeżeniu (do 5 min) lub ponownym logowaniu.
- **Audyt:** każda udana zmiana roli trafia do audit-service jako `ROLE_ASSIGNED` / `ROLE_REMOVED` (sekcja 9).
- **Znane ograniczenia:** role przypisywane wyłącznie bezpośrednio (bez grup); sprawdzenie ostatniego administratora
  nie jest atomowe ze zmianą (dwa równoczesne odebrania `ADMIN` dwóm ostatnim adminom mogą obie przejść).

## 9. Audyt (audit-service)

API `http://localhost:8083` (spec §6.3). Operacje administracyjne są zapisywane w tabeli `audit.audit_events`
(PostgreSQL, JSONB `details`). Wpisy są niezmienne: API nie ma edycji ani usuwania.

| Endpoint | Role | Opis |
|---|---|---|
| `GET /api/audit-events` | ADMIN, AUDITOR | lista z filtrami `eventType`, `actorUserId`, `targetUserId`, `from` (włącznie), `to` (wyłącznie); `page`, `size` (domyślnie 20, > 100 → 400 `INVALID_REQUEST`), `sort` = `occurredAt` / `eventType` (domyślnie `occurredAt,desc`) |
| `GET /api/audit-events/{id}` | ADMIN, AUDITOR | szczegóły; brak → 404 `AUDIT_EVENT_NOT_FOUND` |
| `POST /internal/api/audit-events` | tylko `ROLE_SERVICE` (token Client Credentials klienta `mini-iam-service`) | zapis zdarzenia przez serwisy → 201 `{id}` |

```bash
curl "http://localhost:8083/api/audit-events?targetUserId=<keycloakId>&sort=occurredAt,asc" \
  -H "Authorization: Bearer $TOKEN"
```

Rejestrowane zdarzenia (`actorUserId`, `targetUserId` = keycloakId; `actorUsername`, `targetUsername` = nazwy
użytkowników w chwili zdarzenia, puste w zdarzeniach sprzed ich wprowadzenia; `correlationId` = `X-Correlation-Id`
żądania, które spowodowało zdarzenie — ten sam identyfikator jest w logach i w Kibanie):

| Zdarzenie | Serwis | Operacja | `details` |
|---|---|---|---|
| `USER_CREATED` | user-service | `POST /api/users` | `{username}` |
| `USER_UPDATED` | user-service | `PUT /api/users/{id}`, `PUT /api/users/me` — tylko gdy coś się zmieniło | `{changedFields: [...]}` (nazwy pól, bez wartości) |
| `USER_ACTIVATED` / `USER_DEACTIVATED` | user-service | `PATCH /api/users/{id}/status` — tylko przy faktycznej zmianie | `{}` |
| `USER_DELETED` | user-service | `DELETE /api/users/{id}` | `{username}` |
| `ROLE_ASSIGNED` / `ROLE_REMOVED` | auth-service | `POST` / `DELETE /api/users/{userId}/roles` | `{roleName}` |

- **Kiedy:** zdarzenie jest wysyłane (OpenFeign, token Client Credentials z rolą `SERVICE`, timeouty connect/read 2 s/2 s, bez
  retry) dopiero po udanej operacji w Keycloak i bazie. Odrzucone operacje (403, 404, 409) nie są audytowane;
  profil tworzony just-in-time przy `GET /api/users/me` też nie.
- **Gdy audit-service nie działa (best effort):** operacja kończy się normalnie (2xx), a brak wpisu jest
  logowany jako ERROR `Audit event NOT recorded (operation already applied)` z typem zdarzenia, aktorem, celem
  i `correlationId` — do ręcznego uzupełnienia. Świadoma decyzja MVP (spec §7): dostępność operacji
  administracyjnych ważniejsza niż kompletność audytu; trwały outbox poza zakresem.
- **Dane:** w `details` nie ma haseł (także tymczasowych), tokenów ani sekretów; e-maile i wartości pól nie są
  zapisywane. audit-service odrzuca nieznane typy zdarzeń i `details` większe niż 4 KB / 20 kluczy (400).
- **Znane ograniczenia:** niezmienność zapewnia API (brak edycji/usuwania), nie uprawnienia w bazie — użytkownik DB
  `audit_service` jest właścicielem schematu (potrzebne do migracji Liquibase). Audyt działa w wątku żądania — gdy audit-service nie odpowiada, udana operacja kończy się po
  timeoucie (do ok. 2 s).

## 10. Logi i śledzenie żądań (ELK)

Każdy serwis przyjmuje lub generuje nagłówek `X-Correlation-Id`, zwraca go w odpowiedzi i dodaje
do każdego wpisu logu jako `correlationId` (spec §16).

Przepływ: serwisy (JSON/ECS na stdout, sterownik `json-file`) → Filebeat → Logstash → Elasticsearch
(indeks `mini-iam-logs-YYYY.MM.dd`) → Kibana (data view **Mini IAM logs**, tworzony automatycznie).

Śledzenie żądania:

```bash
curl -i -H "X-Correlation-Id: demo-123" http://localhost:8081/actuator/health
```

W Kibanie: **Discover** → data view *Mini IAM logs* → zapytanie `correlationId : "demo-123"`.
Żądania do `/actuator/**` nie generują logu zakończenia (healthchecki), ale też dostają `X-Correlation-Id`.

## 11. Testy

```bash
./mvnw test      # testy jednostkowe i MockMvc (reguły 401/403, CORS, converter ról)
./mvnw verify    # + testy integracyjne (*IT) z Testcontainers - wymaga działającego Dockera
cd frontend/mini-iam-ui && npm test -- --watch=false   # testy Angular (Vitest)
```

Testy integracyjne uruchamiają PostgreSQL i Keycloak (z tym samym realm importem co Compose)
w Testcontainers: migracje Liquibase na czystej bazie, walidacja prawdziwych tokenów
(poprawny token, zły issuer, brak audience, zmieniony podpis) oraz pełny cykl życia użytkownika w user-service
(tworzenie w Keycloak, duplikaty, edycja, status, ostatni admin, usuwanie, profil just-in-time) i zarządzanie
rolami w auth-service (przypisanie/odebranie w Keycloak, reguły biznesowe, prawdziwe tokeny użytkowników:
po nadaniu `MANAGER` token go zawiera, a `MANAGER`/`USER` dostają 403 przy zmianie ról). audit-service jest
testowany na prawdziwym PostgreSQL (zapis z `correlation_id`, JSONB, filtry, paginacja). Klient Feign do
audit-service (user-service i auth-service) jest testowany na lokalnym serwerze HTTP (nagłówki, brak retry,
timeout, błąd nie przerywa operacji).
Nie wymagają uruchomionego Compose ani ELK.

## 12. Typowe problemy

| Problem | Rozwiązanie |
|---|---|
| `PKIX path building failed` przy `./mvnw` (Windows, proxy/antywirus podmieniający certyfikaty) | `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` (PowerShell: `$env:MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"`) |
| `npm ci`: `can only install with an existing package-lock.json` (`EUSAGE`) | Uruchamiasz npm w złym katalogu — komendy npm tylko w `frontend/mini-iam-ui` (albo użyj `./scripts/start-frontend.sh`). |
| `npm ci` / `npx` kończy się błędem certyfikatu (`UNABLE_TO_GET_ISSUER_CERT_LOCALLY`) | `NODE_OPTIONS=--use-system-ca` (używa magazynu certyfikatów systemu) |
| Frontend: pętla przekierowań lub 401 po zalogowaniu | Keycloak i serwisy muszą działać (`docker compose ps`); issuer w tokenie to `http://localhost:8080/...` — otwieraj Keycloak przez `localhost`, nie `127.0.0.1`. |
| Zmiana roli nie jest widoczna od razu | Role są w tokenie; zmiana obowiązuje po odświeżeniu tokena (do 5 min) lub ponownym zalogowaniu (spec §11.2). |
| user-/auth-service nie startuje: `Could not resolve placeholder 'KEYCLOAK_ADMIN_CLIENT_SECRET'` lub `'SERVICE_CLIENT_SECRET'` (uruchomienie poza Dockerem) | Ustaw `KEYCLOAK_ADMIN_CLIENT_SECRET` = `MINI_IAM_ADMIN_CLIENT_SECRET` i `SERVICE_CLIENT_SECRET` = `MINI_IAM_SERVICE_CLIENT_SECRET` z `.env` (w Compose przekazywane automatycznie). |
| `required variable MINI_IAM_SERVICE_CLIENT_SECRET is missing` / audyt nie zapisuje się po aktualizacji repo | Dodaj `MINI_IAM_SERVICE_CLIENT_SECRET` do `.env` (wzór w `.env.example`) i zrób czysty start `docker compose down -v` (nowy klient trafia do Keycloak tylko przy imporcie realmu; **kasuje dane**). |
| Brak wpisów audytu, w logu `Audit event NOT recorded ... HTTP 401` | Sekret `MINI_IAM_SERVICE_CLIENT_SECRET` w `.env` różni się od zaimportowanego do Keycloak albo realm jest sprzed dodania klienta `mini-iam-service` — `docker compose down -v`. |
| auth-service: 403 `SELF_ROLE_CHANGE` przy zmianie ról | Administrator nie zmienia własnych ról (spec §2) — zmianę musi wykonać inny ADMIN. |
| user-service / auth-service: 502 `KEYCLOAK_INTEGRATION_ERROR` | Klient `mini-iam-admin` odrzucony przez Keycloak — sprawdź, czy `MINI_IAM_ADMIN_CLIENT_SECRET` w `.env` jest taki sam jak przy imporcie realmu (zmiana sekretu wymaga `docker compose down -v`). |
| `required variable ... is missing a value` przy `docker compose` | Brak `.env` lub brakująca zmienna — skopiuj `.env.example` do `.env`. |
| Testy `*IT` nie startują (`Could not find a valid Docker environment`) | Uruchom Docker Desktop. |
| Admin API Keycloak zwraca 401 dla service account | Token musi zawierać scope `kc-admin-api` (Keycloak ≥ 26.8.2) — jest w realm imporcie; przy starym wolumenie zrób `docker compose down -v`. |
| Zmiany w realm imporcie nie są widoczne | Realm importuje się tylko raz — `docker compose down -v`. |
| Brak logów w Kibanie | Sprawdź `docker compose --profile elk logs filebeat logstash`; serwisy muszą mieć sterownik logów `json-file` (ustawiony w Compose) i etykietę `co.elastic.logs/enabled=true`. Zawęź zakres czasu w Discover. |
| Elasticsearch kończy się błędem pamięci | Zwiększ pamięć dla Docker Desktop (min. 4 GB z profilem `elk`). |
| PostgreSQL: `password authentication failed` po zmianie haseł w `.env` | Konta bazy tworzy `infrastructure/postgres/init/01-init.sh` tylko przy pustym wolumenie — zmień hasło jako `postgres` (`ALTER ROLE user_service PASSWORD '...';`) albo `docker compose down -v` (**kasuje dane**). |
| Port zajęty | Zmień odpowiedni `*_PORT` w `.env`. |
