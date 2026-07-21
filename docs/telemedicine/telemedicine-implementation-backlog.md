# Telemedicine Implementation Backlog

Companion to the [National Telemedicine & Virtual Care Specification](NATIONAL_TELEMEDICINE_VIRTUAL_CARE_SPECIFICATION.md) (§31) and the [gap matrix](telemedicine-traceability-gap-matrix.md). Priorities: **P0** national-use blocker · P1 national-grade completeness · P2 maturity. "Blocks national use" = a clinical-safety or trust invariant is unmet without it.

## Epic index

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
