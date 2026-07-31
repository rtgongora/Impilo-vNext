# Web ↔ Mobile Parity Matrix — Impilo & Impilo Provider

**Date:** 2026-07-31 · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
**Method:** Three independent static audits (citizen app, provider app, shared platform) reading actual screen/service/navigation code and cross-checking every endpoint against `services/experience-bff` controllers — **not** name-matching. Runtime evidence (emulator install/launch/screenshots) is recorded in `docs/mobile/MOBILE_RECOVERY_REPORT.md`; where a row says *static-verified*, the code is fully wired but end-to-end runtime completion has not been individually proven.

**Statuses:** `Verified parity` (wired + runtime-consistent evidence) · `Partial parity` · `Mobile redesign required` · `Missing` · `Broken` · `Orphaned` (built but unreachable — a mobile-specific failure class) · `Build-blocked` · `API-blocked` · `Permission-blocked` · `N/A`.

This matrix supersedes the optimistic classifications in `docs/mobile/full-mobile-parity-matrix.md` and `docs/architecture/MOBILE_PARITY_MATRIX.md` wherever they conflict: those were written without runtime proof and without the orphan/endpoint cross-checks below.

---

## Section 1 — Impilo (citizen)

Navigation truth: store-driven (Zustand), `AuthGuard` → `CitizenTabs` (8 tabs); ~50 sub-sections hang off the Personal screen's pill bar. Anonymous lane exists pre-auth (signup, OTP, health info, verify, emergency tracking). All services call the BFF via `mobile-api-client`; **no dead endpoints found** — every endpoint cross-checked against BFF controllers resolves.

| Capability | Status | Key evidence / gap |
|---|---|---|
| Find care / Ndila | Partial parity (static-verified) | ProviderDiscovery + FacilityDirectory/Detail + guest gateway; `/internal/v1/facilities`, `/public/gateway/facilities`; deep link `/welcome/find-care`; Maestro tier3. **Map canvas renders blank on Android** (no Google Maps API key — see §3). |
| Emergency SOS / Daidzai | Partial parity (static-verified) | SosScreen + FAB collect **callback phone** for guests → `POST /internal/v1/public/gateway/sos`; authenticated → Daidzai. Track-by-reference on public lane. Runtime Maestro guest raise not yet proven on 218. |
| Nompilo | Partial parity | Global assistant bottom-sheet + guidance sections → `/internal/v1/nompilo/*`, `/internal/v1/llm/chat`. Gap: **no anonymous lane** — pre-login users silently get canned fallback, never the model (web has anonymous Q&A). |
| Appointments / scheduling | Partial parity (static-verified) | AppointmentsSection (cancel/check-in) + BookingsSection (create); deep link `/appointments` (note: that path has no web top-level route — link drift). |
| Virtual care (telemedicine + Impilo Live) | Partial parity (static-verified) | TelehealthList/Session (LiveKit via `mobile-session`, waiting room, token refresh) + Live discover/event screens; Maestro waiting-room flow. Gaps: telehealth **not deep-linkable** (code comment claims it is); preview media over `ws://:7880` cleartext; no TURN/TLS until `turn.*` DNS lands. |
| Health records | Partial parity (static-verified) | Records/Timeline/Allergies/Conditions/Immunizations/CarePlans/CareTeam + sharing/claim/verify longtail (offline-queued); Maestro wave2 flow. Online-only for core reads. |
| Prescriptions / pharmacy | Partial parity (static-verified) | PrescriptionsSection (refill), delegated pickup, Nhume tracking overlay. |
| Lab / diagnostic results | Partial parity (static-verified) | ResultsSection → `/mobile/citizen/results` (read-only, matches citizen web scope). |
| Khuluma messages & notifications | Partial parity | CommsHub + Meeting (LiveKit) + messaging inbox/threads wired. **NotificationsScreen is Orphaned** (fully built, zero importers — Home bell routes to Messages tab instead). SSE realtime risks: RN has no native EventSource; bearer token in query string. |
| Ruvimbo (coverage) | Partial parity (static-verified) | CoverageSection + offline-queued commands (claims/appeals/eligibility/preauth/remittances). No dedicated appeal-tracking UI depth vs web Ruvimbo faces. |
| Bills / payments / wallet | Partial parity (static-verified) | Finance/Wallet/SmartCard sections → wallet + Costa pending charges. Deep link `/bills` has no web counterpart route (drift). |
| Profile / settings | Partial parity (static-verified) | Profile, consents, notification prefs, account deletion, privacy/terms. |
| Household / guardian / caregiver | **Missing** | `caregiving/DelegationSection` is a **hardcoded-EmptyState stub with a no-op button, and orphaned**; BFF `CitizenDependantController` unused. Dependants only appear read-only inside WalletOverview. Biggest citizen functional gap. |
| Wellness | Partial parity | Sections + challenges/programs/journeys/assessments/monitoring wired; **WellnessSection has no loading/error handling** around 8 calls. |
| Marketplace | Partial parity (static-verified) | Marketplace tab + cart + HealthOS apps + store sections; Maestro smoke taps. |
| Madi (blood donor) | Partial parity (static-verified) | Full donor hub (become/screening/drives/history/profile/feedback); drives use ndila maps (blank-canvas risk on Android). |
| Learning (Fundo) | Partial parity (static-verified) | Fundo shell + course player + LiveKit classroom; Maestro live-join flow. 3 taps deep (discoverability). |
| Social / communities / feed | Partial parity (static-verified) | SocialHub → feed/clubs/communities/pages/crowdfunding/composer. |
| Rito (feedback/quality) | Partial parity | Feedback + tracking wired auth-side; deep link `/welcome/report`. Gap: **anonymous reporting lane missing** (web allows pre-auth report; mobile gates it behind login). |
| Get involved / participation | Partial parity (thin) | Deep link lands on PublicHealthScreen (summary+alerts only, no error state); `/public/gateway/get-involved` API unused. |
| CRVS (Ubomi) | Partial parity (static-verified) | UbomiCrvsScreen → `/internal/v1/ubomi`; deep in Services→Apps. |
| Support | Partial parity (static-verified) | SupportScreen → `/mobile/citizen/support`. |
| Onboarding / identity | Partial parity (static-verified) | Login, signup, assurance choice, contact OTP, ID recovery, patient consent; Maestro onboarding + login flows. |
| Offline states (cross-cutting) | Partial parity | Real sync engine initialized, but only longtail sharing + coverage commands are offline-queued; core clinical reads online-only. NetworkStatusBar present. |

## Section 2 — Impilo Provider

Navigation truth: `AuthGuard` → login → ProviderActivation → SelectFacility → SelectWorkspace → `ModeRouter` (5 TSHEPO-role-gated modes). Provider mode: 11 tabs + dynamic Encounter tab; **~35 screens hang off one Tools hub with 48 horizontally-scrolled tool tabs**. Trust headers (facility/provider/workspace) injected on every call.

| Capability | Status | Key evidence / gap |
|---|---|---|
| TSHEPO context resolution | Partial parity | Real chain login→activation→facility→workspace; headers injected per call; modes role-gated. **But see Broken row below on activation.** |
| Provider activation (Varapi) | **Broken** | `ProviderActivationScreen` accepts any free-text string >3 chars as Provider ID → becomes trusted `x-provider-id`, **no Varapi/credential verification** despite `/internal/v1/identity/provider/{id}` existing. Security-relevant defect. |
| Work / worklists / tasks | Partial parity (static-verified) | Dashboard → clinical-worklist, tasks mine/complete/escalate, notices; Maestro tier1/2. |
| My Professional | Partial parity | Profile + settings/channels hubs wired; **hubs silently render hardcoded FALLBACK_SECTIONS when BFF returns empty** — masks backend failure as content (demo-theatre risk). |
| My Life | Partial parity (thin) | Wellness social workbench reachable only via Network tab button. |
| Facility Mode | Partial parity (static-verified) | FacilityAdmin/Setup, ControlTower, QueueDefinitions, PlaceMode (Indawo outbreaks/field-teams/alerts) → facility-mode + place-mode APIs. Buried in Tools. |
| Facility / workspace selection | Partial parity (static-verified) | SelectFacility/SelectWorkspace → facilities + workspaces APIs; Maestro journey-smoke. |
| Worklist & queues | Partial parity (static-verified) | QueueManagement (call/complete/stats), booking requests; Maestro tier2 queue-triage. |
| Client search & identification | Partial parity (static-verified) | Patients tab → MobileProviderSearchController. |
| Consultation / encounter | Partial parity (static-verified) | EncounterScreen + AdaptiveEncounterCockpit (cadre-driven) + notes/diagnosis/vitals/forms panels; dynamic Encounter tab; Maestro journey-smoke. |
| Orders (lab/diagnostics) | Partial parity (static-verified) | LabOrderPanel, Diagnostics tab (orders+drafts), PACS viewer (studies/preview); Maestro tier2 lab. |
| Prescriptions / dispensing | Partial parity (static-verified) | PrescriptionPanel + PharmacyHub/Dispensing incl. five-rights verification; Maestro tier2 pharmacy. |
| Referrals | Partial parity (thin) | Encounter panel only; **no standalone referral inbox/worklist** like web. |
| Admissions / inpatient | Partial parity | InpatientScreen + APGAR/NEWS2/Resus/Trauma/CriticalEvent/WardAlerts/CarePlan/ShiftHandoff/DischargeClearance. **BedManagementScreen orphaned; ConfirmDeathScreen orphaned → mobile cannot complete inpatient death workflow.** |
| Theatre | **Partial parity** | TheatreQueueScreen + TheatreCaseScreen (M1–M2 bedside OR) + **TheatreOpsHubScreen / TheatreCaseOpsScreen (M4)** over BFF `/internal/v1/theatre/**`, `/internal/v1/scheduling/**`, `/internal/v1/referrals/surgical/**`, `/internal/v1/reports/theatre-utilisation/run`, `/internal/v1/procedures/{id}/anaesthesia/chart`. Ops hub: readiness board, surgical referrals decide, waitlist, theatre lists + session detail, utilisation KPIs. Case ops: anaesthesia chart depth, CSSD instrument sets, controlled-drug register (not implant-only). Nav: Theatre tab → queue + “Theatre ops”; case → case ops. List/board GET failures surface unavailable (not empty); BFF scheduling lists no longer swallow upstream failures to `[]`. Gaps vs web: obstetric cascade, body-map site marking, discharge summary depth, Maestro theatre flow. |
| Surgical episodes | **Partial parity** | SurgeryEpisodesScreen (**provider Surgery tab**) → `/internal/v1/surgery/episodes/**`: list/open by CPID, assessment, decision/MDT, reopen, specialties, course-of-care (prehab/complications/longitudinal/followup/waitlist). Client also exposes grade/disclose/close, longitudinal remove/revise, link-procedure, specialty indications/templates/analytics. Honesty: list failure ≠ empty; assessment/decision/follow-up 404 = not recorded. Specialty catalogue UI thinner than web. |
| Procedures catalogue | **Partial parity** | ProceduresCatalogueScreen (Clinical Tools → **Procedures**) → `/internal/v1/procedures/**`: search/detail, appropriateness, competence, safety-pause / sedation / recovery / aftercare, analytics indicators, Clavien-Dindo + complication profiles. Honesty: catalogue failure ≠ empty. Perioperative episode wizard remains under Theatre. |
| Emergency / ED / ePCR | Partial parity (static-verified) | **Emergency tab** (1 tap) → hub: Episodes | ED | Trauma | Resus | MH. Episode spine service wraps all `/internal/v1/emergency-episodes/**`; ED depth (zone, diagnostics, protocol, page, trauma ack/escalate); resus on `/internal/v1/ed/resuscitation/*`; offline outbox for ED/episode writes; `NOT_TRIAGEABLE_OFFLINE` on triage. Also in Clinical Tools + Inpatient legacy tabs. ePCR Maestro flow exists. DaidzaiFieldMissionScreen still orphaned. |
| Mental health (provider) | Partial parity (API-blocked in preview) | `mentalHealthService` + queue (PENDING/ACCEPTED) + referral clinical record (assessment, risk, safety, involuntary, restraint, admission, follow-up) in Emergency hub. Reads fail honestly when mental-health-service undeployed — same as web. |
| Khuluma (provider) | Partial parity (static-verified) | MessagingScreen → provider messaging + communication dashboard + notifications; realtime via channels. |
| Impilo Live (provider) | Partial parity (static-verified) | TelemedicineScreen/CallScreen + ProviderLiveHub → teleconsult + live room/token; Maestro waiting-admit flow. |
| Ruvimbo Provider (coverage) | Partial parity (buried) | Full eligibility/claims/preauth/remittance/appeals service — surfaced only inside FinanceOverview under Tools→Finance; no dedicated coverage workspace. |
| Vashandi shifts / roster | Partial parity (static-verified) | Workforce hub + attendance/availability/roster/facility-staff → ProviderVashandiController. No Maestro flow. |
| Rito (safety/quality) | Partial parity (static-verified) | ReportSafety + MySafetyCases → work rito cases. |
| Tuso (premises self-service) | **Missing** | No screens; service card redirects to Professional tab. |
| Regulatory self-service | Partial parity (static-verified) | My Regulatory Affairs under Professional (`/internal/v1/me/regulatory/*`, practice establishments, student sections/resubmit, contributor invite redeem + deep link). Citizen public explore (councils+registers via public gateway). Operator desks (register reconcile, student review, W1D boards, config authoring) stay **WEB_ONLY**. |
| Learning (Fundo CPD) | Partial parity (static-verified) | Fundo shell + classroom + Training tab; Maestro classroom flow. |
| Madi (provider) | Partial parity (static-verified) | Orders/Transfusion/DriveCapture/ReactionReport/CentralBank + offline drive sync. |
| Budgets | **Orphaned** | BudgetSummaryScreen + budgetService fully built, never imported. |
| Equipment | **Orphaned** | EquipmentToolsScreen + 3 children built with service, unreachable. |
| Outreach mode | Partial parity | Dashboard/households/screening/field-tasks wired; FollowUpScreen orphaned; zero Maestro coverage. |
| Supervisor mode | Partial parity (static-verified) | Team/metrics + inventory (stock/alerts/requisitions/dispatch) + escalations; zero Maestro coverage. |
| Offline mode | Partial parity (static-verified) | Real sync engine, local queue, conflict review, break-glass activate/deactivate; zero Maestro coverage. |
| Courier mode | Partial parity (thin) | 2 screens over nhume/deliveries; skeletal vs web Nhume logistics. |
| Adult medicine workspace | **Partial parity** | Encounter → Medicine + Tools → Medicine: programmes/problems/allergies via shared BFF (`/internal/v1/programmes`, `/conditions`, `/allergies`); unavailable ≠ empty. Examination, specialty §8 tools, order sets **NOT BUILT**. See `docs/mobile/adult-medicine-parity.md`. |
| Medicine CDS (8 topics) | **Partial parity** | Tools → Med CDS + Encounter Medicine embed: `POST /internal/v1/medicine/cds/{topic}/evaluate`; 502 surfaced as failure not all-clear. Legacy Tools → CDS tab is a different endpoint. |
| Clerking continuity | **Partial parity** | Encounter → Clerking + Tools → Clerking: read-only problems + visit attestations; extensions/exam write **NOT BUILT**. |
| Chronic registers | **Partial parity** | Tools → Registers: `GET /internal/v1/programmes/register` facility worklist; no control assessment write on mobile. |

## Section 3 — Shared mobile platform

| Capability | Status | Key evidence / gap |
|---|---|---|
| Trust-header contract | Partial parity | All overlapping header names byte-identical with TSHEPO/web. Drift: assurance-vector headers (`x-ial`, `x-aal`, `x-assurance-level` constant, `x-subject-id`, `x-workflow-state`, `x-tuso-facility-id`) missing/hardcoded; web `contracts.ts` and mobile don't share one source. |
| API envelope | **Broken (masked)** | Mobile expects `{success,…}`; BFF actually sends `{data, meta}` / `{error, meta}`. Client auto-unwrap is dead code; every service re-implements unwrapping defensively. Works today, trap for every new screen. |
| Anonymous public lane defaults | Verified parity (static) | Tenant `00000000-…-0001` + pod `national-spine` match `PublicGatewayAnonymousDefaultsFilter` exactly. |
| Auth (PKCE) | Partial parity | Full PKCE/refresh/revoke/logout + secure storage. Biometric unlock **not implemented in mobile-auth** (only a storage flag; app-side usage exists in 2 screens). Preview: Keycloak not externally exposed until the `/realms` ingress route (this recovery) is applied; token **issuer mismatch risk** vs services validating `http://keycloak:8080` (see recovery report). |
| Push notifications | **Missing** | No expo-notifications/FCM/APNs stack, no google-services.json (eas.json references a non-existent file). `registerDevice()` posts a token that can never exist. In-app SSE only — and RN has no native EventSource (needs polyfill; silently broken on device) + bearer token leaks into URL query. |
| Offline platform | Partial parity | Real store + sqlite adapter + 276-line sync engine with conflict records; ~30 import sites. Server contract `/internal/v1/sync/*` assumed. |
| LiveKit (Impilo Live) | Partial parity | URL normalizer byte-identical to web; token via shared teleconsult contract. Preview media cleartext `ws://:7880`; no TURN/TLS fallback until `turn.*` DNS lands (external blocker, known). |
| Maps / Ndila tiles | **Broken** | Governed Martin MVT tile config is fetched but **never rendered** (no UrlTile/MapLibre); react-native-maps defaults to Google provider with **no API key in either manifest** → blank basemap on Android devices. |
| Deep linking / App Links | Partial parity | Custom schemes work (OAuth callback). https App Links: intent filters exist only in `app.config.ts`, **absent from the committed AndroidManifests** (bare builds don't register them); OS verification blocked by TEAMID/signing-cert placeholders; `/appointments` + `/bills` targets have no web route. |
| Design system | Verified parity (static) | 82 exports incl. loading/error/empty/offline components used across ~80 screens. |
| Nompilo SDK | Partial parity | Role-aware prompts → `/internal/v1/llm/chat` with audit flags + deterministic fallback; **no anonymous lane**. |
| Timeline / Integration / Registry | Partial parity (thin by design) | Wired to mobile BFF routes; registry is honest metadata incl. per-service knownGaps. |
| iOS platform | **Build-blocked** | No native iOS projects (config-only, placeholder Apple Team ID). Android-only estate today. |

## Cross-cutting verdict

The June "complete parity wave" claim was **directionally true for breadth of code, false for verified product parity**: virtually every recent web capability has *some* mobile counterpart wired to real BFF endpoints (impressively few dead endpoints), but parity is degraded by (a) a 48-tab Tools hub burying provider capabilities, (b) 10+ orphaned screens across both apps, (c) missing anonymous lanes (SOS raise, Rito report, Nompilo), (d) the caregiving/household stub, (e) platform-level runtime risks (blank maps, no push, envelope drift, App-Links absence), and (f) zero Maestro coverage for 4 of 5 provider modes. Runtime boundary: see `MOBILE_RECOVERY_REPORT.md` for what was actually executed on the emulator.
