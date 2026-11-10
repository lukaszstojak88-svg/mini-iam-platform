# Mini IAM Platform --- plan implementacji

> Dokument prowadzący Claude Code przez kolejne etapy realizacji
> projektu.\
> Szczegółowe wymagania funkcjonalne, architektura, stack i kryteria
> jakości znajdują się w `project-spec.md`.\
> Zasady pracy Claude Code znajdują się w `CLAUDE.md` w katalogu głównym.

## Zasady realizacji

-   Realizuj etapy w podanej kolejności.
-   Nie rozpoczynaj kolejnego etapu bez zakończenia i akceptacji
    poprzedniego.
-   Po każdym etapie projekt powinien się budować, a odpowiednie testy
    powinny przechodzić.
-   Jeśli zależność od wcześniejszego etapu nie jest gotowa, zgłoś
    blokadę zamiast obchodzić wymagania.
-   Nie rozszerzaj zakresu MVP bez wyraźnej zgody użytkownika.

## Plan realizacji

Implementować iteracyjnie. Po każdym etapie projekt powinien się budować
i mieć działające testy.

### Etap 1 --- Repozytorium i infrastruktura

-   Maven parent POM i moduły trzech serwisów.
-   Docker Compose z PostgreSQL i Keycloak.
-   Realm `mini-iam`, klient Angular, klient techniczny i role.
-   `.env.example`, README i healthchecki.
-   Stos ELK w profilu Compose `elk` (Elasticsearch, Logstash, Kibana,
    Filebeat) z konfiguracją w `infrastructure/elk/`.
-   Logowanie JSON/ECS na stdout i filtr `X-Correlation-Id` w każdym
    serwisie.

**Gotowe, gdy:** infrastruktura uruchamia się lokalnie, realm importuje
się poprawnie, każdy serwis startuje, a jego logi (z `correlationId`) są
widoczne w Kibanie.

### Etap 2 --- Bezpieczeństwo

-   Resource Server w każdym backendzie.
-   Walidacja issuer/audience.
-   Converter ról Keycloak.
-   Reguły 401/403.
-   Angular OIDC login z PKCE.

**Gotowe, gdy:** użytkownik loguje się przez Keycloak, a backend
rozróżnia role.

### Etap 3 --- User Service

-   Model i Liquibase.
-   CRUD i walidacja.
-   Keycloak Admin Client.
-   Obsługa częściowych awarii.
-   Testy jednostkowe i integracyjne.

**Gotowe, gdy:** administrator może utworzyć i zarządzać użytkownikiem w
Keycloak oraz lokalnym profilu.

### Etap 4 --- Auth Service

-   Odczyt i przypisywanie Realm Roles.
-   Reguły biznesowe.
-   OpenFeign, w tym propagacja `X-Correlation-Id` przez interceptor.
-   Testy z Keycloak Testcontainer.

**Gotowe, gdy:** administrator może przypisywać i odbierać role, a
autoryzacja API działa zgodnie z tokenem.

### Etap 5 --- Audit Service

-   Model zdarzeń i migracje.
-   Endpoint wewnętrzny do zapisu (z zapisem `correlation_id` z
    nagłówka `X-Correlation-Id`).
-   Endpointy odczytu.
-   Integracja Feign z pozostałymi serwisami.
-   Testy.

**Gotowe, gdy:** operacje administracyjne są rejestrowane i można je
przeglądać.

### Etap 6 --- Angular UI

-   Dashboard.
-   Users.
-   User Details.
-   Roles.
-   Audit Logs.
-   Guards, interceptor, formularze i obsługa błędów.

**Gotowe, gdy:** główny scenariusz biznesowy da się przejść z poziomu
przeglądarki.

### Etap 7 --- Dokumentacja i jakość

-   Pełne testy.
-   README.
-   Diagram architektury.
-   Przykładowe requesty (np. plik kolekcji HTTP).
-   Weryfikacja konfiguracji bezpieczeństwa i sekretów, w tym braku
    tokenów i sekretów w logach.
-   Opis w README: śledzenie żądania po `correlationId` w Kibanie.

------------------------------------------------------------------------

## Definicja zakończenia etapu

Etap można uznać za zakończony dopiero wtedy, gdy: 1. wszystkie
wymienione w nim elementy zostały zaimplementowane; 2. odpowiednie testy
i build zostały uruchomione; 3. wyniki testów oraz ewentualne
ograniczenia zostały przedstawione użytkownikowi; 4. dokumentacja
została zaktualizowana, jeśli zmienił się sposób uruchamiania lub
używania systemu; 5. użytkownik zaakceptował zakończenie etapu.
