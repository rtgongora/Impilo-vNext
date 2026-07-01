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

**Increment B — CSV→seed loader (LANDED 2026-07-01, verified):** `loadPackFromCsv` reads the
canonicalised `clean_tuso_facility_import.csv` (previously only a JSON seed was supported). The CSV has
no `facility_uid`, so a stable, code-independent `MASTER_FACILITY_UID` correlation key is derived from
provenance (`MHF-<sourceLabel>-row<source_row>`) — never from the public code — so re-imports match the
same internal facility and never regenerate it. Header-mapped, quoted-field parser; prefers `*_canonical`
columns; blank numerics/coordinates/status preserved as null (never faked).

**Increment C — batch persistence + in-service duplicate-name guard (LANDED 2026-07-01, verified):**
Every `importPack` (dry-run or real) now writes a `FacilityImportRunEntity` audit row to the pre-existing
`tuso.facility_import_run` table (totals, quality summary, status, initiated_by, timing) — audit failures
never fail the import. A within-batch duplicate facility-name guard excludes duplicated names for review
(`EXCLUDED_DUPLICATE_FACILITY_NAME`, skipped, never auto-imported/merged) as defence-in-depth over the
already-clean pack. Tuso import tests now 10/10.

**Increment D — product visibility (import-run read API + BFF + admin UI + facility checklist)
(LANDED 2026-07-01, verified):** the persisted absorption state is now surfaced in the product, not
only in backend tests.

- **TUSO read API:** `GET /v1/internal/facilities/import/runs` (list) and `/runs/{runId}` (detail),
  tenant-scoped. Quality summary enriched from real row outcomes (imported, missing-code, dup-code,
  dup-name, excluded total, acceptable-missing, failed, valid-coordinates, source_label). Plus
  `GET /v1/internal/facilities/{id}/import-provenance`: identity separation (internal id/uuid vs public
  code vs external identifiers), acceptable-missing flags, and a configuration-completeness checklist
  (TUSO-owned items counted from repositories; queues/workforce/stores marked PENDING_DOWNSTREAM).
- **BFF proxy (policy-protected under `/internal/v1/admin/**`):** facility-import-runs list/detail,
  `/rows` (honestly reports row-level staging NOT_PERSISTED), `/review` (real breakdown buckets, per-row
  detail flagged pending), facility `import-provenance` and `missing-field-checklist`.
- **Admin UI:** `/admin/facility-imports` (batches list), `/admin/facility-imports/[runId]` (detail),
  `/admin/facility-imports/[runId]/review` (read-only, honestly labelled queues). Facility detail page
  gains an identity + provenance + missing-field + setup-completeness checklist section.
- **Verified:** TUSO `FacilityMasterImportServiceTest` (13) + `FacilityProvenanceServiceTest` (1); BFF
  `AdminFacilityImportControllerTest` (5); web tsc clean, route-parity 680/680, no-stub OK,
  product-truth 0 violations, `useFacilityImports` hook test (3) + routes (31).

**Row-level exposure:** run-level summary + breakdown are exposed; **row-level per-run staging is NOT
persisted yet** — `/rows` reports this honestly and the excluded/review source CSVs remain authoritative
for individual rows. Row-level staging is the next backend slice.

**Remaining increments (documented, not yet built):** row-level per-run staging + mutation (approve/
reject review decisions); importer script modes (`--stage-only/--validate/--apply-approved/--reconcile`)
— the operator script still reads the JSON seed with no mode flags; downstream TUSO→PCT queue
materialisation (task #15). None are faked; imported facilities remain `IMPORTED_PENDING_CONFIGURATION`
until real configuration exists. Migrations `V011`/`V013` + service changes are compile + unit-test
verified, **not DB-executed** (no database in the sandbox — no migration was run against a DB).

---

## Facility code vs. internal facility identity — security & identity correction

**Required product-truth note (verbatim, binding):**

> The uploaded master facility dataset provides facility codes, not secure digital facility
> identities. Facility codes are public administrative identifiers used for import, reporting, DHIS2
> alignment, and interoperability. TUSO must generate and maintain a separate internal Impilo/TUSO
> facility digital ID. Authentication, Facility Mode, provider assignment, facility setup authority,
> and downstream access control must use verified user identity, provider/staff assignment, Tshepo
> context resolution, and OPA policy — never possession of a facility code.

**Identity separation (verified + hardened 2026-07-01):**

- **Internal facility identity** = `FacilityEntity.facilityUuid` (opaque, immutable `updatable=false`
  canonical UUID, generated in `@PrePersist`, unique-indexed via `V013`) plus the numeric surrogate
  `FacilityEntity.id`. This is the sole reference for authz, downstream services, audit and events.
- **Public administrative code** = `FacilityEntity.facilityCode` — a matching/interoperability handle,
  never identity, never a credential.
- **External identifier taxonomy** — new `FacilityIdentifierSystem` constants formalise the external,
  public identifier systems stored in `FacilityIdentifierEntity`: `NATIONAL_FACILITY_CODE`,
  `DHIS2_ORG_UNIT_ID`, `LEGACY_EHR_FACILITY_ID`, `HPA_REGISTRATION_NUMBER`,
  `MOHCC_FACILITY_REGISTRY_CODE`, `LOCAL_AUTHORITY_CODE`, `IMPORT_SOURCE_ROW_ID`, plus the import
  correlation key `MASTER_FACILITY_UID`. `isInternalIdentity(system)` always returns `false` — an
  explicit guard asserting none of these are internal identity or authority; `issuingAuthority(system)`
  labels each by its source registry.

**Import wiring (LANDED 2026-07-01, verified 7/7 tests):**

- The import now persists the facility code as a `NATIONAL_FACILITY_CODE` **external identifier** (in
  addition to the human-facing `facility_code` field) and records `IMPORT_SOURCE_ROW_ID` provenance —
  it never uses the code as a primary key or as the internal identity.
- Re-import matches an existing facility by the `MASTER_FACILITY_UID` correlation key and **reuses the
  existing internal identity** (`id` + `facility_uuid` unchanged) — the internal id is never
  regenerated, and never derived from the public code. New tests
  `facilityCodeStoredAsExternalNationalIdentifierNotAsInternalIdentity` and
  `reimportReusesInternalIdentityAndDoesNotRegenerateItFromTheCode` prove both.

**Codebase audit (very-thorough sweep, 2026-07-01) — no code-as-authority found:**

| Surface | Finding |
|---|---|
| Login / auth | Email/phone + password only; no facility code accepted as a credential. |
| Facility Mode | `FacilityModeController` (TUSO + experience-bff) key off internal `Long facilityId`; the shell store (`useFacilityStore`) is keyed on internal `id`, code is display-only. |
| Invitations | Tracked by `invitationId` + `expiresAt` + verified provider/staff assignment, not raw codes. |
| QR codes | Only signed Health-ID QR tokens (public-key verified); no facility-code-as-authority QR. |
| Protected routes | All facility routes take internal `facilityId`/`uuid`; no `@PathVariable`/`@RequestParam facilityCode` for access control. |
| Frontend labelling | Code shown as a display subtitle / labelled `facilityCode` on the registration form; never presented as "Facility ID" or asked for as a login credential. |

The correction is therefore **structural doctrine now enforced in the identifier model + import**, and a
verified-clean audit of the login / Facility Mode / invitation / QR / route / labelling surfaces. Facility
Mode entry remains gated by verified identity + assignment + Tshepo/OPA (`FACILITY-MODE-ENTER`), with the
facility code carried only as display/interoperability metadata.
