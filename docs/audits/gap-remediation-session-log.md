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

## Later phases (2–7)
SYS-2 capability-matrix + probeEvidence · SYS-3 patient lane + journey ITs · Nompilo addendum ·
Khuluma W4–W8 · OPA-as-PDP migration.

## PO decisions parked
See `docs/audits/po-decision-index.md`.
- **PO-20260629-01** — Fundo training-gate enforcement (block vs warn + requirement mapping). Conservative
  default applied: kept existing behaviour (no blocking gate). Consumer/seam deferred. Remediation continued.
