# Org-Registry Cutover — Phase 2 Runbook

Status: **phase-2a delivered (additive + dormant scaffolding + evidence tooling)**.
Owner workstream: IATG org-registry cutover.
Last updated: 2026-07-06.

## Context

`organization-registry-service` is the **permanent future system of record (SoR)**
for organizations. `wgv_organisation` (in `workforce-governance-service`) is the
**legacy store being strangled**.

A prior workstream (W2-5) built a **one-way mirror producer**: when the mirror is
enabled, WGV publishes org create/update events and an `AFTER_COMMIT` relay POSTs
each org to the org-registry `WgvMirrorController`, which upserts an idempotent
mirror row (`source = WGV_MIRROR`, `source_ref = wgv_organisation.id`). It also
built a re-runnable backfill endpoint. **All of it is flag-gated OFF**
(`impilo.org-mirror.enabled` default `false`), so the mirror table is currently
**empty**.

The **irreversible cutover** (repoint FKs onto the org-registry key, freeze WGV
writes, disable the mirror, declare org-registry sole-SoR) is gated on a **live
mirror-completeness soak** that only the preview VM can produce.

## What phase-2a added (this change) — all additive, all dormant

Nothing below changes any default behaviour. No existing FK, column, flag default,
or read path was altered. Everything is opt-in / callable-only.

### organization-registry-service (read-only, no migration)
- `GET /internal/v1/org-registry/mirror/wgv/inventory?page=&size=` — paginated,
  read-only inventory of every `source = WGV_MIRROR` row:
  `{orgRegistryId, sourceRef, code, legalName, status, verificationStatus}`.
  Returns an empty page while the mirror is empty. Backed by
  `WgvMirrorInventoryService` over existing data (no schema change).

### workforce-governance-service
- **Reconciliation report** (criterion-1 evidence generator):
  `GET /internal/v1/workforce-governance/org-mirror/reconcile` →
  `OrgMirrorReconciliationService` pulls the org-registry inventory (via the
  extended `OrgRegistryMirrorClient.fetchInventory` GET) and diffs it against
  local **ACTIVE** `wgv_organisation` rows. Returns:
  - `wgvActiveTotal` — active `wgv_organisation` rows
  - `mirroredCount` — how many of those have a matching mirror row (by `source_ref`)
  - `missingSourceRefs[]` — active wgv ids not yet mirrored
  - `driftRows[]` — `{sourceRef, driftFields}` where `code` / `legalName` /
    `status` disagree (status compared case-normalised, since the mirror
    upper-cases status)
  - `completenessPct` — `mirroredCount / wgvActiveTotal * 100` (100.0 when there
    are no active rows). **An empty mirror honestly reports `0.0`%, never an error.**
- **V009 dual-key scaffolding** (`V009__org_registry_dual_key_scaffolding.sql`):
  adds a **nullable** `org_registry_org_id UUID` (indexed) to each of the 5
  FK-carrier tables — `wgv_hsc_employment`, `wgv_facility_organisation_link`,
  `wgv_site_organisation_link`, `wgv_organisation_unit`,
  `wgv_organisation_membership`. **Un-backfilled** (mirror is empty). The existing
  `organisation_id` / `employer_organisation_id` FK **remains authoritative until
  phase-2c**. Matching nullable entity fields (`orgRegistryOrgId`) added.
- **Callable-only dual-key backfill** (`OrgKeyBackfillService` +
  `POST /internal/v1/workforce-governance/org-mirror/backfill-keys`): sets
  `org_registry_org_id` from the org-registry id resolved via `source_ref`. Gated
  behind `impilo.org-mirror.enabled` (no-op while off). **Never runs
  automatically.** Writes only the additive forward key — reversible until 2c.
- **Flagged read-preference resolver** (`OrgReadPreferenceResolver`) behind
  `impilo.org-mirror.read-preference` = `WGV` (default) | `ORG_REGISTRY`.
  `WGV` = strict no-op (legacy path). `ORG_REGISTRY` resolves via org-registry
  **only when a dual-key mapping exists**, else falls back to WGV. **No live read
  path is wired to it in phase-2a.**

## Stage 2b — the soak (runs on the preview VM, NOT in this change)

Operator sequence:

1. **Enable the mirror producer**
   `impilo.org-mirror.enabled=true` (env `ORG_MIRROR_ENABLED=true`) on
   workforce-governance-service. Confirm `base-url` / `mirror-path` /
   `inventory-path` point at the org-registry instance.
2. **Seed the mirror**
   `POST /internal/v1/workforce-governance/org-mirror/backfill` — sweeps all
   active `wgv_organisation` rows and pushes each to org-registry (idempotent on
   `source_ref`; safe to re-run). Also leave the live relay running so ongoing
   writes keep the mirror current.
3. **Reconcile**
   `GET /internal/v1/workforce-governance/org-mirror/reconcile` — read the report.
4. **Interpret / decide criterion-1**
   - **Proceed only when `completenessPct == 100.0` AND `driftRows` is empty,**
     **sustained across the agreed soak window** (poll reconcile repeatedly; a
     one-off green reading is not sufficient).
   - `missingSourceRefs` non-empty → re-run the backfill and investigate why those
     ids did not mirror (e.g. blank `legalName` rejected by the receiver).
   - `driftRows` non-empty → the mirror disagrees with WGV; investigate the
     producer/receiver mapping before proceeding. Do **not** cut over on drift.

## Stage 2c — the flip (LATER; explicitly NOT in this change)

Only after 2b sign-off (sustained 100% + zero drift):

1. **Backfill the dual-key columns**
   `POST /internal/v1/workforce-governance/org-mirror/backfill-keys` — populates
   `org_registry_org_id` on the 5 FK-carrier tables from the mirror inventory.
   (Still additive/reversible at this point — the authoritative FK is untouched.)
2. **Flip the read-preference**
   `impilo.org-mirror.read-preference=ORG_REGISTRY` and wire the live read paths
   to `OrgReadPreferenceResolver` (still falls back to WGV where unmapped).
3. **The irreversible steps** (require change control + sign-off):
   repoint the FK constraints onto `org_registry_org_id`, **freeze the WGV
   organisation write-path**, **disable the mirror producer**, and declare
   `organization-registry-service` the **sole SoR** for organizations.

> ⚠️ **Stage 2c is NOT included in this change.** Phase-2a delivers only the
> evidence tooling and dormant scaffolding required to make 2b runnable and 2c
> ready. No irreversible action is taken here, and default behaviour is
> byte-identical to before this change.

## Configuration reference (workforce-governance-service)

| Property | Default | Meaning |
|----------|---------|---------|
| `impilo.org-mirror.enabled` | `false` | Master switch for producer + backfills. Unchanged default. |
| `impilo.org-mirror.read-preference` | `WGV` | Dormant read resolver preference. `WGV` = no-op. |
| `impilo.org-mirror.base-url` | `http://localhost:8153` | org-registry base URL. |
| `impilo.org-mirror.mirror-path` | `/v1/internal/org-registry/mirror/wgv` | Producer POST target (W2-5). |
| `impilo.org-mirror.inventory-path` | `/internal/v1/org-registry/mirror/wgv/inventory` | Reconciliation GET source (2a). |
| `impilo.org-mirror.backfill-page-size` | `200` | Page size for backfill + reconcile sweeps. |
