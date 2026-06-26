# Fundo Campaign & Surveillance → Learning Bridges (B6)

Status: learning-side contract REAL; BFF/jobs consumers QUEUED/PARTIAL (2026-06-26)

## Learning-side contract (real)
`POST /internal/v1/learning/v11/enrolments/bulk` — bulk-enrol a list of subjects into a
course, idempotent per subject (reuses the single-enrolment path). This is the endpoint
the bridges call. Returns `{created, alreadyEnrolled, total}`.

## Campaign → learning (BFF orchestration — QUEUED)
A campaign with a learning component calls the existing **campaigns-service** (SoR);
a thin BFF `campaign-learning` orchestration maps the campaign's enrolled target group to
`POST /v11/enrolments/bulk`. Campaigns stays the campaign SoR; learning stays the enrolment
SoR. No campaign tables in learning-service.

## Surveillance → learning (event consumer — QUEUED)
A BFF/jobs **consumer** of `surveillance.case.confirmed.v1` (surveillance-service already
emits via its outbox) maps outbreak-type → required pathway (reuse
`RoleLearningRequirementEntity` / `SubjectPathAssignmentEntity` as the mapping/seed) and
calls `/v11/enrolments/bulk` for the assigned tracers. Consume-only; no surveillance logic
in learning.

## Honest partial
The BFF campaign-learning orchestration and the surveillance event consumer are **QUEUED**
until a broker/consumer is wired (same honesty as the notification-dispatcher STUB). The
learning-side contract they depend on is in place and tested.
