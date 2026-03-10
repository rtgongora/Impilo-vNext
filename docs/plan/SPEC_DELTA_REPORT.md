# Impilo vNext — Spec Delta Report: Current Repo vs. vNext V3 + Tech Companion Spec 2.0

**Date**: 2026-03-10
**Author**: Claude (Junior Developer)
**Branch**: `claude/review-project-manifest-jb5O0`

---

## 1. Executive Summary

This report compares the **current repository state** against the requirements outlined in the **vNext V3 Manifest** and **Tech Companion Spec 2.0** documents. It identifies gaps in service coverage, API surfaces, eventing, and infrastructure.

> **NOTE**: The docx source files (`/mnt/data/vNext V3.docx` and `/mnt/data/vNext Tech Companion Spec 2.0.docx`) were **not found on the filesystem**. This delta report is based on extrapolation from existing architecture docs (`docs/architecture/v1.1/`, `docs/plan/`), the service catalog, and the non-negotiable requirements stated in the prompt.

---

## 2. Services: What Exists vs. What's Missing

### 2.1 Existing Services (Implemented or Skeleton)

| Service | Status | v1.1 Compliance | Notes |
|---|---|---|---|
| TSHEPO cluster (6 sub-services) | LIVE | Partial | Needs Class A/B/C enforcement, decision evidence |
| VITO (client registry) | LIVE | Partial | Needs snapshot endpoint, delta events |
| VARAPI (provider registry) | SKELETON | Partial | Needs full v1.1 endpoints |
| TUSO (facility registry) | SKELETON | Partial | Needs full v1.1 endpoints |
| ZIBO (terminology) | LIVE | Partial | Needs snapshot endpoint |
| MSIKA (product registry) | SKELETON | Partial | Needs benefit catalog extensions |
| MUSHEX (finance) | LIVE | Partial | Needs v1.5 endpoints (see §2.3) |
| BUTANO (SHR/FHIR) | LIVE | Partial | CPID-only, PII-free ✓ |
| PCT, OROS, COSTA, Pharmacy | LIVE | Partial | Needs Class A/B/C annotations |
| Integration Hub, Notification, Rules | LIVE | Full | v1.1-native ✓ |
| Surveillance, Campaigns | LIVE | Full | v1.1-native ✓ |
| Inpatient | SKELETON | Partial | Port 8120 |

### 2.2 Missing Services (Required by Spec)

| Service | Purpose | Required By | Port | Status |
|---|---|---|---|---|
| **channels-service** | Omnichannel Access: session management, message routing, inbound channels (USSD/WhatsApp/SMS/IVR), escalation to live agents, assisted interactions | vNext V3 | 8130 | **NEW — to be created** |
| **coverage-service** | Coverage & Eligibility: insurance coverage verification, pre-authorization, claims lifecycle, payment coordination | vNext V3 | 8140 | **NEW — to be created** |
| **indawo-service** | Location/Address Registry: standardized address management, geocoding, catchment area mapping, facility-to-location linking | vNext V3 | 8150 | **NEW — to be created** |

### 2.3 MUSHEX v1.5 Gaps

The existing `mushex-service` (port 8087) handles payments, claims switching, and ledger. The v1.5 spec requires additional endpoints:

| Endpoint Group | Current Status | v1.5 Requirement |
|---|---|---|
| Authorization endpoints | Partial (payment.authorize exists) | Full pre-auth workflow with step-up |
| Settlement endpoints | Missing | `/internal/v1/settlements` — batch settlement, release, reconciliation |
| Reconciliation endpoints | Missing | `/internal/v1/reconciliation` — cross-pod ledger reconciliation |
| Enhanced fraud detection | Basic fraud.flag | Real-time fraud scoring, pattern detection |

### 2.4 MSIKA Benefit Catalog Extensions

| Gap | Description |
|---|---|
| Benefit plan definitions | Formulary-linked benefit plans with coverage rules |
| Eligibility rules engine | Integration with coverage-service for real-time eligibility |
| Pack-to-benefit mapping | Linking product packs to specific benefit categories |

---

## 3. API Surface Gaps

### 3.1 Dual API Prefix Requirement

The spec mandates both `/internal/v1/` and `/external/v1/` prefixes for new services.

| Service | `/internal/v1/` | `/external/v1/` | Gap |
|---|---|---|---|
| Integration Hub | ✅ | ❌ | Missing external |
| Notification | ✅ | ❌ | Missing external |
| Rules | ✅ | ❌ | Missing external |
| channels-service | — | — | **Not created yet** |
| coverage-service | — | — | **Not created yet** |
| indawo-service | — | — | **Not created yet** |

### 3.2 Required Headers (v1.1+)

Already enforced by `tech-companion` library for v1.1-native services:
- ✅ X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID
- ✅ Idempotency-Key on POST/PUT/PATCH
- ✅ Error envelope format
- ✅ Federation authority (@NationalOnly)

### 3.3 Snapshot Endpoints

| Service | Endpoint | Status |
|---|---|---|
| VITO | `/internal/v1/clients/snapshot` | ❌ Missing |
| VARAPI | `/internal/v1/providers/snapshot` | ❌ Missing |
| TUSO | `/internal/v1/facilities/snapshot` | ❌ Missing |
| MSIKA | `/internal/v1/catalog/snapshot` | ❌ Missing |
| ZIBO | `/internal/v1/artifacts/snapshot` | ❌ Missing |
| MUSHEX | `/internal/v1/ledger/snapshot` | ❌ Missing |

---

## 4. Eventing Gaps

### 4.1 Event Type Naming

Current v1.1-native services use the canonical format: `impilo.<service>.<domain>.<entity>.<action>.v1`

Legacy services still use non-canonical naming. Migration required per `02-migration-plan.md`.

### 4.2 Missing Event Types for New Services

| Service | Required Events |
|---|---|
| channels-service | `impilo.channels.session.created.v1`, `impilo.channels.session.closed.v1`, `impilo.channels.message.received.v1`, `impilo.channels.message.sent.v1`, `impilo.channels.escalation.created.v1` |
| coverage-service | `impilo.coverage.eligibility.checked.v1`, `impilo.coverage.preauth.requested.v1`, `impilo.coverage.preauth.approved.v1`, `impilo.coverage.preauth.denied.v1`, `impilo.coverage.claim.submitted.v1`, `impilo.coverage.claim.adjudicated.v1` |
| indawo-service | `impilo.indawo.address.created.v1`, `impilo.indawo.address.updated.v1`, `impilo.indawo.catchment.mapped.v1`, `impilo.indawo.geocode.resolved.v1` |

### 4.3 EventEnvelope Compliance

- ✅ `libs/shared-kernel-java` has `EventEnvelope` record with all v1.1 fields
- ✅ Outbox table schema defined in `docs/plan/EVENTING_AND_TOPICS.md`
- ❌ Delta-first payloads not yet implemented in legacy services
- ❌ Schema Registry (Apicurio) not yet deployed

---

## 5. Infrastructure Gaps

| Component | Status | Required By |
|---|---|---|
| Schema Registry (Apicurio) | NEW (not deployed) | STL-1 |
| HashiCorp Vault (KMS/HSM) | NEW (not deployed) | STL-6 / Phase 1 |
| Prometheus | NEW (not deployed) | Phase 0 |
| Grafana | NEW (not deployed) | Phase 0 |
| OpenTelemetry Collector | NEW (not deployed) | Phase 0 |

---

## 6. Key Architectural Compliance Status

| Spec Requirement | Status | Detail |
|---|---|---|
| Trust-first (Envoy ext_authz → TSHEPO) | ✅ Implemented | Golden thread proven |
| No PII in SHR (BUTANO CPID-only) | ✅ Implemented | Enforced by design |
| Outbox pattern | ✅ Implemented | All v1.1 services have `*_event_outbox` |
| Class A/B/C enforcement | ❌ Not implemented | Classification table exists in docs only |
| Federation protocol | ❌ Partial | `federation-connector` lib is SKELETON |
| Delta-first events | ❌ Not implemented | DeltaTracker utility not built |
| Snapshot endpoints | ❌ Not implemented | Contract defined, no implementations |
| 3-Bus separation | ❌ Not implemented | Single Kafka instance, no namespace separation |
| Break-glass protocol | ❌ Not implemented | Defined in spec only |
| Decision evidence logging | ❌ Not implemented | Schema defined, not wired |

---

## 7. Summary of Actions for This Prompt

| # | Action | Files |
|---|---|---|
| 1 | Create `channels-service` skeleton (port 8130) | New module |
| 2 | Create `coverage-service` skeleton (port 8140) | New module |
| 3 | Create `indawo-service` skeleton (port 8150) | New module |
| 4 | Update `services/pom.xml` with new modules | Modified |
| 5 | Each service gets: pom.xml, Application class, application.yml, application-test.yml, V001__init.sql, GoldenContractIT | New files |
| 6 | Create execution plan for Prompts 20–22 | New doc |
