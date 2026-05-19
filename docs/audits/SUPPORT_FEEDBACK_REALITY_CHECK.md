# Support, Omnichannel, and Feedback Reality Check

## Status

| Surface | Web | Mobile | Classification |
|---|---|---|---|
| Support routes in one-ui-shell | Present | N/A | `WEB_ONLY` (paired with mobile support screens) |
| Support console app | Present (`ui/support-console`) | N/A | `WEB_ONLY` |
| Mobile support screens | N/A | Present | `MOBILE_ONLY` |
| Feedback endpoints | Present in BFF for core transaction context | Partial surface usage | `PARTIAL` |

## Findings

- Support capability is split across dedicated web app (`ui/support-console`) and shell surfaces.
- Mobile support exists but parity with web support-console depth is partial.
- Nompilo handoff/feedback semantics need full backend integration (beyond accepted placeholders).

## Recommended Work

1. Define shared support capability matrix (web shell vs support-console vs mobile support).
2. Ensure feedback submission outcomes are persisted and visible to operators.
3. Add parity tests for feedback submission success/failure flows.
