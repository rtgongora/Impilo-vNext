# Service Wiring Matrix

Legend: `Complete`, `Partial`, `Stub/mock`, `Broken`, `Missing`, `N/A`

| Service / Feature | Backend | Persistence | Migrations | API | Gateway/BFF | Trust headers/context | Web UI | Mobile UI | Tests/smoke | Observability | Gap |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Tshepo trust cluster | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Cross-service enforcement consistency |
| Vito client registry | Complete | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | End-to-end UX parity |
| Varapi provider registry | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Deeper provider workflows |
| Tuso facility registry | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Facility context propagation audit |
| Butano SHR | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Additional clinical journey surfacing |
| Ubomi CRVS | Complete | Complete | Complete | Complete | Partial | Partial | Partial | N/A | Partial | Partial | UI discoverability |
| Zibo terminology | Complete | Complete | Complete | Complete | Partial | Partial | Complete | N/A | Partial | Partial | Workflow-level integration depth |
| Msika registry/core | Complete | Complete | Complete | Complete | Complete | Partial | Complete | Partial | Partial | Partial | Mobile parity breadth |
| Msika Flow orchestration | Complete | Complete | Complete | Complete | Complete | Partial | Complete | Partial | Partial | Partial | Deep operator telemetry |
| MusheX | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Reconciliation UX hardening |
| Costa | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Costing journey completeness |
| Fundo/Learning | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Mobile learner parity |
| Nompilo/LLM orchestration | Complete | Partial | Partial | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Provider abstraction hardening |
| Comms Hub/channels | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Delivery status UX surfaces |
| Telehealth (pct + bff flows) | Complete | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Session status and failover UX |
| Public health intelligence | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | End-user role scoping polish |
| Ndila geospatial | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Operational map dashboards |
| Nhume dispatch/delivery | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Full dispatch lifecycle UI |
| Integration Hub/service | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Adapter registry UX and control plane |
| Community/social timeline | Complete | Complete | Complete | Complete | Complete | Partial | Complete | Complete | Partial | Partial | Moderation governance depth |
| Experience BFF | Complete | N/A | N/A | Complete | N/A | Partial | N/A | N/A | Partial | Partial | Header doctrine normalization |
| Provider app | N/A | N/A | N/A | Complete | Complete | Partial | N/A | Complete | Complete | Partial | Runtime telemetry |
| Citizen app | N/A | N/A | N/A | Complete | Complete | Partial | N/A | Complete | Complete | Partial | Marketplace/ops journey depth |

## Provider / Clinical / Place integration wave

Legend unchanged. `Trust headers/context` = header/context propagation only — fine-grained **policy enforcement of the new rules is spec-only (GAP-6, CZO-locked)**, NOT enforced. `Web UI` covers provider/place surfaces only; **patient-facing surfaces are Missing (GAP-8)** and **mobile parity is Missing (GAP-19)** for every row below.

| Service / Feature | Backend | Persistence | Migrations | API | Gateway/BFF | Trust headers/context | Web UI | Mobile UI | Tests/smoke | Observability | Gap |
|---|---|---|---|---|---|---|---|---|---|---|---|
| PCT Cadre Engine (`/v1/cadre/decision`) | Complete | Complete | Complete | Complete | Complete | Partial | Partial | Missing | Complete | Partial | Two cadre authorities to unify (GAP-4); enforcement spec-only (GAP-6) |
| PCT Sorting Desk | Complete | Complete | Complete | Complete | N/A | Partial | Partial | Missing | Complete | Partial | Unifying sorting_session entity Missing (GAP-11) |
| PCT Problems list | Complete | Complete | Complete | Complete | N/A | Partial | Partial | Missing | Complete | Partial | ICD-11/SNOMED readiness Missing (GAP-15) |
| PCT OPD care plans | Complete | Complete | Complete | Complete | N/A | Partial | Partial | Missing | Complete | Partial | Cadre form content depth (GAP-10) |
| PCT Community context | Complete | Complete | Complete | Complete | N/A | Partial | Partial | Missing | Complete | Partial | Offline reconcile present; breadth pending |
| PCT Telemedicine completeness | Complete | Complete | Complete | Complete | Complete | Partial | Partial | Missing | Complete | Partial | Structured response + telemed→value Live |
| PCT↔inpatient admission handshake | Complete | Complete | Complete | Complete | N/A | Partial | N/A | N/A | Complete | Partial | Idempotent; six-context parity unverified (GAP-20) |
| TUSO Facility Mode / setup / units / service-points | Complete | Complete | Complete | Complete | Complete | Partial | Complete | Missing | Complete | Partial | Mobile parity Missing (GAP-19) |
| TUSO Control Tower | Complete | Complete | Complete | Complete | Complete | Partial | Complete | Missing | Complete | Partial | Operator depth |
| Indawo surveillance / place mode | Complete | Complete | Complete | Complete | Complete | Partial | Complete | Missing | Complete | Partial | Mobile parity Missing (GAP-19) |
| Workforce-governance facility↔regulator | Complete | Complete | Complete | Complete | Complete | Partial | Complete | Missing | Complete | Partial | Multi-council status lifecycle Live |
| VARAPI provider bootstrap + council/EC resolver | Complete | Complete | Complete | Complete | N/A | Partial | Partial | Missing | Complete | Partial | Self-claim token single-use Live |
| Vashandi work-context + ad-hoc check-in | Complete | Complete | Complete | Complete | Complete | Partial | Partial | Missing | Complete | Partial | Work/Pro/Life server enforcement deferred (GAP-7) |
| Tshepo-identity silent resolution | Complete | Complete | Complete | Complete | Complete | Partial | N/A | N/A | Complete | Partial | Phone/email/invite Partial (GAP-5) |
| COSTA emergency reconciliation + waivers | Complete | Complete | Complete | Complete | N/A | Partial | Partial | Missing | Complete | Partial | MADI-blood value signal still a gap |
| COSTA teleconsult→value | Complete | Complete | Complete | Complete | N/A | Partial | N/A | N/A | Complete | Partial | C8 leakage closed |
| Coverage subsidy enrolment + cap | Complete | Complete | Complete | Complete | N/A | Partial | Partial | Missing | Complete | Partial | Drawdown concurrency-safe |
| Experience BFF FacilityMode/Regulator/PlaceMode/auth-session/work-context/encounter-cadre | Complete | N/A | N/A | Complete | N/A | Partial | N/A | N/A | Complete | Partial | BFF stateless composition; dedicated tests landed (GAP-2 closed) |
