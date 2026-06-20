# Product Truth — Gap Register

> Generated: 2026-06-20T11:50:37.471Z
> Total gaps: **69**

## Gap categories (A–R)

| Cat | Description | Count |
|-----|-------------|------:|
| A | Backend exists, UI missing | 0 |
| B | Backend exists, BFF missing | 0 |
| C | Backend exists, contract missing/stale | 0 |
| D | Backend exists, frontend only partially wired | 26 |
| E | UI exists, backend missing | 43 |
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
| high | 43 |
| low | 26 |

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
| 10 | /caregiving/dependants | E | high | /caregiving/dependants: no BFF/API backing detected |
| 11 | /caregiving/delegation | E | high | /caregiving/delegation: no BFF/API backing detected |
| 12 | /caregiving/tasks | E | high | /caregiving/tasks: no BFF/API backing detected |
| 13 | /caregiving/notifications | E | high | /caregiving/notifications: no BFF/API backing detected |
| 14 | /monitoring/care-plans | E | high | /monitoring/care-plans: no BFF/API backing detected |
| 15 | /monitoring/provider-dashboard | E | high | /monitoring/provider-dashboard: no BFF/API backing detected |
| 16 | /discover/providers | E | high | /discover/providers: no BFF/API backing detected |
| 17 | /discover/facilities | E | high | /discover/facilities: no BFF/API backing detected |
| 18 | /discover/services | E | high | /discover/services: no BFF/API backing detected |
| 19 | /operations/equipment | E | high | /operations/equipment: no BFF/API backing detected |
| 20 | /support/knowledge-base | E | high | /support/knowledge-base: no BFF/API backing detected |
| 21 | /developer/api-catalog | E | high | /developer/api-catalog: no BFF/API backing detected |
| 22 | /developer/clients | E | high | /developer/clients: no BFF/API backing detected |
| 23 | /guidance/reminders | E | high | /guidance/reminders: no BFF/API backing detected |
| 24 | /guidance/education | E | high | /guidance/education: no BFF/API backing detected |
| 25 | /learning/library | E | high | /learning/library: no BFF/API backing detected |
| 26 | /learning/library/[resourceId] | E | high | /learning/library/[resourceId]: no BFF/API backing detected |
| 27 | /learning/surveys/[surveyId] | E | high | /learning/surveys/[surveyId]: no BFF/API backing detected |
| 28 | /learning/admin | E | high | /learning/admin: no BFF/API backing detected |
| 29 | /learning/admin/assessments | E | high | /learning/admin/assessments: no BFF/API backing detected |
| 30 | /madi/logistics | E | high | /madi/logistics: no BFF/API backing detected |
| 31 | /work/administration-governance/onboard/citizen | E | high | /work/administration-governance/onboard/citizen: no BFF/API backing detected |
| 32 | /work/administration-governance/onboard/external-partner-user | E | high | /work/administration-governance/onboard/external-partner-user: no BFF/API backing detected |
| 33 | /work/administration-governance/onboard/hsc-user | E | high | /work/administration-governance/onboard/hsc-user: no BFF/API backing detected |
| 34 | /work/administration-governance/onboard/madi-user | E | high | /work/administration-governance/onboard/madi-user: no BFF/API backing detected |
| 35 | /work/administration-governance/onboard/marketplace-user | E | high | /work/administration-governance/onboard/marketplace-user: no BFF/API backing detected |
| 36 | /work/administration-governance/onboard/municipal-user | E | high | /work/administration-governance/onboard/municipal-user: no BFF/API backing detected |
| 37 | /work/administration-governance/onboard/payer-user | E | high | /work/administration-governance/onboard/payer-user: no BFF/API backing detected |
| 38 | /work/administration-governance/onboard/private-facility-user | E | high | /work/administration-governance/onboard/private-facility-user: no BFF/API backing detected |
| 39 | /work/administration-governance/onboard/provider-worker | E | high | /work/administration-governance/onboard/provider-worker: no BFF/API backing detected |
| 40 | /work/administration-governance/onboard/public-sector-worker | E | high | /work/administration-governance/onboard/public-sector-worker: no BFF/API backing detected |
| 41 | /work/administration-governance/onboard/regulator-user | E | high | /work/administration-governance/onboard/regulator-user: no BFF/API backing detected |
| 42 | /work/administration-governance/onboard/system-admin | E | high | /work/administration-governance/onboard/system-admin: no BFF/API backing detected |
| 43 | /work/administration-governance/onboard/training-user | E | high | /work/administration-governance/onboard/training-user: no BFF/API backing detected |
| 44 | /access/governance | D | low | Unregistered route |
| 45 | /clinical/emergency/[visitId] | D | low | Unregistered route |
| 46 | /developer/event-catalogue | D | low | Unregistered route |
| 47 | /ehr/[patientId]/charts/[chartId] | D | low | Unregistered route |
| 48 | /ehr/[patientId]/emergency | D | low | Unregistered route |
| 49 | /ehr/[patientId]/procedures/[episodeId] | D | low | Unregistered route |
| 50 | /ehr/[patientId]/workspace/[specialty] | D | low | Unregistered route |
| 51 | /groups/[id] | D | low | Unregistered route |
| 52 | /groups | D | low | Unregistered route |
| 53 | /inventory/items | D | low | Unregistered route |
| 54 | /inventory/reconciliation | D | low | Unregistered route |
| 55 | /inventory/stock | D | low | Unregistered route |
| 56 | /landela | D | low | Unregistered route |
| 57 | /learning/admin/moderation | D | low | Unregistered route |
| 58 | /marketplace/apps/[itemCode] | D | low | Unregistered route |
| 59 | /marketplace/apps/admin/activation | D | low | Unregistered route |
| 60 | /marketplace/apps/admin/installations | D | low | Unregistered route |
| 61 | /marketplace/apps/integration | D | low | Unregistered route |
| 62 | /marketplace/apps | D | low | Unregistered route |
| 63 | /page.tsx | D | low | Unregistered route |
| 64 | /professional | D | low | Unregistered route |
| 65 | /registry/facilities/[id]/edit | D | low | Unregistered route |
| 66 | /registry/facilities/new | D | low | Unregistered route |
| 67 | /registry/providers/[id]/edit | D | low | Unregistered route |
| 68 | /registry/providers/new | D | low | Unregistered route |
| 69 | /tuso | D | low | Unregistered route |

## Services requiring product-owner decision

_None flagged as blocker requiring immediate PO decision._
