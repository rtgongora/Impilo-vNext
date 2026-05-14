# Trust, Identity Assurance & Governance Plane

TSHEPO policy, authorisation, consent, audit, keys, identity assurance, session and device risk.

## Service Ownership

| Service ID | Maven module | Domain | System of record | Forbidden responsibilities |
|---|---|---|---|---|
| `identity-assurance-service` | `identity-assurance-service` | identity-governance | Identity Assurance canonical records | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `mvumo-service` | `mvumo-service` | identity-governance | Mvumo canonical records | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-audit-service` | `tshepo-audit-service` | identity-governance | Tshepo Audit canonical records | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-authz-service` | `tshepo-authz-service` | identity-governance | Tshepo Authz canonical records | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-consent-service` | `tshepo-consent-service` | identity-governance | Tshepo Consent canonical records | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-identity-service` | `tshepo-identity-service` | identity-governance | Tshepo Identity canonical records | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-keys-service` | `tshepo-keys-service` | identity-governance | Tshepo Keys canonical records | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-offline-service` | `tshepo-offline-service` | identity-governance | Tshepo Offline canonical records | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-service` | `tshepo-service` | identity-governance | Tshepo canonical records | must-not-own-clinical-record-content, must-not-own-billing-ledgers |

## Operating Guardrails

- Authz, audit and observability controls are mandatory on production routes.
- No new mock or stub path is allowed in production execution routes.
- Contract and integration changes must be reflected in registry and cross-plane maps.