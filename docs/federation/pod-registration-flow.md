# Pod Registration Flow

> Wave 21 — Federation Pilot | Status: Implemented

## Overview

The pod registration flow establishes a trust relationship between a sovereign facility pod and the national spine. Registration grants the pod a scoped authority that defines what data it can access, how long it can operate offline, and which operations it may perform.

## Protocol

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

## Implementation

### API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/internal/v1/federation/pods/register` | Register a new pod |
| POST | `/internal/v1/federation/pods/{podId}/heartbeat` | Pod heartbeat |
| POST | `/internal/v1/federation/pods/{podId}/revoke` | Revoke a pod |
| POST | `/internal/v1/federation/pods/{podId}/reinstate` | Reinstate a pod |
| POST | `/internal/v1/federation/pods/{podId}/renew` | Renew registration |
| GET | `/internal/v1/federation/pods/{podId}` | Get pod status |
| GET | `/internal/v1/federation/pods/{podId}/revocation-status` | Check revocation cache |
| GET | `/internal/v1/federation/pods` | List active pods |

### Registration Request

```json
{
  "podId": "<uuid>",
  "podName": "Facility-Pod-Harare-Central",
  "podCertificate": "<PEM-encoded X.509>",
  "capabilities": ["PATIENT_REGISTRY", "CLINICAL_CAPTURE", "BILLING"],
  "region": "Harare",
  "facilityIds": ["<uuid>"],
  "requestedDataClasses": ["PATIENT", "ENCOUNTER", "OBSERVATION"],
  "requestedMaxOfflineHours": 72
}
```

### Registration Acknowledgment

```json
{
  "registration_id": "<uuid>",
  "pod_id": "<uuid>",
  "pod_name": "Facility-Pod-Harare-Central",
  "audience": "impilo-federation",
  "authority_scope": {
    "data_classes": "[\"PATIENT\",\"ENCOUNTER\",\"OBSERVATION\"]",
    "max_offline_hours": 48,
    "merge_authority": false,
    "revocation_priority": "HIGH"
  },
  "heartbeat_interval_seconds": 300,
  "status": "ACTIVE",
  "issued_at": "2026-03-15T10:00:00Z",
  "expires_at": "2026-04-14T10:00:00Z"
}
```

### Authority Scope Enforcement

| Rule | Enforcement |
|------|-------------|
| Data classes scoped to approved set | `PodRegistrationService.scopeDataClasses()` |
| Max offline hours capped at 48 | `PodRegistrationService.registerPod()` |
| Merge authority always false for pods | `PodRegistrationService.registerPod()` |
| Revocation priority always HIGH | `PodRegistrationService.registerPod()` |
| Registration validity 30 days | `PodRegistrationService.registerPod()` |

### Key Files

| File | Purpose |
|------|---------|
| `services/tshepo-service/.../federation/api/FederationControlController.java` | REST API |
| `services/tshepo-service/.../federation/core/PodRegistrationService.java` | Core logic |
| `services/tshepo-service/.../federation/core/PodRegistrationRequest.java` | Request DTO |
| `services/tshepo-service/.../federation/persistence/PodRegistrationEntity.java` | JPA entity |
| `services/tshepo-service/.../federation/persistence/PodRegistrationRepository.java` | Repository |
| `services/tshepo-service/.../federation/persistence/PodStatus.java` | Status enum |

### Pod Lifecycle States

```
  PENDING → ACTIVE → REVOKED → (reinstate) → ACTIVE
                   → EXPIRED → (renew) → ACTIVE
                   → SUSPENDED → (reinstate) → ACTIVE
```

## Pilot Script

```bash
# Register a pod
./scripts/federation/register-pod.sh http://localhost:8081 <pod-uuid> "My Pod" Harare

# Issue a federation token
./scripts/federation/issue-pilot-token.sh <pod-uuid>

# Verify end-to-end
./scripts/federation/verify-federation.sh http://localhost:8081 <pod-uuid> <token>
```
