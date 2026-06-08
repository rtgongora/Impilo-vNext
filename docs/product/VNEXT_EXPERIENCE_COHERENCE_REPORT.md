# vNext Experience Coherence Report

> Generated: 2026-06-08T14:34:53.641Z

| # | Metric | Value |
|---|--------|------:|
| 1 | Total frontend routes | 496 |
| 2 | Routes mapped to actor/context/intent/transaction | 401 |
| 3 | Orphan routes | 112 |
| 4 | Backend capabilities without journeys | 31 |
| 5 | Mock/stub/placeholder routes | 7 |
| 6 | Isolated pages | 43 |
| 7 | Incomplete transaction journeys | 1 |
| 8 | Mobile surfaces disconnected | 46 |

## Top 20 coherence gaps

1. **route:/auth/forgot-password** — `unclear-intent`: hooks/queries/useAuth.ts
2. **route:/auth/reset-password** — `unclear-intent`: hooks/queries/useAuth.ts
3. **route:/auth/logout** — `unclear-intent`: hooks/queries/useAuth.ts
4. **route:/privacy** — `unclear-intent`: hooks/queries/usePrivacyPreferences.ts
5. **route:/terms** — `isolated-page`: Terms of Use
6. **route:/account-deletion** — `isolated-page`: Account Deletion
7. **route:/privacy/app-stores** — `unclear-intent`: hooks/queries/usePrivacyPreferences.ts
8. **route:/clinical-tools** — `missing-journey`: Clinical Tools
9. **route:/clinical-tools/rules** — `missing-journey`: Rules Engine
10. **route:/clinical-tools/forms** — `mock-stub`: Form Builder
11. **route:/production-command-centre** — `isolated-page`: Production Command Centre
12. **route:/health-os/command-centre** — `isolated-page`: Health OS Command Centre
13. **route:/data-intelligence** — `missing-journey`: Data & Intelligence
14. **route:/data-intelligence/quality** — `missing-journey`: Data Quality
15. **route:/data-intelligence/pipelines** — `missing-journey`: Data Pipelines
16. **route:/data-intelligence/integration** — `missing-journey`: Integration Monitor
17. **route:/data-intelligence/reports** — `missing-journey`: Reporting Hub
18. **route:/data-intelligence/audit** — `missing-journey`: Audit Intelligence
19. **route:/public-health** — `missing-journey`: Public Health
20. **route:/public-health/surveillance** — `missing-journey`: Surveillance

## Recommended first orchestration-completion batch

1. **Context activation chain** — Login → facility → workspace → shift headers before clinical routes
2. **Core transaction next-actions** — Every doctrine/journey page exposes next action from BFF timeline
3. **Patient search → encounter entry** — Queue search opens chart with transaction_id correlation
4. **Nompilo route context** — Pass pathname + transaction context to /ask and handoff endpoints
5. **Register orphan pages** — 27 unregistered pages into routes.ts with guard/sidebar
6. **Mobile provider encounter shell** — Align provider-app encounter with web encounter journey
7. **Finance payer-ops wiring** — Replace stub adapters with typed BFF fail-close envelopes
8. **Wellness routes map** — Complete wellness map surface (currently coming-soon)
