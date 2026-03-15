# High-Priority Revocation Channel

> Wave 21 — Federation Pilot | Status: Implemented

## Overview

The revocation channel provides a high-priority path to immediately block revoked pods from accessing any federated resource. It uses a multi-layer architecture to guarantee enforcement within SLA targets.

## Architecture

```
┌──────────────┐   Revoke Command    ┌──────────────────┐
│  Spine Admin  │ ──────────────────→ │ TSHEPO Federation │
│               │                     │ Control Module     │
│               │                     │                    │
│               │                     │  1. Mark pod       │
│               │                     │     revoked in DB  │
│               │                     │  2. Update local   │
│               │                     │     revocation     │
│               │                     │     cache (L1)     │
│               │                     │  3. Publish to     │
│               │                     │     Kafka HIGH_PRI │
│               │                     │  4. Outbox event   │
│               │                     │     for audit      │
│               │                     └────────┬───────────┘
│               │                              │
│               │                    Kafka: impilo.federation.pod.revoked.v1
│               │                              │
│               │                     ┌────────▼───────────┐
│               │                     │ Consumer 1: TSHEPO  │
│               │                     │ → revocation cache  │
│               │                     │   (cross-instance)  │
│               │                     ├────────────────────┤
│               │                     │ Consumer 2: VITO    │
│               │                     │ → local deny list   │
│               │                     │   (merge blocking)  │
│               │                     └────────────────────┘
│               │
│               │              ┌─────────────────────────────┐
│               │              │ FederationIdentityFilter     │
│               │              │ (every service, every req)   │
│               │              │                              │
│               │              │ Checks revocation cache      │
│               │              │ BEFORE JWT validation        │
│               │              │ → 403 POD_REVOKED            │
│               │              └─────────────────────────────┘
```

## SLAs

| Metric | Target | Mechanism |
|--------|--------|-----------|
| Revoke command to cache update | ≤ 5 seconds | Direct cache write on same instance; Kafka for cross-instance |
| Revoke to all services enforcing | ≤ 30 seconds | Kafka consumer propagation |
| Revocation persistence | Survives restart | DB-backed + startup hydration |
| Cache TTL for revocations | Never expires | Explicit reinstatement required |

## Implementation

### Revocation Flow

1. **Admin calls** `POST /internal/v1/federation/pods/{podId}/revoke`
2. **PodRegistrationService.revokePod()** executes:
   - Sets `status = REVOKED` in DB
   - Records `revoked_at`, `revoked_by`, `revocation_reason`
   - Calls `PodRevocationCacheService.markRevoked(podId)` — **immediate L1 cache update**
   - Publishes `impilo.federation.pod.revoked.v1` event to outbox
3. **Outbox relay** publishes event to Kafka topic `impilo.federation.pod.revoked.v1`
4. **Consumer 1 (TSHEPO)**: `FederationPodRevocationConsumer` updates revocation cache on other instances
5. **Consumer 2 (VITO)**: `FederationPodRevocationConsumer` adds pod to local deny list

### Enforcement Point

The `FederationIdentityFilter` (order 9) checks revocation **before** JWT validation:

```java
// Check revocation cache — revoked pods are blocked before any JWT work
if (revocationChecker != null && revocationChecker.isRevoked(podId)) {
    ErrorEnvelopeWriter.write(httpRes, 403, POD_REVOKED_CODE, ...);
    return;
}
```

### Startup Hydration

`PodRevocationCacheHydrator` runs on `ApplicationReadyEvent`:
- Queries all pods with `status = REVOKED`
- Bulk-loads into `PodRevocationCacheService`
- Ensures revocations survive service restarts

### Reinstatement

1. **Admin calls** `POST /internal/v1/federation/pods/{podId}/reinstate`
2. **PodRegistrationService.reinstatePod()** executes:
   - Sets `status = ACTIVE`, clears revocation fields
   - Calls `PodRevocationCacheService.clearRevocation(podId)`
   - Publishes `impilo.federation.pod.reinstated.v1` event

## Key Files

| File | Purpose |
|------|---------|
| `services/tshepo-service/.../federation/core/PodRegistrationService.java` | Revocation/reinstatement logic |
| `services/tshepo-service/.../federation/core/PodRevocationCacheService.java` | In-process revocation cache |
| `services/tshepo-service/.../federation/core/PodRevocationCacheHydrator.java` | Startup hydration |
| `services/tshepo-service/.../federation/core/FederationPodRevocationConsumer.java` | Kafka consumer (TSHEPO) |
| `services/vito-service/.../events/FederationPodRevocationConsumer.java` | Kafka consumer (VITO) |
| `libs/federation-connector/.../filter/FederationIdentityFilter.java` | Revocation check in filter |
| `libs/federation-connector/.../identity/PodRevocationChecker.java` | Revocation checker interface |

## Consumers

| Service | Consumer Class | Group ID | Action |
|---------|---------------|----------|--------|
| TSHEPO | `FederationPodRevocationConsumer` | `tshepo-federation-revocation` | Update revocation cache |
| VITO | `FederationPodRevocationConsumer` | `vito-federation-revocation` | Add to local deny list |

Both consumers also handle `impilo.control.revocation.v1` for consent/identity revocations through separate `RevocationControlChannelConsumer` instances.

## Pilot Script

```bash
# Revoke a pod and verify propagation
./scripts/federation/revoke-and-propagate.sh http://localhost:8081 <pod-uuid>

# Revoke and automatically reinstate after verification
REINSTATE=true ./scripts/federation/revoke-and-propagate.sh http://localhost:8081 <pod-uuid>
```
