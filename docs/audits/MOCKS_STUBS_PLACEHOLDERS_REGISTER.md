# Mocks, Stubs, and Placeholders Register

## High-Risk Entries

| ID | Severity | Area | File(s) | Evidence | Status |
|---|---|---|---|---|---|
| MSP-001 | CRITICAL | Core Transaction web | `ui/one-ui-shell/src/app/core-transaction/page.tsx` and sibling journey pages | Imports from `features/core-transaction/fixtures/core-transactions` | Mitigated with fixture honesty label; live wiring pending |
| MSP-002 | HIGH | Mobile citizen conditions | `apps/mobile/citizen-app/src/screens/personal/ConditionsSection.tsx` | `TODO: Wire to backend service`, empty results | Honesty label added; backend wiring pending |
| MSP-003 | HIGH | Mobile citizen allergies | `apps/mobile/citizen-app/src/screens/personal/AllergiesSection.tsx` | `TODO` + previously no-op actions | Honesty label added; no-op action disabled |
| MSP-004 | HIGH | Mobile provider discovery | `apps/mobile/citizen-app/src/screens/discover/ProviderDiscoveryScreen.tsx` | `TODO` + empty set | Honesty label added; backend wiring pending |
| MSP-005 | MEDIUM | Nompilo command semantics | Web command surfaces + BFF synthetic accepted responses | Partial grounding risk | Pending end-to-end command integration |

## Acceptable Fixture Usage Criteria

- Explicitly labeled as fixture/prototype.
- Not presented as live production state.
- Not used to mask backend failures.
- Backed by documented remediation plan.
