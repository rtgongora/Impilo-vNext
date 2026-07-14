# Design — G4: Delegate address/geography ownership to ndila

**Status:** Phase 0 (seam) + Phase 3 (retire orphan) **BUILT & pushed** (`eb92b9fe3`); Phase 1
(backfill) + Phase 2 (reads-authoritative) **remain** — they need the migration run on live data /
a deploy. **Type:** consolidation refactor of live systems. **SoR target:** `ndila-service`.

> Scheduled per PO decision (2026-07-14) to plan both consolidation refactors ahead of a driving
> need. This is a **design**, not a build — it touches live identity data (client addresses), so it
> is staged, dual-write-first, and migration-gated.

## Grounding correction (important)

A prior note claimed client addresses "live via registry-intake into `ind_addresses`." **The code
does not support that.** The authoritative person address today is a **JSONB blob on the client row
in vito-service** (`clients.address`), written by vito's own registration/update services. Indawo's
`ind_addresses` table has a live controller but **no caller** (orphaned), and the
`ind_catchment_areas` / `ind_facility_locations` tables were already dropped (indawo `V008`). The
design below is built on the real state, not the note.

## Current state (three disconnected address stores)

| Store | Location | Holds | Writers |
|---|---|---|---|
| **vito** `clients.address` (JSONB) | vito-service | **person/client address** (the live one) | `ExternalRegistrationService:233`, `ClientIdentityOperationsService:769`, `ClientUpdateService:69` |
| **indawo** `sites.address_json` (JSONB) | indawo-service | facility/site address | `SiteService:61,97` (via BFF `RegistryIntakeService`) |
| **indawo** `ind_addresses` | indawo-service | standalone address | **none** (orphaned; live endpoint, no caller) |
| **ndila** `ndila_locations` | ndila-service | **canonical geo location** (SoR) | `NdilaLocationService` |

`ndila_locations` already carries the delegation hook: **soft ownership back-reference**
`owner_service` / `owner_entity_type` / `owner_entity_id`, plus lat/long, normalized address parts,
plus_code/what3words/geohash/h3, and a live **geocoding engine** (`NdilaGeocodingController` + ~10
provider adapters). Registry docs already declare ndila the **geography/geocoding/catchment SoR**
(`system-of-record-map.md:56`) and say other services should delegate rather than duplicate.

So the gap is not "ndila can't own this" — ndila already can and is declared to. The gap is that
**vito (person) and indawo (site) still self-own address blobs and never register them with ndila.**

## Design

### Ownership model
Every person/facility address becomes an `ndila_locations` row owned by the originating service via
the existing back-reference:
- person: `owner_service='vito'`, `owner_entity_type='client'`, `owner_entity_id=<health_id>`
- site: `owner_service='indawo'`, `owner_entity_type='site'`, `owner_entity_id=<site_id>`

The owning record keeps a **`ndila_location_id` reference column** (the id ndila returns) and a
**denormalized cached address** (read-optimization + offline), but ndila is authoritative for the
normalized address + coordinates.

### Offline-safe write path (the key constraint)
Vito registration can happen **offline / federated**, so a synchronous ndila call is not always
possible. Therefore **do not** make address registration a blocking cross-service call. Instead:

1. Vito writes the address locally as it does today (keep `clients.address` as the cache) **and**
   emits an outbox event (`CLIENT_ADDRESS_UPSERTED` with health_id + raw address).
2. An ndila consumer materializes/updates the canonical `ndila_locations` row (geocoding it through
   the existing engine), keyed idempotently by `(owner_service, owner_entity_type, owner_entity_id)`.
3. Ndila emits `LOCATION_MATERIALIZED`; vito stores the returned `ndila_location_id` on the client.

This preserves offline registration, avoids a new hard dependency in the registration hot path, and
reuses the outbox pattern every service already has. (A synchronous BFF read path can still resolve
the live canonical address from ndila for online consumers.)

### Read path
- Online consumers that need normalized/geocoded address resolve via ndila
  (`/internal/v1/ndila/**` through the BFF) using the stored `ndila_location_id`.
- The cached `clients.address` remains for offline/degraded reads and FHIR demographics, flagged as
  a cache (ndila is authoritative).

## Staged rollout (each stage independently shippable + reversible)

- **Phase 0 — seam, no behaviour change.** Add `ndila_location_id` (nullable) to vito `clients`;
  add the vito→ndila outbox event + ndila consumer; ndila materializes locations. Vito still writes
  `clients.address`. Dual-write; nothing depends on ndila yet. *Fully reversible.*
- **Phase 1 — backfill.** One-off job: for every existing `clients.address`, emit the upsert event
  (or batch-call ndila) so ndila has a canonical row for each; store `ndila_location_id`. Idempotent
  on the owner key. Verify counts (clients-with-address == ndila locations owned by vito).
- **Phase 2 — reads delegate.** Point online demographics/FHIR/BFF address reads at ndila via the
  stored id; keep `clients.address` as cache only. Deprecate direct writes to the JSONB (writes now
  flow through the outbox → ndila → cache-refresh).
- **Phase 3 — retire orphan + optional site delegation.** Retire indawo `ind_addresses` (orphaned —
  drop the controller/entity/table after removing the OpenAPI path; no runtime caller). Optionally
  apply the same delegation to indawo `sites.address_json` (separable, lower priority).

## Blast radius (from the code)

- **Move:** 3 vito client-address writers + (optionally) 1 indawo site writer.
- **Retire (safe):** `ind_addresses` + `AddressController`/`AddressEntity`/`AddressRepository` — no
  live caller; only the OpenAPI contract + report JSONs reference it. `ind_catchment_areas` /
  `ind_facility_locations` already dropped.
- **Migrate:** `clients.address` JSONB → `ndila_locations` (the real data migration); add
  `clients.ndila_location_id`.
- **Contracts:** `contracts/openapi/indawo.openapi.yaml` (`/internal/v1/addresses`); vito client DTOs
  (`ClientDemographicsUpdateRequest`, `ExternalClientRegistrationRequest` carry `addressLine1/city/
  district/province` — unchanged for callers; only the sink changes).
- **Consumers reading `clients.address`:** `ClientIdentityOperationsService:1052` snapshot + any
  FHIR/demographics publisher — must tolerate the cache/authoritative split.

## Prerequisites / operator input

- Confirm vito has outbound infra for the (async) ndila path — outbox exists; a synchronous fallback
  client is optional.
- **Data-migration sign-off** — Phase 1 backfills real person addresses; run dry-run + reconciliation
  (same discipline as the wallet-drain runbook).
- Decide whether to keep `clients.address` as a permanent cache or fully remove it after Phase 2
  (recommend keep-as-cache for offline).
- G21 (person-address geocoding) is **delivered for free** by this refactor: once addresses register
  with ndila, they are normalized/geocoded by the existing engine — the current gap (vito addresses
  never geocoded) closes at Phase 1.

## Non-goals

- Not changing the client DTO shape callers send (they still send address parts).
- Not migrating logistics delivery addresses (nhume) or household-unit passthrough — out of scope.
- Not building new geocoding (ndila already has it).
