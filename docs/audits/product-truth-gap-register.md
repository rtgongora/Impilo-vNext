# Product Truth — Gap Register

> Generated: 2026-06-27T13:47:09.777Z
> Total gaps: **15**

## Gap categories (A–R)

| Cat | Description | Count |
|-----|-------------|------:|
| A | Backend exists, UI missing | 0 |
| B | Backend exists, BFF missing | 0 |
| C | Backend exists, contract missing/stale | 1 |
| D | Backend exists, frontend only partially wired | 0 |
| E | UI exists, backend missing | 4 |
| F | UI exists, uses mock/stub/fixture data | 8 |
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
| high | 14 |
| medium | 1 |

## Prioritized gaps (top 100)

| Rank | Entity | Category | Severity | Description |
|------|--------|----------|----------|-------------|
| 1 | /welcome | E | high | /welcome: no BFF/API backing detected |
| 2 | /welcome/find-care | E | high | /welcome/find-care: no BFF/API backing detected |
| 3 | /welcome/emergency | E | high | /welcome/emergency: no BFF/API backing detected |
| 4 | /welcome/accessibility | E | high | /welcome/accessibility: no BFF/API backing detected |
| 5 | experience-bff | F | high | experience-bff: mock/stub/fixture/in-memory patterns in product path (7 hits) |
| 6 | mushe-wallet-service | F | high | mushe-wallet-service: mock/stub/fixture/in-memory patterns in product path (2 hits) |
| 7 | pct-service | S | high | pct-service: security/crypto/authz placeholder in product path (5 hits) |
| 8 | vashandi-workforce-service | S | high | vashandi-workforce-service: security/crypto/authz placeholder in product path (1 hits) |
| 9 | /welcome | F | high | /welcome: mock/stub data |
| 10 | /welcome/find-care | F | high | /welcome/find-care: mock/stub data |
| 11 | /welcome/emergency | F | high | /welcome/emergency: mock/stub data |
| 12 | /welcome/accessibility | F | high | /welcome/accessibility: mock/stub data |
| 13 | /wellness/commodities | F | high | /wellness/commodities: mock/stub data |
| 14 | /operations/facility-operations | F | high | /operations/facility-operations: mock/stub data |
| 15 | patient-safety-service | C | medium | patient-safety-service: no matched OpenAPI contract |

## Services requiring product-owner decision

_None flagged as blocker requiring immediate PO decision._
