# ADR-TSHEPO-LEGACY-MONOLITH-CONSTRAINT-AND-RETIREMENT

- Status: Proposed (Trust-plane control baseline)
- Date: 2026-05-14
- Decision owners: Trust Architecture / Platform Security / Experience-BFF Governance
- Scope: `tshepo-service` and TSHEPO trust decomposition services

## Context

`tshepo-service` (legacy monolith) still coexists with decomposed TSHEPO trust services. This creates overlapping trust responsibility, mixed API conventions, and operational/security ambiguity.

Current canonical TSHEPO trust decomposition:
- `tshepo-authz-service`
- `tshepo-consent-service`
- `tshepo-identity-service`
- `tshepo-audit-service`
- `tshepo-keys-service`
- `tshepo-offline-service`

Observed risk conditions:
- Monolith route overlap with decomposed services.
- Legacy route conventions (`/v1/*`) still dominant in trust services.
- Legacy monolith has permissive security posture that is incompatible with future trust hardening expectations.

## Decision

1. `tshepo-service` is designated **LEGACY/COMPATIBILITY ONLY**.
2. The six decomposed TSHEPO services are the **canonical trust source-of-truth execution path**.
3. **No new consumer integration is allowed** directly against `tshepo-service`.
4. Any retained `tshepo-service` route must either:
   - delegate to canonical decomposed services, or
   - be explicitly marked `DEPRECATED` with retirement metadata.
5. Permissive legacy security posture in `tshepo-service` must be constrained and then eliminated as part of retirement execution.

## Constraints and Guardrails

- New backend, BFF, mobile, UI, or integration work must target canonical decomposed TSHEPO services.
- Direct new route additions to `tshepo-service` are prohibited.
- Trust governance checks must reject PRs that introduce new `tshepo-service` dependencies without approved exception.

## Migration Approach for Existing Consumers

1. Build endpoint mapping inventory (`tshepo-service` route -> canonical service route).
2. Add compatibility delegations where safe.
3. Mark all non-delegated retained legacy routes as deprecated.
4. Migrate consumers in waves:
   - Wave 1: service-to-service trust consumers
   - Wave 2: BFF/gateway mediated paths
   - Wave 3: residual tooling/admin consumers
5. Remove legacy routes after compatibility window closure and telemetry confirmation.

## Compatibility Window

- Compatibility window is temporary and controlled.
- Default planning window: two production release cycles (or explicit governance override).
- Any extension requires architecture and security sign-off with quantified risk.

## Retirement Criteria

`tshepo-service` can retire only when all are true:
- No active production consumer depends on non-delegated monolith routes.
- Canonical decomposition services provide required route parity.
- Security controls, audit obligations, and observability parity are validated.
- Runbooks and incident paths are updated to decomposed ownership.
- Cutover/recovery drill completed and signed off.

## Monitoring and Audit Requirements During Transition

- Track all runtime traffic to `tshepo-service` routes with consumer identity tagging.
- Track deprecated route usage trend to zero.
- Emit trust audit events for fallback/delegation and deprecation-path usage.
- Alert on new direct consumer registrations for `tshepo-service`.

## Risks if Retained Indefinitely

- Trust control drift (policy/identity/consent/audit split-brain risk).
- Increased attack surface from permissive/legacy controls.
- Operational ambiguity during incidents and audits.
- Slower delivery due to duplicate ownership and compatibility debt.

## Rollback Considerations

- Rollback preserves compatibility delegations and can temporarily re-enable selected deprecated routes if critical path fails.
- Rollback must not permit new net functionality to be added into `tshepo-service`.
- Rollback requires immediate incident/audit records and follow-up remediation timeline.

## Consequences

Positive:
- Clear trust ownership boundaries.
- Better security posture and governance clarity.
- Reduced long-term trust-plane technical debt.

Negative / cost:
- Near-term migration overhead for existing consumers.
- Need for additional telemetry, deprecation governance, and route parity execution.

## Documentation and Registry Alignment

- `tshepo-service` production-readiness classification remains `LEGACY/COMPATIBILITY`.
- Service-readiness and gap-remediation registers must keep explicit blockers and migration progress visible until retirement.
