# Product Truth — Gap Register

> Generated: 2026-06-20T10:28:53.343Z
> Total gaps: **355**

## Gap categories (A–R)

| Cat | Description | Count |
|-----|-------------|------:|
| A | Backend exists, UI missing | 0 |
| B | Backend exists, BFF missing | 0 |
| C | Backend exists, contract missing/stale | 2 |
| D | Backend exists, frontend only partially wired | 134 |
| E | UI exists, backend missing | 129 |
| F | UI exists, uses mock/stub/fixture data | 8 |
| G | UI exists, form submits but does not persist | 10 |
| H | UI exists, button/card is dead or decorative | 0 |
| I | API exists, database persistence missing | 2 |
| J | Database exists, API missing | 0 |
| K | Contract exists, implementation missing | 0 |
| L | BFF exists, downstream service not wired | 0 |
| M | Mobile parity missing | 0 |
| N | Auth/policy/tenant scoping missing | 64 |
| O | Tests missing | 6 |
| P | Workflow incomplete across services | 0 |
| Q | Service is internal-only and needs documentation instead of UI | 0 |
| R | Duplicate/overlapping capability requiring consolidation | 0 |

## By severity

| Severity | Count |
|----------|------:|
| high | 137 |
| medium | 91 |
| low | 127 |

## Prioritized gaps (top 100)

| Rank | Entity | Category | Severity | Description |
|------|--------|----------|----------|-------------|
| 1 | / | E | high | /: no BFF/API backing detected |
| 2 | /home/referrals | E | high | /home/referrals: no BFF/API backing detected |
| 3 | /citizen | E | high | /citizen: no BFF/API backing detected |
| 4 | /citizen/health-id/request | E | high | /citizen/health-id/request: no BFF/API backing detected |
| 5 | /citizen/id-recovery | E | high | /citizen/id-recovery: no BFF/API backing detected |
| 6 | /citizen/delegated-pickup | E | high | /citizen/delegated-pickup: no BFF/API backing detected |
| 7 | /verify/credential | E | high | /verify/credential: no BFF/API backing detected |
| 8 | /queue/waiting | E | high | /queue/waiting: no BFF/API backing detected |
| 9 | /queue/search | E | high | /queue/search: no BFF/API backing detected |
| 10 | /ehr/[patientId]/referrals | E | high | /ehr/[patientId]/referrals: no BFF/API backing detected |
| 11 | /ehr/[patientId]/teleconsults | E | high | /ehr/[patientId]/teleconsults: no BFF/API backing detected |
| 12 | /ehr/[patientId]/charts | E | high | /ehr/[patientId]/charts: no BFF/API backing detected |
| 13 | /admin | E | high | /admin: no BFF/API backing detected |
| 14 | /admin/audit | E | high | /admin/audit: no BFF/API backing detected |
| 15 | /admin/audit/[id] | E | high | /admin/audit/[id]: no BFF/API backing detected |
| 16 | /admin/keys | E | high | /admin/keys: no BFF/API backing detected |
| 17 | /admin/federation | E | high | /admin/federation: no BFF/API backing detected |
| 18 | /admin/sidecar-retirement | E | high | /admin/sidecar-retirement: no BFF/API backing detected |
| 19 | /dags | E | high | /dags: no BFF/API backing detected |
| 20 | /dags/policy | E | high | /dags/policy: no BFF/API backing detected |
| 21 | /organization-admin/staffing | E | high | /organization-admin/staffing: no BFF/API backing detected |
| 22 | /registry/trust | E | high | /registry/trust: no BFF/API backing detected |
| 23 | /registry/providers | E | high | /registry/providers: no BFF/API backing detected |
| 24 | /registry/products | E | high | /registry/products: no BFF/API backing detected |
| 25 | /registry/products/[id] | E | high | /registry/products/[id]: no BFF/API backing detected |
| 26 | /marketplace/vendor | E | high | /marketplace/vendor: no BFF/API backing detected |
| 27 | /marketplace/vendor/orders | E | high | /marketplace/vendor/orders: no BFF/API backing detected |
| 28 | /finance | E | high | /finance: no BFF/API backing detected |
| 29 | /finance/ledger | E | high | /finance/ledger: no BFF/API backing detected |
| 30 | /finance/workspace | E | high | /finance/workspace: no BFF/API backing detected |
| 31 | /finance/refunds | E | high | /finance/refunds: no BFF/API backing detected |
| 32 | /finance/payer-claims | E | high | /finance/payer-claims: no BFF/API backing detected |
| 33 | /finance/payer-claims/[claimId] | E | high | /finance/payer-claims/[claimId]: no BFF/API backing detected |
| 34 | /finance/commerce-integrations | E | high | /finance/commerce-integrations: no BFF/API backing detected |
| 35 | /pharmacy | E | high | /pharmacy: no BFF/API backing detected |
| 36 | /enterprise | E | high | /enterprise: no BFF/API backing detected |
| 37 | /enterprise/oversight | E | high | /enterprise/oversight: no BFF/API backing detected |
| 38 | /erp | E | high | /erp: no BFF/API backing detected |
| 39 | /erp/assets | E | high | /erp/assets: no BFF/API backing detected |
| 40 | /settings | E | high | /settings: no BFF/API backing detected |
| 41 | /wellness/commodities | E | high | /wellness/commodities: no BFF/API backing detected |
| 42 | /caregiving/dependants | E | high | /caregiving/dependants: no BFF/API backing detected |
| 43 | /caregiving/delegation | E | high | /caregiving/delegation: no BFF/API backing detected |
| 44 | /caregiving/tasks | E | high | /caregiving/tasks: no BFF/API backing detected |
| 45 | /caregiving/notifications | E | high | /caregiving/notifications: no BFF/API backing detected |
| 46 | /monitoring | E | high | /monitoring: no BFF/API backing detected |
| 47 | /monitoring/care-plans | E | high | /monitoring/care-plans: no BFF/API backing detected |
| 48 | /monitoring/provider-dashboard | E | high | /monitoring/provider-dashboard: no BFF/API backing detected |
| 49 | /discover | E | high | /discover: no BFF/API backing detected |
| 50 | /discover/providers | E | high | /discover/providers: no BFF/API backing detected |
| 51 | /discover/facilities | E | high | /discover/facilities: no BFF/API backing detected |
| 52 | /discover/services | E | high | /discover/services: no BFF/API backing detected |
| 53 | /lab | E | high | /lab: no BFF/API backing detected |
| 54 | /lab/worklist | E | high | /lab/worklist: no BFF/API backing detected |
| 55 | /imaging/worklist | E | high | /imaging/worklist: no BFF/API backing detected |
| 56 | /imaging/facility | E | high | /imaging/facility: no BFF/API backing detected |
| 57 | /lab/catalog | E | high | /lab/catalog: no BFF/API backing detected |
| 58 | /lab/reconciliation | E | high | /lab/reconciliation: no BFF/API backing detected |
| 59 | /operations | E | high | /operations: no BFF/API backing detected |
| 60 | /operations/facility-operations | E | high | /operations/facility-operations: no BFF/API backing detected |
| 61 | /operations/vito/match | E | high | /operations/vito/match: no BFF/API backing detected |
| 62 | /operations/assets | E | high | /operations/assets: no BFF/API backing detected |
| 63 | /operations/equipment | E | high | /operations/equipment: no BFF/API backing detected |
| 64 | /support | E | high | /support: no BFF/API backing detected |
| 65 | /support/knowledge-base | E | high | /support/knowledge-base: no BFF/API backing detected |
| 66 | /developer | E | high | /developer: no BFF/API backing detected |
| 67 | /developer/api-catalog | E | high | /developer/api-catalog: no BFF/API backing detected |
| 68 | /developer/clients | E | high | /developer/clients: no BFF/API backing detected |
| 69 | /marketplace/substitutions | E | high | /marketplace/substitutions: no BFF/API backing detected |
| 70 | /shell/file-manager | E | high | /shell/file-manager: no BFF/API backing detected |
| 71 | /shell/task-manager | E | high | /shell/task-manager: no BFF/API backing detected |
| 72 | /intelligence | E | high | /intelligence: no BFF/API backing detected |
| 73 | /search | E | high | /search: no BFF/API backing detected |
| 74 | /guidance | E | high | /guidance: no BFF/API backing detected |
| 75 | /guidance/reminders | E | high | /guidance/reminders: no BFF/API backing detected |
| 76 | /guidance/education | E | high | /guidance/education: no BFF/API backing detected |
| 77 | /learning/studio/courses | E | high | /learning/studio/courses: no BFF/API backing detected |
| 78 | /learning/library | E | high | /learning/library: no BFF/API backing detected |
| 79 | /learning/library/[resourceId] | E | high | /learning/library/[resourceId]: no BFF/API backing detected |
| 80 | /learning/surveys/[surveyId] | E | high | /learning/surveys/[surveyId]: no BFF/API backing detected |
| 81 | /learning/admin | E | high | /learning/admin: no BFF/API backing detected |
| 82 | /learning/admin/courses | E | high | /learning/admin/courses: no BFF/API backing detected |
| 83 | /learning/admin/assessments | E | high | /learning/admin/assessments: no BFF/API backing detected |
| 84 | /nhume | E | high | /nhume: no BFF/API backing detected |
| 85 | /nhume/dashboard | E | high | /nhume/dashboard: no BFF/API backing detected |
| 86 | /nhume/deliveries | E | high | /nhume/deliveries: no BFF/API backing detected |
| 87 | /nhume/deliveries/new | E | high | /nhume/deliveries/new: no BFF/API backing detected |
| 88 | /nhume/deliveries/[deliveryId] | E | high | /nhume/deliveries/[deliveryId]: no BFF/API backing detected |
| 89 | /nhume/dispatcher | E | high | /nhume/dispatcher: no BFF/API backing detected |
| 90 | /nhume/map | E | high | /nhume/map: no BFF/API backing detected |
| 91 | /nhume/courier | E | high | /nhume/courier: no BFF/API backing detected |
| 92 | /nhume/fleet | E | high | /nhume/fleet: no BFF/API backing detected |
| 93 | /nhume/fleet/[assetId] | E | high | /nhume/fleet/[assetId]: no BFF/API backing detected |
| 94 | /nhume/couriers | E | high | /nhume/couriers: no BFF/API backing detected |
| 95 | /nhume/couriers/[courierId] | E | high | /nhume/couriers/[courierId]: no BFF/API backing detected |
| 96 | /nhume/policies | E | high | /nhume/policies: no BFF/API backing detected |
| 97 | /nhume/autonomous | E | high | /nhume/autonomous: no BFF/API backing detected |
| 98 | /nhume/analytics | E | high | /nhume/analytics: no BFF/API backing detected |
| 99 | /nhume/custody/[deliveryId] | E | high | /nhume/custody/[deliveryId]: no BFF/API backing detected |
| 100 | /nhume/track/[deliveryId] | E | high | /nhume/track/[deliveryId]: no BFF/API backing detected |

## Services requiring product-owner decision

_None flagged as blocker requiring immediate PO decision._
