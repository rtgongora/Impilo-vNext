# Service surfacing — traceability matrix

Experience UI (`ui/experience`) calls the Experience BFF under `/internal/v1/*` (Next rewrites to `NEXT_PUBLIC_BFF_URL`, default `http://localhost:8160`). Public registry-style APIs use `/api/v1/*` via `NEXT_PUBLIC_API_GATEWAY_URL` (Envoy `infra/envoy/envoy.yaml`, port `10000` locally).

| Capability | Backend service | API (BFF / gateway) | Frontend surface | Mobile surface | Events | Audit trail | Status | Remaining gap | Test coverage |
|------------|-----------------|---------------------|------------------|----------------|--------|-------------|--------|---------------|----------------|
| Patient journey / queue context (PCT-aligned) | PCT (`PctServiceClient`), queue flows | BFF: queue + encounters + referrals + telemedicine + admissions + lab hooks (see `MobileProviderExtendedController`, encounter/patient controllers) | `PatientJourneyContextPanel`, chart `/ehr/[patientId]`, encounter, discharge, queue copy | `MobileProviderExtendedController` `/internal/v1/mobile/provider/*` | Domain/outbox varies by service; UI uses React Query invalidation | BFF audit on governed routes (e.g. imaging); PCT-side depends on deployment | **Partial** — UI aggregates **real** BFF resources; not a single “PCT journey” DTO | Unified PCT timeline DTO; SLA timers; explicit handoff payload | Vitest: journey panel indirect via pages; no dedicated panel test |
| Laboratory orders & results (OROS) | OROS via BFF lab proxy | `/internal/v1/lab-orders` (`LabOrdersController`) | `/ehr/[patientId]/orders`, `/results`, summary counts, journey panel | `/internal/v1/mobile/provider/labs` | Order lifecycle events (service-dependent) | Ack/result POSTs audited when BFF configured | **Wired** | Procedure/imaging **order** parity with lab in one form | `orders/page.test.tsx`, results flows |
| Imaging / PACS (governed) | PACS adapter + Orthanc (behind BFF) | `/internal/v1/imaging/*` (`ImagingExperienceController`); viewer launch hooks | `/ehr/[patientId]/imaging`, `/imaging/viewer`, Orders & Results imaging card | Mobile extended `GET .../imaging/studies` | Study access | `IMAGING_STUDY_*` audit in controller | **Wired** | Encounter-linked study metadata write to Butano/SHR | `imaging/page.test.tsx` |
| Telemedicine (7-stage UX) | Telemedicine service via BFF | `/internal/v1/mobile/provider/telemedicine/*` (`MobileTelemedicineController`) | Hub `/telemedicine` (+ facility lens + assistant strip), session `/telemedicine/session/[id]`, consults tab, `TelemedicineWorkflowStrip` | Same BFF prefix family | Session status changes | Per-route audit per BFF policy | **UX complete**; **facility lens** for receiving site vs remote host | Explicit `referring_facility_id` on session DTO when available | Resolver + `telemedicine-facility-lens` tests |
| Referrals | Referral service via BFF | `/internal/v1/referrals` (and related) — see `useReferrals` | Chart, consults, summary, journey | Partial via mobile extended | Referral state | BFF + downstream | **Partial** | Attach imaging/lab results package UX | Consult/summary tests |
| Coverage / costing (COSTA) | COSTA | BFF `CostaServiceClient` + `/internal/v1/finance/costa-intel/*` (`useCostaIntel`), billing workspace | `ClinicalFinanceContextStrip`, `/coverage`, `/finance/workspace` | Limited | Billing lifecycle | Finance controllers | **Partial** | Automatic costing hooks from every clinical action | Finance hooks tests where present |
| Claims / MusheX console | MusheX / registry | `/internal/v1/registry/*` (council obligations, etc.) | Registry admin surfaces; finance links | Low | Registry | Audit | **Admin-weighted** | In-chart **wallet/remittance** without mock balances | Sparse |
| SHR / Butano timeline | Butano SHR | `/internal/v1/timeline` (`useTimeline`) + IPS bundles | `/ehr/[patientId]/timeline`, IPS link on summary | N/A | Timeline entries | When SHR returns events | **Data-dependent** | Empty timeline when SHR sparse — documented in UI | Timeline page tests if any |
| Rules | Rules engine (Zibo / policy) | Mixed: Zibo `/api/v1` via gateway; rules in BFF grep `rules` | Encounter forms, governance | Partial | Policy evaluation | Tshepo / service | **Fragmented** | Single “rules fired” panel in encounter | Varies |
| Notifications | Notification / outbox + assistant | BFF `/internal/v1/assistant/notifications`; comms `useNotifications` | Shell tray; **Telemedicine Hub** assistant strip; `NotificationsCommsHub` | Mobile notices / messaging controllers | Outbox | Audit | **Partial** | Unified clinical header centre across all chart routes | Assistant strip on hub; tray elsewhere |
| Identity / CPID (Vito) | Vito | Gateway `/api/v1/clients` | Patient registration, IPS | Citizen apps | — | Tshepo audit | **Wired** | — | — |
| Provider registry (Varapi) | Varapi | Gateway `/api/v1/providers` | Staff pickers, registry | — | — | — | **Wired** | — | — |
| Facility / workspace (Tuso) | Tuso | Gateway `/api/v1/facilities`, `/workspaces` | Facility store, scheduling | — | — | — | **Wired** | — | — |
| Terminology (Zibo) | Zibo | Gateway `/v1/artifacts` etc. | Forms, coding | — | — | — | **Wired** | — | — |

---

## Ingress: Experience vs Envoy (BFF path coverage)

The One UI **normally bypasses Envoy for BFF** in local dev: `ui/experience/next.config.mjs` rewrites `/internal/:path*` directly to `NEXT_PUBLIC_BFF_URL` (default `http://localhost:8160`). The browser never hits Envoy for those calls unless you deliberately point the SPA at the gateway.

| Ingress | Host (typical) | Path pattern | Target | Notes |
|---------|----------------|--------------|--------|-------|
| Next.js rewrite | `localhost:3099` (dev) | `/internal/v1/*` | Experience BFF `:8160` | Primary clinical data plane for the SPA. |
| Next.js rewrite | same | `/api/v1/*` | `NEXT_PUBLIC_API_GATEWAY_URL` (Envoy `:10000`) | Tshepo, Vito, Varapi, Tuso, PCT public work APIs, etc. |
| Envoy (compose runtime) | `:10000` | `/internal/v1/*` | `experience_bff` cluster → BFF | See `infra/envoy/envoy-runtime.yaml` — **same BFF**, different hop (e.g. mobile or gateway-only clients). |
| Envoy (compose runtime) | `:10000` | `/bff/*` | BFF with `prefix_rewrite: "/"` | Legacy/alternate BFF prefix. |
| Envoy (`envoy.yaml` static dev) | `:10000` | `/internal/v1/*` | **Not defined** in the checked file | Base `infra/envoy/envoy.yaml` focuses on `/api/v1/*` mesh; prefer `envoy-runtime.yaml` for full BFF routing. |

### Envoy → BFF row (runtime file)

Source: `infra/envoy/envoy-runtime.yaml` (listener `public_listener`, route `impilo_services`).

| Match prefix | Cluster | Rewrite | Upstream purpose |
|--------------|---------|---------|------------------|
| `/internal/v1/` | `experience_bff` | (none) | All Experience BFF JSON:API-style internal routes. |
| `/bff/` | `experience_bff` | strip `/bff` → `/` | Alternate entry; BFF serves from root after rewrite. |
| `/external/v1/` | `tshepo_service` | — | External auth / policy surface (not BFF). |

### Envoy → domain services (subset, same runtime file)

| Match prefix | Cluster | Notes |
|--------------|---------|-------|
| `/api/v1/work`, `/api/v1/encounters`, `/api/v1/queues` | `pct_service` | PCT public API (BFF also calls PCT server-side). |
| `/v1/orders` | `oros_service` | OROS (BFF lab proxy is still the preferred path from SPA). |
| `/api/v1/pharmacy`, `/api/v1/prescriptions` | `pharmacy_service` | |
| `/api/v1/payments`, `/api/v1/refunds` | `mushex_service` | MusheX |
| `/api/v1/bills`, `/api/v1/tariffs` | `costa_service` | COSTA |
| `/api/v1/wards`, `/api/v1/beds`, `/api/v1/admissions` | `ubomi_service` | Inpatient |
| `/api/v1/clients`, `/api/v1/providers`, `/api/v1/facilities`, … | registry clusters | Vito / Varapi / Tuso per route block. |
| `/v1/artifacts`, `/v1/packs` | `zibo_service` | Zibo |

---

## BFF `/internal/v1/*` prefix catalog (controller roots)

Grouped by first path segment after `/internal/v1/`. Controllers live under `services/experience-bff/.../controller/` (and `intake/`). This is the **authoritative surface** for “what the SPA can call without going through Envoy”.

| Prefix | Example controller | Typical SPA / mobile consumer |
|--------|----------------------|-------------------------------|
| `/internal/v1/patients` | `PatientController` | Registration, chart identity |
| `/internal/v1/encounters` | `EncounterController` | Encounter CRUD |
| `/internal/v1/queue` | `QueueController` | Facility queue |
| `/internal/v1/referrals` | `ReferralsController` | Referrals tab |
| `/internal/v1/lab-orders` | `LabOrdersController` | Orders & results |
| `/internal/v1/imaging` | `ImagingExperienceController` | Governed imaging |
| `/internal/v1/pacs` | `PacsController` | Legacy/direct PACS helpers |
| `/internal/v1/timeline` | `ClinicalTimelineController` | Chart timeline (PCT-backed) |
| `/internal/v1/conditions` | `ConditionsController` | Chart conditions |
| `/internal/v1/ehr` | `StructuredHistoryController` | Structured history |
| `/internal/v1/finance/costa-intel` | `CostaIntelBffController` | COSTA intel |
| `/internal/v1/finance/mushex-platform` | `FinanceMushexPlatformController` | MusheX platform |
| `/internal/v1/finance/billing-workspace` | `FinanceBillingWorkspaceController` | Billing workspace |
| `/internal/v1/registry` | `RegistryController`, `RegistryGeoLocalityController` | Registry / MusheX-adjacent |
| `/internal/v1/registry/zibo` | `ZiboRegistryProxyController` | Zibo via BFF |
| `/internal/v1/registry/coverage` | `CoverageRegistrationPreviewController` | Coverage preview |
| `/internal/v1/search` | `SearchController` | Search |
| `/internal/v1/assistant` | `AssistantNotificationsController` | `GET .../notifications` |
| `/internal/v1/mobile/provider` | `MobileProviderExtendedController`, `MobileTelemedicineController`, `MobileLabController`, … | Mobile provider parity |
| `/internal/v1/mobile/provider/telemedicine` | `MobileTelemedicineController` | Telemedicine sessions (also used by One UI hooks) |
| `/internal/v1/mobile/citizen/*` | Multiple `Citizen*` controllers | Citizen app |
| `/internal/v1/shell/*` | `ShellFileCatalogController`, `ShellWorkspaceStateController` | Shell |
| `/internal/v1/inventory` | `InventoryController` | Stock |
| … | (see repo grep `@RequestMapping("/internal/v1`)`) | Additional admin, reports, marketplace, trust, etc. |

**Full appendix:** class-level roots are catalogued in [`bff-internal-v1-controller-roots.md`](./bff-internal-v1-controller-roots.md) (regenerate when adding controllers).

---

## Mobile parity (subset)

| Desktop / One UI hook | BFF path | Mobile provider analogue |
|-------------------------|----------|---------------------------|
| `useTelemedicineSessions` | `/internal/v1/mobile/provider/telemedicine/sessions` | Same controller family |
| `useLabOrders` (EHR) | `/internal/v1/lab-orders` | `/internal/v1/mobile/provider/labs` |
| Lab results | `/internal/v1/lab-orders` / results | `/internal/v1/mobile/provider/labs/results` |
| Queue (extended) | `/internal/v1/queue` (web) | `/internal/v1/mobile/provider/queue` (extended controller) |
| Referrals | `/internal/v1/referrals` | `/internal/v1/mobile/provider/referrals` |

**Gap:** Not every web hook has a documented mobile twin; extended controller is a partial mirror.

---

## Butano / SHR write (BFF contract stub)

`ButanoServiceClient` remains **GET**-only for summaries. **`POST /internal/v1/clinical/shr-artifacts`** is implemented on the BFF as `ClinicalShrArtifactController`: it validates `patient_id` + `artifact_type`, emits a structured **`SHR_ARTIFACT_REQUEST`** log line, and returns **`501 NOT_IMPLEMENTED`** with JSON `{ code, message, correlation_id }` until Butano exposes ingest.

**Allowed `artifact_type` values:** `IMAGING_STUDY`, `REFERRAL_PACKAGE`, `LAB_RESULT_PACKAGE`, `TELECONSULT_SUMMARY`.

**Experience:** Imaging page exposes “Log SHR linkage intent (selected study)” which calls this endpoint and surfaces the **real** 501 message (no fake success).

---

## Rules vs notifications (routing clarity)

| Concern | Primary API | Routed by | UI today |
|---------|-------------|-------------|----------|
| ABAC / policy | Tshepo `/api/v1/policies` | Envoy → `tshepo_service` | Admin policies page; enforcement server-side |
| Assistant / nudges | `/internal/v1/assistant/notifications` | BFF direct or Envoy `/internal/v1` | Telemedicine hub strip; `ShellNotificationTray` / `useAssistantNotifications` |
| Operational comms | `/internal/v1/notifications` (if enabled for tenant) | BFF | `NotificationsCommsHub` |

## Build / test record

Recorded **2026-04-12** on Windows, from `Impilo-vNext/ui/experience`:

| Command | Result |
|---------|--------|
| `npm run lint` | **Pass** (exit 0). Next/ESLint reports existing project **warnings** only — no errors. |
| `npm run type-check` | **Pass** (`tsc --noEmit`, exit 0). |
| `npm test` | **Pass** — Vitest **142** files, **399** tests. |
| `npm run build` | **Pass** — `next build` completed (standalone output). |

Re-run before merge if clinical routes or BFF contracts change.
