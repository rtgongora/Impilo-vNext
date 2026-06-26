# Cross-Program Audit — Provider Experience (T1) · Core Transaction (T3) · Facility/Place/Org/Regulation (T4)

> **Status:** DESIGN GATE output. Branch `intake/provider-clinical-place-design`.
> Grounded in the real repo at canonical HEAD `6d522d291`. Verdicts use:
> **Live** (wired logic, real APIs) · **Partial** (some layers/logic missing) · **Fixture** (UI/stub, no real backend) ·
> **NotWired** (exists but not connected) · **Missing** (absent).
> Companion docs: [journey map](../../journeys/core-transaction-patient-access-encounter-orchestration.md) ·
> [Facility-Mode ownership split](../../design/provider-clinical-place/facility-mode-ownership-split.md) ·
> [shared read-models](../../design/provider-clinical-place/shared-read-models.md) ·
> [Tshepo policy list](../../design/provider-clinical-place/tshepo-policy-contract-list.md) ·
> [lane plan](../../design/provider-clinical-place/implementation-lane-plan.md).

## 0. Headline findings

1. **The backend SoR spine is far more complete than the experience.** VITO, Varapi, Vashandi, TUSO, Indawo,
   PCT, OROS, inpatient-service, COSTA, MUSheX, Coverage all have real entities, controllers and lifecycle
   logic. The gaps cluster in **(a) net-new orchestration** (Cadre Engine, Sorting Desk, Facility-Mode
   cockpit, emergency reconciliation) and **(b) experience wiring** (adaptive cockpit, community context,
   telemedicine media, facility-scoped dashboards).
2. **Facility Mode is half-built and split across owners — the #1 collision risk.** Shell *state* exists
   (`identity-context.ts`, `useSessionExperienceContract`, `SessionExperienceService` flags
   `facilityModeAvailable`/`facilityModeActive`), and a facility *selection* page exists, but the **cockpit
   and setup wizard are Missing**. T1 and T4 will both reach for these files. Resolved in the ownership-split doc.
3. **Person-first login exists but the anti-enumeration / silent-resolution / Provider-ID-cannot-authenticate
   guarantees are Partial or Missing** — and this surface **collides with the live CZO auth cluster**.
4. **The PCT Cadre Engine does not exist.** Routing is explicit-queue + acuity→priority only. This is the
   single highest-leverage T3 build.
5. **Honest product truth holds:** telemedicine real-time media fails closed (`501`), payment rails are
   stubbed (`liveCapable()=false`) — these are declared, not faked.

## 1. Migration heads (verified — drive lane migration assignments)

| Service | Dir | Head | Next free |
|---------|-----|------|-----------|
| vito-service | `services/vito-service/.../db/migration` | **V029** | V030 |
| varapi-service | `services/varapi-service/...` | **V015** | V016 |
| vashandi-workforce-service | `services/vashandi-workforce-service/...` | **V001** | V002 |
| workforce-governance-service | `services/workforce-governance-service/...` | **V003** | V004 |
| tuso-service | `services/tuso-service/...` | **V011** | V012 |
| indawo-service | `services/indawo-service/...` | **V006** | V007 |
| pct-service | `services/pct-service/...` | **V014** | V015 |
| inpatient-service | `services/inpatient-service/...` | **V012** | V013 |
| oros-service | `services/oros-service/...` | **V002** | V003 (⚠ live session `task_6b859160`) |
| costing-engine-service (COSTA) | `services/costing-engine-service/...` | **V011** | V012 |
| mushex-service | `services/mushex-service/...` | **V008** | V009 |
| coverage-service | `services/coverage-service/...` | **V009** | V010 |
| referral-service | `services/referral-service/...` | **V001** | V002 |
| booking-service | `services/booking-service/...` | **V001** | V002 |
| madi-service | `services/madi-service/...` | **V005** | V006 (⚠ OROS session) |
| tshepo-authz-service | `services/tshepo-authz-service/...` | **V013** | ⚠ CZO single-writer lock |
| tshepo-identity-service | `services/tshepo-identity-service/...` | **V001** | V002 |
| experience-bff | `services/experience-bff/...` | V44 | **DEAD — stateless, no datasource; do not persist here** |

## 2. T1 — Provider Experience audit

| Capability | Existing path / route | Status | backend / BFF / web / mobile | Gap | Action |
|------------|----------------------|--------|------------------------------|-----|--------|
| Person record + lookup | `vito-service` `GET /v1/clients/{healthId}`, `/by-impilo-id/{id}` | Live | be✓ bff✓ web✓ mob✓ | no phone/email→HealthID resolution | extend resolution chain |
| Anti-enumeration masked search | `vito-service` `POST /v1/internal/clients/search` (name+DOB masking) | Live | be✓ bff✓ | login-path timing/enumeration not covered | spec policy + login behaviour |
| Provider professional profile | `varapi-service` `GET /v1/internal/providers/{id}`, `/by-health-id/{id}`, `/standing-summary` | Live | be✓ bff✓ web-partial | no EC/council-number lookup endpoint | add council-number resolver |
| Provider licence / council affiliation | `varapi-service` LicenseController, AffiliationController | Live | be✓ bff✓ | UI thin | wire professional UI |
| Workforce assignment lifecycle | `vashandi-workforce-service` `/v1/internal/vashandi/assignments/*` (precheck/approve/activate/suspend/end) | Live | be✓ | no "active assignments for actor" query | add context-resolution query |
| **Provider check-in** | `vashandi` `POST /v1/internal/vashandi/attendance/check-in` | Live | be✓ web✓ (`CheckInOutPanel`) mob✓ (`VashandiAttendanceScreen`) | requires pre-assigned shift_id; no ad-hoc | add ad-hoc check-in |
| Person-first login (email/pwd) | `experience-bff` `POST /internal/v1/auth/login` (Keycloak ROPC) | Live | be✓ web✓ mob✓ | silent email→HealthID resolution missing | wire to tshepo-identity (⚠ CZO) |
| Linked-ID discovery (post-login) | `experience-bff` `GET /internal/v1/identity/linked-ids` | Live | be✓ | — | reuse |
| **Provider-ID cannot authenticate** | — | **Missing** | — | no guard preventing Provider Public ID as credential | spec policy `LOGIN-PROVIDERID-DENY` |
| Work / My-Professional / My-Life shell | `experience-bff` `SessionExperienceService` (tabs + visibility), web `WorkspaceSwitcher` | Partial | be✓ web✓ | tab logic exists; no enforced auth boundary between life/pro/work | spec separation policies + enforce |
| Provider-as-person separation | — (implicit) | **Missing** | — | work perms vs own-citizen-record isolation not enforced | spec `WORK-PRO-LIFE-ISOLATION` policies |
| WHERE/WHAT context picker | `experience-bff` workspaces `GET /internal/v1/workspaces?facility_id=`, web `ContextRail`/`WorkspaceSwitcher` | Partial | be✓ web✓ mob✓ | only facility+workspace; **no dept/ward/service-point/virtual-pool/above-site** | extend picker dimensions |
| Workspace entry / activation | `experience-bff` `POST /internal/v1/workspaces/{id}/activate` | Live | be✓ web✓ | — | reuse |
| Facility Mode (enter) | shell state `identity-context.ts`, `useSessionExperienceContract` (`facilityModeActive`) | Partial | web✓ | enters but cockpit Missing (see split doc) | T1 consumes; T4 builds |
| Bootstrap (admin→activate) | `experience-bff` `POST /internal/v1/bootstrap/first-admin`, `/activate`, `/status` | Live | be✓ | no org→reps→bulk-preload→self-claim chain | build bootstrap chain |
| Provider self-claim | — | **Missing** | — | providers cannot claim a preloaded profile | build self-claim |
| Bulk provider preload | — | **Missing** | — | no bulk import path | build preload |

## 3. T3 — Core Transaction audit

| Capability | Existing path / route | Status | be / bff / web / mob / patient-view / access-comp | Gap | Action |
|------------|----------------------|--------|--------------------------------------------------|-----|--------|
| Encounter (SoR) | `pct-service` `EncounterEntity`, `POST /v1/journeys/{id}/encounter/start`, `/encounters/{id}/complete` | Live | be✓ bff✓ web✓ mob✓ | no visit-type state machine | extend |
| Journey state machine | `pct-service` `JourneyEntity` (ARRIVED→TRIAGED→QUEUED→SEEN→ADMITTED/DISCHARGED/...) | Live | be✓ | — | reuse |
| Triage + acuity | `pct-service` `TriageService` `POST /v1/triage` (acuity 1–5) | Live | be✓ web✓ mob✓ (`TriageScreen`) | no re-triage UI | minor |
| Queue / routing | `pct-service` `QueueEngine`, `RoutingEngine`, `POST /v1/queues/{id}/enqueue` | Live | be✓ web✓ mob✓ | no cadre/pool/smart routing | extend |
| **PCT Cadre Engine** | — | **Missing** | — | role+cadre+scope+visitType+acuity+context⇒workflow absent | **BUILD (PCT V015)** |
| **Sorting desk + visit-type** | — | **Missing** | — | no explicit pre-queue sort step | **BUILD** |
| Encounter Cockpit (web) | `one-ui-shell` `/ehr/[patientId]/encounter/[encounterId]`, `EncounterOrchestrationRail` | Partial | web✓ | tabs fixed not adaptive; Problems/Orders-create/CarePlan stubs | wire adaptive spine |
| Problems list | — | **Missing** | — | no problems entity | build (PCT) |
| Clinical notes (SOAP) | `pct-service` `ClinicalNoteEntity` `POST /v1/clinical-notes` | Live | be✓ web-partial | — | wire UI |
| Orders create from cockpit | `one-ui-shell` `EncounterLabOrdersPanel`/`EncounterImagingOrdersPanel` (display-only) | Partial | web-display | no create flow into OROS | wire OROS (live session) |
| Care plan (outpatient) | — (inpatient-only) | **Missing** | — | no OPD care plan | build (PCT) |
| Care plan (inpatient) | `inpatient-service` `CarePlanEntity`+goals+interventions | Live | be✓ | — | reuse |
| Referral / consult | `referral-service` `POST /internal/v1/referrals`; `pct` referral+`ReferralPackageEntity` | Live | be✓ web-partial (`ReferralPackageBuilder`) | builder lacks consent/attachments/auto-summary | complete builder |
| Admission | `pct` `AdmissionWorkflow` + `inpatient-service` AdmissionEntity | Live | be✓ web✓ mob✓ | two admission entities; handshake unclear | reconcile ownership |
| Ward / bed / round | `inpatient-service` Ward/Bed/WardRound/EWS/Handover (V012) | Live | be✓ web✓ (`/clinical/inpatient/*`) mob✓ | rounds log thin on mobile | wire |
| Transfer | `inpatient-service` TransferEntity; `pct` TransferService | Live | be✓ | — | reuse |
| Discharge clearance | `pct` `DischargeWorkflow` (clinical/pharmacy/billing/payment blockers) | Live | be✓ web-partial (`EncounterDischargePanel`) | UI not fully interactive | wire UI |
| Death workflow | `pct` `DeathWorkflow` → Ubomi | Live | be✓ | — | reuse |
| Telemedicine 7-stage | `pct` `TelemedicineOrchestrationService`, `TelemedicineController`; bff `TeleconsultController` | Partial | be✓ bff✓ web-partial mob-partial | stages 2/3/6/7 incomplete; media absent by design | complete (see §7 journey) |
| Telemed session providers | `pct` 4 providers (managed/async/manual/external) | Live | be✓ | real-time media fails closed `501` | honest; build signaling later |
| Six work contexts | Inpatient✓ ED✓ Virtual✓ Procedure-partial OPD-partial Community-missing | Partial | mixed | Community backend absent | build community context |
| Booking / appointment | `booking-service` `AppointmentService`; bff `BookingController` | Live | be✓ bff✓ web✓ mob✓ (`BookingsSection`) | — | reuse |
| Patient check-in (patient side) | `one-ui-shell` `appointment-check-in-routing.ts`; kiosk `/kiosk` | Partial | web (kiosk=Fixture) | kiosk not production-wired | wire walk-in |
| Visit outcome → value | covered in Lane C below | Partial | — | telemed→value unwired | wire |

## 4. T4 — TUSO / Indawo / Facility Mode / Org / Regulation audit

| Capability | Existing path / route | Status | be / bff / web / mobile | Gap | Action |
|------------|----------------------|--------|-------------------------|-----|--------|
| Facility master + lifecycle | `tuso-service` `FacilityEntity`, `FacilityRegulatoryService`, `/v1/internal/facilities/{id}/regulatory/*` (DRAFT→REGISTERED→SUSPENDED→REVOKED) | Live | be✓ | no UI | build facility admin UI (T4) |
| Facility units (departments) | `tuso-service` `FacilityUnitEntity` | Live | be✓ | no public API endpoint | add controller |
| Service-points / resources / workspace rules | `tuso-service` `ResourceService`, `WorkspaceService`, `/v1/internal/resources/*`, `/workspaces/*` | Partial | be✓ | no UI binding | wire facility-mode |
| Control tower / ops dashboard | `tuso-service` `ControlTowerController` `/v1/internal/control-tower/*` | Partial | be✓ | real-time aggregation stub; no UI | build dashboard UI |
| **Facility Mode cockpit** | — | **Missing** | — | no facility home/overview/quick-actions page | **BUILD (T4)** |
| **Facility setup wizard** | — | **Missing** | — | no dept/service-point/queue/workflow/workforce/OROS-routing/Khuluma/Fundo/go-live wizard | **BUILD (T4)** |
| Facility Mode shell state | `one-ui-shell` `identity-context.ts` (`ShellMode=facility_mode`), `useSessionExperienceContract`, `experience-bff` `SessionExperienceService` flags, `/facility/page.tsx` | Live/Partial | web✓ | state+selection only | **T1 consumes, T4 builds cockpit** |
| Facility name resolver | `experience-bff` `FacilityNameResolver` | Fixture | bff (seeded) | not wired to real TUSO lookup | wire to TUSO |
| Site (public-health place) | `indawo-service` `SiteEntity` (+lifecycle/regulatory) | Live | be✓ | no UI | build Indawo mode UI |
| Site inspections / compliance / enforcement | `indawo-service` `SiteInspectionEntity`, `SiteRegulatoryController` `/internal/v1/site-registry/*` | Live | be✓ | no UI | wire |
| Site assignments / operators | `indawo-service` `SiteAssignmentEntity`, `SiteOperatorEntity` | Live | be✓ | — | reuse |
| **Site surveillance / outbreak** | — | **Missing** | — | no outbreak/case-investigation/surveillance-alert model | **BUILD (Indawo V007)** |
| **Field teams / inspector dispatch** | — | **Missing** | — | no team coordination/roaming | **BUILD** |
| Org registry + hierarchy | `workforce-governance-service` `OrganisationEntity`, `OrganisationUnitEntity`, `FacilityOrganisationLinkEntity` | Live | be✓ | no public REST for membership | add controllers + UI |
| Affiliations (facility↔org) | `workforce-governance-service` `FacilityOrganisationLinkEntity` (temporal, primary flag) | Live | be✓ | — | reuse |
| Tenancy / X-Tenant-ID enforcement | `butano` `TenantEnforcementInterceptor`; all services via `RequestContextHolder` | Live | be✓ | — | reuse |
| Council registry + regulation | `varapi-service` `CouncilEntity`, `ProviderCouncilProfileEntity`; `tshepo` `CouncilRegulatoryEvaluationService` | Live | be✓ | **single council per tenant assumption**; no facility↔regulator relationship model | build multi-regulator relationship |
| Regulator / oversight mode | `one-ui-shell` `/work/regulators/[regulatorId]/*` (8 panels) | Partial | web✓ | backed by partial data | wire to council/regulatory backends |
| Scoped dashboards + cross-tenant protection | `experience-bff` `FacilityOperationsAggregateController`, `AggregateVisibilityGuard`; `indawo` `/dashboard/summary` | Partial | be✓ | UI Missing; data-quality dash is Fixture | build scoped dashboards |

## 5. Experience surfaces (cross-cutting reality check)

- **one-ui-shell** (Next.js app router): ~81 top-level route groups; `/work/*` (101 pages), `/home/*` (16),
  `/clinical/*` (12), `/queue/*` (7), `/settings/*` (7) are largely **Live** and BFF-wired. `/professional/*`
  is a **Fixture** stub (no pages). `/provider/*` (activate/status) **Partial**. `/core-transaction/*` feed
  Live, detail router Fixture. Trust headers fully injected in `api-client.ts`.
- **Mobile** (`apps/mobile`, Expo monorepo): `citizen-app` ~80 screens, `provider-app` ~95 screens, mostly
  **Live** against the same BFF. Notable: provider-app has **outreach** screens (community) that are
  **NotWired** to a backend context, and **offline/break-glass** flows that are Live.
- **experience-bff**: 256 controllers, ~800 endpoints, **stateless proxy** — its migration files (head V44)
  are **DEAD** (no datasource). *No persistence work may land in the BFF;* push state into the owning SoR.
- **i18n**: en/sn/nd locale catalogs Live with fallback chain — patient-message multilingual readiness exists.

## 6. Gap clusters → owning lane (summary)

| Gap cluster | Net-new? | Owning SoR / lane | Migration |
|-------------|----------|-------------------|-----------|
| PCT Cadre Engine, Sorting Desk, Problems, OPD Care Plan, Community context | mostly new | PCT (clinical/encounter lane) | PCT V015+ |
| Inpatient↔PCT admission handshake | wiring | inpatient lane | inpatient V013 |
| Facility Mode cockpit + setup wizard, facility admin/control-tower UI, service-point APIs | new | TUSO + facility-mode lane (T4) | TUSO V012+ |
| Indawo surveillance/outbreak/field-teams + Indawo mode UI | new | Indawo (place lane) | Indawo V007+ |
| Org membership APIs, multi-regulator relationship, regulator mode wiring | mixed | workforce-governance + TUSO/Indawo | WGV V004, TUSO V012 |
| Emergency reconciliation, subsidy enrolment, waiver CRUD, telemed→value | mixed | COSTA/Coverage/MUSheX (value lane) | COSTA V012, Coverage V010, MUSheX V009 |
| Person-first login hardening, Provider-ID-deny, life/pro/work separation, context-picker dims, bootstrap chain, self-claim | mixed | provider-experience lane (⚠ CZO auth coordination) | vashandi V002, varapi V016, vito V030 |
| Telemedicine completeness (consent/attachments/structured response/routing pools) | wiring | clinical lane + OROS (consume) | PCT |
| Adaptive cockpit, community UI, facility dashboards, regulator UI | wiring | experience (web+mobile, no migration) | — |

## 7. Lovable absorption matrix (`rtgongora/impilo-structure` — IDEA-MINE ONLY)

The Lovable prototype is a **UX idea-mine**; it may be wrong/jumbled. **Adopt/adapt/reject — never copy
blindly, never let it override SoR doctrine.** It was not fetched into this repo (external, treat as
reference). Decisions below are driven by what the real repo already proves, cross-referenced with the UX
patterns the prototype is known to surface (referral builder, teleconsult panes, encounter cockpit).

| Lovable UX pattern | Verdict | Rationale / where it lands |
|--------------------|---------|----------------------------|
| Multi-step **Referral Package Builder** wizard | **Adopt** | already partially mirrored in `ReferralPackageBuilder.tsx`; complete it (consent + attachments + auto-summary) per journey §7 |
| **3-pane teleconsult** layout (chat / response draft / patient panel) | **Adopt** | matches `telemedicine/session/[sessionId]` web layout; keep, wire structured response |
| **Encounter cockpit with fixed tab rail** | **Adapt** | replace fixed tabs with **Cadre-Engine-driven adaptive spine** (doctrine: adaptive per context) — do NOT hardcode tabs |
| Incoming-referrals **worklist with status badges** | **Adopt** | matches `/queue/incoming-referrals`; add time-waiting + pool routing |
| **Real-time video room** mock | **Reject (as-is)** | repo intentionally fails closed (`501`) until a real signaling service exists; do not ship a mock that fakes liveness |
| **Single hardcoded regulator** assumption in prototype | **Reject** | violates T4 "no hardcoded single regulator"; use council-relationship model |
| **Sorting desk / visit-type** screen (if present) | **Adapt** | no backend today; build PCT sorting-desk first, then bind a screen |
| Provider **role/context picker** UX | **Adapt** | reconcile with existing `WorkspaceSwitcher` + extend dimensions (dept/ward/service-point); do not fork a new switcher |
| Patient-facing **plain-language status cards** | **Adopt** | aligns with message catalog §8; bind to i18n keys, not literals |
| Any **mock-only dashboards / dead buttons** | **Reject** | violates "no mock-only screens / dead buttons / fake completions" — only build dashboards backed by the Live control-tower/aggregate controllers |

**Absorption rule:** a Lovable pattern is adopted **only** when (a) it maps to a real SoR capability (Live or
planned in a lane) and (b) it does not duplicate an existing shell component. Otherwise adapt or reject.
