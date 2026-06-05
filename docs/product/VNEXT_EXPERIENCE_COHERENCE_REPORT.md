# vNext Experience Coherence Report

> Generated: 2026-06-05T08:46:44.754Z

| # | Metric | Value |
|---|--------|------:|
| 1 | Total frontend routes | 445 |
| 2 | Routes mapped to actor/context/intent/transaction | 368 |
| 3 | Orphan routes | 93 |
| 4 | Backend capabilities without journeys | 31 |
| 5 | Mock/stub/placeholder routes | 9 |
| 6 | Isolated pages | 38 |
| 7 | Incomplete transaction journeys | 40 |
| 8 | Mobile surfaces disconnected | 45 |

## Top 20 coherence gaps

1. **route:/auth/forgot-password** — `unclear-intent`: hooks/queries/useAuth.ts
2. **route:/auth/reset-password** — `unclear-intent`: hooks/queries/useAuth.ts
3. **route:/auth/logout** — `unclear-intent`: hooks/queries/useAuth.ts
4. **route:/auth** — `missing-journey`: hooks/queries/useAuth.ts
5. **route:/auth/register** — `missing-journey`: hooks/queries/useAuth.ts
6. **route:/auth/register/assurance** — `missing-journey`: hooks/queries/useAuth.ts
7. **route:/auth/register/status** — `missing-journey`: hooks/queries/useAuth.ts
8. **route:/privacy** — `unclear-intent`: hooks/queries/usePrivacyPreferences.ts
9. **route:/terms** — `isolated-page`: Terms of Use
10. **route:/account-deletion** — `isolated-page`: Account Deletion
11. **route:/privacy/app-stores** — `unclear-intent`: hooks/queries/usePrivacyPreferences.ts
12. **route:/clinical** — `missing-journey`: hooks/queries/useClinicalCuration.ts
13. **route:/clinical-tools** — `missing-journey`: Clinical Tools
14. **route:/clinical-tools/rules** — `missing-journey`: Rules Engine
15. **route:/clinical-tools/forms** — `mock-stub`: Form Builder
16. **route:/clinical/control-tower** — `missing-journey`: hooks/queries/useClinicalCuration.ts
17. **route:/clinical/dictation** — `missing-journey`: hooks/queries/useClinicalCuration.ts
18. **route:/clinical/emergency** — `missing-journey`: hooks/queries/useClinicalCuration.ts
19. **route:/clinical/inpatient** — `missing-journey`: hooks/queries/useClinicalCuration.ts
20. **route:/clinical/inpatient/admissions** — `missing-journey`: hooks/queries/useClinicalCuration.ts

## Recommended first orchestration-completion batch

1. **Context activation chain** — Login → facility → workspace → shift headers before clinical routes
2. **Core transaction next-actions** — Every doctrine/journey page exposes next action from BFF timeline
3. **Patient search → encounter entry** — Queue search opens chart with transaction_id correlation
4. **Nompilo route context** — Pass pathname + transaction context to /ask and handoff endpoints
5. **Register orphan pages** — 27 unregistered pages into routes.ts with guard/sidebar
6. **Mobile provider encounter shell** — Align provider-app encounter with web encounter journey
7. **Finance payer-ops wiring** — Replace stub adapters with typed BFF fail-close envelopes
8. **Wellness routes map** — Complete wellness map surface (currently coming-soon)
