# Web/Mobile Parity Audit

## Parity Classification Snapshot

| Feature | Web status | Mobile status | Class | Remediation |
|---|---|---|---|---|
| Core Transaction Journey Shell | Fixture | Missing | `WEB_MOCK_MOBILE_MISSING` | Wire to core-transaction BFF and add mobile equivalent or explicit scope decision. |
| Queue/Triage Workflows | Real | Real | `WEB_REAL_MOBILE_REAL` | Keep parity tests and state language aligned. |
| Telemedicine | Real | Real/Partial | `WEB_REAL_MOBILE_REAL` (partial depth) | Converge session capabilities and error states. |
| Nompilo Command Layer | Partial/mock | Missing/partial | `WEB_REAL_MOBILE_MISSING` | Implement capability matrix and mobile parity baseline. |
| Claims/Coverage | Real (finance-heavy) | Partial | `WEB_REAL_MOBILE_PARTIAL` | Expand mobile claims detail flows and shared statuses. |
| Reports | Real | Partial | `WEB_REAL_MOBILE_PARTIAL` | Align report generation, filters, and exports. |
| Registry Admin | Real | Missing | `WEB_ONLY` | Document intentional admin-only scope. |
| Citizen Conditions/Allergies discovery | N/A | Placeholder | `MOBILE_ONLY` (placeholder) | Wire mobile API clients to existing backend/BFF. |
| Workflow/Dispatch ops | Missing | Missing | `BACKEND_ONLY` | Add operator UI surfaces. |

## Parity Risks

- Cross-surface trust: backend exists but UI communicates fixture states.
- Hidden asymmetry: web finance richness outpaces mobile clarity.
- Nompilo expectations drift without explicit capability parity matrix.
