# HPA Regulatory Platform — Implementation Report

**Date:** 2026-07-11
**Repo:** `/opt/impilo/repos/Impilo-vNext` · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
**Starting commit:** `c04aa05a7` · **Final commit:** `d3306e1b9`
**Source pack:** `/home/robert/tuso-varapi-hpa-regulatory-pack` (HPA Registration, Inspection and Renewal Manual, Vol 1, 2017 — the **current operative HPA manual**, confirmed in force by the programme owner on 2026-07-11, effective until formally superseded)

## 1. What was built

Tuso moved from a facility directory + generic regulatory engine to a national facility
**registration / inspection / licensing / renewal / material-change / regulatory-status**
capability; Varapi gained a unified, time-specific **PIC eligibility assessment** truth with
credential-change events. No new service was created; no Tshepo files were touched; money
remains COSTA/MusheX references only; organisations remain org-registry territory.

## 2. Commits (all pushed)

| Commit | Scope |
|---|---|
| `2d63f99b9` | tuso backend: V018 migration, premises, catalogues, versioned rules, PIC nomination FSM, inspection visits/responses, RFI, council reviews, verification codes, renewal scheduler, new controller surfaces, Varapi client, credential-event consumer |
| `ca07722c2` | varapi: V023 snapshot table, `PicEligibilityAssessmentService` (per-axis, asOf-time-specific), interop assessment endpoints, `varapi.provider.eligibility.changed` events, envelope hygiene |
| `7375c0970` | design doc (gap matrix + reconciliation) + tuso `VARAPI_BASE_URL` runtime wiring |
| `2ef103d64` | experience-bff: whitelisted regulatory mirror + public certificate verification route (permitAll) |
| `4e64120d5` | one-ui-shell: 28 hooks + facility-file panels (PIC/RFI/council/premises/visits), `/professional/pic-nominations`, `/verify/facility-certificate`, cockpit regulatory panel (routes 698→700) |
| `d3306e1b9` | runtime-proof fixes: committee decisions permitted from inspection-phase states; NON_COMPLIANT visit responses open CAPA; public verify lazy-proxy fix |

## 3. Data model

**Tuso `V018__hpa_regulatory_platform.sql`** — new tables: `facility_premises`,
`facility_premises_occupancy` (shared premises = >1 active occupancy),
`regulatory_application_type` (8-entry catalogue), `facility_classification` (39),
`inspection_type_reference` (7), `regulatory_rule` (19 versioned rules incl. 3 CONFIG
`value_json` rules — remediation windows, renewal cycle, renewal-due window),
`application_information_request` (RFI), `external_council_review`, `pic_nomination`,
`inspection_visit`, `inspection_response`. Additive columns on `facility_application`
(catalogue/premises/unit/fee/payment refs + unique `application_number`),
`practitioner_in_charge_assignment` (review axes + predecessor link), `facility_unit`
(`pic_required`), `inspection_checklist_template` (governance/approval columns; V006 seeds
backfilled to `PENDING_REGULATOR_APPROVAL`), `facility_certificate` (unique
`verification_code`, unit scope, conditions, `status_reason`), `inspection_finding`
(response link, extension, recurrence), `facility_document` (supersedes chain).

**Varapi `V023__pic_eligibility_snapshot.sql`** — `provider_eligibility_snapshot`
(per-axis JSON evidence, purpose, asOf, result).

All seeds carry `HPA-2017-V1` provenance with source pages. Following the programme
owner's source-authority confirmation (2026-07-11), faithful direct representations of
the manual seed **ACTIVE** (16 principle rules + the manual-express CONFIG timeframes)
and the reference catalogues seed `VALIDATED_CURRENT_SOURCE`; items remain
`PENDING_REGULATOR_APPROVAL` only for substantive reasons recorded per row (see §9a).
No fee amount is hard-coded anywhere — the manual defers amounts to Statutory
Instrument 78 of 2017, so the platform records fee *requirements* with configurable
references only. All timeframes resolve through versioned `RegulatoryRuleService`
CONFIG rules at runtime, so future amendments apply without code changes.
`raw_inspection_requirement_candidates.*` was **not** loaded as production truth.

## 4. APIs

**Tuso** `/v1/internal/facility-registry/**` (`HpaRegulatoryOperationsController` +
patched `FacilityRegulatoryController` engine): application-types, classifications,
rules (+approve), premises (+occupancies), information-requests (open/respond/close),
council-reviews, pic-nominations (nominate → respond → record-review → activate →
withdraw), pic-assignments/{id}/resolve-review, inspections/{id}/visits,
visits/{id}/responses (+complete). Public: `GET /v1/public/facilities/certificates/verify/{code}`
(disclosure-limited; 404 on unknown; honest status for expired/superseded).

**Varapi** `POST|GET /v1/internal/interop/eligibility/assessments`
(`TusoInteropController`): per-axis identity/council/registration/practising-certificate/
licence/restrictions verdicts (PASS/WARN/FAIL), asOf time-specificity, persisted snapshot,
result ELIGIBLE / CONDITIONAL / INELIGIBLE — council standing is never a boolean.

**BFF** `/internal/v1/facility-registry/**` explicit whitelisted mirror
(`HpaRegulatoryBffController`) + `/internal/v1/public/facility-certificates/verify/{code}`
(permitAll). Upstream rejections surfaced verbatim (`REGULATORY_ACTION_REJECTED`).

## 5. Events

Tuso outbox (legacy emit mode preserved): application lifecycle, `premises.relocated`,
PIC nomination/assignment transitions, visit completion summaries — every row carries
`pod_id` + `idempotency_key` (proven: 39/39 in the run). Varapi emits
`varapi.provider.eligibility.changed` (aggregate CREDENTIAL → topic `varapi.credential`);
tuso's `VarapiCredentialEventConsumer` flags affected ACTIVE PIC assignments
`UNDER_REVIEW` — audited state, never deletion.

## 6. Experience

Facility file: PIC nominations, information requests, council reviews, premises, and
inspection visits panels. `/professional/pic-nominations` (practitioner accept/decline —
health-ID-anchored: only the nominated practitioner may respond).
`/verify/facility-certificate` (public, `?code=` prefill). Ops cockpit regulatory status
panel. Route census 700; hook tests 88/88; `tsc` clean.

## 7. Runtime proof — 36/36 PASS

Evidence: [`reports/journeys/hpa-runtime-proof-20260711/`](../../reports/journeys/hpa-runtime-proof-20260711/)
(journal, per-journey JSON, the executable script `hpa-journeys.sh`).

Rig: virgin scratch Postgres 16 — tuso V001→V018 and varapi V001→V023 applied cleanly from
zero; both jars booted against it (OAuth test-bypass flag, Kafka listeners off — consumer
seam exercised in-process). **Not** run against preview/staging/production (deployment
explicitly not authorised).

| Journey | Verdict |
|---|---|
| J1 initial private registration: create → number → submit → council gate blocks premature approval → RFI park/return → council ENDORSED → inspection visit with 3 structured responses → NON_COMPLIANT derives finding (rule-driven 30d) + CAPA → verify action → committee APPROVED → `REGISTERED_ACTIVE` + certificate w/ verification code | PASS |
| J2 government route approved **without** council review (route differentiation) | PASS |
| PIC lifecycle: live varapi assessment (per-axis, snapshot persisted) → nomination → foreign practitioner blocked (negative) → accept → activate (effective-dated) | PASS |
| J5 PIC succession: predecessor end-dated historically, successor links `predecessor_assignment_id` | PASS |
| J6 credential change → `eligibility.changed` event → assignment `UNDER_REVIEW` (audited, not deleted) | PASS |
| J4 shared premises: two regulated facilities, one premises, distinct identities | PASS |
| J8 relocation: old certificate SUPERSEDED ("not transferable"), primary occupancy switched, history preserved | PASS |
| J9 renewal: successor certificate; predecessor preserved SUPERSEDED | PASS |
| J10 voluntary closure: explicit human decision; facility record preserved | PASS |
| J12 public verification: public fields only, no confidential leakage, unknown code → 404 | PASS |
| Negative guards: illegal `VOLUNTARILY_CLOSED → PENDING_INSPECTION` rejected; premature approval blocked by council gate | PASS |
| Cross-cutting: 39 outbox events all hygienic, 32 audit events, 8-entry status-history chain, reload continuity | PASS |

## 8. Gates

tuso 133/133 · varapi 202/202 · experience-bff compile clean · UI `tsc` clean, routes 700,
hook tests 88/88 · migrations proven virgin-DB-clean.

## 9a. Rule / template activation status (source-authority correction, 2026-07-11)

The manual is the current operative source; activation reflects **fidelity**, never the
publication year.

**ACTIVE (18 of 19 rules)** — approved as
`PROGRAMME_OWNER_SOURCE_AUTHORITY_CONFIRMATION_2026-07-11`, effective-dated, full
provenance retained:
- All 16 principle rules (faithful, page-cited representations).
- `REMEDIATION_WINDOW_DAYS` `{CRITICAL:14, MAJOR:30, MINOR:30}` — manual §5: "from two
  (2) weeks to a month … critical shortfalls, a shorter time frame". The former
  `OBSERVATION:60` entry was **removed** (not stated in the manual; observations fall to
  the engine's 30-day default within the manual's range).
- `RENEWAL_CYCLE_MONTHS` `{mode:CALENDAR_YEAR, months:12}` — manual §3.1: certificates
  are valid for a calendar year, up to 31 December. The engine now computes calendar-year
  expiry (previously rolling 12 months — that was platform interpretation, corrected).

**PENDING (1 rule + 4 templates)** — each for a precise substantive reason:
- `RENEWAL_DUE_WINDOW_DAYS` `{days:90}` — the manual prescribes no renewal-reminder
  window; this is a platform operational parameter awaiting explicit governance approval.
- 4 inspection checklist templates — their content is **sample/skeleton material** (3
  generic items each), not a faithful extraction of the manual's requirement schedules
  (e.g. manual pp. 38–40 lab requirements). They stay pending on fidelity grounds until
  manual-derived items are curated and published through template governance.

## 9. Unresolved policy questions (for the regulator / PO)

1. **Preview deployment awaits explicit authorisation** — nothing from this mission is
   deployed; the permission gate correctly denied a preview roll and it was not retried.
2. Fee **amounts** (application, renewal, penalty, noncompliance) are defined by
   Statutory Instrument 78 of 2017 and its successors, not by the manual — the platform
   records fee requirements and references; amounts must be configured from the statutory
   instrument, never invented.
3. Faithful checklist-template extraction from the manual's requirement schedules
   (pp. 16–40+) remains to be curated and published through template governance.
4. Council review integration is a recorded human step (MDPCZ etc.); machine-to-machine
   council interfaces are out of scope until agreements exist.

## 9b. Inspection content completion wave (2026-07-12)

The four V006 "Sample checklist" skeletons are **retired from operational use** and the
manual's minimum-requirement schedules (pp. 16–73) are now the operational inspection
content, delivered through a composable, versioned catalogue (migration **V019** +
content resources + `InspectionContentSeeder`).

**Catalogue:** 4 sample templates RETIRED (rows preserved, relabelled "SAMPLE /
NON-REGULATORY / RETIRED", rejected at scheduling) · **38 ACTIVE requirement modules**
(1 common "All Health Institutions" pp16–19 + 37 service-specific — every schedule in
the manual incl. all imaging modalities, theatre, ICU, A&E, ambulance and the
air-ambulance explicit checklist with its own item numbering preserved) · **1,172
structured requirements** (stable codes, per-item source page + heading, obligation
distinctions MANDATORY/OPTIONAL/RECOMMENDED/WHERE_APPLICABLE/WHERE_POSSIBLE, 35
grouped equipment/tray lists with parent/component visibility, 37 numeric measurement
items, quantities/alternatives/conditionals preserved verbatim-faithfully; **no invented
severity, scoring or pass thresholds** — enforced by the seeder's activation proof and
`InspectionContentCoverageTest`) · **39 ACTIVE compositions** (common module first,
never duplicated) · **73 applicability mappings** — all 39 V018 classifications resolve;
regulated units (pharmacy/lab/theatre/ICU/imaging/…) attach their modules automatically;
an unmapped profile is **rejected with an explicit gap message**, never silently given a
generic checklist.

**Governance:** pipeline DRAFT → SOURCE_RECONCILED → VALIDATED_CURRENT_SOURCE → ACTIVE;
activation honoured only when the automated proof passes (per-item provenance, unique
codes, no invented policy, review-reasons present); versions are immutable
(checksum-guarded — content changes require a new version); inspections freeze exact
module versions at scheduling (`composition_code` + `module_versions`), so historical
inspections retain their original content. **20 items REVIEW_REQUIRED** individually —
each quoting the manual's genuine ambiguity (e.g. the air-ambulance duplicate "1-04"
numbering, "Hydrocortisone x vials" without a count, A&E outlet-count contradiction,
mammography FANR/RPAZ authority inconsistency) — without holding the catalogue pending.
Full register: [content-catalogue-register.md](../../reports/journeys/hpa-content-proof-20260712/content-catalogue-register.md).

**Renewal reminders:** `RENEWAL_DUE_WINDOW_DAYS` (90) activated as
**PROGRAMME_OPERATIONAL_POLICY** (approved by PROGRAMME_OWNER, effective 2026-07-12 —
the manual prescribes no reminder window; no claim it does; no effect on certificate
validity, which remains 31 December per §3.1). New `RENEWAL_REMINDER_MILESTONE_DAYS`
{90, 60, 30, 7} — configurable milestones; the renewal scheduler emits
`tuso.facility.certificate.renewal_reminder` outbox events at each milestone with
per-day idempotency keys.

**Runtime proof (fresh V001→V019 rig):** original workflow suite re-proven **36/36**
and the new content suite **34/34** — medical-consulting, pharmacy (save/resume +
progress + missing-evidence), laboratory (inspector-recorded measurement), hospital
with 5 regulated units (merged modules, common module exactly once), routine inspection
deriving findings + CAPA from both common (ALL-*) and specific (PHM-*) items,
multidisciplinary visit with 3 attributed contributors, air-ambulance via the manual's
own checklist, and negatives (unmapped facility type rejected; retired sample template
rejected). Evidence:
[reports/journeys/hpa-content-proof-20260712/](../../reports/journeys/hpa-content-proof-20260712/).

**Gates:** tuso 144/144 (incl. `InspectionContentCoverageTest` — the automated
activation proof — and reminder-milestone tests) · experience-bff 866/866 (new content
mirrors) · UI `tsc` clean + hook suites 132/132 (checklist + progress rendering in the
facility file; no route changes).

## 10. Honest gaps / deferred

- UI journeys proven at typecheck/route/hook level only — browser journeys need the
  authorised deploy.
- Kafka consumer proven via the service seam (no broker in rig); broker-path proof lands
  with the next full-boot.
- J3 (separate unit registration) partially covered: `ADD_UNIT` engine path + unit-scoped
  certificates exist; not scripted end-to-end. J11 enforcement uses the pre-existing
  `enforcement_case` engine; not re-proven this wave.
- ~~Checklist templates pending on fidelity grounds~~ — CLOSED by §9b: manual-derived
  module catalogue is live; samples retired. Remaining content follow-ups: the 20
  REVIEW_REQUIRED items need regulator clarification; module authoring/versioning UI
  (content is resource+seeder-managed, governed and immutable) is a future convenience.
- COSTA fee integration = reference fields only (by ownership design); no tariff logic in tuso.
