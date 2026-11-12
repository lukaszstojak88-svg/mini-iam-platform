# Testy i raporty pokrycia

Jak uruchomić testy Mini IAM i obejrzeć raporty pokrycia kodu. Mapowanie testów na wymagania akceptacyjne
(spec §12.3) jest w [`README.md`](../README.md#11-testy).

Na Windows z proxy TLS przed komendami ustaw `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` (Maven)
i `NODE_OPTIONS=--use-system-ca` (npm/npx).

## Backend (JaCoCo)

Wygenerowanie raportu (z katalogu głównego repozytorium):

```bash
./mvnw clean test      # tylko testy jednostkowe, szybko
./mvnw clean verify    # testy jednostkowe + integracyjne (Testcontainers, wymaga Dockera)
```

`clean` usuwa poprzednie dane pokrycia. Bez niego JaCoCo dopisuje wyniki do starego pliku `target/jacoco.exec`
i raport miesza stare przebiegi z nowymi.

Każdy serwis ma osobny raport HTML:

| Serwis | Raport |
|---|---|
| user-service | `user-service/target/site/jacoco/index.html` |
| auth-service | `auth-service/target/site/jacoco/index.html` |
| audit-service | `audit-service/target/site/jacoco/index.html` |

Otwarcie w przeglądarce:

```bash
start user-service/target/site/jacoco/index.html   # Windows (Git Bash / PowerShell)
xdg-open user-service/target/site/jacoco/index.html  # Linux
open user-service/target/site/jacoco/index.html      # macOS
```

Obok HTML są też `jacoco.xml` i `jacoco.csv` (np. dla narzędzi CI).

## Frontend (Vitest + `@vitest/coverage-v8`)

```bash
cd frontend/mini-iam-ui
npm run test:coverage                    # testy + podsumowanie pokrycia w konsoli
start coverage/mini-iam-ui/index.html    # raport HTML (Linux: xdg-open, macOS: open)
```

Katalog `coverage/` jest w `.gitignore`.

## Jak czytać raport

- Nawigacja: pakiet / katalog → klasa / plik → kod źródłowy z kolorowaniem.
- Kolory linii: zielony — pokryta, czerwony — niepokryta, żółty — częściowo (nie wszystkie gałęzie `if` /
  `switch` zostały sprawdzone; liczbę pokazuje romb przy linii w JaCoCo).
- Kolumny JaCoCo: *Instructions* (instrukcje bajtkodu), *Branches* (gałęzie), *Lines*, *Methods*, *Classes*.
  Najbardziej miarodajne są *Branches* i *Lines*.
- Kolumny Vitest: *Statements*, *Branches*, *Functions*, *Lines*.

Raporty nie mają progu blokującego build (decyzja z Etapu 7). Służą do wskazywania nieprzetestowanych miejsc,
a nie jako cel procentowy.

## Pozostałe testy

| Komenda | Co sprawdza |
|---|---|
| `./mvnw test` | testy jednostkowe i MockMvc (bez Dockera) |
| `./mvnw verify` | + testy integracyjne `*IT` na prawdziwym PostgreSQL i Keycloak (Testcontainers) |
| `cd frontend/mini-iam-ui && npx ng test --watch=false` | testy Angular (Vitest) |
| `./scripts/e2e-smoke.sh` | test end-to-end na działającym Compose; z `ADMIN_TOKEN` pełny scenariusz administracyjny (opis w README, sekcja „Testy”) |