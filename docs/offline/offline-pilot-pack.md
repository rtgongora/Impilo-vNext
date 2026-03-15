# Offline Pilot Pack — Edge Site Field Test

> Wave 22 | Date: 2026-03-15

## Test Environment

| Component | Instance | Port |
|-----------|----------|------|
| Offline Edge Service | localhost | 8360 |
| TSHEPO Offline Service | localhost | 8086 |
| National Spine (TSHEPO) | localhost | 8081 |
| BUTANO (HAPI FHIR) | localhost | 8090 |
| Keycloak | localhost | 8080 |
| Kafka (KRaft) | localhost | 9092 |

## Pilot Workflow: Capture Vitals Offline

| Field | Value |
|-------|-------|
| Workflow Type | CAPTURE_VITALS |
| Consistency Class | C (Always Allowed Offline) |
| Entitlement TTL | 24 hours |
| Max Offline Encounters | 50 |
| Clock Skew Tolerance | 30 seconds |
| Break-Glass Review Deadline | 24 hours |

## Pre-Flight Checklist

- [ ] Kafka topics created:
  - `impilo.offline.entitlement.issued.v1`
  - `impilo.offline.vital.captured.v1`
  - `impilo.offline.action.replayed.v1`
  - `impilo.offline.action.conflict.v1`
  - `impilo.offline.break_glass.activated.v1`
  - `impilo.offline.break_glass.reviewed.v1`
  - `impilo.offline.sync.completed.v1`
- [ ] Offline Edge Service database migration applied (ofe_* tables)
- [ ] TSHEPO Offline Service configured with Ed25519 signing key
- [ ] HMAC secret configured in offline-edge-service
- [ ] BUTANO FHIR endpoint accessible

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/offline/capture-vitals-offline.sh` | Full offline vitals workflow end-to-end |
| `scripts/offline/break-glass-review.sh` | Break-glass activation and review cycle |

## Test Results

### 1. Entitlement Issuance + Device Binding

| Test | Result | Notes |
|------|--------|-------|
| Entitlement issued with device fingerprint | [ ] | `POST /offline/entitlements` |
| Device fingerprint stored and returned | [ ] | Verify response includes `device_fingerprint` |
| Max offline encounters configured | [ ] | Default 50, configurable |
| Entitlement rejected for expired token | [ ] | `OfflineEntitlementVerifierTest` |
| Entitlement rejected for wrong capability | [ ] | CAPTURE_VITALS required |
| Device fingerprint mismatch blocked (403) | [ ] | `DEVICE_MISMATCH` error code |

**Code coverage:**
- `Wave22DeviceBindingTest` — 4 tests (fingerprint storage, default max encounters, null device, audit payload)
- `OfflineEntitlementVerifierTest` — 7 tests (valid, expired, wrong issuer/audience, missing capability, invalid signature)

### 2. Offline Capture + Audit

| Test | Result | Notes |
|------|--------|-------|
| Vitals captured with all audit fields | [ ] | idempotency key, sequence num, hash chain |
| Sequence numbers monotonically increasing | [ ] | Client responsibility; server stores |
| Hash chain integrity verifiable | [ ] | SHA-256 hash_chain_prev → hash_chain_current |
| Max encounters limit enforced (hard stop) | [ ] | `MAX_ENCOUNTERS_EXCEEDED` error |
| Break-glass flag stored on action | [ ] | `break_glass = true` |
| Break-glass emits HIGH priority event | [ ] | `impilo.offline.break_glass.activated.v1` |

**Code coverage:**
- `Wave22OfflineVitalsTest.MaxEncounters` — 3 tests (under limit, at limit, zero = unlimited)
- `Wave22OfflineVitalsTest.HashChain` — 3 tests (prev stored, current in response, genesis)
- `Wave22OfflineVitalsTest.BreakGlass` — 3 tests (flag stored, HIGH event, non-break-glass no event)

### 3. Post-Sync Reconciliation + Conflict Queues

| Test | Result | Notes |
|------|--------|-------|
| Batch sync completes with per-entry status | [ ] | `SyncResponse` with results |
| Duplicate entries auto-resolved | [ ] | Idempotency key deduplication |
| Conflicting values queued for review | [ ] | `ofe_conflict_reviews` table |
| Device fingerprint verified on sync | [ ] | `DEVICE_MISMATCH` error |
| Conflict resolution (KEEP_OFFLINE) works | [ ] | `ConflictReviewController` |
| Conflict resolution (KEEP_EXISTING) works | [ ] | `ConflictReviewController` |
| Conflict resolution (MERGED) works | [ ] | `ConflictReviewController` |

**Code coverage:**
- `OfflineVitalsWorkflowTest.ReplayTests` — 2 tests (replay marks sent, conflict detection)
- `OfflineSyncServiceTest` — 3 tests (sync batch, correlation preservation, expired denial)

### 4. Break-Glass Audited + Review Workflow

| Test | Result | Notes |
|------|--------|-------|
| Break-glass activation logged with full context | [ ] | actor, facility, patient, reason |
| Break-glass event published to outbox | [ ] | HIGH priority |
| Review queue item created with 24h deadline | [ ] | `review_required_by` field |
| APPROVED resolution recorded | [ ] | Status update + outbox event |
| ESCALATED resolution recorded | [ ] | Status update + outbox event |
| FLAGGED resolution recorded | [ ] | Status update + outbox event |
| Already-reviewed event rejected (409) | [ ] | Idempotent review |
| Overdue events auto-escalated | [ ] | Scheduled task every 15 min |
| Invalid resolution rejected | [ ] | Only APPROVED/ESCALATED/FLAGGED |

**Code coverage:**
- `Wave22BreakGlassTest.Activation` — 5 tests (pending status, reason required, blank reason, HIGH event, default override type)
- `Wave22BreakGlassTest.Review` — 7 tests (approved, escalated, flagged, invalid, already-reviewed, outbox event, not found)
- `Wave22BreakGlassTest.Escalation` — 2 tests (overdue escalated, no escalation when none overdue)

## Propagation Path Summary

### Offline Vitals Capture (Edge → BUTANO)
```
Edge Device: Capture vitals offline
  → offline-edge-service: POST /internal/v1/offline/vitals
    → CapturedActionEntity stored (status=QUEUED)
    → Outbox: impilo.offline.vital.captured.v1
    → If break-glass: impilo.offline.break_glass.activated.v1 (HIGH)
  → After connectivity restored:
    → POST /internal/v1/offline/sync (batch)
    → POST /internal/v1/offline/replay/{entitlement_id}
      → BUTANO: POST /fhir/Observation (tagged offline-captured)
      → 201 Created → REPLAYED
      → 409 Conflict → ofe_conflict_reviews → clinical review
```

### Break-Glass Review (Ops Console)
```
Break-glass activated
  → ofe_break_glass_events (status=PENDING_REVIEW)
  → Outbox: impilo.offline.break_glass.activated.v1 (HIGH)
  → Reviewer: POST /internal/v1/offline/break-glass/{id}/review
    → status → APPROVED / ESCALATED / FLAGGED
    → Outbox: impilo.offline.break_glass.reviewed.v1
  → Scheduler: auto-escalate overdue after 24h
```

## Test Counts

| Module | Tests | Status |
|--------|-------|--------|
| offline-edge-service (Wave22DeviceBindingTest) | 4 | Implemented |
| offline-edge-service (Wave22OfflineVitalsTest) | 9 | Implemented |
| offline-edge-service (Wave22BreakGlassTest) | 14 | Implemented |
| offline-edge-service (OfflineVitalsWorkflowTest) | 5 | Existing |
| offline-edge-service (OfflineVitalsEndpointTest) | 5 | Existing |
| offline-edge-service (OfflineSyncServiceTest) | 3 | Existing |
| offline-edge-service (OfflineEdgeApiMockMvcTest) | 5+ | Existing |
| offline-sdk (OfflineEntitlementVerifierTest) | 10 | Existing |
| offline-sdk (OfflineQueueFormatTest) | 3 | Existing |
| **Total** | **58+** | |

## Sign-Off

- [ ] Clinical Lead: _________________ Date: _______
- [ ] Security Lead: _________________ Date: _______
- [ ] Operations Lead: _________________ Date: _______
