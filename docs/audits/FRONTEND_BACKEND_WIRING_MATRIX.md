# Frontend-Backend Wiring Matrix

## Matrix

| Surface | Web | Mobile | Contract | BFF | Backend | Classification |
|---|---|---|---|---|---|---|
| Core transaction doctrine pages | Fixture | Missing | Canonical contract exists | Exists | Exists | `WEB_MOCK_MOBILE_MISSING` |
| Queue/worklist | Real | Real | Present | Present | Present | `WEB_REAL_MOBILE_REAL` |
| Telemedicine | Real | Real/partial | Present | Present | Present | `WEB_REAL_MOBILE_REAL` (partial depth) |
| Nompilo command/handoff | Partial | Missing/partial | Present | Present | Partial | `WEB_REAL_MOBILE_MISSING` |
| Payments/claims | Real/partial | Partial | Present | Present | Present | `WEB_REAL_MOBILE_PARTIAL` |
| Reporting | Real/partial | Partial | Present | Present | Present | `WEB_REAL_MOBILE_PARTIAL` |
| Workflow/dispatch | Missing | Missing | Present | Present | Present | `BACKEND_ONLY` |

## Notes

- This matrix complements `BFF_API_WIRING_AUDIT.md` and `WEB_MOBILE_PARITY_AUDIT.md`.
- Detailed gap backlog listed in `BACKEND_NOT_SURFACED_REGISTER.md` and `REMEDIATION_SUMMARY.md`.
