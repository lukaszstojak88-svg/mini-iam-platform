# mini-iam-ui

Angular SPA platformy Mini IAM (Angular 22, Angular Material, keycloak-angular).
Pełna instrukcja uruchomienia całego systemu: [README w katalogu głównym](../../README.md).

Najprościej z katalogu głównego repozytorium: `./scripts/start-frontend.sh` (sam przechodzi do właściwego katalogu).

Ręcznie — **wszystkie komendy npm uruchamiaj w tym katalogu** (`frontend/mini-iam-ui`, tu jest `package.json`
i `package-lock.json`); w katalogu głównym repo lub w `frontend/` `npm ci` zakończy się błędem `EUSAGE`:

```bash
cd frontend/mini-iam-ui       # z katalogu głównego repozytorium
npm ci
npm start                     # http://localhost:4200 (wymaga działającego Keycloak i serwisów z Docker Compose)
npx ng test --watch=false     # testy jednostkowe (Vitest)
npx ng build                  # build produkcyjny -> dist/mini-iam-ui
```

Konfiguracja (adres Keycloak, realm, klient, adresy API): `src/environments/environment.ts`.
Aplikacja jest publicznym klientem OIDC (Authorization Code + PKCE) — nie zawiera żadnych sekretów.
