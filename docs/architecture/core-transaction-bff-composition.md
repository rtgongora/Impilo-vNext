# Core Transaction BFF Composition

Canonical BFF contract: `contracts/openapi/core-transaction-openapi.yaml`.

Runtime controller surface (dual):

- `/internal/v1/core-transactions/*` (canonical internal route family)
- `/experience/core-transactions/*` (doctrine-facing alias family)

## Composed View

`GET /experience/core-transactions/{transactionId}` returns a composed transaction view:

- transaction core state/type/stage
- client summary
- provider + facility/workspace context
- trust context
- clinical context + orders
- financial context
- follow-up context
- timeline
- next actions + permissions
- audit summary
- offline sync status + failure modes
- three synchronized journey views (person/provider/platform)
- Nompilo companion context (guidance, accessibility, feedback, handoff)

Additional doctrine-facing endpoints:

- `GET /experience/core-transactions/{transactionId}/journeys`
- `GET /experience/core-transactions/{transactionId}/nompilo`
- `POST /experience/core-transactions/{transactionId}/feedback`
- `POST /experience/core-transactions/{transactionId}/nompilo/handoff`

Runtime support is implemented through the shared controller mapping that exposes both internal and alias route families.

## Composition Rules

1. BFF composes from sovereign services and trusted registries.
2. BFF never claims source ownership of clinical/registry/trust/financial records.
3. BFF must preserve correlation/audit metadata across downstream calls.
4. BFF responses must expose explicit loading/error/permission/failure context to the UI.
