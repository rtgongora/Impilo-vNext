# Experience, Workflow & Orchestration Plane

one-ui-shell, experience-bff, provider/citizen/admin journeys and orchestration.

## Service Ownership

| Service ID | Maven module | Domain | System of record | Forbidden responsibilities |
|---|---|---|---|---|
| `community-service` | `community-service` | workflow-orchestration | Community canonical records | must-not-own-domain-source-data, must-not-bypass-bff-authz-audit-controls |
| `experience-bff` | `experience-bff` | workflow-orchestration | Experience Bff canonical records | must-not-own-domain-source-data, must-not-bypass-bff-authz-audit-controls |
| `learning-service` | `learning-service` | workflow-orchestration | Learning canonical records | must-not-own-domain-source-data, must-not-bypass-bff-authz-audit-controls |
| `wellness-service` | `wellness-service` | workflow-orchestration | Wellness canonical records | must-not-own-domain-source-data, must-not-bypass-bff-authz-audit-controls |

## Operating Guardrails

- Authz, audit and observability controls are mandatory on production routes.
- No new mock or stub path is allowed in production execution routes.
- Contract and integration changes must be reflected in registry and cross-plane maps.