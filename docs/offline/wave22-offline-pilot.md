# Wave 22 — Offline Pilot at the Edge

> Status: Implemented | Date: 2026-03-15

## Goal

Prove offline works for a real workflow in a real operating mode. A healthcare worker at an edge site must be able to capture patient vitals while offline, sync when connectivity returns, and have conflicts resolved through a defined review workflow.

## Prerequisites

| Wave | Dependency |
|------|-----------|
| 15 | Offline & Edge MVP (offline-sdk, offline-edge-service, JWT entitlements, capture/sync/replay) |
| 21 | Federation pilot (mTLS pod handshake, authority enforcement for edge pods) |

## Deliverables

### 1. Entitlement Issuance + Device Binding

#### Entitlement Issuance Flow

```
┌────────────┐   Request Entitlement   ┌──────────────────────┐
│ Edge Device │ ──────────────────────→ │ tshepo-offline-service│
│ (Nurse app) │                         │                       │
│             │   Signed JWT Token      │ Validates:            │
│             │ ←────────────────────── │  - Actor identity     │
│             │                         │  - Facility scope     │
│             │                         │  - Device fingerprint │
│             │                         │  - Capability grant   │
└────────────┘                         └──────────────────────┘
```

#### Device Binding Contract

| Field | Purpose | Enforcement |
|-------|---------|------------|
| `device_fingerprint` | Ties entitlement to specific device | JWT claim; verified on every offline request |
| `facility_id` | Restricts data access to facility scope | Policy engine; cross-facility access blocked |
| `max_offline_encounters` | Limits offline captures before re-sync | Counter in offline-sdk; hard stop at limit |
| `expires_at` | Time-bound offline window | JWT expiry; checked even in offline mode (clock skew tolerance) |

#### Verification Checklist

- [ ] Entitlement issued to authorized actor + registered device
- [ ] Entitlement rejected for unregistered device
- [ ] Entitlement rejected for actor without CAPTURE_VITALS capability
- [ ] Device fingerprint mismatch blocks offline requests
- [ ] Expired entitlement blocks offline capture (with clock skew tolerance)

### 2. Offline Capture + Audit

#### Capture Workflow (Nurse at Edge Site)

```
1. Nurse opens app at edge clinic (no connectivity)
2. App verifies local JWT entitlement (offline-sdk OfflineEntitlementVerifier)
3. Nurse selects patient (cached CPID from last sync)
4. Nurse captures vitals:
   - Blood pressure: 120/80 mmHg
   - Heart rate: 72 bpm
   - Temperature: 36.8°C
   - SpO2: 98%
5. Each capture → OfflineActionEntry with:
   - Unique idempotency key
   - Sequence number
   - Captured timestamp (device clock)
   - Entitlement context (actor, facility, device)
6. Actions queued in local storage (IndexedDB / SQLite)
7. Audit trail: every action logged locally with hash chain
```

#### Audit Requirements

| Audit Field | Source | Purpose |
|-------------|--------|---------|
| `entry_id` | UUID v4 | Unique action identifier |
| `actor_id` | JWT claim | Who captured |
| `device_id` | Device fingerprint | Which device |
| `facility_id` | JWT claim | Where captured |
| `captured_at` | Device clock | When captured |
| `sequence_num` | Monotonic counter | Ordering guarantee |
| `idempotency_key` | Client-generated | Deduplication on sync |
| `hash_chain_prev` | SHA-256 of previous entry | Tamper evidence |

#### Verification Checklist

- [ ] Vitals captured offline with all audit fields populated
- [ ] Sequence numbers monotonically increasing
- [ ] Hash chain integrity verifiable post-sync
- [ ] Max encounters limit enforced (hard stop)
- [ ] Offline captures persisted across app restart

### 3. Post-Sync Reconciliation + Conflict Queues

#### Sync Flow

```
Edge Device                          offline-edge-service              BUTANO (FHIR)
    │                                        │                            │
    │  POST /offline/sync                    │                            │
    │  (OfflineSyncBatch)                    │                            │
    │ ──────────────────────────────────────→ │                            │
    │                                        │  Validate batch            │
    │                                        │  Deduplicate entries       │
    │                                        │  Store in ofe_captured_actions │
    │                                        │                            │
    │                                        │  POST /offline/replay      │
    │                                        │ ──────────────────────────→│
    │                                        │                            │
    │                                        │  Per-entry results:        │
    │                                        │  201 Created / 409 Conflict│
    │                                        │ ←──────────────────────────│
    │                                        │                            │
    │  Sync result                           │  Conflicts → ofe_conflict_reviews
    │ ←────────────────────────────────────── │                            │
    │                                        │                            │
```

#### Reconciliation Rules

| Scenario | Detection | Resolution |
|----------|-----------|------------|
| Duplicate entry (same idempotency key) | BUTANO returns 409 | Auto-resolve: skip (already recorded) |
| Conflicting value (same patient, same time, different value) | BUTANO returns 409 with existing resource | Queue for clinical review |
| Stale patient reference (CPID merged) | VITO returns 301 with new CPID | Auto-remap to new CPID and retry |
| Entitlement expired during sync | JWT validation | Flag batch for admin review |
| Sequence gap | Missing sequence numbers | Flag for investigation |

#### Conflict Review Queue

```json
{
  "conflict_id": "<uuid>",
  "entry_id": "<uuid>",
  "patient_ref": "CPID-12345",
  "conflict_type": "VALUE_MISMATCH",
  "offline_value": { "code": "8867-4", "value": 72, "unit": "bpm" },
  "existing_value": { "code": "8867-4", "value": 68, "unit": "bpm" },
  "captured_at": "2026-03-14T10:00:00Z",
  "existing_recorded_at": "2026-03-14T09:55:00Z",
  "status": "PENDING_REVIEW",
  "assigned_reviewer": null
}
```

#### Verification Checklist

- [ ] Batch sync completes with per-entry status
- [ ] Duplicate entries auto-resolved (idempotent)
- [ ] Conflicting values queued for review
- [ ] Stale CPIDs auto-remapped via VITO
- [ ] Conflict review queue accessible via API
- [ ] Conflict resolution (KEEP_OFFLINE / KEEP_EXISTING / MERGE) works

### 4. Break-Glass Audited + Review Workflow

#### Break-Glass Scenario

When a clinician needs emergency access beyond their entitlement scope (e.g., accessing a patient from a different facility during an emergency):

```
1. Clinician invokes break-glass mode
2. System logs break-glass activation:
   - Actor, facility, patient, reason
   - Override type (scope extension / expired entitlement)
3. Action proceeds with elevated access
4. Break-glass event published to outbox (HIGH priority)
5. Break-glass review queue populated
6. Reviewer must review within 24 hours
```

#### Break-Glass Audit Event

```json
{
  "eventType": "impilo.offline.break_glass.activated.v1",
  "priority": "HIGH",
  "payload": {
    "actor_id": "nurse-001",
    "facility_id": "<uuid>",
    "patient_ref": "CPID-67890",
    "reason": "Emergency: patient transferred from Facility-B, no connectivity to re-scope",
    "override_type": "SCOPE_EXTENSION",
    "original_scope": ["FACILITY-A"],
    "extended_scope": ["FACILITY-A", "FACILITY-B"],
    "activated_at": "2026-03-14T14:30:00Z",
    "review_required_by": "2026-03-15T14:30:00Z"
  }
}
```

#### Review Workflow

| Step | Actor | Action |
|------|-------|--------|
| 1 | System | Break-glass event triggers review item |
| 2 | Facility Manager | Receives notification (email/SMS) |
| 3 | Reviewer | Opens break-glass review in Ops Console |
| 4 | Reviewer | Validates reason, checks patient context |
| 5 | Reviewer | Approves / Escalates / Flags for investigation |
| 6 | System | Closes review item, updates audit trail |

#### Verification Checklist

- [ ] Break-glass activation logged with full context
- [ ] Break-glass event published to outbox immediately
- [ ] Review queue item created with 24h deadline
- [ ] Notification sent to facility manager
- [ ] Review resolution recorded in audit trail
- [ ] Unreviewed break-glass items escalated after deadline

## Deliverable: Offline Pilot Pack + Reconciliation Outcomes

```markdown
# Offline Pilot Pack — Edge Site Field Test

## Test Environment
- Edge site: ___
- Device type: ___
- Connectivity profile: ___
- Pilot duration: ___
- Number of participants: ___

## Pilot Results

### Entitlement Issuance
- [ ] Entitlements issued to N devices
- [ ] Device binding verified
- [ ] Expiry enforcement working

### Offline Capture
- [ ] Total vitals captured offline: ___
- [ ] Average captures per session: ___
- [ ] Max captures before sync: ___
- [ ] Audit trail integrity verified (hash chain)

### Sync & Reconciliation
- [ ] Total sync batches: ___
- [ ] Auto-resolved duplicates: ___
- [ ] Conflicts queued for review: ___
- [ ] Stale CPIDs auto-remapped: ___
- [ ] Average sync duration: ___

### Conflict Resolution
- [ ] Total conflicts: ___
- [ ] KEEP_OFFLINE resolutions: ___
- [ ] KEEP_EXISTING resolutions: ___
- [ ] MERGE resolutions: ___
- [ ] Average resolution time: ___

### Break-Glass
- [ ] Break-glass activations: ___
- [ ] All reviewed within 24h: ___
- [ ] Escalations: ___

## Reconciliation Outcomes Summary
- Data integrity: ___% of offline captures reconciled without conflict
- Clinical impact: ___ clinical decisions affected by conflicts
- System reliability: ___% sync success rate

## Sign-Off
- [ ] Clinical Lead: _________________ Date: _______
- [ ] Security Lead: _________________ Date: _______
- [ ] Operations Lead: _________________ Date: _______
```

## Exit Criteria

- [ ] Entitlement issuance and device binding working end-to-end
- [ ] Offline capture with full audit trail verified
- [ ] Post-sync reconciliation handles all conflict types
- [ ] Break-glass activated, audited, and reviewed
- [ ] Pilot pack completed with reconciliation outcomes
