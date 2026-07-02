# Facility Master Import — Service-Level Smoke (VM / preview)

Proves the facility master absorption flow through the **running** services (TUSO + BFF/admin), not
only SQL and unit tests. Complements the DB-migration validation
([`facility-master-db-validation-report.md`](../runbooks/facility-master-db-validation-report.md)).

- Orchestration script: [`scripts/operator/smoke-facility-import-service.sh`](../../scripts/operator/smoke-facility-import-service.sh)
- Importer (called for stage/apply/reconcile): [`scripts/operator/import-facility-master-pack.sh`](../../scripts/operator/import-facility-master-pack.sh)
- Smoke fixture: [`scripts/operator/fixtures/smoke_facility_import.csv`](../../scripts/operator/fixtures/smoke_facility_import.csv)
- Fake-service tests (no stack): [`scripts/operator/test-smoke-facility-import-service.sh`](../../scripts/operator/test-smoke-facility-import-service.sh)

## Prerequisites

- **Running services on the VM/preview:** TUSO (`:8084`) reachable; for the BFF/admin checks, the
  experience-BFF (`:8160`) reachable with an **admin-role** bearer token. A Postgres the TUSO service
  is migrated against (V001–V015).
- The importer calls **TUSO directly** with operator trust headers (existing convention). The BFF admin
  routes are RBAC-protected and need `IMPILO_AUTH_TOKEN` with an admin role; without it the BFF checks
  are skipped with a warning (the TUSO flow still runs).

## Environment variables

```bash
export TUSO_BASE_URL=https://<vm>/tuso            # or http://localhost:8084
export EXPERIENCE_BFF_BASE_URL=https://<vm>/bff   # or http://localhost:8160
export IMPILO_AUTH_TOKEN=<admin-jwt>              # required for BFF admin checks
export X_TENANT_ID=moh-zw                          # tenant for trust context
```

CLI flags override env: `--base-url --bff-base-url --token --tenant --source --source-label
--smoke-fixture --run-id --full-stage --skip-ui-check`.

## 0. Verify the ready pack is present

The full dataset must exist at the repo-relative path with the expected row count:

```bash
test -f data/source/zimbabwe/master-health-facility/2024-07-23/clean_tuso_facility_import.csv
python3 - <<'PY'
import csv
p="data/source/zimbabwe/master-health-facility/2024-07-23/clean_tuso_facility_import.csv"
n=sum(1 for _ in csv.DictReader(open(p,encoding="utf-8-sig")))
print("rows=",n); assert n==1773, n
PY
```

The smoke script also enforces this before a `--full-stage` (fails if the file is missing or the row
count ≠ 1,773).

## 1. Run the fixture smoke (first proof — 7-row fixture)

```bash
scripts/operator/smoke-facility-import-service.sh \
  --base-url "$TUSO_BASE_URL" \
  --bff-base-url "$EXPERIENCE_BFF_BASE_URL" \
  --token "$IMPILO_AUTH_TOKEN"
```

What it does (all against the running stack):

1. `import-facility-master-pack.sh --stage-only` with the fixture → captures the **run id**.
2. Reads run / rows / review via API; asserts a missing-code row, a duplicate-name pair, and an
   acceptable-missing row are present.
3. Review mutations via API: **supply-code** + **approve** the corrected missing-code row; **approve** a
   clean row; **reject** one duplicate-name row; **resolve-distinct** (with reason) the other and approve
   it; approve one duplicate-code row and prove its twin **cannot** be approved while it shares the code
   (expects a conflict).
4. `--apply-approved` → imports only approved rows; verifies an imported facility has a `facility_uuid`
   distinct from its code and lifecycle `IMPORTED_PENDING_CONFIGURATION`.
5. `--reconcile` → confirms result facilities resolve + provenance/lifecycle.
6. BFF/admin route checks: run list/detail, rows, review, facility import-provenance, missing-field
   checklist.
7. Prints admin UI routes to verify manually.

## 2. Inspect the admin UI

Open and confirm the run + row detail render with real data:

```
/admin/facility-imports
/admin/facility-imports/<runId>
/admin/facility-imports/<runId>/review
```

Facility detail (`/facility/<id>`) for an imported facility must show the identity block (internal
uuid vs external code), source provenance, missing-field checklist, and lifecycle
`IMPORTED_PENDING_CONFIGURATION`.

## 3. Full 1,773-row stage-only (no auto-apply)

Once the fixture smoke passes, stage the real pack (this does **not** apply):

```bash
scripts/operator/smoke-facility-import-service.sh \
  --full-stage \
  --base-url "$TUSO_BASE_URL" \
  --source data/source/zimbabwe/master-health-facility/2024-07-23/clean_tuso_facility_import.csv \
  --source-label MASTER_HEALTH_FACILITY_2024_07_23
```

Verifies: ready pack = 1,773 rows, run created, staged row count = 1,773, acceptable-missing rows still
flagged, **no rows auto-imported**, and prints the admin UI route.

## 4. Apply approved rows (only after review)

Full apply is never automatic. After reviewing/approving rows in the admin UI (or via the review APIs):

```bash
scripts/operator/import-facility-master-pack.sh --run-id <runId> --apply-approved
scripts/operator/import-facility-master-pack.sh --run-id <runId> --reconcile
```

## Honest boundaries

- The smoke **only proves anything when pointed at a running TUSO/BFF stack.** In an environment without
  the stack it fails safely (non-2xx / unreachable → non-zero exit).
- In the repo/CI sandbox only the **fake-service tests** run (`test-smoke-facility-import-service.sh`,
  14 checks) — they validate orchestration/parsing/flow control, **not** real service behaviour.
- Do **not** record "service-level smoke passed" unless this script was run against the actual
  VM/preview stack and printed `PASS`.
- Do **not** record "full 1,773 rows staged/applied" unless `--full-stage` (stage) or `--apply-approved`
  (apply) actually ran against the VM DB.
- Downstream **TUSO → PCT materialisation** and broader facility lifecycle configuration are **not**
  covered by this smoke.
