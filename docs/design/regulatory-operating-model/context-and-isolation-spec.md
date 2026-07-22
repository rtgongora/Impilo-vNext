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

## 3a. Dual-capacity persons (one person, clinical + regulatory)

Most council members and many council officers ARE practising professionals — an MDPCZ or NCZ
member is typically a registrant with a full clinical life. The same person therefore holds, on
one Health-ID anchor: a clinical identity (varapi provider + vashandi assignments, driving
`my_professional` / `facility_work`) AND one or more regulatory appointments (org-registry,
driving `regulatory_work`). These are **coexisting capacities**, not a person type. The build
already reflects this: the org-session branch resolves the appointment regardless of whether the
person is also a provider, and mints a clean org-only token; the clinical session path is
unchanged. The person switches capacity via the mode picker.

Two invariants follow:

- **Capacity isolation, not person typing.** A regulatory session SHALL NOT require, inherit or
  expose clinical facility/provider context; a clinical session SHALL NOT inherit regulatory
  authority. Each capacity's token carries only its own dimensions (regulatory: org_id + role +
  jurisdiction; clinical: facility + provider + workspace). Never conflate them because they
  belong to the same person (doctrine §2.4).

- **Self-regulation firewall (recusal).** Because a regulator may also be a registrant of the
  same council, a person SHALL NOT act in a regulatory capacity on their OWN record — reviewing,
  deciding, moderating, adjudicating or sitting on a committee for a case, application, register
  entry, complaint or disciplinary matter whose subject is that same person (or, where declared,
  a conflicted relationship). This is a governed recusal, enforced where the actions live:
  varapi application/disciplinary services and the committee docket (W4/W7/W8) SHALL refuse a
  self-subject action (`actor person == subject person`) with a RECUSAL_REQUIRED outcome and an
  audit entry; the conformance pack asserts it (ROM-RECUSAL). Detecting the tie uses the person
  Health-ID on both sides (regulator appointment ↔ subject register entry/case), never the
  provider public id alone.

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
