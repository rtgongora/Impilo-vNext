# Wave 21 — Federation Pilot & Pod Readiness

> Status: Not Started | Date: 2026-03-14

## Goal

Prove the sovereign pod model end-to-end. A National Spine instance and one Pod must complete the full federation handshake, enforce authority boundaries, handle revocations, and propagate merges correctly.

## Prerequisites

| Wave | Dependency |
|------|-----------|
| 17 | Federation control module in TSHEPO (pod authority enforcement, mTLS handshake) |
| 19 | Production readiness gate (SLOs, alerting, security posture verified) |

## Deliverables

### 1. Pod Registration Handshake

#### Protocol

```
┌──────────────┐                         ┌──────────────────┐
│   Pod (Edge)  │                         │  National Spine   │
│               │  1. Registration Req    │                   │
│               │ ───────────────────────→│                   │
│               │    (pod_id, pod_cert,   │  TSHEPO Federation│
│               │     capabilities)       │  Control Module   │
│               │                         │                   │
│               │  2. Registration Ack    │                   │
│               │ ←───────────────────────│                   │
│               │    (spine_cert, aud,    │                   │
│               │     authority_scope)    │                   │
│               │                         │                   │
│               │  3. mTLS Session Est.   │                   │
│               │ ←──────────────────────→│                   │
│               │                         │                   │
│               │  4. Heartbeat (periodic)│                   │
│               │ ───────────────────────→│                   │
└──────────────┘                         └──────────────────┘
```

#### Registration Request Payload

```json
{
  "pod_id": "<uuid>",
  "pod_name": "Facility-Pod-Harare-Central",
  "pod_certificate": "<PEM-encoded X.509>",
  "capabilities": ["PATIENT_REGISTRY", "CLINICAL_CAPTURE", "BILLING"],
  "region": "Harare",
  "facility_ids": ["<uuid>", "<uuid>"],
  "requested_authority_scope": {
    "data_classes": ["PATIENT", "ENCOUNTER", "OBSERVATION"],
    "max_offline_hours": 72
  }
}
```

#### Registration Acknowledgment

```json
{
  "registration_id": "<uuid>",
  "pod_id": "<uuid>",
  "spine_certificate": "<PEM-encoded X.509>",
  "audience": "impilo-federation",
  "authority_scope": {
    "data_classes": ["PATIENT", "ENCOUNTER", "OBSERVATION"],
    "max_offline_hours": 48,
    "merge_authority": false,
    "revocation_priority": "HIGH"
  },
  "heartbeat_interval_seconds": 300,
  "issued_at": "2026-03-14T10:00:00Z",
  "expires_at": "2026-04-14T10:00:00Z"
}
```

#### Verification Checklist

- [ ] Pod sends registration request with valid certificate
- [ ] Spine validates certificate chain
- [ ] Spine issues scoped authority grant
- [ ] mTLS session established using exchanged certificates
- [ ] Heartbeat interval respected
- [ ] Registration renewal works before expiry

### 2. mTLS + aud=federation JWT Validation

#### JWT Claims for Federation

```json
{
  "iss": "tshepo-federation",
  "sub": "<pod_id>",
  "aud": "impilo-federation",
  "iat": 1710000000,
  "exp": 1710003600,
  "pod_id": "<uuid>",
  "authority_scope": {
    "data_classes": ["PATIENT", "ENCOUNTER"],
    "facilities": ["<uuid>"]
  },
  "federation_version": "1.0"
}
```

#### Validation Rules

| Check | Enforcement | Failure Response |
|-------|------------|-----------------|
| mTLS certificate matches registered pod | TSHEPO federation module | 403 + `FEDERATION_CERT_MISMATCH` |
| `aud` claim equals `impilo-federation` | JWT validator | 401 + `INVALID_AUDIENCE` |
| `authority_scope` within granted scope | Policy engine | 403 + `AUTHORITY_EXCEEDED` |
| Token not expired | JWT validator | 401 + `TOKEN_EXPIRED` |
| Pod not revoked | Revocation cache check | 403 + `POD_REVOKED` |

#### Verification Checklist

- [ ] Pod with valid mTLS + JWT can access federated endpoints
- [ ] Pod with mismatched certificate is rejected (403)
- [ ] JWT with wrong `aud` is rejected (401)
- [ ] Expired JWT is rejected (401)
- [ ] Revoked pod is rejected even with valid JWT (403)

### 3. Authority Violations Enforced

#### Authority Boundary Rules

| Rule | Example | Enforcement Point |
|------|---------|-------------------|
| Pod cannot access data outside its facility scope | Pod-A requests Patient from Facility-B | TSHEPO policy engine |
| Pod cannot exceed granted data classes | Pod requests BILLING data when only granted PATIENT | Federation control module |
| Pod cannot merge without merge authority | Pod attempts patient merge | VITO merge endpoint |
| Pod cannot issue entitlements beyond its scope | Pod issues offline token for ungrantedcapability | TSHEPO offline service |

#### Violation Event Schema

```json
{
  "eventType": "impilo.federation.authority_violation.v1",
  "payload": {
    "pod_id": "<uuid>",
    "violation_type": "SCOPE_EXCEEDED",
    "requested_resource": "Patient/<cpid>",
    "granted_scope": ["FACILITY-A"],
    "attempted_scope": "FACILITY-B",
    "action_taken": "BLOCKED",
    "timestamp": "2026-03-14T10:30:00Z"
  }
}
```

#### Verification Checklist

- [ ] Cross-facility access attempt blocked and logged
- [ ] Data class violation blocked and logged
- [ ] Unauthorized merge attempt blocked and logged
- [ ] All violations produce outbox events
- [ ] Violation count metrics exposed via Prometheus

### 4. High-Priority Revocation Channel

#### Revocation Flow

```
┌──────────────┐   Revoke Command    ┌──────────────────┐
│  Spine Admin  │ ──────────────────→ │ TSHEPO Federation │
│               │                     │ Control Module     │
│               │                     │                    │
│               │                     │  1. Mark pod       │
│               │                     │     revoked in DB  │
│               │                     │  2. Publish to     │
│               │                     │     Kafka HIGH_PRI │
│               │                     │  3. Push to        │
│               │                     │     revocation     │
│               │                     │     cache (Redis)  │
│               │                     └────────┬───────────┘
│               │                              │
│               │                     ┌────────▼───────────┐
│               │                     │ All services check  │
│               │                     │ revocation cache    │
│               │                     │ on every federated  │
│               │                     │ request             │
│               │                     └────────────────────┘
```

#### Revocation SLAs

| Metric | Target |
|--------|--------|
| Time from revoke command to cache update | ≤ 5 seconds |
| Time from revoke to all services enforcing | ≤ 30 seconds |
| Revocation persistence | Survives service restart (DB-backed) |
| Cache TTL for revocation entries | Never expires (explicit reinstatement required) |

#### Verification Checklist

- [ ] Revocation command propagates to cache within 5s
- [ ] Revoked pod's next request is rejected (403)
- [ ] Revocation survives service restarts
- [ ] Revocation event published to Kafka
- [ ] Reinstatement flow works (admin re-enables pod)

### 5. Merge/Revocation Propagation Verified

#### Merge Propagation (Spine → Pod)

When a patient merge occurs at the Spine (VITO), the merge must propagate to all Pods that hold copies of the affected patient:

```
Spine VITO: Merge Patient-A → Patient-B
  → Kafka: impilo.registry.patient.merged.v1
    → Pod-1 VITO replica: Apply merge locally
    → Pod-2 VITO replica: Apply merge locally
    → Verify: All pods converge to same CPID
```

#### Revocation Propagation (Spine → All Pods)

When a pod is revoked, all other pods must be notified to stop accepting federation traffic from the revoked pod:

```
Spine: Revoke Pod-3
  → Kafka HIGH_PRI: impilo.federation.pod.revoked.v1
    → Pod-1: Add Pod-3 to local deny list
    → Pod-2: Add Pod-3 to local deny list
    → Verify: Pod-3 cannot reach any pod or spine
```

#### Verification Checklist

- [ ] Patient merge at Spine propagates to all registered Pods
- [ ] Merge convergence verified (all Pods report same CPID)
- [ ] Pod revocation propagates to all other Pods
- [ ] Revoked pod cannot communicate with any other pod
- [ ] Propagation latency measured and within SLA (≤ 60s for merges, ≤ 30s for revocations)

## Deliverable: Federation Pilot Pack

```markdown
# Federation Pilot Pack — National Spine ↔ Pod

## Test Environment
- Spine instance: ___
- Pod instance: ___
- Network configuration: ___

## Test Results

### Registration Handshake
- [ ] Registration request/ack completed
- [ ] mTLS session established
- [ ] Heartbeat functioning

### JWT Validation
- [ ] Valid federation JWT accepted
- [ ] Invalid audience rejected
- [ ] Expired token rejected
- [ ] Revoked pod rejected

### Authority Enforcement
- [ ] Cross-facility access blocked
- [ ] Data class violation blocked
- [ ] Unauthorized merge blocked
- [ ] Violation events in outbox

### Revocation Channel
- [ ] Revocation propagation ≤ 5s to cache
- [ ] Revocation enforcement ≤ 30s all services
- [ ] Revocation survives restart
- [ ] Reinstatement works

### Merge Propagation
- [ ] Spine merge reaches all Pods
- [ ] CPID convergence verified
- [ ] Propagation latency ≤ 60s

## Sign-Off
- [ ] Federation Architect: _________________ Date: _______
- [ ] Security Lead: _________________ Date: _______
- [ ] Platform Lead: _________________ Date: _______
```

## Exit Criteria

- [ ] Pod registration handshake completes successfully
- [ ] mTLS + aud=federation JWT validation enforced
- [ ] Authority violations blocked and audited
- [ ] Revocation propagation within SLA
- [ ] Merge propagation verified with CPID convergence
- [ ] Federation pilot pack signed off
