# Rito — Quality, Safety & Client Voice :: Implementation Report

> Branch `intake/rito-quality-safety` (off canonical `claude/staging-ux-orchestration-remediation-Yypyl`).
> Worktree `/opt/impilo/repos/impilo-rito-build`. Built as a separable lane in a larger parallel sprint.

## 1. Summary

Rito is now a **real operating service**, not a dashboard or a form. It owns the general
quality + client-voice + clinical-service-safety + assurance + accountable-improvement case
truth and runs the full operating cycle **Listen → Classify → Triage → Assign → Investigate →
Act → Verify → Close → Learn → Improve**. Delivered end-to-end:

- **Backend** `rito-quality-safety-service` (port **8391**, schema `rito`, 21 `rit_*` tables + outbox).
- **BFF** `RitoController` + `RitoPersonaController` with the full case/safety/signal/audit/CAPA/QI/
  survey/dashboard surface and persona entry points.
- **Web** 13 real, BFF-backed pages with honest loading/error/empty states.
- **Mobile** citizen feedback (submit + track) and provider safety-report slices in both apps.
- **Trust** policy spec (`rito.*` roles + checks) queued for the CZO single-writer; AI-never-auto-closes
  enforced in code; rate-limit security baseline; trust-context + tenant scoping.
- **Governance** registered in the service registry, port map, brand registry, OpenAPI contract,
  and product-truth — where Rito scores **all dimensions real, zero gaps**.

Product-truth gate: **PASS** (violations 6 ≤ baseline 6, blockers 1/1 pre-existing — Rito adds 0).
Tests: **11 service tests green** on H2; web + mobile type-clean; branding + completeness guards green.

## 2. Audit-first (reuse vs create)

Full audit: [`capability-audit.md`](capability-audit.md). Headline: **no existing general
quality/safety/client-voice/CAPA/QI service** — the capability was scattered across SoRs that Rito
**consumes/links, never duplicates**:

| Existing | What it owns | Rito relationship |
|---|---|---|
| `support-service` | IT/ops helpdesk | routing **source** only |
| `surveillance-service` | public-health/environmental signals | lifecycle pattern reused; signal source |
| `madi-service` | transfusion **haemovigilance** (SoR) | linked by id; never duplicated |
| `patient-safety-service` | **pharmacovigilance** ADR/AEFI/VigiFlow (SoR) | linked by id; routing boundary frozen |
| `tuso`/`indawo` | **statutory** regulatory inspection (SoR) | internal audit is distinct; finding = signal |
| `forms`/`channels`/`campaigns`/`notification` | schema/comms/solicitation/send rails | consumed, never forked |

Frozen boundary: [`sor-boundary-rito-vs-patient-safety.md`](sor-boundary-rito-vs-patient-safety.md).

## 3. Service identity

Registered slug **`rito`**, name **Rito**, domain **Quality, Safety & Client Voice**, full alias
list + `ShieldCheck` fallback + generated `/brand/services/rito-logo.png`, surfaced via route-prefix
inference (`/rito`, `/my-life/feedback`, `/work/{facility,above-site}/rito`) and surface coverage.

## 4. Backend (`services/rito-quality-safety-service`)

- **Schema** `V001__init.sql`: `rit_case` (+ `_party/_link/_event/_assignment/_message/_attachment`),
  `rit_safety_incident`, `rit_quality_signal`, `rit_audit`/`_tool`/`_section`/`_finding`,
  `rit_corrective_action`, `rit_qi_plan`/`_qi_task`, `rit_survey`/`_survey_response`,
  `rit_sla_policy`, `rit_escalation_rule`, `rit_outbox`.
- **Domain**: `CaseLifecycle` state machine (16 statuses, validated transitions); `CaseClassification`
  (case types → pillar, sensitive-category set, link types).
- **Services**: `CaseService` (lifecycle, SLA resolution, event+outbox emission, **AI-never-auto-closes**
  guard), `SafetyIncidentService`, `QualitySignalService` (ingest→review→convert), `AuditService`
  (tools/sections/audits/findings + compliance scoring), `ImprovementService` (CAPA verify-effectiveness
  + QI/PDSA), `SurveyService` (CSAT/NPS + low-score case spawn + aggregation), `ConfigService`
  (SLA + escalation), `DashboardService` (real aggregations, honest empty states).
- **Events**: transactional outbox → Kafka via `RitoOutboxPublisher`; `RitoSignalConsumer` ingests
  PCT/TUSO/INDAWO/MADI/patient-safety/support/Fundo signals as read-only quality signals.
- **Controllers**: 8 REST controllers under `/internal/v1/rito/**` + `RitoExceptionHandler`.

## 5. BFF

`RitoServiceClient` + `RitoController` (`/internal/v1/rito/**`) and `RitoPersonaController`
(`/internal/v1/{nompilo,mobile,work,facility,above-site}/rito/**`). Registered `ritoBaseUrl` (8391)
in `ServiceEndpoints` + `impilo.services`. Trust context (incl. Idempotency-Key) forwarded downstream.

## 6. Frontend (web)

`/rito` hub; `/rito/cases` worklist + `/rito/cases/[caseId]` detail with full lifecycle action panel;
`/rito/quality-signals`; `/rito/audits` + `/rito/audits/[auditId]`; `/rito/improvement` (CAPA+QI);
`/rito/surveys` (live aggregates); citizen `/my-life/feedback` (+`/new`, +`/[caseId]`); facility
`/work/facility/rito` + above-site `/work/above-site/rito` dashboards. All real BFF calls, no fake counts.

## 7. Mobile

Citizen app: `FeedbackScreen` (submit) + `TrackFeedbackScreen` (track by reference) wired to
`/internal/v1/mobile/rito`, registered as Personal sections. Provider app: `ReportSafetyScreen` +
`MySafetyCasesScreen` via `/internal/v1/{mobile,work}/rito`, registered in Clinical Tools. Type-clean.

## 8. Integrations (consume SoRs, never duplicate)

Kafka signal consumer (PCT/TUSO/Indawo/Madi/patient-safety/support/Fundo) → quality signals;
notification + Fundo learning recommendations emitted via outbox intent events (no fake delivery);
cross-SoR links stored as **id references only** (`linked_patient_safety_case_id`,
`linked_haemovigilance_case_id`, `linked_regulatory_finding_id`, `linked_support_ticket_id`).

## 9. Policy / trust

[`policy-spec.md`](policy-spec.md): 16 `rito.*` roles + check matrix + sensitive-category identity
protection (ABAC overlay), **queued for the CZO single-writer** (PolicyEngine/OPA not edited).
Enforced now in code: AI/system actors cannot resolve/close CRITICAL or sensitive cases; trust-context
filter; tenant scoping on every query; rate-limit security baseline; Idempotency-Key on writes.

## 10. Product-truth & validation

| Check | Result |
|---|---|
| `mvn -o test` (rito-quality-safety-service) | **11 tests green** (lifecycle, AI-guard, audit scoring, CSAT spawn, CAPA verify, signal convert, web MockMvc) |
| Rito product-truth dimensions | db/entitiesRepos/serviceLayer/controllers/**contract/bffWiring/frontendUi/tests/authzAudit = real**; mobileRefs=7; **0 gaps** |
| `check-product-truth.sh` (worktree) | PASS — violations 6 ≤ baseline 6, blockers 1/1 (Rito adds 0) |
| `node --test scripts/completeness/__tests__` | 13 pass |
| serviceBranding vitest | 7 pass (18 sovereign services) |
| web tsc (rito/feedback scope) | 0 errors |
| mobile tsc (touched files) | 0 errors |
| no-stub guard | no Rito stubs (2 pre-existing hits in unrelated files) |

## 11. Honest known gaps / next wave

- **Policy enforcement** is service-layer + queued spec only; fine-grained `rito.*` RBAC/ABAC lands
  when the CZO merges the rego. Until then, identity redaction for sensitive categories is specified
  but not yet enforced per-field at the API.
- **Survey question rendering** uses a JSONB question bank; the web survey UI shows aggregates and
  submit, not a full dynamic question renderer (forms-service schema binding is referenced, not yet
  rendered).
- **M&M review** case type is provisionally owned by Rito (see boundary doc §6) — awaiting user
  confirmation before a dedicated M&M workflow.
- **Mobile** ships a focused citizen-feedback + provider-safety slice (not the full facility/supervisor
  triage parity); `mobileUi` is reported `n/a` by the scanner pending a keyword mapping, though 7 real
  mobile references exist.
- **Document attachments** persist a `document_ref` to document-service; upload UX is not yet built.
- **Notifications/learning** are emitted as outbox intent events; the consuming services' delivery is
  out of Rito's lane (honest by design — Rito records *requested*, not *delivered*).

## 12. Commits

`docs(rito) audit+boundary` → `feat(rito) operating service` → `feat(experience-bff) facade+personas`
→ `feat(one-ui-shell) brand+logo` → `feat(one-ui-shell) routes+pages` → `feat(rito) security baseline`
→ `docs(rito) OpenAPI` → `docs(rito) policy spec` → `chore(rito) registry+product-truth` →
`feat(mobile) feedback+safety slices` → `chore(rito) product-truth regen`. All pushed.
