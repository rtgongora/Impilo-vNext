# Steel Thread Matrix — Cross-Service Integration Closure

## Overview
This matrix maps each steel thread to the services, endpoints, compliance requirements, and evidence artifacts that prove cross-service integration.

## Steel Thread Summary

| Thread | Name | Services | Status |
|--------|------|----------|--------|
| A | Auth Bootstrap + Provider Flow | Keycloak → TSHEPO → VARAPI → Experience BFF | Implemented |
| B | Citizen Flow | Keycloak → Experience BFF → VITO | Implemented |
| C | Support Escalation Flow | Experience BFF → Support Service | Implemented |
| D | Messaging Flow | Notification Service → Channels Service | Implemented |
| E | Eventing Flow | VITO (producer) → Kafka → outbox verification | Implemented |
| F | Federation-Protected Flow | TSHEPO federation endpoints | Implemented (federation chosen over offline — see rationale) |

## Steel Thread A — Auth Bootstrap + Provider Flow

### Flow
1. Authenticate `dr.mapfumo` (CLINICIAN) via Keycloak direct access grant → obtain JWT
2. Call VARAPI `/api/v1.1/providers` with valid trust headers + Bearer token
3. Verify trust header enforcement (X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID)
4. Verify error envelope format on missing headers (code, message, details, request_id, correlation_id)
5. Verify outbox event produced for any write operation

### Services
- Keycloak (auth) → integration-test client, direct access grant
- TSHEPO (trust enforcement) — validates headers via shared-core TrustContextFilter
- VARAPI (port 8083) — provider registry

### Compliance Proven
- [x] Trust header enforcement (4 mandatory headers)
- [x] Error envelope format (v1.1 spec)
- [x] Idempotency key enforcement on commands
- [x] request_id / correlation_id propagation
- [x] JWT token validation (when OAuth2 enabled)

### Evidence
- `test/integration/steel-thread-a-provider.sh`
- HTTP response bodies saved as evidence

---

## Steel Thread B — Citizen Flow

### Flow
1. Authenticate `citizen.moyo` (CITIZEN) via Keycloak → obtain JWT
2. Call Experience BFF `/internal/v1/auth/login` to establish session
3. Call Experience BFF patient endpoint or VITO `/api/v1.1/patients` with trust headers
4. Verify CPID-based patient lookup works
5. Verify correlation_id propagation in response meta

### Services
- Keycloak → citizen-portal client
- Experience BFF (port 8160) — session + patient data
- VITO (port 8082) — client registry

### Compliance Proven
- [x] Trust header enforcement
- [x] Error envelope format
- [x] Citizen role scope (no admin endpoints)
- [x] PII handling (VITO holds PII, not SHR)
- [x] request_id / correlation_id in response

### Evidence
- `test/integration/steel-thread-b-citizen.sh`

---

## Steel Thread C — Support Escalation Flow

### Flow
1. Authenticate `support.agent1` (SUPPORT_AGENT) via Keycloak
2. Create a support ticket via support-service POST `/api/v1.1/tickets`
3. Verify ticket created with correlation_id and request_id preserved
4. Verify outbox event produced for ticket creation
5. Escalate the ticket via PUT `/api/v1.1/tickets/{id}/escalate`
6. Verify escalation preserves the original correlation_id chain

### Services
- Keycloak → integration-test client
- Support Service (port 8340)

### Compliance Proven
- [x] Trust header enforcement
- [x] Error envelope format
- [x] Idempotency on ticket creation
- [x] correlation_id chain preservation across operations
- [x] Outbox event for write

### Evidence
- `test/integration/steel-thread-c-support.sh`

---

## Steel Thread D — Messaging Flow

### Flow
1. Authenticate as `dr.mapfumo` (CLINICIAN)
2. Send a notification via notification-service POST `/api/v1.1/notifications`
3. Verify notification created with proper tenant_id, request_id
4. Verify outbox event produced
5. Query notification status to confirm it's in the correct state

### Services
- Keycloak → integration-test client
- Notification Service (port 8111)

### Compliance Proven
- [x] Trust header enforcement
- [x] Error envelope format
- [x] Multi-channel notification routing
- [x] Outbox event for notification dispatch
- [x] request_id / correlation_id propagation

### Evidence
- `test/integration/steel-thread-d-messaging.sh`

---

## Steel Thread E — Eventing Flow

### Flow
1. Trigger a write in VITO: register a patient via POST `/api/v1.1/patients`
2. Query the event_outbox table directly (via DB or actuator endpoint)
3. Verify outbox row contains:
   - tenant_id = "moh-zw"
   - pod_id = "national"
   - request_id present
   - correlation_id present
   - idempotency_key present
4. Verify event envelope validity:
   - schema_version >= 1
   - event_type follows dot-notation (e.g., "impilo.vito.patient.created.v1")
   - meta.partition_key present (falls back to subject_id)
5. Verify Kafka consumer wiring exists (topic configuration)

### Services
- VITO (port 8082) — producer
- PostgreSQL — outbox table verification
- Kafka — topic exists check

### Compliance Proven
- [x] EventEnvelope v1.1 format (all 15 fields)
- [x] Outbox pattern (event_outbox table)
- [x] tenant_id + pod_id context preserved
- [x] schema_version >= 1
- [x] partition_key derivation
- [x] Kafka topic wiring

### Evidence
- `test/integration/steel-thread-e-eventing.sh`
- SQL query results for outbox verification

---

## Steel Thread F — Federation-Protected Flow

### Rationale for choosing Federation over Offline
Federation is more mature in this repo:
- `libs/federation-connector` is COMPLETE (13 src, 4 tests) with pod identity verification, revocation checking, spine client
- TSHEPO has explicit federation endpoints (`FederationControlController`)
- GoldenContractSuite already tests federation authority (private pod → 403)
- Offline-edge-service is COMPLETE but offline-sync is only ADEQUATE and requires more complex multi-step flow

### Flow
1. Authenticate as `admin.central` (SYSTEM_ADMIN)
2. Call TSHEPO federation endpoint with X-Pod-ID = "national" → expect 2xx (allowed)
3. Call same endpoint with X-Pod-ID = "private-harare" → expect 403 FEDERATION_AUTHORITY_VIOLATION
4. Verify error envelope contains proper code and correlation_id
5. Verify federation decision is auditable

### Services
- Keycloak → integration-test client
- TSHEPO (port 8081) — federation control

### Compliance Proven
- [x] Federation authority enforcement
- [x] Pod-level access control
- [x] FEDERATION_AUTHORITY_VIOLATION error code
- [x] Error envelope with request_id/correlation_id on 403
- [x] National vs private pod discrimination

### Evidence
- `test/integration/steel-thread-f-federation.sh`

---

## Compliance Cross-Reference

| Requirement | Thread A | Thread B | Thread C | Thread D | Thread E | Thread F |
|-------------|----------|----------|----------|----------|----------|----------|
| Trust headers | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Error envelope | ✅ | ✅ | ✅ | ✅ | — | ✅ |
| Idempotency | ✅ | — | ✅ | — | — | — |
| Federation denial | — | — | — | — | — | ✅ |
| Outbox/event | — | — | ✅ | ✅ | ✅ | — |
| Envelope context | — | — | — | — | ✅ | — |
| correlation_id chain | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| JWT auth | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
