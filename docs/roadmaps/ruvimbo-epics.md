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

## Wave 2 — Authorisations and Estimates ⏸

| Epic | Spec §§ | Notes |
|---|---|---|
| Referrals & gatekeeping | §16 | new `cv_referrals`; validate referring/referred provider via VARAPI |
| Full authorisation machine | §17 | replace `cv_preauth_requests` 4-status with the 14-status machine + `authorisation.*` events; line-level |
| Concurrent / retrospective review | §17.1 | extension, retrospective, info-request loop |
| Benefit reservations wired to auth | §13, §17 | auth approval → `BenefitAccumulatorService.reserve` |
| Patient-liability estimation | §18 | Coverage rules + COSTA price → estimate |
| Rules-service migration | §33 | move eligibility/auth rules into rules-service (8241) keys |
| Provider + payer workbenches | §26.2/26.4 | queues |

## Wave 3 — Claims and Remittance ⏸

| Epic | Spec §§ | Notes |
|---|---|---|
| Claims v2 | §19 | claim types, line-level statuses, lineage (corrected/replace/void/reverse), scrubbing §2.8 |
| Adjudication pipeline | §6 | line-level, accumulator-consuming, explainable (rule/contract/benefit versions) |
| EOB / remittance depth | §20 | citizen EOB + provider remittance advice |
| Settlement wiring | §21 | approved liability → MUSHEX `/mushex/v1/claims/{id}/remit` → `/settlements/run` → `/recon` |
| **BFF /finance/claims re-wire** | — | currently reads COSTA claim packs; reconcile onto `cv_claims` canon (FinanceController:453-473) |
| Claims switch formalization | Expansion §5 | routing/translation/ack stages; technical-ack ≠ approval |

## Wave 4 — Advanced Financing Operations ⏸

| Epic | Spec §§ | Notes |
|---|---|---|
| Coordination of benefits | §15 | payment waterfall; no double-claim |
| Employer / group admin | §24 | stage→validate→review→apply roster imports |
| Government programmes | §23 | beyond current subsidy model |
| Appeals depth | §22 | segregation of duties |
| Fraud / waste / abuse | §25 | risk flags + governed review |
| Capitation | §5, §19 | encounter reports |
| Payer gateway satellite | §32 | new `coverage-payer-gateway` on free port 8141+ |
| Real payer API verification | §7.1 | replace REGISTRY_ATTESTED with live payer response match |
| Offline store-and-forward | §35 | signed-token consumption, replay with idempotency |

---

## Acceptance ledger (spec §40 / §43 + Expansion §13)

Tracked as journeys are proven live. Wave 1 targets: citizen adds coverage; principal views
dependants; facility discovers coverage from a VITO-linked patient; receptionist runs a real
eligibility check returning network/benefit/co-payment/authorisation flags; a plan version is
published and immutable; a suspended payer blocks new enrolments; every material action is
auditable via the outbox. The remaining §40 criteria (auth end-to-end, claim from delivered care,
adjudication, EOB, settlement, reconciliation, appeals overturn, COB, waivers) map to Waves 2–4.
