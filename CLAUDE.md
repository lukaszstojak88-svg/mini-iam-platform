# Mini IAM Platform — instrukcje dla Claude Code

Komunikacja i dokumentacja po polsku; kod, nazwy klas, metod i endpointów po angielsku.

## Dokumentacja (źródła prawdy)

- [`docs/project-spec.md`](docs/project-spec.md) — wymagania, architektura, stack, kryteria jakości. **Nadrzędna specyfikacja.**
- [`docs/implementation-plan.md`](docs/implementation-plan.md) — etapy i kolejność realizacji, definicja zakończenia etapu.
- [`docs/gui.jpg`](docs/gui.jpg) — wzorzec wyglądu UI (tylko styl i układ, patrz spec §10.3).

### Co czytać przy którym etapie

| Etap | Rozdziały `project-spec.md` |
|------|-----------------------------|
| 1 — Repozytorium i infrastruktura | §3, §4, §5.1, §8.4, §9, §13, §14, §16 |
| 2 — Bezpieczeństwo | §2, §5, §10.2, §11 |
| 3 — User Service | §6.1, §7, §8.1, §9, §12, §15 |
| 4 — Auth Service | §2, §6.2, §7, §8.3, §12, §15, §16.2 |
| 5 — Audit Service | §6.3, §7, §8.2, §9, §12, §15, §16.2 |
| 6 — Angular UI | §2.1, §5.2, §10 |
| 7 — Dokumentacja i jakość | §11, §12, §14, §16 |

## Aktualny etap

**Etap 1 — Repozytorium i infrastruktura** (jeszcze nie rozpoczęty).
Najpierw sprawdź repozytorium, zaproponuj strukturę Maven multi-module, wersje kompatybilnych zależności
i plan Docker Compose. Implementuj wyłącznie ten etap. Nie przechodź do logiki biznesowej ani Angular UI
bez osobnej decyzji użytkownika.

## Komendy

_Uzupełnić w Etapie 1 (build, testy, uruchomienie infrastruktury)._

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
- UI: z makiety bierzemy tylko wygląd; funkcje spoza MVP (tworzenie ról, import/eksport, Pending Reviews,
  Settings, Billing, globalna wyszukiwarka, powiadomienia) pomijamy.

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
