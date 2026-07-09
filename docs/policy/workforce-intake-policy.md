# Workforce Intake — Policy Specification

Status: active (seeded by `tshepo-authz-service` migration `V032__workforce_intake_policy_rules.sql`)

## Scope

The staged Workforce Intake journey bootstraps MoHCC staff at scale:

```
upload CSV → column mapping + validation → duplicate detection + Health-ID matching
→ confirm/approve → execute (VARAPI Provider ID issuance + Vashandi profiles/assignments)
→ activation invitations → completion tracker
```

Surfaces guarded by this policy family:

| Layer | Path | Owner |
|---|---|---|
| Experience BFF | `/internal/v1/workforce-intake/**` | experience-bff (stateless composition) |
| Batch model (SoR) | `/v1/internal/governance/imports/**` (`import_type=workforce_intake`) | workforce-governance-service |

## PDP rules (V032)

Three ALLOW rules in `tshepo_authz.policy_rule`, all pinned with
`{"path_contains": "/workforce-intake"}` so they never leak onto colliding
resource types from other services (DENY-wins and first-matching-ALLOW
semantics unchanged):

| Rule | Role | Access |
|---|---|---|
| `workforce-intake-system-admin-full` | `SYSTEM_ADMIN` | Full pipeline |
| `workforce-intake-facility-admin-full` | `FACILITY_ADMIN` | Full pipeline |
| `workforce-intake-hie-admin-full` | `HIE_ADMIN` | Full pipeline |

Without a matching rule the PDP fails closed (`NO_MATCHING_RULES → DENY`).

## Defense in depth

- **BFF precheck** — every step calls `AdminGovernancePolicyService` with a
  step-specific action (`WORKFORCE_INTAKE_UPLOAD` / `_MAPPING` / `_MATCH` /
  `_APPROVE` / `_EXECUTE`) evaluated against the caller's session experience
  contract (management-workspace visibility), before any upstream call.
- **Stage machine** — workforce-governance-service rejects out-of-order stage
  transitions (`UPLOADED→MAPPED→VALIDATED→MATCHED→APPROVED→EXECUTING→COMPLETED|PARTIAL`);
  execution requires an APPROVED (or PARTIAL, for resume) batch.
- **Audit** — every step emits a `workforce_intake.*` event via tshepo-audit
  (`batch_uploaded`, `column_mapping_applied`, `batch_matched`,
  `batch_approved`, `batch_executed`).
- **Anti-enumeration preserved** — Health-ID matching consumes the C3 silent
  identifier resolution endpoint (`/v1/identity/resolve-identifier`), which
  returns a uniform shape on hit and miss.

## UI route guard

`/admin/workforce-intake` is registered in `ui/one-ui-shell/src/lib/routes.ts`
with `guard: "role", requiredRole: "ADMIN"` (admin zone).
