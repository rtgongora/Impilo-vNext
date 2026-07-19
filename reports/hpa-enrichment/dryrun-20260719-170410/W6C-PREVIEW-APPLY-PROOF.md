# HPA enrichment — apply against the LIVE preview DB (W6c)

Proves the safe match-or-create apply + idempotency + rollback against the **actual
`impilo-full-preview` tuso database** (not a copy), with a real backup and zero
lasting impact on shared infrastructure.

## Step 25 — snapshot (real backup)
`pg_dump` of `tuso.facility` + `tuso.facility_identifier` from the live preview →
`preview-tuso-facility-snapshot-20260719-230925.sql.gz` (committed alongside this report).

## Step 26–27 — apply + idempotency + rollback (transactional, against the live DB)
Run as a single `BEGIN … ROLLBACK` transaction on the preview postgres, so uncommitted
rows were never visible to other sessions and nothing persists:

| stage | facilities |
|---|---|
| preview before | **1,776** |
| reconciliation | NEW_REGULATED_ESTABLISHMENT **5,488** · POSSIBLE_EXISTING_REVIEW **687** · CONFIRMED_EXISTING_ENRICH **152** (total 6,327) |
| after apply (create NEW) | **7,264** (+5,488) |
| after 2nd apply (idempotency) | **7,264** — 0 new |
| after ROLLBACK | **1,776** |
| preview after (fresh connection) | **1,776**, 0 `HPA-*` rows left |

The reconciliation counts are **identical to the runtime-proof rig** (W6b) — confirming
the rig faithfully mirrored the live estate.

## Why rolled back rather than persisted (honest)
The preview `tuso` service runs a **stale jar** (predates V036 + the HpaEnrichmentImportService),
and the citizen HPA-disclosure stack (tuso public-profile fields, BFF, shell) is **not deployed**
there. Persisting 5,488 regulator-listed facilities into the shared preview would surface them
through the stale UI **without the honest "listed as of … / not confirmed open" disclosure** — the
exact "implies operational" harm the doctrine forbids — and would mutate shared infra other sessions
use. So the apply was proven end-to-end against the live DB and rolled back, leaving preview pristine.

## To persist (runbook, deploy-gated)
Coordinated redeploy of tuso (V036 + importer) + experience-bff + one-ui-shell, then:
`POST /v1/internal/facilities/hpa-import/apply` with the feed path (or the transactional
SQL above without the ROLLBACK). Rollback remains delete-by-`hpa_import_batch` (never a
canonical hard delete). The snapshot above is the pre-apply backup.
