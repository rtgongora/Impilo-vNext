# observability-service — Internal Only

> **Classification:** Q — internal-only; no actor-facing UI required.

## Purpose

Observability operates as an internal platform/integration component under the **integration** plane (platform-ops).

## Why no user-facing UI

- Consumed by other services, BFF orchestration, or edge agents — not directly by clinicians/citizens.
- Operational visibility belongs in ops/admin tooling or observability stacks, not the Health OS experience shell.

## Exposure

| Surface | Status |
|---------|--------|
| REST/FHIR API | See service controllers and OpenAPI contract |
| Experience BFF | Proxied only where orchestration requires |
| one-ui-shell | **Not required** |
| Mobile | **Not required** |

## Tests required

- Contract/golden IT for primary API paths
- Integration smoke via compose/full-boot when deployed

## Related services

See [product-truth-backend-ui-traceability.md](../product-truth-backend-ui-traceability.md).
