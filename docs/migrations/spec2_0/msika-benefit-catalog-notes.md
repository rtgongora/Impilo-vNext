# MSIKA — Benefit Catalog Alignment Notes (Spec 2.0)

> **Status:** Planning — no code changes to `msika-service` in this iteration.
> **Author:** auto-generated during INDAWO Ring 0 implementation.
> **Date:** 2026-03-11

## Context

The vNext Tech Companion Spec 2.0 introduces benefit catalog requirements that
MSIKA (the Benefits & Entitlements service) must support. This document
summarizes what MSIKA must add to align with the spec.

## Required Additions

### 1. Benefit Catalog Registry Endpoint

MSIKA must expose a catalog of available benefits per scheme/program:

- `GET /internal/v1/benefit-catalogs` — list all benefit catalogs (paged)
- `GET /internal/v1/benefit-catalogs/{catalog_id}` — retrieve a single catalog
- `PUT /internal/v1/benefit-catalogs/{catalog_id}` — upsert a catalog definition (idempotent)

### 2. Data Model Changes

Add a `benefit_catalog` table:

| Column | Type | Notes |
|--------|------|-------|
| catalog_id | UUID PK | |
| tenant_id | UUID NOT NULL | Multi-tenant scoping |
| scheme_id | UUID NOT NULL | FK to scheme/program |
| name | VARCHAR(255) | Human-readable catalog name |
| effective_from | DATE | When this catalog version becomes active |
| effective_to | DATE | Nullable; when this catalog expires |
| benefits_json | JSONB | Array of benefit definitions |
| status | VARCHAR(32) | DRAFT, ACTIVE, EXPIRED |
| version | INT | Optimistic locking |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

### 3. Events

Following the v1.1 delta-first EventEnvelope pattern:

- `impilo.msika.benefit-catalog.created.v1`
- `impilo.msika.benefit-catalog.updated.v1`

Each event must include:
- `schema_version >= 1`
- `meta.partition_key = catalog_id`
- Delta payload with `op`, `before`, `after`, `changed_fields`

### 4. Outbox Pattern

Use the standard `event_outbox` table with all v1.1 context columns
(correlation_id, causation_id, idempotency_key, producer, tenant_id, pod_id,
subject_id, subject_type, partition_key).

### 5. Snapshot Endpoint

- `GET /internal/v1/snapshots/benefit-catalogs?cursor=&limit=&as_of=`

Required for downstream consumers (e.g., PCT, Coverage) to bootstrap their
local catalog state.

### 6. v1.1 Header Compliance

Ensure MSIKA's benefit catalog endpoints enforce:
- `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID` (mandatory)
- `Idempotency-Key` on PUT/POST command endpoints
- Standard error envelope on validation failures

### 7. Integration Points

- **INDAWO**: Benefit catalogs may reference site-level constraints (e.g.,
  benefits only available at certain site types). MSIKA should accept optional
  `site_id` references linking to INDAWO sites.
- **PCT**: The Pricing & Costing service consumes benefit catalog events to
  update pricing rules when catalogs change.
- **Coverage**: The Coverage service uses benefit catalogs to determine member
  entitlements.

## Testing Requirements

- GoldenContractIT (extends `GoldenContractSuite`)
- MockMvc tests for:
  - Missing headers → 400 envelope
  - Idempotency replay
  - Snapshot as_of semantics
  - Outbox v1.1 compliance

## Non-Goals (This Iteration)

- No changes to existing MSIKA flow/claim endpoints
- No migration of existing benefit data structures
- No UI changes
