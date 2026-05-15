# Service Remediation Report

Final execution report for the activation/remediation program, including the
final hardening wave (frontend surfacing, duplicate reduction posture,
integration/event parity audit, and evidence closure).

## Section 18 Evidence Closure

### 18.1 Final status counts (services-registry baseline)

Total services evaluated: **155**

| Category | Status | Count |
| --- | --- | ---: |
| Remediation | Fixed | 153 |
| Remediation | Partially Fixed | 2 |
| Activation | Activated | 148 |
| Activation | Skeleton | 5 |
| Activation | Duplicated | 2 |
| Contract Alignment | Aligned | 79 |
| Contract Alignment | Not Applicable | 74 |
| Contract Alignment | Partial | 2 |
| Surfacing Quality | Complete | 110 |
| Surfacing Quality | Not Required | 45 |
| Integration Status | Integrated | 110 |
| Integration Status | Standalone | 45 |
| Implementation Status | live | 148 |
| Implementation Status | skeleton | 5 |
| Implementation Status | deprecated | 2 |

### 18.2 Owner-decision closure state

| Metric | Count |
| --- | ---: |
| `owner_decision_required: true` | 0 |
| Remaining policy-blocked items | 0 |

### 18.3 Final hardening wave issue counts (this wave)

| Hardening Category | Fixed | Partially Fixed | Deferred | Needs Owner Decision |
| --- | ---: | ---: | ---: | ---: |
| Frontend surfacing discoverability gaps | 10 | 0 | 0 | 0 |
| Duplicate client/component consolidation | 2 | 2 | 0 | 1 |
| Integration/event contract parity | 4 | 0 | 0 | 0 |
| **Total (wave-level)** | **16** | **2** | **0** | **1** |

## Hardening Wave Changes Applied

### A) Exhaustive frontend surfacing hardening

Implemented in `ui/one-ui-shell`:

- Registered `/dags` and `/dags/policy` in guarded route definitions.
- Added missing work-rail surfacing for Telemedicine, Secure Messaging, Bed Management, and Institutional ERP.
- Added Public Health surfacing in professional navigation and command/app registry.
- Added Intelligence link in life-zone navigation.
- Added command registry coverage for Product Registry and DAGS.
- Extended Admin hub cards for operations admin pages already routed.
- Extended Registry hub cards for trust, consent, locality-review, and facility lifecycle surfaces.

### B) Duplicate consolidation posture (safe reductions)

- Updated duplication register to include service-level alias duplicates and
  frontend duplicate client/component families.
- Kept destructive refactors out of this pass; applied only safe consolidation
  posture updates and explicit extraction candidates.
- Alias duplicates remain on approved sunset path with canonical routing retained.

### C) Integration/event parity audit and targeted fixes

- Added event-topology relationship guidance:
  - `contracts/async/README.md` (new)
  - updated `contracts/asyncapi/README.md`
  - updated `contracts/async/impilo-events.asyncapi.yaml`
- Added parity drift evidence and reconciliation notes:
  - `docs/architecture/kafka-event-catalog.md`
  - `docs/plan/EVENTING_AND_TOPICS.md`
  - `docs/architecture/SERVICE_INTEGRATION_MAP.md`
  - `services/surveillance-service/README.md`
- Enriched `docs/architecture/services-registry.yaml` with concrete
  `events_published`, `events_consumed`, and `event_evidence` for 10
  event-active services.

## Residual Risks and Follow-through

### Runtime-risk changes intentionally deferred

- No runtime Kafka topic renames or consumer retargeting were executed in this
  hardening wave (to avoid regression risk without cross-service test windows).
- Parity drifts remain documented for controlled follow-on slices:
  - none on the four targeted rails after final convergence pass
- Execution sequence, rollback gates, and phase-by-phase closure checkpoints are
  now codified in `docs/architecture/EVENT_CONTRACT_PARITY_CONVERGENCE_PLAN.md`.

### Remaining partially fixed items

- `referral-service` and `analytics-pipeline-service` remain `Partially Fixed`
  pending concrete runtime-backed contract evidence.

## Validation Evidence

Validation and checks executed in this wave:

- service-registry status recomputation from `services-registry.yaml`
- frontend TypeScript/type-check validation for `one-ui-shell`
- architecture registry validator execution (`validate-service-registry.py`)

All policy closures requested in this wave are now encoded in source and docs.
