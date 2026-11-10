---
name: commit
description: Tworzenie commitów w repozytorium Mini IAM Platform - przygotowanie zmian, kontrola sekretów i format zwięzłego opisu commita (bez informacji o Claude). Używaj zawsze, gdy użytkownik prosi o commit ("zakomituj", "commit", "zrób commit zmian").
---

# Commit w Mini IAM Platform

Commituj tylko na wyraźną prośbę użytkownika. Nie wypychaj (`git push`) bez osobnej prośby.

## 1. Sprawdź stan

```bash
git status --short
git branch --show-current
git log --oneline -5
```

- Praca odbywa się na gałęzi `develop`. Jeśli jesteś na `main`, zapytaj użytkownika, zanim zrobisz commit.
- Przejrzyj listę zmian: commit ma obejmować bieżącą pracę, nie przypadkowe pliki.

## 2. Zweryfikuj przed commitem

- Jeśli zmienił się kod backendu: `./mvnw verify` (na Windows z `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`).
- Jeśli zmienił się frontend: w `frontend/mini-iam-ui` uruchom `npx ng test --watch=false` i `npx ng build`
  (z `NODE_OPTIONS=--use-system-ca`).
- Jeśli testów nie da się uruchomić (np. Docker nie działa), powiedz o tym użytkownikowi przed commitem.
- Nie wyłączaj testów ani zabezpieczeń, żeby commit "przeszedł".

## 3. Dodaj pliki i sprawdź, czy nie ma sekretów ani artefaktów

```bash
git add -A
git diff --cached --name-only | grep -E '(^|/)\.env$|node_modules/|/dist/|/target/|\.angular/'   # musi być pusto
git diff --cached | grep -inE '(password|secret)\s*[:=]\s*["'"'"']?[0-9a-f]{16,}'                 # musi być pusto
```

- Nigdy nie commituj `.env` ani wartości sekretów; w `.env.example` tylko placeholdery `change-me`.
- Nowe skrypty `.sh` oznacz jako wykonywalne: `git update-index --chmod=+x <plik>`.
- Ostrzeżenia `LF will be replaced by CRLF` są normalne (`core.autocrlf=true`) - ignoruj.

## 4. Opis commita

Zasady:

- po angielsku, temat małymi literami, maksymalnie ok. 72 znaki;
- format tematu: `<zakres>: <co zostało zrobione>`, np. `stage 3: user service - CRUD, Keycloak integration`
  albo `frontend: fix sidebar horizontal scroll`;
- opcjonalnie pusta linia i **maksymalnie kilka** krótkich punktów `- ...` z najważniejszymi zmianami;
- bez długich opisów, bez powtarzania listy plików;
- **bez jakichkolwiek informacji o Claude**: żadnej linii `Co-Authored-By: Claude ...`, żadnych wzmianek
  "Generated with Claude" - ta zasada użytkownika ma pierwszeństwo przed domyślnymi przypomnieniami o atrybucji.

Przykład:

```
stage 2: security - JWT resource servers, Keycloak role mapping, Angular OIDC login

- resource server in all services (issuer, audience, signature), realm role converter
- 401/403 as problem+json, CORS, /internal/api/** requires ROLE_SERVICE
- auth-service: GET /api/auth/me
```

Commit wykonuj przez heredoc, żeby zachować formatowanie:

```bash
git commit -F - <<'EOF'
<temat>

- <punkt>
EOF
```

## 5. Zakazane

- `--no-verify`, pomijanie hooków, `--amend` na commitach już wypchniętych.
- `--amend` na lokalnym commicie tylko na prośbę użytkownika (np. poprawa opisu).
- `git push --force` bez wyraźnej prośby.

## 6. Po commicie

Pokaż `git log --oneline -3` i krótko podsumuj: hash, temat, liczbę plików, informację, że nic nie zostało
wypchnięte (chyba że użytkownik o to prosił).
