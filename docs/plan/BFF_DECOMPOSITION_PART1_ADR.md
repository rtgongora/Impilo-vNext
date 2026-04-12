# BFF Decomposition & PCT Architecture — Part 1: Architecture Decision Record

**Version**: 1.0.0
**Date**: 2026-04-12
**Status**: PROPOSED — pending architectural review
**Scope**: Experience-BFF monolith decomposition, PCT clinical execution store, BUTANO publication flow

---

## 1. Problem Statement

The experience-bff service has accumulated **123 tables in a single PostgreSQL database**, acting as
the system of record for clinical, operational, financial, social, and wellness data. This violates:

- **Law 2A** (logical boundaries ≠ deployment boundaries) — the BFF has become a hidden monolith
- **Law 9** (service count discipline) — while we have 69 services, the BFF bypasses most of them
- **Section 1.4.5** (canonical truth rule) — clinical data that should survive application replacement
  is trapped in a convenience store

Clinical writes (allergies, vitals, conditions, prescriptions, encounters, lab orders) flow from
**UI → BFF → BFF's own Postgres via JdbcTemplate INSERT**, bypassing PCT, OROS, Pharmacy, and BUTANO entirely.

The BFF already has typed service clients for 25 sovereign services but uses them primarily for reads.

---

## 2. Architectural Decision: PCT as Clinical Execution Store

### 2.1 Amendment to Section 1.4.5

The Technical Companion Spec v1.3 Section 1.4.5 states:

> *"If a data element must survive local application replacement and remain longitudinally valid
> beyond the lifecycle of a departmental workflow application, it belongs either in BUTANO or in
> the appropriate Ring 0 canonical registry. Local execution stores may hold transient workflow
> state, task state, draft state, and operational convenience data, but they must not become
> hidden system-of-record replacements for national truth domains."*

**Proposed amendment**: PCT is not a "departmental workflow application." PCT is the national care
coordination engine — the EHR brain that follows every patient across every facility, every modality,
every encounter. Its database is not transient convenience storage. It is the **operational clinical
execution store** for the national Health Operating System.

**Revised position**:

- **PCT stores clinical data** — allergies, conditions, vitals, immunizations, observations,
  clinical notes, care plans, prescriptions, orders, results, growth, EWS, maternity, emergency
- **PCT is the internal system of record** for active clinical execution
- **BUTANO is the longitudinal interoperability projection** — the FHIR R4 SHR that publishes
  clinical truth for cross-system, cross-facility, cross-country exchange and IPS generation
- **PCT feeds BUTANO** via the outbox/event pattern — not the reverse

### 2.2 Rationale

PCT must store clinical data because:

1. **Speed** — clinicians cannot wait for a FHIR round-trip to record vitals during resuscitation
2. **Coordination** — PCT tracks order→result→action dependencies; it needs the clinical context
3. **Completeness** — PCT assembles the full clinical picture from all contributors during an encounter
4. **Safety** — Class A consistency checks (controlled substance prescribing, allergy interactions)
   require local transactional integrity, not eventual consistency from a FHIR store
5. **Offline** — Class C offline entitlements require a local clinical store that works without BUTANO

### 2.3 The Boundary

| Concern | PCT Owns | BUTANO Owns |
|---------|----------|-------------|
| Active encounter clinical data | YES | Receives via event |
| Longitudinal history across systems | References BUTANO | YES |
| FHIR R4 resource representation | No (internal schema) | YES |
| IPS generation | No | YES |
| External system interoperability | No | YES |
| Cross-country exchange | No | YES |
| Journey/queue/task orchestration | YES | No |
| Order tracking and dependencies | YES | No |
| Real-time clinical decision support | YES (has the data) | No (query target) |

### 2.4 Write Flow

```
Clinician → UI → BFF → PCT (stores in pct_db, writes outbox event)
                          ├→ OROS (if order needed — PCT tracks completion)
                          ├→ Pharmacy (if prescription — PCT tracks dispensing)
                          ├→ COSTA (if billing needed — PCT anchors encounter cost)
                          └→ Outbox event → Kafka → BUTANO (creates FHIR resource)
                                                  → NDR (national intelligence)
                                                  → tshepo-audit (audit chain)
```

---

## 3. Architectural Decision: BFF Becomes a Thin Proxy

### 3.1 Current State

The BFF has 123 tables across these domains:

- **~45 clinical tables** (allergies, conditions, vitals, encounters, prescriptions, lab orders, etc.)
- **~20 operational tables** (facilities, workspaces, shifts, beds, wards, queues, appointments)
- **~15 inpatient/emergency tables** (admissions, resuscitation, maternity, CTG, ward charts)
- **~15 communication tables** (messages, announcements, clinical pages, community groups)
- **~15 wellness tables** (activities, clubs, challenges, mood, sleep, exercise, Health Connect)
- **~10 marketplace/finance tables** (orders, wallets, coverage, crowdfunding)
- **~5 infrastructure tables** (outbox, idempotency, audit, admin users)

### 3.2 Target State

The BFF retains ONLY:

- **Infrastructure**: event_outbox, experience_bff_idempotency, audit_log
- **Wellness** (BFF-native per experience doctrine): wellness_*, feed_items, feed_likes
- **Social** (BFF-native): community_groups, community_group_members, professional_pages
- **UI convenience caches** (read-only, populated from sovereign service responses)

Everything else moves to its sovereign owner.

### 3.3 Sovereign Owner Map (Summary)

| Domain | Tables | Sovereign Owner | Write Path |
|--------|--------|----------------|------------|
| Clinical (allergies, conditions, vitals, immunizations, notes, care plans, observations, growth, EWS, apgar) | ~20 | **PCT** | BFF → PCT API |
| Encounter/journey/queue/triage | ~8 | **PCT** | BFF → PCT API |
| Orders/results/prescriptions | ~5 | **PCT** (tracks) → **OROS/Pharmacy** (executes) | BFF → PCT API |
| Inpatient/emergency/maternity/CTG/ward charts | ~15 | **PCT** (pct_inpatient schema) | BFF → PCT API |
| Discharge/referrals | ~3 | **PCT** | BFF → PCT API |
| Facilities/workspaces/shifts | ~5 | **TUSO** | BFF → TUSO API (read cache in BFF) |
| Patients | ~1 | **VITO** | BFF → VITO API (read cache in BFF) |
| Beds/wards/admissions/transfers | ~5 | **PCT** or **Inpatient** | BFF → PCT API |
| Appointments | ~1 | **TUSO** (booking) | BFF → TUSO API |
| Inventory/stock | ~6 | **Inventory Service** | BFF → Inventory API |
| Coverage | ~1 | **Coverage Service** | BFF → Coverage API |
| Marketplace | ~3 | **MSIKA-Flow** | BFF → MSIKA-Flow API |
| Omnichannel (USSD/SMS/IVR) | ~6 | **Channels Service** | BFF → Channels API |
| Messages/announcements | ~8 | **Channels/Notification** | BFF → Channels/Notification API |
| Wellness | ~15 | **BFF** (stays) | Direct write (experience-native) |
| Wallets/crowdfunding | ~4 | **BFF** (stays, low-priority) | Direct write |
| Infrastructure | ~5 | **BFF** (stays) | Internal |

---

## 4. Architectural Decision: PCT Technology Stack

### 4.1 Runtime

| Dimension | Choice | Rationale |
|-----------|--------|-----------|
| Language/Framework | Java 21 / Spring Boot 3.3 | Platform standard |
| Primary Database | PostgreSQL 16 (dedicated `pct_db`) | ACID, JSONB, RLS, partitioning |
| Cache | Redis 7 | Sub-100ms queue/encounter hot paths |
| Schema Design | 6 schemas in one DB | Isolation + cross-schema coordination |
| Eventing | Kafka via outbox pattern | CompanionOutboxPublisher (shared-kernel) |

### 4.2 PCT Database Schema Structure

```
pct_db (dedicated, encrypted, RLS-enforced)
├── pct_core        — journeys, encounters, tasks, transitions, referrals
├── pct_clinical    — allergies, conditions, vitals, immunizations, notes,
│                     observations, care plans, growth, EWS, apgar
├── pct_orders      — order tracking, result tracking, prescription tracking
├── pct_inpatient   — admissions, beds, wards, transfers, discharge,
│                     emergency, resuscitation, maternity, CTG, ward charts
├── pct_queue       — queue definitions, queue items, triage records
├── pct_finance     — encounter billing refs, payment gates
└── pct_outbox      — event outbox for Kafka publishing
```

### 4.3 Security

- **Encryption at rest**: AES-256-GCM (app-layer) + TDE/LUKS (disk-layer)
- **Encryption in transit**: mTLS everywhere (service↔service, service↔DB, service↔Redis)
- **Tenant isolation**: PostgreSQL Row-Level Security (engine-enforced)
- **Identity model**: CPID-only (no PII — names/addresses stay in VITO)
- **Consent**: Check tshepo-consent before returning clinical history
- **Audit**: Every write produces outbox event → tshepo-audit-service
- **Keys**: Managed by tshepo-keys-service (platform KMS)

### 4.4 Redis Hot Paths

- Active queue state (who's waiting, position, priority)
- Current encounter context (open encounters per patient)
- Provider workspace sessions (who's logged in where)
- Order status tracking (pending results per encounter)
- Bed/ward occupancy snapshot

---

## 5. Architectural Decision: Seven-Plane Alignment

The service registry currently uses 10 ad-hoc plane labels. The v1.3 architectural definition
specifies **7 planes**. Alignment:

| v1.3 Plane | Services |
|------------|----------|
| **Kernel** (Ring 0) | TSHEPO cluster (7), VITO, VARAPI, TUSO, INDAWO, MSIKA, ZIBO, BUTANO (3), UBOMI, MUSHEX, Audit Ledger, Schema Registry, KMS, AI Governance, Omnichannel Control |
| **Clinical** (Ring 1) | PCT, OROS, Pharmacy, Inpatient, Document, PACS Adapter, Clinical Knowledge Platform |
| **Coverage/Financing/Payer** (Ring 1) | Coverage, Costing Engine (COSTA), Credential Verification, Share Slip + new: Scheme Admin, Membership, Preauth, Claims, Contributions, Provider Payments |
| **Public Health/Data/Governance** (Ring 2) | Surveillance, Campaigns, Data Pipeline, NDR, Reporting, Data Warehouse, Data Ingestion, Data Governance, Data Access Governance |
| **Supply** (Ring 2) | Inventory, Inventory eLMIS Adapter, Asset Registry |
| **Intelligence/AI** (Ring 2) | Guidance, Rules, Forms, Search, Clinical Knowledge Platform |
| **Experience** | Experience BFF, all UI apps, mobile apps |

Cross-cutting: Integration Hub, Workflow, Notification, Channels, Jobs, Offline Sync/Edge,
Observability, Security Hardening, Support, Developer Portal, Dispatch, IoT Ingestion,
Landela Adapter, Connector FHIR Adapter.

---

## 6. Key Open Questions

1. **Should Inpatient be absorbed into PCT?** — Law 9 says start modular. Inpatient currently has
   3 entities and 2 migrations. PCT could absorb it as `pct_inpatient` schema rather than maintaining
   a separate service with separate deployment.

2. **Coverage/Financing plane services** — The v1.3 spec defines 11 capability areas. Currently only
   Coverage Service and MUSHEX exist. Do we build the remaining 9 as modules within those two, or as
   separate services? Law 9 says modular-first.

3. **Channels as Ring 0** — The spec puts omnichannel orchestration in Ring 0 kernel. Currently
   channels-service is `plane: integration`. Should be promoted.

4. **PCT sovereignty** — PCT is currently `sovereign: false` in the registry. Given its role as the
   national clinical execution store, should it be `sovereign: true`?

---

## 7. Next Documents

- **Part 2**: Complete BFF table → sovereign owner mapping (every table, every controller, every write path)
- **Part 3**: PCT schema DDL design (Flyway migrations for 6 schemas)
- **Part 4**: Wave execution plan (migration order, strangler pattern, dual-write strategy)
