# Facility Lifecycle Wave — Product Truth Note

**Status: 2026-07-01.** The PCT queue-definition issue exposed a broader question, and investigation
(two code sweeps across TUSO, PCT, experience-bff, and the web shell) produced a precise, evidence-based
picture. This note records it so the wave is scoped against reality, not assumptions.

## Product truth framing (per the facility-lifecycle directive)

> The queue-definition issue exposed a broader facility lifecycle gap. Queue definitions are **not**
> standalone PCT configuration. They are part of the **TUSO-led facility lifecycle**. Facility
> creation, activation, service-point setup, workspace setup, and queue configuration must be real
> end-to-end, and downstream services such as PCT must **materialise** the operational pieces they
> need. PCT queues are one downstream operational materialisation of facility configuration, not a
> standalone PCT editing domain.

## What already EXISTS in TUSO (verified — do not rebuild)

TUSO is already the source of truth for facility identity and configuration, far more completely than
the docs implied:

- **Facility master record** — `tuso-service/.../persistence/entity/FacilityEntity.java`: name, code,
  type, level, ownership, managing authority, province/district, lat/long, tier, deployment mode,
  parent/merged-into relationships, opened/closed dates, `gofr_id`, `institution_file_number`,
  registration pathway, JSONB metadata + alias names, audit columns. Related entities:
  `FacilityGeoEntity`, `FacilityCapabilityEntity` (services + operating hours),
  `FacilityReadinessEntity` (connectivity/power/devices/EHR), `FacilityContactEntity`,
  `FacilityIdentifierEntity`.
- **Lifecycle state machine** — `FacilityRegulatoryStatus` (16 states: DRAFT → … → REGISTERED_ACTIVE,
  plus RENEWAL/RESTRICTED/SUSPENDED/CLOSED/MERGED/VOLUNTARILY_CLOSED) and `FacilityApplicationState`
  (18 states). Transition endpoints in `FacilityRegulatoryController`: applications, submit,
  ready-for-inspection, inspections, record-inspection, compliance-actions, committee-reviews,
  enforcement-cases, documents.
- **Configuration (TUSO SoR)** — service-points (`ServicePointController`), units/departments
  (`FacilityUnitController`), workspaces (`WorkspaceController`), setup wizard
  (`FacilitySetupController`: departments → service-points → queues → workflows → workforce →
  oros-routing → khuluma-channels → fundo-readiness → go-live).
- **Events / outbox** — `tuso.event_outbox` (v1.1 context columns) + `TusoOutboxPublisher` (scheduled
  Kafka poller, DUAL/legacy/v1.1 emit modes). Real events emitted today: `tuso.facility.created`,
  `tuso.facility.updated`, `tuso.facility.closed`, `tuso.facility.merged`,
  `tuso.facility.application.created`, `SERVICE_POINT_CREATED`, `SERVICE_POINT_RETIRED`,
  `FACILITY_UNIT_CREATED`, `tuso.workspace.created`, `tuso.workspace.updated`, `tuso.config.*`.
- **Web UI** — real screens: `/facility` (select), `/facility/[id]` (detail), `/facility/[id]/cockpit`,
  `/facility/[id]/setup` (wizard), `/facility/[id]/control-tower`, `/facility/[id]/regulators`,
  `/registry/facility-lifecycle` (regulatory dashboard).
- **Mobile (this session, waves G/M/N/O/P)** — provider-app now surfaces Control Tower, Regulators,
  Facility Setup (units + service-points + wizard step-advance + go-live), and a read-only Queue
  Definitions viewer.

## The one true MVP-critical gap: TUSO → PCT materialisation

PCT does **not** materialise facility configuration from TUSO. Specifics:

- `PctEventConsumer.consumeTusoWorkspaceUpdated` (topics `tuso.workspace.updated` /
  `impilo.tuso.workspace`) is a **stub** (logs only; `// Future: invalidate local cache`). It does not
  inject `QueueRepository` and writes nothing.
- The only writer of PCT `QueueEntity` rows is the demo seed `V010__seed_demo_queue_journey.sql`
  (plus an integration test). **There is no service-layer create path** — so PCT's "queue truth" is
  demo data, not materialised facility config.
- `TusoIntegration.getQueueDefinitions(UUID facilityId)` calls `GET {tuso}/v1/facilities/{id}/queues`,
  **which TUSO does not expose** — dead code that always returns empty. The real queue source in TUSO
  is `ServicePointEntity` (each service point carries `queueId`, `workflowArchetype`, `facilityId`,
  `tenantId`, `active`). So: **service-points-with-a-queueId ARE the queue definitions.**

Correct target model:

```
TUSO facility lifecycle → service-points/workspaces (each with queueId)
  → SERVICE_POINT_CREATED/RETIRED events + reconciliation
  → PCT materialises QueueEntity (upsert/deactivate)
  → GET /v1/queues (PCT) → BFF /internal/v1/queue/definitions → web/mobile viewer
```

## BLOCKING DECISION (why this cannot be safely built yet)

**Facility identity is not correlated across services.** TUSO facility id is a numeric `Long`
(`FacilityEntity.id` IDENTITY PK; workspace/service-point events emit `facilityId` as a number). PCT
`QueueEntity.facilityId` is a **`UUID`**. The BFF `FacilityModeController` makes the split explicit:
`@PathVariable long facilityId` (TUSO anchor) + optional `@RequestParam String pctFacilityId` (PCT
UUID) — the PCT UUID is **supplied by the caller**, not derived. No stored TUSO-Long ↔ PCT-UUID
mapping exists in the code, and `ServicePointEntity.queueId` is a free-form `String` (not a UUID).

Materialising service-points → PCT queues therefore requires a canonical facility-identity decision.
Guessing it would mis-key operational queues to the wrong facility (patient-safety-adjacent), so it is
parked as a decision, not implemented on assumption. **Options:**

1. **Add a canonical facility UUID in TUSO** (persist + emit it on facility/service-point events); PCT
   keys `facilityId`/`queueId` off it. (Cleanest; a TUSO schema + event-payload change.)
2. **PCT adopts TUSO's Long facility id** (change `QueueEntity.facilityId` + PCT facility-keyed
   entities/queries to Long). (Larger PCT migration.)
3. **A facility-identity resolver/mapping table** (TUSO Long ↔ PCT UUID) owned by TUSO or a shared
   identity service, populated on facility activation. (Adds a mapping component.)

**Recommendation: Option 1** — a canonical facility UUID emitted by TUSO — because it aligns with the
"one anchor, many IDs" doctrine, keeps PCT's UUID model, and makes every downstream materialisation
(Dura stores, Ndila map points, Vashandi locations) key off one stable id.

### DECISION TAKEN: Option 1 — increment status

Proceeding with Option 1. Incremental, verified delivery (each increment compiles/tests before push):

- **Increment 1 — TUSO canonical facility UUID keystone (LANDED 2026-07-01):** additive migration
  `V013__facility_canonical_uuid.sql` (add `facility_uuid`, backfill existing, default
  `gen_random_uuid()`, NOT NULL, unique index) + `FacilityEntity.facilityUuid` generated in
  `@PrePersist`. Verified: `tuso-service` compiles offline. **Compile-verified + SQL-reviewed; the
  migration is additive/idempotent but was NOT executed against a DB in-sandbox** (no DB here).
- **Increment 2 (next):** expose `facilityUuid` in `FacilityResponse` + include it in the facility /
  service-point event payloads (`WorkspaceService`/`ServicePointService`/`FacilityService`).
- **Increment 3 (next):** PCT `QueueMaterializationService.reconcileFacilityQueues(tenantId,
  facilityUuid)` upserting `QueueEntity` from TUSO service-points; wire `PctEventConsumer`; add a TUSO
  endpoint that lists a facility's queue definitions (service-points-with-queueId) or delete the dead
  `getQueueDefinitions`; drop seed-as-truth reliance.
- **Increment 4 (next):** honest sync/materialisation status in the web/mobile queue viewer.

## Implementation plan once the id decision is made (otherwise ready)

1. `QueueMaterializationService` in pct-service — `reconcileFacilityQueues(tenantId, facilityRef)`:
   fetch TUSO service-points (with `queueId`) for the facility, upsert `QueueEntity`
   (`findByTenantIdAndQueueId` + save), deactivate queues no longer present. Reconciliation-first
   (survives missed/replayed events).
2. Wire `PctEventConsumer` to reconcile on `SERVICE_POINT_CREATED`/`SERVICE_POINT_RETIRED` /
   `tuso.workspace.updated` (inject the service + `QueueRepository`).
3. Remove seed-as-truth: keep `V010` for local/test only; PCT queue truth comes from materialisation.
4. Add a TUSO endpoint that actually lists a facility's queue definitions (or delete the dead
   `getQueueDefinitions` and source from service-points).
5. Honest UI: the mobile/web queue viewer shows materialisation/sync status (materialised / stale /
   pending), labelled "owned by TUSO facility configuration, materialised into PCT for care operations."
6. Tests: `QueueMaterializationService` unit test (mock TUSO client + `QueueRepository`, assert upsert +
   deactivate) and a `PctEventConsumer` test asserting reconcile is triggered.

## Downstream materialisation (same pattern, later waves)

Once facility identity is canonical, the same reconcile-from-TUSO pattern extends to: PCT care
locations, Dura stock locations, Ndila map/routing points, Vashandi assignment locations, OROS order
destinations — each an honest, event-plus-reconciliation materialisation, surfaced in a facility
"downstream readiness" checklist driven by real API checks (not fabricated ticks).

## Honest boundary

TUSO lifecycle + events + service-point-as-queue config are **real today**. PCT materialisation is
**not** — it is blocked on the facility-identity decision above, not on build capacity. No fake PCT
queue editor and no seed-as-MVP-truth will be shipped to paper over it.

---

## Zimbabwe Master Health Facility absorption (MASTER_HEALTH_FACILITY_2024_07_23)

**Product truth note (required):** The Zimbabwe Master Health Facility dataset dated 23 July 2024 has
been cleaned into an Opus-ready TUSO absorption package. The importable dataset excludes facilities
without facility codes and excludes duplicate facility-code/name conflicts for manual review. Missing
latitude/longitude, facility type, ownership, and status are acceptable for import but must remain
visible as missing/incomplete on the frontend and in the facility setup checklist. Imported facilities
enter TUSO as national facility master records **pending configuration**, not as fully operational
vNext facilities.

**Package placed:** `data/source/zimbabwe/master-health-facility/2024-07-23/` (+ PROVENANCE.md). Counts
verified exactly (2,023 source = 1,773 clean + 250 excluded/review; 124 missing-code, 81 dup-code,
47 dup-name; 194 acceptable-missing).

**Existing infra reused (not rebuilt):** `FacilityMasterImportService` + `FacilityMasterImportController`
(`/v1/internal/facilities/import/master-pack{,/dry-run,/quality-report}`) +
`scripts/operator/import-facility-master-pack.sh` + migration `V011` (staging + `facility_import_run`) +
`FacilityDataQualityController` (BFF quality-report). Mockito test harness runs in-sandbox.

**Increment A — product-truth enforcement in the import service (LANDED 2026-07-01, verified 5/5 tests):**
The pre-existing service violated three binding rules; fixed:
- **No synthetic codes.** `resolveFacilityCode` no longer fabricates `MHL-<uid>`; a missing-code row is
  excluded (`EXCLUDED_MISSING_FACILITY_CODE`, skipped, never created). Duplicate code →
  `EXCLUDED_DUPLICATE_FACILITY_CODE` (review, no auto-merge).
- **Not operational on import.** New imported facilities enter `IMPORTED_PENDING_CONFIGURATION` (new
  `FacilityRegulatoryStatus` value), not `REGISTERED_ACTIVE`.
- **Blanks never clobber verified data.** On update, master values win only when present; blank CSV
  fields are preserved. Blank status no longer becomes `ACTIVE`/`INACTIVE` — it sets
  `operating_status = MISSING_REQUIRES_CONFIRMATION`.
- **Structured completeness flags** in facility metadata (`geospatial_incomplete`,
  `missing_facility_type`, `missing_ownership`, `missing_operating_status`, `source_label`) so the
  frontend can show missing acceptable fields without faking defaults.

**Remaining increments (documented, not yet built):** CSV→seed loader for `clean_tuso_facility_import.csv`
(current importer reads a JSON seed); persist `facility_import_run` batch rows + `duplicate-name` check
in-service; BFF routes + admin UI (batches list, batch detail, review queues, missing-field checklist);
importer script modes (`--stage-only/--validate/--apply-approved/--reconcile`); facility-detail
missing-field checklist UI; downstream materialisation honesty. None of these are faked; imported
facilities remain `IMPORTED_PENDING_CONFIGURATION` until real configuration exists.
