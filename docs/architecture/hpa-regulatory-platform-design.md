# HPA Regulatory Platform — Phase 1 Gap Matrix + Phase 2 Design

> **Source pack:** `/home/robert/tuso-varapi-hpa-regulatory-pack` (HPA Registration, Inspection and
> Renewal Manual Vol 1, 2017 — authoritative *baseline*, not current law; every seeded rule/checklist
> is versioned + `PENDING_REGULATOR_APPROVAL`).
> **Base:** `claude/staging-ux-orchestration-remediation-Yypyl` @ `c04aa05a7`.
> **Constraints honoured:** `services/tshepo-service/**` NO-TOUCH · claims/organisations =
> `organization-registry-service` (no duplicate org truth in tuso) · COSTA/MusheX owns money (tuso
> stores refs/gating only) · no AI-made regulatory decisions · forward-only migrations
> (tuso next = **V018**, varapi next = **V023**).

## 1. Phase-1 gap matrix (pack requirement → current truth → action)

| Pack requirement | Current truth (audited) | Action |
| --- | --- | --- |
| Premises/site identity separate from facility | MISSING (only `facility_geo` address block; `CHANGE_OF_PREMISES` enum) | **V018**: `facility_premises` + `facility_premises_occupancy` (occupancy link = shared-premises representation) |
| Organisation/operator + ownership route | Flat strings (`facility.ownership`, `operating_entity`); org truth lives in organization-registry-service | **Reference, don't own**: `facility.operator_organization_ref` stays a reference (metadata + application fields); no new org tables in tuso |
| Shared premises | MISSING | ≥2 active occupancies on one premises **is** the arrangement; API + UI expose it |
| Application-type catalogue (data-driven) | Hardcoded 8-value enum | **V018**: `regulatory_application_type` catalogue seeded from pack (8 rows, provenance, `REFERENCE_REQUIRES_REGULATOR_VALIDATION`); enum kept as engine key, catalogue carries route/council/inspection/fee/evidence policy |
| Council (PCC) review as real external record | `committee_review` exists (HPA committee) but no *external council* review record | **V018**: `external_council_review` (authority, reference, submitted/decision dates, status, conditions, evidence, recorded-by) + application states honour it |
| RFI cycle | `AWAITING_DOCUMENTS` declared, never set | **V018**: `application_information_request` + open/respond/close endpoints driving `AWAITING_DOCUMENTS` |
| Fee/payment reference | `fee_state` string only | **V018**: `fee_reference` + `payment_reference` + `fee_assessed_amount`(informational) columns on application; COSTA remains ledger |
| PIC nomination/acceptance workflow | PIC row synced from varapi Kafka (`impilo.varapi.pic_assignment`); created pre-APPROVED in `applyApprovedOutcome` | **V018**: `pic_nomination` FSM (pack 3.5): `PROPOSED→ELIGIBILITY_CHECKED→PRACTITIONER_ACCEPTANCE_PENDING→ACCEPTED/DECLINED→REGULATOR_REVIEW_PENDING→APPROVED→ACTIVATED`; activation end-dates predecessor + writes effective-dated assignment + records in varapi (existing PIC API) so both registries stay truthful |
| Time-specific Varapi eligibility w/ evidence | `EligibilityService`/`TusoInteropController` exist but "now"-only, flat booleans, ignores council-registration/disciplinary tables | **varapi V023**: `provider_eligibility_snapshot` + `PicEligibilityAssessmentService` unifying identity/council/registration/certificate/licence/restrictions with per-axis evidence + source-record versions + `asOf` |
| Credential-change → facility review | No tuso consumer for credential events | varapi emits `provider.eligibility.changed` (CREDENTIAL aggregate, envelope-complete); new tuso consumer flags ACTIVE PIC assignments `REVIEW_REQUIRED` + review task + event — never auto-erases |
| Inspection case vs visit, team/lead/COI | Single `facility_inspection` row; untyped `inspector_assignments` | **V018**: `inspection_visit` (case keeps `facility_inspection`), typed team JSONB (actor, role, authority, councilNominee, coiDeclared), lead inspector |
| Per-item structured responses | Findings only (partial submission) | **V018**: `inspection_response` (COMPLIANT/NON_COMPLIANT/NOT_APPLICABLE/NOT_OBSERVED/UNABLE_TO_VERIFY + measurements/evidence/provenance); findings link `response_id` |
| Versioned regulator-approvable templates + provenance | `version` int never advanced; no status/source; seed-only | **V018**: template `status` (DRAFT/PENDING_REGULATOR_APPROVAL/APPROVED/RETIRED), `source_manual`, `source_pages`, `approved_by/at`, `supersedes_template_id`; existing 4 seeds honestly → `PENDING_REGULATOR_APPROVAL`; CRUD + approve endpoints |
| Configurable remediation timeframes / renewal cycle | Hardcoded 14/30 days; `plusYears(1)`; 90-day dashboard window | **V018**: `regulatory_rule` store (versioned, effective-dated, `PENDING_REGULATOR_APPROVAL`, provenance); engine consults rules w/ code defaults as fallback |
| Renewal as successor + cycle + queues | Successor cert exists; cycle hardcoded; no RENEWAL_DUE automation | Rule-driven expiry; scheduled `RENEWAL_DUE` marker job + event; renewal pre-population endpoint |
| Relocation | Enum only, no handling | Premises-aware branch: new premises occupancy, old retired, old cert `SUPERSEDED(reason=RELOCATION)`, never silently transferred |
| Voluntary closure / enforcement / reopening | Generic paths exist (`VOLUNTARY_CLOSURE_NOTICE`, enforcement_case, `REOPENING` enum) | Explicit closure-request journey + reopening branch + decision-gated suspension; transition guard matrix in `applyFacilityStatus` |
| Public certificate verification (QR/code) | MISSING | **V018**: `verification_code` on certificate + `GET /v1/public/facilities/certificates/verify/{code}` disclosing only public fields |
| Facility classifications (39, class A/B/C) | `facility_class`/`facility_category` free strings | **V018**: `facility_classification` reference table seeded w/ provenance + validation status; intake picker uses it |
| Inspection types catalogue | Enum matches pack exactly (7/7) | Seed display/purpose/provenance reference rows only |
| Transition guard | Any method can jump any status | Explicit allowed-transition matrix in `applyFacilityStatus` (SYSTEM-context override for import/ops) |
| Evidence versioning | Independent rows w/ `version` int | Additive `supersedes_document_id` on `facility_document` |

**Enum reconciliation (pack drift):** pack 3.1 application states map onto the existing engine enum —
`VALIDATING→UNDER_ADMIN_REVIEW`, `AWAITING_EXTERNAL_COUNCIL_REVIEW→(new)`, `REPORT_PENDING→INSPECTED`,
`AWAITING_COMMITTEE_DECISION→READY_FOR_COMMITTEE`, `SHORTFALLS_REQUIRING_ACTION→AWAITING_RECTIFICATION`.
We extend the existing enum additively (`AWAITING_COUNCIL_REVIEW`, `WITHDRAWN`) rather than renaming —
existing data + UI keep working; the pack names are recorded in the catalogue metadata.

## 2. Ownership boundaries (unchanged, enforced)

- **Tuso**: facility/premises/unit identity, applications+RFI+council-review records, inspections
  (case/visit/response/finding/corrective action), decisions, certificates+public verification,
  renewals, material changes, relocation, closure/suspension/reopening, effective-dated regulatory
  status, **PIC nomination workflow + effective-dated facility assignment**.
- **Varapi**: practitioner identity/council/registration/certificate/qualification/restrictions,
  **eligibility assessment truth** (snapshotted, versioned), professional-registry PIC record
  (existing API/event mirror kept), inspector credential facts.
- **organization-registry-service**: organisations, representatives, claims (tuso references only).
- **COSTA/MusheX**: fees/payments (tuso stores references + gating state).
- **Tshepo/OPA**: consumes; untouched.

## 3. Migration sequence

- `tuso V018__hpa_regulatory_platform.sql` — all new tables/columns + catalogue/classification/rule
  seeds (provenance + `REFERENCE_REQUIRES_REGULATOR_VALIDATION`/`PENDING_REGULATOR_APPROVAL`) +
  template governance backfill.
- `varapi V023__pic_eligibility_snapshot.sql` — `provider_eligibility_snapshot`.

## 4. Events (existing envelope conventions; pod_id + idempotency_key mandatory)

New tuso: `tuso.facility.pic.nominated|accepted|declined|review_recorded|activated|review_required`,
`tuso.facility.application.information_requested|information_provided`,
`tuso.facility.council_review.recorded`, `tuso.facility.inspection.visit_recorded`,
`tuso.facility.renewal.due`, `tuso.facility.suspended|closed|reopened`,
`tuso.facility.premises.relocated`.
New varapi: `varapi.provider.eligibility.changed` (aggregate `CREDENTIAL` → legacy topic
`varapi.credential`), `varapi.provider.eligibility.assessed`.
Envelope-hygiene fixes: varapi `PractitionerInChargeService`/`CredentialingService` outbox rows gain
`pod_id` + `idempotency_key` (existing poison-row defect family).

## 5. UI routes

- `/registry/facility-lifecycle/[facilityId]` file page gains: PIC nomination panel (Varapi
  assessment rendered per-axis), RFI panel, council-review panel, premises/relocation panel,
  renewal + closure actions, per-item inspection capture (visit + responses), template approval.
- `/professional/pic-nominations` — practitioner accept/decline/dispute + history.
- `/verify/facility-certificate` — public verification by code (no auth).
- Facility Mode cockpit regulatory panel (identity/premises/operator/classification/certificate/
  PIC/expiry/conditions/open actions/verification link).

## 6. Policy fields requiring HPA/council validation (seeded as pending, never enforced as law)

Facility classifications (39), application-type routes/evidence requirements, inspection-type
purposes, remediation windows (severity→days), renewal cycle + expiry model (2017 = calendar-year
31 Dec — NOT assumed), checklist templates (all), curated rules (18). Each carries
`source_manual='HPA-2017-V1'` + source pages.
