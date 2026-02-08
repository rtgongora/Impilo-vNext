# Impilo vNext — v1.1 Gap Analysis: Per-Service Assessment

**Date**: 2026-02-08
**Scope**: All implemented Ring 0 Kernel services + Ring 1 clinical services

---

## Ring 0 Service Analysis

### 1. TSHEPO — Trust & Policy Service (Cluster of 6 Sub-Services)

#### Current Behavior
TSHEPO has been decomposed into 6 sub-services behind Envoy:
- **tshepo-authz-service** (8081): ext_authz gRPC endpoint, PolicyEngine (RBAC/ABAC), BreakGlassService, StepUpService, device identity
- **tshepo-identity-service** (8181): CPID resolution, MOSIP integration hooks
- **tshepo-consent-service** (8182): FHIR Consent CRUD, consent evaluation, share-links
- **tshepo-audit-service** (8183): SHA-256 hash-chain audit ledger, Kafka consumer, query/export/verify APIs
- **tshepo-keys-service** (8184): Ed25519 signing, JWKS, key rotation, certificate trust, token signing
- **tshepo-offline-service** (8185): Capability tokens, offline packs, reconciliation

Plus `libs/tshepo-contracts` (shared DTOs) and `libs/tshepo-sdk` (TrustContext filter, AuthzClient).

#### v1.1 Requirements
- IAM (OIDC), consent evaluation, token issuance
- Device identity & attestation
- Offline entitlement issuance (signed JWT/CBOR with scope/time/device binding)
- Step-up auth integration and risk scoring hooks
- Audit decision logging (policy decisions with policy_version)
- OPA integration for central policy enforcement
- KMS/HSM-backed keys for CPID pseudonymization

#### Mismatch List

| # | Issue | Severity |
|---|---|---|
| T1 | **No OPA integration** — PolicyEngine is custom Java. v1.1 Law 2 names OPA explicitly. | HIGH |
| T2 | **No `policy_version` in decision evidence** — Audit events lack policy version tracking for inquiry survival. | HIGH |
| T3 | **No Clinical Safety Class enforcement** — Authz evaluates RBAC/ABAC but doesn't classify actions as A/B/C. | CRITICAL |
| T4 | **KEK not HSM/Vault-backed** — `tshepo.keys.kek` loaded from application config, not Vault. | HIGH |
| T5 | **No CPID keyed pseudonymization** — Law 1 requires HSM-backed derivation with rotation support. CPID mapping exists in tshepo-identity but uses lookup, not cryptographic derivation. | HIGH |
| T6 | **No `pod_id` in TrustContext** — Federation headers absent. | CRITICAL |
| T7 | **Offline entitlements need verification** — Must confirm signed tokens include scope, time window, device binding, patient context constraints per v1.1 Law 7. | MEDIUM |
| T8 | **No revocation propagation channel** — Consent revocation in tshepo-consent has no High-Priority Control Channel for cross-pod propagation. | CRITICAL |
| T9 | **No SLOs defined** — No availability/latency targets for the trust plane. | HIGH |
| T10 | **Audit chain uses software SHA-256 only** — No HSM-backed signing of audit entries for non-repudiation. | MEDIUM |

#### Remediation Plan
1. **T3 first** (stop-the-line): Add `ConsistencyClass` enum to `tshepo-contracts`, create action classification table in authz DB
2. **T6 second**: Add `pod_id` to `TrustContext` (shared-core + tshepo-sdk), Envoy headers, event envelope
3. **T2**: Add `policy_version` column to audit event entity and migration
4. **T4 + T5**: Add Spring Cloud Vault dependency, implement Vault KMS provider, wire CPID derivation
5. **T1**: Evaluate OPA sidecar vs Java PolicyEngine equivalence — recommend keeping Java engine but adding OPA-compatible policy format export
6. **T8**: Create `trust.revocation` Kafka topic with guaranteed delivery, implement revocation consumer in consent service
7. **T9**: Define SLOs in service configuration
8. **T10**: Optional: add HMAC signing via keys-service for audit entries

#### Backward-Compatibility Plan
- `pod_id` header: default to `"national-spine"` when absent (backwards-compatible)
- `policy_version`: nullable column, backfill as `"v0-unversioned"` for existing entries
- Event envelope migration: add new columns as nullable, existing events remain valid
- TrustContext: add `podId` as Optional field, filter extracts from header with fallback

---

### 2. VITO — Client Registry (MPI)

#### Current Behavior
- CRID management with dedup/merge workflows
- CPID mapping table
- National identifier linkage
- Outbox events: `vito.client.registered`, `vito.client.updated`, `vito.merge.completed`
- Flyway migrations for client registry tables
- Integration with Ubomi for birth/death events

#### v1.1 Requirements
- CRID management, dedup/merge
- CPID mapping rules and re-keying under governance
- Linkage to Ubomi and national identifiers under policy
- Merge events with mapping (not destructive overwrite)
- Cross-pod merge reconciliation within bounded time

#### Mismatch List

| # | Issue | Severity |
|---|---|---|
| V1 | **No federation merge events** — Merges happen locally but no structured merge event with ID mapping for pod consumption. | CRITICAL |
| V2 | **No CPID re-keying governance** — No automated re-keying mechanism under HSM control. | HIGH |
| V3 | **No cross-pod identity linkage governance** — No consent-gated linkage controls. | CRITICAL |
| V4 | **Event envelope missing v1.1 mandatory fields** — Same minimal outbox pattern as all services. | CRITICAL |
| V5 | **No snapshot endpoint** — No `/api/v1/clients/snapshot` for consumer bootstrap/recovery. | HIGH |
| V6 | **No delta events** — Full client payload in every event. v1.1 prohibits this for large domains. | HIGH |

#### Remediation Plan
1. **V4 + V6**: Migrate event outbox to v1.1 envelope; implement delta change tracking
2. **V1**: Create structured `MergeEvent` with old-CRID→new-CRID mapping, emit on dedicated topic
3. **V5**: Add snapshot endpoint with cursor-based pagination
4. **V3**: Add consent check for cross-pod identity resolution
5. **V2**: Integrate with tshepo-keys-service for CPID re-keying

#### Backward-Compatibility
- Merge events: new topic `vito.merge.propagation` (additive, no breaking change)
- Delta events: dual-emit (full + delta) during transition period
- Snapshot endpoint: new REST path, no impact on existing APIs

---

### 3. VARAPI — Provider Registry

#### Current Behavior
- Practitioner identity, licensure, privilege management, role assignments
- Outbox events for provider lifecycle
- REST API for provider CRUD and privilege queries

#### v1.1 Requirements
- Practitioner identity, licensure, privileges, role assignments
- Privilege revocation propagation rules (federation-aware)

#### Mismatch List

| # | Issue | Severity |
|---|---|---|
| P1 | **No privilege revocation propagation** — Revocations are local; no federation-aware propagation to pods. | CRITICAL |
| P2 | **Event envelope missing v1.1 fields** | CRITICAL |
| P3 | **No snapshot endpoint** | HIGH |
| P4 | **No delta events** | HIGH |

#### Remediation Plan
1. **P1**: Emit revocation events on High-Priority Control Channel (`trust.revocation.privilege`)
2. **P2 + P4**: Migrate outbox to v1.1 envelope with delta tracking
3. **P3**: Add snapshot endpoint

---

### 4. TUSO — Facility Registry

#### Current Behavior
- Facility topology, departments, capabilities, service availability
- Control Tower with occupancy snapshots, alert rules, bed management
- Telemetry endpoints (PCT-sourced)
- Bookings and resource management
- Rich implementation with tests (AlertRuleEngineTest)

#### v1.1 Requirements
- Facility topology, departments, capabilities, service availability
- Facility identity federation for pods

#### Mismatch List

| # | Issue | Severity |
|---|---|---|
| F1 | **No facility identity federation** — No pod-aware facility ID mapping or cross-pod facility resolution. | HIGH |
| F2 | **Telemetry on clinical bus** — Control Tower telemetry events share Kafka with clinical events (violates Law 8). | HIGH |
| F3 | **Event envelope missing v1.1 fields** | CRITICAL |
| F4 | **No snapshot endpoint for facility master data** | HIGH |

#### Remediation Plan
1. **F3**: Migrate outbox to v1.1 envelope
2. **F2**: Route telemetry events to `telemetry.*` bus namespace
3. **F4**: Add snapshot endpoint for facility/department/capability data
4. **F1**: Add pod-aware facility ID mapping table and federation sync

---

### 5. MSIKA — Product & Service Registry

#### Current Behavior
- Products, services, tariffs, catalogs (versioned with effective dates)
- Pack management, import/export, mappings
- Outbox events for catalog lifecycle
- Note: `product-registry-service` also exists as a separate service — potential duplication

#### v1.1 Requirements
- Orderables, billables, tariffs
- Versioned catalogs and effective date management
- Delta + snapshot emission

#### Mismatch List

| # | Issue | Severity |
|---|---|---|
| M1 | **Two services for one domain** — `msika-service` + `product-registry-service` both exist. v1.1 says MSIKA is the canonical product registry. | HIGH |
| M2 | **Event envelope missing v1.1 fields** | CRITICAL |
| M3 | **No delta events** — Full catalog payloads in events (explicitly prohibited by Law 4 for large domains like catalogs). | CRITICAL |
| M4 | **No snapshot endpoint** | HIGH |

#### Remediation Plan
1. **M1**: Consolidate `product-registry-service` into `msika-service` or deprecate one
2. **M3**: Implement delta event tracking for catalog changes (changed fields + effective dates)
3. **M2**: Migrate outbox to v1.1 envelope
4. **M4**: Add snapshot endpoint with cursor pagination for catalog bootstrap

---

### 6. ZIBO — Terminology & Semantic Governance

#### Current Behavior
- 6 FHIR types: CodeSystem, ValueSet, ConceptMap, NamingSystem, StructureDefinition, ImplementationGuide
- Publication lifecycle: DRAFT → PUBLISHED → DEPRECATED → RETIRED (immutable after publish — good)
- Pack management, validation engine (sync + async), mapping service
- Redis caching with graceful degradation
- Import/export (FHIR Bundle + CSV)

#### v1.1 Requirements
- ICD-11, SNOMED, LOINC mapping governance
- Value sets, concept maps, version lifecycles
- National governance workflows (approvals, publishing)

#### Mismatch List

| # | Issue | Severity |
|---|---|---|
| Z1 | **Event envelope missing v1.1 fields** | CRITICAL |
| Z2 | **No delta events for terminology changes** — Entire artifact payload in events (prohibited for large domains). | CRITICAL |
| Z3 | **No snapshot endpoint** | HIGH |
| Z4 | **No schema registry integration** — Terminology validation is internal; no schema-level governance for event compatibility. | HIGH |

#### Remediation Plan
1. **Z1 + Z2**: Migrate outbox; implement delta for terminology artifact changes
2. **Z3**: Add snapshot endpoint for terminology artifacts
3. **Z4**: Register event schemas in Schema Registry

---

### 7. BUTANO — Shared Health Record (FHIR)

#### Current Behavior
- HAPI FHIR R4 JPA server (butano-service wrapping hapi-fhir Docker image)
- 5 interceptors: HeaderValidation, TenantEnforcement, PiiPrevention, ProvenanceStamping, TerminologyValidation
- Custom endpoints: IPS summary, visit summary, timeline, O-CPID reconciliation, resource stats
- PII-free (CPID-only) — compliant with Law 1
- Outbox events: `butano.resource.created`, `butano.resource.updated`, `butano.reconcile.completed`
- Dual schema approach (HAPI manages FHIR tables, Flyway manages custom tables)

#### v1.1 Requirements
- Canonical longitudinal record primitives
- FHIR profile governance + validation (via ZIBO)
- Subscription hooks where permitted
- FHIR is canonical clinical write model

#### Mismatch List

| # | Issue | Severity |
|---|---|---|
| B1 | **Event envelope missing v1.1 fields** | CRITICAL |
| B2 | **No snapshot endpoint for FHIR resources** — No bulk export/bootstrap mechanism. | HIGH |
| B3 | **No FHIR subscription hooks** — v1.1 mentions subscription hooks "where permitted". | MEDIUM |
| B4 | **Events are full-resource** — Resource created/updated events carry entire FHIR resource JSON (may be large). Should be delta or reference-only. | HIGH |

#### Remediation Plan
1. **B1**: Migrate outbox to v1.1 envelope
2. **B2**: Add FHIR $export or custom snapshot endpoint
3. **B4**: Events should carry resource reference (type + ID + version) not full resource; consumers pull via FHIR API
4. **B3**: Evaluate FHIR R4 subscriptions for permitted use cases

---

### 8. UBOMI — CRVS Interface

#### Current Behavior
- Births/deaths linkage service
- Integration with VITO for identity events
- Reconciliation patterns for CRVS data

#### v1.1 Requirements
- Births/deaths linkage protocols
- Identity event handling with reconciliation patterns

#### Mismatch List

| # | Issue | Severity |
|---|---|---|
| U1 | **Event envelope missing v1.1 fields** | CRITICAL |
| U2 | **No federation-aware death event propagation** — Death events should propagate via High-Priority Control Channel. | HIGH |

---

### 9. MUSHEX — Finance Engine / Claims Switch

#### Current Behavior
- Payment intent engine (8-state lifecycle), claims switching (8-state lifecycle)
- Double-entry-lite ledger, remittance (HMAC OTP/QR), settlement
- Fraud detection, reconciliation
- 4 payment rail adapters (MobileMoney, BankTransfer, CardGateway, SandboxMock)
- 20 Postgres tables, 11 core services, 9 REST controllers
- 8 test classes
- Step-up auth integration

#### v1.1 Requirements
- Claims processing, billing, settlement switching
- Consumes MSIKA pricing and national coverage rules
- Integrates fraud signals and audit requirements

#### Mismatch List

| # | Issue | Severity |
|---|---|---|
| MX1 | **Event envelope missing v1.1 fields** | CRITICAL |
| MX2 | **No Class A enforcement for billing/claims finalization** — v1.1 classifies billing finalization as Class A (Hard-Truth Required). | CRITICAL |
| MX3 | **No snapshot endpoint** | HIGH |
| MX4 | **No delta events** | HIGH |
| MX5 | **Settlement release should require break-glass-auditable step-up** — Currently has step-up hooks but not integrated with Class A enforcement. | HIGH |

---

## Ring 1 Service Analysis (Summary)

### PCT — Patient Care Tracker
- 13-state journey state machine, 13 tables, queue engine, multi-blocker discharge
- **Gaps**: No consistency class annotations on actions, no offline entitlement support, event envelope non-compliant
- **Class A actions**: Controlled substance orders (delegated to OROS), death recording
- **Class B actions**: Triage, vitals, routine encounter documentation
- **Class C actions**: Emergency triage, basic notes

### OROS — Orders & Results Orchestration
- 13-state order lifecycle, 10 workstep types, SLA timers, reconciliation
- **Gaps**: No Class A enforcement for controlled substance orders, event envelope non-compliant, no snapshot endpoints
- **Class A actions**: Controlled substance prescribing, high-risk procedure orders
- **Class B actions**: Routine orders, results entry
- **Class C actions**: Emergency orders

### COSTA — Costing Engine
- 5 cost engines, charging rule engine, exemption engine, bill lifecycle
- **Gaps**: No Class A enforcement for billing finalization, event envelope non-compliant
- **Class A actions**: Bill finalization, claims submission, invoice issuance
- **Class B actions**: Cost estimation, draft billing

### Pharmacy, Inventory, MSIKA Flow
- All have functional domain logic but share the same cross-cutting gaps:
  - Non-compliant event envelopes
  - No consistency class enforcement
  - No snapshot endpoints
  - No delta events
  - No federation awareness

---

## Cross-Cutting Gap Summary

| Gap | Services Affected | Priority |
|---|---|---|
| Event envelope non-compliant | ALL 24+ services | P0 — Stop the line |
| No Schema Registry | ALL | P0 — Stop the line |
| No Clinical Safety Classes | All clinical services | P0 — Stop the line |
| No Federation Protocol | ALL | P1 — Before new features |
| No Snapshot Endpoints | ALL Ring 0 | P1 |
| No Delta Events | ALL | P1 |
| No Bus Separation | ALL | P2 |
| No Observability | ALL | P2 |
| No DR/SLOs | ALL | P2 |
| No Developer Portal | Platform | P3 |
