# Functional-Depth & Journey-Truth Gap Register

**Date:** 2026-07-13
**Scope:** Varapi, Vito, Indawo, PCT, Telemedicine, PACS, Madi, Coverage, Fundo, Khuluma, Impilo Live + mobile apps (citizen & provider)
**Method:** Six parallel code audits (engine → BFF → UI depth + stage-by-stage journey traces). Every negative claim is backed by a disproving search; two hedged claims were hand re-verified before inclusion. Depth bar = the Tuso/Vashandi waves: rich domain engine (FSMs, lifecycle, tables), full BFF wiring, real UI surfaces, personas, events/outbox, tests.
**Purpose:** Identify functionality gaps ahead of landing current functionality in production. Fixes are scheduled as remediation waves R1–R10 (below), not in this document's wave.

---

## 1. Headline

The "very thin" perception is real but it is mostly a **surfacing/wiring problem, not an engine problem** — the exact pre-wave Vashandi pattern. Most audited services carry deep, tested engines whose capabilities never reached the BFF or UI. There are two genuine engine holes (Indawo geography, PACS modality worklist), one recently-built lane that is stranded (Coverage subsidy), one flagship journey broken at its first stage (inpatient admission), and one previously-feared blocker that turned out to be deployment drift rather than code (VITO MPI 403). **Madi is not thin at all — it is reference quality and should be treated as the bar.** **Fundo's three previously known blockers are verified fixed in code.**

## 2. Direct answers to the journey questions

### 2.1 The 7-stage telemedicine cycle

Defined in `docs/design/teleconsultation-lifecycle.md` ("Teleconsultation Lifecycle — 7-Stage Specification"). Stage-by-stage trace:

| Stage | Engine | BFF | UI (really invokes?) | Verdict |
|---|---|---|---|---|
| 1 Case Identified | ✓ `TelemedicineController.createReferral` (pct-service) | ✓ `POST /internal/v1/teleconsult/sessions` | ✓ `telemedicine/new/page.tsx` → `useCreateTelemedicineSession` | **FUNCTIONAL** |
| 2 Build Referral Package | ✓ `updateReferralStage` | ✓ `PUT /sessions/{id}/referral` + consent | ✓ (patient/visit-summary panels static; attachments free-text) | **FUNCTIONAL** (core) |
| 3 Routing & Worklists | ✓ `routeReferral`/`submitReferral` | ✓ `POST /sessions/{id}/submit` | ✓ (2 of 7 documented worklist types surfaced) | **FUNCTIONAL** (basic) |
| 4 Review & Accept | ✓ `acceptReferral` | ✓ `POST /sessions/{id}/accept`, `/decline` (governed, tested) | ✗ no telemedicine UI control invokes them; `queue/incoming-referrals` accepts via the *plain* `ReferralsController` route | **WIRED-BUT-NO-UI** |
| 5 Session | ✓ messages + RTC via rtc-gateway | ✓ media/token, waiting-room, admit, messages | ✓ session page (`ensureGovernedMedia`, chat); video runtime-gated on LiveKit config | **FUNCTIONAL** |
| 6 Submit Response | ✓ `respondReferral`/`respond-structured` | ✓ `POST /sessions/{id}/response` | ✓ narrative only — orders/prescribing "not wired yet" (self-documented in `work/telemedicine/page.tsx`) | **FUNCTIONAL** (narrative) |
| 7 Completion & Loop Closure | ✓ `completeReferral` → FHIR summary + COSTA bill + analytics | ✓ `POST /sessions/{id}/complete` | ✓ Stage-7 form on session page | **FUNCTIONAL** |

**Answer:** the referrer can drive 1→2→3→5→6→7 end-to-end today. The seam is Stage 4 (specialist governed accept has no UI) and Stage 6 structured orders.

### 2.2 Walk-in outpatient encounter

Register a walk-in → queue → call-next → start consult → document → orders → close → billing: **FUNCTIONAL end-to-end, no break point found.**

| Stage | Evidence |
|---|---|
| Register/lookup | `queue/walk-in/page.tsx` → `usePatients` + `VitoClientRegistrationWizard` (VITO via BFF `PatientController`) |
| Enqueue + call-next | `handleCreateEntry` → `POST /internal/v1/queue/entries` (WALK_IN); `useCallQueueEntry` |
| Encounter start | `ehr/[patientId]/encounters` → `useCreateEncounter` (journey_id supplied by walk-in path — required by design) |
| Documentation | vitals / clinical-notes / triage / forms panel all post to real routes |
| Orders | wired links to `/ehr/[id]/orders` (OROS) and `/pharmacy/prescriptions` |
| Close + billing | `useCloseEncounter` → `POST /encounters/{id}/close`; close handler drafts COSTA bill (non-blocking, idempotent); `costa_bill_id` rendered |

### 2.3 Admission → inpatient stay → discharge

**BROKEN at stage 1 — do not represent as functional.**

- Engine ✓ (pct `AdmissionController`, inpatient-service beds, `InpatientBedAssignedConsumer` Kafka wiring), BFF ✓ (`InpatientController`, `CareEmergencyInpatientController`), hooks ✓ (`useCreateAdmission` in `useInpatient.ts`, `useAssignBed` in `useBeds.ts`) — **zero call sites** (grep across `ui/one-ui-shell/src` finds only the definitions).
- "New admission" and Visit-Outcome-ADMIT "Assign Bed" both dead-end to `/beds`, which can only toggle bed *status* and free a bed — no assign-to-patient control.
- Ward-round recording: `POST /ward-rounds` exists in BFF and inpatient-service; UI `useWardRounds` is GET-only, rounds/episode pages read-only.
- Discharge + discharge-summary (finalise/countersign) are **fully wired** (`clinical/inpatient/discharge/[admissionId]`, `discharge-board`) but unreachable without an out-of-band admission.
- E2E tests mask this: `e2e/inpatient-admission-compose.spec.ts` is load-only; `e2e/inpatient-admission-rounds-flow.spec.ts` mocks a pre-existing admission.

### 2.4 Referral loop

Facility A refers → B receives in `queue/incoming-referrals` → accepts → responds/outcome flows back: **FUNCTIONAL** (create/incoming/accept/respond/complete all UI-invoked against real PCT routes, status sectioning PENDING/ACCEPTED/RESPONDED/COMPLETED).

## 3. Per-domain verdicts

| Domain | Engine | BFF | UI | Verdict |
|---|---|---|---|---|
| **VARAPI** (8083) | Deep: 23 migrations, 33 controllers, **184 endpoints**, 18-state lifecycle FSM, council/CPD/disciplinary engines, ~40 outbox event types | **~40/184 endpoints reachable; 1 dedicated BFF controller** | Partial | **Engine ~80% dark at experience layer** — worst surfacing deficit in the estate |
| **VITO** (8082) | Deep: 30 migrations, 24 controllers, 113 endpoints, merge/**unmerge**, biometrics (fail-closed), cards, patient-share; strongest runtime rigs | Broad: 14 dedicated BFF controllers | Good | Healthy; relationships/household UI + secondary screens missing. MPI 403 = estate drift, not code (§5 G28) |
| **Indawo** (8150) | Regulatory (11-state FSM, inspections, licences, enforcement) + surveillance deep; **geography = dead schema** | Regulatory full (`PublicHealthController`); geo zero | Surveillance + site-registry | **Named remit (place/geography) is a skeleton**: `ind_catchment_areas`/`ind_facility_locations` have no code |
| **PCT** (8088) | Very deep: 31 migrations, 42 tables, 24 controllers, 8 FSMs, queue materialization, forms spine, death pathway, cadre engine | Extensive (~44 BFF controllers, ~40-method client) | Heavy (work/130, ehr/40 pages) | Not thin; sorting desk + body custody are engine-but-no-surface |
| **Telemedicine** (PCT+BFF+RTC+COSTA) | Deep: referral FSM, 5-provider session router, real RTC tokens, live billing seam (`clinical.teleconsult.value` → COSTA) | ~30 routes | Builder + session console | Stage-4 accept surface, Stage-6 orders, scheduling/auto-match missing |
| **PACS** (8113) + OROS | Solid adapter: 15 tables, study FSM, ops suite, real Orthanc integration; order+report lifecycle lives in OROS (rich workflow enum) | Strong (DICOMweb proxy incl. STOW, viewer launch, governance) | OHIF viewer + worklists + diagnostics journey | **No DICOM MWL/MPPS** (capability flags only); no viewer-linked reporting authoring UI |
| **Madi** (8300) | Deepest: 7 migrations, **44 tables**, 12 controllers, 11 FSMs, real transfusion safety rules (ABO/CPID pre-verify gate), cold-chain, SLA timers | Comprehensive (`MadiController` + citizen/provider mobile) | ~25 pages incl. admin/governance | **Reference quality — the bar, not a gap** |
| **Coverage** (8140) | Deep: 12 migrations, 13 controllers; cap-enforced subsidy engine (atomic no-lost-update drawdown), preauth utilization denial, appeals FSM | **Subsidy writes NOT proxied** (only `GET /subsidies`, hand-verified) | 12-tab console; subsidies tab read-only | **L4 subsidy lane stranded + duplicated data model (V010 vs V011/V012)** |
| **Fundo** (learning-service) | Deep: 29 migrations, 30 controllers; cert lifecycle sweeps, completion-rule engine, auto-grading, training gate (ALLOW/ADVISE/CONDITIONAL/BLOCK) | 142 endpoints (`LearningController`) | ~40 pages (catalog→attempt→certificates→CPD→studio) | **Production-landable.** Prior blockers verified FIXED: CPD loop (`impilo.learning.certificate.issued.v1` → varapi listener), Vashandi cpd-summary wire, Tshepo gate. Notification delivery = config toggle (G32) |
| **Khuluma** (8390) | Messaging/meetings deep (WS+SSE+Redis fan-out, lobby/cohost/action-items); escalation FSM + SLA breach sweep real; rtc header issue FIXED (`ServiceHttpConfig` interceptor) | ~40 routes | secure-messaging, my/work comms, admin ops | **Partial**: escalation never pages the on-call roster; broadcast unexposed; delivery-SoR overlap with notification-service |
| **Impilo Live** (live 8380 + rtc-gateway) | Real signed LiveKit JWTs (Nimbus MACSigner + VideoGrant), lobby gate, recording egress, governed modes incl. CLINICAL_SESSION; PCT link via `V013` `live_event_id` | Full teleconsult + live routes | Waiting room, admit control, LiveRoom, backstage, `/live/*` | **Mostly landable**; recording→clinical-record writeback missing (session notes DO land via `TelehealthController.end`) |
| **Mobile** (`apps/mobile/{citizen,provider}-app`, Expo SDK 54 / RN 0.81) | Citizen ~93 screens (8 tabs); Provider ~110 screens across 5 role modes (Provider/Outreach/Supervisor/Offline-Edge/Courier); real SQLite offline + sync engine to offline-sync-service:8095; LiveKit telehealth; ~54 dedicated mobile BFF controllers; 405 unit tests green; 26 Maestro flows | — | — | **Architecturally strong, runtime NOT_PROVEN** (matches `docs/mobile/mobile-runtime-truth-report.md`); APK buildable in CI only; no production signing; auth bypasses BFF ext_authz |

## 4. Consolidated gap register

### P0 — journey blockers & stranded capability

| # | Domain | Gap | Type | Evidence |
|---|---|---|---|---|
| **G1** | Inpatient | **Admission creation + bed assignment have no UI invocation** — `useCreateAdmission`/`useAssignBed` defined, zero call sites; buttons dead-end to bed-status page; ward-round recording GET-only. Journey broken at stage 1; discharge half fully wired but unreachable | Dead-button / wiring | `ui/one-ui-shell/src/hooks/useInpatient.ts`, `useBeds.ts`; `clinical/inpatient` + beds pages; e2e specs mock pre-existing admissions |
| **G2** | Coverage | **Subsidy enrolment+cap lane stranded**: `POST /enrolments`, `/enrolments/{id}/consume` with atomic cap enforcement exist in service; BFF proxies only `GET /subsidies`; UI tab read-only | Wiring | `services/coverage-service/.../SubsidyController.java` vs `services/experience-bff/.../client/CoverageServiceClient.java:82` |
| **G3** | Coverage | **Duplicated subsidy data model**: `cv_subsidy_enrolments`+`balances` (V010) vs `cv_subsidy_enrollments`+`drawdowns` (V011/V012), two entity sets, two controller mappings (`/enrolments` and `/enrollments`). Reconcile BEFORE wiring G2 | Engine / debt | coverage-service migrations V010–V012 |
| **G4** | Indawo | **Geography core is dead schema**: `ind_catchment_areas`, `ind_facility_locations` — no JPA entity, repository, or controller; no catchment resolution or facility↔catchment API | Engine+wiring+UI | `services/indawo-service/src/main/resources/db/migration/V001__init.sql`; grep of `services/indawo-service/src/main/java` → none |
| **G5** | Telemedicine | **Stage-4 governed specialist accept/decline unreachable from telemedicine UI** (endpoints exist + unit-tested; only the plain referral-queue route is invoked) | Surfacing | BFF `TeleconsultController` accept/decline; `ui/.../queue/incoming-referrals/page.tsx` |
| **G6** | Khuluma | **Escalation never pages the on-call roster** — `EscalationService.open()/assign()` emit outbox only; no realtime/notification dispatch, never consults `OnCallService.onCallRoster` | Engine | `services/khuluma-service/.../core/EscalationService.java` |

### P1 — dark engines (built + tested, invisible — Vashandi pattern)

| # | Domain | Gap |
|---|---|---|
| **G7** | VARAPI | Certificates unwired: `CertificateController` (13 endpoints — practising/registration certs, good-standing letter, PDF generator) — no BFF method, no UI |
| **G8** | VARAPI | Licence issue/renew unwired: BFF exposes only `getProviderLicenses` despite renewal FSM states (`LICENCE_DUE_FOR_RENEWAL`, `RENEWAL_IN_PROGRESS`, `LAPSED`) |
| **G9** | VARAPI | Compliance/disciplinary/sanctions invisible: `ComplianceController` (10 eps), disciplinary cases, `DisciplinaryTriggerType` — no BFF, no UI |
| **G10** | VARAPI | 18-state lifecycle + 14 transition endpoints reduced to one `changeProviderStatus` BFF call; no operator transition console (suspend/restrict/lapse/restore/retire/deceased) |
| **G11** | VARAPI | Qualifications (9 eps), practice contexts (9 eps, 0 UI files), affiliation/privilege writes — all unwired (reads only) |
| **G12** | PCT | Sorting Desk unreachable: `SortingDeskController` + `VisitTypeCatalog` + `sorting_records` — no BFF route, 0 UI files |
| **G13** | Khuluma | Channel broadcast (`POST /channels/{id}/broadcast`) not exposed via BFF → no facility-announcement path; khuluma duty/on-call roster has no dedicated UI (`/scheduling/on-call` is a different V29 staffing roster) |
| **G14** | VITO | Household/relationships UI missing: guardian/caregiver/proxy-access engine + BFF complete (`ClientRelationshipType`, `addClientRelationship`), 0 UI files |
| **G15** | Coverage | Preauth reviewer decision workflow under-surfaced: engine `PUT /preauth/{id}/decision` + utilization-cap denial; UI centers on request creation only |
| **G16** | PCT | Body custody / field body management (V022/V027 engine) 0 UI; verbal autopsy BFF-wired, no dedicated screen |

### P2 — genuine engine gaps

| # | Domain | Gap |
|---|---|---|
| **G17** | Telemedicine | No in-session e-prescribing/structured orders (free text only; self-documented "not wired yet") — pharmacy/OROS hook missing from teleconsult flow |
| **G18** | Telemedicine | No scheduling / provider auto-matching: on-call/team/pool routing returns `501 ROUTING_TYPE_UNAVAILABLE`; booking-service teleconsult appointments not joined to referral lifecycle; no follow-up loop |
| **G19** | PACS | No DICOM Modality Worklist / MPPS — `mwlSupported`/`supportsMpps` are capability-registry metadata flags only; no C-FIND SCP. Needs explicit build-vs-defer decision |
| **G20** | Impilo Live | Recording→clinical-record writeback missing: `impilo.rtc.recording.available.v1` consumed only by live-service replay pipeline; no PCT consumer links recordings to `pct_telehealth_sessions` (session notes DO land) |
| **G21** | Indawo | No address normalization/geocoding: `AddressController` is list/get/create only despite lat/long columns |
| **G22** | PACS | No viewer-linked radiology reporting authoring UI (report lifecycle lives in OROS; distribution is event-only) |

### P3 — mobile production path

| # | Gap |
|---|---|
| **G23** | **No proven mobile runtime**: no APK ever built + executed with recorded evidence. This sandbox cannot build one (no Android SDK; proxy blocks `dl.google.com`). Build paths: CI job `mobile-e2e-maestro` (`.github/workflows/ci.yml` — expo prebuild → `gradlew assembleDebug` → emulator + Maestro, uploads `citizen-debug-apk`/`provider-debug-apk` artifacts) or operator VM (`scripts/mobile/runtime-truth.sh`) |
| **G24** | **No production signing**: both apps' release `signingConfig` = `signingConfigs.debug`; no upload keystore; `eas.json` submit creds are placeholders |
| **G25** | **Mobile auth bypasses BFF/Envoy ext_authz** (device-side Keycloak PKCE, tokens in secure-store) — needs IATG trust-program sign-off (`docs/mobile/mobile-runtime-truth-report.md` §4) |
| **G26** | Preview build hygiene: stale realm clients/DNS; plain-HTTP preview at raw IP under scoped `network_security_config` exception; Maestro Keycloak login never validated on a real emulator |
| **G27** | Mobile capability parity partials: sorting desk absent on both platforms; Nhume dispatch / Ndila maps / payments depth Partial per `docs/mobile/full-mobile-parity-matrix.md` |

### P4 — secondary surfacing + hygiene

- **G28** VITO: delegated-pickup / print-jobs / QR-resolver / wallet-journal engines+BFF have no dedicated pages; confirm reversible-merge queue reaches UI. **Verified NOT a gap:** the MPI 403 is estate drift, not code — the BFF `serviceRestTemplate` interceptor unconditionally sets `X-Access-Mode: INTERNAL` (`services/experience-bff/.../config/ServiceClientConfig.java:331`), exactly what `shared-core` `TrustContextFilter` checks; remediation is a clean no-stale deploy + live re-probe.
- **G29** Madi: no purpose-built crossmatch/serology bench screen (reachable via order screens); forecast/wastage/redistribution UI depth to confirm.
- **G30** VARAPI: PIC engine orphan — Varapi's own `PractitionerInChargeController` (11 eps) + `pic_eligibility_snapshot` unused; the PIC UI targets Tuso's facility-registry PIC. Needs an explicit SoR decision (wire or retire). Note: credential-verification-service (8094) is a payments-rail VC store for MUSHEX, NOT a duplicate of Varapi licensure verification.
- **G31** Khuluma: external-delivery SoR overlap — owns its own SMS/WhatsApp/EMAIL/USSD channel adapters (honest `SKIPPED_NOT_CONFIGURED` degradation, never fake sends) instead of delegating to notification-service:8200; registry forbids duplicate delivery-SoR. Product decision needed.
- **G32** Fundo: notification provider defaults to non-delivering `STUB` (honestly marks `RECORDED`); production must set `learning.notifications.dispatch.provider=NOTIFICATION_SERVICE` + base URL.
- **G33** Telemedicine partials: Stage-2 patient/visit-summary panels are static placeholders; attachments free-text (no uploader); only 2 of 7 documented worklist types surfaced.
- **G34** Hygiene / doc drift: registry dispositions stale (Madi domain `platform-ops` should be clinical/care-delivery; PACS `frontend_wiring_status` under-claimed); `docs/registry/mock-and-stub-register.md` + `backend-to-frontend-wiring-map.md` still claim teleconsult media "intentionally unavailable/501" though RTC token minting is live.
- **G35** Prod-config prerequisites (not code gaps): LiveKit apiKey/secret (else token mint fails), rtc egress enabled for recording, per-tenant khuluma channel adapters, Fundo notification provider (G32).

## 5. Proposed remediation waves

Ordered by journey-blocker severity, then ROI. Each wave follows the standard delivery discipline (atomic conventional commits, BFF+UI wiring completeness, authz/audit/tests, runtime proof where feasible).

- **Wave R1 — Journey unblockers (small, high-value wiring)**: G1 inpatient admission/bed-assign/ward-round UI wiring (hooks already exist) + an honest e2e that *creates* an admission; G5 telemedicine Stage-4 specialist accept surface. *Unblocks two flagship journeys with near-zero engine work.*
- **Wave R2 — VARAPI surfacing** (G7–G11, the Vashandi playbook) + G30 PIC SoR decision.
- **Wave R3 — Coverage subsidy**: G3 reconcile duplicate model → G2 wire BFF/UI → G15 preauth decision queue.
- **Wave R4 — Khuluma completion**: G6 escalation→roster paging engine + G13 broadcast BFF/UI + G31 delivery-SoR decision.
- **Wave R5 — Teleconsult completion**: G17 in-session orders/Rx, G18 scheduling/auto-match, G33 package richness.
- **Wave R6 — Indawo geography build** (G4 + G21; coordinate the Ndila/Tuso geo seam).
- **Wave R7 — PCT + VITO surfacing** (G12 sorting desk, G16 death chain; G14 relationships UI, G28 screens).
- **Wave R8 — Imaging** (G19 MWL build-vs-defer decision, G22 reporting UI, G20 Live recording writeback).
- **Wave R9 — Mobile production path**: CI `mobile-e2e-maestro` APK artifacts + Maestro evidence (G23), production keystore/EAS creds (G24), auth-model trust review (G25), preview DNS+TLS rebuild (G26).
- **Wave R10 — Hygiene + prod config** (G29, G32, G34, G35) folded into the pre-deploy gate.
- **Production landing gate** (after waves): quality gates → clean no-stale fullboot deploy (also resolves the VITO MPI 403 estate drift) → live re-probe of the four traced journeys + golden rigs.

## 6. Audit provenance

| Audit lane | Coverage |
|---|---|
| Registry/identity | VARAPI (33 controllers/184 eps inventoried), VITO (24 controllers/113 eps), credential-verification boundary |
| Care/place | Indawo (27 tables, orphan-schema proof), PCT (42 tables/24 controllers), Telemedicine pipeline (PCT→RTC→COSTA) |
| Diagnostics/finance | PACS adapter + OROS report lifecycle, Madi (44 tables), Coverage (subsidy trace hand-verified) |
| Experience/comms | Fundo (prior gaps re-verified fixed), Khuluma (escalation/broadcast disproving searches), Impilo Live (LiveKit token + recording event trace) |
| Mobile | Both Expo apps, parity matrix vs 763 web routes, APK toolchain check on this host, unit suites executed (41 files/184 + 60 files/221, all green) |
| Journeys | 7-stage teleconsultation spec trace, walk-in OPD, admission→discharge, referral loop — each stage checked for real UI invocation, with e2e/rig cross-checks |
