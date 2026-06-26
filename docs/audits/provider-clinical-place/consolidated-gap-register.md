# Consolidated Gap Register — Core Transaction Wave (Audit of existing + new changes)

> **Status:** authoritative gap register produced by the full audit (doctrine reconciliation + **real** Lovable
> inspection + **independent** test verification of all four lane branches + the 25-part wave spec). Branch
> `intake/provider-clinical-place-design`. Companion: [cross-program audit](cross-program-audit.md) ·
> [Lovable matrix](lovable-absorption-matrix.md) · [journey](../../journeys/core-transaction-patient-access-encounter-orchestration.md).
>
> **Honesty note:** "Verified Live" = I ran the real test suite myself (offline mvn/vitest), not an agent
> self-report. "Partial/Missing" reflects the real repo. Test-runs below are independent.

## 0. Independent verification result (all four lanes — real numbers)

| Module | Tests | Fail | Lane |
|--------|------:|-----:|------|
| pct-service | 77 | 0 | L1 |
| inpatient-service | 8 | 0 | L1 |
| costing-engine-service (costa) | 69 | 0 | L4 |
| coverage-service | 35 | 0 | L4 |
| tuso-service | 50 | 0 | L2 |
| indawo-service | 19 | 0 | L2 |
| workforce-governance-service | 6 | 0 | L2 |
| tshepo-identity-service | 79 | 0 | L3 |
| varapi-service | 111 | 0 | L3 |
| vashandi-workforce-service | 15 | 0 | L3 |
| experience-bff | 521–526 | 0 | L1/L2/L3 |
| web one-ui-shell | tsc 0 err; vitest 1062–1081 green; lint 0 err | — | all |

**Verdict:** the lane code is **real and green** — no fabricated/canned production paths, no dead buttons, no
`501` placeholders. All flagged TODOs **deny/fail-loud rather than fake success** (consistent with no-stub
doctrine). Two of my own prompt labels were wrong (corrected in §5).

## 1. Gaps PLUGGED this turn

| ID | Gap | Fix | State |
|----|-----|-----|-------|
| GAP-1 | My journey/read-model docs invented a **parallel state machine**, diverging from canonical `contracts/core-transaction.ts` (54 states) | Reframed journey diagram as a narrative overlay + full mapping to canonical `CoreTransactionState`; corrected C6 to import the contract union | ✅ fixed + pushed (`da362bd8e`) |

## 2. Gaps CLOSEABLE NOW (small, low-risk — plug in this wave)

| ID | Gap | Owner lane | Effort |
|----|-----|-----------|--------|
| GAP-2 | L2's 3 new BFF controllers (`FacilityModeController`, `FacilityRegulatorBffController`, `IndawoPlaceModeController`) have **no dedicated tests** (exercised only indirectly) | L2 | small |
| GAP-3 | L3 W6 `ProviderBootstrapController/Service` (bulk-preload + self-claim) has **no dedicated unit test** | L3 | small |
| GAP-4 | Reconcile L1's Java `CadreEngine` (pct-service) against a **pre-existing `one-ui-shell/.../cadreEngine.ts`** Lovable flagged — confirm no duplicate cadre-enforcement SoR (PCT must be authoritative; the TS side may only render) | L1/web | small (verify; refactor if dup) |

## 3. Gaps PARTIAL / DEFERRED (real functional boundaries)

| ID | Gap | Detail | Owner | Notes |
|----|-----|--------|-------|-------|
| GAP-5 | **Phone/email/invite identifier resolution** | `SilentIdentifierResolutionService` denies safely for PHONE/EMAIL/INVITE (`notResolved()` + TODO); Health ID/Impilo ID/Provider ID/council are Live | L3 | needs VITO contact-resolve + invite-token endpoints — **verify VITO capability first (SoR-first)** before wiring |
| GAP-6 | **Policy ENFORCEMENT of all new rules** | Cadre actions, facility-mode entry, Provider-ID-deny, WORK-REQUIRES-ASSIGNMENT, SELF-TREATMENT-BLOCK, PROVIDER-SELF-CLAIM etc. are **spec-only TODOs**; rides existing ext_authz but new fine-grained rego is **not authored/enforced** | track P / **CZO-locked** | NOT ours to author (PolicyEngine single-writer lock). Route to WS-OPA `impilo.authz` or queue CZO lead. **Largest systemic gap.** |
| GAP-7 | **Work/Pro/Life server-side enforcement** | client boundary helper Live; server authz deferred to GAP-6 | L3 + track P | tied to GAP-6 |

## 4. Gaps MISSING (net-new scope — further waves; **not built**)

Mapped to the 25-part spec and the real Lovable adopt-targets.

| ID | Gap | Spec ref | Lovable | Size |
|----|-----|----------|---------|------|
| GAP-8 | **Patient-facing experience surfaces per stage** (queue-status/ticket/"running late", check-in confirmation, orders/results status, referral/teleconsult status, inpatient updates, outcome) — **largely absent on our side** | Part 3, DoD #5 | #3 (top miss) | **large** |
| GAP-9 | **Patient message catalog shipped** as i18n strings (en/sn/nd) wired to Khuluma dispatch across all stages (we have design keys, not full implementation+dispatch) | Part 23, DoD #11 | — | medium |
| GAP-10 | **Cadre-specific History/Exam form content** (Doctor SOCRATES/ICD vs Nurse focused vs CHW danger-signs) — engine exists, forms don't | Part 16/18 | #2 | medium |
| GAP-11 | **Unified front-door "sorting session"** entity binding arrival→identity→triage→route + `arrival_mode` tiles (we have a sorting-desk/visit-type step, not the unifying session) | Part 13/14 | #1 | medium |
| GAP-12 | **Referral Package builder** enhancements — specialist-question prompts, multi-target (facility/specialty/provider/pool/on-call) | Part 19 | #4/#5 | medium |
| GAP-13 | **Encounter Tools Bar + Clinical Safety/Intelligence Ribbon + AI-assist** (assistive, marked, non-silent) | Part 17 | adapt | large |
| GAP-14 | **Order sets** (Sepsis Bundle, ANC, etc.) sourced from **ZIBO/CKP governed** content (NOT hardcoded) + picker UX | Part 18 | #3-adapt | medium |
| GAP-15 | **ICD-11/SNOMED readiness** in Problems & Diagnoses | Part 18 | reject-Lovable-codes | medium |
| GAP-16 | **Result-acknowledgment workflow** + **charge-capture review queue** as first-class encounter UX | Part 18/4 | #8/#10 | medium |
| GAP-17 | **Critical-Event shell mode** (full-shell red state, live timer, mandatory outcome) | Part 9 | #9 | medium |
| GAP-18 | **30 seeded end-to-end scenarios** (each showing provider + patient + core-transaction state + access/compensation + audit + notifications + closure) | Part 24, DoD | — | large |
| GAP-19 | **Mobile parity** for the new provider + patient surfaces | Part 22 | — | large |
| GAP-20 | **Procedure context** (day-surgery, no admission) still inpatient-only; full six-context parity unverified at runtime | Part 12, DoD #7 | — | medium |

## 5. CORRECTIONS (from independent verification — fix the record)

- **L4 did NOT change `mushex-service`** (it changed `costing-engine-service` + `coverage-service`). My earlier lane labels and lane-plan said mushex — corrected here.
- **L1 also changed `experience-bff`** (`PctServiceClient`, `EncounterController`) — I'd labeled it pct+inpatient only.
- vNext already has a **UI-side `cadreEngine.ts`** (more advanced than Lovable's) → GAP-4 reconciliation.

## 6. PROCESS / VALIDATION gaps (Part 25 / DoD #12 — **not done**)

| ID | Gap |
|----|-----|
| GAP-21 | **Integration to canonical (round-3 / T5)** not started — 4 isolated branches; no end-to-end runtime; DoD #4 "provider logs in → … → closes visit" unproven |
| GAP-22 | **Product Truth + ROUTE_MAP + SERVICE_WIRING_MATRIX + WEB_MOBILE_PARITY_MATRIX** not updated for the new routes |
| GAP-23 | **No-stub/no-mock check, route-parity check, preview smoke test, screenshots** not run on an integrated tree |
| GAP-24 | **`CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST` (26 pts)** not filled per new feature |

## 7. Recommended plug sequence (honest — this is multi-wave, not one sweep)

1. **This wave (small, safe):** GAP-2, GAP-3 (test coverage), GAP-4 (cadre dup reconcile), verify GAP-5's VITO prerequisite. *Closeable now.*
2. **Round 3 — integrate + validate (highest leverage; unblocks DoD #4/#12):** merge the 4 lanes into canonical in dependency order, prove one end-to-end three-sided flow, run GAP-21→24 (Product Truth/parity/preview/no-stub). **Do this before building more net-new on isolated branches.**
3. **Policy enforcement (GAP-6/7):** route the spec'd rules to WS-OPA `impilo.authz` / CZO lead. Gated by the CZO lock — coordinate, do not author PolicyEngine.
4. **Patient experience wave (GAP-8/9/16/17):** the biggest doctrine gap — the patient side of every stage + message dispatch. Highest product value.
5. **Clinical depth wave (GAP-10/11/12/13/14/15):** cadre forms, sorting session, referral builder, tools bar/safety ribbon, ZIBO-governed order sets, ICD-11/SNOMED.
6. **Breadth wave (GAP-18/19/20):** 30 seed scenarios, mobile parity, procedure context.

**Bottom line:** the lanes are **real and green**, and the audit is complete and honest. But "all gaps plugged" is a **multi-wave program** — §2 is closeable now; §3–§6 are sequenced waves, with **integration+validation (round 3) as the next highest-leverage step**. This register is the tracking source of truth; do not mark a GAP closed without re-verification.
