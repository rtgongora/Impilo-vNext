# Facility Intelligence Wave — "Facilities are surveillance nodes"

> **Wave window:** 2026-07-10 · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl` · **HEAD:** `cea51f333`
> **PO directive:** facilities must not be passive lists — searchable, on maps, contactable, responsive;
> citizens see facilities near them or by service; catchment areas and referral pathways; events in a
> facility's vicinity must reach it. Plus three named follow-ups from the Tuso absorption.

## Shipped

| # | Delivered | Proof |
| --- | --- | --- |
| T1 | **All national sites on the maps** — 2,022-location Ndila seed synced: 1,840 created, 33 updated, 149 honestly queued for geocode review (the coordinate-less rows from the quality report). Unblocked by fixing **six dead-by-construction jsonb mappings** in ndila entities (String→jsonb, SQLState 42804) that silently broke location sync, tracking metadata, geofence geometry/rules and catchment-area geometry. | `ndila_locations` = 1,840 live |
| T2 | **Bulk-approve for import packs** — `POST /runs/{runId}/approve-all` with the exact per-row guards; ineligible rows counted and reported, never force-approved. | tuso tests 128/128 (incl. new bulk test) |
| T3 | **Search-first workplace selection** — the hub queries the registry (debounced, server-side) instead of listing a page; honest no-match copy. | UI gates green |
| T4 | **Citizen facility discovery** — `/discover/facilities`: "Use my location" (geolocation with honest fallback), nearby facilities as human cards (kind + distance) from the Ndila spatial index, service-type filter (hospitals/clinics/RHCs/pharmacies/labs) over the 1,778-facility registry, nearby→registry hand-off. | live after roll |
| T5 | **Referral pathways** — 1,706 REFERS_TO relationships derived structurally over the absorbed registry (PRIMARY → same-district SECONDARY → provincial TERTIARY → national apex; derivation idempotent, manual curation never overwritten; `scripts/seed/16`). `GET /facilities/{id}/referral-pathway` walks the chain through tuso + BFF. | spot-checked live (clinics → district hospitals) |
| T6 | **Facilities as surveillance nodes** — an incident escalated with coordinates resolves its nearest registered facilities via Ndila and emits `daidzai.facility.catchment_alert` per facility (incident ref, triage, distance) on the event stream; degraded location service never blocks emergency intake; escalating actor's bearer propagated. | daidzai tests 19/19 |

## Verification

UI **1587/1587** + routes + launcher dead-ends clean · tuso **128/128** · daidzai **19/19** · ndila **38/38**.
Targeted roll at `cea51f333`: tuso, daidzai, experience-bff, one-ui-shell.

## Honest remaining gaps

- **Catchment geometry**: alerts currently use nearest-N by distance; `ndila` has a catchment-area
  entity (geometry now writable after the jsonb fix) — polygon-true catchment membership is the
  upgrade path.
- **Alert consumption**: `daidzai.facility.catchment_alert` is on the outbox stream; a
  notification-service consumer + facility control-tower "catchment alerts" panel are the next leg.
- **Contact on discovery cards**: contacts (1,663) live in `tuso.facility_contact`; the tuso search
  summary doesn't project them yet — cards link into detail instead of offering `tel:` inline.
- **149 geocode-review locations** await coordinates before appearing on maps.
- Referral pathways are structural derivations — clinical routing (service-specific pathways) is a
  policy layer on top, not yet modelled.
