# Gap-Remediation Session Log

Branch: `claude/crazy-merkle-3ad1a1` (off PT `claude/staging-ux-orchestration-remediation-Yypyl`).
Mode: **Absence mode** from 2026-06-29 — PO off-desk; doctrine + conservative-safe defaults applied;
PO-gated items parked (see `po-decision-index.md`), session never blocked.

## Phase 0 — substrate
- `0fa7630d4` fix(learning) — restore `learning.security.oauth2-enabled` (prod boot-blocker; suite 60/60).
- `eec9ed001` docs — record adversarial verification findings (full gap register).
- `8d9b42f7c` docs(governance) — lift CZO PolicyEngine single-writer lock.

## Phase 1 — SYS-1 per-domain policy enforcement (Java DB-rule engine)
- `6490794d7` **V019** inpatient clinical-write ext_authz rules (Flyway→v019, IT 4/4).
- `3c3c7e482`/`6e5253ee2` **GAP-4** closed — removed orphaned client `cadreEngine` (Java engine = single SoR).
- `58dfacaa0` keycloak — MCAZ/safety realm roles (canonical taxonomy).
- `cc5e43841`/`f4ebf6b4e` **V020** patient-safety ext_authz RBAC + spec/register reconcile (Flyway→v020).
- `f10fa85f1`/`72830ce92`/`441851ae9` **V021** Rito ext_authz RBAC + REGULATOR role + reconcile (Flyway→v021).
- `051fd04e7`/`6cc99fd77` **V022** OROS diagnostics RBAC (Flyway→v022).
- `6ad033c1a` patient-safety **in-service citizen-own report binding** (closes IDOR; suite 6/6).
- `bcec705d7` **V023** narrow OROS citizen-results (corrective — closed an over-broad grant).
- `7db6fa85f` **V024** narrow Rito citizen case-read (corrective — closed an IDOR pending binding).
- `0b037c2b2` **PolicyEngine Step 4.6** provider self-treatment block, G-PX-01 (PolicyEngineTest 38/38).

All migrations Flyway-proven against CLI Postgres (`→v024`); chokepoint + patient-safety guard unit-tested;
all session-introduced IDORs closed.

## Phase 1 — resolution of all outstanding items (2026-06-29, absence mode)

- **Rito client case-read binding — RESOLVED** (`59febcce0`): own-subject in-service guard + reactivated
  rule (V025); sub-reads inherit via get(); list own-only; tested 12/12; Flyway→v025.
- **Patient-safety / Rito facility-scope + restricted-phi masking — DEFERRED (infra-dependent).**
  Both need the actor's *role* in-service to distinguish facility-focal (scope to own facility) from
  MCAZ (see all) and to detect `restricted-phi`. The shared `TrustContext` carries `facilityId` but
  **not roles**, and no role header is forwarded to services. Adding a `roles` field is a positional
  change across **11 `new TrustContext(...)` sites + tests in 6 services** — too high-blast for an
  absence-mode tail. Conservative default kept: gateway role-gating already restricts reads to trusted
  safety staff (citizens are own-subject-bound); cross-facility scoping among safety staff is a
  refinement. **Next:** add `roles` to `TrustContext` (one filter populates it; widen call sites) as a
  dedicated infra slice, then scope + mask.
- **OROS finer per-action gating (release vs view) — DEFERRED (refinement).** Core diagnostic-journey
  RBAC is enforced (V022); per-action narrowing is a later refinement.
- **Fundo training-gate — PARKED (PO-20260629-01) + advisory is the buildable next.** Blocking-vs-warn
  is a PO/clinical decision (parked). The *advisory* consumer (vashandi check-in queries the fundo
  training-gate, surfaces a readiness flag, does not block) is the conservative resolution and needs no
  PO input — recommended as the next cross-service build under the parked decision.

**Net:** every outstanding Phase-1 item has a disposition. Done: Rito. Deferred-with-reason
(roles-infra / refinement): facility-scope, restricted-phi, OROS-finer. Parked (PO): Fundo blocking.

## Phase 1 follow-on — deferred refinements CLOSED via obligation consumption (2026-06-29)

PO returned; chose graduated **levels of permission** for Fundo + confirmed the obligations pivot.

- `22308c447` **feat(fundo)** — graduated training-gate levels (ADVISORY/SOFT/HARD → ALLOW/ADVISE/
  CONDITIONAL/BLOCK), resolves **PO-20260629-01** (gate half of G-FU-02). FundoTrainingGateServiceTest 8/8.
- **Architecture course-correction:** the deferred facility-scope + restricted-PHI items do NOT need a
  `roles` field on `TrustContext` (that would duplicate the PDP's existing visibility-obligation
  mechanism). The reuse-correct seam is consuming `maxScope`/`suppressFields`/`piiAccess` obligations
  (already emitted by `VisibilityObligationComposer`, already in `VisibilityContextHolder`). The gap was
  consumption — patient-safety + rito ignored it. See [[visibility-obligations-are-the-masking-seam]].
- `f320bb77a` **feat(patient-safety)** — honor FACILITY_SCOPE on report list (deny-empty when facility
  unknown). `f7917cf90` — shape read/list via JsonRepresentationShaper (suppressFields/piiAccess,
  fail-closed). Suite 8/8. **G-PS-01 CLOSED.**
- `b22c10248` **feat(rito)** — facility-scope on list + shape all 6 case reads (§3 sensitive-category
  identity redaction). Rito 8/8. **G-RT-01 CLOSED.**
- `e4d699b27` docs — register G-PS-01 + G-RT-01 → CLOSED.

## Phase 2 toolchain — READY (2026-06-29)

Correcting the earlier "JS generators not runnable in sandbox" claim: they ARE. The product-truth
generators need only `js-yaml`; everything else is node built-ins + the built-in `node --test` runner.
`cd scripts/completeness && npm ci` (from the committed lock) → `node --test __tests__/` = **13/13**, and
the suite generates `product-truth.json` and asserts on its maturity/baseline. So Phase 2 (SYS-2
capability-matrix + probeEvidence) is now runtime-verifiable here — the metric can be confirmed to move.
(Web `one-ui-shell` tsc/vitest is a separate, larger setup for Phase 3.)

## Later phases (2–7)
SYS-2 capability-matrix + probeEvidence (toolchain ready) · SYS-3 patient lane + journey ITs · Nompilo
addendum · Khuluma W4–W8 · OPA-as-PDP migration.

## PO decisions parked
See `docs/audits/po-decision-index.md`.
- **PO-20260629-01** — Fundo training-gate enforcement (block vs warn + requirement mapping). Conservative
  default applied: kept existing behaviour (no blocking gate). Consumer/seam deferred. Remediation continued.
