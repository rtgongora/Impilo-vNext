# Wave 8 — Offline Vitals End-to-End Workflow

## Overview

"Capture vitals offline" is the reference Class C offline workflow.
It demonstrates the full offline pipeline: entitlement → local capture →
replay to BUTANO (FHIR) → conflict resolution → audit trail.

## Workflow

```
┌──────────┐    ┌───────────────┐    ┌────────────────┐    ┌──────────┐
│  Device   │───▶│ offline-edge  │───▶│  BUTANO (FHIR) │───▶│  Review  │
│  (Edge)   │    │  service      │    │  Observation   │    │  Queue   │
└──────────┘    └───────────────┘    └────────────────┘    └──────────┘
     │                │                       │                  │
     │  1. Issue      │  2. Capture           │  3. Replay       │  4. Resolve
     │  Entitlement   │  Actions              │  to FHIR         │  Conflicts
```

## Step 1: Issue Entitlement

**Before going offline**, the clinician requests an entitlement token:

```
POST /internal/v1/offline/entitlements
{
  "actorId": "nurse-jane-001",
  "facilityRef": "facility-harare-central",
  "workflowType": "CAPTURE_VITALS"
}
```

Response includes a signed `token_hash` and `expires_at` (default: 24 hours).
An `impilo.offline.entitlement.issued.v1` audit event is emitted.

## Step 2: Capture Actions (Offline)

While offline, the device captures vitals and sends them to the edge service:

```
POST /internal/v1/offline/actions
Headers: Offline-Entitlement: <signed-token>
{
  "entitlementId": "uuid",
  "patientRef": "cpid-123",
  "actionType": "VITAL_SIGN",
  "payload": {
    "code": "8867-4",
    "code_system": "http://loinc.org",
    "display": "Heart rate",
    "value": 72,
    "unit": "bpm",
    "effective_date_time": "2026-03-14T10:00:00Z"
  },
  "capturedAt": "2026-03-14T10:00:00Z",
  "deviceId": "device-mobile-001",
  "sequenceNum": 1
}
```

Actions are stored locally with status `QUEUED`.
Each capture emits `impilo.offline.action.recorded.v1`.

## Step 3: Replay (When Online)

When connectivity is restored, replay all queued actions:

```
POST /internal/v1/offline/replay/{entitlement_id}
```

For each CAPTURE_VITALS/VITAL_SIGN action:
1. Builds a FHIR R4 Observation resource
2. POSTs to BUTANO `/fhir/Observation`
3. Tags the Observation as `offline-captured`
4. On success → status = `REPLAYED`, emits `impilo.offline.action.replayed.v1`
5. On BUTANO 409 → status = `CONFLICT`, creates conflict review entry

Batch status: `COMPLETED`, `CONFLICTS_PENDING`, or `PARTIAL`.

## Step 4: Conflict Resolution

If BUTANO detects a duplicate observation:

```
GET /internal/v1/offline/conflicts?status=PENDING
```

Resolve a conflict:

```
POST /internal/v1/offline/conflicts/{conflict_id}/resolve
{
  "resolution": "KEEP_OFFLINE",  // or KEEP_EXISTING, MERGED
  "resolved_by": "dr-smith",
  "notes": "Offline reading is more recent"
}
```

Conflict events emit `impilo.offline.action.conflict.v1`.

## Audit Trail

Every stage of the workflow emits an outbox event:

| Event Type                            | Stage            |
|---------------------------------------|------------------|
| `impilo.offline.entitlement.issued.v1`| Entitlement      |
| `impilo.offline.action.recorded.v1`   | Capture          |
| `impilo.offline.action.replayed.v1`   | Replay (success) |
| `impilo.offline.action.conflict.v1`   | Replay (conflict)|

## Database Tables

| Table                      | Purpose                          |
|----------------------------|----------------------------------|
| `ofe_entitlements`         | Signed entitlement tokens        |
| `ofe_captured_actions`     | Offline-captured actions         |
| `ofe_reconciliation_batches`| Replay batch tracking           |
| `ofe_conflict_reviews`     | Conflict review queue            |
| `ofe_outbox_events`        | Transactional outbox for Kafka   |

## Consistency Class Integration

Offline capture endpoints are registered as Class C in the ActionRegistry:
- `ConsistencyClassFilter` enforces `Offline-Entitlement` header presence
- The entitlement token is verified via `OfflineEdgeService.verifyEntitlementToken()`

## Testing

See `OfflineVitalsWorkflowTest` in `services/offline-edge-service/src/test/`.
Covers: entitlement issuance, capture with valid/expired/missing entitlement,
replay with BUTANO success/conflict, audit event emission.
