# ROM Ownership Rulings (R1–R8)

Binding rulings for the Regulatory Operating Model. Each names the system of record, the
contested seam it settles, and the deprecation path where one applies. These rulings are
enforced by the conformance pack (`tests/regulatory-contract/`, ROM-W11) and codified in
`docs/registry/services-registry.yaml` (hand-edited — never regenerate).

## R1 — Regulatory organisation: org-registry is identity SoR; `varapi.councils` is the profile

- `org_registry_organization` (org_type already includes `PUBLIC_HEALTH_AUTHORITY`,
  `STATUTORY_REGULATOR`, `PROFESSIONAL_COUNCIL`) owns legal name, type, contact, existence and
  lifecycle of all nine organisations.
- `varapi.councils` becomes the professional-regulation configuration profile: **varapi V028**
  adds `councils.org_registry_org_id UUID UNIQUE` (nullable on introduction, backfilled by the
  9-org seed, then constrained NOT NULL). `CouncilService.create` gains a mandatory
  `orgRegistryOrgId` and refuses unlinked councils. Council-specific truth stays in varapi:
  `registration_number_pattern` (V012 regex), `council_regulatory_configs` JSONB (incl.
  `workflow_template_code`), CPD rules, fee schedule references.
- Tuso never grows an org table; HPA's tuso-side configuration keys on HPA's
  `org_registry_org_id` where a regulator reference is needed.
- **Anti-duplication law**: an org attribute (name/type/status) is never written in varapi/tuso;
  a regulation attribute (register pattern, workflow config) is never written in org-registry.

## R2 — Appointments: org-registry SoR; vashandi operational mirror; `council_users` deprecated

- **org-registry V006** introduces `org_registry_regulatory_appointment` (person_health_id,
  org_id, `role_code` FK → closed vocabulary table `org_registry_appointment_role`:
  REGISTRAR, DEPUTY_REGISTRAR, REGISTRATION_OFFICER, INSPECTOR, CPD_OFFICER,
  INVESTIGATIONS_OFFICER, COMMITTEE_MEMBER, FINANCE_OFFICER, RECORDS_OFFICER, LEGAL_OFFICER,
  COUNCIL_CEO, HPA_OVERSIGHT_OFFICER, HPA_INSPECTORATE_OFFICER; `jurisdiction_code`,
  valid_from/valid_to, status, verification refs). This upgrades the
  `org_registry_authorized_representative` shape (free `role_title`, no jurisdiction) without
  breaking it; appointment onboarding reuses the existing claim/invitation rails (V002/V004/V005).
- **vashandi** mirrors accepted appointments as `vsh_workforce_assignment` rows with
  `organisation_id` set, `facility_id NULL` (already expressible per V008),
  `engagement_type = REGULATORY_APPOINTMENT`, role_template per role_code — event-driven
  consumer, the `facility_admin_appointment` → vashandi precedent inverted to org scope.
- **`varapi.council_users` deprecation path**: ROM-W2 freezes writes (service-level guard) →
  backfill maps rows to org-registry appointments → varapi reads via compatibility view →
  ROM-W11 records the forbidden-responsibility token ("varapi SHALL NOT originate regulatory
  personnel records") and the table goes legacy-read-only. **No column drops in this program.**

## R3 — Jurisdiction: zibo value set + appointment attribute + authz dimension

- **zibo V005** seeds the `regulatory-jurisdiction` value set (NATIONAL; the ten provinces;
  districts). Councils default NATIONAL; the dimension exists for HPA inspectorate regions and
  future regional offices — modelled now, defaulted, never speculatively subdivided.
- The appointment carries `jurisdiction_code`; tshepo-authz gains the matching policy dimension
  (R6 of context-and-isolation-spec; authz V045).

## R4 — Registers: varapi first-class (professional); tuso already de-facto (premises)

- **varapi V029** creates `professional_registers` (council_id, register_code, name),
  `register_entries` (provider ↔ register, entry_number, status FSM), and first-class
  `register_entry_restrictions` + `good_standing_status` rows replacing today's strings.
  `provider_council_affiliations` remains the *relationship* record with a compatibility view;
  readers migrate before the view retires (out of this program's drop scope).
- **tuso** needs no new register entity — `facility_regulatory_profile` (V020) +
  `facility_credential` (V035) ARE the premises register; ROM adds only a read projection for
  reporting.

## R5 — Complaints/disciplinary: rito intake, registries proceed; human-mediated referral

- **rito** owns: public + registrant complaint intake (claim-code rail), the two-way case voice
  (`rit_case_message` PUBLIC/INTERNAL visibility), the new REPORT_UNREGISTERED_PRACTICE branch,
  triage/routing to the responsible regulator.
- **varapi** owns the professional disciplinary PROCEEDING — ROM-W7 gives
  `provider_disciplinary_cases` a real FSM: RECEIVED → PRELIMINARY_ASSESSMENT →
  UNDER_INVESTIGATION → CHARGES_FORMULATED → REFERRED_TO_COMMITTEE → HEARING → DETERMINED →
  APPEALED/CLOSED; determinations write R4 restriction rows.
- **tuso** owns facility compliance proceedings (inspection/compliance_action, already mature).
- **Firewall** (provider-reputation-doctrine §8): a rito case NEVER auto-transitions a
  proceeding. `provider_disciplinary_cases.source_rito_case_id` is set only when an appointed
  officer decides to open proceedings; rito receives a status echo for complainant transparency.
  Application RFI threads do NOT route through rito.

## R6 — Committees & hearings: identity split from docket

- **org-registry V008** owns the committee as an organisational organ (committee table under
  the org) and its membership (appointments with role_code=COMMITTEE_MEMBER + committee_id) —
  one truth for who sits where, reusing appointment verification.
- **The case-owning service owns the docket**: varapi (W8) adds hearings / hearing_sittings /
  case_docket_assignments extending `provider_committee_reviews`; tuso keeps `committee_review`
  + `committee_state` and gains only appeal linkage (V040). A shared *contract shape* (spec
  section, not a shared service) keeps the two aligned.
- **Appeals** are cases in the owning service referencing the appealed decision. HPA
  cross-council appellate visibility is an oversight grant (R8), never a data move.

## R7 — Applications, correspondence, payments, certificates

- varapi extends `provider_applications` + `ProviderApplicationService` (typed FSM incl. RFI
  loop); tuso extends `facility_application` and adds the **pre-licensing establishment case**
  (W6) — the `FacilityApplicationType` enum presupposes an existing facility and is NOT
  extended; establishment is a new case route on the V018 spine, creating the facility only on
  approval.
- **forms-service V003** hosts versioned section-schema definitions for regulatory application
  forms (schema+validation only; submissions live in varapi/tuso).
- RFI/two-way correspondence: tuso `application_information_request` (OPEN|RESPONDED|CLOSED) is
  the canonical shape; varapi gets a mirror + `application_case_message` adopting rito's
  PUBLIC/INTERNAL visibility model. The duplicated RFI shape across varapi/tuso is DELIBERATE
  (same shape, two SoRs) and guarded by a contract-shape test.
- Payments: the tuso V021 rail (fee gate → `MushexPaymentIntentClient` →
  `MushexFeePaymentConsumer` / `CostaChargeCreatedConsumer`) is replicated as a PATTERN in
  varapi against `provider_payment_obligations` (V007). Fee amounts follow the SI 78/2017
  discipline: NULL + `PENDING_REGULATOR_APPROVAL`, never invented.
- Certificates: rendered artifacts via document-service; verification via the existing
  `PublicPractitionerVerificationController` (varapi) and `PublicCertificateVerificationController`
  (tuso).

## R8 — CPD, dashboards/reports, oversight

- **CPD**: fundo (learning-service) = evidence/completions SoR; varapi = adjudication +
  council-compliance status (seam already wired: `CpdController /summary`,
  `FundoWebhookController`, `FundoCertificateIssuedListener`, `fundo_cpd_candidates`). Renewal
  reads varapi CPD status only.
- **Reporting**: reporting-service owns statutory + public-interest + oversight report
  DEFINITIONS and RUNS (`rpt_report_definitions/runs/schedules`); ROM-W9 lands real
  read-model-backed content (no-theatre gate). Operational/management dashboards = stateless
  experience-bff aggregation over SoR queues + shell panels
  (`ReportingDashboardOrchestrationPanel` pattern). experience-bff stays migration-free.
- **HPA oversight** = aggregate reads + explicit per-case escalation grants (varapi V036 +
  authz V047) — never standing operational access, never data copies.
