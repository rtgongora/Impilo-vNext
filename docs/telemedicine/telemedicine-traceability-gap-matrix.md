# Telemedicine Implementation Traceability and Gap Matrix

Companion to the [National Telemedicine & Virtual Care Specification](NATIONAL_TELEMEDICINE_VIRTUAL_CARE_SPECIFICATION.md) (§30). Living document — update on every telemedicine change.

**Status legend:** `LIVE` implemented + runtime-proven · `BUILT` implemented, code-verified · `NOT-SURFACED` implemented but not exposed · `FE-ONLY` · `BE-ONLY` · `MOCKED` · `STUBBED` · `PARTIAL` partially persistent/complete · `UNDOC` undocumented behaviour · `INCONSISTENT` conflicts with spec · `ABSENT` · `BLOCKED-INFRA` · `NEEDS-DECISION`

**Evidence date:** 2026-07-21, canonical branch @ `4376782c5`. Runtime proofs reference `scripts/runtime-proof/virtual-care-journeys.sh` (J-VC-1..3), `scripts/e2e/session-recording-proof.sh`, `e2e/telehealth-patient-flow.spec.ts`, `e2e/session-media-core.spec.ts`.

## 1. Requirement traceability

| # | Requirement | Source | Canonical decision | Owning service | Frontend route | Mobile | API | DB table | Event | FHIR | Tests | Status | Gap ref |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| R1 | Durable case before any media room | Spec §Stage1 | BFF ordering enforces | pct | `/telemedicine/new`, `/citizen/virtual-care/request` | both apps | `POST /teleconsult/sessions` | `pct_referrals` | `referral_created` | — | J-VC-1 | **LIVE** | — |
| R2 | Case state machine with guarded transitions | Spec §11 | Canonical model §11.3 | pct | — | — | lifecycle endpoints | `pct_referrals.status` (string) | lifecycle topic | — | none for guards | **PARTIAL/INCONSISTENT** — statuses set, not gated; guarded machine only in inactive legacy profile | TM-G1 |
| R3 | Full referral package (structured section set) | Brief + Spec §Stage2 | Active-spine enrichment | pct | composer | partial | `PUT …/referral` | `pct_referrals` fields | `referral_updated` | Composition/QR target | compose e2e | **PARTIAL** — reason/summary/urgency/specialty/attachments live; full section set only in legacy model | TM-G2 |
| R4 | Consent gate before media | Spec §17 | MVUMO journey + hard gate default-on | mvumo/tshepo-consent/pct/rtc | consent UI in composer + citizen request | citizen app | `POST …/consent`; token path | `mvumo.*`; pointers on case | `consent_updated` | Consent | J-VC-3 (422 no-row) | **PARTIAL** — BFF blocks only DENIED/REVOKED/REFUSED; hard `consentReference` gates flag-gated | TM-G3 |
| R5 | Waiting room: no token before admit | Session suite | rtc lobby is truth | rtc-gateway | `/my/telehealth/[id]`, admit control | both apps | waiting-room/admit/deny | `rtc.session_participants` | `participant.waiting/admitted/denied.v1` | — | telehealth-patient-flow (negative proof) | **LIVE** | — |
| R6 | Role-scoped, template-enforced tokens | Template contract | telemedicine.json | rtc-gateway | — | — | `…/participants/token` | sessions/participants | — | — | recording-proof role refusal | **LIVE** | — |
| R7 | Real A/V web + mobile parity | Spec §Stage5 | One session engine | rtc/LiveKit | `AdaptiveSessionRoom` | `AdaptiveSessionRoomNative` | — | — | rtc events | — | session-media-core | **LIVE** (web proven; mobile built) | — |
| R8 | Recording: PROVIDER-only, consent-required, artifact→PCT | Template | Enforced | rtc/document | RecordingBadge | — | recording start/stop | `rtc.recordings` | `recording.*.v1` | DocumentReference | session-recording-proof | **LIVE** | — |
| R9 | Structured clinical response | Spec §Stage6 | `respond-structured` spine + enrichment | pct | console | partial | `…/respond-structured` | `structured_response` jsonb | `response_submitted` | Composition target | J-VC-1 | **PARTIAL** — 4-field spine; full package + validation absent | TM-G9 |
| R10 | Completion note mandatory ("no closure without audit") | Workflow edition | Enforced 400 | pct | completion form | — | `…/complete` | `completion_payload` | `.completed` + `TELECONSULT_COMPLETED` | DiagnosticReport (thin) | J-VC-1 | **LIVE** | TM-G11 (enrich) |
| R11 | Billing on completion | Pipeline doc | COSTA draft→approve→finalize | costa | — | — | value trigger | costa charge | `clinical.teleconsult.value` | — | J-VC-1 (charge row assert) | **LIVE** | — |
| R12 | Orders enter OROS with teleconsult provenance | Spec §21 | TELECONSULT RequestSource + case link | oros/pct | `TeleconsultOrdersSection` | — | generic order APIs only | — | — | ServiceRequest/MedicationRequest | none | **INCONSISTENT** — UI places generic orders; no teleconsult source; controller unwired | TM-G4 |
| R13 | Routing to practitioner/workspace/facility-service/pool with SoR validation | Operating model | VARAPI/TUSO validation | pct/bff | routing picker, worklist | — | `…/route` | routing cols (V020) | `.routed` | — | J-VC-1 | **LIVE** | — |
| R14 | On-call / unit / national-pool routing + duty rosters | Both sources | Honest 501 until directory backend | vashandi/tuso/pct | routing page (fail-closed) | — | 501 | — | — | — | — | **ABSENT** | TM-G6 |
| R15 | Provider authority hard-gate at accept | Spec §Stage4 J | PDP per-action | tshepo/varapi/vashandi | worklist | provider app | `…/accept` | — | — | — | — | **PARTIAL** — soft duty gate (onDuty snapshot) only | TM-G5 |
| R16 | Decline/reassign never strands a case | Workflow edition | Exception worklists | pct | worklist | — | `…/decline` (reason req.) | `responses[]` | lifecycle | — | UI enforces reason | **PARTIAL** — reason enforced; exception worklists absent | TM-G8 |
| R17 | Scheduling + reminders | Spec §20 | booking-service link | booking/notification | scheduled queue | — | schedule path | `booking.appointment` + V032 link | `.scheduled` | Appointment target | — | **PARTIAL** — link live; **scheduled reminders impossible** (`NotifyRequest` lacks `scheduledAt`) | TM-G14 |
| R18 | Post-response execution states (awaiting-*/follow-up-due) | Both sources | Target states §11 | pct | worklists | — | — | — | — | Task target | — | **ABSENT** | TM-G8/TM-B7 |
| R19 | Follow-up recurrent loop + linked cases | Brief D6 | Linked-case chain | pct | — | — | — | origin refs | `.followup_required` | EpisodeOfCare target | — | **PARTIAL** | TM-B7 |
| R20 | Reopen/cancel/expire/abandon/error states | Spec §11 | Additive target states | pct | — | — | — | — | — | — | — | **ABSENT** | TM-B1 |
| R21 | Citizen request lane (fail-closed VH availability) | Operating model | REQUESTABLE-only CTA | pct/bff | `/citizen/virtual-care/*`, `/discover/virtual-care` | citizen app | `/virtual-care/requests` | `pct_referrals` (POOL routing) | lifecycle | — | J-VC-1/2 | **LIVE** | — |
| R22 | Virtual-hospital backend substrate | Operating model HO-2 | TUSO-adjacent registry (not fake facilities) | tuso (proposed) | VH pages (config-only) | — | — | — | — | Organization/HealthcareService | — | **ABSENT** (config substrate only) | TM-B16 |
| R23 | MDT / emergency-advisory / diagnostics-review session modes | Both sources | New templates (W0 lease) + policy | session-templates/tshepo | session-modes page (honest) | — | — | — | — | CareTeam target | — | **ABSENT** (config-only; HO-3/HO-4) | TM-B15 |
| R24 | Identity: Impilo ID citizen-facing everywhere | Identity contract (wins) | Copy migration | ui | auth/register/find-care + all citizen surfaces | both apps | — | — | — | — | — | **INCONSISTENT** — pervasive "Health ID" citizen copy | TM-G15 |
| R25 | CPID-only SHR writes, PII prevention | Identity contract | Enforced interceptor | butano/fhir-gateway | — | — | gateway forward | — | — | all | conformance pack §16 | **BUILT** (contract AMBER until pack green) | TM-G19 |
| R26 | Guardianship/caregiver delegation | Identity doctrine §7 | MVUMO Relationship (not RelatedPerson) | mvumo/tshepo | consent center | — | delegation APIs | `mvumo.delegation_relationship` | — | RelatedPerson export optional | CJ14/CJ15 live | **LIVE** | OD-8 |
| R27 | Nompilo guidance at telemedicine stages | Spec §P sections | Route-bound guidance + in-consult panel | guidance-service | global bar + `/guidance`; no in-consult panel | — | `/nompilo/context` | seeded guidance | — | — | ai-guidance e2e | **PARTIAL** — no telemedicine-specific in-consult guidance | TM-B10 |
| R28 | Khuluma journey orchestration for teleconsult comms | Doctrine | OD-3 decision needed | khuluma/notification | `/my/comms` | both apps | BFF→notification direct today | khuluma tables | lifecycle consumer | Communication target | comms e2e | **NEEDS-DECISION** | TM-G13/OD-3 |
| R29 | Session chat persistence for clinically relevant content | Spec §Stage5 | Persist to case/Khuluma | khuluma/pct | session chat | — | — | — | — | Communication | — | **ABSENT** (template `SESSION` = ephemeral) | TM-G10 |
| R30 | Offline case/draft/queue + store-and-forward integrity | Both sources | §19 mechanisms | pct/clients | — | — | — | — | — | — | — | **ABSENT** (async spine live; offline capture absent) | TM-B5 |
| R31 | Conflict/multi-writer UX | Spec §18 | Side-by-side reconciliation | pct/shr/ui | — | — | — | — | — | Provenance | — | **ABSENT** | TM-B6 |
| R32 | Emergency escalation path from any stage | Both sources | §23 table | daidzai/nhume/ndila/pct | emergency actions | — | — | — | — | — | — | **PARTIAL** — emergency entry exists; ESCALATED/TRANSFERRED states + Daidzai wiring absent | TM-B12 |
| R33 | Ops command + per-session failure diagnostics | Operating model | Query API over rtc events | rtc/analytics | operations page | — | `/ops/*` | `rtc.session_events` | — | — | — | **PARTIAL** | TM-B19 |
| R34 | Recording→learning artefact (governed) | Operating model | W0 W1 TODO; separate consent | live/fundo | — | — | — | — | `recording.available.v1` | — | — | **ABSENT** | TM-B16 |
| R35 | TURN/TLS public relay for restrictive networks | Media plan | SNI passthrough on 443 | infra | — | — | — | — | — | — | on-estate relay proof | **LIVE** (estate) — off-network verification pending DNS-era mobile test | — |
| R36 | Specialty coded terminology for routing | Spec §Stage3 | ZIBO CodeSystem | zibo | — | — | — | `zibo_artifacts` | — | ValueSet | — | **ABSENT** | TM-G7 |
| R37 | Event naming/versioning consistency | Spec §16 | `.v1` suffix everywhere | pct | — | — | — | outbox | mixed (`.v1` only on rtc) | — | — | **PARTIAL** | TM-G12 |
| R38 | Local draft persistence + crash recovery | Workflow edition | Client durable drafts | ui | composer | — | — | — | — | — | — | **ABSENT** (server drafts only) | TM-G17 |
| R39 | Upload malware scanning + controls | Spec §Stage2 | Scan at document-service seam | document | attachments | — | — | — | — | — | — | **ABSENT** | TM-G18 |
| R40 | No mocks/dead buttons on telemedicine surfaces | Constraints | Honest-gap doctrine + `test:no-stubs` | ui | all | all | — | — | — | — | no-stubs gate | **LIVE** (verified sweep: no fake data/dead buttons; honest deferred states) | — |

## 2. Named gaps (with remediation pointers)

| Gap | Description | Priority | Backlog |
|---|---|---|---|
| TM-G1 | Case status is an ungated string; guarded transitions only in inactive legacy service. Risk: illegal transitions, silent skips. | **P0** | TM-B1 |
| TM-G2 | Full structured referral-package section set not on active spine (legacy-only shape); receiver context panels thin. | P1 | TM-B2 |
| TM-G3 | Consent hard-gate (consentReference for media) flag-gated, BFF blocks only explicit denial; submit-without-consent guard absent on active spine. | **P0** | TM-B8 |
| TM-G4 | Orders lack teleconsult provenance: no `TELECONSULT` RequestSource, teleconsult controller unwired to OROS; UI orders ride generic paths. | **P0** | TM-B7 |
| TM-G5 | Acceptance authority is a soft duty snapshot (`onDuty` true/false/UNKNOWN), not a PDP hard gate on licence/scope/assignment. | **P0** | TM-B8 |
| TM-G6 | ON_CALL/UNIT/NATIONAL_POOL routing 501; no duty/pool directory backend (Vashandi rosters facility-scoped; Khuluma on-call presence-only). | P1 | TM-B3 |
| TM-G7 | No ZIBO specialty CodeSystem; specialty is free text on the case. | P1 | TM-B3 |
| TM-G8 | No post-response execution states/worklists (awaiting-*, follow-up-due, exception queues); RESPONDED→COMPLETED jump loses execution tracking. | **P0** | TM-B1/B7 |
| TM-G9 | Response package = 4-field spine; full validation set (authority, terminology, allergy/dose conflicts) absent. | P1 | TM-B6 |
| TM-G10 | Session chat ephemeral (`persistence: SESSION`); clinically relevant chat lost on room close. | P1 | TM-B5 |
| TM-G11 | FHIR completion write is a thin DiagnosticReport stub (fixed conclusion string); no Encounter/Composition projection. | P1 | TM-B6 |
| TM-G12 | Lifecycle events unversioned (rtc uses `.v1`; `telemedicine.session.*` does not). | P2 | TM-B20 |
| TM-G13 | Teleconsult notifications bypass Khuluma orchestration (BFF→notification-service direct). | P2 (decision OD-3) | TM-B9 |
| TM-G14 | Scheduled reminders impossible — `NotifyRequest` has no `scheduledAt`. | P1 | TM-B9 |
| TM-G15 | Citizen-facing "Health ID" copy violates ratified "Impilo ID" naming. | P1 | TM-B13 |
| TM-G16 | No duplicate-case detection at entry. | P2 | TM-B2 |
| TM-G17 | No local durable drafts (browser/device-loss recovery). | P2 | TM-B11 |
| TM-G18 | No malware scanning / full upload controls at attachment seam. | P1 | TM-B2 |
| TM-G19 | Identity contract verdict AMBER pending conformance pack green. | P1 | (identity programme) |

## 3. Anti-pattern sweep results (commissioned checklist)

| Checked for | Finding |
|---|---|
| Mock data / hardcoded queues / static referral lists | **None found** on telemedicine surfaces — all lists BFF-fed |
| Non-functional controls / fake call buttons | **None** — media controls honestly disabled until governed token ("Waiting for governed RTC media"); historical dead mic button already removed |
| Placeholder video rooms | None — rooms provisioned server-side only, case-first |
| Non-persistent drafts | Server drafts persist; **local** durable drafts absent (TM-G17) |
| Missing policy checks | Accept-time hard authority gate (TM-G5); consent hard-gate default (TM-G3); identity-visibility levels presentational (HO-4) |
| Missing audit | Session plane CLINICAL-depth ✔; case-audit read API absent |
| Missing mobile parity | TELEMEDICINE parity FULL (native apps); some ops/worklist surfaces web-only (acceptable per parity matrix) |
| Disconnected clinical forms | In-session full encounter-note surface absent (TM-G9/TM-B6) |
| Responses not writing to SHR | Thin DiagnosticReport only (TM-G11) — honest `clinicalSummaryWritten` flag |
| Orders not entering OROS | Confirmed for teleconsult provenance (TM-G4) |
| Notifications not entering Khuluma | Confirmed — direct notification-service path (TM-G13/OD-3) |
| Rooms without durable cases | None — `sessionId==referralId` invariant holds |
| Cases closing without completion notes | Impossible (400 enforced, runtime-proven) |
| Status values without transition guards | **Confirmed** on active spine (TM-G1) |
| Tests proving only HTTP 200 | Runtime proofs assert DB rows, charge rows, event outbox, negative paths — pattern is healthy; state-guard tests missing (follows TM-B1) |
