# Mini IAM Platform — architektura

> Szkic z Etapu 1. Pełny opis i diagram zostaną uzupełnione w Etapie 7.
> Źródło prawdy: [`project-spec.md`](project-spec.md) (§4, §7, §8, §16).

## Komponenty

| Komponent | Port | Dane | Odpowiedzialność |
|---|---|---|---|
| `user-service` | 8081 | PostgreSQL, schemat `users` (użytkownik DB `user_service`) | profile użytkowników, CRUD kont w Keycloak |
| `auth-service` | 8082 | brak (role w Keycloak) | odczyt/przypisywanie Realm Roles |
| `audit-service` | 8083 | PostgreSQL, schemat `audit` (użytkownik DB `audit_service`) | trwały dziennik audytowy |
| Keycloak 26.8 | 8080 | PostgreSQL, osobna baza `keycloak` | tożsamość, tokeny, role (realm `mini-iam`) |
| PostgreSQL 17 | 5432 | wolumen `postgres-data` | jedna instancja, izolacja przez schematy i użytkowników |
| ELK 9.5 (profil `elk`) | 9200, 5601 | wolumen `elasticsearch-data` | centralne logi |

## Przepływy

```text
Angular SPA ──(Access Token JWT)──> user-service / auth-service / audit-service
user-service ──OpenFeign (Client Credentials, ROLE_SERVICE)──> audit-service
auth-service ──OpenFeign (Client Credentials, ROLE_SERVICE)──> audit-service
user-service, auth-service ──Admin REST API (mini-iam-admin)──> Keycloak

Logi:
user/auth/audit-service ── stdout JSON/ECS ──> Filebeat ──> Logstash ──> Elasticsearch ──> Kibana
```

## Izolacja danych (spec §8.4)

- `user_service` jest właścicielem wyłącznie schematu `users`, `audit_service` — wyłącznie `audit`;
  brak uprawnień do schematu `public` i do cudzych schematów (zweryfikowane w Etapie 1).
- Keycloak używa osobnej bazy `keycloak` i użytkownika `keycloak`; użytkownicy serwisów nie mają do niej
  `CONNECT`.
- Każdy serwis wykonuje własne migracje Liquibase (tabele `databasechangelog*` w swoim schemacie).

## Śledzenie żądań (spec §16.2)

`CorrelationIdFilter` w każdym serwisie (osobna kopia — brak współdzielonego modułu): przyjmuje poprawny
`X-Correlation-Id` (`[A-Za-z0-9_-]{1,100}`) lub generuje UUID, umieszcza go w MDC (`correlationId`)
i w nagłówku odpowiedzi. Propagacja przez Feign — Etap 4/5.
