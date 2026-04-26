# Service surfacing audit (Experience layer)

This document complements `service-surfacing-traceability-matrix.md` with a per-capability narrative. Paths are relative to the `Impilo-vNext` repository root.

## How routing works

- **Browser → Experience BFF:** `ui/experience/next.config.mjs` rewrites `/internal/:path*` → `NEXT_PUBLIC_BFF_URL` (default `http://localhost:8160`).
- **Browser → public mesh APIs:** `/api/:path*` → `NEXT_PUBLIC_API_GATEWAY_URL` (local Envoy `infra/envoy/envoy.yaml` on port `10000`).
- **Envoy:** Exposes Tshepo, Vito, Varapi, Tuso, Zibo, Msika, etc.; does **not** terminate every BFF path — clinical “One UI” data is primarily **BFF**-backed.

---

### PCT (Patient Care / journey orchestration)

| Dimension | Notes |
|-----------|--------|
| Backend | Java `PctServiceClient` used from multiple BFF controllers (e.g. conditions, structured history, mobile queue `callNext`). |
| API | No single public “PCT journey JSON” consumed by the SPA; instead **composite** `/internal/v1/*` resources (queue, encounters, admissions, referrals). |
| Envoy | PCT typically service-to-service from BFF; not always on public Envoy prefix. |
| Frontend | `PatientJourneyContextPanel` (`ui/experience/src/components/clinical/PatientJourneyContextPanel.tsx`); queue/triage copy. |
| Patient chart | Chart overview, summary, timeline coordination. |
| Encounter | Compact panel on encounter page. |
| Mobile | `MobileProviderExtendedController` queue endpoints. |
| Events | Depends on PCT/outbox deployment. |
| Audit | Follows downstream PCT + BFF logging. |
| Gaps | Single journey DTO, SLA timers, explicit handoff tokens in UI. |

### OROS (Orders & results)

| Dimension | Notes |
|-----------|--------|
| Backend | BFF `LabOrdersController` proxies OROS. |
| API | `/internal/v1/lab-orders` CRUD, collect, result, acknowledge, cancel. |
| Envoy | Internal to BFF. |
| Frontend | `/ehr/[patientId]/orders`, `/results`; summary links; journey “OROS labs” row. |
| Mobile | `/internal/v1/mobile/provider/labs`. |
| Events | Invalidate `lab-orders` query keys on mutation. |
| Audit | BFF + OROS policies. |
| Gaps | Unified ordering UX for **procedures/imaging orders** vs labs. |

### PACS / DICOM / Orthanc

| Dimension | Notes |
|-----------|--------|
| Backend | `ImagingExperienceController` + governance policy services. |
| API | `/internal/v1/imaging/studies`, study get/search, viewer launch. |
| Envoy | Internal. |
| Frontend | `/ehr/[patientId]/imaging`, viewer route; Orders & Results card. |
| Mobile | Studies list on extended mobile controller. |
| Events | Study list refresh via React Query. |
| Audit | `IMAGING_STUDY_*` actions in BFF. |
| Gaps | Persist **encounter link** + Butano/SHR metadata write from UI/BFF. |

### Telemedicine

| Dimension | Notes |
|-----------|--------|
| Backend | Telemedicine microservice behind BFF. |
| API | Session resources under `/internal/v1/...` (see `useTelemedicine` hooks). |
| Envoy | May be BFF-only. |
| Frontend | Hub, session detail, consults tab; `TelemedicineWorkflowStrip` + `telemedicine-workflow-stages.ts`. |
| Mobile | Feasible where session APIs exposed to mobile BFF. |
| Events | Session status. |
| Audit | Session access logging per environment. |
| Gaps | Dedicated **receiving vs referring** facility queues in UI. |

### Referrals

| Dimension | Notes |
|-----------|--------|
| Backend | Referral service via BFF. |
| API | `/internal/v1/referrals` (patient scoped). |
| Frontend | Consults & referrals, summary, journey. |
| Gaps | Package attachments for imaging/lab **results** from governed rails. |

### COSTA

| Dimension | Notes |
|-----------|--------|
| Backend | `CostaServiceClient` — bills, payments, estimates. |
| API | `/costa/v1/...` service-side; BFF wraps finance intel `/internal/v1/finance/costa-intel/*`. |
| Frontend | `ClinicalFinanceContextStrip`, coverage/finance routes. |
| Gaps | Automatic cost hooks from **each** clinical action; chart-level bill status without mock data. |

### MusheX

| Dimension | Notes |
|-----------|--------|
| Backend | Linked registry/council flows (e.g. obligations). |
| API | `/internal/v1/registry/...` council paths. |
| Frontend | Admin/registry; finance role deep links. |
| Gaps | Clinician-safe **summary** only (no ledger noise on chart). |

### Butano / SHR

| Dimension | Notes |
|-----------|--------|
| Backend | SHR timeline + IPS bundles. |
| API | `/internal/v1/timeline`; IPS via BFF. |
| Frontend | Timeline page; IPS on summary. |
| Gaps | Write-back of imaging metadata; richer timeline when SHR empty. |

### Vito / Varapi / Tuso / Zibo

Registry and terminology services: primarily **gateway** (`/api/v1/*`, `/v1/artifacts`) for pickers and facility context; surfacing is indirect (stores, forms) rather than standalone “service pages” in clinical flows.

### Rules

Policy and terminology mix: Zibo artifacts + Tshepo policies; encounter-specific rule **visibility** is not centralized in One UI.

### Notifications

Inbox/toast patterns depend on shell configuration; no single documented clinical notification hub in this audit pass.

---

## Acceptance cross-check

- PCT-aligned context: **aggregated real data** in `PatientJourneyContextPanel` (not fabricated journey JSON).
- OROS: **Orders & Results** route and results/ack paths.
- PACS: **Imaging** route + governed study list (no raw PACS chrome).
- Telemedicine: **Seven** named stages in UI resolver + strip.
- COSTA/MusheX: **Light** finance strip + deep links for authorised roles; no fake balances.
- Butano/SHR: timeline + IPS; empty states when API returns nothing.
