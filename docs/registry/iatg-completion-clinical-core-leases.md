# IATG — Clinical-Core Completion Pack — Lease Record

Delivery-boundary record for the coordinator-routed **clinical-core completion pack**
(opened 2026-07-26, branch `claude/elegant-nash-d08292`), which pays down the
`DownstreamRouteContractTest` baseline: the vitals → observations remap and the
discarded-`isError` repairs on the vitals/records consumers.

## Migration-number leases

**This pack ships no migration.** The V130–V134 block briefly claimed here was
released the same day: while this pack was building a `pct_patient_documents` index
(V130) for the dead `/v1/records` vertical, the records completion landed on the
canonical branch first as `V102__clinical_documents.sql` (pct
`ClinicalDocumentController`, serving `/v1/records` + `/v1/patient/{cpid}/records`
directly). Under the no-duplicate-SoR rule the landed capability won; this pack's
V130 build was dropped unmerged and its citizen-safety improvement (subject-bound
single-document read) was layered onto the V102 surface instead.

For a future lane: the next free `pct` block after the existing leases
(Adult Medicine V100–V129 · emergency V200–V239 · IMAM V400s) starts at **V130**,
which this pack no longer holds.

## Scope actually shipped

- `services/pct-service` (additive, no migration): observation void endpoint
  (`POST /v1/observations/{id}/void`, ENTERED_IN_ERROR + outbox event); optional
  `subject_cpid` binding on `GET /v1/records/{documentId}` so the citizen path can
  hold a person to their own record without disclosing what exists.
- `services/experience-bff`: vitals lane remapped onto `/v1/observations` via
  `VitalsObservationBridge` (LOINC codes, both wire dialects, transactional mobile
  batch, void-backed delete); records controllers composed onto the V102 lane
  (JSON:API rows for the shell, flat `MedicalRecord` rows + real `meta.page` for the
  citizen app, subject-bound detail read, signed-URL enrichment); route-contract
  baseline entries for `/v1/vitals` cleared.
- Frontend: discarded-`isError` repairs on the vitals/records consumers named in the
  pack's audit (EHR vitals page, patient summary, encounter CDS sources, telemedicine
  intake, mobile VitalsPanel and VitalsMonitorScreen — whose BP card ids now match the
  emitted vocabulary).
- NO-TOUCH honoured: `services/tshepo-service`, inpatient-service, document-service.
