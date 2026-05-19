# Enterprise Resource & Market Operations Plane

MusheX, COSTA, coverage, claims, billing, marketplace and enterprise operations.

## Service Ownership

| Service ID | Maven module | Domain | System of record | Forbidden responsibilities |
|---|---|---|---|---|
| `costing-engine-service` | `costing-engine-service` | finance | Costing Engine canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `coverage-service` | `coverage-service` | finance | Coverage canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `credential-verification-service` | `credential-verification-service` | finance | Credential Verification canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `general-ledger-service` | `general-ledger-service` | enterprise-resource | General Ledger canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `hr-payroll-service` | `hr-payroll-service` | enterprise-resource | Hr Payroll canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `msika-flow-service` | `msika-flow-service` | marketplace | Msika Flow canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `msika-service` | `msika-service` | marketplace | Msika canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `mushe-wallet-service` | `mushe-wallet-service` | finance | Mushe Wallet canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `mushex-service` | `mushex-service` | finance | Mushex canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `procurement-service` | `procurement-service` | enterprise-resource | Procurement canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `share-slip-service` | `share-slip-service` | finance | Share Slip canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `simba-service` | `simba-service` | wellness-personal-health-data | wellness journeys, lifestyle plans, self-care plans, preventive care workflows, wellness goals, habit tracking workflows, coaching and nudge workflows, wellness programme participation, longitudinal wellness progress, connected source registry and permissions, personal wellness readings and manual entries, wellness remote monitoring alerts | must-not-own-clinical-encounter-lifecycle, must-not-own-acute-care-orders-or-results, must-not-own-prescription-dispensing, must-not-own-inpatient-care-state, must-not-own-patient-identity-source-of-truth, must-not-own-provider-identity-source-of-truth, must-not-own-facility-registry, must-not-own-consent-policy-authority, must-not-own-payment-ledgers, must-not-own-public-health-surveillance-source-of-truth |
| `wellness-service` | `wellness-service` | wellness-compatibility-alias | — | must-not-own-public-health-surveillance-source-of-truth, must-not-own-clinical-encounter-lifecycle, must-not-own-marketplace-or-payment-ledgers, must-not-own-patient-identity-source-of-truth, must-not-own-provider-identity-source-of-truth, must-not-own-facility-registry |
| `workforce-governance-service` | `workforce-governance-service` | workforce-operations | Workforce Governance canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |

## Operating Guardrails

- Authz, audit and observability controls are mandatory on production routes.
- No new mock or stub path is allowed in production execution routes.
- Contract and integration changes must be reflected in registry and cross-plane maps.