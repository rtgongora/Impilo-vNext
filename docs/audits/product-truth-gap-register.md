# Product Truth — Gap Register

> Generated: 2026-08-08T11:41:28.764Z
> Total gaps: **11**

## Gap categories (A–R)

| Cat | Description | Count |
|-----|-------------|------:|
| A | Backend exists, UI missing | 0 |
| B | Backend exists, BFF missing | 0 |
| C | Backend exists, contract missing/stale | 0 |
| D | Backend exists, frontend only partially wired | 5 |
| E | UI exists, backend missing | 0 |
| F | UI exists, uses mock/stub/fixture data | 3 |
| G | UI exists, form submits but does not persist | 0 |
| H | UI exists, button/card is dead or decorative | 0 |
| I | API exists, database persistence missing | 0 |
| J | Database exists, API missing | 0 |
| K | Contract exists, implementation missing | 0 |
| L | BFF exists, downstream service not wired | 3 |
| M | Mobile parity missing | 0 |
| N | Auth/policy/tenant scoping missing | 0 |
| O | Tests missing | 0 |
| P | Workflow incomplete across services | 0 |
| Q | Service is internal-only and needs documentation instead of UI | 0 |
| R | Duplicate/overlapping capability requiring consolidation | 0 |
| S | Security/crypto/authz placeholder in a product path | 0 |

## By severity

| Severity | Count |
|----------|------:|
| medium | 11 |

## Prioritized gaps (top 100)

| Rank | Entity | Category | Severity | Description |
|------|--------|----------|----------|-------------|
| 1 | butano-fhir | D | medium | butano-fhir: partial frontend/BFF wiring |
| 2 | developer-portal-service | D | medium | developer-portal-service: partial frontend/BFF wiring |
| 3 | dispatch-service | L | medium | dispatch-service: 2 BFF downstream path(s) not served by the service (/internal/v1/dispatch/tasks, /internal/v1/dispatch/tasks/) |
| 4 | msika-apps-service | L | medium | msika-apps-service: 7 BFF downstream path(s) not served by the service (/internal/v1/marketplace/launcher, /internal/v1/marketplace/publishers, /internal/v1/marketplace/activation-requests) |
| 5 | ndila-service | L | medium | ndila-service: 2 BFF downstream path(s) not served by the service (/internal/v1/ndila/tiles/{z}/{x}/{y}.png, /internal/v1/ndila/tiles/{z}/{x}/{y}.mvt) |
| 6 | product-registry-service | D | medium | product-registry-service: partial frontend/BFF wiring |
| 7 | share-slip-service | D | medium | share-slip-service: partial frontend/BFF wiring |
| 8 | abis-service | D | medium | abis-service: partial frontend/BFF wiring |
| 9 | /facility/[id]/regulators | F | medium | /facility/[id]/regulators: hardcoded dashboard/data |
| 10 | /telemedicine/new | F | medium | /telemedicine/new: hardcoded dashboard/data |
| 11 | /my-life/feedback/respectful-maternity | F | medium | /my-life/feedback/respectful-maternity: hardcoded dashboard/data |

## Services requiring product-owner decision

_None flagged as blocker requiring immediate PO decision._
