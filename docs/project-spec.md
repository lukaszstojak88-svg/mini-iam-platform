# Mini IAM Platform --- Specyfikacja projektu

> Dokument wymagań dla Claude Code. Stanowi źródło prawdy dla
> architektury, zakresu funkcjonalnego, ograniczeń i kryteriów
> akceptacji projektu.

-   **Nazwa:** Mini IAM Platform
-   **Wersja specyfikacji:** 1.0
-   **Status:** MVP --- do implementacji
-   **Język dokumentacji i komunikatów:** polski (nazwy klas, metod,
    endpointów i kodu po angielsku)
-   **Docelowy charakter:** edukacyjny projekt portfolio prezentujący
    integrację Spring Boot + Keycloak, autoryzację opartą o role oraz
    komunikację mikroserwisów.

------------------------------------------------------------------------

## 1. Cel i wizja produktu

Mini IAM Platform to niewielka aplikacja do zarządzania użytkownikami,
rolami i historią operacji administracyjnych. Ma demonstrować praktyczne
zastosowanie architektury mikroserwisowej oraz integracji z Keycloak.

Administrator może zarządzać kontami i przypisywać role. Użytkownik
loguje się przez Keycloak, a backendy weryfikują token JWT i egzekwują
uprawnienia. Operacje administracyjne są rejestrowane w niezależnym
serwisie audytowym.

Projekt ma być możliwy do uruchomienia lokalnie za pomocą Docker Compose
oraz posiadać testy jednostkowe i integracyjne.

### 1.1 Główne cele

1.  Uwierzytelnianie użytkowników przez Keycloak (OpenID Connect).
2.  Weryfikacja JWT w każdym mikroserwisie udostępniającym API.
3.  Zarządzanie użytkownikami w Keycloak przez Admin REST API.
4.  Zarządzanie rolami i przypisywanie ich użytkownikom.
5.  Rozdzielenie odpowiedzialności na małe, niezależnie uruchamiane
    serwisy.
6.  Komunikacja synchroniczna między serwisami z użyciem OpenFeign.
7.  Audytowanie operacji administracyjnych.
8.  Automatyczne tworzenie i aktualizowanie schematów baz danych przez
    Liquibase.
9.  Testowanie z PostgreSQL i Keycloak uruchamianymi w Testcontainers.
10. Centralne, strukturalne logowanie w stosie ELK (Elasticsearch,
    Logstash, Kibana) oraz śledzenie przebiegu żądań między serwisami
    przez nagłówek `X-Correlation-Id`.

### 1.2 Poza zakresem MVP

-   rejestracja publiczna użytkowników;
-   odzyskiwanie i resetowanie hasła przez własny mechanizm aplikacji;
-   MFA i federacja z zewnętrznymi dostawcami tożsamości;
-   multi-tenancy i wiele organizacji;
-   rozbudowany silnik polityk ABAC/ReBAC;
-   event broker (Kafka/RabbitMQ);
-   Kubernetes, service mesh i API Gateway;
-   wysyłka prawdziwych wiadomości e-mail;
-   rozproszony workflow/Saga;
-   panel do edycji konfiguracji samego Keycloak.

------------------------------------------------------------------------

## 2. Użytkownicy systemu i role

System rozróżnia następujące role aplikacyjne. W MVP są to **Realm
Roles** w realmie Keycloak `mini-iam`.

  ---------------------------------------------------------------------
  Rola                               Uprawnienia
  ---------------------------------- ----------------------------------
  `ADMIN`                            Pełne zarządzanie użytkownikami,
                                     rolami i odczyt audytu

  `MANAGER`                          Odczyt użytkowników i
                                     tworzenie/edycja użytkowników; bez
                                     zarządzania rolami i audytem

  `USER`                             Odczyt i edycja ograniczonych
                                     danych własnego profilu

  `AUDITOR`                          Tylko odczyt zdarzeń audytowych

  ---------------------------------------------------------------------

Zasady:

-   Nowy użytkownik otrzymuje domyślnie rolę `USER`. `USER` jest rolą
    bazową: nadaje ją Keycloak przez default roles realmu (patrz 5.1) i
    nie można jej odebrać przez API.
-   Tylko `ADMIN` może przypisywać i odbierać role (`ADMIN`, `MANAGER`,
    `AUDITOR`). `MANAGER` nie może zmieniać żadnych ról.
-   Oprócz ról aplikacyjnych istnieje techniczna realm role `SERVICE`,
    nadawana wyłącznie kontom serwisowym (service accounts) backendów.
    Nie jest rolą aplikacyjną: nie jest zwracana przez `GET /api/roles`
    i nie można jej przypisać ani odebrać przez API.
-   Nie wolno dopuścić do usunięcia lub dezaktywacji ostatniego
    aktywnego użytkownika z rolą `ADMIN`.
-   Użytkownik nie może zmienić własnych ról.
-   Użytkownik może odczytywać wyłącznie własny profil, chyba że ma
    uprawnienia administracyjne.
-   Nazwy ról są stałymi domenowymi, a nie dowolnymi wartościami
    tworzonymi w UI.
-   W MVP nie tworzymy własnego katalogu ról niezależnego od Keycloak.
    Keycloak jest źródłem prawdy dla ról używanych do autoryzacji.

### 2.1 Uprawnienia do endpointów

  Obszar                             ADMIN   MANAGER       USER        AUDITOR
  --------------------------------- ------- --------- --------------- ---------
  Lista użytkowników                   ✓        ✓           ---          ---
  Szczegóły dowolnego użytkownika      ✓        ✓           ---          ---
  Własny profil                        ✓        ✓            ✓            ✓
  Tworzenie użytkownika                ✓        ✓           ---          ---
  Edycja danych użytkownika            ✓        ✓      własny profil     ---
  Dezaktywacja/aktywacja               ✓       ---          ---          ---
  Usunięcie użytkownika                ✓       ---          ---          ---
  Odczyt ról użytkownika               ✓       ---      własne role      ---
  Przypisanie/odebranie roli           ✓       ---          ---          ---
  Odczyt audytu                        ✓       ---          ---           ✓

Każdy endpoint musi mieć jawnie określoną regułę autoryzacji. Nie należy
polegać wyłącznie na ukrywaniu elementów interfejsu Angular.

------------------------------------------------------------------------

## 3. Stack technologiczny

Wersje bibliotek i pluginów należy dobierać jako stabilne, kompatybilne
wydania w momencie rozpoczęcia implementacji. Nie należy bez potrzeby
przypinać przestarzałych wersji.

### Backend

-   Java 21
-   Spring Boot 3.x (linia kompatybilna z Java 21)
-   Spring Web
-   Spring Security
-   Spring OAuth2 Resource Server
-   Spring Data JPA
-   Bean Validation
-   Spring Cloud OpenFeign --- wersja zgodna z wybraną wersją Spring
    Boot
-   Keycloak Admin Client lub kontrolowany klient REST do Keycloak Admin
    REST API
-   Maven, preferowany Maven Wrapper
-   Liquibase
-   PostgreSQL
-   Structured logging Spring Boot (format ECS, JSON na stdout) --- bez
    dodatkowych bibliotek logujących

### Frontend

-   Angular i TypeScript --- stabilna wersja zgodna z aktualnym
    ekosystemem
-   Angular Material
-   `keycloak-js` lub kompatybilna biblioteka integracyjna Angular
-   RxJS
-   Angular Router, Reactive Forms i HttpClient

### Testowanie

-   JUnit 5
-   Mockito
-   Spring Boot Test
-   MockMvc
-   Spring Security Test
-   Testcontainers (PostgreSQL i Keycloak)
-   testy integracyjne z rzeczywistym kontekstem Spring

### Uruchamianie i narzędzia

-   Docker
-   Docker Compose
-   PostgreSQL
-   Keycloak
-   Git
-   Maven Wrapper
-   Elasticsearch, Logstash, Kibana, Filebeat --- jedna, wspólna wersja
    stosu Elastic, stabilna w momencie implementacji (patrz §16)

------------------------------------------------------------------------

## 4. Architektura logiczna

MVP składa się z Angular SPA, Keycloak oraz trzech mikroserwisów
backendowych:

1.  `user-service`
2.  `auth-service`
3.  `audit-service`

`notification-service` jest opcjonalnym rozszerzeniem i nie powinien być
implementowany w pierwszym etapie.

### 4.1 Diagram koncepcyjny

``` text
                         +----------------------+
                         |     Angular SPA      |
                         |   OIDC Authorization |
                         |      Code + PKCE     |
                         +----------+-----------+
                                    |
                          Access Token (JWT)
                                    |
                  +-----------------+------------------+
                  |                 |                  |
          +-------v------+  +-------v-------+  +-------v------+
          | user-service |  | auth-service  |  | audit-service|
          |              |  |               |  |              |
          | User CRUD    |  | Role mgmt     |  | Audit query  |
          | Keycloak API |  | Keycloak API  |  | Audit storage|
          +------+-------+  +-------+-------+  +------+-------+
                 |                  |                 ^
                 |                  +-----------------+
                 |                    OpenFeign       |
                 +----------------------------------->|
                    OpenFeign / audit recording
                 |                                    |
          +------v-------+                      +-----v--------+
          | PostgreSQL   |                      | PostgreSQL   |
          | users schema |                      | audit schema |
          +--------------+                      +--------------+

          auth-service nie ma własnej bazy — role są w Keycloak.
          Wywołania OpenFeign: Client Credentials + rola SERVICE.

                         +----------------------+
                         |       Keycloak       |
                         | Realm: mini-iam      |
                         | Users, Roles, OIDC   |
                         +----------------------+
```

Wszystkie serwisy backendowe weryfikują JWT samodzielnie jako OAuth2
Resource Server. Keycloak jest dostawcą tożsamości i źródłem ról.

Przepływ logów (szczegóły w §16):

``` text
user-service ┐
auth-service ├─ stdout (JSON/ECS) ─> Filebeat ─> Logstash ─> Elasticsearch ─> Kibana
audit-service┘
```

### 4.2 Zasady architektoniczne

-   Każdy mikroserwis jest osobną aplikacją Spring Boot, ma własny
    `pom.xml` lub własny moduł Maven i może być uruchamiany niezależnie.
-   Każdy mikroserwis jest właścicielem swoich danych.
-   Serwisy nie odczytują bezpośrednio tabel należących do innych
    serwisów.
-   Komunikacja między serwisami odbywa się przez REST/OpenFeign.
-   Nie współdzielimy encji JPA ani repozytoriów pomiędzy serwisami.
-   Nie budujemy wspólnego modułu zawierającego logikę biznesową.
-   Wspólne biblioteki można dodać tylko dla stabilnych, technicznych
    elementów (np. standard błędów), gdy pojawi się rzeczywista
    potrzeba.
-   W MVP nie dodajemy API Gateway. Angular komunikuje się bezpośrednio
    z API serwisów.
-   Każdy serwis ma własną konfigurację bezpieczeństwa i własne migracje
    Liquibase.
-   Wywołania Feign muszą mieć określone timeouty i obsługę błędów.
-   Nie wykonujemy synchronicznych wywołań do innych serwisów wewnątrz
    długich transakcji bazodanowych.

### 4.3 Granice odpowiedzialności

#### `user-service`

Właściciel danych profilu aplikacyjnego i operacji CRUD użytkowników.
Integruje się z Keycloak Admin API w celu utworzenia, aktualizacji,
dezaktywacji i usunięcia konta.

#### `auth-service`

Odpowiada za odczyt ról użytkownika oraz przypisywanie i odbieranie
Realm Roles w Keycloak (autoryzacja). Nie realizuje uwierzytelniania ---
logowanie i wydawanie tokenów odbywa się wyłącznie w Keycloak. Nie ma
własnej bazy danych i nie utrzymuje niezależnego źródła prawdy o
rolach. Może posiadać lokalne tabele mapujące techniczne metadane
uprawnień, jeśli będą potrzebne w przyszłym rozszerzeniu, ale nie są
wymagane w MVP.

#### `audit-service`

Właściciel trwałego dziennika audytowego. Udostępnia endpointy do
przyjmowania zdarzeń i ich odczytu. Nie powinien udostępniać operacji
modyfikacji ani usuwania wpisów przez API użytkownika.

------------------------------------------------------------------------

## 5. Integracja z Keycloak

### 5.1 Konfiguracja środowiska

-   Realm: `mini-iam`
-   Client Angular: `mini-iam-frontend`
    -   public client;
    -   Standard Flow włączony;
    -   PKCE `S256`;
    -   bez client secret w aplikacji Angular.
-   Klient techniczny backendu: `mini-iam-admin`
    -   confidential client;
    -   Service Accounts Enabled;
    -   Client Credentials Flow;
    -   tylko minimalne wymagane uprawnienia administracyjne;
    -   service account ma realm role `SERVICE`, a token uzyskany przez
        Client Credentials jest używany w wywołaniach OpenFeign między
        serwisami.
-   Audience dla API: skonfigurowana tak, aby token dostępu był
    przeznaczony dla backendów.
-   Role aplikacyjne: `ADMIN`, `MANAGER`, `USER`, `AUDITOR`.
-   Rola techniczna: `SERVICE` (tylko service accounts).
-   `USER` jest dodana do composite `default-roles-mini-iam`, dzięki
    czemu Keycloak nadaje ją każdemu nowemu kontu.

Konfiguracja realm i klientów powinna być możliwa do odtworzenia z
plików konfiguracyjnych/realm importu przechowywanych w repozytorium.
Sekrety nie mogą znajdować się w repozytorium.

### 5.2 Logowanie Angular

-   Używać OpenID Connect Authorization Code Flow z PKCE.
-   Angular nie przechowuje haseł.
-   Token dostępu jest dołączany do żądań API przez interceptor.
-   Obsłużyć stan niezalogowany, wygaśnięcie sesji, wylogowanie i brak
    uprawnień.
-   Nie traktować tokena ID jako tokena autoryzacyjnego API.
-   Skonfigurować dozwolone redirect URI i web origins dla lokalnego
    środowiska.

### 5.3 Weryfikacja JWT w Spring Boot

Każdy serwis API działa jako OAuth2 Resource Server i weryfikuje podpis,
issuer oraz ważność tokena. Walidacja audience powinna być
skonfigurowana zgodnie z docelowym klientem API.

Role są pobierane z claimów Keycloak:

-   `realm_access.roles`
-   opcjonalnie `resource_access.<client-id>.roles`, jeśli w przyszłości
    zostaną użyte Client Roles.

Należy zaimplementować jawny converter mapujący role na Spring Security
authorities, np. `ROLE_ADMIN`. Nie zakładać, że domyślny converter
Spring automatycznie obsłuży strukturę ról Keycloak.

### 5.4 Operacje administracyjne

Backend korzysta z klienta technicznego i Client Credentials Flow. Dane
dostępowe klienta technicznego są przechowywane w zmiennych
środowiskowych lub lokalnym pliku `.env` wyłączonym z Git.

Nie wolno:

-   umieszczać client secret w Angular;
-   zapisywać haseł użytkowników w bazie aplikacji;
-   modyfikować bezpośrednio bazy Keycloak;
-   przekazywać tokena administratora frontendowego jako substytutu
    klienta technicznego;
-   przyznawać klientowi technicznemu pełnych uprawnień administratora
    bez uzasadnienia.

Należy obsłużyć odpowiedzi i błędy Admin API, w tym konflikt nazwy
użytkownika, brak zasobu, brak uprawnień oraz niedostępność Keycloak.

------------------------------------------------------------------------

## 6. Wymagania funkcjonalne

### 6.1 User Service

#### Tworzenie użytkownika

`POST /api/users`

Przykładowe body:

``` json
{
  "username": "john.doe",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe"
}
```

Wymagania:

-   walidacja pól wejściowych;
-   unikalność username i email zgodnie z polityką Keycloak;
-   utworzenie użytkownika w Keycloak;
-   zapis profilu aplikacyjnego z `keycloakId`;
-   domyślna rola `USER` jest nadawana przez Keycloak (default roles
    realmu) --- user-service nie wywołuje w tym celu `auth-service`;
-   rejestracja zdarzenia audytowego;
-   bez zwracania haseł ani sekretów.

Tworzenie konta wymaga obsługi częściowej awarii. Jeżeli Keycloak
utworzy konto, a zapis lokalny się nie powiedzie, system powinien
wykonać kompensację (np. usunąć świeżo utworzone konto) lub zwrócić
jednoznaczny błąd i umożliwić bezpieczne uzgodnienie stanu. Nie udawać
transakcji rozproszonej.

#### Pozostałe endpointy

  -------------------------------------------------------------------------
  Metoda                Endpoint                   Opis
  --------------------- -------------------------- ------------------------
  `GET`                 `/api/users`               Lista użytkowników,
                                                   paginacja i filtrowanie

  `GET`                 `/api/users/{id}`          Szczegóły użytkownika

  `POST`                `/api/users`               Utworzenie użytkownika

  `PUT`                 `/api/users/{id}`          Aktualizacja danych
                                                   profilu

  `PATCH`               `/api/users/{id}/status`   Aktywacja/dezaktywacja

  `DELETE`              `/api/users/{id}`          Usunięcie użytkownika

  `GET`                 `/api/users/me`            Profil zalogowanego
                                                   użytkownika

  `PUT`                 `/api/users/me`            Aktualizacja dozwolonych
                                                   danych własnego profilu
  -------------------------------------------------------------------------

Identyfikator API użytkownika może być UUID lokalnego profilu.
`keycloakId` jest przechowywany osobno i nie powinien być mylony z
lokalnym ID.

Lista użytkowników powinna obsługiwać `page`, `size`, `sort` oraz
opcjonalny filtr `search`. Maksymalny rozmiar strony: 100.

#### Status użytkownika

Status aplikacyjny jest spójny ze stanem `enabled` w Keycloak. Należy
zdefiniować zachowanie w przypadku braku synchronizacji i udostępnić
logi diagnostyczne bez ujawniania sekretów.

### 6.2 Auth Service

  ------------------------------------------------------------------------------
  Metoda             Endpoint                                 Opis
  ------------------ ---------------------------------------- ------------------
  `GET`              `/api/roles`                             Lista dostępnych
                                                              ról

  `GET`              `/api/users/{userId}/roles`              Role użytkownika

  `POST`             `/api/users/{userId}/roles`              Przypisanie roli

  `DELETE`           `/api/users/{userId}/roles/{roleName}`   Odebranie roli

  `GET`              `/api/auth/me`                         Role i podstawowe
                                                              informacje o
                                                              dostępie bieżącego
                                                              użytkownika
  ------------------------------------------------------------------------------

Przypisanie roli:

``` json
{
  "roleName": "MANAGER"
}
```

Wymagania:

-   `{userId}` w ścieżkach auth-service to `keycloakId` użytkownika
    (auth-service nie ma własnej bazy); user-service zwraca `keycloakId`
    w DTO profilu, a UI używa go w wywołaniach auth-service;
-   tylko `ADMIN` może zmieniać role; `MANAGER` nie ma dostępu do
    zmiany ról;
-   przypisywać i odbierać można wyłącznie `ADMIN`, `MANAGER`,
    `AUDITOR`; próba odebrania `USER` (rola bazowa) lub operacji na
    `SERVICE` kończy się błędem `400`;
-   rola musi istnieć w realmie;
-   nie można przypisać tej samej roli wielokrotnie;
-   nie można odebrać ostatniej roli `ADMIN` ostatniemu aktywnemu
    administratorowi;
-   operacja musi być audytowana;
-   zwracane dane nie mogą ujawniać sekretów Keycloak.

### 6.3 Audit Service

Przykładowe typy zdarzeń:

-   `USER_CREATED`
-   `USER_UPDATED`
-   `USER_ACTIVATED`
-   `USER_DEACTIVATED`
-   `USER_DELETED`
-   `ROLE_ASSIGNED`
-   `ROLE_REMOVED`

Przykładowy model zdarzenia:

``` json
{
  "eventType": "ROLE_ASSIGNED",
  "actorUserId": "keycloak-user-id",
  "targetUserId": "target-keycloak-user-id",
  "details": {
    "roleName": "MANAGER"
  },
  "occurredAt": "2026-11-10T09:00:00Z"
}
```

Endpointy:

  --------------------------------------------------------------------------
  Metoda                Endpoint                       Opis
  --------------------- ------------------------------ ---------------------
  `POST`                `/internal/api/audit-events`   Przyjęcie zdarzenia
                                                       przez serwis

  `GET`                 `/api/audit-events`            Lista zdarzeń z
                                                       filtrami

  `GET`                 `/api/audit-events/{id}`       Szczegóły zdarzenia
  --------------------------------------------------------------------------

Wymagania:

-   wpisy audytowe są niezmienne przez publiczne API;
-   lista obsługuje paginację i filtry: typ zdarzenia, aktor, użytkownik
    docelowy, zakres dat;
-   każde zdarzenie ma UUID i czas UTC;
-   nie zapisujemy tokenów, haseł, client secrets ani innych danych
    uwierzytelniających;
-   endpoint wewnętrzny nie może być dostępny anonimowo;
-   dostęp do odczytu: `ADMIN` i `AUDITOR`;
-   żądania między serwisami muszą być uwierzytelnione, a nie tylko
    chronione przez ukrycie endpointu.

------------------------------------------------------------------------

## 7. Komunikacja między mikroserwisami

OpenFeign jest wykorzystywany do synchronicznej komunikacji REST.

Przykładowe zależności:

-   `user-service` → `audit-service`: zapis zdarzeń dotyczących
    użytkowników;
-   `auth-service` → `audit-service`: zapis zdarzeń dotyczących ról.

Zasady:

1.  Każdy klient Feign ma określony connect/read timeout.
2.  Błędy HTTP i niedostępność serwisu są mapowane na kontrolowane
    wyjątki.
3.  Nie stosować nieskończonych retry.
4.  Retry dopuszczalne wyłącznie dla bezpiecznych, idempotentnych
    operacji i z ograniczoną liczbą prób.
5.  Żądania wewnętrzne muszą mieć uwierzytelnienie serwis-serwis:
    serwis wywołujący pobiera token przez Client Credentials, a
    endpointy `/internal/api/**` wymagają `ROLE_SERVICE`.
6.  Nie przekazywać sekretów ani tokenów do logów.
7.  Każde żądanie ma correlation ID (`X-Correlation-Id`), które jest
    propagowane we wszystkich wywołaniach Feign i obecne w każdym wpisie
    logu (zasady w §16.2).
8.  Nie tworzyć zależności cyklicznych pomiędzy serwisami.

W MVP synchroniczne audytowanie może powodować błąd operacji biznesowej
przy niedostępności `audit-service`. To zachowanie musi być jawne i
opisane. Dopuszczalne jest późniejsze rozszerzenie o trwały mechanizm
outbox/event broker, ale nie jest ono częścią MVP.

------------------------------------------------------------------------

## 8. Model danych

### 8.1 User Service

Tabela `users`:

  Kolumna         Typ            Uwagi
  --------------- -------------- ------------------
  `id`            UUID           PK
  `keycloak_id`   VARCHAR(64)    NOT NULL, UNIQUE
  `username`      VARCHAR(100)   NOT NULL
  `email`         VARCHAR(255)   NOT NULL
  `first_name`    VARCHAR(100)   nullable
  `last_name`     VARCHAR(100)   nullable
  `enabled`       BOOLEAN        NOT NULL
  `created_at`    TIMESTAMPTZ    NOT NULL
  `updated_at`    TIMESTAMPTZ    NOT NULL

Nie przechowywać hasła, hashy haseł ani tokenów.

### 8.2 Audit Service

Tabela `audit_events`:

  Kolumna            Typ            Uwagi
  ------------------ -------------- ----------
  `id`               UUID           PK
  `event_type`       VARCHAR(64)    NOT NULL
  `actor_user_id`    VARCHAR(64)    NOT NULL
  `target_user_id`   VARCHAR(64)    nullable
  `details`          JSONB          NOT NULL
  `occurred_at`      TIMESTAMPTZ    NOT NULL
  `correlation_id`   VARCHAR(100)   nullable

`correlation_id` to wartość nagłówka `X-Correlation-Id` żądania, które
spowodowało zdarzenie (patrz §16.2).

### 8.3 Auth Service

auth-service nie ma bazy ani schematu --- role są zarządzane w Keycloak.
Jeżeli implementacja wymaga lokalnego cache, musi on być jawnie opisany
jako cache, z określoną strategią odświeżania i bez traktowania go jako
źródła prawdy.

### 8.4 Izolacja danych

Dla uproszczenia lokalnego środowiska można użyć jednej instancji
PostgreSQL z osobnymi schematami: `users`, `audit`. Każdy serwis
powinien mieć osobnego użytkownika DB i uprawnienia tylko do własnego
schematu. Keycloak używa osobnej bazy lub przynajmniej osobnego schematu
i użytkownika.

Nie wolno łączyć danych serwisów przez bezpośrednie zapytania SQL do
cudzych schematów.

------------------------------------------------------------------------

## 9. Liquibase

Każdy serwis posiada własny changelog i wykonuje migracje wyłącznie dla
własnego schematu.

Przykładowa struktura:

``` text
user-service/
  src/main/resources/db/changelog/
    db.changelog-master.yaml
    changes/
      001-create-users-table.yaml

audit-service/
  src/main/resources/db/changelog/
    db.changelog-master.yaml
    changes/
      001-create-audit-events-table.yaml
```

Wymagania:

-   migracje są wersjonowane w Git;
-   nie edytować changelogów, które zostały już zastosowane na
    współdzielonym środowisku --- dodawać nowe migracje;
-   każda zmiana schematu ma rollback, jeśli jest sensowny;
-   migracje muszą działać na czystej bazie;
-   testy integracyjne weryfikują uruchomienie migracji;
-   nie używać `ddl-auto=create` ani `update` w środowisku aplikacyjnym.

------------------------------------------------------------------------

## 10. Angular --- zakres UI

Aplikacja Angular jest pojedynczym SPA.

### 10.1 Widoki MVP

1.  Login --- przekierowanie do Keycloak.
2.  Dashboard --- podstawowe informacje o zalogowanym użytkowniku.
3.  Users --- tabela użytkowników, wyszukiwanie, paginacja.
4.  User Details --- dane użytkownika, status, przypisane role.
5.  Create/Edit User --- formularz z walidacją.
6.  Roles --- lista ról i przypisywanie/odbieranie ról.
7.  Audit Logs --- tabela zdarzeń, filtrowanie i paginacja.
8.  Access Denied --- ekran braku uprawnień.

### 10.2 Zasady UI

-   Angular Material jako podstawowy zestaw komponentów.
-   Reactive Forms do formularzy.
-   Route Guards do kontroli widoczności tras.
-   HTTP Interceptor do dołączania access tokena.
-   Obsługa stanów loading, empty, error.
-   Obsługa HTTP 401 i 403.
-   Role pobierane z tokena lub dedykowanego endpointu `/api/auth/me`.
-   UI nie jest granicą bezpieczeństwa --- backend zawsze sprawdza
    uprawnienia.

### 10.3 Wzorzec wizualny

Plik [`gui.jpg`](gui.jpg) jest referencją **wyglądu i układu**, nie
zakresu funkcji:

-   ciemny (granatowy) sidebar z aktywną pozycją w kolorze indygo,
    jasny obszar treści, górny pasek;
-   nagłówek strony z opisem i przyciskiem akcji po prawej, kafelki
    statystyk, tabele w kartach, statusy jako „pill” z kolorową kropką;
-   realizacja: Angular Material z własnym motywem, bez dodatkowych
    bibliotek UI;
-   menu MVP: Dashboard, Users, Roles, Audit log (Audit log tylko dla
    `ADMIN`/`AUDITOR`).

Elementy makiety poza MVP --- nie implementujemy: tworzenie ról,
szablony ról, import CSV, eksport, „Pending Reviews”, Settings,
Billing, globalna wyszukiwarka, powiadomienia.

------------------------------------------------------------------------

## 11. Bezpieczeństwo i ograniczenia

### 11.1 Wymagania bezpieczeństwa

-   HTTPS w środowisku produkcyjnym; lokalnie dopuszczalny HTTP.
-   Walidacja JWT: podpis, issuer, expiry i audience.
-   Minimalne uprawnienia klienta technicznego Keycloak.
-   Sekrety wyłącznie przez zmienne środowiskowe/secret manager.
-   Brak sekretów w repozytorium, logach, obrazach Docker i frontendzie.
-   Walidacja wszystkich danych wejściowych.
-   Ograniczenie maksymalnego rozmiaru strony.
-   CORS ograniczony do znanych originów.
-   Ochrona przed masowym przypisywaniem nieznanych ról.
-   Audyt zmian administracyjnych.
-   Nie logować pełnych tokenów JWT ani danych uwierzytelniających.
-   Logi przesyłane do ELK nie zawierają tokenów, sekretów ani haseł;
    porty ELK są lokalnie publikowane wyłącznie na `127.0.0.1`.
-   Używać parametrów zapytań i mechanizmów ORM bez konkatenacji SQL z
    wejściem użytkownika.

### 11.2 Ograniczenia architektoniczne MVP

-   Brak API Gateway.
-   Brak komunikacji asynchronicznej.
-   Brak automatycznego discovery; lokalne adresy serwisów są
    konfigurowane przez środowisko.
-   Brak wysokiej dostępności i skalowania produkcyjnego.
-   ELK działa jako pojedynczy węzeł (single-node), bez HA i bez
    produkcyjnej polityki retencji logów.
-   Brak pełnej transakcyjności rozproszonej.
-   Brak zaawansowanego mechanizmu synchronizacji Keycloak z bazą
    aplikacji.
-   Brak gwarancji natychmiastowego odświeżenia już wydanego JWT po
    zmianie roli. Zmiana ról wpływa na nowe tokeny po
    odświeżeniu/ponownym zalogowaniu; należy dobrać rozsądny czas życia
    tokenów.
-   Usunięcie użytkownika i usunięcie/zmiana jego profilu w różnych
    systemach nie jest jedną transakcją. Należy obsłużyć częściową
    awarię i zapewnić diagnostykę.

### 11.3 Ograniczenia domenowe

-   Jeden realm: `mini-iam`.
-   Jeden tenant/organizacja.
-   Cztery stałe role aplikacyjne.
-   Użytkownicy tworzeni przez administratora lub managera.
-   Hasłami zarządza Keycloak.
-   Role są źródłem prawdy w Keycloak.
-   Brak dowolnego tworzenia ról z UI w MVP.
-   Brak edycji uprawnień pojedynczych endpointów przez użytkownika.

------------------------------------------------------------------------

## 12. Testowanie i kryteria jakości

### 12.1 Testy jednostkowe

Testować przede wszystkim logikę domenową i przypadki brzegowe:

-   walidację danych użytkownika;
-   reguły zmiany statusu;
-   ograniczenia dotyczące ostatniego administratora;
-   mapowanie błędów Keycloak;
-   mapowanie claimów JWT na authorities;
-   mapowanie DTO;
-   filtrowanie i walidację zdarzeń audytowych.

Mockito stosować do izolowania zewnętrznych zależności, a nie do
mockowania każdej klasy w prostych testach.

### 12.2 Testy integracyjne

Testcontainers:

-   PostgreSQL --- repozytoria, migracje Liquibase, transakcje;
-   Keycloak --- tworzenie użytkownika, pobieranie użytkownika, role,
    tokeny i autoryzacja;
-   Spring Boot Test + MockMvc --- endpointy, walidacja i
    zabezpieczenia.

Testy integracyjne powinny korzystać z rzeczywistego PostgreSQL i
Keycloak, a nie wyłącznie z mocków.

### 12.3 Minimalny zestaw testów akceptacyjnych

1.  Anonimowy klient nie ma dostępu do chronionego API --- HTTP 401.
2.  Zalogowany `USER` nie może wywołać endpointu administratora --- HTTP
    403. 
3.  `ADMIN` może utworzyć użytkownika.
4.  Utworzony użytkownik istnieje w Keycloak i ma lokalny profil.
5.  Nowy użytkownik ma rolę `USER`.
6.  `ADMIN` może przypisać `MANAGER`.
7.  `MANAGER` nie może przypisywać ról.
8.  Nie można odebrać ostatniej roli `ADMIN` ostatniemu aktywnemu
    administratorowi.
9.  Dezaktywacja konta aktualizuje stan w Keycloak.
10. Operacje administracyjne są widoczne w audit-service.
11. Użytkownik nie może odczytać profilu innej osoby.
12. Liquibase buduje schemat na czystej bazie.
13. Błąd niedostępnego mikroserwisu jest obsłużony i nie powoduje
    wycieku szczegółów technicznych do API.
14. Żądanie bez nagłówka `X-Correlation-Id` otrzymuje wygenerowany
    identyfikator; ten sam identyfikator jest zwracany w odpowiedzi,
    występuje w logach wszystkich serwisów biorących udział w operacji
    oraz w zdarzeniu audytowym.

### 12.4 Standard kodu

-   Czytelne nazwy klas i metod po angielsku.
-   Konstruktorowe wstrzykiwanie zależności.
-   DTO oddzielone od encji JPA.
-   Walidacja na granicy API.
-   Globalna obsługa wyjątków (`@RestControllerAdvice`).
-   Spójny format odpowiedzi błędów, najlepiej zgodny z RFC 9457 /
    Spring ProblemDetail.
-   Logi strukturalne (JSON/ECS) z correlation ID, zgodnie z §16.
-   Brak logiki biznesowej w kontrolerach.
-   Brak nadmiernego stosowania wzorców i abstrakcji bez potrzeby.

------------------------------------------------------------------------

## 13. Struktura repozytorium

Preferowany Maven multi-module:

``` text
mini-iam-platform/
├── pom.xml
├── mvnw
├── .mvn/
├── user-service/
│   ├── pom.xml
│   └── src/
├── auth-service/
│   ├── pom.xml
│   └── src/
├── audit-service/
│   ├── pom.xml
│   └── src/
├── frontend/
│   └── mini-iam-ui/
├── infrastructure/
│   ├── docker-compose.yml
│   ├── keycloak/
│   │   └── realm-import/
│   ├── postgres/
│   └── elk/
│       ├── logstash/
│       │   └── pipeline/
│       ├── filebeat/
│       └── kibana/
├── docs/
│   └── architecture.md
├── .env.example
├── .gitignore
└── README.md
```

Nie dodawać `notification-service` do pierwszej implementacji. Można
zostawić opis jako przyszłe rozszerzenie.

### 13.1 Konwencja pakietów backendu

Przykładowo dla `user-service`:

``` text
pl.stojak.miniiam.user/
├── config/
├── controller/
├── dto/
│   ├── request/
│   └── response/
├── domain/
├── exception/
├── integration/
│   └── keycloak/
├── mapper/
├── repository/
├── service/
└── security/
```

Analogicznie dla pozostałych serwisów, z pakietami dopasowanymi do ich
odpowiedzialności.

------------------------------------------------------------------------

## 14. Konfiguracja i uruchomienie lokalne

Docker Compose powinien uruchamiać:

-   PostgreSQL;
-   Keycloak;
-   `user-service`;
-   `auth-service`;
-   `audit-service`;
-   opcjonalnie Angular dev server (może być uruchamiany osobno);
-   w profilu `elk`: Elasticsearch, Logstash, Kibana i Filebeat
    (`docker compose --profile elk up -d`).

Wymagania:

-   healthcheck dla PostgreSQL i Keycloak (oraz Elasticsearch i Kibany w
    profilu `elk`);
-   serwisy backendowe powinny czekać na gotowość zależności lub
    poprawnie ponawiać połączenie przy starcie;
-   porty i konfiguracja przez zmienne środowiskowe;
-   wolumeny dla danych PostgreSQL;
-   realm import dla Keycloak;
-   `.env.example` z nazwami i przykładowymi, niesekretnymi wartościami;
-   `.env` dodany do `.gitignore`;
-   brak domyślnych produkcyjnych haseł w repozytorium.

README ma zawierać instrukcję od zera:

1.  wymagania lokalne;
2.  skopiowanie `.env.example` do `.env`;
3.  uruchomienie `docker compose up -d`;
4.  uruchomienie backendu i frontendu (jeśli nie są uruchamiane przez
    Compose);
5.  adresy aplikacji, konsoli Keycloak i Kibany;
6.  sposób utworzenia lokalnego konta administratora;
7.  uruchomienie stosu ELK i wyszukiwanie logów po `correlationId` w
    Kibanie;
8.  uruchomienie testów;
9.  typowe problemy i ich rozwiązania.

------------------------------------------------------------------------

## 15. API --- wspólne konwencje

-   Prefiks `/api`.
-   JSON jako format wymiany danych.
-   UUID jako identyfikator lokalnych zasobów.
-   Daty w ISO-8601, przechowywane jako UTC (`TIMESTAMPTZ`).
-   Paginacja oparta na `page`, `size`, `sort`.
-   Błędy w formacie Problem Details.
-   Kody HTTP:
    -   `200` --- poprawny odczyt/aktualizacja;
    -   `201` --- utworzenie;
    -   `204` --- poprawne usunięcie/operacja bez body;
    -   `400` --- niepoprawne dane;
    -   `401` --- brak/niepoprawny token;
    -   `403` --- brak uprawnień;
    -   `404` --- zasób nie istnieje;
    -   `409` --- konflikt, np. duplikat;
    -   `502`/`503` --- problem z zależnością zewnętrzną, zależnie od
        sytuacji.
-   Nie zwracać stack trace ani wewnętrznych szczegółów infrastruktury w
    odpowiedzi HTTP.

------------------------------------------------------------------------

## 16. Logowanie i śledzenie żądań (ELK)

Logi wszystkich serwisów backendowych są gromadzone centralnie w stosie
ELK i przeglądane w Kibanie. Przebieg pojedynczego żądania przez wiele
serwisów jest odtwarzany na podstawie `X-Correlation-Id`.

### 16.1 Format logów

-   Każdy serwis loguje na stdout w formacie JSON zgodnym z ECS (Elastic
    Common Schema), używając natywnego structured logging Spring Boot
    (`logging.structured.format.console=ecs`); jeden obiekt JSON na
    linię.
-   Wymagane pola: `@timestamp` (UTC), `log.level`, `service.name`,
    `log.logger`, `message`, `correlationId`; wyjątki w polach `error.*`
    (typ, komunikat, stack trace --- tylko w logu, nigdy w odpowiedzi
    HTTP).
-   Serwisy nie zapisują logów do plików i nie wysyłają ich bezpośrednio
    do ELK --- zbieraniem zajmuje się Filebeat.
-   Przy uruchomieniu lokalnym poza Dockerem dopuszczalny jest czytelny
    format tekstowy (osobny profil Spring), ale zawsze z
    `correlationId`.

### 16.2 Correlation ID (`X-Correlation-Id`)

-   Każdy serwis ma filtr servletowy uruchamiany przed filtrami
    bezpieczeństwa, który:
    -   przyjmuje nagłówek `X-Correlation-Id` z żądania, jeśli jest
        poprawny (maks. 100 znaków, dozwolone znaki `[A-Za-z0-9_-]`);
    -   w przeciwnym razie generuje nowy UUID;
    -   umieszcza wartość w MDC pod kluczem `correlationId`;
    -   zwraca ją w nagłówku odpowiedzi `X-Correlation-Id` (także dla
        odpowiedzi 401/403/5xx);
    -   czyści MDC po zakończeniu żądania.
-   Interceptor Feign przekazuje bieżący `X-Correlation-Id` w każdym
    wywołaniu między serwisami.
-   `audit-service` zapisuje otrzymany identyfikator w kolumnie
    `correlation_id` (§8.2).
-   Angular może wysyłać własny `X-Correlation-Id` (opcjonalnie); CORS
    dopuszcza ten nagłówek w żądaniach i eksponuje go w odpowiedziach.

### 16.3 Co logować, a czego nie

Logować:

-   zakończenie żądania HTTP: metoda, ścieżka (bez query z danymi
    wrażliwymi), status, czas trwania;
-   wywołania Feign i Keycloak Admin API: cel, status, czas trwania;
-   błędy domenowe i integracyjne, w tym kompensacje i rozbieżności
    stanu z Keycloak;
-   zdarzenia bezpieczeństwa (401/403) --- bez treści tokena.

Identyfikatorem aktora w logach jest `keycloakId`; nie umieszczać e-maili
ani innych danych osobowych w logach, jeśli nie jest to konieczne.

Nie logować: tokenów (także fragmentów), nagłówka `Authorization`,
haseł, client secretów, pełnych body żądań i odpowiedzi.

### 16.4 Pipeline i infrastruktura

-   **Filebeat** --- docker autodiscover; zbiera logi wyłącznie z
    kontenerów oznaczonych etykietą `co.elastic.logs/enabled=true`
    (serwisy backendowe), dekoduje JSON i przekazuje do Logstash.
-   **Logstash** --- pipeline przechowywany w repozytorium
    (`infrastructure/elk/logstash/pipeline/`); normalizuje pola, a linie
    niebędące JSON-em zachowuje w polu `message`; zapisuje do
    Elasticsearch w indeksie `mini-iam-logs-YYYY.MM.dd`.
-   **Elasticsearch** --- single-node, wolumen na dane, limit pamięci
    przez `ES_JAVA_OPTS`, healthcheck.
-   **Kibana** --- healthcheck; data view `mini-iam-logs-*` tworzony
    automatycznie przez kontener/skrypt inicjalizujący (lub, jeśli to
    niemożliwe, opisany krok w README).
-   Cały stos ELK jest w profilu Compose `elk`; porty publikowane tylko
    na `127.0.0.1`.
-   Bezpieczeństwo Elasticsearch włączone; hasła `elastic` i
    `kibana_system` pochodzą z `.env` (w `.env.example` tylko nazwy
    zmiennych i niesekretne przykłady).

### 16.5 Zasady

-   Brak lub awaria ELK nie może blokować startu ani działania serwisów.
-   Testy jednostkowe i integracyjne nie wymagają ELK; filtr correlation
    ID i propagacja w Feign są testowane bez niego.
-   Poza zakresem MVP: rozproszony tracing (APM/OpenTelemetry), metryki,
    alerting, ILM i produkcyjna retencja, HA klastra.

------------------------------------------------------------------------

## 17. Plan realizacji i zasady pracy z Claude Code

Szczegółowy harmonogram etapów implementacji znajduje się w pliku
[`implementation-plan.md`](implementation-plan.md).

Zasady współpracy, ograniczenia i wymagany sposób pracy Claude Code
znajdują się w pliku [`CLAUDE.md`](../CLAUDE.md) w katalogu głównym.

Dokumenty te należy traktować łącznie:

-   `project-spec.md` --- wymagania produktu, architektura, technologie
    i kryteria jakości;
-   `implementation-plan.md` --- kolejność i zakres etapów implementacji;
-   `CLAUDE.md` --- zasady działania Claude Code.
