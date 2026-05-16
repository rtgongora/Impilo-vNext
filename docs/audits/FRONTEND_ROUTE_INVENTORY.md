# Frontend Route Inventory

## Scope

- Web: `ui/one-ui-shell/src/app/**/page.tsx`
- Mobile: `apps/mobile/**/screens/*Screen*.tsx`

## Web Route Classification (high-value surfaces)

| Route | Classification | Data Source | Notes |
|---|---|---|---|
| `/core-transaction` | FIXTURE_BACKED | `features/core-transaction/fixtures/*` | Honesty label added in this cycle. |
| `/client-journey` | FIXTURE_BACKED | fixture | Honesty label added. |
| `/provider-workspace` | FIXTURE_BACKED | fixture | Honesty label added. |
| `/platform-journey` | FIXTURE_BACKED | fixture | Honesty label added. |
| `/queue` | REAL_WIRED | `useQueueEntries` / BFF | Real queue orchestration path. |
| `/queue/triage` | REAL_WIRED | BFF POST triage | Tested route. |
| `/ehr/[patientId]/*` | PARTIALLY_WIRED | mixed hooks | Large set; many real hooks with some local derived/demo sub-panels. |
| `/telemedicine` | REAL_WIRED | telemedicine hooks | Web real, parity partial. |
| `/finance/*` | PARTIALLY_WIRED | finance hooks/BFF | Mixed maturity across claims/costing/payment details. |
| `/reports/*` | PARTIALLY_WIRED | reporting hooks/BFF | Mixed between live and partial placeholders. |
| `/support/*` | PARTIALLY_WIRED | mixed | Combined with separate support-console app. |

## Mobile Screen Classification (high-value surfaces)

| Mobile surface | Classification | Data Source | Notes |
|---|---|---|---|
| Provider Queue/Tasks | REAL_WIRED | provider BFF APIs | Core workflow available. |
| Provider Telemedicine | PARTIALLY_WIRED | mobile provider telemedicine APIs | Real path, still maturity gaps. |
| Citizen Wallet/Coverage | PARTIALLY_WIRED | mobile citizen APIs | Real + some incomplete states. |
| Citizen Conditions | PLACEHOLDER | TODO section | Explicit not-wired label added. |
| Citizen Allergies | PLACEHOLDER | TODO section | Explicit not-wired label added. |
| Citizen Provider Discovery | PLACEHOLDER | TODO section | Explicit not-wired label added. |
| Mobile Nompilo command/search | UNKNOWN/PARTIAL | scattered | No complete parity with web command surface. |

## Reachability Notes

- `/home/referrals` route is now explicitly registered in route registry.
- Doctrine pages are reachable but fixture-backed and now clearly marked.
- Some backend capabilities remain backend-only (see `BACKEND_NOT_SURFACED_REGISTER.md`).
