# Mini IAM Platform — specyfikacja frontendu

> Ustalenia, funkcjonalności i ograniczenia aplikacji Angular (`frontend/mini-iam-ui`).
> Uzupełnia [`project-spec.md`](project-spec.md) (§2, §5.2, §10, §11) — w razie konfliktu nadrzędna jest
> `project-spec.md`. Dokument obowiązuje przy każdej pracy nad frontendem; zmiany ustaleń dopisujemy tutaj.

## 1. Status

| Element | Status |
|---|---|
| Projekt Angular, Material, logowanie OIDC + PKCE, guard, interceptory | gotowe (Etap 2) |
| Shell (sidebar + topbar), Dashboard (`/api/auth/me`), Access Denied | gotowe (Etap 2), wygląd do dopracowania w Etapie 6 |
| Users, User Details, Create/Edit User, Roles, Audit Logs | Etap 6 (wymaga API z Etapów 3–5) |

## 2. Stack i konwencje

- Angular 22 (standalone components, sygnały, `inject()`, nowa składnia `@if/@for/@switch`), TypeScript ~6.0,
  RxJS 7, Angular Router, Reactive Forms, HttpClient z funkcyjnymi interceptorami.
- Angular Material 22 (Material 3) z własnym motywem — **bez innych bibliotek UI** (spec §10.3).
- keycloak-angular 22 + keycloak-js 26.
- Testy: Vitest (`@angular/build:unit-test`, jsdom).
- Nazewnictwo plików jak w Angular CLI 22: `dashboard.ts/.html/.scss/.spec.ts` (bez sufiksu `.component`).
- Teksty w UI **po polsku**; nazwy w kodzie i komentarze **po angielsku**.
- Bez nowych zależności npm bez uzasadnienia i akceptacji użytkownika.

### Struktura `src/app`

```text
core/
  auth/        AuthService, authGuard, AppRole
  http/        interceptory (błędy HTTP)
  api/         jeden serwis na backend: auth-api, user-api, audit-api (+ typy odpowiedzi)
layout/
  shell/       sidebar + topbar + <router-outlet>
features/
  dashboard/  users/  user-details/  user-form/  roles/  audit-logs/  access-denied/
shared/        komponenty wielokrotnego użytku (status pill, stat tile, page header, confirm dialog)
environments/  environment.ts - adresy Keycloak i API (bez sekretów)
```

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
- Widoczność menu i akcji wg macierzy spec §2.1:

| Pozycja / akcja | ADMIN | MANAGER | USER | AUDITOR |
|---|:-:|:-:|:-:|:-:|
| Dashboard (własny profil) | ✓ | ✓ | ✓ | ✓ |
| Edycja własnego profilu | ✓ | ✓ | ✓ | — |
| Menu **Users**: lista, szczegóły | ✓ | ✓ | — | — |
| Tworzenie / edycja użytkownika | ✓ | ✓ | — | — |
| Aktywacja / dezaktywacja, usunięcie | ✓ | — | — | — |
| Role użytkownika: odczyt | ✓ | — | własne (Dashboard) | — |
| Menu **Roles**: przypisanie / odebranie | ✓ | — | — | — |
| Menu **Audit log** | ✓ | — | — | ✓ |

Założenie (do potwierdzenia w Etapie 6): edycja własnego profilu (`PUT /api/users/me`) odbywa się na Dashboardzie;
AUDITOR nie ma edycji profilu, bo macierz §2.1 jej nie przewiduje.

## 5. Widoki MVP (spec §10.1)

Każdy widok obsługuje stany **loading / empty / error** (z przyciskiem „Spróbuj ponownie”).

| Widok | Trasa | Role | API |
|---|---|---|---|
| Login | przekierowanie do Keycloak | — | — |
| Dashboard | `/dashboard` | wszyscy | `GET /api/auth/me`, `GET /api/users/me`, `PUT /api/users/me` |
| Users | `/users` | ADMIN, MANAGER | `GET /api/users?page&size&sort&search` |
| User Details | `/users/:id` | ADMIN, MANAGER | `GET /api/users/{id}`, `PATCH /api/users/{id}/status`, `DELETE /api/users/{id}`, `GET /api/users/{keycloakId}/roles` (tylko ADMIN) |
| Create / Edit User | `/users/new`, `/users/:id/edit` | ADMIN, MANAGER | `POST /api/users`, `PUT /api/users/{id}` |
| Roles | `/roles` | ADMIN | `GET /api/roles`, `GET/POST/DELETE /api/users/{keycloakId}/roles[/{roleName}]` |
| Audit Logs | `/audit-logs` | ADMIN, AUDITOR | `GET /api/audit-events`, `GET /api/audit-events/{id}` |
| Access Denied | `/access-denied` | zalogowani | — |

### Szczegóły funkcjonalne

- **Dashboard** — nagłówek strony, kafelki z podstawowymi informacjami (np. liczba ról użytkownika), karta profilu
  (imię, nazwisko, username, e-mail, keycloakId, role jako chipy), formularz edycji dozwolonych pól własnego profilu.
- **Users** — `mat-table` w karcie; kolumny: użytkownik (imię i nazwisko + username), e-mail, status (pill),
  data utworzenia, akcje. Wyszukiwanie `search` z debounce ~300 ms, paginacja i sortowanie **po stronie serwera**
  (`page`, `size` ∈ {10, 20, 50, 100}, maks. 100 — spec §6.1), przycisk „Dodaj użytkownika” (ADMIN, MANAGER).
  Stan listy (strona, filtr) w query params, żeby odświeżenie strony go zachowało.
- **User Details** — dane profilu, status, role (z auth-service po `keycloakId` — nie mylić z lokalnym `id`).
  Akcje ADMIN: aktywuj/dezaktywuj, usuń — zawsze z dialogiem potwierdzenia. Akcja „Edytuj” (ADMIN, MANAGER).
- **Create / Edit User** — Reactive Forms; walidacja zgodna z backendem: `username` wymagany, ≤ 100;
  `email` wymagany, format e-mail, ≤ 255; `firstName`, `lastName` opcjonalne, ≤ 100 (spec §8.1).
  `username` nieedytowalny po utworzeniu. Brak pól hasła i ról. Błąd 409 → komunikat przy polu (duplikat
  username/e-mail). Przycisk zapisu zablokowany w trakcie wysyłania.
- **Roles** — lista ról aplikacyjnych (`GET /api/roles`, bez `SERVICE`) z opisami; wybór użytkownika
  (wyszukiwarka) → przypisane role jako chipy; przypisanie `ADMIN`/`MANAGER`/`AUDITOR`, odebranie z potwierdzeniem.
  `USER` pokazany jako rola bazowa bez możliwości odebrania. Akcje na własnym koncie zablokowane
  (użytkownik nie zmienia własnych ról). Błąd „ostatni administrator” pokazany czytelnym komunikatem.
  Tworzenie ról, szablony i import CSV — poza zakresem.
- **Audit Logs** — tabela tylko do odczytu: czas (lokalna strefa przeglądarki, dane w UTC), typ zdarzenia,
  aktor, użytkownik docelowy, szczegóły (rozwijane / dialog z `details` i `correlationId`). Filtry: typ zdarzenia,
  aktor, użytkownik docelowy, zakres dat; paginacja serwerowa. Brak edycji i usuwania.
- **Access Denied** — komunikat i powrót do Dashboardu.

## 6. Komunikacja z API i obsługa błędów

- Adresy w `environment.api` (`userService` :8081, `authService` :8082, `auditService` :8083); brak API Gateway.
- Identyfikatory: lokalny `id` (UUID) dla user-service, `keycloakId` dla auth-service i audytu — user-service zwraca
  `keycloakId` w DTO.
- Daty z API w ISO-8601 UTC; wyświetlane w strefie przeglądarki (`DatePipe`, locale `pl`).
- Błędy w formacie ProblemDetail (`application/problem+json`):
  - 401 → ponowne logowanie (`httpErrorInterceptor`), 403 → `/access-denied`;
  - 400 → błędy walidacji przy polach formularza; 404 → „Nie znaleziono”; 409 → komunikat konfliktu;
  - 502/503/0 → „Usługa chwilowo niedostępna”;
  - komunikaty w UI po polsku, mapowane ze statusu (nie wyświetlamy surowego `detail` z backendu, który jest po angielsku);
  - przy błędach 5xx pokazujemy identyfikator `X-Correlation-Id` z odpowiedzi („Identyfikator zgłoszenia”) —
    ułatwia wyszukanie w Kibanie.
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

## 8. Poza zakresem MVP

Z makiety **nie implementujemy**: tworzenia ról, szablonów ról, importu CSV, eksportu, „Pending Reviews”,
Settings, Billing, globalnej wyszukiwarki, powiadomień, „Help & Docs”. Ponadto: rejestracja publiczna,
reset hasła w aplikacji (robi to Keycloak), MFA, tryb ciemny, i18n (tylko polski), widok mobilny.

## 9. Testy i jakość

- Testy jednostkowe (Vitest) dla: guardów i logiki ról, interceptorów, serwisów API (`HttpTestingController`),
  formularzy (walidacja, obsługa 409), komponentów widoków (stany loading/empty/error, widoczność akcji per rola).
- `npx ng test --watch=false` i `npx ng build` muszą przechodzić przed zakończeniem etapu.
- Budżet bundla: ostrzeżenie 750 kB, błąd 1.5 MB (`angular.json`); nowe widoki ładowane leniwie (`loadComponent`).
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
