# Registry & Sovereign Identity Spine

VITO, VARAPI, TUSO, ZIBO, UBOMI, INDAWO and authoritative registries.

## Service Ownership

| Service ID | Maven module | Domain | System of record | Forbidden responsibilities |
|---|---|---|---|---|
| `indawo-service` | `indawo-service` | registry-spine | Indawo canonical records | must-not-authorize-access-decisions, must-not-own-clinical-encounters |
| `product-registry-service` | `product-registry-service` | registry-spine | Product Registry canonical records | must-not-authorize-access-decisions, must-not-own-clinical-encounters |
| `tuso-service` | `tuso-service` | registry-spine | Tuso canonical records | must-not-authorize-access-decisions, must-not-own-clinical-encounters |
| `ubomi-service` | `ubomi-service` | registry-spine | Ubomi canonical records | must-not-authorize-access-decisions, must-not-own-clinical-encounters |
| `varapi-service` | `varapi-service` | registry-spine | Varapi canonical records | must-not-authorize-access-decisions, must-not-own-clinical-encounters |
| `vito-service` | `vito-service` | registry-spine | Vito canonical records | must-not-authorize-access-decisions, must-not-own-clinical-encounters |
| `zibo-service` | `zibo-service` | terminology | Zibo canonical records | must-not-authorize-access-decisions, must-not-own-clinical-encounters |

## Operating Guardrails

- Authz, audit and observability controls are mandatory on production routes.
- No new mock or stub path is allowed in production execution routes.
- Contract and integration changes must be reflected in registry and cross-plane maps.