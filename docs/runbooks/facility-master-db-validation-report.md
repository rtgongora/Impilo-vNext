# Facility Master Absorption — DB Migration Validation Report

Harness: [`scripts/operator/validate-facility-import-db.sh`](../../scripts/operator/validate-facility-import-db.sh)
Importer: [`scripts/operator/import-facility-master-pack.sh`](../../scripts/operator/import-facility-master-pack.sh)

## Package / provenance (as verified in this branch)

| Field | Value |
|---|---|
| Package (original ZIP) | `zim_facility_master_absorption_package_2024_07_23.zip` (already unpacked + committed; no ZIP in tree) |
| Source path | `data/source/zimbabwe/master-health-facility/2024-07-23/clean_tuso_facility_import.csv` |
| Source label | `MASTER_HEALTH_FACILITY_2024_07_23` |
| Clean CSV row count | **1,773** |
| Clean CSV SHA256 | `5ba73e383582be615197f8362816d933df9788c2ec8b76378df618f71ece409d` |
| Checksums file | `data/source/zimbabwe/master-health-facility/2024-07-23/SHA256SUMS` |
| Branch | `claude/web-session-anchor-nnnkf6` |
| Pack commit | `1a069360` (pack) · product-truth enforcement `67a05f86` |
| Validation commit | `4aa7fbe1` |

Row counts (data rows, header excluded) — all match the spec exactly:
clean 1,773 · acceptable-missing 194 · duplicate-code 81 · duplicate-name 47 · missing-code 124 ·
excluded/review 250.

## What was actually executed

- **Environment:** local Debian Postgres cluster `16/main`, **PostgreSQL 16.13**, started by the harness
  (`--manage-local-cluster`), throwaway DB `tuso_import_validation` (dropped afterwards).
- **Migrations:** all **15** TUSO Flyway migrations `V001..V015` applied in version order via `psql`
  with `ON_ERROR_STOP=1` — **all executed cleanly** on Postgres 16. (This exercises the migration DDL
  and the resulting schema; it applies the `.sql` files in Flyway version order — it does not run the
  Flyway runtime checksum machinery, and does not boot the TUSO Spring service.)
- **Schema assertions (all passed):** `tuso.facility`, `tuso.facility_identifier`,
  `tuso.facility_import_run`, `tuso.facility_import_row` exist; `facility.facility_uuid`,
  `facility_import_row.{raw_values, acceptable_missing, outcome, decision_status, review_history,
  duplicate_group_key}`, `facility_import_run.quality_report` present.
- **SQL-level workflow smoke (stage → supply-code → approve → apply) — all passed:**
  - duplicate-name row left **unresolved / not imported**;
  - missing-code row imported only after a **real** supplied code (no fabricated code);
  - **acceptable-missing flags remain visible** after import;
  - **raw source values preserved** through the workflow (`raw_values` JSONB);
  - approved rows created facilities as **`IMPORTED_PENDING_CONFIGURATION`** (0 marked operational);
  - facility code stored as an **external identifier** (`facility_identifier.NATIONAL_FACILITY_CODE`),
    internal `facility_uuid` populated and distinct from the code.

Result: **PASS** — migrations execute on Postgres 16 and the schema supports the review→apply workflow
with product-truth invariants intact.

## Honest boundaries (what this does NOT claim)

- **Not executed against the production/preview VM database** — this ran against a local ephemeral
  Postgres 16 cluster in the execution environment. Re-run on the VM/preview with:
  `PGHOST=… PGPORT=… PGUSER=… PGPASSWORD=… scripts/operator/validate-facility-import-db.sh`
  (external mode; omit `--manage-local-cluster`).
- **Service-level (HTTP) smoke not run here** — the staged→review→approve→apply cycle through the
  running TUSO service + BFF + admin UI still needs the service booted on the VM/preview (Kafka/Redis/
  Envoy/Keycloak). The importer script (`--stage-only`/`--validate`/`--apply-approved`/`--reconcile`)
  is the operator entry point for that; it was verified against a fake curl (16/16) but not against a
  live service here.
- **Full 1,773-row import not applied to any database** — only the SQL-level fixture rows were used for
  the workflow smoke. Staging the real 1,773 rows requires the running service; the importer builds the
  full 1,773-row payload correctly (verified) and POSTs it via file to avoid `ARG_MAX`.

## Remaining (per agreed order)

1. Service-level staged→review→approve→apply smoke on the VM/preview (running TUSO + BFF + admin UI).
2. Downstream TUSO → PCT queue materialisation (task #15).
3. Broader facility lifecycle configuration completion.
