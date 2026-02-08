# Impilo vNext — Revised Service Boundaries (v1.1)

**Date**: 2026-02-08

---

## Ring Classification

### Ring 0 — Kernel (Authoritative DPI Services)

Ring 0 services define national truth primitives. They have **zero dependencies on Ring 1+**.
They must maintain the strictest operational standards: SLOs, versioning, backward compatibility.

| Service | Current Status | v1.1 Role | Port | Changes Required |
|---|---|---|---|---|
| **tshepo-authz-service** | Implemented | IAM, RBAC/ABAC, ext_authz, break-glass, step-up, Class A/B/C enforcement | 8081 | Add consistency class enforcement, decision evidence, OPA evaluation |
| **tshepo-identity-service** | Implemented | CPID resolution, MOSIP integration, keyed pseudonymization | 8181 | Add HSM-backed CPID derivation |
| **tshepo-consent-service** | Implemented | FHIR Consent CRUD, evaluation, share-links, revocation propagation | 8182 | Add High-Priority revocation channel |
| **tshepo-audit-service** | Implemented | Tamper-evident audit ledger, decision evidence, chain verification | 8183 | Add policy_version field, HMAC signing |
| **tshepo-keys-service** | Implemented | Ed25519 signing, JWKS, key rotation, certificates, KMS/HSM | 8184 | Integrate Vault/HSM backend |
| **tshepo-offline-service** | Implemented | Offline entitlements (JWT/CBOR), capability tokens, reconciliation | 8185 | Verify signed token format per Law 7 |
| **Federation Control** | **NEW** | Pod registration, authority boundaries, routing, reporting obligations | TBD | New module within TSHEPO cluster |
| **VITO** | Implemented | Client Registry (MPI), CRID/CPID, dedup/merge, federation merges | 8082 | Add merge events, snapshot endpoint, delta events |
| **VARAPI** | Implemented | Provider Registry, licensure, privileges, revocation propagation | 8083 | Add revocation propagation, snapshot |
| **TUSO** | Implemented | Facility Registry, topology, Control Tower, bookings | 8084 | Add facility federation, telemetry bus separation |
| **MSIKA** | Implemented | Product & Service Registry, catalogs, tariffs, packs | 8086 | Consolidate with product-registry-service, add delta+snapshot |
| **ZIBO** | Implemented | Terminology & Semantic Governance, FHIR resources, validation | 8085 | Add delta events, snapshot endpoint |
| **BUTANO** | Implemented | Shared Health Record (FHIR R4), longitudinal record, IPS | 8090 | Add FHIR $export, reference-only events |
| **UBOMI** | Implemented | CRVS Interface, births/deaths linkage | TBD | Add death event propagation |
| **MUSHEX** | Implemented | Finance Engine, payments, claims, settlement, fraud | 8087 | Add Class A enforcement, snapshot |

#### Ring 0 Platform Primitives (NEW — Required by v1.1)

| Primitive | Current Status | v1.1 Role | Changes Required |
|---|---|---|---|
| **Schema Registry** | NOT IMPLEMENTED | Event schema governance, compatibility gates, CI validation | Deploy Apicurio/Confluent, define all event schemas |
| **Contract Testing** | NOT IMPLEMENTED | Partner integration validation | Add Pact or similar framework |
| **Vault KMS/HSM** | NOT IMPLEMENTED | Key management, secrets, CPID derivation | Deploy Vault, integrate with tshepo-keys |
| **Developer Portal** | NOT IMPLEMENTED | SDKs, sandbox, API keys, docs, deprecation policy | Build with Redocly + springdoc-openapi |
| **Observability** | NOT IMPLEMENTED | Prometheus, Grafana, OpenTelemetry, SLO dashboards | Deploy full stack |

---

### Ring 1 — Clinical Plane (Care Execution)

Ring 1 services execute clinical workflows. They depend on Ring 0 for truth (identity, registries, terminology, auth).
They MUST enforce Clinical Safety Consistency Classes.

| Service | Current Status | v1.1 Role | Ring 0 Dependencies |
|---|---|---|---|
| **PCT** | Implemented | Patient Care Tracker: journeys, encounters, queues, discharge, triage | TSHEPO (auth), VITO (identity), TUSO (facility), BUTANO (record), MUSHEX (payment gate) |
| **OROS** | Implemented | Orders & Results Orchestration: orders, worklists, worksteps, SLA | TSHEPO (auth), BUTANO (writeback), ZIBO (terminology), TUSO (routing) |
| **COSTA** | Implemented | Costing Engine: billing, tariffs, exemptions, claims packing | TSHEPO (auth), MSIKA (tariffs), MUSHEX (payment intents) |
| **Pharmacy** | Implemented | Dispense workflow, stock management, FEFO, barcode, pickup proof | TSHEPO (auth), OROS (orders), MUSHEX (charges), MSIKA (formulary) |
| **MSIKA Flow** | Implemented | Health Marketplace: orders, fulfillment, vendor, booking | TSHEPO (auth), MSIKA (catalog), MUSHEX (payments), VARAPI (credentials) |
| **Inpatient** | Skeleton | Bed management, ward allocation | TSHEPO, TUSO, PCT |
| **Scheduling** | Not started | Appointment booking, capacity management | TSHEPO, TUSO, VARAPI |
| **Referral/Care Network** | Not started | Referral routing, care coordination | TSHEPO, TUSO, VARAPI, PCT |
| **Telemedicine** | Not started | Virtual consultation framework | TSHEPO, PCT, OROS |

---

### Ring 2 — Supply, Data, Integration (Scale Layer)

Ring 2 services provide operational support, analytics, and integration. They must NOT impact Ring 1 latency or safety.

| Service | Current Status | v1.1 Role | Notes |
|---|---|---|---|
| **Inventory** | Implemented | Supply chain, stock visibility, requisitions, handovers | Route to supply bus |
| **Inventory eLMIS Adapter** | Implemented | External eLMIS integration | Ring 2 integration |
| **Pharmacy eLMIS Adapter** | Implemented | External eLMIS integration | Ring 2 integration |
| **FHIR Gateway** | Skeleton | Interoperability gateway (FHIR/other standards) | Ring 2 interop |
| **Integration Hub** | Skeleton | DHIS2, eLMIS, LIMS, iHRIS adapters | Ring 2 interop |
| **Notification Hub** | Skeleton | SMS, email, push notifications | Ring 2 support |
| **Offline Sync** | Skeleton | Edge execution framework, data sync | Ring 2 resilience |
| **Jobs Service** | Skeleton | Background job scheduling | Ring 2 support |
| **PACS Adapter** | Skeleton | DICOM/Orthanc integration | Ring 2 imaging |
| **Landela Adapter** | Implemented | Document gateway (Landela/MinIO) | Ring 2 documents |
| **Document Store** | Implemented | MinIO/S3 object storage with AV scan | Ring 2 storage |
| **CVS (Credential Verification)** | Implemented | Ed25519 signed PDFs + QR verify | Ring 2 credentials |
| **Card Print Agent** | Implemented | Smart card printing | Ring 2 physical |
| **Share Slip** | Implemented | OTP-based delegated pickup | Ring 2 sharing |
| **Analytics/NDR** | Not started | Data pipelines, BI, reporting | Ring 2 data |
| **Surveillance (eIDSR)** | Not started | Disease surveillance | Ring 2 data |
| **Data Access Governance** | Not started | Research exports, purpose limitation | Ring 2 data |

---

## Service Consolidation Recommendations

| Action | Rationale |
|---|---|
| Merge `product-registry-service` into `msika-service` | v1.1 defines MSIKA as the canonical product registry. Two services for one domain violates service count discipline (Law 9). |
| Keep `tshepo-service` (legacy) as redirect during migration | Existing consumers may reference old endpoints. Phase out over 1 release cycle. |
| DO NOT extract Federation Control as separate service | Keep as module within TSHEPO cluster per Law 9 (module-first). May extract later if scale requires. |
| Keep Pharmacy + Inventory adapters as separate services | They bridge external systems (eLMIS) — clear boundary justification for extraction. |

---

## Dependency Rules (Enforced)

```
Ring 0 ──→ NOTHING (zero outbound dependencies to Ring 1+)
Ring 1 ──→ Ring 0 only (via REST/events, never direct DB access)
Ring 2 ──→ Ring 0 + Ring 1 (via REST/events)

NEVER: Ring 0 → Ring 1 (Kernel must not depend on clinical)
NEVER: Ring 1 → Ring 2 for critical path (supply/analytics must not block care)
NEVER: Any Ring → Direct DB of another service
```
