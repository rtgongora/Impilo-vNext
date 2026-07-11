# Facility Completion Wave — regulatory lifecycle + operationalization (F-wave)

> **Wave window:** 2026-07-11 · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
> **PO directive:** close the operational dead-end completely — surface the already-built facility
> regulatory lifecycle (HPA registration → inspection → committee → certificate, practitioners in
> charge, practice types) and the operational side (site setup, queues, services, people).
> PO decisions: dedicated HPA personas; bulk default provisioning + wizard refinement; practice
> types start from existing fields, deeper catalogue to follow from the PO.

## Shipped

| # | Delivered | Proof |
| --- | --- | --- |
| F0 | **Facility ID seam healed** — canonical cross-service key = `tuso.facility.facility_uuid`. Seed facilities 1–5 canonicalized to their established `f1000000-…` UUIDs (seed 17, applied live); `facilityUuid` projected in facility detail/summary/public reads and the facility-mode context; new `GET /v1/internal/facilities/by-uid/{uuid}` (tuso + BFF); PCT queue overlay defaults to the facility's own UUID. | seeds 1–5 = f1000000-… live |
| F1 | **All 1,773 imported facilities operationalized** — governed `POST /v1/internal/facilities/operationalize-defaults`: default workspace (typed from facility type), OPD service point, derived capabilities and baseline readiness, all `derived=true`; per-facility transactions; paced resumable operator script. Live run: **processed 1,773, failed 0** (18 batches). | tuso.workspace 10→1,783; service_point 0→1,773 |
| F1 | **PCT queues materialised for every facility** — two dead-by-construction defects found and fixed on the way: (a) LEGACY_ONLY emit mode routed the queue-reconcile trigger to the `tuso.events` catch-all while PCT listens on `impilo.tuso.facility_queue_config`; (b) PCT's TusoIntegration defaulted to `localhost:8084` in-pod **and** sent no trust headers (tenant NPE in tuso) — queue materialisation had never once succeeded. | `pct.pct_queues`: 1,773 facilities SOURCE `TUSO_MATERIALISED` |
| F2 | **Setup wizard tells the truth** — QUEUES step renders the real derived queue definitions with one-click live materialisation; WORKFORCE step lists the facility's governed Vashandi assignments; remaining toggles honestly labelled "operational attestations". Departments gain edit/retire (administrative `VOLUNTARILY_CLOSED`; governed closure stays with the regulator). | wizard live at `/facility/[id]/setup` |
| F2 | **Capability + readiness curation** — new tuso write paths (create/update/retire capability; readiness upsert that replaces the derived baseline with an honest assessment), BFF passthroughs, and two new configuration-console tabs (Services offered, Infrastructure). Derived entries visibly badged until curated. | tuso tests 133/133 |
| F3 | **Regulatory lifecycle finally has a face** — `/registry/facility-lifecycle` is a real HPA console (dashboard truth, state-bucketed queues over the 1,778-facility registry, application intake) and `/registry/facility-lifecycle/[facilityId]` is the governed facility file: documents → submit → ready-for-inspection, checklist-templated inspections with recorded outcomes (auto-escalation honest), committee decisions issuing certificates under HPA authority, practitioner-in-charge, enforcement cases and the status-history chain. All driving the existing 1,354-line `FacilityRegulatoryService` — no engine changes. | pages live over 564-line hook layer |
| F3 | **HPA personas** — `HPA_REGISTRAR` + `HPA_INSPECTOR` realm roles and personas (`hpa.registrar` / `hpa.inspector`, seeded live into Keycloak), console gated by the new `REGULATORY_AUTHORITY` role group. | Keycloak reconcile: created=2 |
| F4 | **Golden journeys** — `facility-regulatory.journey.spec.ts` (registrar → application → inspector records pass → committee approval → ACTIVE certificate + REGISTERED_ACTIVE) and `facility-operations.journey.spec.ts` (wizard truth at an imported facility; nurse reaches Start Session at an imported site). Registered in `run-golden-journeys.sh`. | run results below |
| — | **Outbox hygiene hardening** — workspace/queue-reconcile/facility-unit emits now set `pod_id` + `idempotency_key` (the poison-row defect family that crashlooped tuso). 1,773 × 2 events drained with zero backlog. | `event_outbox` unpublished = 0 after bulk run |

## Verification

- tuso full suite green (incl. 5 new operationalization tests) · experience-bff full suite green ·
  UI vitest **1587/1587** + routes (698) green · `tsc --noEmit` clean.
- Live DB truth: `tuso.facility` 1,778 · active workspaces 1,783 · service points 1,773 ·
  `pct.pct_queues` 1,773 facilities `TUSO_MATERIALISED` · outbox drained.
- Targeted roll: tuso-service, pct-service, experience-bff, one-ui-shell (tags in deploy log).

## Honest remaining gaps

- **Practice-type catalogue** — v1 uses the existing fields (class/category/pathway/checklist
  templates). The PO will supply the deeper catalogue (types of practices and their rules); the
  intake form's class picker is deliberately minimal until then.
- **Inspection findings capture** is a single general checklist verdict in the UI (the engine
  supports itemised findings per checklist item — richer capture is a UI iteration).
- **Attestation steps** (workflows, OROS routing, Khuluma channels, Fundo) remain attestations —
  now labelled as such instead of masquerading as configuration.
- **Renewal/expiry automation** — certificates carry expiry dates and the dashboard counts
  renewals due; scheduled RENEWAL_DUE transitions are not yet automated.
- `TUSO_V11_EMIT_MODE=LEGACY_ONLY` containment still in force (v1.1 topic registry entry for the
  legitimacy family remains the open item); the legacy topic map now covers PCT's consumers.
