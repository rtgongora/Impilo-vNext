# ROM Generic Model Spec

The ONE model all nine regulatory organisations run on. Behaviour differences between councils
are parameters in `varapi.council_regulatory_configs` and the `orgs/*.md` files — never code
forks. Ownership per [`ownership-rulings.md`](ownership-rulings.md).

## 1. Entity model (by owner)

**organization-registry** (`org_registry` schema)
- `org_registry_organization` — the nine orgs (existing table; org_type PUBLIC_HEALTH_AUTHORITY
  | STATUTORY_REGULATOR | PROFESSIONAL_COUNCIL).
- `org_registry_appointment_role` (V006) — closed role vocabulary (see R2).
- `org_registry_regulatory_appointment` (V006) — person_health_id, org_id, role_code,
  jurisdiction_code, valid_from/to, status (PENDING_VERIFICATION | ACTIVE | SUSPENDED | ENDED |
  REVOKED), verification/evidence refs, appointed_by. One ACTIVE per (person, org, role_code)
  partial unique.
- `org_registry_committee` + committee membership via appointments (V008) — committee_code,
  name, org_id, committee_type (REGISTRATION | PROFESSIONAL_CONDUCT | EDUCATION | FINANCE |
  APPEALS | AD_HOC), status.

**varapi** (`varapi` schema)
- `councils` (+ `org_registry_org_id` FK, V028) — regulation profile per R1.
- `professional_registers` (V029) — council_id, register_code, name, description, status.
- `register_entries` (V029) — provider_id, register_id, entry_number (pattern-validated),
  status FSM: PROVISIONAL | REGISTERED | SUSPENDED | REMOVED | RESTORED_PENDING | LAPSED;
  effective dates; provenance.
- `register_entry_restrictions` (V029) — entry_id, restriction_type (SCOPE_LIMIT | SUPERVISION
  | CONDITION | INTERDICTION), text, imposed_by (decision ref), valid_from/to, status.
- `good_standing_status` (V029) — entry_id, standing (GOOD | NOT_GOOD | UNDER_INVESTIGATION —
  disclosure-classed), as_of, derivation refs.
- `provider_applications` (existing) + `application_section_state` + contributor invitations +
  `application_information_request` (varapi mirror of tuso's) + `application_case_message`
  (V031) — see FSM A and §3.
- council fee schedule + `provider_payment_obligations` wiring (V032).
- `provider_disciplinary_cases` + FSM columns + `source_rito_case_id` (V034) — FSM B.
- `hearings`, `hearing_sittings`, `case_docket_assignments` (V035) extending
  `provider_committee_reviews` — FSM C; appeal case type.
- oversight grants + aggregate read models (V036).

**tuso** (existing V006/V018–V021 spine, plus)
- `practice_establishment_case` + pre-licensing practice profile (V039) — NO facility_id until
  approval; owners/directors/beneficial interests; nominated PIC (pic_nomination FSM reused);
  category selection against `facility_classification`; staged sections; RFI reuse.
- appeal linkage on committee decisions (V040).

**rito** — REPORT_UNREGISTERED_PRACTICE intake category + council routing +
`referred_authority_case_ref` echo (V008).

**forms-service** — versioned regulatory application section schemas (V003); validation only.

**reporting-service** — `rpt_report_definitions` rows per report class (V003).

**zibo** — `regulatory-jurisdiction` value set (V005).

## 2. The three state machines

**FSM A — Application (varapi `provider_applications`; tuso `facility_application` and
`practice_establishment_case` align to the same shape):**

DRAFT → SECTIONS_IN_PROGRESS → SUBMITTED → COMPLETENESS_REVIEW →
(RFI_OPEN ⇄ RFI_RESPONDED — repeatable correction loop, section-scoped) →
TECHNICAL_REVIEW → INSPECTION_PENDING (tuso lane) | COMMITTEE_PENDING →
DECIDED_APPROVED | DECIDED_REFUSED | DECIDED_DEFERRED →
AWAITING_PAYMENT → CERTIFICATE_ISSUED → CLOSED
(+ WITHDRAWN from any pre-decision state; + APPEALED from a refusal)

Invariants: applicant always sees stage, outstanding requirements, responsible office,
timelines, next action (doctrine §6.2); reserved declarations only by owner/director/PIC
(§6.3); typed transitions replace string transitions in `ProviderApplicationService`.

**FSM B — Disciplinary (varapi `provider_disciplinary_cases`):**

RECEIVED → PRELIMINARY_ASSESSMENT → UNDER_INVESTIGATION → CHARGES_FORMULATED →
REFERRED_TO_COMMITTEE → HEARING → DETERMINED → APPEALED | CLOSED

Invariants: only an appointed officer's recorded decision creates a case from a rito referral
(firewall); DETERMINED writes restriction/standing rows, never mutates registration silently;
respondent (the professional) has notice + response rights surfaced in My Regulatory Affairs.

**FSM C — Hearing (varapi `hearings`):**

DOCKETED → SCHEDULED → SITTING_IN_PROGRESS → DELIBERATION → DETERMINATION_ISSUED →
(APPEAL_WINDOW_OPEN → APPEAL_FILED?) → CONCLUDED

Invariants: docket assignment gates committee-member visibility (authz V046 dimension); a
member sees ONLY docketed cases.

## 3. Correspondence duality

`application_case_message` (varapi; shape shared with rito `rit_case_message`): case ref,
direction (INBOUND | OUTBOUND), author actor + capacity, **visibility (APPLICANT | INTERNAL)**,
body, attachments (document-service refs), created_at. INTERNAL never serializes into
applicant-facing payloads (asserted by test, W4). RFI rows (`application_information_request`,
canonical shape from tuso V018: message, due_date, status OPEN|RESPONDED|CLOSED,
response_document_id) drive the correction loop; each RFI shows accepted/rejected/outstanding.

## 4. Context-resolution chain (summary; full spec in context-and-isolation-spec.md)

person (Health ID, verified) → org_registry_regulatory_appointment (ACTIVE, verified) →
vashandi org-scoped assignment (mirror) → WORK_CONTEXT token (org_id, role, jurisdiction,
assignment_id) → shell `regulatory_work` workspace for that org → tshepo-authz decisions
carrying org/jurisdiction (+ docket) dimensions.

## 5. Report classes

| Class | Owner | Backing | Audience |
|---|---|---|---|
| OPERATIONAL | experience-bff aggregation (stateless) | live SoR queues (applications, RFIs, inspections, renewals, complaints, appeals, overdue) | working officers |
| MANAGEMENT | experience-bff aggregation | workload/TAT by officer/team/region/profession/facility-type/stage | registrars, CEOs, councils |
| STATUTORY | reporting-service definitions (V003) | named SoR read models; scheduled + downloadable + submission-ready | councils → HPA/Ministry |
| PUBLIC_INTEREST | reporting-service definitions | disclosure-classed projections only | public |
| OVERSIGHT | reporting-service definitions + varapi V036 aggregates | cross-council consolidated indicators; drill-down only via grant | HPA, Ministry |

No-theatre gate (W9): a definition with no named backing read model fails the wave.

## 6. Audit events

Every review/change/approval/rejection/ACCESS on a regulatory record emits the standard audit
event (actor, appointment capacity, org, record, action, outcome, timestamp) through the
existing tshepo audit chain. Audit reports are a first-class report surface (registrar + HPA
mandates). Access-logging on reads applies to register entries, cases and dockets.

## 7. Public contracts

- Verify professional: existing `PublicPractitionerVerificationController` extended to
  register-based good standing (register, entry status, restrictions disclosure-classed).
- Verify facility/certificate/credential: existing tuso public controllers.
- Report unregistered practice: `welcome/report` triage branch → rito claim-code intake
  (case_type REPORT_UNREGISTERED_PRACTICE) → council routing.
- Complaint tracking: existing rito claim-code status rail.
- Disclosure law: internal identifiers, personal contacts, investigation detail and protected
  disciplinary records never appear on public surfaces.
