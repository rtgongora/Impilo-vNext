# Telemedicine Pack — Implementation Traceability and Gap Matrix (Volumes I & II)

Companion to [Volume I — National Telemedicine & Virtual Care Specification](NATIONAL_TELEMEDICINE_VIRTUAL_CARE_SPECIFICATION.md) (§30) and [Volume II — National e-Orders, Fulfilment & Telemonitoring Specification](NATIONAL_EORDERS_FULFILMENT_TELEMONITORING_SPECIFICATION.md) (§25). Living document — update on every change in either domain. Row namespace is single and monotonic (R1.. across both volumes); gap refs are `TM-G*` (Volume I) and `OF-G*` (Volume II).

**Status legend:** `LIVE` implemented + runtime-proven · `BUILT` implemented, code-verified · `NOT-SURFACED` implemented but not exposed · `FE-ONLY` · `BE-ONLY` · `MOCKED` · `STUBBED` · `PARTIAL` partially persistent/complete · `UNDOC` undocumented behaviour · `INCONSISTENT` conflicts with spec · `ABSENT` · `BLOCKED-INFRA` · `NEEDS-DECISION`

**Evidence dates:** Volume I rows: 2026-07-21 @ `4376782c5` (subsequent TM-B1..B20 implementation updated affected rows through 2026-07-23). Volume II rows (§4): 2026-07-23, canonical branch @ `6074bcbc4`, from three code-sweep reports (orders/prescription/dispense · marketplace/coverage/payment/logistics · IoT/monitoring/community). Runtime proofs reference `scripts/runtime-proof/virtual-care-journeys.sh` (J-VC-1..3), `scripts/runtime-proof/teleconsult-hardening-journeys.sh` (J-TH-1..11), `scripts/e2e/session-recording-proof.sh`, `e2e/telehealth-patient-flow.spec.ts`, `e2e/session-media-core.spec.ts`.

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
| R22 | Virtual-hospital backend substrate | Operating model HO-2 | TUSO-adjacent registry (not fake facilities) | tuso (proposed) | VH pages (config-only) | — | — | — | — | Organization/HealthcareService | — | **BUILT** (tuso V022-23 sovereign registry + fail-closed activation + pct pool materialisation + vashandi duty read-backs; proven J-VC-4..7) | TM-B16 |
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
| R34 | Recording→learning artefact (governed) | Operating model | W0 W1 TODO; separate consent | live/fundo | — | — | — | — | `recording.available.v1` | — | — | **PARTIAL** (live-event path BUILT; teaching-consent gate landed SHADOW — live V005 + mvumo V008 teaching_recording template; ENFORCE flip pending directive capture at scheduling; teleconsult-recording lane still absent) | TM-B16 |
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
| OF-G1 | No order/prescription-level clinician signing — `placed_by`/`prescribed_by` are bare strings; edge-authz only. | **P0** | OF-B2 |
| OF-G2 | No order amendment/replacement/versioning — in-place mutation or nothing; result-level amend only. | **P0** | OF-B1 |
| OF-G3 | Prescription aggregate thin + orphaned — flat single-med `rx_prescriptions`, no items/repeats-ceiling/validity/controlled flag, unlinked to the real dispense pipeline. | **P0** | OF-B2 |
| OF-G4 | Prescription↔dispense claim linkage absent — "refill" stamps a timestamp; "dispense" flips status with zero stock effect; no repeats decrement. | **P0** | OF-B12 |
| OF-G5 | Medication safety validation partial — hardcoded rules engine, substring allergy matching, PCT allergy SoR absent (checks degrade to WARNING), no licensed interaction/dose database. | **P0** | OF-B3 |
| OF-G6 | No e-prescription anti-fraud token — no QR/claim/verification machinery; nothing prevents reuse or forgery beyond edge auth. | **P0** | OF-B2 |
| OF-G7 | Controlled-medicine workflow gating absent — DURA controlled register exists (V013) but nothing consumes it; no restricted routing or second-factor handover. | **P0** | OF-B29 |
| OF-G8 | Request-for-offer machinery absent entirely (requests, invitations, PII-minimised publication) — grep zero across msika/coverage. | **P0** | OF-B4 |
| OF-G9 | Offer lifecycle absent — no offer entity, TTL, revalidation-at-acceptance or race handling. | **P0** | OF-B6 |
| OF-G10 | Patient offer-comparison and selection experience absent. | **P1** | OF-B7 |
| OF-G11 | No per-offer/per-acceptance provider eligibility revalidation loop (onboarding + risk-friction gates only). | **P1** | OF-B5 |
| OF-G12 | Marketplace stock reservation disconnected from DURA — local placeholder rows, no-op inventory consumer, no availability check at checkout; double-sell risk. | **P0** | OF-B11 |
| OF-G13 | Financial resolution not wired into selection/fulfilment — liability engine and wallet escrow both BUILT but neither is called from any offer/checkout/handover flow (escrow half rides OF-B10 acceptance). | **P1** | OF-B9 |
| OF-G14 | No payer formulary and no ZIBO medicine-registry artifact type — drug coverage expressible only as raw benefit definitions. | **P1** | OF-B8 |
| OF-G15 | Fulfilment↔delivery write-back is best-effort — Nhume callback failures swallowed to warnings; no retry/escalation contract. | **P1** | OF-B17 |
| OF-G16 | No per-patient remote-monitoring engine — plans/personalised thresholds/alert lifecycle absent; monitoring observations reach the SHR via three ad-hoc writer paths. | **P1** | OF-B22 |
| OF-G17 | Device trust scoring is a hardcoded heuristic (static 95/80/55/25 + fixed operation lists); no real attestation. | **P2** | OF-B25 |
| OF-G18 | No clinical device assignment (patient↔device↔plan) and no calibration/quarantine gating of readings — `owner_health_id` records possession only. | **P1** | OF-B24 |
| OF-G19 | FHIR gateway lacks MedicationDispense/SupplyRequest/DeviceRequest/SupplyDelivery; no dispense projection exists anywhere. | **P1** | OF-B12 |
| OF-G20 | No end-to-end order-to-outcome runtime proof (order→offer→coverage→payment→dispense→delivery→SHR); only per-domain proofs. | **P1** | OF-B30 |
| OF-G21 | Drone/alternative transport modes are CONFIG-ONLY (`nhume_autonomous_missions` table, zero operational evidence) — must remain a governed capability, never claimed live. | **P2** | OF-B20 |

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

## 4. Volume II requirement traceability (e-orders, fulfilment, telemonitoring, IoT)

Evidence: 2026-07-23 code sweeps @ `6074bcbc4`. Sources column cites [Volume II](NATIONAL_EORDERS_FULFILMENT_TELEMONITORING_SPECIFICATION.md) sections.

| # | Requirement | Source | Canonical decision | Owning service | Frontend route | Mobile | API | DB table | Event | FHIR | Tests | Status | Gap ref |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| R41 | Clinical order aggregate w/ lines, guarded lifecycle, provenance | Vol II §8A | OROS = order spine (13-status machine; TELECONSULT source + duplicate guard) | oros | order composers | provider app | `/v1/orders` | `oros_orders`, `oros_order_items` | `oros.order.*` | ServiceRequest (diagnostics→butano) | OrderStateMachine tests | **BUILT** (spine LIVE for lab/imaging/pharmacy/blood) | — |
| R42 | Order-level clinician signing/authorisation | Vol II §8B | Detached JWS via tshepo-keys, stored with the signed version | oros | sign step | provider app | sign endpoint (new) | signature cols (new) | `oros.prescription.signed.v1` (new) | Provenance | — | **ABSENT** (`placed_by` plain string; edge-authz only) | OF-G1 |
| R43 | Order amendment/replacement as immutable versions | Vol II §8A/§9.1 | Amendment = new version; no in-place mutation | oros | — | — | amend endpoint (new) | version table (new) | `oros.order.amended` (exists for results only) | — | — | **ABSENT** (result-level amend only; no order versioning) | OF-G2 |
| R44 | E-prescription aggregate (items, repeats ceiling, validity, controlled flag, indication) | Vol II §7A/§8A | Prescription lives in OROS as parent authorisation; PHARMACY orders = dispense episodes | oros (target); pharmacy (legacy) | prescribe pane | provider app | `/v1/prescriptions` (legacy) | `rx_prescriptions` (legacy, flat single-med) → `oros_prescriptions*` (new) | `pharmacy.prescription.*` (legacy) | MedicationRequest | — | **PARTIAL/INCONSISTENT** (legacy record has no items/repeats/expiry/controlled flag) | OF-G3 |
| R45 | Prescription ↔ dispense linkage (claim decrements repeats) | Vol II §8I–J | Dispense episode carries `prescription_version_id`; claim is atomic, counter server-side | oros + pharmacy | — | — | claim endpoint (new) | link col (new) | `oros.prescription.claimed.v1` (new) | — | — | **ABSENT** (two unlinked silos; "refill" = timestamp stamp; `POST /{id}/dispense` is a status flip with zero stock effect) | OF-G4 |
| R46 | Dispensing workflow: batch/expiry, FEFO, partial fill, substitution, stock ledger, pickup proof | Vol II §8J | pharmacy-service = dispensing SoR, driven by OROS PHARMACY orders via Kafka | pharmacy | dispense worklist | — | `/v1/dispense-orders/*` | `rx_dispense_orders/items`, `rx_substitution_rules`, `rx_stock_movements`, `rx_pickup_proofs` | `pharmacy.dispense.*`, `pharmacy.stock.movement.*` | MedicationDispense (projection ABSENT) | DispenseEngine tests | **BUILT** (counselling capture absent) | — |
| R47 | Medication safety validation (allergy, interaction, dose, pregnancy/renal) | Vol II §8A | Layered: ClinicalRulesEngine + teleconsult validator; licensed interaction DB is a procurement decision | clinical-knowledge + experience-bff + pct | warnings pane | — | validation seam | — | — | — | validator unit tests | **PARTIAL** (hardcoded deterministic rules; substring allergy match; PCT allergy SoR ABSENT so checks degrade honestly to WARNING) | OF-G5 |
| R48 | E-prescription anti-fraud token (signed reference, server retrieval, single-active claim) | Vol II §13 | Opaque token bound to PrescriptionVersionId; no clinical payload in QR; offline = integrity-only | oros + tshepo-keys | pickup code | citizen app | token endpoints (new) | token table (new) | claim events (new) | — | — | **ABSENT** (no Rx QR/token machinery anywhere) | OF-G6 |
| R49 | Controlled-medicine separately governed workflow | Vol II §13 | Never open-broadcast; second-factor handover; mandatory DURA controlled-register write | oros + pharmacy + inventory | — | — | — | `dura` controlled register (inventory V013) | — | — | — | **PARTIAL** (register table BUILT; no workflow gating consumes it) | OF-G7 |
| R50 | Regulated marketplace catalogue, listings, storefronts, vendor onboarding, risk friction | Vol II §11 | Msika core = catalogue/listing/eligibility plane; msika-flow = transaction plane | msika + msika-flow | storefront/listing routes | citizen app | msika APIs | `msika_catalog_items/listings/storefronts/offerings`, `mf_vendor_profiles` | msika events | — | completion-wave probes | **LIVE** (completion wave 25/25; prices server-resolved; real MusheX/Nhume seams) | — |
| R51 | Request-for-offer machinery (MarketplaceRequest → invitations → offers) | Vol II §11 | Net-new in msika-flow; read-only reference to the OROS order | msika-flow | — | — | RFO endpoints (new) | `mf_marketplace_requests/…` (new) | `msika.flow.request.*.v1` (new) | — | — | **ABSENT** (grep zero for RFQ/bid/quote across msika/coverage) | OF-G8 |
| R52 | Offer lifecycle w/ TTL, revalidation at acceptance, race handling | Vol II §9.4/§8I | Offer states + revalidate-on-select; reservation TTL = offer TTL | msika-flow | — | — | (new) | `mf_fulfilment_offers` (new) | (new) | — | — | **ABSENT** | OF-G9 |
| R53 | Patient offer-comparison and selection experience (fair, explained ranking) | Vol II §8F/§20 | Comparison surface with ranked-because labels; no dark patterns | ui + experience-bff | offer comparison (new) | citizen app | (new) | — | — | — | — | **ABSENT** | OF-G10 |
| R54 | Provider eligibility revalidation at offer/acceptance time | Vol II §8D | VARAPI/TUSO/network/capability checks at offer AND commit | msika-flow + varapi + tuso | — | — | — | — | — | — | — | **PARTIAL** (onboarding + risk-friction gates BUILT; no per-offer revalidation loop) | OF-G11 |
| R55 | PII-minimised request publication to competing providers | Vol II §11 | Invitations carry ZIBO-coded lines + coarse ndila zone + capability flags only; identity post-selection | msika-flow | — | — | — | — | — | — | — | **ABSENT** (design settled; rides RFO build) | OF-G8 |
| R56 | Marketplace/pharmacy stock reservation against the sovereign ledger | Vol II §8I | DURA `inv_stock_reservations` is the single ledger; `mf_reservations` demoted to projection | inventory + msika-flow | — | — | reservation seam | `inv_stock_reservations` (BUILT), `mf_reservations` (scaffold) | `inventory.reservation.*.v1` (new) | — | — | **MOCKED at marketplace** (local placeholder rows; `InventoryEventConsumer` is a no-op logger; no inventory client) / **BUILT at DURA** | OF-G12 |
| R57 | Stock truth grades (reported/available/reserved/prepared/consumed) | Vol II §8E | Vendor attestation flagged unverified; DURA-derived availability preferred | inventory + msika-flow | offer stock badges | — | — | `inv_batch_lots` | — | — | — | **PARTIAL** (DURA available=on-hand−reserved BUILT; attestation grading ABSENT) | OF-G12 |
| R58 | Coverage eligibility, benefits, limits, accumulators | Vol II §10 | Ruvimbo = coverage SoR; reservation-aware accumulators | coverage | coverage panes | citizen app | eligibility v2 APIs | `cv_member_coverage`, `cv_benefit_*` | coverage events | Coverage | wave proofs V015-V018 | **BUILT** (waves proven live) | — |
| R59 | Prior-authorisation lifecycle incl. appeals | Vol II §10 | 14-status `cv_authorisations` + line-level; appeals table | coverage | auth worklists | — | auth APIs | `cv_authorisations(_lines)`, `cv_appeals` | — | Claim/ClaimResponse family | — | **BUILT** | — |
| R60 | Per-offer patient-liability calculation in the selection flow | Vol II §8G | COSTA supplies charge; Ruvimbo `cv_liability_estimates` computes shortfall per offer | coverage + costa + msika-flow | offer comparison | — | liability API (exists) | `cv_liability_estimates` | — | — | — | **PARTIAL** (engine BUILT; not wired into any offer/checkout flow) | OF-G13 |
| R61 | Claims, line adjudication, COB, remittance | Vol II §10 | 21-status claims; COB waterfall persisted | coverage + mushex | — | — | claim APIs | `cv_claims(_lines)`, `cv_cob_decisions` | — | Claim family | — | **BUILT** | — |
| R62 | Payer formulary (tiers, PA-required flags) | Vol II §10 | Three-layer formulary: ZIBO national registry · coverage payer formulary · pharmacy facility list | coverage + zibo + pharmacy | — | — | — | `rx_formulary` (facility, BUILT); payer + national layers (new) | — | — | — | **ABSENT** (no payer formulary entity; no ZIBO medicine artifact type) | OF-G14 |
| R63 | Payment intents, refunds, settlement, reconciliation | Vol II §8H | MusheX canonical; intent→PAID + refunds; NO two-phase capture (settled: not built) | mushex | payment panes | citizen app | mushex APIs | `mushex_payment_intents/…` | `mushex.payment.status.changed` | PaymentNotice | money-stack proofs | **LIVE** | — |
| R64 | Escrow hold-until-handover for marketplace orders | Vol II §8H | mushe-wallet escrow released on Nhume proof-of-delivery | mushe-wallet + msika-flow + nhume | — | — | escrow seam | wallet escrow tables (BUILT) | — | — | — | **PARTIAL** (escrow machinery BUILT for campaigns; not wired to fulfilment PoD) | OF-G13 |
| R65 | Delivery orchestration, custody chain, proof-of-delivery, cold chain | Vol II §12 | Nhume = logistics SoR (24-status machine, multi-cargo, temperature custody events) | nhume | dispatch/tracking routes | courier surfaces | `/internal/v1/nhume/deliveries` | `nhume_delivery_*`, `nhume_chain_of_custody_events`, `nhume_delivery_proofs` | nhume events (legacy unprefixed) | — | completion-wave report | **BUILT** (marketplace + telemedicine A5 seams LIVE) | — |
| R66 | Fulfilment ↔ delivery status write-back | Vol II §8L | Nhume callback updates fulfilment; failures never silent | nhume + msika-flow | — | — | internal callback | `mf_delivery_plans` | — | — | — | **PARTIAL** (callback BUILT but best-effort; client swallows failures to warnings) | OF-G15 |
| R67 | Drone / alternative transport modes | Vol II §8M/§12 | Governed capability matrix per mode; enabled by geography+policy; never claimed operational without evidence | nhume + ndila | — | — | — | `nhume_autonomous_missions` (table exists) | — | — | — | **CONFIG-ONLY** (no operational evidence) | OF-G21 |
| R68 | Per-patient remote-monitoring engine (plans, personalised thresholds, alert lifecycle) | Vol II §14 | New clinical-plane telemonitoring-service (ownership-exhaustion proof in Vol II §6); initiated via OROS spine | telemonitoring (new) | monitoring desks (new) | CHW/citizen apps | (new) | (new) | `telemonitoring.plan/alert.*.v1` (new) | CarePlan/Goal/Observation | — | **ABSENT** (surveillance=population only; inpatient EWS=ward, score client-supplied; wellness ingest has no alerting) | OF-G16 |
| R69 | Device digital identity + telemetry ingestion (validated, DLQ, provenance) | Vol II §15 | iot-ingestion = connectivity/identity truth; separate telemetry bus | iot-ingestion | device ops (new) | — | `/internal/v1/telemetry/*`, device registry APIs | `iot.device_registry`, `iot_telemetry_*` | `impilo.iot.telemetry.reading.ingested.v1` | Device/DeviceMetric | ingest tests | **BUILT** (trust scoring heuristic hardcoded) | OF-G17 |
| R70 | Clinical device assignment (patient↔device↔plan) + calibration/quarantine gating of readings | Vol II §15 | telemonitoring owns DeviceAssignmentId; asset-registry calibration projected; readings stamped, never dropped | telemonitoring (new) + asset-registry + iot-ingestion | — | CHW app | (new) | `asr_equipment` (BUILT), assignment (new) | — | Device | — | **ABSENT** (`owner_health_id` = possession only; no assignment model; no calibration gate on readings) | OF-G18 |
| R71 | CHW community monitoring workflow (households, visits, offline idempotency) | Vol II §14 | PCT community work-context = SoR; offline-edge vitals + break-glass | pct + community + offline-edge | community routes | provider app outreach | community APIs | `pct_households`, `pct_community_visits` (offline_id) | — | — | — | **BUILT** | — |
| R72 | Monitoring observations → SHR via a single designated writer | Vol II §14/§16 | telemonitoring is the sole monitoring-band Observation writer (wellness keeps simba path) | telemonitoring (new) + butano | — | — | fhir-gateway forward | — | — | Observation + Provenance | — | **PARTIAL** (today three ad-hoc writer paths: bff FhirPublisher, offline-edge, butano-direct) | OF-G16 |
| R73 | FHIR fulfilment/monitoring resource coverage (MedicationDispense, SupplyRequest, DeviceRequest, SupplyDelivery) | Vol II §16 | Gateway allow-list + route deltas; dispense projection at completion | fhir-gateway + pharmacy | — | — | gateway forward | — | — | MedicationDispense etc. | — | **ABSENT** (not in `CLINICAL_FHIR_RESOURCE_TYPES`; no dispense projection anywhere) | OF-G19 |
| R74 | End-to-end order-to-outcome runtime proof (order→offer→coverage→payment→dispense→delivery→SHR) | Vol II §23 | Extend the pack's rig pattern (journeys #41–#70) | rig | — | — | — | — | — | — | — | **ABSENT** (per-domain proofs exist; no cross-pipeline journey) | OF-G20 |

### 4.1 Volume II anti-pattern sweep (2026-07-23)

| Checked for | Finding |
|---|---|
| Mock prescriptions | **Confirmed structural risk** — legacy `rx_prescriptions` is an orphaned silo: unsigned, single-med, "DISPENSED" flip with zero stock effect (OF-G3/OF-G4) |
| Payment success directly marking fulfilment | **None** — pharmacy MushexConsumer only clears payment blockers; msika-flow pay-then-route separation is clean (verified) |
| Fake stock / availability | **Confirmed at marketplace** — no stock check at cart/checkout; local reservation placeholder never touches DURA (OF-G12) |
| Offers that are not persistent | N/A-by-absence — no offer machinery exists at all (OF-G8); nothing fake is shown |
| Delivery maps without durable shipments | **None** — `mf_delivery_plans` + full `nhume_delivery_*` records are durable |
| Hardcoded prices | **None remaining** — completion wave replaced checkout-time zero-price hardcode with server-resolved listing prices |
| Static providers | **None** — vendors/storefronts persisted + verification-gated |
| IoT readings without patient assignment | **Confirmed** — no clinical assignment model; readings keyed to device + possession only (OF-G18) |
| Unvalidated device data presented as clinical | **Partially guarded** — ingest schema-validates + DLQs; but no calibration/quarantine gate and heuristic trust scores (OF-G17/OF-G18) |
| Alerts closing without accountable action | N/A-by-absence — no per-patient alert lifecycle exists yet (OF-G16); Vol II §14 mandates accountable closure from day one |
| Order IDs reused as shipment IDs | **None** — ULID order ids, UUID delivery ids, distinct namespaces (Vol II §5 collision rules) |
| Prescriptions without signatures | **Confirmed** (OF-G1/OF-G3) |
| Tests proving only rendering/HTTP 200 | Per-domain runtime proofs are healthy (money stack, msika 25/25, nhume waves); the cross-pipeline journey is the gap (OF-G20) |
