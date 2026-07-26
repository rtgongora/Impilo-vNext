# IATG — Clinical-Core Completion Pack — Lease Record

Delivery-boundary record for the coordinator-routed **clinical-core completion pack**
(opened 2026-07-26, branch `claude/elegant-nash-d08292`), which pays down the
`DownstreamRouteContractTest` baseline: the vitals → observations remap, the
patient-document index, and the deletion of the six inpatient shadow lanes on the
PCT client.

## Migration-number leases

Measured before claiming (2026-07-26): highest `pct` migration on disk anywhere in the
repository is **V101** (`V101__medical_episode.sql`). Existing leases honoured:
Adult Medicine holds `pct` **V100–V129**; the emergency lane holds `pct` **V200–V239**;
trauma holds V035–V069 (historic); RMNP holds V058/V059/V061–V069 (historic).

| Service | Highest on disk at claim | This pack's lease | Used |
|---|---|---|---|
| `pct-service` | V101 | **V130–V134** | V130 `patient_documents` |

No other service gains a migration from this pack. The next free `pct` block for a
future lane starts at **V135**.

## Scope boundaries

- `services/pct-service`: additive only — `pct_patient_documents` (V130), the
  observation void endpoint. No change to existing tables or vocabularies; vitals
  writes reuse the LOINC codes the form-extraction seeds already record.
- `services/experience-bff`: `PctServiceClient` (vitals + records + shadow-lane
  sections), `VitalsController`, `MobileVitalsController`, `ClinicalDocumentsController`,
  `CitizenRecordsController`, `ClinicalDepthController` (NEWS2 residual),
  `MobileDischargeController` (residual), `VitalsObservationBridge` (new),
  `DownstreamRouteContractTest` baseline.
- Frontend: the discarded-`isError` repairs on the vitals/records consumers listed in
  the pack's audit.
- NO-TOUCH: `services/tshepo-service` (program-wide invariant), inpatient-service,
  document-service.
