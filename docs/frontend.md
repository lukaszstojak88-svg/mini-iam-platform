# Mini IAM Platform — specyfikacja frontendu

> Ustalenia, funkcjonalności i ograniczenia aplikacji Angular (`frontend/mini-iam-ui`).
> Uzupełnia [`project-spec.md`](project-spec.md) (§2, §5.2, §10, §11) — w razie konfliktu nadrzędna jest
> `project-spec.md`. Dokument obowiązuje przy każdej pracy nad frontendem; zmiany ustaleń dopisujemy tutaj.

## 1. Status

| Element | Status |
|---|---|
| Projekt Angular, Material, logowanie OIDC + PKCE, guard, interceptory | gotowe (Etap 2) |
| Shell (menu wg roli), Dashboard (profil + edycja), Access Denied | gotowe (Etap 6) |
| Users, User Details, Create/Edit User, Roles, Audit Logs | gotowe (Etap 6) |

## 2. Stack i konwencje

- Angular 22 (standalone components, sygnały, `inject()`, nowa składnia `@if/@for/@switch`), TypeScript ~6.0,
  RxJS 7, Angular Router, Reactive Forms, HttpClient z funkcyjnymi interceptorami.
- Angular Material 22 (Material 3) z własnym motywem — **bez innych bibliotek UI** (spec §10.3).
- keycloak-angular 22 + keycloak-js 26.
- Testy: Vitest (`@angular/build:unit-test`, jsdom).
- Nazewnictwo plików jak w Angular CLI 22: `dashboard.ts/.html/.scss/.spec.ts` (bez sufiksu `.component`).
- Teksty w UI **po angielsku** (decyzja użytkownika z Etapu 6: etykiety, komunikaty, błędy); nazwy w kodzie
  i komentarze po angielsku; dokumentacja po polsku. Locale domyślne `en-US`.
- Bez nowych zależności npm bez uzasadnienia i akceptacji użytkownika.

### Struktura `src/app`

```text
core/
  auth/        AuthService (role, userId z tokena), authGuard, AppRole
  http/        http-error.interceptor (401/403), api-error (mapowanie błędów na komunikaty)
  api/         auth-api, user-api, role-api, audit-api + api-models (typy DTO), http-params
layout/
  shell/       sidebar (menu wg roli) + topbar + <router-outlet>
features/
  dashboard/  users/  user-details/  user-form/  roles/  audit-logs/  access-denied/
shared/        page-header, status-pill, stat-tile, load-state, confirm-dialog (+ ConfirmService),
               notify.service (snackbar), form-errors (błędy serwera przy polach), list-query (stan list w URL)
testing/       test-helpers (stuby AuthService, dane testowe)
environments/  environment.ts - adresy Keycloak i API (bez sekretów)
```

Wszystkie widoki ładowane leniwie (`loadComponent`); parametry tras (`:id`) trafiają do `input()` komponentu
(`withComponentInputBinding`).

## 3. Uwierzytelnianie (spec §5.2)

- Keycloak: realm `mini-iam`, klient publiczny `mini-iam-frontend`, Authorization Code + PKCE `S256`.
- `onLoad: 'check-sso'` + `public/silent-check-sso.html`; logowanie wywołuje guard (przekierowanie do Keycloak) —
  aplikacja nie ma własnego formularza logowania i nigdy nie przechowuje haseł.
- Access token dołączany przez `includeBearerTokenInterceptor` **wyłącznie** do URL-i pasujących do
  `environment.apiUrlPattern` (`http://localhost:808[1-3]/api/**`). ID token nigdy nie jest wysyłany do API.
- Automatyczne odświeżanie tokena (`withAutoRefreshToken`); po 30 min bezczynności wylogowanie
  (`environment.sessionIdleTimeoutMs`).
- Wylogowanie: `keycloak.logout({ redirectUri: origin })`.
- Zmiana ról działa po odświeżeniu tokena (do 5 min) lub ponownym logowaniu (spec §11.2) — UI nie obiecuje
  natychmiastowego efektu (komunikat po zmianie roli).

## 4. Autoryzacja w UI

UI **nie jest granicą bezpieczeństwa** — backend zawsze sprawdza uprawnienia; UI tylko ukrywa niedostępne akcje.

- Role do decyzji w UI: `realm_access.roles` z access tokena (`AuthService.roles`), tylko `ADMIN`, `MANAGER`,
  `USER`, `AUDITOR`. Rola `SERVICE` nigdy nie jest pokazywana.
- Trasy: `authGuard` + `data.roles` (dowolna z wymienionych ról); brak roli → `/access-denied`.
- 403 z API: `httpErrorInterceptor` przekierowuje na `/access-denied` **tylko** gdy ProblemDetail nie ma `code`
  (odmowa Spring Security — brak roli). 403 z `code` (`SELF_ROLE_CHANGE`, `SERVICE_ACCOUNT_PROTECTED`,
  `ADMIN_ACCOUNT_PROTECTED`, `SELF_ACCOUNT_PROTECTED`) to reguła biznesowa — widok pokazuje komunikat.
- Widoczność menu i akcji wg macierzy spec §2.1:

| Pozycja / akcja | ADMIN | MANAGER | USER | AUDITOR |
|---|:-:|:-:|:-:|:-:|
| Dashboard (własny profil) | ✓ | ✓ | ✓ | ✓ |
| Edycja własnego profilu (e-mail, imię, nazwisko) | ✓ | ✓ | ✓ | ✓* |
| Menu **Users**: lista, szczegóły | ✓ | ✓ | — | — |
| Tworzenie / edycja użytkownika | ✓ | ✓ | — | — |
| Aktywacja / dezaktywacja, usunięcie | ✓ | — | — | — |
| Role użytkownika: odczyt | ✓ | — | własne (Dashboard) | — |
| Menu **Roles**: przypisanie / odebranie | ✓ | — | — | — |
| Menu **Audit log** | ✓ | — | — | ✓ |

Edycja własnego profilu (`PUT /api/users/me`) odbywa się na Dashboardzie. *Każde konto ma bazową rolę `USER`
(default roles realmu), więc backend (`hasAnyRole('ADMIN','MANAGER','USER')`) pozwala na nią także audytorom —
UI pokazuje formularz każdemu, kto ma rolę `USER`.

## 5. Widoki MVP (spec §10.1)

Każdy widok obsługuje stany **loading / empty / error** (z przyciskiem „Try again”).

| Widok | Trasa | Role | API |
|---|---|---|---|
| Login | przekierowanie do Keycloak | — | — |
| Dashboard | `/dashboard` | wszyscy | `GET /api/auth/me`, `GET /api/users/me`, `PUT /api/users/me` |
| Users | `/users` | ADMIN, MANAGER | `GET /api/users?page&size&sort&search` |
| User Details | `/users/:id` | ADMIN, MANAGER | `GET /api/users/{id}`, `PATCH /api/users/{id}/status`, `DELETE /api/users/{id}`, `GET /api/users/{keycloakId}/roles` (tylko ADMIN) |
| Create / Edit User | `/users/new`, `/users/:id/edit` | ADMIN, MANAGER | `POST /api/users`, `PUT /api/users/{id}` |
| Roles | `/roles` | ADMIN | `GET /api/roles`, `GET/POST/DELETE /api/users/{keycloakId}/roles[/{roleName}]` |
| Audit Logs | `/audit-logs` | ADMIN, AUDITOR | `GET /api/audit-events` (szczegóły zdarzenia z wiersza listy, bez osobnego GET) |
| Access Denied | `/access-denied` | zalogowani | — |

### Szczegóły funkcjonalne

- **Dashboard** — nagłówek strony, kafelki z podstawowymi informacjami (np. liczba ról użytkownika), karta profilu
  (imię, nazwisko, username, e-mail, keycloakId, role jako chipy), formularz edycji dozwolonych pól własnego profilu.
- **Users** — `mat-table` w karcie; kolumny: użytkownik (imię i nazwisko + username), e-mail, status (pill),
  data utworzenia, akcje. Sortowanie tylko po polach obsługiwanych przez API (`username`, `email`, `firstName`,
  `lastName`, `enabled`, `createdAt`, `updatedAt`); odpowiedź: `{content, page, size, totalElements, totalPages}`. Wyszukiwanie `search` z debounce ~300 ms, paginacja i sortowanie **po stronie serwera**
  (`page`, `size` ∈ {10, 20, 50, 100}, maks. 100 — spec §6.1), przycisk „Dodaj użytkownika” (ADMIN, MANAGER).
  Stan listy (strona, filtr) w query params, żeby odświeżenie strony go zachowało.
- **User Details** — dane profilu, status, role (z auth-service po `keycloakId` — nie mylić z lokalnym `id`).
  Akcje ADMIN: aktywuj/dezaktywuj, usuń — zawsze z dialogiem potwierdzenia; **na własnym koncie ukryte** (z notką),
  backend i tak odrzuca je kodem 403 `SELF_ACCOUNT_PROTECTED`. Akcja „Edytuj” (ADMIN, MANAGER).
- **Create / Edit User** — Reactive Forms; walidacja zgodna z backendem: `username` wymagany, 3–100 znaków
  `[A-Za-z0-9._-]` (backend zapisuje małymi literami); `email` wymagany, format e-mail, ≤ 255; `firstName`,
  `lastName` **wymagane** (niepuste), ≤ 100 — także przy edycji i w edycji własnego profilu na Dashboardzie. Tylko przy tworzeniu: **hasło tymczasowe** (wymagane, 8–128 znaków,
  `type="password"`, z polem potwierdzenia po stronie UI) — trafia wyłącznie do Keycloak, użytkownik zmienia je
  przy pierwszym logowaniu; formularz nie przechowuje go po wysłaniu. `username` nieedytowalny po utworzeniu.
  Brak pól ról. Błędy: 400 `VALIDATION_FAILED` → komunikaty przy polach z `errors[]`; 409
  `USERNAME_OR_EMAIL_TAKEN` → komunikat przy polach username/e-mail; 403 `ADMIN_ACCOUNT_PROTECTED` (MANAGER
  edytuje konto ADMIN) → komunikat w formularzu. Przycisk zapisu zablokowany w trakcie wysyłania.
- **Roles** — lista ról aplikacyjnych (`GET /api/roles`, bez `SERVICE`) z opisami; wybór użytkownika
  (wyszukiwarka) → przypisane role jako chipy; przypisanie `ADMIN`/`MANAGER`/`AUDITOR`, odebranie z potwierdzeniem.
  `USER` pokazany jako rola bazowa bez możliwości odebrania. Akcje na własnym koncie zablokowane
  (użytkownik nie zmienia własnych ról). Błąd „ostatni administrator” pokazany czytelnym komunikatem.
  Tworzenie ról, szablony i import CSV — poza zakresem.
  Kontrakt auth-service (Etap 4):
  - `GET /api/roles` (ADMIN, MANAGER) → `[{name, description, assignable}]` w kolejności ADMIN, MANAGER, USER,
    AUDITOR; o tym, czy pokazać akcje przypisania/odebrania, decyduje `assignable` (`false` dla `USER`);
  - `GET /api/users/{keycloakId}/roles` (ADMIN; inni tylko własne) → `{userId, roles: [...]}` (efektywne role);
  - `POST /api/users/{keycloakId}/roles` z `{"roleName": "MANAGER"}` → **200** z aktualnym `{userId, roles}` —
    UI odświeża chipy z odpowiedzi, bez dodatkowego GET; `DELETE .../roles/{roleName}` → **204** (UI usuwa chip);
  - zmiana ról innej osoby nie zmienia tokena zalogowanego ADMIN-a; użytkownik zobaczy nowe role po odświeżeniu
    swojego tokena (do 5 min) — UI może to zaznaczyć w komunikacie sukcesu;
  - konta serwisowe nie są użytkownikami aplikacji — 403 `SERVICE_ACCOUNT_PROTECTED` pokazujemy jako komunikat.
- **Audit Logs** — tabela tylko do odczytu: czas (lokalna strefa przeglądarki, dane w UTC), typ zdarzenia,
  aktor, użytkownik docelowy, szczegóły (rozwijane / dialog z `details` i `correlationId`). Filtry: typ zdarzenia,
  aktor, użytkownik docelowy, zakres dat (niepoprawna data blokuje filtrowanie z komunikatem); paginacja serwerowa;
  strona poza zakresem (stary link) → ostatnia istniejąca strona. Brak edycji i usuwania.
  Kontrakt audit-service (Etap 5):
  - `GET /api/audit-events?eventType&actorUserId&targetUserId&from&to&page&size&sort` (ADMIN, AUDITOR) →
    `{content, page, size, totalElements, totalPages}`; element: `{id, eventType, actorUserId, actorUsername,
    targetUserId, targetUsername, details, occurredAt, correlationId}`; `from` włącznie, `to` wyłącznie (ISO-8601 UTC); `sort` tylko
    `occurredAt` / `eventType` (inne → 400), domyślnie `occurredAt,desc`; `size` > 100 → 400 (UI nie wysyła
    większych stron);
  - `GET /api/audit-events/{id}` → jeden wpis, brak → 404 `AUDIT_EVENT_NOT_FOUND`;
  - typy: `USER_CREATED`, `USER_UPDATED`, `USER_ACTIVATED`, `USER_DEACTIVATED`, `USER_DELETED`, `ROLE_ASSIGNED`,
    `ROLE_REMOVED` (nieznany typ w filtrze → 400); `details`: `{username}`, `{changedFields: [...]}`, `{roleName}`
    lub `{}`;
  - aktor i cel UI pokazuje jako nazwy użytkowników (`actorUsername`, `targetUsername`) zapisane w zdarzeniu
    przez serwis publikujący; pełny `keycloakId` jest w podpowiedzi i w dialogu szczegółów. Starsze zdarzenia
    bez nazw: cel z `details.username` (`USER_CREATED`, `USER_DELETED`), a w pozostałych przypadkach skrócony
    `keycloakId` (8 znaków). Przy zdarzeniu jest też `details.roleName`. UI **nie** pobiera nazw z user-service,
    bo AUDITOR nie ma dostępu do `GET /api/users`; filtry aktora i celu nadal działają po `keycloakId`;
  - zakres dat w UI to dni kalendarzowe w strefie przeglądarki, oba włącznie; wysyłane jako `from` = początek
    pierwszego dnia i `to` = początek dnia po ostatnim (UTC, `to` wyłącznie). W URL: `from`/`to` jako `YYYY-MM-DD`,
    `actor`, `target`, `eventType`.
- Operacje zapisu w user-service i auth-service nie zwracają błędów audytu: przy niedostępnym audit-service
  operacja kończy się sukcesem (audyt best effort), więc UI nie obsługuje kodów `AUDIT_*`.
- **Access Denied** — komunikat i powrót do Dashboardu.

## 6. Komunikacja z API i obsługa błędów

- Adresy w `environment.api` (`userService` :8081, `authService` :8082, `auditService` :8083); brak API Gateway.
- Identyfikatory: lokalny `id` (UUID) dla user-service, `keycloakId` dla auth-service i audytu — user-service zwraca
  `keycloakId` w DTO.
- Daty z API w ISO-8601 UTC; wyświetlane w strefie przeglądarki (`DatePipe`, locale `en-US`).
- Błędy w formacie ProblemDetail (`application/problem+json`) z właściwością `code` (lista kodów user-service:
  `VALIDATION_FAILED` + `errors[{field, message}]`, `INVALID_REQUEST`, `INVALID_USER_DATA`, `USER_NOT_FOUND`,
  `USERNAME_OR_EMAIL_TAKEN`, `LAST_ADMIN`, `ADMIN_ACCOUNT_PROTECTED`, `SELF_ACCOUNT_PROTECTED`, `USER_SYNC_FAILED`, `KEYCLOAK_UNAVAILABLE`,
  `KEYCLOAK_INTEGRATION_ERROR`, `INTERNAL_ERROR`; dodatkowo auth-service: `ROLE_NOT_ASSIGNABLE`, `ROLE_NOT_FOUND`,
  `ROLE_NOT_ASSIGNED`, `RESOURCE_NOT_FOUND`, `ROLE_ALREADY_ASSIGNED`, `SELF_ROLE_CHANGE`, `SERVICE_ACCOUNT_PROTECTED`;
  audit-service: `VALIDATION_FAILED`, `INVALID_REQUEST`, `AUDIT_EVENT_NOT_FOUND`) — komunikaty UI mapujemy po `code`, a gdy go brak, po statusie:
  - 401 → ponowne logowanie (`httpErrorInterceptor`); 403 bez `code` → `/access-denied`, 403 z `code` → komunikat
    w widoku;
  - 400 → błędy walidacji przy polach formularza; 404 → „Not found.”; 409 → komunikat konfliktu;
  - 502/503/0 → „Service temporarily unavailable.”;
  - komunikaty w UI po angielsku z własnego słownika (`core/http/api-error.ts`), mapowane z `code`, a bez niego
    ze statusu; surowego `detail` z backendu nie wyświetlamy (spójność, brak szczegółów technicznych);
  - przy błędach 5xx dokładamy identyfikator `X-Correlation-Id` z odpowiedzi („Request ID”) — ułatwia
    wyszukanie w Kibanie (CORS eksponuje ten nagłówek).
- Sukces operacji: `MatSnackBar` z krótkim komunikatem.

## 7. Wygląd (spec §10.3, wzorzec `docs/gui.jpg`)

Z makiety bierzemy **tylko styl i układ**:

- Sidebar ciemny granatowy (`#111827`), nazwa aplikacji u góry, sekcja „MENU”, aktywna pozycja w kolorze
  indygo (`#4f46e5`) z zaokrągleniem; ikony Material Icons.
- Topbar biały z dolną ramką; po prawej użytkownik i „Wyloguj”.
- Treść na jasnym tle (`#f8fafc`), maks. szerokość czytelna, odstępy 24–32 px.
- Nagłówek strony: tytuł + opis po lewej, przyciski akcji po prawej (główny: wypełniony indygo, drugorzędny: obrys).
- Kafelki statystyk (karty z ikoną w kółku, etykieta wersalikami, duża liczba).
- Tabele w kartach (białe, zaokrąglenie ~12 px, delikatna ramka), nagłówek tabeli na jasnym tle, wiersze z separatorem.
- Statusy jako „pill” z kolorową kropką: aktywny — zielony, nieaktywny — czerwony; typy zdarzeń audytu
  jako neutralne pill.
- Motyw: własny motyw Angular Material (paleta zbliżona do indygo), tokeny kolorów jako zmienne CSS w `styles.scss`;
  font Roboto (domyślny Material).
- Brak poziomego przewijania układu (sidebar ma `overflow-x: hidden`). Minimalna obsługiwana szerokość: 1280 px
  (aplikacja administracyjna, desktop); poniżej sidebar może się zwijać — bez dedykowanego widoku mobilnego w MVP.
- Treść nie może wychodzić poza karty przy wąskim oknie: kolumny gridu z wartościami jako `minmax(0, 1fr)`,
  długie wartości (e-mail, ID) z `overflow-wrap: anywhere`; listy etykieta–wartość w kartach przechodzą
  w jedną kolumnę przez container query (`container-type: inline-size` na karcie).

## 8. Poza zakresem MVP

Z makiety **nie implementujemy**: tworzenia ról, szablonów ról, importu CSV, eksportu, „Pending Reviews”,
Settings, Billing, globalnej wyszukiwarki, powiadomień, „Help & Docs”. Ponadto: rejestracja publiczna,
reset hasła w aplikacji (robi to Keycloak), MFA, tryb ciemny, i18n (tylko angielski), widok mobilny.

## 9. Testy i jakość

- Testy jednostkowe (Vitest) dla: guardów i logiki ról, interceptorów, serwisów API (`HttpTestingController`),
  formularzy (walidacja, obsługa 409), komponentów widoków (stany loading/empty/error, widoczność akcji per rola).
- `npx ng test --watch=false` i `npx ng build` muszą przechodzić przed zakończeniem etapu.
- Budżet bundla: ostrzeżenie 750 kB, błąd 1.5 MB (`angular.json`); wszystkie widoki ładowane leniwie
  (`loadComponent`) — początkowy bundle ok. 600 kB.
- Testy widoków korzystają z `RouterTestingHarness` (prawdziwy router, query params, binding `:id`) i stubów
  serwisów API; wspólne helpery w `src/testing/test-helpers.ts`.
- Dostępność: etykiety pól (`mat-label`), `aria-label` dla przycisków-ikon, kontrast tekstu, obsługa klawiatury
  w tabelach i dialogach.

## 10. Uruchamianie i ograniczenia środowiska

- `./scripts/start-frontend.sh [--with-backend]` albo w `frontend/mini-iam-ui`: `npm ci`, `npm start`
  (http://localhost:4200). Komendy npm tylko w tym katalogu.
- Na maszynie z proxy TLS: `NODE_OPTIONS=--use-system-ca`.
- Frontend nie jest uruchamiany przez Docker Compose (spec §14 — opcjonalne).
- CORS backendu dopuszcza tylko `http://localhost:4200` (`CORS_ALLOWED_ORIGINS`); inny port wymaga zmiany
  `.env`, realmu (redirect URI / web origins) i `environment.ts`.
- Ikony i font ładowane z Google Fonts — bez internetu ikony się nie wyświetlą.
- Jedna konfiguracja środowiska (`environment.ts`, lokalnie); konfiguracja produkcyjna poza zakresem MVP.
