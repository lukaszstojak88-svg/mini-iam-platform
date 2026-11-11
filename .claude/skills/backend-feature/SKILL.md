---
name: backend-feature
description: Konwencje dodawania endpointu lub funkcji w user-service, auth-service albo audit-service (Etapy 3-5) - pakiety, DTO, autoryzacja @PreAuthorize wg macierzy ról, ProblemDetail, Liquibase, audyt przez Feign, logowanie, testy oraz synchronizacja kodu skopiowanego między serwisami. Używaj przy każdej zmianie kodu backendu ("dodaj endpoint", "zaimplementuj CRUD", "add endpoint", "new migration").
---

# Nowa funkcja w backendzie

Źródło prawdy: `docs/project-spec.md`. Ten skill zbiera konwencje przyjęte w projekcie; nie powiela wymagań.

## 1. Struktura (spec §13.1)

Pakiet bazowy `pl.stojak.miniiam.<user|auth|audit>`:

| Pakiet | Zawartość |
|---|---|
| `controller` | cienkie kontrolery: walidacja wejścia (`@Valid`), wywołanie serwisu, kod HTTP |
| `dto/request`, `dto/response` | `record`; nigdy nie zwracaj encji JPA |
| `domain` | encje, enumy domenowe (np. `AppRole`) |
| `service` | logika biznesowa i reguły (np. ostatni ADMIN) |
| `repository` | Spring Data JPA (tylko user-/audit-service) |
| `integration/keycloak`, `integration/audit` | klienci Admin API / Feign |
| `exception` | wyjątki domenowe + `@RestControllerAdvice` |
| `mapper` | ręczne mapowanie encja <-> DTO |

- Wstrzykiwanie przez konstruktor, bez `@Autowired` na polach.
- Brak logiki biznesowej w kontrolerach. Bez zbędnych abstrakcji.
- Komentarze w kodzie i konfiguracji po angielsku.

## 2. Autoryzacja (spec §2, §2.1)

- **Każdy** endpoint ma jawną regułę `@PreAuthorize` zgodną z macierzą §2.1, np.:
  - `@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")` - lista i tworzenie użytkowników,
  - `@PreAuthorize("hasRole('ADMIN')")` - status, usuwanie, role,
  - `@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")` - odczyt audytu,
  - `/me` - `isAuthenticated()` + tylko dane z `jwt.sub`.
- `SecurityConfig` już wymusza: `/api/**` uwierzytelnione, `/internal/api/**` = `ROLE_SERVICE`, reszta `denyAll`.
  Endpoint wewnętrzny umieszczaj pod `/internal/api/...`.
- Identyfikator aktora = `jwt.getSubject()` (keycloakId). W auth-service `{userId}` w ścieżce = keycloakId.
- Reguły domenowe (ostatni aktywny ADMIN, nie zmieniasz własnych ról, `USER`/`SERVICE` nieprzypisywalne)
  egzekwuje serwis, nie tylko UI.

## 3. Błędy (spec §15, §12.4)

- `@RestControllerAdvice` zwraca `ProblemDetail` (`application/problem+json`); `spring.mvc.problemdetails.enabled=true` już jest.
- Kody: 400 walidacja, 404 brak zasobu, 409 konflikt/duplikat, 502/503 problem z Keycloak lub innym serwisem.
- Nigdy nie zwracaj stack trace, komunikatów Keycloak ani szczegółów infrastruktury.
- 401/403 obsługują już `ProblemDetailAuthenticationEntryPoint` / `ProblemDetailAccessDeniedHandler`.

## 4. Liquibase (spec §9)

- Nowa migracja = nowy plik `src/main/resources/db/changelog/changes/NNN-opis.yaml` + `include` w
  `db.changelog-master.yaml` (dziś master ma `databaseChangeLog: []` - zamień na listę `include`).
- Każdy changeset ma `rollback`, jeśli to sensowne. Nigdy nie edytuj zastosowanych changelogów.
- Tabele tylko we własnym schemacie (`users` / `audit`); `ddl-auto=validate` - encje muszą zgadzać się ze schematem.
- Typy wg spec §8: `UUID`, `TIMESTAMPTZ`, `JSONB` (audit `details`).

## 5. Komunikacja i audyt (spec §6.3, §7)

- Wywołania między serwisami: OpenFeign (BOM Spring Cloud jest w parent `pom.xml`), token Client Credentials
  klienta `mini-iam-admin`, nagłówek `X-Correlation-Id` z MDC (`CorrelationIdFilter.MDC_KEY`).
- Każdy klient Feign: connect/read timeout, mapowanie błędów na kontrolowane wyjątki, retry tylko dla
  idempotentnych operacji i ze skończoną liczbą prób.
- Nie wołaj innych serwisów wewnątrz długiej transakcji DB.
- Niedostępność audit-service w MVP może zablokować operację - zachowanie ma być jawne (błąd 503 + log).
- Częściowa awaria Keycloak <-> lokalna baza (spec §6.1): kompensacja albo jednoznaczny błąd + log diagnostyczny.

## 6. Logowanie (spec §16.3)

- Loguj: wywołania Keycloak/Feign (cel, status, czas), błędy domenowe, kompensacje. Aktor = keycloakId.
- Nie loguj: tokenów (ani fragmentów), nagłówka `Authorization`, haseł, secretów, pełnych body.

## 7. Kod skopiowany w 3 serwisach

Pakiet `security/*` i `config/CorrelationIdFilter` istnieją osobno w każdym serwisie (decyzja: brak modułu
współdzielonego). Zmiana w jednym = ta sama zmiana w pozostałych dwóch, razem z testami. Kontrola rozbieżności:

```bash
for s in auth audit; do
  for f in $(cd user-service/src && find . -path '*security*' -o -path '*CorrelationIdFilter*' | grep '\.java$'); do
    g=$(echo "$f" | sed "s#/user/#/$s/#")
    diff <(sed "s/miniiam\.user/miniiam.X/g" "user-service/src/$f") <(sed "s/miniiam\.$s/miniiam.X/g" "$s-service/src/$g") >/dev/null \
      || echo "DIFF: $s-service/src/$g"
  done
done
```

## 8. Testy (spec §12)

- Unit: logika domenowa, reguły (ostatni ADMIN, status), mapowanie DTO, mapowanie błędów Keycloak.
  Mockito tylko do izolowania zależności zewnętrznych.
- `@WebMvcTest(Controller.class)` + `@Import({SecurityConfig.class, CorrelationIdFilter.class, ...})` +
  `jwt().authorities(...)`: dla każdego endpointu 401 (anonim), 403 (rola bez uprawnień), sukces dla właściwej roli,
  400 dla walidacji. Wzorce: `*/security/SecurityRulesTest.java`, `auth-service/.../controller/AuthControllerTest.java`.
- IT (`*IT.java`, failsafe): Testcontainers PostgreSQL (`@ServiceConnection`, init script tworzący schemat) i Keycloak
  (`auth-service/src/test/java/pl/stojak/miniiam/auth/KeycloakJwtValidationIT.java` jako wzorzec - realm import z
  `infrastructure/` przez `testResources`).
- Uruchom: `./mvnw verify` (na tej maszynie z `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`).

## 9. Kolekcja Postman

Nowy lub zmieniony endpoint = aktualizacja `docs/postman/mini-iam.postman_collection.json` (request w folderze
serwisu, opis ról, test oczekiwanego statusu, ewentualnie zmienna środowiska). Bez sekretów w plikach
środowiska; instrukcja w `docs/postman/README.md`.
