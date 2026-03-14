# Wave 15 — Offline & Edge Framework MVP

## Overview

Wave 15 delivers the Offline & Edge Framework MVP for the Impilo vNext platform. This enables healthcare workers in low-connectivity settings to capture patient vitals offline, with automatic synchronization and reconciliation when connectivity is restored.

## Architecture

```
┌──────────────┐     JWT Entitlement     ┌──────────────────────┐
│  Edge Device  │ ──────────────────────→ │  offline-edge-service │
│  (offline-sdk)│                         │  (port 8360)          │
│               │  POST /offline/vitals   │                       │
│  Local Queue  │ ──────────────────────→ │  Captured Actions     │
│               │  POST /offline/sync     │  (ofe_captured_actions)│
│               │ ──────────────────────→ │                       │
└──────────────┘                         │  Event Outbox          │
                                         │  (ofe_event_outbox)    │
                                         │         │              │
                                         └─────────┼──────────────┘
                                                   │
                                                   ▼
                                         ┌──────────────────┐
                                         │   Kafka (Spine)   │
                                         │  EventEnvelope    │
                                         └────────┬─────────┘
                                                  │
                                         ┌────────▼─────────┐
                                         │  BUTANO (HAPI FHIR)│
                                         │  (Replay target)   │
                                         └────────┬─────────┘
                                                  │ 409 Conflict?
                                         ┌────────▼─────────┐
                                         │ Conflict Review   │
                                         │ Queue             │
                                         └──────────────────┘
```

## Components

### libs/offline-sdk

Pure Java 21 library (no Spring dependency) providing:

- **OfflineEntitlementVerifier** — JWT (JWS) verification of TSHEPO capability tokens using Ed25519 (EdDSA) or RS256. Supports JWKS-based public key resolution, clock skew tolerance for edge devices, and capability-based access control.
- **OfflineEntitlement** — Parsed entitlement record with fields: tokenId, issuer, actorId, audience, tenantId, facilityId, deviceFingerprint, capabilities, maxOfflineEncounters, issuedAt, expiresAt.
- **OfflineActionEntry** — Canonical format for a single offline-captured action (self-contained with entitlement context, audit fields, idempotency key).
- **OfflineSyncBatch** — Wire format for batch synchronization of queued actions.

**TSHEPO Compatibility**: The verifier is fully compatible with JWT claims issued by `tshepo-offline-service/CapabilityTokenService`:
```json
{
  "jti": "<token-id>",
  "iss": "tshepo-offline-service",
  "sub": "<actor-id>",
  "aud": "impilo-offline",
  "iat": 1710000000,
  "exp": 1710028800,
  "tenant_id": "<uuid>",
  "facility_id": "<uuid>",
  "device_fingerprint": "<device-fp>",
  "capabilities": ["CAPTURE_VITALS", "READ_PATIENT"],
  "max_offline_encounters": 50
}
```

### services/offline-edge-service

Spring Boot 3.3.6 service (port 8360) providing:

#### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/internal/v1/offline/vitals` | JWT (X-Offline-Entitlement) | Capture a vital sign reading offline |
| POST | `/internal/v1/offline/sync` | JWT (X-Offline-Entitlement) | Push a batch of queued offline actions |
| GET | `/internal/v1/offline/conflicts` | Trust headers | List replay conflicts for review |
| GET | `/internal/v1/offline/conflicts/{id}` | Trust headers | Get a single conflict |
| POST | `/internal/v1/offline/conflicts/{id}/resolve` | Trust headers | Resolve a conflict |
| POST | `/internal/v1/offline/entitlements` | Trust headers | Issue an HMAC-based entitlement |
| POST | `/internal/v1/offline/actions` | Trust headers | Capture a generic offline action |
| POST | `/internal/v1/offline/replay/{entitlement_id}` | Trust headers | Replay queued actions to BUTANO |

#### Storage (PostgreSQL)

| Table | Purpose |
|-------|---------|
| `ofe_captured_actions` | Append-only store of offline-captured actions |
| `ofe_entitlements` | HMAC-based entitlement tokens |
| `ofe_event_outbox` | Outbox pattern for reliable Kafka publishing |
| `ofe_idempotency_keys` | Request deduplication |
| `ofe_reconciliation_batches` | Batch tracking for replay/sync operations |
| `ofe_conflict_reviews` | Review queue for replay conflicts |
| `ofe_audit_log` | Append-only audit trail (Wave 15) |

## Offline Workflow: Capture Vitals (Class C)

### Step 1: Obtain Entitlement (Online)

Before going offline, the edge device requests a capability token from `tshepo-offline-service`:

```
POST /internal/v1/offline/capability-tokens
{
  "tenantId": "<tenant-uuid>",
  "actorId": "nurse-001",
  "facilityId": "<facility-uuid>",
  "deviceFingerprint": "<device-fp>",
  "requestedCapabilities": ["CAPTURE_VITALS"]
}
```

Returns a signed JWT token with the `CAPTURE_VITALS` capability.

### Step 2: Capture Vitals (Offline)

While offline, the device captures vitals using the JWT for authorization:

```
POST /internal/v1/offline/vitals
X-Offline-Entitlement: eyJhbGciOiJFZERTQSJ9...

{
  "patientRef": "CPID-12345",
  "vitals": {
    "code": "8867-4",
    "value": 72,
    "unit": "bpm",
    "display": "Heart rate",
    "effective_date_time": "2026-03-14T10:00:00Z"
  },
  "capturedAt": "2026-03-14T10:00:00Z",
  "deviceId": "DEVICE-EDGE-001",
  "sequenceNum": 1,
  "idempotencyKey": "idem-vitals-001",
  "correlationId": "e2e-corr-123"
}
```

### Step 3: Sync (Online)

When connectivity is restored, push all queued actions as a batch:

```
POST /internal/v1/offline/sync
X-Offline-Entitlement: eyJhbGciOiJFZERTQSJ9...

{
  "batchId": "<uuid>",
  "deviceId": "DEVICE-EDGE-001",
  "actions": [
    {
      "entryId": "<uuid>",
      "patientRef": "CPID-12345",
      "actionType": "CAPTURE_VITALS",
      "payload": { ... },
      "capturedAt": "2026-03-14T10:00:00Z",
      "sequenceNum": 1,
      "idempotencyKey": "idem-001",
      "correlationId": "e2e-corr-123"
    }
  ]
}
```

### Step 4: Replay to BUTANO

Queued actions are replayed to BUTANO (HAPI FHIR) via `POST /internal/v1/offline/replay/{entitlement_id}`. Each vitals reading becomes a FHIR Observation resource.

### Step 5: Conflict Resolution

If BUTANO returns 409 (duplicate), the action enters the conflict review queue. Clinicians resolve via:

```
POST /internal/v1/offline/conflicts/{conflict_id}/resolve
{
  "resolution": "KEEP_OFFLINE",
  "notes": "Offline reading was correct",
  "resolved_by": "dr-review-001"
}
```

## Event Types

| Event | Description |
|-------|-------------|
| `impilo.offline.vital.captured.v1` | Vital captured via JWT-authenticated vitals endpoint |
| `impilo.offline.action.synced.v1` | Action synced from edge device batch |
| `impilo.offline.sync.completed.v1` | Sync batch completed |
| `impilo.offline.action.replayed.v1` | Action replayed to BUTANO |
| `impilo.offline.action.conflict.v1` | Replay conflict detected |
| `impilo.offline.entitlement.issued.v1` | HMAC entitlement issued |
| `impilo.offline.action.recorded.v1` | Generic offline action recorded |

## Security

- **JWT verification**: Ed25519 (EdDSA) signature verification using TSHEPO's JWKS
- **Capability-based access**: Token must include `CAPTURE_VITALS` for vitals endpoint
- **Clock skew tolerance**: 30 seconds default (configurable for edge devices)
- **No PII in BUTANO**: Patient references use CPID only
- **Audit trail**: All operations logged to `ofe_audit_log` and outbox events

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `impilo.offline.jwks-json` | (empty) | JWKS JSON for JWT verification |
| `impilo.offline.clock-skew-seconds` | 30 | Clock skew tolerance |
| `impilo.offline.hmac-secret` | - | HMAC secret for entitlement tokens |
| `impilo.offline.entitlement-ttl-hours` | 24 | Entitlement token TTL |
