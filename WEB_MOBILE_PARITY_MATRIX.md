# Web / Mobile Parity Matrix

> **Authoritative detail:** [docs/frontend/WEB_MOBILE_SURFACING_PARITY.md](docs/frontend/WEB_MOBILE_SURFACING_PARITY.md) (parity sweep 2026-05-28).

Legend: `Complete`, `Partial`, `Missing`, `N/A`

| Capability | Web | Citizen app | Provider app | Notes |
|---|---|---|---|---|
| Auth/session baseline | Partial | Partial | Partial | Build-stable; full production auth hardening still needed |
| Trust context propagation | Partial | Partial | Partial | Headers available via clients, enforcement varies |
| Social feed/timeline | Complete | Complete | Complete | Social controllers and mobile screens compile/test |
| Communities/groups/pages | Complete | Complete | Partial | Provider communities/groups available, parity depth still evolving |
| Marketplace launcher | Complete | Complete | Complete | Button/API mismatches fixed on mobile |
| Nhume dispatch tracking | Partial | Partial | Partial | APIs and screens present, full lifecycle coverage pending |
| Ndila mapping/geospatial | Partial | Partial | Partial | SDK package exists; broader UX and map ops still partial |
| Telehealth core pathways | Partial | Partial | Partial | Present but requires deeper runtime validation |
| Comms/messaging | Partial | Partial | Partial | Baseline screens/tests available |
| Nompilo assistant presence | Partial | Partial | Partial | Package/screens wired; provider policy matrix pending |
| Fundo/Learning | Partial | Partial | Partial | Services present; parity and UX depth pending |
| Payments/costing views | Partial | Partial | Partial | MusheX/Costa web modules build; mobile depth partial |
| Public health ops | Partial | Partial | Partial | Backend endpoints compile; front-end surfacing partial |
| Facility Mode cockpit/setup/units/control-tower | Partial | N/A | Partial | Web surfaces live (`/facility/[id]/*`). **Mobile (2026-07-01):** provider-app now has a **Control Tower** tab (`ControlTowerScreen` + `controlTowerService` → live `/internal/v1/facility-mode/control-tower/{aggregate,alerts,acknowledge}`) — GAP-19 partially closed; setup/units/service-points/regulators still Missing on mobile. |
| Facility↔regulator (multi-council) | Partial | N/A | Missing | Web `/facility/[id]/regulators` live; mobile Missing (GAP-19) |
| Indawo place mode / surveillance / outbreaks / field-teams | Partial | N/A | Partial | Web `/indawo/*` live. **Mobile (2026-07-01):** provider-app now has a **Place Mode** tab (`PlaceModeDashboardScreen` + `placeModeService` → live `/internal/v1/place-mode/{summary,alerts,outbreaks,field-teams}` + create-outbreak + deploy-team) — GAP-19 partially closed. |
| Adaptive Encounter Cockpit (cadre-driven) | Partial | Missing | Partial | Web component renders from cadre decision. **Provider mobile (2026-07-01):** `AdaptiveEncounterCockpit` + `cadreDecisionService` → live `POST /internal/v1/encounters/cadre-decision`, rendered as the first "Cockpit" tab in `EncounterScreen` (spine/actions strictly from the decision; disabled actions greyed with reason). Cadre form *content* still Partial (GAP-10). |
| Sorting desk / front door | Partial | Missing | Missing | Visit-type sort step on web; unifying sorting_session Missing (GAP-11); mobile Missing (GAP-19) |
| Patient-facing journey status (queue/check-in/orders/referral/inpatient/outcome) | Partial | Partial | N/A | **GAP-8 reassessed 2026-07-01 (verified against code):** web ships `/citizen/visit/[transactionId]` + `/citizen/inpatient/[admissionRef]` wired to `PatientLaneService`, plus a `/citizen/my-care` at-a-glance index on `/internal/v1/citizen/health-summary`; citizen mobile ships `QueueStatusSection` on `/internal/v1/mobile/citizen/{queue,visit,inpatient}`. **Still Partial:** unified web queue/active-visit-status aggregation and orders/outcome timeline are not yet composed — do not mark Complete. Provider surface N/A (patient-facing journey). |
