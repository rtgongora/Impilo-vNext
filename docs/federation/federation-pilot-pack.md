# Federation Pilot Pack — National Spine ↔ Pod

> Wave 21 | Date: 2026-03-15

## Test Environment

| Component | Instance | Port |
|-----------|----------|------|
| National Spine (TSHEPO) | localhost | 8081 |
| Client Registry (VITO) | localhost | 8082 |
| Patient Care Tracker (PCT) | localhost | 8088 |
| Keycloak | localhost | 8080 |
| Kafka (KRaft) | localhost | 9092 |
| Envoy | localhost | 10000 |

## Pilot Pod Configuration

| Field | Value |
|-------|-------|
| Pod ID | `<generated at registration>` |
| Pod Name | Facility-Pod-Pilot-1 |
| Region | Harare |
| Capabilities | PATIENT_REGISTRY, CLINICAL_CAPTURE, BILLING |
| Granted Data Classes | PATIENT, ENCOUNTER, OBSERVATION |
| Max Offline Hours | 48 |
| Merge Authority | false |
| Heartbeat Interval | 300s |
| Registration Validity | 30 days |

## Pre-Flight Checklist

- [ ] Kafka topics created:
  - `impilo.federation.pod.revoked.v1`
  - `impilo.federation.pod.reinstated.v1`
  - `impilo.federation.pod.registered.v1`
  - `impilo.control.revocation.v1`
- [ ] TSHEPO database migration applied (pod_registration table)
- [ ] Keycloak realm `impilo` configured with federation client
- [ ] Envoy ext_authz route configured for federation endpoints
- [ ] mTLS certificates generated for pilot pod

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/federation/register-pod.sh` | Register pilot pod with spine |
| `scripts/federation/issue-pilot-token.sh` | Obtain federation JWT |
| `scripts/federation/verify-federation.sh` | Run full verification suite |
| `scripts/federation/revoke-and-propagate.sh` | Test revocation channel |

## Test Results

### 1. Registration Handshake

| Test | Result | Notes |
|------|--------|-------|
| Pod sends registration request with valid certificate | [ ] | `register-pod.sh` |
| Spine validates certificate chain | [ ] | mTLS termination at Envoy |
| Spine issues scoped authority grant | [ ] | Verify response `authority_scope` |
| mTLS session established using exchanged certificates | [ ] | Envoy X-Client-Certificate header |
| Heartbeat interval respected | [ ] | 300s interval, `heartbeat` endpoint |
| Registration renewal works before expiry | [ ] | `renew` endpoint |

**Code coverage:**
- `PodRegistrationServiceTest` — 12 tests covering registration, scope, heartbeat, renewal
- `FederationControlController` — REST API wiring

### 2. JWT Validation

| Test | Result | Notes |
|------|--------|-------|
| Valid federation JWT accepted | [ ] | `aud=impilo-federation` |
| Invalid audience rejected (401/403) | [ ] | `FederationIdentityFilterTest` |
| Expired token rejected | [ ] | JWT `exp` claim validation |
| Revoked pod rejected even with valid JWT (403) | [ ] | `FederationRevocationFilterTest` |
| Pod ID mismatch between header and token rejected | [ ] | `FederationIdentityFilterTest` |

**Code coverage:**
- `FederationIdentityFilterTest` — 8 tests (national bypass, verified pod, unknown pod, wrong aud, mismatch, no bearer)
- `FederationRevocationFilterTest` — 4 tests (revoked blocked, non-revoked passes, null checker, national bypass)
- `JwtAudienceVerifierTest` — audience validation tests

### 3. Authority Enforcement

| Test | Result | Notes |
|------|--------|-------|
| Non-national pod merge attempt blocked (403) | [ ] | `FEDERATION_AUTHORITY_VIOLATION` |
| Cross-facility access attempt blocked | [ ] | Scope enforcement in authority_scope |
| Data class violation blocked | [ ] | `scopeDataClasses()` filtering |
| Unauthorized merge attempt blocked | [ ] | `FederationAuthorityGuard` |
| Violation events in outbox | [ ] | `EventOutboxEntity` published |
| Exception contains pod ID for audit | [ ] | `FederationNotAuthorizedException.getPodId()` |

**Code coverage:**
- `FederationAuthorityViolationWave21Test` — 8 tests (facility, district, offline, UUID, empty, null pod denial)
- `FederationGuardLegacySafetyTest` — 5 tests (legacy safety, private method verification)
- `FederationAuthorityTest` — 5 tests (tech-companion lib)
- `FederationAuthorityViolationTest` — 4 tests (harness-based contract tests)

### 4. Revocation Channel

| Test | Result | Notes |
|------|--------|-------|
| Revocation command propagates to cache within 5s | [ ] | Direct cache write + Kafka |
| Revoked pod's next request rejected (403) | [ ] | `POD_REVOKED` error code |
| Revocation survives service restarts | [ ] | `PodRevocationCacheHydrator` |
| Revocation event published to Kafka | [ ] | Outbox pattern |
| Reinstatement flow works | [ ] | `reinstatePod()` clears cache |

**Code coverage:**
- `PodRevocationCacheServiceTest` — 8 tests (mark, clear, bulk load, idempotency)
- `PodRegistrationServiceTest.PodRevocation` — 3 tests (revoke, already-revoked, reinstate)
- `FederationPodRevocationConsumerTest` (VITO) — 6 tests (deny list, idempotency, malformed)

### 5. Merge/Revocation Propagation

| Test | Result | Notes |
|------|--------|-------|
| Patient merge at Spine propagates to VITO/PCT | [ ] | `RevocationControlChannelConsumer` (IDENTITY_MERGED) |
| Merge convergence verified (CPID convergence) | [ ] | Linkage chain update in VITO consumer |
| Pod revocation propagates to TSHEPO + VITO | [ ] | 2 consumers on `pod.revoked.v1` |
| Revoked pod cannot communicate with any pod | [ ] | Filter check + deny list |
| Propagation latency within SLA | [ ] | ≤ 60s merges, ≤ 30s revocations |

**Code coverage:**
- `RevocationControlChannelConsumerTest` (VITO) — 7 tests
- PCT `RevocationControlChannelConsumer` — deployed and consuming
- `FederationPodRevocationConsumerTest` (VITO) — 6 tests

## Propagation Path Summary

### Merge Propagation (Spine → Pods)
```
Spine VITO: Merge Patient-A → Patient-B
  → MergeService publishes to event_outbox (vito.merge.executed)
  → Kafka: impilo.control.revocation.v1 (IDENTITY_MERGED)
    → VITO RevocationControlChannelConsumer: update linkage chains
    → PCT RevocationControlChannelConsumer: update journey references
```

### Pod Revocation Propagation (Spine → All)
```
Spine Admin: Revoke Pod-X
  → PodRegistrationService.revokePod()
    → DB: status = REVOKED
    → L1 Cache: markRevoked(podId) [immediate]
    → Outbox: impilo.federation.pod.revoked.v1
      → Kafka → TSHEPO consumer: cache update (cross-instance)
      → Kafka → VITO consumer: deny list update
  → FederationIdentityFilter: blocks Pod-X on every request
```

## Test Counts

| Module | Tests | Status |
|--------|-------|--------|
| tshepo-service (PodRegistrationServiceTest) | 12 | Implemented |
| tshepo-service (PodRevocationCacheServiceTest) | 8 | Implemented |
| federation-connector (FederationRevocationFilterTest) | 4 | Implemented |
| federation-connector (FederationIdentityFilterTest) | 8 | Existing |
| federation-connector (JwtAudienceVerifierTest) | Existing | Existing |
| vito-service (FederationAuthorityViolationWave21Test) | 8 | Implemented |
| vito-service (FederationGuardLegacySafetyTest) | 5 | Updated |
| vito-service (FederationPodRevocationConsumerTest) | 6 | Implemented |
| vito-service (RevocationControlChannelConsumerTest) | 7 | Existing |
| tech-companion (FederationAuthorityTest) | 5 | Existing |
| **Total** | **63+** | |

## Sign-Off

- [ ] Federation Architect: _________________ Date: _______
- [ ] Security Lead: _________________ Date: _______
- [ ] Platform Lead: _________________ Date: _______
