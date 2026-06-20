# Product Truth — Gap Register

> Generated: 2026-06-20T11:02:04.418Z
> Total gaps: **232**

## Gap categories (A–R)

| Cat | Description | Count |
|-----|-------------|------:|
| A | Backend exists, UI missing | 0 |
| B | Backend exists, BFF missing | 0 |
| C | Backend exists, contract missing/stale | 2 |
| D | Backend exists, frontend only partially wired | 134 |
| E | UI exists, backend missing | 91 |
| F | UI exists, uses mock/stub/fixture data | 0 |
| G | UI exists, form submits but does not persist | 0 |
| H | UI exists, button/card is dead or decorative | 0 |
| I | API exists, database persistence missing | 0 |
| J | Database exists, API missing | 0 |
| K | Contract exists, implementation missing | 0 |
| L | BFF exists, downstream service not wired | 0 |
| M | Mobile parity missing | 0 |
| N | Auth/policy/tenant scoping missing | 0 |
| O | Tests missing | 5 |
| P | Workflow incomplete across services | 0 |
| Q | Service is internal-only and needs documentation instead of UI | 0 |
| R | Duplicate/overlapping capability requiring consolidation | 0 |

## By severity

| Severity | Count |
|----------|------:|
| high | 91 |
| medium | 14 |
| low | 127 |

## Prioritized gaps (top 100)

| Rank | Entity | Category | Severity | Description |
|------|--------|----------|----------|-------------|
| 1 | / | E | high | /: no BFF/API backing detected |
| 2 | /home/referrals | E | high | /home/referrals: no BFF/API backing detected |
| 3 | /citizen | E | high | /citizen: no BFF/API backing detected |
| 4 | /ehr/[patientId]/referrals | E | high | /ehr/[patientId]/referrals: no BFF/API backing detected |
| 5 | /ehr/[patientId]/teleconsults | E | high | /ehr/[patientId]/teleconsults: no BFF/API backing detected |
| 6 | /admin | E | high | /admin: no BFF/API backing detected |
| 7 | /admin/keys | E | high | /admin/keys: no BFF/API backing detected |
| 8 | /admin/federation | E | high | /admin/federation: no BFF/API backing detected |
| 9 | /admin/sidecar-retirement | E | high | /admin/sidecar-retirement: no BFF/API backing detected |
| 10 | /dags | E | high | /dags: no BFF/API backing detected |
| 11 | /dags/policy | E | high | /dags/policy: no BFF/API backing detected |
| 12 | /organization-admin/staffing | E | high | /organization-admin/staffing: no BFF/API backing detected |
| 13 | /registry/trust | E | high | /registry/trust: no BFF/API backing detected |
| 14 | /marketplace/vendor | E | high | /marketplace/vendor: no BFF/API backing detected |
| 15 | /finance | E | high | /finance: no BFF/API backing detected |
| 16 | /finance/commerce-integrations | E | high | /finance/commerce-integrations: no BFF/API backing detected |
| 17 | /pharmacy | E | high | /pharmacy: no BFF/API backing detected |
| 18 | /enterprise | E | high | /enterprise: no BFF/API backing detected |
| 19 | /enterprise/oversight | E | high | /enterprise/oversight: no BFF/API backing detected |
| 20 | /erp | E | high | /erp: no BFF/API backing detected |
| 21 | /settings | E | high | /settings: no BFF/API backing detected |
| 22 | /wellness/commodities | E | high | /wellness/commodities: no BFF/API backing detected |
| 23 | /caregiving/dependants | E | high | /caregiving/dependants: no BFF/API backing detected |
| 24 | /caregiving/delegation | E | high | /caregiving/delegation: no BFF/API backing detected |
| 25 | /caregiving/tasks | E | high | /caregiving/tasks: no BFF/API backing detected |
| 26 | /caregiving/notifications | E | high | /caregiving/notifications: no BFF/API backing detected |
| 27 | /monitoring | E | high | /monitoring: no BFF/API backing detected |
| 28 | /monitoring/care-plans | E | high | /monitoring/care-plans: no BFF/API backing detected |
| 29 | /monitoring/provider-dashboard | E | high | /monitoring/provider-dashboard: no BFF/API backing detected |
| 30 | /discover | E | high | /discover: no BFF/API backing detected |
| 31 | /discover/providers | E | high | /discover/providers: no BFF/API backing detected |
| 32 | /discover/facilities | E | high | /discover/facilities: no BFF/API backing detected |
| 33 | /discover/services | E | high | /discover/services: no BFF/API backing detected |
| 34 | /operations | E | high | /operations: no BFF/API backing detected |
| 35 | /operations/facility-operations | E | high | /operations/facility-operations: no BFF/API backing detected |
| 36 | /operations/equipment | E | high | /operations/equipment: no BFF/API backing detected |
| 37 | /support | E | high | /support: no BFF/API backing detected |
| 38 | /support/knowledge-base | E | high | /support/knowledge-base: no BFF/API backing detected |
| 39 | /developer | E | high | /developer: no BFF/API backing detected |
| 40 | /developer/api-catalog | E | high | /developer/api-catalog: no BFF/API backing detected |
| 41 | /developer/clients | E | high | /developer/clients: no BFF/API backing detected |
| 42 | /shell/file-manager | E | high | /shell/file-manager: no BFF/API backing detected |
| 43 | /shell/task-manager | E | high | /shell/task-manager: no BFF/API backing detected |
| 44 | /guidance | E | high | /guidance: no BFF/API backing detected |
| 45 | /guidance/reminders | E | high | /guidance/reminders: no BFF/API backing detected |
| 46 | /guidance/education | E | high | /guidance/education: no BFF/API backing detected |
| 47 | /learning/library | E | high | /learning/library: no BFF/API backing detected |
| 48 | /learning/library/[resourceId] | E | high | /learning/library/[resourceId]: no BFF/API backing detected |
| 49 | /learning/surveys/[surveyId] | E | high | /learning/surveys/[surveyId]: no BFF/API backing detected |
| 50 | /learning/admin | E | high | /learning/admin: no BFF/API backing detected |
| 51 | /learning/admin/assessments | E | high | /learning/admin/assessments: no BFF/API backing detected |
| 52 | /nhume | E | high | /nhume: no BFF/API backing detected |
| 53 | /nhume/dashboard | E | high | /nhume/dashboard: no BFF/API backing detected |
| 54 | /nhume/deliveries | E | high | /nhume/deliveries: no BFF/API backing detected |
| 55 | /nhume/deliveries/[deliveryId] | E | high | /nhume/deliveries/[deliveryId]: no BFF/API backing detected |
| 56 | /nhume/dispatcher | E | high | /nhume/dispatcher: no BFF/API backing detected |
| 57 | /nhume/map | E | high | /nhume/map: no BFF/API backing detected |
| 58 | /nhume/courier | E | high | /nhume/courier: no BFF/API backing detected |
| 59 | /nhume/fleet | E | high | /nhume/fleet: no BFF/API backing detected |
| 60 | /nhume/fleet/[assetId] | E | high | /nhume/fleet/[assetId]: no BFF/API backing detected |
| 61 | /nhume/couriers | E | high | /nhume/couriers: no BFF/API backing detected |
| 62 | /nhume/couriers/[courierId] | E | high | /nhume/couriers/[courierId]: no BFF/API backing detected |
| 63 | /nhume/policies | E | high | /nhume/policies: no BFF/API backing detected |
| 64 | /nhume/autonomous | E | high | /nhume/autonomous: no BFF/API backing detected |
| 65 | /nhume/analytics | E | high | /nhume/analytics: no BFF/API backing detected |
| 66 | /nhume/custody/[deliveryId] | E | high | /nhume/custody/[deliveryId]: no BFF/API backing detected |
| 67 | /nhume/track/[deliveryId] | E | high | /nhume/track/[deliveryId]: no BFF/API backing detected |
| 68 | /madi | E | high | /madi: no BFF/API backing detected |
| 69 | /madi/donor/drives | E | high | /madi/donor/drives: no BFF/API backing detected |
| 70 | /madi/drives | E | high | /madi/drives: no BFF/API backing detected |
| 71 | /madi/drives/[driveId] | E | high | /madi/drives/[driveId]: no BFF/API backing detected |
| 72 | /madi/blood-bank | E | high | /madi/blood-bank: no BFF/API backing detected |
| 73 | /madi/blood-bank/orders | E | high | /madi/blood-bank/orders: no BFF/API backing detected |
| 74 | /madi/blood-bank/stock | E | high | /madi/blood-bank/stock: no BFF/API backing detected |
| 75 | /madi/blood-bank/crossmatch | E | high | /madi/blood-bank/crossmatch: no BFF/API backing detected |
| 76 | /madi/blood-bank/issue | E | high | /madi/blood-bank/issue: no BFF/API backing detected |
| 77 | /madi/blood-bank/fridges | E | high | /madi/blood-bank/fridges: no BFF/API backing detected |
| 78 | /madi/orders/[orderId] | E | high | /madi/orders/[orderId]: no BFF/API backing detected |
| 79 | /madi/haemovigilance/national | E | high | /madi/haemovigilance/national: no BFF/API backing detected |
| 80 | /madi/dashboard | E | high | /madi/dashboard: no BFF/API backing detected |
| 81 | /madi/logistics | E | high | /madi/logistics: no BFF/API backing detected |
| 82 | /live | E | high | /live: no BFF/API backing detected |
| 83 | /live/manage | E | high | /live/manage: no BFF/API backing detected |
| 84 | /live/admin | E | high | /live/admin: no BFF/API backing detected |
| 85 | /live/create | E | high | /live/create: no BFF/API backing detected |
| 86 | /live/discover | E | high | /live/discover: no BFF/API backing detected |
| 87 | /live/cpd | E | high | /live/cpd: no BFF/API backing detected |
| 88 | /live/event/[eventId] | E | high | /live/event/[eventId]: no BFF/API backing detected |
| 89 | /live/event/[eventId]/room | E | high | /live/event/[eventId]/room: no BFF/API backing detected |
| 90 | /live/event/[eventId]/replay | E | high | /live/event/[eventId]/replay: no BFF/API backing detected |
| 91 | /live/event/[eventId]/analytics | E | high | /live/event/[eventId]/analytics: no BFF/API backing detected |
| 92 | butano-fhir | D | medium | butano-fhir: partial frontend/BFF wiring |
| 93 | general-ledger-service | D | medium | general-ledger-service: partial frontend/BFF wiring |
| 94 | general-ledger-service | O | medium | general-ledger-service: no automated tests detected |
| 95 | guidance-service | O | medium | guidance-service: no automated tests detected |
| 96 | hr-payroll-service | O | medium | hr-payroll-service: no automated tests detected |
| 97 | msika-apps-service | D | medium | msika-apps-service: partial frontend/BFF wiring |
| 98 | msika-flow-service | D | medium | msika-flow-service: partial frontend/BFF wiring |
| 99 | mushe-wallet-service | O | medium | mushe-wallet-service: no automated tests detected |
| 100 | nhume-service | C | medium | nhume-service: no matched OpenAPI contract |

## Services requiring product-owner decision

_None flagged as blocker requiring immediate PO decision._
