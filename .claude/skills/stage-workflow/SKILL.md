---
name: stage-workflow
description: Przebieg realizacji etapu Mini IAM Platform wg docs/implementation-plan.md - rozpoczęcie (analiza, pytania, plan), implementacja i zamknięcie (weryfikacja, aktualizacja CLAUDE.md/README/spec, raport końcowy). Używaj, gdy użytkownik prosi "zacznij etap N", "zaimplementuj etap N", "zakończ etap".
---

# Realizacja etapu

## 1. Start

1. Przeczytaj w `CLAUDE.md` sekcje „Aktualny etap”, „Podjęte decyzje” oraz tabelę „Co czytać przy którym etapie”;
   przeczytaj wskazane rozdziały `docs/project-spec.md` i opis etapu w `docs/implementation-plan.md`
   (z kryterium „Gotowe, gdy”).
2. Sprawdź stan repo (`git status`, `git log --oneline -5`) i istniejący kod, który etap rozszerza.
3. Sprawdź aktualne, kompatybilne wersje nowych zależności (Maven Central / npm), zgodne z linią Spring Boot 3.5.
4. Zadaj pytania tylko o realne niejednoznaczności, które zmieniają implementację (zakres, wybór biblioteki,
   sposób testowania, zachowanie przy awarii). Rekomendowaną opcję oznacz jako pierwszą.
5. Przedstaw plan: kontekst, zmiany per moduł, lista plików, testy, weryfikacja. Zaczekaj na akceptację,
   chyba że użytkownik wprost zlecił implementację.

## 2. Implementacja

- Tylko zakres bieżącego etapu; bez funkcji spoza MVP i bez kolejnych etapów.
- Kod backendu wg skilla `backend-feature`; zmiany realmu wg `keycloak-realm`; frontend wg `docs/frontend.md`
  (nowe ustalenia frontendowe dopisuj do tego pliku).
- Testy i build po każdej istotnej zmianie. Nie wyłączaj testów ani zabezpieczeń.
- Bez stubów, pustych metod i `TODO` w funkcjach uznanych za gotowe.
- Problemy środowiskowe naprawiaj u źródła i dokumentuj w README („Typowe problemy”).
- Gdy wymaganie koliduje z bezpieczeństwem lub architekturą - zatrzymaj się i wyjaśnij.

## 3. Zamknięcie

1. Weryfikacja wg skilla `verify-stack` (testy, Compose, smoke testy nowych endpointów, UI jeśli dotyczy).
1a. Recenzja agentem `change-reviewer` (zakres: zmiany etapu). Napraw potwierdzone findingi KRYTYCZNE/WYSOKIE,
    pozostałe wymień w raporcie wraz z decyzją (naprawione / świadomie pominięte i dlaczego).
2. Aktualizacja dokumentacji:
   - `CLAUDE.md`: „Aktualny etap” (etap N zrealizowany, oczekuje akceptacji; następny etap), „Komendy”,
     „Podjęte decyzje” (nowe ustalenia);
   - `README.md`: uruchomienie, adresy, testy, typowe problemy;
   - `docs/project-spec.md` tylko gdy użytkownik zmienił ustalenia; `docs/architecture.md` gdy zmienił się przepływ.
3. Raport (po polsku, zasada 14 w `CLAUDE.md`):
   - zakres zmian (moduły, najważniejsze pliki),
   - najważniejsze decyzje i przyjęte założenia,
   - komendy uruchomieniowe,
   - wyniki testów (liczby, co uruchomiono),
   - co zweryfikowano ręcznie, a czego nie (np. UI w przeglądarce),
   - znane ograniczenia i naprawione problemy.
4. Nie commituj automatycznie - zaproponuj commit; po zgodzie użyj skilla `commit`.
