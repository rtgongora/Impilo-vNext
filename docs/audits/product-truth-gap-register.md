# Product Truth — Gap Register

> Generated: 2026-06-26T16:23:42.967Z
> Total gaps: **8**

## Gap categories (A–R)

| Cat | Description | Count |
|-----|-------------|------:|
| A | Backend exists, UI missing | 0 |
| B | Backend exists, BFF missing | 0 |
| C | Backend exists, contract missing/stale | 0 |
| D | Backend exists, frontend only partially wired | 0 |
| E | UI exists, backend missing | 0 |
| F | UI exists, uses mock/stub/fixture data | 4 |
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
| S | Security/crypto/authz placeholder in a product path | 4 |

## By severity

| Severity | Count |
|----------|------:|
| blocker | 1 |
| high | 7 |

## Prioritized gaps (top 100)

| Rank | Entity | Category | Severity | Description |
|------|--------|----------|----------|-------------|
| 1 | mushe-wallet-service | S | blocker | mushe-wallet-service: security/crypto/authz placeholder in product path (2 hits) |
| 2 | experience-bff | F | high | experience-bff: mock/stub/fixture/in-memory patterns in product path (10 hits) |
| 3 | experience-bff | S | high | experience-bff: security/crypto/authz placeholder in product path (2 hits) |
| 4 | mushe-wallet-service | F | high | mushe-wallet-service: mock/stub/fixture/in-memory patterns in product path (3 hits) |
| 5 | pct-service | S | high | pct-service: security/crypto/authz placeholder in product path (5 hits) |
| 6 | vashandi-workforce-service | S | high | vashandi-workforce-service: security/crypto/authz placeholder in product path (1 hits) |
| 7 | /wellness/commodities | F | high | /wellness/commodities: mock/stub data |
| 8 | /operations/facility-operations | F | high | /operations/facility-operations: mock/stub data |

## Services requiring product-owner decision

- **mushe-wallet-service** — mushe-wallet-service: security/crypto/authz placeholder in product path (2 hits)
