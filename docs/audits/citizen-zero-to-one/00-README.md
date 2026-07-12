# Citizen Zero-to-One Trust Journey — Audit & Completion Wave

**Branch:** `intake/citizen-zero-to-one` (off `intake/wave-b-tshepo-gdhcn-trust-primitives`)
**Worktree:** `/opt/impilo/repos/impilo-czo`
**Date:** 2026-06-25
**Owner:** Citizen Zero-to-One coordination session

> **Status update (2026-07-12):** the trust ladder in this audit set is now
> **canonicalized** — with renamed rungs (R0 Public → R1 Reachable → R2 Person-verified →
> R3 Strongly-authenticated → R4 Authorised-relationship → R5 High-assurance) — by
> [`docs/doctrine/health-services-gateway-doctrine.md`](../../doctrine/health-services-gateway-doctrine.md) §4,
> with clause status tracked in `docs/doctrine/doctrine-gap-matrix.md` §8 (GW-04).
> Since this audit was written, **G-CZO-01 (LOA→policy propagation), G-CZO-02 (public L0
> landing), G-CZO-03 (L5 delegation via mvumo + PDP Step 4.5), and G-CZO-04 (citizen
> step-up BFF)** have closed in code. This folder remains the point-in-time
> implementation evidence of record; per-gap current truth lives in the gap matrix and
> [`docs/architecture/gateway-experience-capability-map.md`](../../architecture/gateway-experience-capability-map.md).

## Mission

Prove that Impilo vNext lives in the practical world: an ordinary person can discover Impilo,
go to web/mobile, sign in or **sign UP without a pre-existing Health ID**, request a Health ID,
receive a temporary or full Health ID, and then access **only** the services/records/actions that
match their identity trust, login assurance, consent, and risk.

> **The rope to walk:** access without recklessness; security without exclusion; privacy without
> making care impossible; trust without making the system unusable.

## Doctrine constraints honoured by this audit

- **AUDIT-FIRST.** Substrate is ~65% built and deep. We complete; we do not rebuild.
- **SoR-first.** Reuse Vito (identity), Tshepo (trust/policy), identity-assurance (LOA),
  data-governance (privacy/DSR), tshepo-consent (clinical consent). Never fork a system-of-record.
- **No negative assertion without the disproving search.** Every "not built" below cites the grep/read.
- **Identity-safety doctrine.** Verification never uses sensitive clinical history as a prompt.
  (Verified: `vito-service/.../matching/MatchingEngine.java` scores demographic/administrative fields
  ONLY — given/family name, DOB, sex, phone-hash; no clinical terms.)

## The 10 Phase-0 outputs (this folder)

| # | File | Purpose |
|---|------|---------|
| 0 | [00-README.md](00-README.md) | This index |
| 1 | [01-journey-map.md](01-journey-map.md) | Citizen Zero-to-One journey (discovery → verified access) |
| 2 | [02-trust-ladder.md](02-trust-ladder.md) | L0–L5 ↔ Vito ↔ identity-assurance ↔ Banner cross-walk |
| 3 | [03-login-assurance-matrix.md](03-login-assurance-matrix.md) | Login method → risk → trust → access outcome |
| 4 | [04-dashboard-variation-matrix.md](04-dashboard-variation-matrix.md) | The 12 dashboard states |
| 5 | [05-current-implementation-audit.md](05-current-implementation-audit.md) | Every route/screen/API/policy/test, verified |
| 6 | [06-gap-register.md](06-gap-register.md) | Blocking / High / Medium / Cosmetic / Future |
| 7 | [07-product-truth-update.md](07-product-truth-update.md) | Honest product-truth delta |
| 8 | [08-accessibility-audit.md](08-accessibility-audit.md) | A11y reality vs. the inclusion principle |
| 9 | [09-persona-e2e-test-plan.md](09-persona-e2e-test-plan.md) | Personas A–J test plan |
| 10 | [10-patch-plan.md](10-patch-plan.md) | Sequenced, SoR-first implementation slices |

## Headline findings (verified, line-cited in the detail docs)

1. **[BLOCKING] LOA propagation break is real.** `PolicyEngine` evaluates `min_loa` against `loaLevel`
   sourced from the Keycloak ACR claim (`KeycloakAdapter.extractLoaLevel`), and `account_assurance_required`
   against a separate `assuranceLevel` **string** sourced from the `X-Assurance-Level` header. The BFF
   *forwards* `X-Assurance-Level` but **never populates it** from identity-assurance-service's `current_level`.
   Result: a self-service assurance upgrade (which DOES persist in identity-assurance `assurance_record`)
   changes nothing the policy sees. The L0→L5 ladder is not end-to-end.
2. **[BLOCKING] No public L0 entry.** Root `/` → `redirect("/home")` → auth gate. Public set is only
   `/auth /kiosk /verify /share /privacy /terms /consent /account-deletion` (`middleware.ts`). No guest
   landing, service finder, or emergency/public-health info exists pre-login.
3. **[HIGH] L5 delegated/caregiver access NOT built.** Role constants + a non-persisting
   `stubDelegatedPickup` only. This is a large net-new build → **scoped design + SoR question, then STOP for PO**
   (see [10-patch-plan.md](10-patch-plan.md) §Slice 3).
4. **[HIGH] Step-up UI absent on citizen sensitive actions** — but the reusable pattern already exists
   (`usePolicyDecision()` + `/share/claim`). This is wiring, not invention.
5. **[HIGH→MED] Mobile** — assurance banner absent (session never reads `assuranceLevel`). The clinical-record
   sections are **already fully wired** (not stubs, not duplicated) — the brief's stub concern is stale.
6. **[MED] Accessibility** — high-contrast exists in code but is not user-exposed; no low-data mode, no
   resumable forms, no SMS auth fallback.
7. **[MED] Consent** — clinical consent IS gating at PolicyEngine (fail-closed). The defect is that
   experience-bff capture endpoints don't persist (no datasource) and `/status`,`/history` are `[]` stubs.

See [06-gap-register.md](06-gap-register.md) for the full classified register and
[10-patch-plan.md](10-patch-plan.md) for the sequenced fix plan.
