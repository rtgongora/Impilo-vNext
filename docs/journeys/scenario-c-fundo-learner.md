# Scenario C — Fundo LMS Learner Journey (Runbook)

Catalog → enrol as a real provider → lessons to 100% → assessment →
certificate with verification digest → governed CPD writeback to VARAPI →
learner notification through the platform comms hub. 12 checks.

## Proof script

```bash
bash scripts/e2e/fundo-learner-journey.sh                     # default PROV-ZW-00001
PROVIDER_ID=PROV-ZW-00007 bash scripts/e2e/fundo-learner-journey.sh
```

## Preconditions

- Estate + seeds as Scenario A. Fundo courses seeded by learning V013
  (4 PUBLISHED under the canonical tenant; FUNDO-EHR-101 is CPD-eligible).
- Kafka opt-ins: learning-service + varapi-service listeners ON; ⑤ notification
  flip live (`LEARNING_NOTIFICATION_PROVIDER: NOTIFICATION_SERVICE`,
  `LEARNING_NOTIFICATION_BASE_URL: http://notification-service:8200`,
  `LEARNING_NOTIFICATION_DISPATCH_POLL_MS: "30000"`).

## Flow and the governance model

1. **Template prerequisite (step 0)** — the comms hub renders strictly from
   registered templates; the script idempotently registers
   `learning.certificate.issued` as the national pod (`X-Pod-ID: national`,
   `FederationAuthority.requireNational`).
2. Catalog → CPD-eligible course → structure (modules/lessons).
3. Enrol `{subjectType: PROVIDER, subjectId: PROV-ZW-…}` → start.
4. Every lesson opened + progressed to 100% — the aggregate reconciler completes
   the enrolment only when **all** lesson rows hit 100.
5. Assessment attempt → certificate issued (certificateNumber + verificationDigest).
6. **Governed CPD loop** — the certificate event
   (`impilo.learning.certificate.issued.v1`) lands a **PENDING candidate** in
   varapi. Fundo never awards regulated points: the script opens an IN_PROGRESS
   CPD cycle if absent, drives the council/registry acceptance
   (`POST /v1/internal/provider-council/fundo-cpd-candidates/{id}/accept`), and
   only then asserts `earnedPoints > 0` on the CPD summary. A bare summary poll
   can never go green — that is by design, not a bug.
7. **Notification** — issuance records a PENDING intent in
   `lrn_learning_notification`; the dispatcher (30s poll in preview) delivers via
   notification-service `POST /internal/v1/notify` and marks it SENT; the script
   asserts the `ns_notifications` row reaches SENT/DELIVERED.

## Known limits

- Assessment scoring is a stub attempt (empty answers) — grading depth is not
  asserted.
- Studio (author-side) journey is smoke-level only; learner side is the proven
  steel thread.
- Acceptance is driven by the script as a registry actor; a human council
  workflow UI exists but is not part of this proof.
