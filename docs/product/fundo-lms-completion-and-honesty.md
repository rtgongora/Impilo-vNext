# Impilo Fundo LMS — Completion & Honest Status (2026-06-26)

Lane: `task_e7e0f1dd`, branch `intake/fundo-lms`. Scope = native vNext LMS
(`learning-service`); no external LMS / no Moodle expansion / no Varapi CPD ledger
duplication. This records what is REAL vs PARTIAL so Product Truth stays honest — the
file-existence scanner reports "0 gaps", which is not the same as journey-complete.

## REAL (built & wired before this lane)

Native LMS: catalogue, courses/modules/lessons, enrolment, progress, assessments
(pre/post-test, quiz, survey, practical + manual review), certificates (SHA-256 digest),
pathways, library, media, interactive, cohorts, sessions, reports. BFF passthrough
(~70 endpoints), 56 web pages, mobile (provider + citizen), OpenAPI contract, transactional
outbox + v1.1 events. AI authoring = safe local stub (`LearningAiStubProvider`,
provider-agnostic; no external calls by default).

## REAL (completed in this lane — was broken/missing)

- **Vashandi workforce readiness wire (was DEAD).** `learning-service` now exposes
  `GET /v1/internal/fundo/learners/{id}/cpd-summary`, the contract vashandi's
  `FundoIntegrationClient` already polled. Returns a Fundo-owned readiness signal
  (mandatory learning satisfied/at-risk/overdue, certificate validity, CPD candidates).
- **Certificate renewal/refresher/expiry lifecycle.** Scheduled sweep flips
  `ISSUED → EXPIRED`, emits a pre-expiry refresher signal, records honest notification
  intents. (`V015` migration.)
- **Notification dispatch.** Provider-agnostic adapter + scheduled dispatcher.
- **Tshepo training-gate signal.** `GET /v1/internal/fundo/learners/{id}/training-gate`
  read-model + policy spec.
- **CPD egress signal.** `certificate.issued.v1` enriched to a full CPD-candidate shape.

## PARTIAL / HONEST (do not mark "real" without these)

| Capability | Honest status |
|------------|---------------|
| **Notification delivery** | Default provider is **STUB**: intents are **RECORDED, not SENT** (`sent_at` stays null). Real delivery requires `learning.notifications.dispatch.provider=NOTIFICATION_SERVICE` + `notification-base-url`. No delivery is ever faked. |
| **Tshepo rego enforcement** | Signal + spec ready; the rego that consumes it is **QUEUED** for WS-OPA / CZO lead (PolicyEngine/OPA single-writer locked). Gate is **not yet enforced**. See `docs/policy/fundo-training-gated-access.md`. |
| **Varapi native CPD candidate ingest** | Fundo emits the candidate signal; **varapi-service has no listener yet** (queued for the provider/workforce lane). Native completions do not yet appear as CPD candidates. See `docs/policy/fundo-cpd-evidence-egress.md`. |
| **Role → native-course requirement registry** | Legacy `lrn_role_learning_requirement` maps role→legacy resource/path only. Native role→course requirements must be passed explicitly to the training-gate; a native registry is **not built** (out of lane scope). |
| **Offline (Journey G)** | Backend is **online-only**. Mobile provider app has **read-through cache** (Secure Store, online-first fallback) — NOT offline write/sync/queue. Configured = read-cache; **true offline = not configured.** No fake offline state. |
| **Recommendations** | `my-learning.recommended` = published courses the subject is not enrolled in. **Not** cadre/role-eligibility-ranked yet. |

## Non-negotiables confirmed

No external LMS dependency introduced; Moodle not expanded (legacy adapters untouched);
Varapi CPD ledger not duplicated (candidates only, no regulated points awarded);
`PolicyEngine.java` / OPA rego untouched; AI stays a safe local stub; no mock-only pages,
no dead buttons, no faked completion/offline/delivery.
