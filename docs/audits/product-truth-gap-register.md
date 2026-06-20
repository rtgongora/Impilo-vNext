# Product Truth — Gap Register

> Generated: 2026-06-20T11:32:53.006Z
> Total gaps: **203**

## Gap categories (A–R)

| Cat | Description | Count |
|-----|-------------|------:|
| A | Backend exists, UI missing | 0 |
| B | Backend exists, BFF missing | 0 |
| C | Backend exists, contract missing/stale | 2 |
| D | Backend exists, frontend only partially wired | 134 |
| E | UI exists, backend missing | 67 |
| F | UI exists, uses mock/stub/fixture data | 0 |
| G | UI exists, form submits but does not persist | 0 |
| H | UI exists, button/card is dead or decorative | 0 |
| I | API exists, database persistence missing | 0 |
| J | Database exists, API missing | 0 |
| K | Contract exists, implementation missing | 0 |
| L | BFF exists, downstream service not wired | 0 |
| M | Mobile parity missing | 0 |
| N | Auth/policy/tenant scoping missing | 0 |
| O | Tests missing | 0 |
| P | Workflow incomplete across services | 0 |
| Q | Service is internal-only and needs documentation instead of UI | 0 |
| R | Duplicate/overlapping capability requiring consolidation | 0 |

## By severity

| Severity | Count |
|----------|------:|
| high | 67 |
| medium | 9 |
| low | 127 |

## Prioritized gaps (top 100)

| Rank | Entity | Category | Severity | Description |
|------|--------|----------|----------|-------------|
| 1 | /admin/keys | E | high | /admin/keys: no BFF/API backing detected |
| 2 | /admin/federation | E | high | /admin/federation: no BFF/API backing detected |
| 3 | /admin/sidecar-retirement | E | high | /admin/sidecar-retirement: no BFF/API backing detected |
| 4 | /dags | E | high | /dags: no BFF/API backing detected |
| 5 | /dags/policy | E | high | /dags/policy: no BFF/API backing detected |
| 6 | /marketplace/vendor | E | high | /marketplace/vendor: no BFF/API backing detected |
| 7 | /finance/commerce-integrations | E | high | /finance/commerce-integrations: no BFF/API backing detected |
| 8 | /enterprise | E | high | /enterprise: no BFF/API backing detected |
| 9 | /enterprise/oversight | E | high | /enterprise/oversight: no BFF/API backing detected |
| 10 | /wellness/commodities | E | high | /wellness/commodities: no BFF/API backing detected |
| 11 | /caregiving/dependants | E | high | /caregiving/dependants: no BFF/API backing detected |
| 12 | /caregiving/delegation | E | high | /caregiving/delegation: no BFF/API backing detected |
| 13 | /caregiving/tasks | E | high | /caregiving/tasks: no BFF/API backing detected |
| 14 | /caregiving/notifications | E | high | /caregiving/notifications: no BFF/API backing detected |
| 15 | /monitoring/care-plans | E | high | /monitoring/care-plans: no BFF/API backing detected |
| 16 | /monitoring/provider-dashboard | E | high | /monitoring/provider-dashboard: no BFF/API backing detected |
| 17 | /discover/providers | E | high | /discover/providers: no BFF/API backing detected |
| 18 | /discover/facilities | E | high | /discover/facilities: no BFF/API backing detected |
| 19 | /discover/services | E | high | /discover/services: no BFF/API backing detected |
| 20 | /operations/facility-operations | E | high | /operations/facility-operations: no BFF/API backing detected |
| 21 | /operations/equipment | E | high | /operations/equipment: no BFF/API backing detected |
| 22 | /support/knowledge-base | E | high | /support/knowledge-base: no BFF/API backing detected |
| 23 | /developer/api-catalog | E | high | /developer/api-catalog: no BFF/API backing detected |
| 24 | /developer/clients | E | high | /developer/clients: no BFF/API backing detected |
| 25 | /guidance/reminders | E | high | /guidance/reminders: no BFF/API backing detected |
| 26 | /guidance/education | E | high | /guidance/education: no BFF/API backing detected |
| 27 | /learning/library | E | high | /learning/library: no BFF/API backing detected |
| 28 | /learning/library/[resourceId] | E | high | /learning/library/[resourceId]: no BFF/API backing detected |
| 29 | /learning/surveys/[surveyId] | E | high | /learning/surveys/[surveyId]: no BFF/API backing detected |
| 30 | /learning/admin | E | high | /learning/admin: no BFF/API backing detected |
| 31 | /learning/admin/assessments | E | high | /learning/admin/assessments: no BFF/API backing detected |
| 32 | /nhume/dashboard | E | high | /nhume/dashboard: no BFF/API backing detected |
| 33 | /nhume/deliveries | E | high | /nhume/deliveries: no BFF/API backing detected |
| 34 | /nhume/deliveries/[deliveryId] | E | high | /nhume/deliveries/[deliveryId]: no BFF/API backing detected |
| 35 | /nhume/dispatcher | E | high | /nhume/dispatcher: no BFF/API backing detected |
| 36 | /nhume/map | E | high | /nhume/map: no BFF/API backing detected |
| 37 | /nhume/courier | E | high | /nhume/courier: no BFF/API backing detected |
| 38 | /nhume/fleet | E | high | /nhume/fleet: no BFF/API backing detected |
| 39 | /nhume/fleet/[assetId] | E | high | /nhume/fleet/[assetId]: no BFF/API backing detected |
| 40 | /nhume/couriers | E | high | /nhume/couriers: no BFF/API backing detected |
| 41 | /nhume/couriers/[courierId] | E | high | /nhume/couriers/[courierId]: no BFF/API backing detected |
| 42 | /nhume/policies | E | high | /nhume/policies: no BFF/API backing detected |
| 43 | /nhume/autonomous | E | high | /nhume/autonomous: no BFF/API backing detected |
| 44 | /nhume/analytics | E | high | /nhume/analytics: no BFF/API backing detected |
| 45 | /nhume/custody/[deliveryId] | E | high | /nhume/custody/[deliveryId]: no BFF/API backing detected |
| 46 | /nhume/track/[deliveryId] | E | high | /nhume/track/[deliveryId]: no BFF/API backing detected |
| 47 | /madi/donor/drives | E | high | /madi/donor/drives: no BFF/API backing detected |
| 48 | /madi/drives | E | high | /madi/drives: no BFF/API backing detected |
| 49 | /madi/drives/[driveId] | E | high | /madi/drives/[driveId]: no BFF/API backing detected |
| 50 | /madi/blood-bank/orders | E | high | /madi/blood-bank/orders: no BFF/API backing detected |
| 51 | /madi/blood-bank/stock | E | high | /madi/blood-bank/stock: no BFF/API backing detected |
| 52 | /madi/blood-bank/crossmatch | E | high | /madi/blood-bank/crossmatch: no BFF/API backing detected |
| 53 | /madi/blood-bank/issue | E | high | /madi/blood-bank/issue: no BFF/API backing detected |
| 54 | /madi/blood-bank/fridges | E | high | /madi/blood-bank/fridges: no BFF/API backing detected |
| 55 | /madi/orders/[orderId] | E | high | /madi/orders/[orderId]: no BFF/API backing detected |
| 56 | /madi/haemovigilance/national | E | high | /madi/haemovigilance/national: no BFF/API backing detected |
| 57 | /madi/dashboard | E | high | /madi/dashboard: no BFF/API backing detected |
| 58 | /madi/logistics | E | high | /madi/logistics: no BFF/API backing detected |
| 59 | /live/manage | E | high | /live/manage: no BFF/API backing detected |
| 60 | /live/admin | E | high | /live/admin: no BFF/API backing detected |
| 61 | /live/create | E | high | /live/create: no BFF/API backing detected |
| 62 | /live/discover | E | high | /live/discover: no BFF/API backing detected |
| 63 | /live/cpd | E | high | /live/cpd: no BFF/API backing detected |
| 64 | /live/event/[eventId] | E | high | /live/event/[eventId]: no BFF/API backing detected |
| 65 | /live/event/[eventId]/room | E | high | /live/event/[eventId]/room: no BFF/API backing detected |
| 66 | /live/event/[eventId]/replay | E | high | /live/event/[eventId]/replay: no BFF/API backing detected |
| 67 | /live/event/[eventId]/analytics | E | high | /live/event/[eventId]/analytics: no BFF/API backing detected |
| 68 | butano-fhir | D | medium | butano-fhir: partial frontend/BFF wiring |
| 69 | general-ledger-service | D | medium | general-ledger-service: partial frontend/BFF wiring |
| 70 | msika-apps-service | D | medium | msika-apps-service: partial frontend/BFF wiring |
| 71 | msika-flow-service | D | medium | msika-flow-service: partial frontend/BFF wiring |
| 72 | nhume-service | C | medium | nhume-service: no matched OpenAPI contract |
| 73 | tshepo-audit-service | D | medium | tshepo-audit-service: partial frontend/BFF wiring |
| 74 | tshepo-identity-service | D | medium | tshepo-identity-service: partial frontend/BFF wiring |
| 75 | tshepo-offline-service | D | medium | tshepo-offline-service: partial frontend/BFF wiring |
| 76 | vashandi-workforce-service | C | medium | vashandi-workforce-service: no matched OpenAPI contract |
| 77 | /access/governance | D | low | Unregistered route |
| 78 | /clinical/emergency/[visitId] | D | low | Unregistered route |
| 79 | /developer/event-catalogue | D | low | Unregistered route |
| 80 | /ehr/[patientId]/charts/[chartId] | D | low | Unregistered route |
| 81 | /ehr/[patientId]/emergency | D | low | Unregistered route |
| 82 | /ehr/[patientId]/procedures/[episodeId] | D | low | Unregistered route |
| 83 | /ehr/[patientId]/workspace/[specialty] | D | low | Unregistered route |
| 84 | /groups/[id] | D | low | Unregistered route |
| 85 | /groups | D | low | Unregistered route |
| 86 | /inventory/items | D | low | Unregistered route |
| 87 | /inventory/reconciliation | D | low | Unregistered route |
| 88 | /inventory/stock | D | low | Unregistered route |
| 89 | /landela | D | low | Unregistered route |
| 90 | /learning/admin/moderation | D | low | Unregistered route |
| 91 | /marketplace/apps/[itemCode] | D | low | Unregistered route |
| 92 | /marketplace/apps/admin/activation | D | low | Unregistered route |
| 93 | /marketplace/apps/admin/installations | D | low | Unregistered route |
| 94 | /marketplace/apps/integration | D | low | Unregistered route |
| 95 | /marketplace/apps | D | low | Unregistered route |
| 96 | /page.tsx | D | low | Unregistered route |
| 97 | /professional | D | low | Unregistered route |
| 98 | /registry/facilities/[id]/edit | D | low | Unregistered route |
| 99 | /registry/facilities/new | D | low | Unregistered route |
| 100 | /registry/providers/[id]/edit | D | low | Unregistered route |

## Services requiring product-owner decision

_None flagged as blocker requiring immediate PO decision._
