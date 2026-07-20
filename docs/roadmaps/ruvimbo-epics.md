# Ruvimbo — Coverage / Health-Financing Epic Register

Executable split of `docs/product/ruvimbo-coverage-specification.md` into epics, per spec §44
("The next build artefact should split this into executable epics… with API contracts and
journey-level test cases attached to each epic"). Owner service: **coverage-service** (8140,
DB `impilo_coverage`). Brand: **Ruvimbo**.

**Boundary law:** Coverage decides *who pays & what's covered*; COSTA prices; MUSHEX moves money.
Switch (routing) ≠ adjudication (payability). Canonical claim = `cv_claims`. Every wave stays
production-capable (no mocks/stubs/dead controls in production paths).

**Status legend:** ✅ done · 🔨 in-progress · ⬜ pending · ⏸ deferred (future session).

---

## Wave 1 — Coverage Foundation (this session)

Migration: `coverage-service V015__ruvimbo_foundation.sql`. Proof: `scripts/e2e/ruvimbo-foundation-proof.sh`
+ `ui/one-ui-shell/e2e/journeys/coverage-wallet.journey.spec.ts`.

| Epic | Spec §§ | Contract (new endpoints, under `/internal/v1/coverage`) | Status |
|---|---|---|---|
| **E1 Payer Registry** | §8 | `POST/GET /payers`, `GET /payers/{id}`, `PATCH /payers/{id}`, `POST /payers/{id}/suspend`, `POST /payers/{id}/reactivate` | 🔨 |
| **E2 Plan hierarchy** | §9 | `POST/GET /payers/{id}/schemes`, `POST/GET /schemes/{id}/products`, `POST/GET /products/{id}/versions`, `POST /plan-versions/{id}/publish` (published = immutable) | 🔨 |
| **E3 Membership + verification + dependants** | §10 | `POST /members` (existing, +guards), `POST /members/{id}/verify|suspend|terminate|reinstate`, `POST /members/{id}/dependants`, `GET /members/{id}/dependants`, `GET /members/pending-verification` | 🔨 |
| **E4 Benefits + accumulators** | §13 | `POST/GET /plan-versions/{id}/benefits`, `GET /members/{cpid}/benefit-accumulators`; reservation-aware reserve/consume/release (idempotent) | 🔨 |
| **E5 Eligibility v2 + signed tokens** | §12 | `POST /eligibility/check` (v2 response + reason codes + validity + ruleset version), `POST /eligibility/tokens/verify` | 🔨 |
| **E6 Wallet + Ops console + branding** | §11, §26.1/26.5 | BFF passthroughs; `/coverage/member` wallet (verification badge, masking, dependants, benefits); `/coverage` ops (Payers/Plans/Verification); `ruvimbo` brand slug | 🔨 |
| **E7 Provider-network read surface** | §14 | BFF `ProviderNetworkController` exposing existing coverage network client (search by facility/provider) | ⬜ |

Events (this wave): `coverage.payer.created/updated/suspended`, `coverage.plan-version.published`,
`coverage.declared/verified/activated/suspended/terminated`, `benefit.reserved/consumed/released`,
`eligibility.checked`, `eligibility.token.issued`.

## Wave 2 — Authorisations and Estimates ✅ (V016 · live-proven 20/20 ×2 · `ruvimbo-w2-proof.sh`)

| Epic | Spec §§ | Status |
|---|---|---|
| Referrals & gatekeeping | §16 | ✅ `cv_referrals` + validate (active/in-date/visits, emergency bypass) + consume |
| Full authorisation machine | §17 | ✅ `cv_authorisations` 14-status + `cv_authorisation_lines` line-level + `authorisation.*` events (legacy preauth kept) |
| Benefit reservations wired to auth | §13, §17 | ✅ approved line → `BenefitAccumulatorService.reserve` (idempotent by line id) |
| Patient-liability estimation | §18 | ✅ `cv_liability_estimates` — Coverage rules over a COSTA charge; `ruvimbo-liability-v1` |
| Concurrent/retrospective review; rules-service migration; workbenches | §17.1/§33/§26 | ⏸ future refinement (auth types modelled; in-service rules versioned) |

## Wave 3 — Claims and Remittance ✅ (V017 · live-proven 23/23 ×2 · `ruvimbo-w3-proof.sh`)

| Epic | Spec §§ | Status |
|---|---|---|
| Claims v2 | §19 | ✅ 21-status lifecycle, `cv_claim_lines`, lineage (void/reverse/replace), scrubbing §19.3 |
| Adjudication pipeline | §6 | ✅ line-level, accumulator-consuming (converts auth reservation), explainable + `ruvimbo-adjudication-v1` |
| EOB / remittance | §20 | ✅ citizen EOB (plain-language) + remittance linked to claim |
| Settlement wiring | §21 | ✅ approved liability → remittance PENDING + `coverage.settlement.initiated` (MUSHEX payout credential-gated) |
| BFF /finance/claims re-wire; claims-switch depth | — | ⏸ deferred — cv_claims exposed via `/coverage/v2/claims`; switch formalised in W4 |

## Wave 4 — Advanced Financing Operations ✅ (V018 · live-proven 21/21 ×2 · `ruvimbo-w4-proof.sh`)

| Epic | Spec §§ | Status |
|---|---|---|
| Coordination of benefits | §15 | ✅ `cv_cob_decisions` priority waterfall; total payer ≤ allowed; `ruvimbo-cob-v1` |
| Employer / group admin | §24 | ✅ `cv_employers` + roster stage→validate→apply (no direct active-table writes) |
| Fraud / waste / abuse | §25 | ✅ `cv_fraud_flags` — duplicate/after-termination/impossible-amount screening → governed review |
| Capitation | §5, §19 | ✅ `cv_capitation_reports` |
| Claims Switch / Payer Gateway | §5, §32 | ✅ `cv_gateway_transactions` — route + technical/business ack; external rails credential-gated (PENDING, not faked) |
| Government programmes; appeals depth; payer-gateway satellite; real payer verify; offline | §23/§22/§32/§7.1/§35 | ⏸ future — govt via subsidy+programmes; appeals exist (W1 cv_appeals); satellite deferred (ports 8141+ free); external verify + offline credential/ops-gated |

---

## Acceptance ledger (spec §40 / §43 + Expansion §13)

Tracked as journeys are proven live. Wave 1 targets: citizen adds coverage; principal views
dependants; facility discovers coverage from a VITO-linked patient; receptionist runs a real
eligibility check returning network/benefit/co-payment/authorisation flags; a plan version is
published and immutable; a suspended payer blocks new enrolments; every material action is
auditable via the outbox. The remaining §40 criteria (auth end-to-end, claim from delivered care,
adjudication, EOB, settlement, reconciliation, appeals overturn, COB, waivers) map to Waves 2–4.
