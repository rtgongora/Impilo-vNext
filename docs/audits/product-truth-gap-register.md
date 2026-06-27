# Product Truth — Gap Register

> Generated: 2026-06-27T16:24:42.300Z
> Total gaps: **5**

## Gap categories (A–R)

| Cat | Description | Count |
|-----|-------------|------:|
| A | Backend exists, UI missing | 0 |
| B | Backend exists, BFF missing | 0 |
| C | Backend exists, contract missing/stale | 0 |
| D | Backend exists, frontend only partially wired | 0 |
| E | UI exists, backend missing | 0 |
| F | UI exists, uses mock/stub/fixture data | 3 |
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
| S | Security/crypto/authz placeholder in a product path | 2 |

## By severity

| Severity | Count |
|----------|------:|
| high | 5 |

## Prioritized gaps (top 100)

| Rank | Entity | Category | Severity | Description |
|------|--------|----------|----------|-------------|
| 1 | experience-bff | F | high | experience-bff: mock/stub/fixture/in-memory patterns in product path (2 hits) |
| 2 | pct-service | S | high | pct-service: security/crypto/authz placeholder in product path (5 hits) |
| 3 | vashandi-workforce-service | S | high | vashandi-workforce-service: security/crypto/authz placeholder in product path (1 hits) |
| 4 | /wellness/commodities | F | high | /wellness/commodities: mock/stub data |
| 5 | /operations/facility-operations | F | high | /operations/facility-operations: mock/stub data |

## Services requiring product-owner decision

_None flagged as blocker requiring immediate PO decision._
