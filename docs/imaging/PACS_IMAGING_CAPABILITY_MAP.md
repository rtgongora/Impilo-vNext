# PACS / DICOM / Imaging Integration — Repo-Grounded Capability Map (Wave 0)

**Workstream**: Cursor PACS/Imaging integration stream (Fable Seven Pipeline board — P6)
**Branch**: `cursor/e2e-pacs-imaging-integration` (base: `claude/web-session-anchor-nnnkf6` @ `d44bb6022`)
**Date**: 2026-07-04

Doctrine: Impilo vNext is the national clinical orchestration layer for imaging.
PACS/VNA remains the imaging archive; the DICOM gateway bridges machines on the
ground; the vNext DICOM viewer is a clinical viewing surface inside vNext
journeys, not the image source of truth.

Classification legend: **REAL** (real and wired) · **PARTIAL** (partially wired) ·
**DEFAULT-OFF** (real, honestly gated off) · **STUB** (honest seam / TODO) ·
**MISSING** · **W0-LEASED** (unsafe to modify in parallel) · **PHYSICAL**
(requires physical machine/gateway deployment).

## 1. Order → accession → worklist (OROS)

| Area | Where | Class |
|---|---|---|
| Imaging order lifecycle (draft→submit→imaging state machine) | `oros-service` `OrderSubmissionService`, `ImagingWorkflowService`, `ImagingWorkflow` (deny-by-default guard), outbox `IMAGING_STATE_*` | REAL |
| Accession number generation | `AccessionNumberService` (`ACC-{year}-{facility8}-{seq6}`), `V003` counter table, reserved at submit for IMAGING/LAB | REAL |
| Routing IMAGING → `RouteTarget.PACS` | `RoutingEngine.resolveTarget` (~line 226); adapter mode via `oros_capabilities` (facility → tenant default), default `INTERNAL` | REAL |
| Modality Worklist publish (REST DICOM-JSON + DIMSE dcm4che UPS N-CREATE) | `integration/dicom/` `MwlPublisherRouter` + `RestMwlPublisher` + `DimseMwlPublisher` + `MwlDatasetBuilder`; `oros.integration.dicom.mwl.mode: OFF` | DEFAULT-OFF (real when enabled) |
| Modality passed to MWL publish | `OrderController.scheduleOrder` calls `mwlPublisherRouter.publish(order, null)` — order-item modality (`oros_order_items.modality`) never joined | PARTIAL (closed in Wave 3 of this stream) |
| OROS → pacs-adapter HTTP dispatch | `AdapterDispatcher.dispatch` → `PacsAdapter.sendImagingOrder` posts to `/api/v1/imaging-orders` — **no caller in repo**, and no such endpoint exists in pacs-adapter. Event bridge (Kafka `oros.order.placed` → `PacsEventConsumer` pending-study placeholder) is the live path | STUB (uncalled); Kafka path REAL |
| Hybrid-routing reconcile queue | `oros_reconcile_queue`, `ReconcileController`, `ReconciliationService` | REAL (unused under default INTERNAL mode) |
| Stuck-order diagnostics reconciliation | `DiagnosticReconcileBucket`, `DiagnosticOperationsController` `/v1/reconcile/diagnostics/*` | REAL |

## 2. Archive bridge (pacs-adapter-service)

| Area | Where | Class |
|---|---|---|
| Study registry (register/correlate/forward, hierarchy, viewer sessions, access audit, report/order links, annotations) | `ImagingStudyService`, `ImagingStudyController` `/internal/v1/imaging-studies/**`, migrations `V001–V005` | REAL |
| Orthanc backend | `OrthancClient` (`impilo.pacs.backend.provider=ORTHANC`, default) | REAL |
| External PACS DICOMweb backend (QIDO/WADO) | `ExternalPacsClient` (`provider=EXTERNAL`), fail-closed without base URL | REAL (fail-closed) |
| Placeholder forwarding | `impilo.orthanc.allow-placeholder-forward: false` | DEFAULT-OFF |
| Unmatched studies / failed correlations / failed writebacks ops queues | `/internal/v1/imaging-studies/ops/*`; `PENDING_ORDER` placeholders from `PacsEventConsumer`; `imaging.study.unmatched` outbox | REAL |
| Manual import (CD/USB/workstation) source marking | `imaging_study.source_type` column exists (default `DICOM`) but not settable via `CreateImagingStudyRequest` | PARTIAL (closed in this stream) |
| **Per-facility imaging capability / deployment-mode model** | — | **MISSING** (closed in Wave 1 of this stream) |
| **Facility modality/machine registry (AE title, host, port, worklist/storage flags)** | AE titles exist only as global YAML (`oros.integration.dicom.mwl.*`) | **MISSING** (closed in Wave 2 of this stream) |

## 3. Result return & clinical record

| Area | Where | Class |
|---|---|---|
| FHIR DiagnosticReport to BUTANO | `oros ButanoIntegration.createDiagnosticReport` via `ReportService.createFinal` + amendments (best-effort) | REAL |
| FHIR ImagingStudy to BUTANO | `ButanoIntegration.createImagingStudy` gated by `oros.integration.fhir.imagingstudy-outbound.enabled` = `false`; NOT_LIVE surfaced by `IntegrationStatusService` | DEFAULT-OFF |
| PCT encounter ↔ study links | `pct ImagingLinkService` + `/v1/encounters/{id}/imaging-links` + BFF `TriageImagingLinkController`; `pct_imaging_links` (`V014`) | REAL |
| Radiology report authoring UI | `/diagnostics/reporting` (one-ui-shell) + OROS `ReportService` (preliminary/final/amend) | REAL |
| Critical results / urgent findings | OROS `CriticalEscalationService`, `/diagnostics/critical-queue` | REAL |

## 4. Experience / BFF / governance

| Area | Where | Class |
|---|---|---|
| Governed imaging BFF (`/internal/v1/imaging/**`) | `ImagingExperienceController` → `PacsServiceClient`; `ImagingGovernanceService` (Tshepo PDP flags + audit ingest) + `ImagingAccessPolicyService` (clinical role + patient–study binding) | REAL |
| Raw PACS DICOMweb proxy (`/internal/v1/pacs/**`) | `PacsController` — QIDO/WADO/STOW + preview proxy straight to Orthanc (parallel path, bypasses pacs-adapter registry) | REAL (noted risk: parallel ingest/view path) |
| Diagnostics BFF (`/internal/v1/diagnostics/**`) | `DiagnosticsExperienceController` incl. `POST /orders/{orderId}/viewer` (order→study→audited viewer session) | REAL |
| Viewer (3 engines: DICOMWEB_STACK canvas, OHIF iframe, DWV native) | `/ehr/[patientId]/imaging/viewer` + `resolveViewerEngine.ts` + governed launch-context/viewer-sessions | REAL (OHIF needs `NEXT_PUBLIC_OHIF_BASE_URL`; CORNERSTONE backend-listed, no frontend impl) |
| Viewer route ignores `?orderId=` | `EncounterImagingOrdersPanel` links with `?orderId=`, viewer page only honours `studyUid`/`governedStudyId` | PARTIAL (closed in Wave 5 of this stream) |
| Imaging ops dashboards | `/imaging/facility`, `/imaging/worklist`, `/admin/system-monitor` (useImagingOps*), `/operations/diagnostics-reconciliation` | REAL (facility page is worklist-only; no capability/PACS status — extended in Wave 9) |
| Mobile viewer | `PACSViewerScreen` preview-only; registry notes web handoff | PARTIAL (documented; not in this stream's scope) |

## 5. Billing / payments / adjacent

| Area | Where | Class |
|---|---|---|
| IMAGING order → COSTA bill line | `costa CostaEventConsumer` on `oros.order.placed` → `BillLineKind.SERVICE` (requires active COSTA encounter+bill) | REAL (generic order path; no imaging-specific tariff seed) |
| MusheX shortfall/payment | Downstream of COSTA; **double-bill hazard `mushex CostaEventConsumer.onBillFinalized` is RED-serialized (R3) — not touched by this stream** | OUT OF SCOPE (hazard preserved as-is) |
| Dura contrast/consumables ↔ imaging linkage | No imaging-specific consumable hook found | MISSING (deferred; documented seam) |
| Critical-result notifications | `notification-service OrosResultCriticalKafkaListener` consumes `oros.result.critical` (published by OROS `OutboxPublisher`), gated by `impilo.kafka.oros.enabled=false` | DEFAULT-OFF (real when enabled) |

## 6. Telemedicine / diagnostics review

| Area | Where | Class |
|---|---|---|
| Telemedicine session imaging viewer link | `/telemedicine/session/[sessionId]` uses W0 `AdaptiveSessionRoom`; page modified by W0 commits on the anchor | **W0-LEASED** — imaging panel deferred; handoff note in final report |
| Diagnostics review surfaces | `/diagnostics/reporting`, `/diagnostics/results-inbox` show imaging state but no direct viewer launch where a study is linked | PARTIAL (extended in Wave 8/9 where safe) |

## 7. Physical-deployment honesty

Machine → gateway → PACS ingestion (C-STORE receivers, store-and-forward
gateways, digitisation bridges) requires physical deployment and is **not**
claimed complete by this stream. What this stream delivers is the **contract
and configuration surface** for those deployments: the per-facility deployment
mode + modality registry so each site's true integration state is
representable, honest, and operationally visible. MWL publishing and FHIR
ImagingStudy outbound remain default-OFF.

## 8. Facility deployment modes (Wave 1 model)

`pacs.facility_imaging_capability.deployment_mode` values:

| Mode | Ground pattern |
|---|---|
| `PACS_VNA` | Central hospital with existing PACS/VNA (machine → PACS → DICOMweb bridge → vNext viewer) |
| `GATEWAY_STORE_FORWARD` | District: digital machines + local Impilo imaging gateway → central/regional PACS |
| `DIGITAL_NO_PACS` | Digital machines, no PACS, no gateway yet |
| `LEGACY_LIMITED_DICOM` | Old machines with limited DICOM (export/CD/USB → gateway import) |
| `MANUAL_IMPORT` | Manual import from CD/USB/workstation only |
| `DIGITISATION_BRIDGE` | Analogue/non-DICOM equipment via CR/digitizer/frame capture |
| `REFERRAL_ONLY` | No local imaging; orders referred to imaging-capable facility |
| `OFFLINE_SYNC` | Offline-first / delayed-sync imaging site |
| `NONE` | No imaging capability |

## 9. Ownership preserved (no collapse)

VITO patient identity · TUSO facility identity · VARAPI provider identity ·
TSHEPO policy/audit · PCT encounter · OROS imaging order + accession + MWL ·
ZIBO catalogue · PACS/VNA archive (native Orthanc or external) · pacs-adapter =
archive/gateway bridge + capability/modality registry · viewer = vNext surface ·
PCT+BUTANO reporting · COSTA billing · MusheX payments · Dura stock · Khuluma
notifications · Nompilo guidance.

## 10. Coordination notes

- Register slot `WS-P6-A` (Claude worker E, branch `fable/e2e-pacs-imaging`) is
  **not dispatched** and the branch does not exist; this stream covers the same
  gap (per-facility capability) with a broader wave plan. Flagged to Fable to
  avoid duplicate dispatch.
- Board §11 no-touch respected: no `routes.ts`/`app-registry.ts` edits (UI work
  extends existing registered imaging routes), no helm/compose, no Kafka topic
  renames, no W0 session-suite files, no shared migrations (all new migrations
  are service-local additive in `pacs-adapter-service`).
