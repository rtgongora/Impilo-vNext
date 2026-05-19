# Core Transaction UI Wiring Audit

## Current State

| Surface | Web | Mobile | Data source | Status |
|---|---|---|---|---|
| `/core-transaction` | Present | Missing | Fixture | `WEB_MOCK_MOBILE_MISSING` |
| `/client-journey` | Present | Missing | Fixture | `WEB_MOCK_MOBILE_MISSING` |
| `/provider-workspace` | Present | Missing | Fixture | `WEB_MOCK_MOBILE_MISSING` |
| `/platform-journey` | Present | Missing | Fixture | `WEB_MOCK_MOBILE_MISSING` |

## Contract Mapping

- Canonical model: `contracts/core-transaction.ts`
- Current UI model: `ui/one-ui-shell/src/features/core-transaction/types.ts` (local duplicate)

## Remediation Done This Cycle

- Added fixture honesty badges on all four doctrine pages.

## Required Next Wiring

1. Create web query hooks for `/internal/v1/core-transactions/{id}` and related journey endpoints.
2. Replace fixture imports in doctrine pages with API-backed state.
3. Add mobile core transaction journey shell or explicitly document no-mobile scope.
4. Align UI type imports to canonical contract definitions.
