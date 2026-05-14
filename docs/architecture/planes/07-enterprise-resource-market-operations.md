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
| `workforce-governance-service` | `workforce-governance-service` | workforce-operations | Workforce Governance canonical records | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |

## Operating Guardrails

- Authz, audit and observability controls are mandatory on production routes.
- No new mock or stub path is allowed in production execution routes.
- Contract and integration changes must be reflected in registry and cross-plane maps.