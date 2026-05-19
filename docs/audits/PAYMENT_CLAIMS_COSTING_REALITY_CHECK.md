# Payment, Claims, and Costing Reality Check

## Cross-Surface Status

| Area | Web | Mobile | Parity class | Notes |
|---|---|---|---|---|
| Wallet | Real/partial | Real/partial | `WEB_REAL_MOBILE_REAL` (partial depth) | Mobile and web both present with uneven depth. |
| Claims/Coverage | Real/partial | Partial | `WEB_REAL_MOBILE_PARTIAL` | Mobile lacks some advanced adjudication/ops paths. |
| Costa costing intelligence | Real web finance surfaces | Partial mobile tools | `WEB_REAL_MOBILE_PARTIAL` | Further mobile parity needed. |
| Payment intent state display | Mixed | Mixed | `UNKNOWN` | Needs strict shared status mapping audit. |

## Risks

- Users may assume parity where only subset of claim/costing states are available on mobile.
- Some backend/payment capabilities remain partially surfaced.

## Priority Remediation

1. Standardize payment/claim status enums across web/mobile display components.
2. Add parity tests for mandatory payment-required state rendering.
3. Explicitly label unsupported mobile billing actions until wired.
