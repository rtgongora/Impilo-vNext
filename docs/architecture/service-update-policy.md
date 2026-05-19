# Service Update Policy

## Policy Statement
The Service Architecture Register is the single source of truth for Impilo vNext service ownership. Rings describe operational criticality and dependency level. Planes describe architectural responsibility and system-of-record ownership. Every service must have one Ring and one primary Plane before it can be accepted into the platform.

A service that is not in the register does not officially exist in vNext.

## Mandatory Update Rule
Every new service, app, adapter, worker, shared library, mobile app, infrastructure component, or external dependency must update:
- `docs/architecture/SERVICE_ARCHITECTURE_REGISTER.md`
- `docs/architecture/services-registry.yaml`

## Classification Doctrine
- Every service must have exactly one Ring and exactly one primary Plane.
- Secondary Planes are allowed but cannot imply ownership of truth.
- Plane assignment follows system-of-record responsibility, not branding, UI location, developer preference, folder placement, or implementation convenience.
- If classification is unclear, mark as Unclear and add to unresolved services.

## Surface Traceability Rules
- New backend capabilities must document frontend/API/event exposure decisions.
- New frontend capabilities must identify backend/API/service-of-truth dependencies.
- New database schemas, event producers, and event consumers must be reflected in the registry.

## Pull Request Checklist
- [ ] Service Architecture Register updated
- [ ] services-registry.yaml updated
- [ ] Ring assigned
- [ ] Primary Plane assigned
- [ ] Category display label assigned
- [ ] System-of-record responsibility documented
- [ ] API/frontend/event/database surfaces documented
- [ ] Boundary notes added
- [ ] Validation script passes
- [ ] Any unclear classification added to unresolved services

## Enforcement Roadmap
- Phase 0 (Advisory): run `scripts/architecture/validate-service-registry.py` in CI with non-blocking results.
- Phase 1 (Soft Gate): fail CI on structural validation failures (missing entries, invalid Ring/Plane/category, missing required fields).
- Phase 2 (Hard Gate): fail CI on structural and policy validation failures.
- CI phase control is driven by `SERVICE_REGISTRY_VALIDATION_MODE` (`advisory`, `soft`, `hard`).

## OpenAPI Evidence Enforcement
- New or skeleton backend services must include OpenAPI evidence immediately.
- Live backend services enter legacy backfill mode until `OPENAPI_LEGACY_DEADLINE`.
- After the legacy deadline, missing OpenAPI evidence for live backend services becomes a hard validation failure.
- Default legacy deadline is tracked in CI and validator configuration and must be updated only by architecture owner approval.
- Soft-gate promotion readiness additionally requires zero `Missing` contract-alignment entries in `docs/architecture/services-registry.yaml`.

## Approved Alias Runtime Closure Plan
- Deprecated alias runtimes `product-registry-service` and `wellness-service` follow a fixed phased closure:
  - Freeze new downstream alias references: `2026-05-15`
  - Runtime cutover complete to canonical owners (`msika-service`, `simba-service`): `2026-09-30`
  - Hard runtime/module sunset: `2026-12-31`
- During the closure window, downstream configuration must remain canonical-first; alias references are compatibility-only and must not be expanded.
- Alias-deprecated runtime entries are treated as `Not Applicable` for independent contract ownership because canonical contract ownership lives with the designated canonical service.
