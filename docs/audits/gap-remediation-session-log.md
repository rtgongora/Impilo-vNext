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

## In progress / remaining (absence-mode continuation)
- Rito in-service own-subject case binding (then reactivate citizen-read).
- Patient-safety facility-scope + restricted-phi masking.
- Fundo training-gate enforcement seam (cross-service vashandi↔fundo).
- Phases 2–7 (SYS-2 capability-matrix, SYS-3 patient lane + journey ITs, Nompilo, Khuluma W4–W8, OPA-as-PDP).

## PO decisions parked
See `docs/audits/po-decision-index.md`.
- **PO-20260629-01** — Fundo training-gate enforcement (block vs warn + requirement mapping). Conservative
  default applied: kept existing behaviour (no blocking gate). Consumer/seam deferred. Remediation continued.
