# Client, Marketplace, and Wellness Reality Check

## Web/Mobile Snapshot

| Capability | Web | Mobile | Class | Notes |
|---|---|---|---|---|
| Citizen journey shells | Present | Present (different IA) | `WEB_REAL_MOBILE_REAL` (partial parity) | Not all journey depth is equivalent. |
| Marketplace browsing/orders | Present | Present | `WEB_REAL_MOBILE_REAL` (partial) | Some operations/admin detail remain web-heavy. |
| Wellness modules | Present | Present | `WEB_REAL_MOBILE_REAL` (partial) | Some sections use staged maturity. |
| Provider discovery | Present | Placeholder in citizen mobile | `WEB_REAL_MOBILE_MOCK` | Mobile discovery TODO currently not wired. |
| Conditions/allergies personal sections | Web EHR provider context real | Mobile citizen placeholders | `WEB_REAL_MOBILE_MOCK` | Honesty labels added this cycle. |

## Remediation Completed

- Added mobile not-wired badges and explanatory copy in placeholder citizen sections.

## Next Remediation

1. Implement citizen API-backed conditions/allergies/discovery data services.
2. Add parity tests for client journey outcome states across web and mobile.
