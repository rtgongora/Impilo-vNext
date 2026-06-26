# Fundo → Varapi CPD Evidence Egress — Integration Point

Status: **SIGNAL READY in Fundo; consumer wiring queued for the provider/workforce lane** (2026-06-26)
Owner lane: Fundo LMS (`task_e7e0f1dd`) provides the signal. Consumer = varapi-service
(provider/workforce round-2 lane).

## Boundary

- **Varapi = CPD ledger authority.** It decides CPD credit and owns
  `FundoCpdCandidateEntity` / `FundoCpdGovernanceService.ingestCompletion(...)`.
- **Fundo does NOT duplicate the ledger and does NOT award regulated CPD points.**
  It surfaces completions as *evidence* (`GET /internal/v1/learning/v11/cpd/evidence`,
  `/cpd/eligible-completions`) and emits completion/certificate events as
  *CPD candidates*.

## Current state (verified 2026-06-26)

- Legacy path (Moodle): external completion → Varapi signed webhook
  (`FundoSignatureUtil`) → `FundoCpdGovernanceService.ingestCompletion` → Varapi pushes
  the learning-side fact back to learning-service via
  `LearningPlatformSyncClient` → `POST /internal/v1/learning/integrations/varapi-fundo-completion`.
- **Native Fundo path (new v1.1 LMS): GAP.** When a learner completes a native course
  and a certificate is issued in learning-service, Fundo emits
  `impilo.learning.certificate.issued.v1` and `impilo.learning.course.completed.v1`, but
  **varapi-service has no Kafka listener on `platform.learning.events`** (its only
  listener is `MushexPaymentStatusChangedListener`). So native completions never become
  CPD candidates.

## Fundo-side change made in this lane

The `certificate.issued.v1` event payload is now a complete CPD-candidate shape so the
consumer needs no extra lookups:

```jsonc
{
  "tenantId": "…",
  "certificateId": "…",
  "enrolmentId": "…",
  "courseId": "…",
  "subjectType": "PROVIDER",
  "subjectId": "…",            // provider public/worker id
  "certificateNumber": "…",
  "title": "…",
  "issuedAt": "…",
  "validUntil": "…",
  "cpdEligible": true,
  "suggestedCpdPoints": 5,      // SUGGESTED only — Varapi decides actual credit
  "verificationDigest": "…"     // SHA-256, tamper-evident (not PKI-signed)
}
```

## Queued consumer wiring (varapi-service — NOT built in this lane)

The provider/workforce lane should add a `@KafkaListener` on `platform.learning.events`
filtering `eventType == impilo.learning.certificate.issued.v1 && cpdEligible == true`,
mapping the payload to `FundoCpdGovernanceService.ingestCompletion(...)`. This keeps the
CPD ledger inside Varapi while consuming Fundo's native completion signal. Idempotency:
dedupe on `certificateId` / `verificationDigest`.

> Not built here because varapi-service is another lane's SoR. Editing it would cross the
> consume-not-duplicate boundary. Fundo's responsibility — emit a complete, consumable
> signal — is satisfied.
