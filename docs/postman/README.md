# Kolekcja Postman — Mini IAM

| Plik | Zawartość |
|---|---|
| `mini-iam.postman_collection.json` | kolekcja (Postman v2.1): wszystkie endpointy API używane w projekcie |
| `mini-iam-local.postman_environment.json` | środowisko lokalne (adresy, klienci, zmienne robocze); **bez sekretów** |

## Import

1. Postman → **Import** → oba pliki z tego katalogu.
2. W prawym górnym rogu wybierz środowisko **Mini IAM - local**.
3. Uzupełnij w środowisku zmienną `serviceClientSecret` wartością `MINI_IAM_SERVICE_CLIENT_SECRET` z `.env`
   (potrzebna tylko dla tokena serwisowego). Nie zapisuj sekretu z powrotem do pliku w repozytorium —
   zmienna ma typ *secret*, a jej wartość trzymaj jako *Current value*.
4. Uruchom środowisko: `docker compose up -d --build`.

## Uwierzytelnienie

- **Token użytkownika** (domyślny dla kolekcji): OAuth 2.0 **Authorization Code + PKCE (S256)**, klient publiczny
  `mini-iam-frontend`, callback `http://localhost:4200/dashboard`. Kolekcja → zakładka **Authorization** →
  **Get New Access Token** → zaloguj się w oknie Keycloak (np. `admin` / `MINI_IAM_ADMIN_PASSWORD`) → **Use Token**.
  Okno logowania jest wbudowane w Postmana i przechwytuje przekierowanie, więc frontend nie musi działać.
  Access token żyje 5 minut — po wygaśnięciu pobierz nowy (albo włącz w Postmanie automatyczne odświeżanie).
- **Token serwisowy** (klient `mini-iam-service`, tylko rola `SERVICE`): request *00 Tokeny → Token serwisowy
  (client_credentials)* zapisuje go w `serviceToken`.
  Ma tylko rolę techniczną `SERVICE`, dlatego służy w folderze *05* do pokazania odmów dostępu.
- Aby sprawdzić inne role, zaloguj się na konto z rolą `MANAGER`, `USER` lub `AUDITOR` (np. `AUDITOR` widzi
  tylko folder *04 audit-service*; konto tworzy
  *Utworzenie użytkownika*, rolę nadaje *Przypisanie roli*; przy pierwszym logowaniu Keycloak wymusi zmianę
  hasła tymczasowego).

## Foldery i kolejność

| Folder | Co zawiera |
|---|---|
| 00 Tokeny | token serwisowy (Client Credentials) |
| 01 Health | `/actuator/health` trzech serwisów (bez tokena) |
| 02 user-service | własny profil, lista, tworzenie, szczegóły, edycja, aktywacja/dezaktywacja |
| 03 auth-service | `/api/auth/me`, lista ról, role użytkownika, przypisanie i odebranie roli |
| 04 audit-service | lista zdarzeń audytu z filtrami, historia użytkownika z folderu 02, szczegóły zdarzenia |
| 05 Bezpieczeństwo (negatywne) | oczekiwane 401 / 403 / 400 (także dla audytu i endpointu wewnętrznego) |
| 06 Sprzątanie | usunięcie użytkownika utworzonego w folderze 02 i sprawdzenie wpisu `USER_DELETED` |

Kolekcję można uruchomić w całości (**Run collection**) jako ADMIN: requesty mają testy oczekiwanych statusów
i przekazują sobie identyfikatory przez zmienne środowiska:

| Zmienna | Ustawia | Używa |
|---|---|---|
| `userId` | *Utworzenie użytkownika* (lokalne `id` profilu) | user-service `/api/users/{id}` |
| `keycloakId` | *Utworzenie użytkownika* (`keycloakId`) | auth-service `/api/users/{keycloakId}/roles` |
| `myKeycloakId` | *Mój profil* | *Moje role*, *Odebranie sobie ADMIN* |
| `auditEventId` | *Lista zdarzeń* (pierwsze zdarzenie na liście) | *Szczegóły zdarzenia* |
| `newUsername` | skrypt kolekcji (unikalna nazwa), czyszczona po *Usunięciu użytkownika* | *Utworzenie użytkownika* |
| `roleName` | środowisko (domyślnie `MANAGER`) | *Przypisanie roli*, *Odebranie roli* |

Uwaga: *Edycja mojego profilu* nadpisuje e-mail, imię i nazwisko zalogowanego konta wartościami z body
(domyślnie dane konta `admin` z realm importu) — przed uruchomieniem na innym koncie popraw body.

Każde żądanie dostaje nagłówek `X-Correlation-Id` (`postman-<timestamp>`, skrypt kolekcji), który wraca
w odpowiedzi — po nim można znaleźć żądanie w logach serwisów i w Kibanie (`correlationId : "postman-..."`),
a dla operacji administracyjnych także wpis w audycie (pole `correlationId` w *04 audit-service*).

## Bezpieczeństwo

- Nie eksportuj środowiska ani kolekcji z *Current values* — `serviceToken` (access token) i
  `serviceClientSecret` trafiłyby do pliku. Pliki w repozytorium mają te wartości puste.
- Hasło tymczasowe w *Utworzenie użytkownika* (`Temp-Pass-123`) spełnia politykę haseł realmu (12–128 znaków,
  różne od nazwy użytkownika i e-maila). Krótsze → 400 `VALIDATION_FAILED`, równe nazwie użytkownika → 400
  `INVALID_USER_DATA`.
- Token użytkownika z Postmana można przekazać do testu end-to-end: `ADMIN_TOKEN=<token> ./scripts/e2e-smoke.sh`
  (README, sekcja „Testy”).

## Utrzymanie

Kolekcję aktualizujemy razem z API: nowy lub zmieniony endpoint = nowy lub zmieniony request (z opisem ról
i oczekiwanym statusem w teście). Endpoint wewnętrzny `POST /internal/api/audit-events` jest w kolekcji tylko
jako test odmowy dla tokena użytkownika — zapisują przez niego wyłącznie serwisy.
