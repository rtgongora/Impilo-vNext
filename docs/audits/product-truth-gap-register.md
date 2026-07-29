# Product Truth — Gap Register

> Generated: 2026-07-29T06:32:45.832Z
> Total gaps: **6**

## Gap categories (A–R)

| Cat | Description | Count |
|-----|-------------|------:|
| A | Backend exists, UI missing | 0 |
| B | Backend exists, BFF missing | 0 |
| C | Backend exists, contract missing/stale | 3 |
| D | Backend exists, frontend only partially wired | 0 |
| E | UI exists, backend missing | 1 |
| F | UI exists, uses mock/stub/fixture data | 1 |
| G | UI exists, form submits but does not persist | 0 |
| H | UI exists, button/card is dead or decorative | 0 |
| I | API exists, database persistence missing | 0 |
| J | Database exists, API missing | 0 |
| K | Contract exists, implementation missing | 0 |
| L | BFF exists, downstream service not wired | 0 |
| M | Mobile parity missing | 0 |
| N | Auth/policy/tenant scoping missing | 1 |
| O | Tests missing | 0 |
| P | Workflow incomplete across services | 0 |
| Q | Service is internal-only and needs documentation instead of UI | 0 |
| R | Duplicate/overlapping capability requiring consolidation | 0 |
| S | Security/crypto/authz placeholder in a product path | 0 |

## By severity

| Severity | Count |
|----------|------:|
| high | 2 |
| medium | 4 |

## Prioritized gaps (top 100)

| Rank | Entity | Category | Severity | Description |
|------|--------|----------|----------|-------------|
| 1 | /my-life | E | high | /my-life: no BFF/API backing detected |
| 2 | / | F | high | /: mock/stub data |
| 3 | procedures-service | C | medium | procedures-service: no matched OpenAPI contract |
| 4 | surgery-service | C | medium | surgery-service: no matched OpenAPI contract |
| 5 | surgery-service | N | medium | surgery-service: auth/policy/audit gaps (trust-context-filter, security-baseline-config) |
| 6 | mental-health-service | C | medium | mental-health-service: no matched OpenAPI contract |

## Services requiring product-owner decision

_None flagged as blocker requiring immediate PO decision._
