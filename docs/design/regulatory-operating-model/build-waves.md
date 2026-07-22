# ROM Build Waves (ROM-W0 – ROM-W11)

One commit-set per wave; conventional commits; `git pull --rebase` before push; all new JVM
tests `*Test` (surefire); migrations validated BEGIN..ROLLBACK against the live preview DBs;
shell waves: `npx tsc --noEmit` + vitest incl. routes test at the bumped
`EXPECTED_ROUTE_COUNT`; every rego addition: `opa test`; SHADOW divergence evidence before any
ENFORCE. Migration ledger in [`README.md`](README.md).

**Law honoured throughout**: every wave delivers its process as BOTH the applicant journey and
the regulator workflow (doctrine §5).

**Dual-capacity + recusal (doctrine §4.4, cross-cutting)**: a regulator may also be a registrant
of the same council. Capacities never bleed (regulatory token carries no facility/provider;
clinical token carries no regulatory authority — already true after W2). And a person may not act
in a regulatory capacity on their OWN record: W4 (application review), W7 (disciplinary
transitions) and W8 (committee docket assignment + sitting) SHALL refuse a self-subject action
(`actor Health-ID == subject Health-ID`) with a `RECUSAL_REQUIRED` outcome + audit entry. Asserted
by the pack as **ROM-RECUSAL** (W11).

---

**ROM-W0 — Doctrine + spec pack** *(docs only)*
Doctrine + README index; this pack; `services-registry.yaml` hand-edits (R1/R2/R5/R8 tokens).
Gate: PO review of the nine `orgs/*.md` files (items marked `TO_CONFIRM` need registrar
confirmation before their seeds are cut in W1/W3/W8).

**ROM-W1 — Nine organisations, one truth**
org-registry `V006__regulatory_appointment_vocabulary.sql` (appointment + closed role table +
jurisdiction), `V007__regulatory_organisation_seed.sql` (9 orgs, deterministic UUIDs); varapi
`V028__council_org_link_and_nine_council_seed.sql` (FK + 9 cross-linked council rows +
`council_regulatory_configs` + reg-number patterns from org files); zibo
`V005__regulatory_jurisdiction_valueset.sql`. Classes: org-registry
`RegulatoryAppointmentService/Controller` (reusing invitation rails); `CouncilService.create`
hardened (mandatory orgRegistryOrgId). Tests: seed integrity (9 rows both sides), FK
round-trip, reg-pattern regex validity. Slice: an appointable registrar exists per council;
varapi's council listing shows all nine.

**ROM-W2 — Org session (the login seam)**
vashandi `V009__regulatory_org_assignment.sql` (+`RegulatoryAppointmentConsumer` → org-only
assignment); experience-bff `WorkContextController` org branch (FACILITY_REQUIRED only when
neither subject present; `matchAssignment` org matching); tshepo-authz
`V045__org_jurisdiction_scope_dimension.sql` + per-council isolation seeds **SHADOW** + rego;
shell `regulatory_work` mode + `/work/regulatory/[orgId]` hub + WorkspaceSwitcher appointments
+ re-parent `/work/regulators/[regulatorId]/*` stubs; EXPECTED_ROUTE_COUNT bump same commit.
council_users write-freeze guard + backfill to appointments. Slice: NCZ Registration Officer
logs in → NCZ workspace; a PCZ officer's attempted NCZ read produces a SHADOW-denied log.

**ROM-W3 — Registers first-class + both self-service anchors**
varapi `V029__professional_registers.sql` + `V030__register_seed_and_status_migration.sql`
(registers per org files; string→row status migration; restrictions + good-standing tables;
affiliation compat view). NEW self endpoints `/v1/me/regulatory/...` (authenticated person →
own entries — fixes the `?providerId=` admin-plane defect); shell "My Regulatory Affairs" in My
Professional; registrar register view in the council workspace; public verify extended to
register-based good standing. Tests: migration idempotence, status equivalence, self-endpoint
negative test (foreign providerId refused). Slice: registrant sees own standing/restrictions;
registrar browses the register; the public verifies — three sides of one truth.

**ROM-W4 — Professional registration application E2E** *(first full vertical)*
varapi `V031__application_sections_rfi_messages.sql` (sections, contributor invites, RFI
mirror, `application_case_message` APPLICANT/INTERNAL) + `V032__council_fee_schedule_and_
payment_wiring.sql` (fee shape from tuso V021; `VarapiMushexPaymentIntentClient` +
`VarapiFeePaymentConsumer` mirroring the tuso consumers); forms
`V003__regulatory_application_section_schemas.sql`; `ProviderApplicationService` typed FSM
incl. RFI loop; notification templates + khuluma consumer; Nompilo guidance cards
(`AdvisoryAdminController`); staged applicant UI in My Professional; de-stub
`regulators/[regulatorId]/registration-review`; certificate via document-service → public
verify. Tests: FSM transition table; INTERNAL-never-serialized; payment consumer; certificate
round-trip. Slice: a nurse applies to NCZ end-to-end (draft → RFI loop → review → decision →
payment → certificate) while the registrar works the same case.

**ROM-W5 — Renewal + CPD gate**
varapi `V033__renewal_and_cpd_gate.sql`; `LicenceRenewalSweep` extended: ACTIVE→DUE opens a
renewal case + khuluma notice (no silent LAPSED); renewal gated on varapi-adjudicated CPD
status (fundo seam); de-stub `cpd-review` (waiver adjudication). Tests: sweep-opens-case; CPD
block/unblock. Slice: registrant sees the CPD shortfall blocking renewal with a Fundo
deep-link; the CPD officer adjudicates; a compliant renewal pays and re-certificates.

**ROM-W6 — Practice & Facility Regulation + pre-licensing**
tuso `V039__practice_establishment_case.sql` (pre-licensing case + practice profile, NO
facility_id — facility created on approval; `FacilityApplicationType` enum NOT extended);
reuse pic_nomination FSM, RFI, inspections, the complete V021 payment rail. Shell "Practice &
Facility Regulation" owner/manager workspace (staged application, RFI responses, inspection
remediation, renewals; reserved declarations gated to owner/director/PIC). Test:
facility-only-on-approval. Slice: a pharmacist owner establishes a pharmacy end-to-end while
the HPA officer processes it on existing rails.

**ROM-W7 — Complaints / investigations / disciplinary E2E**
rito `V008__regulatory_intake_and_referral.sql` (REPORT_UNREGISTERED_PRACTICE public intake on
the welcome/report claim-code rail; council routing; referral echo fields); varapi
`V034__disciplinary_case_fsm.sql` (FSM + `source_rito_case_id`; determinations write W3
restriction rows). UI: registrant "complaints involving me" (notice + respond); de-stub
`disciplinary` + `cases` regulator routes; investigations queue. Tests: firewall (a rito case
can never transition a varapi case; only officer action opens one); respondent visibility.
Slice: public report → rito triage → officer opens proceedings → investigation → charges, the
respondent participating throughout.

**ROM-W8 — Committees / hearings / appeals**
org-registry `V008__committee_organs_and_membership.sql` (+seeds per org files); varapi
`V035__hearings_dockets_appeals.sql` (sittings, docket assignments extending
`provider_committee_reviews`, appeal case type); tuso `V040__facility_decision_appeal_link.sql`
(linkage only); authz `V046__committee_case_assignment_dimension.sql` SHADOW. Tests:
docket-scoped visibility (SHADOW log assertion); appeal references determination. Slice: a W7
case is docketed to the NCZ Professional Conduct Committee; a member sees ONLY docketed cases;
determination → restriction on the register → registrant appeals → appeals sitting.

**ROM-W9 — Dashboards + statutory reports**
reporting `V003__regulatory_report_definitions.sql` — definitions per class
(OPERATIONAL/MANAGEMENT/STATUTORY/PUBLIC_INTEREST/OVERSIGHT) over NAMED varapi/tuso read
models (**no-theatre gate**: a definition with no backing query fails the wave); experience-bff
aggregation endpoints for operational queues; shell per-workspace dashboards
(`ReportingDashboardOrchestrationPanel` + above-site pattern). Slice: registrar operational
board + council CEO management board + one generated statutory annual return per council — all
from the queues the earlier slices populated.

**ROM-W10 — HPA oversight environment**
varapi `V036__oversight_grants_and_escalations.sql` (per-case escalation grants;
aggregate-only read models); authz `V047__hpa_oversight_policies.sql` (aggregate + granted-case
reads; operational workspaces absent by construction); shell `/work/regulatory/hpa/oversight`
(council supervision, cross-council intelligence, escalations). Tests: aggregate-only;
granted-case-only. Slice: HPA sees 9-org indicators, cannot open NCZ's queue, sees one
explicitly escalated case.

**ROM-W11 — Conformance pack + ENFORCE**
`tests/regulatory-contract/{README.md, regulatory-journeys.sh, council-isolation-journeys.sh}`
(reputation-contract pattern: GREEN 0 / AMBER 2 / RED 1; SKIP-never-false-PASS). Invariants:
ROM-OWN (org identity single SoR, councils FK-bound), ROM-APPT (access only from verified
appointment), ROM-ISO (cross-council denied), ROM-CTX (full chain in token → workspace),
ROM-APPL (two-sided application; INTERNAL never leaks), ROM-CPD (renewal consumes varapi
adjudication only), ROM-FIREWALL (rito→disciplinary human-mediated), ROM-COMMITTEE (docketed
only), ROM-OVERSIGHT (aggregates + grants, no workspaces), ROM-RECUSAL (a person cannot act as
regulator on their own record — self-subject action refused), ROM-AUDIT (review/change/approve/
access trail for a full W4 journey). authz SHADOW→ENFORCE for V045/V046/V047 after soak
(strictly AFTER the identity program's flip); council_users deprecation completed (backfill
verified; forbidden token recorded).

---

## Risks / coordination

- **Identity WORK_CONTEXT flip in flight** — ROM dimensions land SHADOW-only; ROM ENFORCE
  sequences after the identity flip; never simultaneous.
- **CZO PolicyEngine single-writer** — W2/W8/W10 policy = seed migrations + rego batched
  through CZO slots; `services/tshepo-service` untouched.
- **Hand-curated services-registry.yaml** — manual edits only; wave-gate diff check that
  `seed-registry.mjs` was never run.
- **routes.ts EXPECTED_ROUTE_COUNT** — bump in the same commit per wave; rebase per wave.
- **Scope guards** — no generic workflow engine; ONE parameterised council workspace; committee
  model minimal (docket + sitting + decision — no agenda/quorum engine); fee amounts stay
  PENDING_REGULATOR_APPROVAL; scope = the nine listed orgs only; registers seeded from org
  files only; W6 must NOT extend `FacilityApplicationType`.
