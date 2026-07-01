# Frontend / Backend Parity Audit

## Method

This audit consolidates:

- web route registry (`ui/one-ui-shell`)
- mobile route/screen surfaces (`apps/mobile/citizen-app`, `apps/mobile/provider-app`)
- existing parity and wiring evidence from `docs/audits/*`, `docs/registry/*`, `docs/mobile/*`

Classification labels:

- `Complete`, `Partial`, `Backend Only`, `Frontend Only`, `Mock Only`, `Broken`, `Missing`, `Needs Refactor`

## Capability parity matrix (current wave)

| Capability | Web | Citizen | Provider | Status summary |
|---|---|---|---|---|
| Trust/context propagation | Partial | Partial | Partial | Header pipelines exist; mobile needed parity expansion toward v1.2 context set |
| Registry-powered discovery and administration | Partial | Partial | Partial | Legacy route drift has been removed for product registry, terminology, identity aliases, and unavailable federation/key admin pages; Registry Hub identity operations are guided and live-only; provider mobile Admin & Registry now surfaces facility lifecycle, locality review, intake/import, MSIKA product search, ZIBO terminology resolve, and trust/consent reads through real BFF routes; citizen mobile ID Recovery exposes live identity search/resolve/recovery |
| Queue/clinical encounter workflows | Partial | N/A | Partial | Accounted existing PCT-backed web/mobile surfaces before patching: queue, encounters, triage, vitals, labs, referrals, prescriptions, notes, discharge, and telemedicine are already surfaced. Current wave fixes canonical encounter start payloads around required PCT journey IDs, aligns mobile vitals/triage payloads with typed mobile BFF controllers, and brings live triage into the mobile encounter tab instead of a pointer-only panel. |
| Telemedicine | Partial | Partial | Partial | Real routes and clients exist; operational depth and failover handling remain |
| Marketplace / Health OS apps launcher | Partial | Partial | Partial | Launcher surfaces exist on web/mobile with mixed maturity |
| Coverage, claims, and payer operations | Partial | Partial | Partial | Coverage page now has guided command fields, React-state tabs, canonical appeal submit/review/decision, and live eligibility/member/claim/preauth/appeal commands; `/finance/payer-ops` composes claims/remittance with an intent-linked state machine for attempts/receipts/settlements/refunds; citizen/provider mobile expose payer-ops workspaces with reconciliation and durable provisional retry queues |
| Social / communities / pages | Partial | Partial | Partial | Wired service layer exists with growing parity |
| Public health / field operations | Partial | Partial | Partial | Present in both apps with partial workflow depth |
| Core transaction journey | Partial | Partial | Partial | Doctrine routes now run BFF-only (no fixture injection) with explicit loading/error/empty states; deeper action coverage pending |
| Workflow + dispatch operations | Partial | Partial | Partial | Web ops and provider mobile Flow/Ops now surface workflow/dispatch reads plus live workflow start/transition and dispatch task/delivery commands; citizen mobile remains limited to journey-specific views |
| Nompilo global companion | Partial | Partial | Partial | Embedded presence exists; command/action parity and policy depth ongoing |
| Offline/provisional flows | Partial | Partial | Partial | Coverage mobile commands now persist to the shared offline queue, expose retry history, and reconcile via sync engine; other domains still need equivalent UX |
| Facility / place mode operations | Partial | N/A | Missing | Web `/facility/[id]/*` (cockpit/setup/units/service-points/control-tower/regulators) and `/indawo/*` (surveillance/outbreaks/field-teams) are live against tuso/indawo/governance via BFF; **mobile parity Missing (GAP-19)** |
| Encounter cadre orchestration | Partial | Missing | Partial | Adaptive Encounter Cockpit renders from PCT/BFF cadre decision; cadre-specific form *content* Partial (GAP-10); two cadre authorities to unify (GAP-4) |
| Provider bootstrap / silent resolution | Partial | Partial | Partial | VARAPI bootstrap + council/EC resolver and Tshepo-identity silent resolution live for Health/Impilo/Provider-ID/council; phone/email/invite deny-safe (GAP-5) |
| Access / value / compensation (COSTA + coverage) | Partial | N/A | Missing | Emergency reconciliation, waiver CRUD, teleconsult→value, subsidy+cap are live backend; web surfacing Partial; mobile Missing (GAP-19) |
| Patient-facing journey status surfaces | Partial | Partial | N/A | **GAP-8 reassessed 2026-07-01 (verified vs code):** web `/citizen/visit/[transactionId]`, `/citizen/inpatient/[admissionRef]` (wired to `PatientLaneService`) and `/citizen/my-care` (live `/internal/v1/citizen/health-summary`) ship; citizen mobile `QueueStatusSection` on `/internal/v1/mobile/citizen/{queue,visit,inpatient}`. Still Partial: unified web queue/active-visit-status + orders/outcome timeline not yet composed. |

## Doctrine-alignment findings

- **Health OS coherence:** shell and service launcher patterns exist, but not all route families are equally mature.
- **BFF composition:** dominant pattern is BFF composition, with remaining legacy drift documented in audits.
- **Contract-first:** strong movement, but legacy local model drift still appears in selected fronts.
- **No fake capability:** maturity badges and explicit placeholders are in use; remaining fixture-backed doctrine routes are documented.
- **Person-first and trust layer:** structure exists in identity and header layers; consistency across all surfaces remains partial.

## Reconciliation note (2026-07-01)

A verification pass confirmed several matrix rows lagged the code:

- **GAP-8 (patient-facing journey status)** was labelled "Missing everywhere" but web
  `/citizen/visit/[transactionId]` + `/citizen/inpatient/[admissionRef]` are wired to
  `PatientLaneService`, and citizen mobile ships `QueueStatusSection`. Downgraded from Missing
  to **Partial** (web + citizen) and a `/citizen/my-care` at-a-glance index was added on the
  live `/internal/v1/citizen/health-summary` composition. Remaining Partial: unified web
  queue/active-visit-status + orders/outcome timeline (deferred seam — not faked).
- **Mobile trust headers**: verified the mobile client (`@impilo/mobile-trust/headerBuilder`)
  auto-injects the full v1.1/v1.2 header set (14 headers, 4 hard-required); the "Partial" label
  reflects server-side *enforcement* variance (GAP-6), not header *injection*, which is complete.

Fuller row-by-row reconciliation across all services remains open.

**Deferred seam — citizen Costa billing (mobile):** verified the citizen pending-charges route is
backed by an existing COSTA client method (`CostaServiceClient.getFinancePatientOutstanding`), and
recorded an implementation-ready spec in `docs/implementation/mobile-costa-bff-contract.md`. Not
shipped in this drive because the experience-bff Java module cannot be compiled/verified in the
web-session environment; the citizen surface remains honestly `blocked` (no fabricated charges)
rather than wired to an undeployed route.

## High-priority gaps (remediation queue)

1. Complete wiring for fixture-backed doctrine pages to real BFF orchestration where available.
2. Finish trust/context parity so mobile and web carry equivalent context headers.
3. Close remaining reachability and route-integration gaps for implemented screens (initial pass completed for citizen provider discovery, provider clinical orphan surfaces, and mobile encounter triage/vitals contract alignment).
4. Expand workflow/event/audit timeline visibility on major operational surfaces.
5. Align remaining frontend DTO drift to canonical contracts.

Update in this remediation wave:

- **Provider / Clinical / Place integration wave registered (GAP-22):** the ~21k-line merge on `integration/provider-clinical-place` added new provider/facility/place surfaces (PCT cadre/sorting/problems/care-plans/community/telemedicine, TUSO facility-mode/setup/units/control-tower, Indawo surveillance/place-mode, governance facility↔regulator, VARAPI bootstrap, Tshepo-identity silent resolution, Vashandi work-context, COSTA emergency-reconcile/waivers/teleconsult→value, coverage subsidy+cap) plus web routes `/facility/[id]/{cockpit,setup,departments,regulators,control-tower}` and `/indawo/{,,surveillance,outbreaks,field-teams}`. Status labels are taken straight from `docs/audits/provider-clinical-place/consolidated-gap-register.md` — not inflated. Policy enforcement is spec-only (GAP-6), patient-facing surfaces are Missing (GAP-8), mobile parity is Missing (GAP-19), cadre form content Partial (GAP-10), sorting-session Missing (GAP-11), phone/email resolution Partial (GAP-5).

- Doctrine web journey routes now query live core-transaction feeds without fixture data injection and render explicit empty/error states when no live data is available.
- Platform journey now includes live workflow and dispatch timeline visibility from `/internal/v1/workflows` and `/internal/v1/dispatch/tasks`.
- Operator telemetry rendering is now standardized across platform and operations routes via a shared component, and platform telemetry filters are URL-synced for reproducible operator views.
- Provider workspace now includes live provider-facing workflow/dispatch telemetry overlays with URL-synced drill-down filters for reproducible provider operations views.
- Telemetry rows now support actionable drill-down links into operations routes with focused-row context for investigation continuity.
- Core transaction shells now expose a real Nompilo handoff command using the BFF endpoint with explicit success/failure operator feedback.
- Operations dispatch now surfaces additional backend datasets from dispatch-service controllers and exposes live delivery-create command submission from the UI.
- Operations workflows now surface workflow definitions and workflow instances in addition to telemetry feed cards.
- Core transaction shells now also expose governed Nompilo command execution from live BFF contracts with explicit accepted/error feedback.
- Workflow BFF now exposes sovereign instance start/transition endpoints and the operations workflow page provides live command consoles for those actions.
- Dispatch operations now expose task create/assign/complete and delivery action commands alongside delivery creation, with explicit success/failure feedback.
- Provider mobile `Flow/Ops` now uses a shared mobile service for workflow/dispatch reads and command actions, beginning mobile parity for this high-value backend family.
- Registry Hub now surfaces VITO/VARAPI identity operations through guided validated forms; product pages use `/internal/v1/product-registry/*`; terminology pages use ZIBO resolve at `/internal/v1/registry/zibo/artifacts/resolve`; federation/key admin pages are explicitly unavailable until typed BFF contracts exist; Mvumo has a typed admin BFF surface at `/internal/v1/mvumo-admin/*`; provider mobile Admin & Registry uses mobile services for identity, facility lifecycle, locality review, registry intake/import, MSIKA product search, ZIBO terminology resolve, and trust/consent reads. Facility direct create/update remains explicitly unsupported; facility lifecycle uses canonical `/internal/v1/facility-registry/*` application/inspection workflows instead.
- Coverage Operations now uses guided command payloads for eligibility, member enrollment, claim submission, preauth, and appeals; review/decision appeal actions are BFF-proxied to the sovereign appeal contract; dedicated finance refund/settlement routes remain intact but are now composed from `/finance/payer-ops`; citizen/provider mobile have payer-ops workspaces plus durable queued command reconciliation.

## Evidence references

- `DOCTRINE_COMPLIANCE_MATRIX.md`
- `SERVICE_WIRING_MATRIX.md`
- `WEB_MOBILE_PARITY_MATRIX.md`
- `docs/audits/FRONTEND_ROUTE_INVENTORY.md`
- `docs/audits/WEB_MOBILE_PARITY_AUDIT.md`
- `docs/audits/BFF_API_WIRING_AUDIT.md`
- `docs/registry/backend-to-frontend-wiring-map.md`
