# IATG Trust Console — Tshepo Policy (ENFORCED)

> **Status (2026-07-09): ENFORCED at ext_authz** via DB `policy_rule` seeds
> (`tshepo-authz V032__trust_console_policy_rules.sql`), imitating the
> `path_contains` pinning style of V031 (platform-origin). No PolicyEngine or
> OPA rego change — DENY-wins + first-matching-ALLOW unchanged.
>
> **Role taxonomy:** enforced realm roles are UPPERCASE_SNAKE. `SYSTEM_ADMIN`
> already exists; `HIE_ADMIN` is being added to the Keycloak realm by the
> coordinator in the parallel IATG workstream — the rules reference it now so
> access lights up the moment the role lands.

## What the console is

A unified governance surface (`/registry-admin/trust-console` in the shell,
`/internal/v1/trust-console` on the experience-bff) so IATG review work stops
being invisible: pending provider access requests, pending facility-admin
claims, pending organisation onboarding, the assurance upgrade queue, and
recent decisions — with approve / reject / needs-more-information actions.

## Roles

| Role | Scope |
|---|---|
| `SYSTEM_ADMIN` | national platform administration — every Trust Console queue and decision |
| `HIE_ADMIN` | HIE governance administrators — same queues; the console is their primary surface |

All other roles have **no matching rule** on these paths → PDP fail-closed
(NO_MATCHING_RULES → DENY). Applicant-facing submit/read paths keep their
existing rules; nothing is narrowed by V032.

## Path families → rules (all `effect=ALLOW`, priority 50)

| Path pin (`path_contains`) | Service | Covers |
|---|---|---|
| `/providers/access-requests` | varapi | `GET /v1/internal/providers/access-requests/review`, `POST /v1/internal/providers/access-requests/{publicId}/decision` (and admin read of the family) |
| `/facility-admin-appointments` | tuso | `GET /v1/internal/facility-admin-appointments?state=PENDING`, `POST /v1/internal/facility-admin-appointments/{id}/approve` |
| `/trust-console` | experience-bff | `GET /internal/v1/trust-console/summary`, `GET .../queues/{queue}`, `GET .../decisions/recent`, `POST .../queues/{queue}/{id}/decision` |

`actor_type`, `resource_type`, `action` and `purpose` are NULL (wildcards);
the `path_contains` pin keeps each rule from leaking onto colliding resource
types from other services, exactly as V031 does for `/platform-origin`.

## Layered enforcement

1. **ext_authz (this spec):** role + path via the V032 `policy_rule` seeds.
2. **BFF precheck:** every Trust Console route re-runs the
   `AdminGovernancePolicyService` session-contract precheck and emits an
   `AdminGovernanceAuditHelper` audit event (non-blocking on audit failure).
3. **Service-side truth:** varapi enforces the decidable-status transition set
   (SUBMITTED / PENDING_*_REVIEW / NEEDS_* → APPROVED / REJECTED /
   NEEDS_MORE_INFORMATION) and records `decided_by` / `decided_at` /
   `decision_note`; tuso derives tenancy from the owning facility and only
   approves PENDING appointments.

## Non-goals / follow-ups

- Tenant column values beyond the default tenant are seeded by operational
  tooling, not this migration.
- Queue-level partitioning of HIE_ADMIN (e.g. org-onboarding only) is a
  follow-up: it requires purpose-of-use or resource_type tightening once the
  runtime contract for the console's purpose header is settled.
