# Service surfacing — traceability matrix

Experience UI (`ui/experience`) calls the Experience BFF under `/internal/v1/*` (Next rewrites to `NEXT_PUBLIC_BFF_URL`, default `http://localhost:8160`). Public registry-style APIs use `/api/v1/*` via `NEXT_PUBLIC_API_GATEWAY_URL` (Envoy `infra/envoy/envoy.yaml`, port `10000` locally).

| Capability | Backend service | API (BFF / gateway) | Frontend surface | Mobile surface | Events | Audit trail | Status | Remaining gap | Test coverage |
|------------|-----------------|---------------------|------------------|----------------|--------|-------------|--------|---------------|----------------|
| Patient journey / queue context (PCT-aligned) | PCT (`PctServiceClient`), queue flows | BFF: queue + encounters + referrals + telemedicine + admissions + lab hooks (see `MobileProviderExtendedController`, encounter/patient controllers) | `PatientJourneyContextPanel`, chart `/ehr/[patientId]`, encounter, discharge, queue copy | `MobileProviderExtendedController` `/internal/v1/mobile/provider/*` | Domain/outbox varies by service; UI uses React Query invalidation | BFF audit on governed routes (e.g. imaging); PCT-side depends on deployment | **Partial** — UI aggregates **real** BFF resources; not a single “PCT journey” DTO | Unified PCT timeline DTO; SLA timers; explicit handoff payload | Vitest: journey panel indirect via pages; no dedicated panel test |
| Laboratory orders & results (OROS) | OROS via BFF lab proxy | `/internal/v1/lab-orders` (`LabOrdersController`) | `/ehr/[patientId]/orders`, `/results`, summary counts, journey panel | `/internal/v1/mobile/provider/labs` | Order lifecycle events (service-dependent) | Ack/result POSTs audited when BFF configured | **Wired** | Procedure/imaging **order** parity with lab in one form | `orders/page.test.tsx`, results flows |
| Imaging / PACS (governed) | PACS adapter + Orthanc (behind BFF) | `/internal/v1/imaging/*` (`ImagingExperienceController`); viewer launch hooks | `/ehr/[patientId]/imaging`, `/imaging/viewer`, Orders & Results imaging card | Mobile extended `GET .../imaging/studies` | Study access | `IMAGING_STUDY_*` audit in controller | **Wired** | Encounter-linked study metadata write to Butano/SHR | `imaging/page.test.tsx` |
| Telemedicine (7-stage UX) | Telemedicine service via BFF | Telemedicine routes under BFF (sessions CRUD — grep `Telemedicine` in `experience-bff`) | Hub `/telemedicine`, session `/telemedicine/session/[id]`, consults tab, `TelemedicineWorkflowStrip` | Provider mobile where session APIs mirrored | Session status changes | Per-route audit per BFF policy | **UX complete**, **stage** from `status` + optional `stage` | Receiving/referring **facility** split queues in UI | Resolver unit test `telemedicine-workflow-stages.test.ts` |
| Referrals | Referral service via BFF | `/internal/v1/referrals` (and related) — see `useReferrals` | Chart, consults, summary, journey | Partial via mobile extended | Referral state | BFF + downstream | **Partial** | Attach imaging/lab results package UX | Consult/summary tests |
| Coverage / costing (COSTA) | COSTA | BFF `CostaServiceClient` + `/internal/v1/finance/costa-intel/*` (`useCostaIntel`), billing workspace | `ClinicalFinanceContextStrip`, `/coverage`, `/finance/workspace` | Limited | Billing lifecycle | Finance controllers | **Partial** | Automatic costing hooks from every clinical action | Finance hooks tests where present |
| Claims / MusheX console | MusheX / registry | `/internal/v1/registry/*` (council obligations, etc.) | Registry admin surfaces; finance links | Low | Registry | Audit | **Admin-weighted** | In-chart **wallet/remittance** without mock balances | Sparse |
| SHR / Butano timeline | Butano SHR | `/internal/v1/timeline` (`useTimeline`) + IPS bundles | `/ehr/[patientId]/timeline`, IPS link on summary | N/A | Timeline entries | When SHR returns events | **Data-dependent** | Empty timeline when SHR sparse — documented in UI | Timeline page tests if any |
| Rules | Rules engine (Zibo / policy) | Mixed: Zibo `/api/v1` via gateway; rules in BFF grep `rules` | Encounter forms, governance | Partial | Policy evaluation | Tshepo / service | **Fragmented** | Single “rules fired” panel in encounter | Varies |
| Notifications | Notification / outbox | BFF notification endpoints (grep `Notification`) | Shell / toasts / inbox patterns | Push (platform) | Outbox | Audit | **Partial** | Cross-cutting “notification centre” in clinical header | Varies |
| Identity / CPID (Vito) | Vito | Gateway `/api/v1/clients` | Patient registration, IPS | Citizen apps | — | Tshepo audit | **Wired** | — | — |
| Provider registry (Varapi) | Varapi | Gateway `/api/v1/providers` | Staff pickers, registry | — | — | — | **Wired** | — | — |
| Facility / workspace (Tuso) | Tuso | Gateway `/api/v1/facilities`, `/workspaces` | Facility store, scheduling | — | — | — | **Wired** | — | — |
| Terminology (Zibo) | Zibo | Gateway `/v1/artifacts` etc. | Forms, coding | — | — | — | **Wired** | — | — |

## Build / test record

Recorded **2026-04-12** on Windows, from `Impilo-vNext/ui/experience`:

| Command | Result |
|---------|--------|
| `npm run lint` | **Pass** (exit 0). Next/ESLint reports existing project **warnings** only — no errors. |
| `npm run type-check` | **Pass** (`tsc --noEmit`, exit 0). |
| `npm test` | **Pass** — Vitest **142** files, **399** tests. |
| `npm run build` | **Pass** — `next build` completed (standalone output). |

Re-run before merge if clinical routes or BFF contracts change.
