# Kafka — Clinical guidance event contracts (v1 draft)

Topic prefix: `impilo.clinical` (configure per environment).

All payloads MUST wrap in the platform `EventEnvelope` from `shared-kernel-java` when publishing from outbox relay.

## Topics

| Topic | Schema version | When emitted |
|-------|----------------|--------------|
| `impilo.clinical.knowledge.version.published` | 1.0 | After new `source_documents` row activated |
| `impilo.clinical.rule.published` | 1.0 | After rule definition approved (future workflow) |
| `impilo.clinical.guidance.recommendation.generated` | 1.0 | After `recommendation_traces` insert (assistant / prescribing) |
| `impilo.clinical.guidance.alert.fired` | 1.0 | For each high-severity `RuleAlert` (optional duplicate of trace) |
| `impilo.clinical.guidance.override.recorded` | 1.0 | After `override_records` insert |
| `impilo.clinical.pathway.session.completed` | 1.0 | Pathway session status → `COMPLETED` |
| `impilo.clinical.citizen.nudge.generated` | 1.0 | Citizen-safe nudge pipeline (future) |
| `impilo.clinical.multimorbidity.issue.detected` | 1.0 | Per DETECTED finding of the §9 multimorbidity assessment; consumed by BUTANO as FHIR `DetectedIssue` (brief.md §19). Payload is coded only — `subjectCpid`, `detectionKey`, `code`, `severity`, `contentVersion`, `detectedAt`. The engine's `message` / `explanation` / `requiredAction` are generated prose that interpolates medicine, clinic and orderer names and deliberately never travel. Idempotency key downstream is `<cpid>\|<code>`. |

## Payload sketch (`guidance.recommendation.generated`)

```json
{
  "trace_id": "uuid",
  "support_mode": "SOURCE_GROUNDED",
  "request_type": "ASSISTANT_ASK",
  "knowledge_version": "ckp-seed-1",
  "source_version": "2025",
  "rule_codes": ["ASTHMA_SABA_MONOTHERAPY"]
}
```

Implementation note: `clinical.event_outbox` table is provisioned; relay to Kafka can reuse the standard Impilo outbox publisher pattern used in other services.
