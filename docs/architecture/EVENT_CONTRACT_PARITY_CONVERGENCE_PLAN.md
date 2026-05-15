# Event Contract Parity Convergence Plan

Runtime-safe rollout plan for closing the remaining event-topic parity drifts
without unsafe big-bang topic renames.

## Implementation Status

| Phase | Status |
| --- | --- |
| Phase 0 - Instrumentation and Readiness | Implemented (consumer metrics counters and tagged topic visibility) |
| Phase 1 - Consumer Compatibility Expansion | Implemented |
| Phase 2 - Producer Dual-Emit Window | Implemented (feature-flagged bridges) |
| Phase 3 - Canonical Preference Flip | Implemented (canonical rails are default listener targets) |
| Phase 4 - Legacy Retirement and Contract Closure | Implemented (legacy rails removed from defaults; parity docs/contracts updated) |

Reporting aggregate producer gap status: **Closed** via `data-pipeline-service`
outbox secondary emission to `analytics.reporting.aggregate` and explicit AsyncAPI
contract in `contracts/asyncapi/data-pipeline-reporting-aggregate.asyncapi.yaml`.

## Scope

This plan covers the four documented parity rails:

1. Surveillance clinical encounter rails
2. Data pipeline clinical rails
3. Data pipeline kernel client rail
4. Reporting aggregate producer gap

Reference evidence:

- `docs/architecture/kafka-event-catalog.md`
- `docs/architecture/SERVICE_INTEGRATION_MAP.md`
- `contracts/async/impilo-events.asyncapi.yaml`
- `contracts/asyncapi/README.md`

## Drift Inventory and Target Outcomes

| Rail | Current Listener Topic(s) | Current Producer Topic(s) | Target End-State |
| --- | --- | --- | --- |
| Surveillance encounter rails | `clinical.pct.encounter.completed`, `clinical.pct.death.recorded` | `pct.encounter.completed`, `pct.death.recorded` | Both sides converge on canonical `clinical.pct.*` topics with controlled dual-emit sunset |
| Data pipeline clinical rails | `clinical.pct.journey.completed`, `clinical.oros.result.available` | `pct.journey.state_changed`, `oros.result.available` | PCT and OROS publish canonical clinical topics; listeners remain stable |
| Data pipeline kernel client rail | `kernel.vito.client.registered` | `impilo.vito.identity` family | VITO emits canonical kernel registration topic during cutover window |
| Reporting aggregate rail | `analytics.reporting.aggregate` | no explicit producer literal found | Explicit producer owner designated and event contract published |

## Delivery Constraints

- No destructive topic removal in a single release.
- Consumer compatibility must be expanded before producer cutover.
- Idempotency and replay safety must be validated in each step.
- Rollback must be executable by configuration or traffic-routing change within
  one deployment cycle.

## Rollout Phases

### Phase 0 - Instrumentation and Readiness (T0)

Objective: make drift measurable before changing runtime traffic.

Actions:

- Add per-topic consume counters and lag dashboards for each affected consumer:
  - `surveillance-service`
  - `data-pipeline-service`
  - `reporting-service`
- Record baseline 7-day volume by topic and processing outcomes.
- Freeze new topic introductions on the four rails until cutover closes.

Exit criteria:

- Baseline volume and lag dashboards are available.
- Alert thresholds are set for ingest reject rate and consumer lag.

Rollback:

- Not applicable (observability-only).

### Phase 1 - Consumer Compatibility Expansion (T0 + 1 sprint)

Objective: ensure consumers can process both current and target topic names.

Actions (consumer-first):

- Update listeners to accept both legacy and canonical topics for affected rails
  using explicit topic arrays or configuration-resolved topic lists.
- Enforce idempotency for duplicated payload windows:
  - Use event envelope keys where present (`event_id`, `idempotency_key`).
  - Add defensive dedupe guards for counters prone to double increments.
- Maintain existing group IDs to avoid accidental replay fan-out.

Primary classes:

- `services/surveillance-service/src/main/java/zw/gov/mohcc/impilo/surv/events/SurveillanceEventConsumer.java`
- `services/data-pipeline-service/src/main/java/zw/gov/mohcc/impilo/pipeline/events/PipelineEventConsumer.java`
- `services/reporting-service/src/main/java/zw/gov/mohcc/impilo/reporting/events/ReportingEventConsumer.java`

Exit criteria:

- Dual-topic listeners deployed with zero increase in reject/error rates.
- No duplicate business side effects in controlled replay tests.

Rollback:

- Revert listener topic lists to pre-cutover values.
- Keep producer topology unchanged.

### Phase 2 - Producer Dual-Emit Window (T0 + 2 sprints)

Objective: emit both old and target topics while validating downstream parity.

Actions:

- Add dual emit for affected producers:
  - PCT outbox publisher maps selected events to both `pct.*` and `clinical.pct.*`.
  - OROS outbox publisher adds `clinical.oros.result.available` for result events.
  - VITO publisher adds `kernel.vito.client.registered` alongside current VITO
    event-family topics (prefer shared-kernel dual emit policy where available).
- Keep payload shape and event envelope stable across both topics.
- Add dual-emit feature flags per service for fast disable.

Primary classes:

- `services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/events/OutboxPublisher.java`
- `services/oros-service/src/main/java/zw/gov/mohcc/impilo/oros/events/OutboxPublisher.java`
- `services/vito-service/src/main/java/zw/gov/mohcc/impilo/vito/events/VitoOutboxPublisher.java`

Exit criteria:

- Consumers process canonical topics with parity to legacy throughput.
- No idempotency regressions during dual-emit overlap.

Rollback:

- Disable dual-emit flags and continue legacy-only publishing.
- Consumers remain backward compatible from Phase 1.

### Phase 3 - Canonical Preference Flip (T0 + 3 sprints)

Objective: make canonical topics authoritative while retaining short rollback.

Actions:

- Switch consumer preference and monitoring to canonical topic rails.
- Keep legacy topic subscription in passive compatibility mode for one release.
- Mark legacy topics as deprecated with dated retirement notice in contracts/docs.

Exit criteria:

- Canonical topics carry production traffic with stable SLOs.
- Legacy topic traffic drops to near-zero or expected fallback-only levels.

Rollback:

- Re-enable legacy-topic preference in consumer config.
- Preserve canonical listeners; do not remove until stability re-established.

### Phase 4 - Legacy Retirement and Contract Closure (T0 + 4 sprints)

Objective: remove temporary overlap and close parity findings.

Actions:

- Remove legacy topic listeners for migrated rails.
- Disable dual-emit paths in producers.
- Finalize contract docs:
  - `contracts/asyncapi/*` and `contracts/async/*`
  - `docs/architecture/kafka-event-catalog.md`
  - `docs/plan/EVENTING_AND_TOPICS.md`

Exit criteria:

- All four rails are either Fixed or explicitly owner-triaged with approved
  deferred date.
- Validator/reporting artifacts reflect closed parity state.

Rollback:

- Re-enable compatibility listeners from previous release tag.
- Re-open dual-emit flags only for impacted rail.

## Reporting Aggregate Producer Gap (Owner Decision Path)

`analytics.reporting.aggregate` has no explicit in-repo producer literal.
Execution requires owner assignment before runtime change:

1. Assign producer owner service (recommended: analytics-pipeline-service).
2. Publish AsyncAPI rail for aggregate production contract.
3. Implement producer behind feature flag.
4. Validate reporting-service consumption in canary.

Until assigned, keep status as `Needs Owner Decision`.

## Verification Matrix

| Check | Phase | Evidence |
| --- | --- | --- |
| Consumer dual-topic compatibility | 1 | Service integration tests + replay test logs |
| Producer dual-emit correctness | 2 | Outbox publisher tests and topic-level traffic counters |
| No duplicate side effects | 1-3 | Idempotency metrics and domain-specific reconciliation checks |
| Canonical traffic adoption | 3 | Lag/throughput dashboards by canonical topics |
| Legacy retirement safety | 4 | Zero-traffic window + rollback drill evidence |

## Change Control and Rollback Gates

- Gate A: Phase 1 cannot close without duplicate side-effect reconciliation pass.
- Gate B: Phase 2 cannot close without canary in one non-prod and one prod shard.
- Gate C: Legacy listener removal requires two consecutive stable releases.
- Gate D: Any SLO breach triggers automatic rollback to previous phase topology.
