# Web/Mobile Frontend Inventory

## Inventory Summary

| Area | Web | Mobile | Shared contract/BFF status |
|---|---|---|---|
| Core Transaction Journey | Present (fixture-backed) | Missing as dedicated journey shell | BFF exists; frontend not wired |
| Provider Workspace/Queue | Real queue/EHR routes | Real provider task/queue screens | Shared concept, different API families |
| Telemedicine | Real web routes | Real mobile surfaces | Partial parity, split endpoint families |
| Payments/Claims/Coverage | Rich finance web routes | Citizen/provider financial surfaces partial | BFF present, mixed completeness |
| Reports/Intelligence | Real reports routes | Mobile reports available via tools | Partial parity |
| Support/Feedback | Web support routes + support-console | Mobile support surfaces | Partial parity |
| Nompilo/Command | Web command-like surfaces, mixed wiring | Limited dedicated equivalent | BFF command endpoints exist, UI incomplete |
| Marketplace/Wellness | Web rich routes | Mobile sections present | Partial parity |

## Classification Legend

- `REAL_WIRED`
- `PARTIALLY_WIRED`
- `FIXTURE_BACKED`
- `DEMO_ONLY`
- `PLACEHOLDER`
- `BROKEN`
- `UNREACHABLE`
- `UNKNOWN`

## Notable Inventory Findings

1. Web doctrine journey pages are fixture-backed (`FIXTURE_BACKED`) and now explicitly labeled.
2. Mobile has explicit TODO clinical screens in citizen app for conditions/allergies/discovery (`PLACEHOLDER`), now honesty-labeled.
3. Backend workflow and dispatch capabilities are `BACKEND_ONLY` currently.
