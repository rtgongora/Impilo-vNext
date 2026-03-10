# Impilo vNext — Strict Execution Plan: Prompts 20–22

**Date**: 2026-03-10
**Author**: Claude (Junior Developer)
**Prerequisites**: This prompt (Prompt 19) must be complete — skeleton modules committed.

---

## Prompt 20: Channels Service — Full Implementation

### Scope
Implement the full Omnichannel Access Gateway in `services/channels-service`.

### Pre-flight Checklist
- [ ] Skeleton exists (pom.xml, Application class, V001__init.sql, application.yml, GoldenContractIT)
- [ ] Tech-companion auto-configuration verified (impilo.companion.enabled=true)
- [ ] Parent pom.xml includes `channels-service` module

### Implementation Steps

| # | Task | Files | Non-Negotiable |
|---|---|---|---|
| 20.1 | **Session lifecycle service** — full CRUD + state machine (ACTIVE → PAUSED → ESCALATED → CLOSED) | `service/SessionService.java` | Outbox event on every state transition |
| 20.2 | **Message routing engine** — inbound/outbound message handling with content types (TEXT, MENU, MEDIA, FORM) | `service/MessageService.java`, `domain/ChannelMessageEntity.java` (already in V001) | Each message emits outbox event |
| 20.3 | **Inbound channel adapters** — webhook controllers for USSD, WhatsApp, SMS, IVR | `api/inbound/UssdWebhookController.java`, `api/inbound/WhatsAppWebhookController.java`, `api/inbound/SmsWebhookController.java` | All use `/external/v1/inbound/{channel}` prefix; idempotency enforced |
| 20.4 | **Escalation engine** — escalate session to live agent with queue management | `service/EscalationService.java`, `domain/EscalationEntity.java`, V002 migration | Class B staleness for agent lookup |
| 20.5 | **Assisted interaction** — agent-assisted flows where agent acts on behalf of citizen | `api/AssistedController.java` | Must propagate original client_id + agent_id in trust context |
| 20.6 | **Event types** — implement all 5 event types with canonical naming | Outbox writes in services | `impilo.channels.session.{created,closed}.v1`, `impilo.channels.message.{received,sent}.v1`, `impilo.channels.escalation.created.v1` |
| 20.7 | **Snapshot endpoint** | `api/SnapshotController.java` | `GET /internal/v1/sessions/snapshot` per §6 of EVENTING_AND_TOPICS.md |
| 20.8 | **Tests** | Unit + GoldenContractIT must pass | MockMvc for all endpoints; H2 test profile |

### Exit Criteria
- [ ] All endpoints return correct HTTP status codes
- [ ] Missing headers → 400 MISSING_REQUIRED_HEADER
- [ ] Missing Idempotency-Key on POST → 400 IDEMPOTENCY_KEY_REQUIRED
- [ ] Same key + different body → 409 IDENTITY_CONFLICT
- [ ] GoldenContractIT passes
- [ ] All events use `impilo.channels.*` prefix
- [ ] No TODOs, no stubs

---

## Prompt 21: Coverage Service — Full Implementation

### Scope
Implement the full Coverage & Eligibility Engine in `services/coverage-service`.

### Pre-flight Checklist
- [ ] Skeleton exists (pom.xml, Application class, V001__init.sql, application.yml, GoldenContractIT)
- [ ] Tech-companion auto-configuration verified
- [ ] Parent pom.xml includes `coverage-service` module

### Implementation Steps

| # | Task | Files | Non-Negotiable |
|---|---|---|---|
| 21.1 | **Coverage plan management** — CRUD for insurance/coverage plan definitions | Already scaffolded in `CoveragePlanController.java` — add full service layer | Outbox event on plan creation/update |
| 21.2 | **Member enrollment** — link clients to coverage plans with relationship tracking | `api/MemberController.java`, `service/MemberService.java` | Must validate client_id exists (bounded stale OK — Class B, 5 min) |
| 21.3 | **Eligibility check** — real-time coverage eligibility verification | `api/EligibilityController.java`, `service/EligibilityService.java` | `GET /internal/v1/eligibility?client_id=X&service_code=Y` — Class A for controlled substances, Class B for routine |
| 21.4 | **Pre-authorization workflow** — request → review → approve/deny lifecycle | `service/PreauthService.java` | State machine: PENDING → APPROVED/DENIED/EXPIRED; outbox event on each transition; Class A for high-cost procedures |
| 21.5 | **Claims lifecycle** — DRAFT → SUBMITTED → ADJUDICATED → PAID/REJECTED | `api/ClaimsController.java`, `service/ClaimsService.java` | Integration point with MUSHEX for payment coordination; Class A for adjudication |
| 21.6 | **Payment coordination** — coordinate with MUSHEX for payment processing | `service/PaymentCoordinationService.java` | Emit `impilo.coverage.claim.submitted.v1` for MUSHEX to consume |
| 21.7 | **Event types** — implement all coverage event types | Outbox writes | `impilo.coverage.eligibility.checked.v1`, `impilo.coverage.preauth.{requested,approved,denied}.v1`, `impilo.coverage.claim.{submitted,adjudicated}.v1` |
| 21.8 | **Snapshot endpoint** | `api/SnapshotController.java` | `GET /internal/v1/coverage/snapshot` |
| 21.9 | **Tests** | Unit + GoldenContractIT | All endpoints tested; H2 test profile |

### Integration Points
- **MUSHEX**: coverage-service emits claim events → MUSHEX handles payment
- **VITO**: client_id resolution for member enrollment
- **MSIKA**: benefit catalog integration for eligible items
- **COSTA**: coverage check during bill finalization

### Exit Criteria
- [ ] Full eligibility check flow works end-to-end (via MockMvc)
- [ ] Pre-auth lifecycle complete with proper state transitions
- [ ] Claims lifecycle complete with adjudication
- [ ] All v1.1 headers enforced
- [ ] GoldenContractIT passes
- [ ] All events use `impilo.coverage.*` prefix
- [ ] No TODOs, no stubs

---

## Prompt 22: INDAWO Service — Full Implementation

### Scope
Implement the full Location & Address Registry in `services/indawo-service`.

### Pre-flight Checklist
- [ ] Skeleton exists (pom.xml, Application class, V001__init.sql, application.yml, GoldenContractIT)
- [ ] Tech-companion auto-configuration verified
- [ ] Parent pom.xml includes `indawo-service` module

### Implementation Steps

| # | Task | Files | Non-Negotiable |
|---|---|---|---|
| 22.1 | **Address management** — full CRUD with standardization | Already scaffolded in `AddressController.java` — add service layer with validation | Address normalization (capitalize province, validate country code) |
| 22.2 | **Catchment area management** — hierarchical geographic area definitions | `api/CatchmentController.java`, `domain/CatchmentAreaEntity.java` (already in V001), `service/CatchmentService.java` | Tree structure: PROVINCE → DISTRICT → CONSTITUENCY → WARD |
| 22.3 | **Facility-location linking** — associate TUSO facilities with addresses and catchment areas | `api/FacilityLocationController.java`, `service/FacilityLocationService.java` | Must validate facility_id against TUSO (bounded stale — Class B, 15 min) |
| 22.4 | **Geocoding service** — coordinate resolution and quality scoring | `service/GeocodingService.java` | Record geocode_quality (HIGH/MEDIUM/LOW/MANUAL); emit event on resolution |
| 22.5 | **Catchment-based lookup** — find facilities serving a given address | `api/LookupController.java`, `service/LookupService.java` | `GET /internal/v1/addresses/{id}/facilities` — returns facilities in the address's catchment |
| 22.6 | **National-only admin endpoints** — catchment area creation restricted to national pod | Use `FederationAuthority.requireNational()` | `POST /internal/v1/catchments` → @NationalOnly |
| 22.7 | **Event types** — implement all INDAWO event types | Outbox writes | `impilo.indawo.address.{created,updated}.v1`, `impilo.indawo.catchment.mapped.v1`, `impilo.indawo.geocode.resolved.v1` |
| 22.8 | **Snapshot endpoint** | `api/SnapshotController.java` | `GET /internal/v1/addresses/snapshot` |
| 22.9 | **Tests** | Unit + GoldenContractIT | All endpoints tested; federation test for national-only |

### Integration Points
- **TUSO**: facility_id validation for facility-location linking
- **VITO**: client address resolution (VITO stores address_id reference)
- **PCT**: catchment-based facility routing for referrals

### Exit Criteria
- [ ] Full address CRUD with normalization
- [ ] Hierarchical catchment areas with parent-child relationships
- [ ] Facility-location linking functional
- [ ] Catchment-based facility lookup works
- [ ] National-only endpoints return 403 for non-national pods
- [ ] GoldenContractIT passes (including federation test)
- [ ] All events use `impilo.indawo.*` prefix
- [ ] No TODOs, no stubs

---

## Cross-Cutting Reminders for All Three Prompts

### Non-Negotiables (Repeat from Manifest)

1. **API Surfaces**: Both `/internal/v1/` and `/external/v1/` prefixes where applicable
2. **Required Headers**: X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID — enforced by tech-companion auto-config
3. **Idempotency**: POST/PUT/PATCH require Idempotency-Key — enforced by tech-companion
4. **Error Envelope**: All errors return `{"error": {"code", "message", "details", "request_id", "correlation_id"}}`
5. **Eventing**: All events use `impilo.<service>.<domain>.<entity>.<action>.v1` format
6. **Outbox Pattern**: All events written to `*_event_outbox` in the same transaction as domain changes
7. **No Placeholders**: No TODOs, no stubs. Mark `BLOCKED` with exact blocker if unable to complete.

### Dependency Order

```
Prompt 20 (Channels)  ─── independent, can run first
Prompt 21 (Coverage)  ─── depends on MUSHEX event contracts (already defined)
Prompt 22 (INDAWO)    ─── depends on TUSO facility_id contract (already defined)

Recommended order: 20 → 21 → 22 (or 20 ∥ 22, then 21)
```

### Files NOT to Touch
- No changes to any of the 16 legacy services' domain logic
- No renaming of existing modules
- No Docker/Testcontainers introduction
- No changes to `libs/tech-companion` or `libs/tech-companion-harness`

### Test Execution
After each prompt, run:
```bash
cd services
mvn -pl channels-service -am clean test     # Prompt 20
mvn -pl coverage-service -am clean test     # Prompt 21
mvn -pl indawo-service -am clean test       # Prompt 22
```
