# Ruvimbo — Coverage Wave 1 (Foundation) — Delivery Report

**Date:** 2026-07-19 · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
**Capability:** Ruvimbo (Coverage / Health Financing) · **Owner service:** `coverage-service` (8140)
**Spec:** [`docs/product/ruvimbo-coverage-specification.md`](../../../docs/product/ruvimbo-coverage-specification.md) ·
**Epics:** [`docs/roadmaps/ruvimbo-epics.md`](../../../docs/roadmaps/ruvimbo-epics.md)

## What shipped (spec §42 Wave 1 — Coverage Foundation)

Built at the completion-lens bar: real writes, real state transitions, live-proven through the
real ingress, no mocks/stubs/dead controls in production paths.

| Epic | Delivered |
|---|---|
| **Payer Registry** (§8) | `cv_payers` + CRUD/suspend/reactivate. A suspended payer blocks NEW enrolments (enforced in the enrolment path); historic memberships stay accessible. |
| **Plan hierarchy** (§9) | `cv_schemes → cv_products → cv_plan_versions`; the 15-value coverage-type enum; **PUBLISHED versions immutable** (re-publish → 400). Flat `cv_coverage_plans` retained as the live projection, backfilled + linked. |
| **Membership machine** (§10) | 13-status state machine with guarded transitions (illegal → 409); verification on a **separate axis** (§10.2); dependants via `principal_member_id` (§10.3); **duplicate-active-enrolment guard** (partial unique index + pre-check → 409, fixing the completion-lens nit). |
| **Benefits + accumulators** (§13) | `cv_benefit_definitions/_limits`; **reservation-aware** `cv_benefit_accumulators` (used + reserved) with atomic, idempotent-by-reference reserve/consume/release movements (`cv_benefit_movements`), modeled on the proven subsidy ledger. |
| **Eligibility v2 + tokens** (§12) | Explainable decision (status + reason codes + network/referral/auth flags + remaining benefit + copay + ruleset version + validity) persisted on `cv_eligibility_checks`; **signed HS256 offline eligibility tokens** (JDK HMAC) + verify endpoint. |
| **Experience** (§11, §26) | BFF passthroughs for all of the above (honest 4xx via `upstreamWrite`); citizen wallet gains a **"Your benefits"** panel (reservation-aware position); **Ruvimbo** brand front-and-centre (launcher tile, slug, logo, route). |

New event families (outbox): `coverage.payer.*`, `coverage.plan-version.published`,
`coverage.declared/verified/activated/suspended/terminated`, `benefit.reserved/consumed/released`,
`eligibility.checked`, `coverage.eligibility.token.issued`.

## Proof (live, through Traefik → envoy → experience-bff → coverage-service)

**`scripts/e2e/ruvimbo-foundation-proof.sh` — 33 ok / 0 fail, green ×2.** Self-contained on a
fresh payer; DB truth verified on every table:

```
payer → scheme → product → draft version → benefit → PUBLISH → re-publish 400 (immutable)
→ flat plan linked to payer+version → enrol principal → duplicate enrol 409 → verify (VERIFIED)
→ add dependant → benefit accumulators list → reserve 60 (remaining 40) → over-limit reserve 409
→ idempotent replay (no double-apply) → eligibility v2 (ELIGIBLE + reason codes + ruleset version)
→ signed token issued + verifies valid + tampered token rejected
→ suspend payer blocks new enrolment 409 → reactivate accepts 201
DB truth: cv_payers, cv_plan_versions PUBLISHED, membership VERIFIED, dependant linked,
          accumulator reserved 60.00, ONE movement row (idempotent), eligibility ruleset version,
          coverage.* events in outbox.
```

**Browser:** `ui/one-ui-shell/e2e/journeys/coverage-wallet.journey.spec.ts` — Ruvimbo launcher
tile + wallet renders with the "Your benefits" section.

## Migration validation

`V015__ruvimbo_foundation.sql` applied cleanly against a **schema+data clone of the live
`coverage` DB** before deploy (backfilled 2 payers/schemes/products/published versions, 10
benefits+limits, both seeded plans linked). Confirmed live post-deploy: `flyway=015`,
coverage-service booted (Hibernate `validate` passed against the migrated schema).

## Live bug caught + fixed by the proof

- **BFF `/members` swallowed every enrolment error into a blanket 400** (`ENROLL_FAILED`),
  hiding the real 409 (duplicate enrolment / suspended-payer) from the UI. Fixed to propagate
  the engine's status verbatim via `upstreamWrite()` — the UI now shows the true reason.

## Deployed (digest-pinned)

- `coverage-service` @ `sha256:54666b1d…` (V015 migrated live)
- `experience-bff` @ `sha256:db0e3bb8…`
- `one-ui-shell` @ `sha256:fb7914e5…` (Ruvimbo branding + wallet benefits)

Browser journey `coverage-wallet.journey.spec.ts` passed **green ×2** live; final API
proof re-run **33/33** after the shell deploy (no digest drift).

## Boundary honored

Coverage decides who pays & what's covered; **COSTA prices, MUSHEX moves money** — referenced,
never duplicated. `cv_claims` is the canonical claim (COSTA claim packs file into it; MUSHEX
settles it). Switch ≠ adjudication. G3 subsidy dual-lane untouched. Policy plane (Tshepo/OPA)
untouched.

## Waves 2–4 — DELIVERED + PROVEN LIVE (2026-07-20)

The PO opted to build all deferred waves continuously (internal rails honestly gated). Each
wave was built → migrated → deployed digest-pinned → proven live ×2 through the real ingress.

| Wave | Migration | Delivered | Proof |
|---|---|---|---|
| **W2 Authorisations & Estimates** | V016 | Referrals/gatekeeping (validate + emergency bypass + consume); full §17 14-status authorisation machine + line-level (approval reserves benefit, idempotent); patient-liability estimation (COSTA charge → coverage split, `ruvimbo-liability-v1`) | `ruvimbo-w2-proof.sh` **20/20 ×2** |
| **W3 Claims & Remittance** | V017 | Claims v2 (21-status, line-level, lineage void/reverse/replace, scrubbing); **real line-level adjudication** (auth-match + accumulator-consume + copay/coinsurance, explainable `ruvimbo-adjudication-v1`); citizen EOB; settlement instruction → `coverage.settlement.initiated` (MUSHEX payout credential-gated) | `ruvimbo-w3-proof.sh` **23/23 ×2** |
| **W4 Advanced Financing** | V018 | Coordination of benefits (priority waterfall, total payer ≤ allowed); employer/group admin (roster stage→validate→apply); fraud flags (screen → governed review, never auto-denies); capitation reports; Claims Switch/Payer Gateway (route + technical/business ack; external rails PENDING, not faked) | `ruvimbo-w4-proof.sh` **21/21 ×2** |

**Live bugs caught + fixed by the proofs:** W2 outbox idempotency-key collision on authorisation
create (`submitted`→`created`) + non-covered liability double-count; W3 fragile proof parse.
A pre-existing **foreign** test break (`AdminFacilityImportControllerTest`, an unrelated concurrent
`NdilaServiceClient` ctor change) was flagged as a separate chip, not fixed here; builds used
`-Dmaven.test.skip=true`. Every migration (V016/V017/V018) was clone-validated against the live DB
before deploy.

**Deployed live (all four waves):** coverage-service (`flyway=018`), experience-bff, one-ui-shell.

## Complete UI — every capability operable in-browser (2026-07-20)

The W2–W4 rails (and W1 admin surfaces) were API+BFF only; the PO asked that everything I built
have a complete UI. Delivered:

- **`useRuvimbo.ts`** — React Query hooks for every Wave 1–4 BFF endpoint (envelope-tolerant,
  idempotency via `apiClient`).
- **`/coverage/operations`** (ADMIN) — a 10-tab Ruvimbo Operations workbench: **Payers**
  (register/suspend/reactivate) · **Plans** (payer→scheme→product→version→publish + benefits) ·
  **Verification** queue (verify/dispute/terminate) · **Authorisations** (review → approve/deny
  all) · **Claims** (file → scrub → submit → adjudicate → EOB) · **Coordination** (waterfall) ·
  **Employers** (register → stage → validate → apply) · **Fraud** (screen → confirm/dismiss) ·
  **Capitation** · **Switch** monitor. Engine 4xx (409/400) surfaces verbatim — no fake success.
- **Citizen wallet** (`/coverage/member`) — added **Authorisations**, **Referrals**, and
  **Cost-estimate** sections alongside the existing plans/benefits/claims/appeals.
- Route registered ADMIN-gated; discoverability card on the legacy `/coverage` console.

**Browser-proven live ×2:** `coverage-operations.journey.spec.ts` — admin reaches the console,
sees the seeded payer registry, **registers a payer in-browser**, and Plans/Switch tabs render;
`coverage-wallet.journey.spec.ts` still green with the new sections. Deployed one-ui-shell
`@e9512598`. tsc clean.

## Deferred (registered in `ruvimbo-epics.md`)

- **W2** Authorisations & Estimates: referrals/gatekeeping, full §17 authorisation machine,
  concurrent/retrospective review, patient-liability estimation (with COSTA), rules-service
  migration, provider+payer workbenches.
- **W3** Claims & Remittance: claims v2 (line-level, lineage, scrubbing), real adjudication
  pipeline, EOB/remittance depth, MUSHEX settlement wiring, **BFF `/finance/claims` re-wire onto
  the `cv_claims` canon**, claims switch.
- **W4** Advanced: COB, employer/group admin, government programmes, appeals depth, fraud,
  capitation, payer-gateway satellite (port 8141+), real payer-API verification, offline.
