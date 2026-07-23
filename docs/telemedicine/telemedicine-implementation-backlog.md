# Telemedicine Implementation Backlog

Companion to [Volume I — National Telemedicine & Virtual Care Specification](NATIONAL_TELEMEDICINE_VIRTUAL_CARE_SPECIFICATION.md) (§31), [Volume II — National e-Orders, Fulfilment & Telemonitoring Specification](NATIONAL_EORDERS_FULFILMENT_TELEMONITORING_SPECIFICATION.md) (§26) and the [gap matrix](telemedicine-traceability-gap-matrix.md). One backlog serves the whole pack: Volume I epics are TM-B1..TM-B20 (all 20 cores IMPLEMENTED, waves A–D 2026-07-22/23); Volume II epics are OF-B1..OF-B30 (not started). Priorities: **P0** national-use blocker · P1 national-grade completeness · P2 maturity. "Blocks national use" = a clinical-safety or trust invariant is unmet without it.

## Volume I epic index (TM-B)

| # | Epic | Priority | Blocks national use |
|---|---|---|---|
| TM-B1 | Canonical telemedicine aggregate + guarded state machine | **P0** | **Yes** |
| TM-B2 | Referral and context package (full section set, provenance, uploads) | P1 | No |
| TM-B3 | Routing directory + worklists (pools, on-call, specialty codes) | P1 | No |
| TM-B4 | Scheduling and waiting room completion | P1 | No |
| TM-B5 | Impilo Live session runtime completions (chat persistence, downgrade ladder, side-channels) | P1 | Partially (chat loss) |
| TM-B6 | Clinical documentation and SHR integration (response package, FHIR projection, conflict UX) | **P0** | **Yes** |
| TM-B7 | Orders, tasks and execution loop (OROS wiring, Task model, awaiting-* states, follow-up) | **P0** | **Yes** |
| TM-B8 | Consent and trust enforcement hardening (hard gates, accept-time authority) | **P0** | **Yes** |
| TM-B9 | Khuluma/notification orchestration + scheduled reminders | P1 | No |
| TM-B10 | Nompilo telemedicine guidance | P2 | No |
| TM-B11 | Offline and store-and-forward | P1 | For offline sites |
| TM-B12 | Emergency escalation and conversion (Daidzai/Nhume/Ndila wiring, ESCALATED/TRANSFERRED) | **P0** | **Yes** |
| TM-B13 | Patient & caregiver experience completion (Impilo ID copy, instructions view, feedback) | P1 | No |
| TM-B14 | Provider workspace completion (console context panels, availability/handover) | P1 | No |
| TM-B15 | MDT and specialist-board mode (templates + policy) | P1 | No |
| TM-B16 | Virtual-hospital substrate + governed pools + recording→learning | P1 | No |
| TM-B17 | Quality, analytics and Rito integration | P2 | No |
| TM-B18 | Mobile parity maintenance | P2 | No |
| TM-B19 | Observability and operational command (session diagnostics API) | P2 | No |
| TM-B20 | End-to-end runtime proof expansion + event versioning | P1 | Gate for every epic |

---

## TM-B1 — Canonical aggregate + guarded state machine (P0)

**Problem.** `pct_referrals.status` is an ungated string; the guarded machine lives only in the inactive legacy profile; closure/exception states (CLOSED family, NEEDS_MORE_INFORMATION, ESCALATED, TRANSFERRED, CANCELLED, EXPIRED, ABANDONED, ENTERED_IN_ERROR, REOPENED, AWAITING_*) are absent (TM-G1, TM-G8, R20).
**Outcome.** Spec §11.3 implemented on the active spine: transition guard layer in `TelemedicineOrchestrationService` (single choke point), additive states, per-state timer/notification/audit semantics, machine-readable transition set, backward-compatible migration (existing rows map 1:1).
**Owner.** pct-service. **Dependencies.** None (foundation for most others).
**Acceptance.** Illegal transitions rejected 409 with structured code; every transition emits lifecycle event + audit; state table drives worklist buckets; legacy statuses migrate cleanly; decline/reassign land in exception worklists (void-proofing invariant test).
**Tests.** State-machine unit matrix (every permitted/prohibited pair); restart-recovery; concurrency (two actors racing a transition); runtime proof extending J-VC-1 with illegal-transition attempts.
**Migration.** Additive enum-check constraint + data backfill of `stage`-consistent states. **Risk.** Medium (touchpoint for all flows) — mitigate with shadow-guard mode first.

## TM-B2 — Referral & context package (P1)

**Problem.** Full clinical section set (TM-G2) only in legacy shape; duplicate-case detection absent (TM-G16); upload scanning absent (TM-G18); receiver context panels thin.
**Outcome.** Structured package sections on the active spine (jsonb sections + coded fields), provenance classes rendered, duplicate-case interstitial, document-service scanning + size/format enforcement, receiver review screen per Stage 4 E.
**Owner.** pct + document-service + ui. **Dependencies.** TM-B1 (NEEDS_MORE_INFORMATION), ZIBO codes (TM-B3).
**Acceptance.** Stage 2 W criteria; Stage 4 review-screen checklist rendered from real data; EICAR-style scan test blocks infected upload.
**Tests.** Package round-trip persistence; provenance display units; scanning integration. **Migration.** Additive columns/jsonb. **Risk.** Low.

## TM-B3 — Routing directory + worklists (P1)

**Problem.** ON_CALL/UNIT/NATIONAL_POOL 501 (TM-G6); no specialty CodeSystem (TM-G7); worklist set incomplete (Stage 4 E).
**Outcome.** Duty/pool directory (Vashandi engagement-window on-call + pool membership; Khuluma presence consumed, not owned), ZIBO `impilo-clinical-specialty` CodeSystem + ValueSet, BFF route proxy for all types, full worklist buckets incl. exception queues, routing-decision class recording.
**Owner.** vashandi (rosters), zibo (codes), pct (queues), bff/ui. **Dependencies.** TM-B1 (states drive buckets); HO-1 seam.
**Acceptance.** Stage 3 W criteria; 501s retired one-by-one with runtime proof each; routing basis visible on case.
**Tests.** Directory contract tests; pool claim race; SLA breach → escalation event. **Risk.** Medium (cross-service).

## TM-B4 — Scheduling & waiting room (P1)

**Problem.** Reminder scheduling impossible (TM-G14 — moved to TM-B9 for the NotifyRequest change); estimated wait, local-staff readiness, interpreter status, leave-and-notify absent from waiting room.
**Outcome.** Booking linkage enriched (dedicated external-ref field replacing notes-tagging), waiting-room panel completions, no-show path (state + notice + follow-up).
**Owner.** booking + rtc/ui + pct. **Dependencies.** TM-B1 (ABANDONED/no-show), TM-B9 (reminders).
**Acceptance.** Stage 5 waiting-room checklist all green; no-show journey (catalogue #37/38) proven.
**Tests.** e2e no-show; reschedule; slot-race. **Risk.** Low.

## TM-B5 — Session runtime completions (P1)

**Problem.** Clinically relevant chat ephemeral (TM-G10); audio→async downgrade partial; provider-only side discussion absent; consent-withdrawal mid-session handling partial.
**Outcome.** Chat persistence rule (OD-10): flagged-clinical messages persist to case/Khuluma with provenance; full downgrade ladder; labelled side-channel (policy-gated); withdrawal stops modality immediately.
**Owner.** rtc-gateway + khuluma + pct (W0 lease coordination required). **Dependencies.** OD-10 decision.
**Acceptance.** Room destruction loses zero flagged content (journey #14); withdrawal test stops publish within seconds.
**Tests.** Media e2e extensions; websocket contract. **Risk.** Medium (W0-owned surfaces).

## TM-B6 — Clinical documentation & SHR integration (P0)

**Problem.** 4-field response spine (TM-G9); thin DiagnosticReport (TM-G11); no in-session encounter-note surface; no conflict UX (TM-B6/R31); amendment semantics informal.
**Outcome.** Full Stage 6 response package with pre-submission validation (authority, ZIBO `$validate-code`, allergy/medication conflict, dose/route); FHIR projection: virtual-class Encounter + response Composition + enriched DiagnosticReport + Provenance; signed-version/amendment/addendum model; §18 conflict UX.
**Owner.** pct + fhir-gateway/butano + ui. **Dependencies.** TM-B1; ZIBO sets.
**Acceptance.** Stage 6 W criteria; journey #17 (simultaneous medication edits) shows conflict UX, never silent overwrite; timeline shows provenance-marked teleconsult authorship.
**Tests.** Validation matrix units; FHIR contract tests; concurrency e2e. **Migration.** Response schema versioning (v1 spine preserved). **Risk.** Medium-high (clinical safety core).

## TM-B7 — Orders, tasks & execution loop (P0)

**Problem.** No OROS teleconsult wiring (TM-G4); no Task model; no awaiting-*/follow-up states or worklists (TM-G8); duplicate-order prevention informal; critical-result acknowledgement absent.
**Outcome.** OROS `TELECONSULT` RequestSource + case linkage from the teleconsult path; Task entity (owner, due, state, escalation) feeding Awaiting-Local-Action worklists; results return to AWAITING_RESULTS; critical-result ack + escalation timers; follow-up commitments → FOLLOW_UP_DUE → linked case spawn; closure preconditions (§Stage 7 I) enforced.
**Owner.** oros + pct + ui. **Dependencies.** TM-B1 (states).
**Acceptance.** Journeys #18 (duplicate order blocked), #25 (follow-up overdue escalates), #26 (unacknowledged critical result escalates), #27 (closure with pending result requires assignment) proven end-to-end.
**Tests.** Order idempotency; task escalation timers; loop-closure e2e. **Migration.** New task table + OROS enum addition. **Risk.** Medium.

## TM-B8 — Consent & trust hardening (P0)

**Problem.** Consent hard-gate flag-gated; submit-without-consent unguarded on active spine (TM-G3); accept-time authority soft (TM-G5).
**Outcome.** `consentReference`-for-media default-on (OD-4); submit guard (409 `CONSENT_REQUIRED_MISSING` semantics ported from legacy); PDP hard gate on accept (licence/scope/assignment/context via VARAPI axes + Vashandi + WORK-token match) with break-glass exception path; consent-withdrawal propagation.
**Owner.** pct + bff + tshepo-authz (policy seeds) + rtc-gateway. **Dependencies.** OD-4 sign-off; TM-B3 for duty data.
**Acceptance.** Journey #9 (licence expires before acceptance → hard block); consent journeys (#22 recording denied) all green; flag removal proven safe in preview first.
**Tests.** Policy tests per 10 dimensions; negative-path e2e. **Risk.** Medium (flag rollout discipline).

## TM-B9 — Comms orchestration + scheduled reminders (P1)

**Problem.** OD-3 undecided; `NotifyRequest` lacks `scheduledAt` (TM-G14); notification catalogue partially seeded.
**Outcome.** Ratified orchestration path (either Khuluma journey-orchestrator consuming lifecycle topics, or documented BFF-direct with Khuluma surfaces); scheduled-delivery capability in notification-service; full §16.2 catalogue seeded with PHI-minimised templates + retry/fallback/ack/expiry semantics; delivery-failure events + fallback proof.
**Owner.** khuluma + notification-service + bff. **Dependencies.** OD-3.
**Acceptance.** Pre-session reminder fires at T-minus (journey #3); journey #36 (channel failure → fallback) proven; no external template carries clinical content (template lint).
**Tests.** Template rendering; retry/fallback integration; catalogue completeness check. **Risk.** Low-medium.

## TM-B10 — Nompilo telemedicine guidance (P2)

**Problem.** No in-consult/waiting-room-specific guidance; stage guidance items unseeded (R27).
**Outcome.** Seeded route-bound guidance for every §P item (identity confirmation, Impilo ID recovery, proxy access, consent plain-language, device checks, privacy, waiting-room status, danger-sign escalate-only behaviour, post-session next steps, failure recovery); in-consult panel (patient + provider variants); guardrail tests that Nompilo never diagnoses/prescribes/alters records.
**Owner.** guidance-service + ui. **Dependencies.** none hard.
**Acceptance.** Guidance renders per route context (extend ai-guidance e2e); guardrail negative tests.
**Risk.** Low.

## TM-B11 — Offline & store-and-forward (P1)

**Problem.** No offline case/draft/queue; S&F package lacks integrity controls; reconciliation absent (TM-B5 §19; TM-G17).
**Outcome.** Local durable drafts (web IndexedDB + mobile storage); queued idempotent submission; signed S&F package (checksum, freshness, expiry); reconciliation with conflict UX hand-off; downgrade-aware sync; stale banners.
**Owner.** ui/mobile + pct + bff. **Dependencies.** TM-B1, TM-B6 (conflict UX).
**Acceptance.** Journeys #15/#16 (facility offline; package syncs later) and #34 (browser refresh during draft) proven; duplicate-event prevention verified under replay.
**Tests.** Sync property tests; clock-skew cases. **Risk.** High (client complexity) — stage behind a pilot-site flag.

## TM-B12 — Emergency escalation & conversion (P0)

**Problem.** ESCALATED/TRANSFERRED absent; Daidzai/Nhume/Ndila not wired from teleconsult; failed-escalation documentation absent (R32).
**Outcome.** §23 table implemented: emergency action from all mandated surfaces; Daidzai escalation call; Nhume dispatch; Ndila destination; transfer note + structured handover; states + events; break-glass review queue; post-event Rito hook.
**Owner.** pct + daidzai + nhume + ndila + ui. **Dependencies.** TM-B1 (states).
**Acceptance.** Journeys #6, #23, #24, #31 proven; "financial status never obstructs emergency care" asserted (billing bypass on EMERGENCY purpose).
**Tests.** Escalation e2e with dispatch stub-then-real; failure-path documentation test. **Risk.** Medium.

## TM-B13 — Patient & caregiver experience (P1)

**Problem.** "Health ID" copy non-compliant (TM-G15); instructions/orders view partial; feedback loop partial; caregiver request-on-behalf UX partial.
**Outcome.** Impilo ID copy migration (all citizen surfaces + translations, per OD-5 plan); post-consult instructions + orders view; Rito feedback prompt at closure; delegation-aware request flows.
**Owner.** ui/mobile + rito. **Dependencies.** OD-5.
**Acceptance.** Copy audit script finds zero citizen-facing "Health ID"; journeys #4, #21, #29 proven.
**Risk.** Low.

## TM-B14 — Provider workspace completion (P1)

**Problem.** Console context panels thin (longitudinal summary, allergies, meds, results in-session); availability/handover absent.
**Outcome.** Stage 5 patient-context region complete (visibility-tier aware); provider availability + handover surface; responsibility-ladder display (who holds what, since when).
**Owner.** ui + bff (+ pct summary composition). **Dependencies.** TM-B6.
**Acceptance.** Stage 4/5 review-screen checklists; handover journey.
**Risk.** Low-medium.

## TM-B15 — MDT & specialist-board mode (P1)

**Problem.** Session templates missing for MDT/case-review, audit/mortality (no-name/no-blame), emergency advisory, diagnostics review, provider-advice; identity-visibility policy absent (HO-3/HO-4).
**Outcome.** New templates in `libs/session-templates` (W0 lease change) with governance fields per `session-modes.ts`; TSHEPO/OPA identity-visibility levels + case-notes access grants; MDT workspace (agenda, board, consensus + dissent, actions with owners).
**Owner.** session-templates owner (W0) + tshepo policy owner + ui + pct. **Dependencies.** HO-3/HO-4 handoffs; TM-B1.
**Acceptance.** Journeys #19, #20, #21 (catalogue) proven; pseudonymised presentation enforced server-side.
**Risk.** Medium-high (policy work).

## TM-B16 — Virtual-hospital substrate + pools + learning artefacts (P1)

**Problem.** VH config-only (HO-2); governed clinical groups absent (HO-5); recording→learning artefact TODO (R34).
**Outcome.** TUSO-adjacent virtual service-delivery registry (VH ID, operating authority, linked-facility roles, provider privileges, jurisdiction policy — per HO-2 recommendation; `virtual-hospitals.ts` as seed spec); governed pool membership; ClinicalGroup model; live-service `onRecordingAvailable` → Fundo artefact under separate consent (journey #12).
**Owner.** tuso (registry) + vashandi (membership) + live/fundo (artefacts) + khuluma (threads). **Dependencies.** OD-7; TM-B3.
**Acceptance.** A configured VH becomes REQUESTABLE end-to-end with real pool + governance; journey #12 recording-to-artefact with consent gate.
**Risk.** High (new sovereign model) — coordinator-gated per HO-2.

## TM-B17 — Quality, analytics & Rito (P2)

**Outcome.** §24 metric set complete incl. equity-of-access dimensions; Rito feedback/complaint/safety wiring; outcome analytics from completion notes.
**Owner.** analytics + rito + pct. **Acceptance.** Metrics dashboard renders §24 set from events only (no DB scraping); feedback journey #29.

## TM-B18 — Mobile parity maintenance (P2)

**Outcome.** Parity matrix kept green for every new surface in TM-B2..B16; deep links for all new states; native downgrade ladder.
**Owner.** mobile. **Acceptance.** Parity matrix CI check; journeys #32/#33.

## TM-B19 — Observability & ops command (P2)

**Outcome.** Session-diagnostics query API over `rtc.session_events`/`participant_stats`; helpdesk surface (no clinical content — visibility-tier enforced); per-journey failure drill-down; notification-failure dashboards.
**Owner.** rtc-gateway + analytics + ui. **Acceptance.** Journey #13 (failed call → helpdesk diagnostics) proven; helpdesk role blocked from clinical reads (policy test).

## TM-B20 — Runtime proof expansion + event versioning (P1)

**Outcome.** Journey catalogue automated: every P0/P1 epic lands with its catalogue journeys in `scripts/runtime-proof/` or Playwright (live-estate pattern, DB/event assertions, negative paths); lifecycle events versioned (`telemedicine.session.*.v1`) with consumer-compatible migration (TM-G12); contract tests for the frozen BFF paths.
**Owner.** all + SQA. **Acceptance.** CI-runnable journey suite; zero unversioned new events.

---

## Sequencing recommendation

1. **Wave A (P0 foundations):** TM-B1 → TM-B8 → TM-B7 → TM-B6 → TM-B12 (state machine first; everything hangs off it).
2. **Wave B (P1 spine):** TM-B2, TM-B3, TM-B4, TM-B9, TM-B13, TM-B14, TM-B20 in parallel lanes.
3. **Wave C (P1 expansion):** TM-B5, TM-B11, TM-B15, TM-B16.
4. **Wave D (P2 maturity):** TM-B10, TM-B17, TM-B18, TM-B19.

---

# Telemedicine Pack Backlog — Volume II Epic Blocks (OF-B1..OF-B30) — DRAFT

Companion to [Volume II — National e-Orders, Fulfilment & Telemonitoring Specification](NATIONAL_EORDERS_FULFILMENT_TELEMONITORING_SPECIFICATION.md) (§26 reserved titles) and the [gap matrix](telemedicine-traceability-gap-matrix.md) (§2 OF-G1..21, §4 R41–R74). Same priority legend as the TM-B blocks: **P0** national-use blocker · P1 national-grade completeness · P2 maturity. "Blocks national use" = a clinical-safety, statutory or trust invariant is unmet without it. Journey numbers #41–#70 are the Volume II order-to-outcome catalogue (commissioning-instruction journeys 1–30, offset +40).

## Volume II epic index (OF-B)

| # | Epic | Priority | Blocks national use |
|---|---|---|---|
| OF-B1 | Canonical clinical-order aggregate | **P0** | **Yes** |
| OF-B2 | E-prescription authoring and signing | **P0** | **Yes** |
| OF-B3 | Medication safety validation | **P0** | **Yes** |
| OF-B4 | MSIKA fulfilment marketplace (request-for-offer) | **P0** | **Yes** |
| OF-B5 | Provider eligibility and matching | **P0** | **Yes** |
| OF-B6 | Offer and quotation lifecycle | **P0** | **Yes** |
| OF-B7 | Patient offer-comparison and selection | P1 | No |
| OF-B8 | Ruvimbo coverage and prior authorisation | P1 | No |
| OF-B9 | COSTA patient-liability calculation | P1 | No |
| OF-B10 | MUSHEX payment and reconciliation | P1 | No |
| OF-B11 | DURA stock reservation | **P0** | **Yes** |
| OF-B12 | Pharmacy dispense workflow | P1 | **Yes** (claim linkage) |
| OF-B13 | Diagnostics fulfilment | P1 | No |
| OF-B14 | Multi-provider and partial fulfilment | P1 | No |
| OF-B15 | Substitution and prescriber clarification | P1 | No |
| OF-B16 | Pickup and collection | P1 | No |
| OF-B17 | NHUME delivery orchestration | P1 | No |
| OF-B18 | Chain of custody and proof of delivery | P1 | No |
| OF-B19 | Cold-chain IoT | P2 | No |
| OF-B20 | Drone and alternative delivery-mode enablement | P2 | No |
| OF-B21 | Community telemonitoring programme | P1 | No |
| OF-B22 | Monitoring-plan engine | P1 | No |
| OF-B23 | CHW monitoring workflow | P1 | No |
| OF-B24 | Device registry and lifecycle | P1 | No |
| OF-B25 | IoT ingestion and normalisation | P1 | No |
| OF-B26 | Monitoring alert and escalation engine | P1 | No |
| OF-B27 | Patient and caregiver monitoring experience | P2 | No |
| OF-B28 | Remote-monitoring command workspace | P2 | No |
| OF-B29 | Fraud, anomaly and marketplace fairness controls | **P0** (controlled gating) / P2 (anomaly + fairness analytics) | **Yes** (controlled half) |
| OF-B30 | End-to-end order-to-outcome runtime proof | P1 | **Gate for every epic** |

**OF-B29 split justification.** The controlled-medicine half (OF-G7, **P0** in the matrix) is a statutory invariant — the DURA controlled register exists (inventory V013) but nothing consumes it, so a controlled substance can today ride the same rails as paracetamol; no national deployment is defensible in that state. The anomaly-detection and fairness-analytics half needs marketplace volume to observe and tune against, which cannot exist before Waves OF-A/OF-B land — so it is honestly P2, not padding.

---

## OF-B1 — Canonical clinical-order aggregate (P0)

**Problem.** Order amendment/replacement/versioning absent — in-place mutation or nothing; only result-level amend exists (OF-G2, R43). The 13-status OROS spine is BUILT (R41) but has no immutable-version model, so every downstream control (signing, tokens, fraud-by-construction §13.8) has nothing stable to bind to.
**Outcome.** Spec §8A/§9.1 on the OROS spine: amendment = new immutable version, never in-place mutation; version table with supersedes-chain; cancel/replace semantics per the §9.1 order machine; provenance on every version; `oros.order.amended.v1`/`.replaced.v1` events.
**Owner.** oros. **Dependencies.** None (foundation for OF-B2, B4, B6, B15).
**Acceptance.** Journeys #41 (versioned order reaches pickup), #50 (cancel-after-reservation releases downstream cleanly), #44 (clarification amend creates v2, v1 marked superseded — dispense against v1 refused).
**Tests.** Version-machine unit matrix; concurrent-amend race; downstream-reference integrity (offer/dispense pin a version id).
**Migration.** oros — order-version table + supersedes columns, next-free at execution. **Risk.** Medium — the version pin ripples into every consumer; land before any RFO code binds to bare order ids.

## OF-B2 — E-prescription authoring and signing (P0)

**Problem.** No clinician signing anywhere — `placed_by`/`prescribed_by` are bare strings (OF-G1, R42); prescription aggregate thin + orphaned — flat single-med `rx_prescriptions`, no items/repeats-ceiling/validity/controlled flag, unlinked to the dispense pipeline (OF-G3, R44); no anti-fraud token (OF-G6, R48).
**Outcome.** Prescription aggregate lives in **OROS** (parent authorisation; PHARMACY orders = dispense episodes): items, repeats ceiling, validity window, controlled flag, indication; detached JWS signing via tshepo-keys stored with the signed version (`oros.prescription.signed.v1`); opaque PrescriptionToken bound to PrescriptionVersionId per §13.2 (no clinical payload in the QR, server-side retrieval, single-active-claim); legacy `rx_prescriptions` formally deprecated with a cutover plan (OD-13).
**Owner.** oros + tshepo-keys (+ pharmacy for the legacy deprecation seam). **Dependencies.** OF-B1 (versions to sign); OD-11 (legal signature model + non-medication signing scope); OD-13 (legacy cutover).
**Acceptance.** Journeys #41 (token-claimed pickup), #51 (repeat-already-dispensed refused by server-side counter), #54 (SMS pickup code path — no smartphone); tampered/replayed token rejected with audit.
**Tests.** JWS verify round-trip; token single-claim race; repeats-ceiling decrement units; negative forged-token e2e.
**Migration.** oros — prescription + version + token tables, next-free at execution; pharmacy — deprecation marker only. **Risk.** High — legal-signature semantics (OD-11) and the legacy cutover (OD-13) are decision-gated; build flag-gated dual-write until ratified.

## OF-B3 — Medication safety validation (P0)

**Problem.** Safety validation is a hardcoded deterministic rules engine with substring allergy matching; PCT allergy SoR absent so checks degrade honestly to WARNING; no licensed interaction/dose database (OF-G5, R47).
**Outcome.** Spec §8A layered validation at authoring AND at substitution/dispense recheck: structured allergy model in the PCT SoR (coded, not substring); ZIBO-coded medicine references end-to-end (no free-text products); interaction/dose/pregnancy/renal checks behind a versioned rules seam so a licensed database (procurement decision) drops in without re-architecture; hard-block vs override-with-reason tiers, every override audited.
**Owner.** clinical-knowledge + pct (allergy SoR) + experience-bff + zibo. **Dependencies.** OF-B8's ZIBO medicine-registry artifact type (shared seam); TM-B6 validation set (Volume I sibling).
**Acceptance.** Journey #41 gate (unsafe order blocked at authoring, not at pharmacy); #44 (substitution candidate re-validated before approval request); WARNING-degrade path visibly honest when SoR data is absent.
**Tests.** Validation matrix units (allergy/interaction/dose per tier); override-audit assertions; coded-vs-substring regression pack.
**Migration.** pct — allergy tables, next-free at execution; clinical-knowledge — rule-version tables. **Risk.** Medium — the licensed-database procurement is external; ship the seam + deterministic ruleset, never claim coverage the ruleset lacks.

## OF-B4 — MSIKA fulfilment marketplace (request-for-offer) (P0)

**Problem.** RFO machinery absent entirely — requests, invitations, PII-minimised publication all grep-zero across msika/coverage (OF-G8, R51, R55).
**Outcome.** Net-new RFO layer in **msika-flow** per §11: `mf_marketplace_requests` + invitations referencing the OROS order read-only (never copying clinical truth); §9.3 request machine; **PII-minimised publication** per the §11.8 binding table — invitations carry ZIBO-coded lines, coarse Ndila zone and capability flags only, patient identity revealed post-selection; publication modes per §11.2 (targeted/network/broadcast per OD-12); emergency orders never auctioned (§4.4).
**Owner.** msika-flow (+ oros read-seam, ndila zones). **Dependencies.** OF-B1 (version pin); OF-B5 (eligibility at invitation); OD-12 (broadcast mode + ranking fairness policy).
**Acceptance.** Journeys #41/#42 (request→invitation→offer→selection spine), #45 (split-fulfilment publication), #56 (lab RFO variant); PII-minimisation asserted by inspecting the actual invitation payloads (no name, no HID/CPID, no free-text clinical content).
**Tests.** Request-machine unit matrix; invitation-payload minimisation contract test; broadcast-mode policy tests.
**Migration.** msika-flow — request/invitation tables, next-free at execution. **Risk.** Medium-high — largest net-new surface in the volume; the PII table is binding, so payload review is part of definition-of-done.

## OF-B5 — Provider eligibility and matching (P0)

**Problem.** Only onboarding + risk-friction gates exist; no per-offer/per-acceptance revalidation loop (OF-G11, R54) — a provider whose licence lapsed after onboarding can still win and fulfil an order.
**Outcome.** §4.3 six-precondition check evaluated at **invitation AND acceptance** (§11.3): VARAPI licence class + dispensing scope, TUSO facility verification + operational status, VASHANDI employment binding (a licence alone is never facility authority), network/capability flags; controlled lines route only to authorised fulfillers (feeds OF-B29); fail-closed with structured refusal codes.
**Owner.** msika-flow + varapi + tuso + vashandi. **Dependencies.** OF-B4 (invitation seam); OF-B29 controlled-gating half.
**Acceptance.** Journey #52 (controlled-to-unauthorised — unauthorised provider is never invited AND is re-blocked at accept if authority changed mid-flight); licence-lapse-between-offer-and-accept negative path proven.
**Tests.** Precondition matrix units (each of the six failing alone); revalidation-at-accept race; registry-contract tests against VARAPI/TUSO.
**Migration.** msika-flow — eligibility-snapshot columns on invitations/offers, next-free at execution. **Risk.** Medium — cross-registry latency at accept; mitigate with bounded-staleness snapshots + hard recheck on commit only.

## OF-B6 — Offer and quotation lifecycle (P0)

**Problem.** Offer lifecycle absent — no offer entity, TTL, revalidation-at-acceptance or race handling (OF-G9, R52); stock truth grades unenforced at offer time (R57 partial).
**Outcome.** §9.4 offer machine in msika-flow: `mf_fulfilment_offers` with price, stock grade (VERIFIED = DURA on-hand−reserved per §8E), fulfilment window, TTL; expiry sweeps; **revalidate-on-select** (§11.7 idempotent selection — eligibility + stock + price all rechecked at commit); reservation TTL = offer TTL; losing offers notified and released.
**Owner.** msika-flow (+ inventory availability seam). **Dependencies.** OF-B4 (requests), OF-B5 (eligibility recheck), OF-B11 (stock recheck + reservation).
**Acceptance.** Journeys #48 (payment-after-offer-expiry → honest re-offer, no zombie commitment), #49 (stock-disappears at revalidation → fail-closed re-offer, patient told the truth), #41 (happy-path selection commits exactly once under concurrent double-select).
**Tests.** Offer-machine matrix; TTL-expiry sweep units; double-select idempotency race; stock-grade downgrade display.
**Migration.** msika-flow — offer tables + TTL indexes, next-free at execution. **Risk.** Medium — race-safety at select is the atomic hinge (§8I); property-test the commit path.

## OF-B7 — Patient offer-comparison and selection (P1)

**Problem.** Comparison/selection experience absent entirely (OF-G10, R53) — patients cannot see, understand or choose between offers.
**Outcome.** §8F/§11.6 comparison surface (web + citizen app): ranked offers with **ranked-because explanations** (§11.5 fairness — no dark patterns, no hidden commercial influence CC-21), per-offer liability from OF-B9, stock grade badges, delivery-vs-pickup options; Nompilo explains rankings and substitutions without steering; selection calls the OF-B6 idempotent commit.
**Owner.** ui + experience-bff (+ guidance-service explanations). **Dependencies.** OF-B6 (offers), OF-B9 (per-offer liability); OD-12 (ranking policy).
**Acceptance.** Journeys #41 (compare→select→commit), #46 (payer-covers-med-not-delivery shown honestly per offer); ranking-explanation copy audit; selection races surface honest "offer no longer available" states.
**Tests.** BFF composition contract tests; ranking-explanation snapshot tests; accessibility pass; Playwright selection e2e.
**Migration.** None (BFF/UI composition; no datasource in BFF). **Risk.** Low-medium — fairness copy is policy-sensitive; review against OD-12 before GA.

## OF-B8 — Ruvimbo coverage and prior authorisation (P1)

**Problem.** Coverage/PA/claims/COB engines BUILT and proven (R58/R59/R61), but no payer formulary entity and no ZIBO medicine-registry artifact type — drug coverage expressible only as raw benefit definitions (OF-G14, R62).
**Outcome.** Three-layer formulary per the canonical stance: **ZIBO national medicine registry** (new artifact type — the coding that prevents free-text products anywhere) · **coverage `cv_formulary`** (payer tiers, PA-required flags) · pharmacy facility list (`rx_formulary`, already BUILT); PA-required flags drive the §10.6 prior-auth lifecycle in the offer flow; reservation-aware accumulators per §10.
**Owner.** coverage (Ruvimbo) + zibo. **Dependencies.** OF-B4/OF-B6 (offer flow to wire into); OF-B3 (shares the ZIBO medicine seam).
**Acceptance.** Journeys #46 (med covered, delivery not — line-level split honest), #47 (prior-auth rejected → patient sees status + appeal path, order preserved not destroyed).
**Tests.** Formulary-resolution units (three layers, precedence); PA-flag→auth-lifecycle integration; accumulator regression.
**Migration.** coverage — `cv_formulary` tables, next-free at execution; zibo — artifact-type addition. **Risk.** Low-medium — engines proven; the risk is formulary content governance, not code.

## OF-B9 — COSTA patient-liability calculation (P1)

**Problem.** Liability engine BUILT (`cv_liability_estimates`) but not wired into any offer/checkout flow (OF-G13, R60) — patients would select offers blind to cost.
**Outcome.** §8G/§10.4 per-offer flow: COSTA supplies the charge; Ruvimbo computes eligibility→benefit→estimated liability per offer; **estimate-never-final** labelling binding (§10.5); shortfall feeds OF-B10 payment intents; emergency financial bypass honoured (§10.8 — financial status never obstructs emergency care).
**Owner.** costa + coverage + msika-flow (call seam). **Dependencies.** OF-B6 (offers to price), OF-B8 (formulary tiers).
**Acceptance.** Journey #46 (per-offer liability shown, med-vs-delivery split correct); estimate-vs-final delta visible at reconciliation (#70 support); zero-liability emergency path proven.
**Tests.** Liability-ladder units (eligibility ≠ benefit ≠ adjudication); per-offer batch computation perf; estimate-labelling contract test.
**Migration.** None expected (engine exists; wiring only) — any new columns next-free at execution. **Risk.** Low — wiring work on proven engines.

## OF-B10 — MUSHEX payment and reconciliation (P1)

**Problem.** Payment intents/refunds LIVE (R63), but escrow hold-until-handover is built only for campaigns, not wired to fulfilment proof-of-delivery (OF-G13 escrow half, R64); no cross-pipeline reconciliation view.
**Outcome.** §8H payment doctrine in the offer flow: intent→PAID for the shortfall before commit; **mushe-wallet escrow released on Nhume proof-of-handover** (OF-B18 seam); refunds on failed delivery/returns (§12.7); payment-fail preservation (clinical order untouched, reservation held to TTL, honest retry §8.8.4); payment events never set fulfilment state (CC-2); reconciliation across intent/claim/settlement without duplication.
**Owner.** mushex + mushe-wallet + msika-flow. **Dependencies.** OF-B6 (commit), OF-B18 (PoD trigger for escrow release).
**Acceptance.** Journeys #48 (payment lands after offer expiry → refund/re-offer, never a phantom commitment), #70 (reconciliation-no-duplication — one order, one claim, one payment trail).
**Tests.** Escrow release integration (PoD-triggered); refund path e2e; reconciliation invariant checks; CC-2 negative test (PAID event alone moves nothing clinical).
**Migration.** mushe-wallet/mushex — escrow-linkage columns, next-free at execution. **Risk.** Medium — money paths; stage escrow behind a flag with the campaign machinery as the proven base.

## OF-B11 — DURA stock reservation (P0)

**Problem.** Marketplace reservation is disconnected from DURA — local placeholder `mf_reservations` rows, `InventoryEventConsumer` is a **no-op logger**, no availability check at cart/checkout; double-sell risk confirmed (OF-G12, R56, R57).
**Outcome.** §8I canonical stance: DURA `inv_stock_reservations` is the **sole** reservation ledger; atomic conditional reserve (available = on-hand − reserved must cover quantity), TTL = fulfilment window, **fail-close — no reservation, no commitment**; `mf_reservations` demoted to a read-projection fed by real `inventory.reservation.*.v1` events; the no-op consumer made real; release on cancel/expiry; attested vendor stock flagged unverified per OD-17.
**Owner.** inventory (DURA) + msika-flow. **Dependencies.** OF-B6 (commit path calls reserve); OD-17 (vendor-without-DURA tolerance).
**Acceptance.** Journeys #49 (stock-disappears → commit refused, honest re-offer), #50 (cancel-after-reservation → reservation released, stock available again — DB-asserted); concurrent double-reserve of last unit: exactly one wins.
**Tests.** Conditional-reserve race (property test); TTL release sweep; projection-consistency test (mf row never disagrees with DURA row); no-op-consumer replacement contract test.
**Migration.** inventory — reservation-event outbox additions; msika-flow — projection demotion migration; both next-free at execution. **Risk.** Medium-high — the anti-double-sell invariant is load-bearing for trust; shadow-run the projection before cutover.

## OF-B12 — Pharmacy dispense workflow (P1 — blocks national use)

**Problem.** Prescription↔dispense claim linkage absent — "refill" stamps a timestamp, `POST /{id}/dispense` flips status with zero stock effect, no repeats decrement (OF-G4, R45 — **P0 gap**); FHIR MedicationDispense/SupplyRequest/DeviceRequest/SupplyDelivery missing from the gateway, no dispense projection anywhere (OF-G19, R73). Dispense engine itself BUILT (R46: FEFO, partial fill, stock ledger, pickup proofs).
**Outcome.** Dispense episode carries `prescription_version_id`; **claim is atomic with server-side repeats counter** (`oros.prescription.claimed.v1`); dispense drives real `rx_stock_movements` (CC-12: no status flip without stock effect); counselling capture added; MedicationDispense projection to BUTANO at completion via gateway allow-list additions (§16.3); offline-tolerant claim per §15.2 patterns.
**Owner.** pharmacy + oros + fhir-gateway/butano. **Dependencies.** OF-B2 (prescription versions + tokens), OF-B11 (stock).
**Acceptance.** Journeys #41 (claim→dispense→pickup proof→SHR projection), #44 (substituted dispense recorded against the right version), #51 (second claim refused), #55 (pharmacy-offline-sync — queued claim reconciles idempotently, no double decrement).
**Tests.** Claim-counter race; FEFO/partial-fill regressions; MedicationDispense contract tests; offline replay idempotency.
**Migration.** pharmacy — linkage columns + counselling table; fhir-gateway — resource-type allow-list; next-free at execution. **Risk.** Medium — cutover interacts with OD-13 legacy deprecation; dual-run window required.

## OF-B13 — Diagnostics fulfilment (P1)

**Problem.** OROS diagnostics spine (accessioning, results) BUILT (R41), but diagnostics have no marketplace path — no lab/imaging RFO variant, no home-collection logistics composition; gaps ride OF-G8/OF-G10 for the diagnostics category.
**Outcome.** Category B (§7.2) flows through the same RFO rails: lab offers include collection mode (walk-in / home collection via Nhume specimen leg); imaging offers include modality/slot; results return through existing OROS result paths with critical-result guards (Volume I TM-B7 seam); ServiceRequest projections already in place extended with fulfilment linkage.
**Owner.** oros + msika-flow + nhume (specimen leg) + ui. **Dependencies.** OF-B4/OF-B6 (RFO), OF-B17 (collection logistics).
**Acceptance.** Journeys #56 (lab-auction-home-collection — offer→collect→accession→result→SHR), #57 (imaging-fulfilment end-to-end).
**Tests.** Diagnostics-RFO variant contract tests; specimen custody linkage; result-return regression against the existing spine.
**Migration.** msika-flow — category-variant columns, next-free at execution. **Risk.** Low-medium — mostly composition over BUILT parts; specimen cold-custody edge cases ride OF-B19.

## OF-B14 — Multi-provider and partial fulfilment (P1)

**Problem.** Governed splitting (§4.7) has no machinery — a multi-line order cannot be fulfilled by different providers, and partial fill exists only inside a single pharmacy episode (R46); rides the absent RFO layer (OF-G8/OF-G9).
**Outcome.** Line-level RFO: a request may split into per-line invitations; per-line offers, selections and commitments each pinned to the order version; split visibility for the patient (one order, N fulfilments, unified tracking); no line orphaned — unfulfilled lines re-offered or returned to the prescriber; partial-fill remainder handling unified with split logic.
**Owner.** msika-flow + oros + ui. **Dependencies.** OF-B4, OF-B6, OF-B7 (comparison shows split composition), OF-B11 (per-line reservations).
**Acceptance.** Journey #45 (split fulfilment — two providers, two deliveries, one closed loop; no line lost); partial-fill remainder journey re-enters the pipeline correctly.
**Tests.** Split-commit atomicity per line; orphan-line sweep test; unified-tracking BFF composition.
**Migration.** msika-flow — line-level linkage tables, next-free at execution. **Risk.** Medium — combinatorial state space; constrain to per-line machines composed, never a bespoke "split machine".

## OF-B15 — Substitution and prescriber clarification (P1)

**Problem.** `rx_substitution_rules` engine BUILT (R46) but the prescriber-approval loop is absent — no clarification request/response channel, no amend path to record the approved substitution (rides OF-G2, R43).
**Outcome.** §4.7 governed substitution: substitution proposal from the fulfiller → prescriber notification → approve/deny with reason → approval recorded as an order amendment (OF-B1 version) → dispense proceeds against the new version; generic-substitution policy tiers (auto-allowed vs approval-required vs never); safety recheck (OF-B3) on every candidate; patient informed in plain language (Nompilo).
**Owner.** pharmacy + oros + pct (clarification task) + khuluma/notification. **Dependencies.** OF-B1 (amend), OF-B3 (recheck), OF-B12 (dispense pin).
**Acceptance.** Journey #44 (generic substitution approval — proposal→prescriber approve→v2→dispense v2, all audited; deny path returns to original or re-offer).
**Tests.** Clarification round-trip integration; policy-tier units; version-pin regression (dispense against superseded version refused).
**Migration.** pharmacy/oros — clarification linkage, next-free at execution. **Risk.** Low-medium — prescriber responsiveness is the operational risk; needs escalation timers (PCT task seam).

## OF-B16 — Pickup and collection (P1)

**Problem.** Pickup proofs BUILT (`rx_pickup_proofs`, R46) but claim verification rides the absent token (OF-G6); caregiver collection and no-smartphone paths undefined; locker/curbside flows (§12.5) absent.
**Outcome.** Pickup = token claim (OF-B2) + identity-grade check per the §12.4 proof ladder; caregiver collection via MVUMO delegation (Volume I R26 machinery, delegation recorded on the proof); SMS one-time pickup code for no-smartphone patients (integrity-only offline per §13.2); locker flow with expiry→return→restock; curbside variant.
**Owner.** pharmacy + oros (token claim) + mvumo (delegation) + ui/notification. **Dependencies.** OF-B2 (tokens), OF-B12 (dispense linkage).
**Acceptance.** Journeys #41 (standard pickup), #53 (caregiver-collects — delegation verified + recorded), #54 (SMS-pickup-no-smartphone), #69 (locker-expiry → return + refund/restock loop).
**Tests.** Proof-grade ladder units; delegation-verification integration; locker-expiry sweep; SMS-code single-use race.
**Migration.** pharmacy — proof-grade + delegation columns, next-free at execution. **Risk.** Low-medium — SMS-code fraud surface; rate-limit + short TTL, covered by OF-B29 anomaly rules later.

## OF-B17 — NHUME delivery orchestration (P1)

**Problem.** Nhume logistics SoR BUILT (24-status machine, multi-cargo, R65), but fulfilment↔delivery write-back is best-effort — callback failures swallowed to warnings, no retry/escalation contract (OF-G15, R66).
**Outcome.** §12.8 write-back hardening: durable outbox-driven callbacks with retry + DLQ + ops escalation, never silent; delivery-task creation from commitment (§12.1) with courier minimum-necessary payloads (§12.2, CC-9 — no clinical content to couriers); failed-delivery/returns/refund loop (§12.7) wired to OF-B10; recipient-unavailable re-attempt policy; new Nhume streams use `nhume.<aggregate>.<action>.v1` (unprefixed legacy grandfathered).
**Owner.** nhume + msika-flow. **Dependencies.** OF-B6 (commitment), OF-B10 (refunds).
**Acceptance.** Journeys #42 (motorcycle delivery end-to-end with status truth in the patient view), #68 (recipient-unavailable → re-attempt → return path, fulfilment state honest throughout); induced callback failure surfaces in ops, never lost.
**Tests.** Write-back retry/DLQ integration; courier-payload minimisation contract test; return-loop e2e.
**Migration.** msika-flow/nhume — callback outbox tables, next-free at execution. **Risk.** Low-medium — hardening of a BUILT seam; event-naming migration discipline required.

## OF-B18 — Chain of custody and proof of delivery (P1)

**Problem.** Custody machinery BUILT (`nhume_chain_of_custody_events`, `nhume_delivery_proofs`, R65), but the proof-of-handover grade ladder (§12.4) is not enforced per order class, escrow release is not wired to PoD (OF-G13/R64), and controlled-substance custody policy is undecided (OD-15).
**Outcome.** Graded proof-of-handover per §12.4 (grade required scales with order class — controlled lines demand second-factor handover per §13.4); unbroken custody chain asserted at completion (gap in chain = exception, not silence); PoD event triggers mushe-wallet escrow release (OF-B10 seam) and fulfilment confirmation (§8N); safe handover identity via VITO name-grade (no CPID/HID leak to couriers).
**Owner.** nhume + mushe-wallet + vito (identity grade). **Dependencies.** OF-B17, OF-B10 (escrow); OD-15 (controlled custody policy); OF-B29 (controlled gating).
**Acceptance.** Journeys #42 (custody chain complete, PoD grade recorded, escrow released), #53 (caregiver handover grade + delegation), #52 support (controlled handover second factor).
**Tests.** Chain-gap detection units; PoD-grade enforcement matrix; escrow-release integration; identity-leak contract test.
**Migration.** nhume — grade-requirement columns, next-free at execution. **Risk.** Medium — OD-15 gates the controlled half; ship graded ladder for standard classes first.

## OF-B19 — Cold-chain IoT (P2)

**Problem.** Temperature custody events BUILT (R65) but §12.6 cold-chain is not closed-loop: no continuous sensor telemetry binding, no excursion-detection rule, no automatic quarantine-and-replace flow.
**Outcome.** Cold-chain legs bind a sensor DeviceId (iot-ingestion telemetry) to the delivery; excursion rule evaluates against product-specific thresholds; excursion → cargo quarantined, recipient + fulfiller notified, replacement flow initiated, DURA stock adjusted (spoilage), refund/re-dispense per §12.7; excursion evidence preserved on the custody chain.
**Owner.** nhume + iot-ingestion + inventory + pharmacy. **Dependencies.** OF-B17, OF-B25 (telemetry), OF-B11 (stock adjust).
**Acceptance.** Journey #43 (cold-chain excursion → quarantine → replacement dispensed → patient made whole; excursion visible on the chain, never delivered silently).
**Tests.** Excursion-rule units per threshold profile; quarantine-flow integration; spoilage stock-adjustment assert.
**Migration.** nhume — sensor-binding columns, next-free at execution. **Risk.** Medium — depends on physical sensor estate; keep honest CONFIG states where hardware absent, never simulate compliance.

## OF-B20 — Drone and alternative delivery-mode enablement (P2)

**Problem.** Drone/alternative modes are CONFIG-ONLY — `nhume_autonomous_missions` table exists with zero operational evidence; must remain a governed capability, never claimed live (OF-G21, R67).
**Outcome.** §12.9 transport-mode enablement matrix as a governed capability: per-mode enablement by geography + policy + evidence gate (a mode becomes offerable only with recorded operational evidence); mission lifecycle for autonomous modes incl. weather/reroute handling; fallback-to-conventional path that preserves the custody chain; honest UI (modes never shown where not enabled).
**Owner.** nhume + ndila (geography) + ops governance. **Dependencies.** OF-B17, OF-B18 (custody continuity across mode handoff).
**Acceptance.** Journey #67 (drone-weather-reroute → fallback mode, custody unbroken, patient informed); disabled-mode never appears in offers (negative UI proof).
**Tests.** Enablement-matrix policy tests; reroute-fallback integration; evidence-gate enforcement.
**Migration.** nhume — enablement/evidence columns, next-free at execution. **Risk.** High externally (regulatory airspace, hardware) but low in-code; the epic's job is honest gating, not flight ops.

## OF-B21 — Community telemonitoring programme (P1)

**Problem.** PCT community workflow BUILT (households, visits, offline_id — R71), but the §14.1 programme model and §14.2 enrolment (programme catalogue, per-programme profiles, consented enrolment initiated via the OROS spine) are absent — monitoring today has no programme container (rides OF-G16).
**Outcome.** Programme catalogue + profile model (hypertension/diabetes/maternal etc. per §14.1); enrolment as an OROS Category C order (MONITORING vs OTHER per OD-16) with MVUMO consent; enrolment creates the MonitoringEpisode in telemonitoring-service (OF-B22) and binds CHW assignment via the PCT community context; disenrolment/transfer lifecycle.
**Owner.** telemonitoring-service (new) + oros + pct + mvumo. **Dependencies.** OF-B22 (episode/plan home); OD-16 (OrderType).
**Acceptance.** Journeys #58 (DeviceRequest-BP-monitor — enrolment → device order → plan active), #59 context (CHW sees enrolled households).
**Tests.** Enrolment order-flow integration; consent-gate negative test; disenrolment lifecycle units.
**Migration.** telemonitoring-service — new schema (programme/enrolment tables), next-free at execution (V001+ in the new service). **Risk.** Medium — first consumer of the net-new service; land OF-B22 skeleton first.

## OF-B22 — Monitoring-plan engine (P1)

**Problem.** No per-patient remote-monitoring engine — plans, personalised thresholds and alert lifecycle absent; monitoring observations reach the SHR via three ad-hoc writer paths (OF-G16, R68, R72). Ownership-exhaustion proof (§6.1) mandates a **net-new clinical-plane telemonitoring-service**.
**Outcome.** telemonitoring-service minted (registered in `services-registry.yaml` before first commit): MonitoringPlan aggregate per §14.3 (clinician-approved, personalised thresholds, schedules, review cadence); threshold-profile catalogue; plan lifecycle events `telemonitoring.plan.*.v1`; **sole monitoring-band Observation writer** to BUTANO (three ad-hoc paths consolidated; wellness keeps the simba path); CarePlan/Goal FHIR projections.
**Owner.** telemonitoring-service (new) + butano/fhir-gateway. **Dependencies.** OF-B21 (enrolment feeds plans); registry sign-off for the new service; OD-16.
**Acceptance.** Journeys #58 (plan activated with personalised thresholds), #60 (amber threshold breach evaluated against the plan, not a global default); writer-consolidation proven (grep + runtime: exactly one monitoring-band writer path).
**Tests.** Plan-lifecycle unit matrix; threshold-evaluation property tests; single-writer contract test; FHIR projection contracts.
**Migration.** telemonitoring-service — new schema V001+, next-free at execution. **Risk.** Medium-high — net-new sovereign service; keep the aggregate small and let OF-B26 own alerting.

## OF-B23 — CHW monitoring workflow (P1)

**Problem.** CHW community substrate BUILT (R71: households, visits, offline idempotency), but monitoring-specific flow is absent: no visit-capture against a plan, no rejected-reading reconciliation queue (§15.8-11), no multi-patient offline attribution safety (§14.4-8/-9) tied to plans.
**Outcome.** §14.4 CHW workflow on the provider app: visit capture against active MonitoringPlans; offline-edge readings with per-reading patient attribution enforced at capture; pending-sync visibility; server rejections return as a human-visible reconciliation queue, never a silent drop; break-glass for emergencies during visits; CHW task list fed by OF-B26 alerts.
**Owner.** pct + telemonitoring-service + offline-edge + mobile. **Dependencies.** OF-B22 (plans), OF-B25 (ingest), OF-B26 (tasks from alerts).
**Acceptance.** Journeys #59 (CHW-offline-readings — captured offline, synced idempotently, attributed correctly), #62 support (wrong-patient attribution caught at reconciliation, not buried).
**Tests.** Offline replay idempotency; attribution-conflict reconciliation e2e; restart-recovery of the capture queue.
**Migration.** pct — visit↔plan linkage, next-free at execution. **Risk.** Medium — offline complexity (Volume I TM-B11 lesson: high client complexity, pilot-site flag).

## OF-B24 — Device registry and lifecycle (P1)

**Problem.** No clinical device assignment (patient↔device↔plan) and no calibration/quarantine gating of readings — `owner_health_id` records possession only; `asr_equipment` calibration state exists but nothing consumes it (OF-G18, R70).
**Outcome.** The §6.2 three-way split enforced: telemonitoring-service owns **DeviceAssignmentId** (which patient, which plan, which clinician accountable); asset-registry calibration state projected into ingest gating; §15.5 lifecycle states (enrolled→assigned→active→quarantined→retired); a reading is clinically presentable only when telemetry valid + asset calibrated + assignment active (CC-18) — failing readings **stamped, never dropped** (§15.7); assignment handover/return flows.
**Owner.** telemonitoring-service + asset-registry + iot-ingestion. **Dependencies.** OF-B22 (plans), OF-B25 (gate enforcement point); OD-14 (attestation/BYOD).
**Acceptance.** Journeys #62 (wrong-patient-device — reading with mismatched assignment flagged and quarantined, alerting suppressed with reason), #63 (calibration-expired — readings stamped non-clinical, replacement workflow fires).
**Tests.** Three-way-agreement matrix (each leg failing alone); assignment lifecycle units; calibration-projection contract test.
**Migration.** telemonitoring-service — assignment tables, next-free at execution. **Risk.** Medium — cross-service truth composition; keep each SoR sovereign, compose at read/gate time.

## OF-B25 — IoT ingestion and normalisation (P1)

**Problem.** Ingest BUILT (schema validation, DLQ, provenance, offline batch — R69) but device trust scoring is a hardcoded heuristic (static 95/80/55/25 + fixed operation lists) with no real attestation (OF-G17); duplicate/out-of-order handling and clock-skew re-basing (§15.7) incomplete against spec.
**Outcome.** §15.3/§15.4 trust grading made real: policy-driven trust model with attestation seam (authority per OD-14, BYOD enrolment path); wearable/consumer devices graded lower with grade carried as provenance on every reading; §15.7 data-quality dimensions enforced — dedup on device sequence + offline_id, original device timestamps preserved with sync-time as separate provenance, clock-skew flag + re-base; normalisation to canonical units before the telemetry bus.
**Owner.** iot-ingestion. **Dependencies.** OD-14; OF-B24 (assignment gate consumes trust grade).
**Acceptance.** Journeys #64 (duplicate-readings — replays collapse to one clinical reading, evidence retained), #66 (wearable-lower-trust — grade visibly carried into clinical presentation and alert weighting).
**Tests.** Dedup property tests; clock-skew re-base units; trust-grade policy tests; DLQ reason-code coverage.
**Migration.** iot-ingestion — trust-policy + dedup-index migrations, next-free at execution. **Risk.** Low-medium — BUILT base; attestation authority (OD-14) is the external unknown.

## OF-B26 — Monitoring alert and escalation engine (P1)

**Problem.** No per-patient alert lifecycle exists (OF-G16, R68) — Vol II §14.6 mandates accountable closure from day one; no escalation ladder (§14.7) connects amber/red alerts to virtual review, CHW dispatch or EMS.
**Outcome.** AlertEpisode aggregate in telemonitoring-service: rule evaluation against plan thresholds (personalised, not global); severity bands; **accountable closure** — every alert closed by a named actor with a reason, never auto-vanished (CC-19); §14.7 escalation ladder — amber → virtual review (Volume I teleconsult spine, sessionId==referralId), repeated/red → Daidzai EMS escalation (alerts are a distinct identifier class from EMS incidents, §5.3); device-silence detection (no readings in window) as its own alert class; `telemonitoring.alert.*.v1` events.
**Owner.** telemonitoring-service + pct (review tasks) + daidzai (EMS seam). **Dependencies.** OF-B22 (plans/thresholds), OF-B24/25 (gated readings in); TM-B12 (escalation rails).
**Acceptance.** Journeys #60 (amber-alert-virtual-review — alert→teleconsult case→closure with linkage), #61 (repeated-abnormal-emergency → Daidzai escalation, financial bypass honoured), #65 (device-offline-3-days → silence alert → CHW task).
**Tests.** Rule-evaluation matrix; accountable-closure negative test (closure without actor/reason impossible); escalation-ladder integration; silence-detection sweep units.
**Migration.** telemonitoring-service — alert tables, next-free at execution. **Risk.** Medium-high — clinical-safety core of the monitoring half; alert fatigue is the design risk (threshold personalisation is the mitigation, not suppression).

## OF-B27 — Patient and caregiver monitoring experience (P2)

**Problem.** No patient/caregiver monitoring surfaces — readings, plan visibility, alert transparency all absent (rides OF-G16; R68 "monitoring desks (new)").
**Outcome.** Citizen app + web: my-monitoring view (plan, readings trend, device status, what-happens-when explanation via Nompilo); caregiver visibility via MVUMO delegation; reading-submission UX for patient-operated devices; alert transparency ("your care team was notified") without inducing panic — copy reviewed; Impilo ID naming discipline (Volume I TM-G15 lesson applies to all new citizen surfaces).
**Owner.** ui/mobile + experience-bff + guidance-service. **Dependencies.** OF-B22, OF-B26 (data + alerts to show), OF-B24 (device status).
**Acceptance.** Journeys #58 (patient sees plan + device active), #60 (patient-side view of the amber-alert flow); delegation-scoped caregiver view proven; copy audit (no "Health ID").
**Tests.** BFF composition contracts; delegation-scope policy tests; Playwright monitoring-view e2e; accessibility pass.
**Migration.** None (composition; BFF stateless). **Risk.** Low.

## OF-B28 — Remote-monitoring command workspace (P2)

**Problem.** No clinician/programme-level monitoring workspace (§14.8) — no triage board over alert episodes, no caseload view, no programme analytics.
**Outcome.** §14.8 workspaces: monitoring-desk triage board (alert queue by severity/age/accountability), per-patient drill-down (plan, trend, device health, alert history), caseload management for CHW supervisors, programme-level dashboards fed by events only (no DB scraping — Volume I TM-B17 rule); visibility-tier aware (helpdesk-style roles blocked from clinical content).
**Owner.** ui + experience-bff + telemonitoring-service (query APIs) + analytics. **Dependencies.** OF-B26 (alert lifecycle to command), OF-B23 (CHW caseloads).
**Acceptance.** Journeys #61 (red alert triaged from the board to EMS with full audit), #65 (silence alert worked from queue to resolution); role-visibility policy test.
**Tests.** Query-API contracts; board-bucket correctness against alert states; event-fed dashboard assertion.
**Migration.** telemonitoring-service — read-model/query indexes, next-free at execution. **Risk.** Low-medium.

## OF-B29 — Fraud, anomaly and marketplace fairness controls (P0 controlled gating / P2 anomaly + fairness)

**Problem.** Controlled-medicine workflow gating absent — the DURA controlled register (inventory V013) exists but **nothing consumes it**; no restricted routing, no second-factor handover (OF-G7, R49 — P0). The wider §13 threat catalogue (anomaly detection, provider-performance monitoring §13.7, ranking-fairness monitoring §11.5/§21) has no engine (P2 — needs marketplace volume to tune against).
**Outcome.** **P0 half:** controlled lines never open-broadcast (§13.4) — restricted routing to authorised fulfillers only (OF-B5 gate), mandatory DURA controlled-register write on dispense, second-factor handover (OF-B18), separation-of-duties checks (§13.5), custody policy per OD-15. **P2 half:** anomaly rules over dispense/claim/offer patterns (§13.7), ranking-fairness monitoring with Rito firewall (§21), immutable audit sweep (§13.6) — version-validation fraud-impossibility (§13.8) is largely delivered by OF-B1/B2 by construction.
**Owner.** oros + pharmacy + inventory + msika-flow + tshepo (audit) + rito (fairness signals). **Dependencies.** OF-B2 (tokens), OF-B5 (routing gate), OF-B18 (handover); OD-15, OD-12.
**Acceptance.** Journey #52 (controlled-to-unauthorised — blocked at invitation, at accept, and at handover: three independent gates), #51 support (reuse pattern flagged); controlled-register write asserted on every controlled dispense.
**Tests.** Three-gate negative matrix; register-write invariant test; (P2) anomaly-rule backtests on accumulated marketplace data.
**Migration.** pharmacy/inventory — register-consumption linkage, next-free at execution. **Risk.** Medium — OD-15 gates custody policy; ship the three-gate spine with a conservative default, tighten per ratified policy.

## OF-B30 — End-to-end order-to-outcome runtime proof (P1 — gate for every epic)

**Problem.** No cross-pipeline runtime proof (order→offer→coverage→payment→dispense→delivery→SHR) — only per-domain proofs exist (money stack, msika 25/25, nhume waves) (OF-G20, R74).
**Outcome.** Extend `scripts/runtime-proof/` with the Volume II journey rig (journeys **#41–#70**, live-estate pattern: DB/event-outbox assertions + negative paths, per the Volume I TM-B20 discipline); the Appendix A stage A–N sequence proven as one continuous journey; every new event versioned (`oros.prescription.*.v1`, `msika.flow.*.v1`, `telemonitoring.*.v1` — no unversioned additions); contract tests for the OROS↔msika-flow↔DURA↔Nhume seams; CI-runnable.
**Owner.** rig (all epic owners contribute) + SQA. **Dependencies.** Grows with every wave — each P0/P1 epic lands **with** its catalogue journeys, it does not wait for them.
**Acceptance.** All thirty journeys #41–#70 pass on the live estate; the #70 reconciliation journey closes the loop (one order, one payment trail, one SHR projection, zero duplication); zero unversioned events (lint gate).
**Tests.** The rig **is** the test; plus rig self-checks (journeys fail honestly when a seam is broken — no green-by-mock).
**Migration.** None (scripts + CI). **Risk.** Low in code, high in discipline — the known failure mode is "proof written after the fact"; the sequencing rule below is the mitigation.

---

## Volume II sequencing recommendation

1. **Wave OF-A (P0 foundations):** OF-B1 → OF-B2 → OF-B3 (the order/prescription trust spine: versions, then signatures + tokens over versions, then safety over coded content), then OF-B4 → OF-B5 → OF-B6 → OF-B11 (the marketplace spine: requests, eligibility, offers, then the DURA reservation that makes commit real). The OF-B29 controlled-gating half rides alongside OF-B2/B5 (routing gate) and completes with OF-B18's handover gate in Wave OF-B.
2. **Wave OF-B (P1 marketplace-complete + finance):** OF-B7, OF-B8, OF-B9, OF-B10, OF-B12, OF-B13, OF-B14, OF-B15, OF-B16, OF-B17, OF-B18 — parallel lanes over the Wave OF-A spine (comparison/finance lane: B7-B10; dispense lane: B12, B15, B16; logistics lane: B17, B18; diagnostics/split lane: B13, B14). OF-B12's claim linkage closes the last P0-severity gap (OF-G4).
3. **Wave OF-C (monitoring):** OF-B22 first (the net-new service skeleton + plan engine), then OF-B21, OF-B24, OF-B25 in parallel, then OF-B26 (alerts need plans + gated readings), then OF-B23, OF-B27, OF-B28 (workflows and surfaces over the engine).
4. **Wave OF-D (governance + maturity):** OF-B19, OF-B20, OF-B29 (anomaly + fairness half — now with real marketplace volume to tune against), OF-B30 closure.
5. **OF-B30 is continuous, not terminal:** the rig grows **per wave** — Wave OF-A lands with journeys #41/#48–#52 green, Wave OF-B adds #42–#47/#53–#57/#68–#70, Wave OF-C adds #58–#66, Wave OF-D adds #43/#67 and the full-suite closure. An epic without its journeys is not done (TM-B20 precedent). This prevents the known failure mode where the end-to-end proof is authored last against an estate that quietly drifted.
