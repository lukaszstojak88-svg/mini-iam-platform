# Mini IAM Platform

Edukacyjny projekt portfolio: zarządzanie użytkownikami, rolami i audytem operacji administracyjnych
w architekturze mikroserwisowej (Spring Boot 3.5 + Keycloak 26 + PostgreSQL + ELK).

- Specyfikacja: [`docs/project-spec.md`](docs/project-spec.md)
- Plan etapów: [`docs/implementation-plan.md`](docs/implementation-plan.md)
- Architektura: [`docs/architecture.md`](docs/architecture.md)

> **Status:** Etap 1 (repozytorium i infrastruktura). Serwisy startują, ale nie mają jeszcze
> logiki biznesowej ani zabezpieczeń JWT (Etap 2+).

## 1. Wymagania lokalne

| Narzędzie | Wersja |
|---|---|
| JDK | 21+ (kod kompilowany z `release 21`) |
| Docker + Docker Compose | Docker Desktop z Compose ≥ 2.20 (wymagane `include`) |
| RAM dla Dockera | ~4 GB z profilem `elk`, ~2 GB bez |
| Maven | nie jest wymagany — używaj `./mvnw` |

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

Frontend (Angular) powstanie w Etapie 6.

## 4. Adresy

| Usługa | Adres |
|---|---|
| user-service | http://localhost:8081 (health: `/actuator/health`) |
| auth-service | http://localhost:8082 |
| audit-service | http://localhost:8083 |
| Keycloak — konsola admina | http://localhost:8080/admin (login: `KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD`) |
| Keycloak — realm `mini-iam` | http://localhost:8080/realms/mini-iam |
| Kibana (profil `elk`) | http://localhost:5601 (login: `elastic` / `ELASTIC_PASSWORD`) |
| Elasticsearch (profil `elk`) | http://localhost:9200 |
| PostgreSQL | localhost:5432, baza `mini_iam` (schematy `users`, `audit`) |

Wszystkie porty są publikowane tylko na `127.0.0.1`.

## 5. Konto administratora aplikacji

Realm import (`infrastructure/keycloak/realm-import/mini-iam-realm.json`) tworzy użytkownika **`admin`**
z rolami `ADMIN` i `USER`. Hasło pochodzi z `MINI_IAM_ADMIN_PASSWORD` w `.env` (placeholder rozwiązywany
przez Keycloak przy imporcie).

Realm jest importowany **tylko przy pierwszym starcie** (gdy nie istnieje w bazie Keycloak). Zmiana pliku
realmu lub haseł w `.env` wymaga czystego startu: `docker compose down -v`.

Konfiguracja realmu:

- role realmu: `ADMIN`, `MANAGER`, `USER`, `AUDITOR` + techniczna `SERVICE`; `USER` w `default-roles-mini-iam`;
- `mini-iam-frontend` — public client, Authorization Code + PKCE S256, `http://localhost:4200`;
- `mini-iam-admin` — confidential client z service account (Client Credentials), rola `SERVICE` oraz
  `realm-management`: `view-users`, `query-users`, `manage-users`, `view-realm`; scope `kc-admin-api`
  (wymagany przez Admin API od Keycloak 26.8.2);
- audience `mini-iam-api` w tokenach obu klientów; `fullScopeAllowed=false` (tokeny zawierają tylko
  role przypisane w scope mappings);
- czas życia access tokena: 5 minut.

## 6. Logi i śledzenie żądań (ELK)

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

## 7. Testy

```bash
./mvnw test      # testy jednostkowe
./mvnw verify    # + testy integracyjne (*IT) z Testcontainers - wymaga działającego Dockera
```

Testy integracyjne uruchamiają PostgreSQL w Testcontainers i weryfikują migracje Liquibase na czystej bazie.
Nie wymagają uruchomionego Compose ani ELK.

## 8. Typowe problemy

| Problem | Rozwiązanie |
|---|---|
| `PKIX path building failed` przy `./mvnw` (Windows, proxy/antywirus podmieniający certyfikaty) | `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` (PowerShell: `$env:MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"`) |
| `required variable ... is missing a value` przy `docker compose` | Brak `.env` lub brakująca zmienna — skopiuj `.env.example` do `.env`. |
| Testy `*IT` nie startują (`Could not find a valid Docker environment`) | Uruchom Docker Desktop. |
| Admin API Keycloak zwraca 401 dla service account | Token musi zawierać scope `kc-admin-api` (Keycloak ≥ 26.8.2) — jest w realm imporcie; przy starym wolumenie zrób `docker compose down -v`. |
| Zmiany w realm imporcie nie są widoczne | Realm importuje się tylko raz — `docker compose down -v`. |
| Brak logów w Kibanie | Sprawdź `docker compose --profile elk logs filebeat logstash`; serwisy muszą mieć sterownik logów `json-file` (ustawiony w Compose) i etykietę `co.elastic.logs/enabled=true`. Zawęź zakres czasu w Discover. |
| Elasticsearch kończy się błędem pamięci | Zwiększ pamięć dla Docker Desktop (min. 4 GB z profilem `elk`). |
| Port zajęty | Zmień odpowiedni `*_PORT` w `.env`. |
