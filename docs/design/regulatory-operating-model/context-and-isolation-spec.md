# ROM Context & Isolation Spec

The login seam: how a regulatory appointment becomes a working session, and how cross-council
isolation is enforced. Doctrine §§2, 4, 11.

## 1. The chain, component by component

**person → verified identity** — existing identity spine (Health ID, assurance levels).
Appointment onboarding requires verified person identity (consume the person-proofing
framework; never bypass).

**→ regulatory organisation + appointment** — `org_registry_regulatory_appointment` (V006, R2).
Onboarding lanes reuse org-registry claim/invitation rails: registrar invites officer;
officer accepts; verification recorded. Appointment end/revocation emits an event.

**→ vashandi mirror** — `RegulatoryAppointmentConsumer` (vashandi V009) materialises an
org-scoped `vsh_workforce_assignment` (`organisation_id` set, `facility_id NULL`,
`engagement_type = REGULATORY_APPOINTMENT`, `role_template_id` mapped from role_code, validity
from the appointment). Appointment revocation → assignment end → existing
`assignment.ended.v1` → WORK_CONTEXT token teardown (the revocation pipeline already built by
the Provider/Place program).

**→ org session (WORK_CONTEXT)** — `experience-bff WorkContextController`
`POST /internal/v1/work-context/session` gains an org branch:
- accepts `organisationId` as the context subject; `FACILITY_REQUIRED` error only when NEITHER
  facilityId nor organisationId is present;
- `matchAssignment` matches org-scoped assignments on (organisation_id, workspace_id, role);
- the issued WORK_CONTEXT token (tshepo-identity V004 schema — `org_id` claim ALREADY exists)
  carries org_id, role (role_template), jurisdiction, assignment_id, session_assurance; 15-min
  TTL + revoke-on-switch unchanged. tshepo-identity V005 only if a new claim field is needed
  (target: none — jurisdiction rides context_claims).
- Facility-mode flows are untouched (regression bar: existing facility session tests stay green).

**→ shell workspace** — `operational-context.ts` adds mode `regulatory_work` (context subject =
organisationId, no facility/shift). `/work/regulatory/[orgId]` hub; WorkspaceSwitcher offers
the person's ACTIVE appointments as contexts ("Nurses Council — Registration Officer", "HPA —
Inspectorate"). Existing `/work/regulators/[regulatorId]/*` stubs re-parent under this mode.
ONE parameterised council workspace serves all nine orgs (org file supplies labels/queues).

**→ policy decision** — tshepo-authz dimensions (below). PDP resolves professional
scope/live-status per action, never in-token (Provider/Place doctrine D-P3 carried over).

## 2. Authz dimensions (tshepo-authz; seed migrations + rego via CZO channel; tshepo-service NO-TOUCH)

- **V045 `org_jurisdiction_scope_dimension`**: `policy_rule` gains org/jurisdiction condition
  support (pattern: V043 first-class dimensions — workflow-state/department/provider-id).
  Seeds: per-council isolation ALLOW rules (role_template × org_id match between token and
  target record's owning org) + DENY-wins cross-org defaults. **SHADOW** on landing.
  Precedent for facility-less national actors: V037 MCAZ rules (`facility_scope=false`).
- **V046 `committee_case_assignment_dimension`** (W8): docket-scoped visibility — a
  COMMITTEE_MEMBER read requires a matching `case_docket_assignments` row. SHADOW.
- **V047 `hpa_oversight_policies`** (W10): HPA oversight actors get aggregate-read ALLOW +
  granted-case ALLOW; council operational workspaces absent by construction (no rule grants
  them — fail-closed).

SHADOW → ENFORCE: divergence≈0 evidence over a soak window; ROM's ENFORCE flips sequence
strictly AFTER the identity program's WORK_CONTEXT enforce flip; never simultaneous.

## 3. Isolation policy matrix (seed shape, V045)

| Actor (role_template @ org) | Own-org records | Other council records | HPA aggregates | Docketed cases |
|---|---|---|---|---|
| REGISTRATION_OFFICER @ council X | ALLOW (registration lanes) | DENY | — | — |
| INSPECTOR @ council X / HPA | ALLOW (inspection lanes, jurisdiction-bounded) | DENY | — | — |
| INVESTIGATIONS_OFFICER @ X | ALLOW (case lanes) | DENY | — | — |
| FINANCE_OFFICER @ X | ALLOW (fee/payment lanes only) | DENY | — | — |
| COMMITTEE_MEMBER @ X | DENY (base) | DENY | — | ALLOW (docketed only, V046) |
| REGISTRAR / COUNCIL_CEO @ X | ALLOW (management views) | DENY | — | — |
| HPA_OVERSIGHT_OFFICER | DENY (council row-level) | DENY | ALLOW | ALLOW (granted only, V047) |

An inspector is not finance; a committee member is not council-wide; HPA is not inside any
council's desk. Every DENY row is proven live by the conformance pack (ROM-ISO / ROM-COMMITTEE
/ ROM-OVERSIGHT).

## 4. Applicant-side context (non-regulator capacities)

- "My Regulatory Affairs" rides `my_professional` (no new mode): NEW varapi self endpoints
  `/v1/me/regulatory/...` resolve the AUTHENTICATED person → provider → register entries /
  applications / cases. Fixes the current defect where the only self-service page
  (`registry/provider-council/self-service`) is admin-plane and `?providerId=`-keyed.
- "Practice & Facility Regulation" rides the owner/manager capacity (org-registry
  representative or facility_admin_appointment): tuso establishment case + facility_application
  lanes surfaced to the OWNING person; reserved declarations gated to owner/director/PIC.
- An applicant with an active case never resolves a regulator workspace from it (doctrine §2.4).

## 5. Failure paths

- Appointment expired/revoked mid-session → token teardown (revocation pipeline) → shell
  `work_denied`-style remediation surface with the appointment status.
- Org session requested with no ACTIVE appointment → refusal with remediation (apply/claim
  lane), never a silent empty workspace.
- PDP unreachable → fail closed (existing posture).
