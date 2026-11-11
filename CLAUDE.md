# Mini IAM Platform — instrukcje dla Claude Code

Komunikacja i dokumentacja po polsku; kod, nazwy klas, metod i endpointów po angielsku.

## Dokumentacja (źródła prawdy)

- [`docs/project-spec.md`](docs/project-spec.md) — wymagania, architektura, stack, kryteria jakości. **Nadrzędna specyfikacja.**
- [`docs/implementation-plan.md`](docs/implementation-plan.md) — etapy i kolejność realizacji, definicja zakończenia etapu.
- [`docs/frontend.md`](docs/frontend.md) — ustalenia, funkcjonalności i ograniczenia frontendu. **Obowiązkowa lektura przy każdej pracy nad Angularem**; nowe ustalenia frontendowe dopisuj tam.
- [`docs/gui.jpg`](docs/gui.jpg) — wzorzec wyglądu UI (tylko styl i układ, patrz spec §10.3).

### Co czytać przy którym etapie

| Etap | Rozdziały `project-spec.md` |
|------|-----------------------------|
| 1 — Repozytorium i infrastruktura | §3, §4, §5.1, §8.4, §9, §13, §14, §16 |
| 2 — Bezpieczeństwo | §2, §5, §10.2, §11 |
| 3 — User Service | §6.1, §7, §8.1, §9, §12, §15 |
| 4 — Auth Service | §2, §6.2, §7, §8.3, §12, §15, §16.2 |
| 5 — Audit Service | §6.3, §7, §8.2, §9, §12, §15, §16.2 |
| 6 — Angular UI | §2.1, §5.2, §10 + cały `docs/frontend.md` |
| 7 — Dokumentacja i jakość | §11, §12, §14, §16 |

## Aktualny etap

Etapy 1–4 zrealizowane i zaakceptowane.
Następny: **Etap 5 — Audit Service** (nie zaczynać bez decyzji użytkownika).

## Komendy

```bash
cp .env.example .env                          # potem ustaw własne sekrety
./mvnw test                                   # testy jednostkowe
./mvnw verify                                 # + testy integracyjne Testcontainers (wymaga Dockera)
docker compose up -d --build                  # PostgreSQL, Keycloak, 3 serwisy (z katalogu głównego)
docker compose --profile elk up -d --build    # + Elasticsearch, Logstash, Kibana, Filebeat
docker compose --profile elk down -v          # czysty reset (ponowny realm import)
./scripts/start-frontend.sh [--with-backend]          # Angular dev server, http://localhost:4200
cd frontend/mini-iam-ui && npx ng test --watch=false   # testy Angular (Vitest)
cd frontend/mini-iam-ui && npx ng build                # build produkcyjny
```

Na tej maszynie (Windows, proxy TLS) Maven wymaga `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`,
a npm/npx `NODE_OPTIONS=--use-system-ca`.

## Podjęte decyzje (uzupełniają spec)

- Serwisy: `user-service`, `auth-service` (dawniej `access-service`; tylko role/autoryzacja, bez własnej bazy,
  uwierzytelnianie wyłącznie w Keycloak), `audit-service`.
- Pakiet bazowy / groupId: `pl.stojak.miniiam` (np. `pl.stojak.miniiam.auth`).
- Serwis–serwis: Client Credentials + techniczna realm role `SERVICE`; `/internal/api/**` wymaga `ROLE_SERVICE`.
  `SERVICE` nie jest rolą aplikacyjną — niewidoczna w `GET /api/roles`, nieprzypisywalna przez API.
- `USER` nadaje Keycloak przez composite `default-roles-mini-iam`; to rola bazowa, nie można jej odebrać.
- Role zmienia tylko `ADMIN` (`ADMIN`, `MANAGER`, `AUDITOR`). `MANAGER` nie zmienia żadnych ról.
- `{userId}` w auth-service = `keycloakId`; user-service zwraca `keycloakId` w DTO profilu.
- Logi: JSON/ECS na stdout (structured logging Spring Boot) → Filebeat → Logstash → Elasticsearch → Kibana;
  stos ELK w profilu Compose `elk`. Śledzenie żądań wyłącznie przez `X-Correlation-Id` (bez osobnego
  tracking ID), propagowany przez Feign i zapisywany w audycie (spec §16).
- Wersje: Spring Boot 3.5.x (+ Spring Cloud 2025.0.x), Java 21, Keycloak 26.8, PostgreSQL 17, Elastic 9.5.
- Filtr `X-Correlation-Id` kopiowany osobno do każdego serwisu (brak modułu współdzielonego).
- Konto `admin` (ADMIN) tworzone przez realm import, hasło z `.env` (`MINI_IAM_ADMIN_PASSWORD`).
- Klient `mini-iam-admin` ma scope `kc-admin-api` (wymóg Admin API Keycloak ≥ 26.8.2) i `fullScopeAllowed=false`.
- JWT: `issuer-uri` (walidacja `iss` = `http://localhost:8080/realms/mini-iam`) + `jwk-set-uri` (w Dockerze
  `http://keycloak:8080/...`) + `audiences: mini-iam-api`. Pakiet `security` (converter ról, `SecurityConfig`,
  401/403 jako ProblemDetail, CORS) kopiowany do każdego serwisu. Reguły: `/internal/api/**` → `ROLE_SERVICE`,
  `/api/**` → authenticated + `@PreAuthorize` per endpoint, reszta `denyAll`.
- `GET /api/auth/me` (auth-service) zwraca dane z JWT; role tylko aplikacyjne (bez `SERVICE`).
- Frontend: Angular 22 + Material 22 + keycloak-angular 22 (`check-sso`, PKCE S256, Bearer tylko dla
  `http://localhost:808[1-3]/api/**`), testy Vitest. Konfiguracja w `src/environments/environment.ts`.
- user-service ↔ Keycloak: własny `KeycloakAdminClient` (Spring RestClient + Spring Security OAuth2 Client,
  client_credentials `mini-iam-admin`, timeouty 2 s/5 s, bez retry). Kolejność: Keycloak → baza, przy błędzie
  zapisu lokalnego kompensacja w Keycloak; `enabled` — źródło prawdy Keycloak (wyrównywane przy `GET /{id}`).
- Tworzenie konta z `temporaryPassword` (tylko do Keycloak, `temporary=true`); profil just-in-time przy
  `GET /api/users/me`; `PUT /api/users/me` zmienia e-mail, imię, nazwisko; MANAGER nie edytuje kont ADMIN.
- Błędy user-service: ProblemDetail + `code` (`ErrorCode`); lista: sort tylko po whitelist, `size` ≤ 100.
- Audyt operacji user-service zostanie dodany w Etapie 5 (integracja Feign z audit-service).
- auth-service: role tylko w Keycloak (bez bazy i cache), własny `KeycloakAdminClient` (wzorzec z user-service,
  ten sam klient `mini-iam-admin`; realm bez zmian). `GET /api/roles` — ADMIN, MANAGER (`{name, description,
  assignable}`); `GET /api/users/{userId}/roles` — ADMIN lub właściciel; `POST` (200 + aktualne role) i `DELETE`
  (204) — tylko ADMIN. Przypisywalne tylko `ADMIN`/`MANAGER`/`AUDITOR` (dokładne nazwy), inne → 400
  `ROLE_NOT_ASSIGNABLE`; własne role → 403 `SELF_ROLE_CHANGE`; konta serwisowe → 403 `SERVICE_ACCOUNT_PROTECTED`;
  duplikat → 409; odebranie nieprzypisanej (bezpośrednio) → 404 `ROLE_NOT_ASSIGNED`; ostatni aktywny ADMIN → 409
  `LAST_ADMIN`. Role przypisywane wyłącznie bezpośrednio (bez grup).
- Feign (auth-service, Etap 4): `AuditClient` + `AuditEventPublisher` (token Client Credentials przez wspólny
  `OAuth2AuthorizedClientManager`, `CorrelationIdRequestInterceptor`, timeouty 2 s/5 s, `Retryer.NEVER_RETRY`,
  `AuditErrorDecoder` → 503 `AUDIT_UNAVAILABLE` / 502 `AUDIT_INTEGRATION_ERROR`). `@EnableFeignClients` w osobnej
  konfiguracji (nie w klasie aplikacji). Wywołania audytu ról (`ROLE_ASSIGNED`/`ROLE_REMOVED`) podpinane w Etapie 5.
- Kolekcja Postman `docs/postman/` (kolekcja v2.1 + środowisko lokalne bez sekretów, instrukcja w `README.md`):
  przy każdym nowym lub zmienionym endpoincie aktualizuj odpowiedni request (opis ról, test oczekiwanego statusu).
- Komentarze w kodzie i plikach konfiguracyjnych po angielsku; dokumentacja Markdown po polsku.
- UI: z makiety bierzemy tylko wygląd; funkcje spoza MVP (tworzenie ról, import/eksport, Pending Reviews,
  Settings, Billing, globalna wyszukiwarka, powiadomienia) pomijamy.

## Skille projektu (`.claude/skills/`)

| Skill | Kiedy |
|---|---|
| `stage-workflow` | rozpoczęcie, realizacja i zamknięcie etapu |
| `backend-feature` | każdy nowy endpoint/funkcja w serwisach (autoryzacja, błędy, Liquibase, Feign, testy, kod skopiowany ×3) |
| `keycloak-realm` | zmiany w realm imporcie, problemy z tokenami lub Admin API |
| `verify-stack` | uruchomienie i weryfikacja end-to-end środowiska |
| `commit` | każdy commit |

Agent `change-reviewer` (`.claude/agents/change-reviewer.md`) — recenzja zmian (bugi, security, zgodność ze spec
i wzorcami); tylko czyta i raportuje. Uruchamiać po zakończeniu etapu lub istotnej zmiany, przed commitem.

## Zasady pracy

1. Najpierw przeanalizuj istniejące repozytorium. Nie nadpisuj istniejącego kodu ani konfiguracji bez sprawdzenia ich zawartości.
2. Implementuj etapami zgodnie z `docs/implementation-plan.md`. Nie generuj całego systemu w jednym kroku.
3. Przed implementacją etapu przedstaw krótki plan zmian i listę plików/modułów, których dotknie; zaczekaj na akceptację, chyba że użytkownik wyraźnie zlecił implementację etapu.
4. Zachowuj granice mikroserwisów; nie współdziel encji ani repozytoriów.
5. Nie dodawaj technologii spoza stacku bez uzasadnienia i akceptacji.
6. Nie implementuj funkcjonalności spoza MVP, chyba że użytkownik wyraźnie o to poprosi.
7. Nie zostawiaj pozornie działających stubów, pustych metod ani `TODO` w funkcjonalnościach uznanych za ukończone.
8. Po każdej istotnej zmianie uruchom odpowiednie testy i build. Jeśli nie możesz ich uruchomić, jasno to zgłoś.
9. Nie wyłączaj testów ani zabezpieczeń po to, aby build przeszedł.
10. Sekrety i hasła wyłącznie z konfiguracji środowiskowej (`.env` poza Git); nigdy na stałe w kodzie, logach ani obrazach Docker.
11. Nie używaj `ddl-auto=create`/`update` — schemat tylko przez Liquibase; nie edytuj zastosowanych changelogów.
12. W razie niejednoznaczności wybierz najprostsze rozwiązanie zgodne ze spec i opisz przyjęte założenie.
13. Jeżeli wymaganie jest sprzeczne z bezpieczeństwem lub poprawnością architektury, zatrzymaj się i wyjaśnij problem przed implementacją.
14. Po zakończeniu etapu podaj: zakres zmian, najważniejsze decyzje, komendy uruchomieniowe, wyniki testów i znane ograniczenia.
15. Aktualizuj README i dokumentację (także sekcje „Aktualny etap”, „Komendy” i „Podjęte decyzje” w tym pliku), jeśli zmiana wpływa na sposób uruchamiania lub używania systemu.
16. Commity wyłącznie wg skilla [`.claude/skills/commit/SKILL.md`](.claude/skills/commit/SKILL.md): zwięzły opis, bez informacji o Claude.
