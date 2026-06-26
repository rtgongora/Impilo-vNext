# Rito — Repo Capability Audit (Quality, Safety & Client Voice)

> **Status:** DESIGN-FIRST audit. Grounded in real file paths read on branch
> `intake/rito-design` (off `claude/staging-ux-orchestration-remediation-Yypyl`).
> No implementation code written. This document decides whether to **extend** an
> existing service or **create** `services/rito-service`, and maps every reuse target.

## 1. Headline decision

**There is NO existing general quality / safety / client-voice / feedback / CAPA / QI
domain service to extend.** The capabilities that exist are *scattered, domain-specific,
and owned by other systems-of-record*:

- `support-service` = **operational IT helpdesk** (ticketing), not clinical client voice.
- `surveillance-service` = **public-health signals + environmental complaints**, not
  general service-quality / clinical-quality.
- `madi-service` = **haemovigilance** (transfusion adverse reactions) — a specialised
  vigilance SoR, not generic safety.
- `tuso-service` / `indawo-service` = **regulatory licensing inspection** of facilities/
  sites — statutory regulator workflow, not internal quality improvement.
- `forms-service` = generic JSON-schema form registry (no survey/checklist semantics).
- `channels-service` / `campaigns-service` / `notification-service` = comms/solicitation/
  send rails (consumed, never owned).
- `tshepo-audit-service` / `audit-ledger-service` = **security/access audit trails**
  (tamper-evident hash chains) — **NOT** clinical quality audit. Confirmed distinct.

**Decision: CREATE `services/rito-service`** as a new first-class domain service that owns
the *general quality + client-voice + clinical-quality-incident + CAPA/QI + supervision +
clinical-audit + experience/satisfaction* case truth, and **consumes/links** the existing
capabilities above rather than duplicating them. See the SoR boundary doc for the precise
non-duplication contract vs `patient-safety-service`, `madi` haemovigilance, and `tuso`/
`indawo` regulatory inspection.

---

## 2. Capability inventory (grounded)

Legend — **Live** = controller+service+persistence+route wired; **Partial** = some layers;
**Fixture** = hardcoded/mock; **NotWired** = code exists, no route/consumer; **Missing** = none.

### 2.1 Client voice — feedback / complaint / compliment / suggestion / grievance / satisfaction

| Capability | Existing path / route | State | Gap | Reuse-or-build |
|---|---|---|---|---|
| Operational support tickets | `services/support-service/.../api/TicketController.java` → `/internal/v1/support/tickets`; UI `ui/one-ui-shell/src/app/support/page.tsx` | **Live** | IT/operational helpdesk only; no clinical complaint semantics, no CSAT-on-closure, no quality routing | **Reuse as a routing SOURCE** (a support ticket may escalate to a Rito case). Do NOT make Rito a generic helpdesk. |
| Environmental / public-health complaints | `services/surveillance-service/.../core/EnvironmentalComplaintService.java`, `.../entity/EnvironmentalComplaintEntity.java` → `POST /internal/v1/public-health/complaints` | **Live** | Scoped to environmental public-health; **good lifecycle template** (RECEIVED→TRIAGED→INVESTIGATING→RESOLVED→CLOSED) | **Reuse lifecycle pattern**; do not extend its table for general complaints. |
| Live-event feedback (rating+comment) | `services/live-service/.../entity/LiveEventFeedbackEntity.java`, `InteractionService.submitFeedback()` → `POST /internal/v1/live/interactions/{eventId}/feedback` | **Live** | Simple rating+comment, event-scoped; no multi-question survey | **Signal source** (post-event experience) → emit to Rito. |
| Donor experience feedback | `services/madi-service/.../entity/DonorFeedbackEntity.java`, `DonorService.submitFeedback()` → `POST /internal/v1/madi/donors/{donorId}/feedback` | **Live** | Donor-drive scoped | **Signal source** → emit donor-experience signal to Rito. |
| Learning surveys / course feedback | `services/learning-service/.../api/v11/fundo/FundoStudioController.java` (SURVEY/FEEDBACK/REFLECTION activity types); UI `…/learning/surveys/[surveyId]`, `…/learning/feedback/course/[courseId]` | **Live** | LMS-scoped (training feedback) | **Learning-loop sink** — Rito learning loops can publish lessons into Fundo; Rito does not own LMS surveys. |
| Support tickets — post-resolution CSAT | (none) | **Missing** | No satisfaction capture on ticket/case closure | Build in Rito (satisfaction survey case type) + optional link from support. |
| Generic patient/client satisfaction survey (CSAT/NPS/Likert, response storage, aggregation) | (none — `forms-service` stores schema only, no responses) | **Missing** | No survey-response store, no aggregation/reporting | **Build in Rito** (survey definition references a `forms-service` schema; responses persisted in Rito). |
| Community social feedback / moderation | `services/community-service/.../SocialService.java`, `SocialModerationCaseRepository` | **Partial** | UGC moderation, not structured feedback | Out of scope; possible future implicit-sentiment signal. |
| ED/trauma patient survey | `services/pct-service/.../entity/EdTraumaSurveyEntity.java` | **NotWired** | Entity exists, no submission route; internal triage use | Leave with PCT; possible signal source. |

### 2.2 Safety — clinical incident / near-miss / adverse event / risk

| Capability | Existing path / route | State | Gap | Reuse-or-build |
|---|---|---|---|---|
| Haemovigilance (transfusion adverse reaction) | `services/madi-service/.../entity/AdverseTransfusionReactionEntity.java`, `HaemovigilanceCaseEntity.java`, `core/HaemovigilanceService.java` (`reportReaction/openCase/investigate/closeCase`); UI `…/madi/haemovigilance/page.tsx` + national dashboard; outbox `MadiOutboxPublisher` | **Live** | Transfusion-specific vigilance SoR | **SoR — do NOT duplicate.** Transfusion reactions route to madi. Rito **reuses the case-lifecycle PATTERN** (proven open→investigate→close + national dashboard aggregation). |
| Pharmacovigilance (ADR / AEFI / VigiFlow) | (none in this worktree) | **Missing here** | Built in parallel as `services/patient-safety-service` on `intake/patient-safety-pv` | **SoR = patient-safety.** Rito routes drug/vaccine/device reactions there. |
| General clinical safety incident / near-miss (falls, wrong-site, documentation, diagnostic delay, process) | (none) | **Missing** | No generic patient-safety-incident case | **Build in Rito** — this is Rito's `CLINICAL_QUALITY_INCIDENT` / `NEAR_MISS` case type (the safety incidents NOT owned by madi or patient-safety). |
| Risk register / hazard log | (none) | **Missing** | No risk register, mitigation tracking | **Build in Rito** (`RISK` case type / risk register). |
| Sentinel-event escalation | partial via `surveillance-service` signals | **Partial** | No sentinel taxonomy / regulator escalation | Build escalation in Rito; surveillance remains a public-health signal source. |
| Mortality/Morbidity (M&M) review | `services/surveillance-service/.../core/CounterService.java` (death counters) | **Partial** | Counters only; no structured M&M review/RCA | Counters stay in surveillance; **Rito owns M&M *review* workflow** if in scope (flag — see truncation gaps). |

### 2.3 Quality — audit / supervision / assessment / accreditation / checklist / CAPA / QI

| Capability | Existing path / route | State | Gap | Reuse-or-build |
|---|---|---|---|---|
| Facility **regulatory** inspection + checklist | `services/tuso-service/.../core/FacilityRegulatoryService.java`; entities `FacilityInspectionEntity`, `InspectionChecklistTemplateEntity`, `InspectionFindingEntity`, `ComplianceActionEntity`, `CommitteeReviewEntity`, `EnforcementCaseEntity` | **Live** | **Statutory regulatory licensing** of facilities (regulator-facing) | **SoR — do NOT duplicate.** Rito owns *internal quality audit + supportive supervision + accreditation-readiness self-assessment*, distinct case records. Rito **consumes tuso findings** as a quality signal and may share checklist form-packs. |
| Site **regulatory** inspection (mirror) | `services/indawo-service/.../api/SiteRegulatoryController.java`, `SiteInspectionEntity`, `SiteInspectionFindingEntity` | **Live** | Site-level regulatory | Same boundary as tuso. Signal source. |
| Compliance / corrective action tracking | `services/tuso-service/.../entity/ComplianceActionEntity.java` (+repo) | **Live (regulatory-scoped)** | Tied to regulatory findings only; no general CAPA lifecycle (RCA, preventive, due-date, owner, verification, effectiveness check) | **Build full CAPA in Rito**; tuso's compliance action stays regulatory. |
| QI plan / PDSA cycle | (none) | **Missing** | No QI project / PDSA | **Build in Rito.** |
| Clinical supervision / supportive supervision | `services/vashandi-workforce-service/` (worker profile/roster only) | **Partial (no supervision)** | No supervision visit, competency assessment, feedback-to-worker | **Build supervision in Rito**; vashandi = worker/roster source of truth. |
| Generic form schema registry (checklists/surveys) | `services/forms-service/.../api/FormSchemaController.java` → `/internal/v1/forms` (form_key unique/tenant) | **Live (generic)** | No survey/checklist question semantics, scoring, branching, or response store | **Reuse** for schema definition + validation; Rito owns checklist/survey *packs* (own form_keys) and *responses*. |
| Workflow state machine | `services/workflow-service/` | **Partial** | Generic engine; no QA/CAPA templates | Optional orchestration; Rito owns its own case state machine (see design). |
| Rules engine | `services/rules-service/` | **Partial** | Generic expression eval | Optional for checklist scoring / escalation rules. |
| Clinical assessment instruments (PHQ-9 etc.) | `ui/one-ui-shell/src/app/ehr/[patientId]/assessments/page.tsx` | **Fixture/NotWired** | No BFF API ("no experience-BFF API yet") | Out of Rito scope (clinical instrument, belongs to clinical plane); note only. |

### 2.4 Cross-cutting / security-audit distinction (confirmed)

| Capability | Path | State | Note |
|---|---|---|---|
| Access/authorization audit trail | `services/tshepo-audit-service/` (`AccessHistoryEntry`, `AuditChainService`) → `/v1/audit/*` | **Live** | **Security audit, NOT clinical quality audit.** Rito will *emit* its own domain audit events here, but does not own this. |
| Resilience append-only ledger | `services/audit-ledger-service/` → `/internal/v1/audit/records` | **Live** | Tamper-evident ops ledger. Same: distinct from clinical quality audit. |

---

## 3. Shared infrastructure to reuse (consume, never fork)

| Concern | Reuse target (grounded) |
|---|---|
| **Outbox pattern** | `libs/shared-kernel-java/.../events/CompanionOutboxPublisher.java` + `EventEnvelope.java`; copy a service template (e.g. `campaigns-service/.../events/CampaignsOutboxPublisher.java`) and the canonical outbox table (`channels-service/.../db/migration/V001__init.sql`, table `ch_event_outbox`). Rito gets its own `rito_event_outbox`. |
| **Trust context / headers** | `services/shared-core/.../auth/TrustContext*`; `libs/tech-companion/.../context/RequestContext.java`; `CompanionHeaders.java`. |
| **API response envelope** | `services/shared-core/.../response/ApiResponse`. |
| **Comms front door** | `channels-service` — link a client-voice conversation via `ch_sessions.subject_ref` to a Rito case id. |
| **Solicitation** | `campaigns-service` — define a survey/feedback solicitation campaign (`campaign_type` JSONB) targeting clients. |
| **Send engine** | `notification-service` — `POST /internal/v1/notify` or Kafka topic for case/CAPA/survey notifications. |
| **Signal ingestion pattern** | `surveillance-service/.../events/SurveillanceEventConsumer.java` — copy the `@KafkaListener` consumer pattern so Rito ingests signals from PCT/Fundo/Vashandi/Varapi/TUSO/Indawo/live/madi/support. |
| **BFF** | `experience-bff` — add a dedicated `RitoBffController` (the codebase already has ~200 sibling controllers); register the downstream base URL in `ServiceClientConfig`/`ServiceEndpoints` (known wiring gotcha — see lane plan). |

---

## 4. Port assignment (proposed)

Port allocation (`docs/runbooks/port-allocation.md`) is populated through **8380** (`live-service`).
Next free slots: **8390, 8395/8400…**.

- **Proposed: `rito-service` = 8390.**
- **Coordination flag:** `patient-safety-service` (parallel build) must take a *different* free
  port (propose **8395** or **8400**). This must be reconciled in `port-allocation.md` at build
  time so the two parallel services do not both grab 8390. See lane plan §Flyway/registry.

---

## 5. What Rito must NOT do (anti-duplication guardrails)

1. Do **not** re-implement transfusion adverse-reaction reporting — that is `madi` haemovigilance (SoR).
2. Do **not** re-implement ADR/AEFI/VigiFlow — that is `patient-safety-service` (SoR).
3. Do **not** re-implement statutory regulatory facility/site inspection + enforcement — that is
   `tuso`/`indawo` (SoR). Rito does *internal* quality audit / supervision / accreditation-readiness.
4. Do **not** become a generic IT helpdesk — that is `support-service`.
5. Do **not** fork `forms-service`, `channels-service`, `campaigns-service`, `notification-service`,
   `surveillance-service` — consume them; add only Rito's own files (see lane plan).
6. Do **not** edit `PolicyEngine.java`/OPA — produce a policy SPEC and queue it (single-writer lock).
