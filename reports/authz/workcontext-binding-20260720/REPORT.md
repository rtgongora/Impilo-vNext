# Close the role/authz gaps — the WORK_CONTEXT duty token becomes enforceable

**Date:** 2026-07-20 · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
**Plane:** trust (tshepo-authz PDP, tshepo-identity, experience-bff, companion, envoy)
**Rollout:** SHADOW-first (deployed observe-only; PO flips to ENFORCE)

## The gap

An end-to-end trace showed the authorization spine was sound in shape but broken at the
last mile: **role = a static Keycloak claim**, and the Vashandi-proven **WORK_CONTEXT duty
token was minted then dead-ended** — the UI stored it in `sessionStorage` and never sent
it; nothing downstream read it. The PDP authorized off loose picker headers
(`X-Facility-ID`/`X-Provider-ID`) never bound to any proven token. Several of the ten
access dimensions were stubbed (workflow-state captured but unread; department/ward
opportunistic). Two live correctness defects compounded it: a **DENY branch that ignored
its own conditions** (a conditional DENY fired on every request that matched the coarse
role/action — `asset-deny-stock-as-asset`, keyed on role=* / resource=NULL, was denying
**every request in the seed tenant unconditionally**), and **`/v1/policies` runtime CRUD
with no guard**.

## What changed (5 phases, all committed)

- **A — carry the token on the wire** (`11c46fb30`): `CompanionHeaders.WORK_CONTEXT_TOKEN`;
  api-client injects it from the work-session store; BFF forwards it (plus the previously
  dropped department/ward/programme/workflow-state headers); `WorkContextController`
  enriches the minted token with ward/programme/org/assignment; `departmentId` UUID→String;
  identity emits the new context claims; envoy strips the token on the public lane.
- **B — the PDP consumes + binds the token, SHADOW** (`7e52b1770`): identity `introspect`
  now returns `tokenKind` + `contextClaims`; a new `IdentityIntrospectionClient` resolves
  the token (fail-open); `PolicyEngine.bindWorkContext` compares token↔headers
  (actor/facility/workspace/provider), audits `WORK_CONTEXT_MATCHED/MISMATCH/REVOKED` to
  the governance outbox, folds the proven duty role into the effective roles on a clean
  match, and **never denies in SHADOW**; ENFORCE denies a mutating request on a
  mismatched/revoked token. Gated by `tshepo.authz.work-context-mode` (OFF|SHADOW|ENFORCE).
- **C — correctness + governance** (`64dc6fd59`): the DENY branch now applies the same
  scope + `evaluateConditions` gating as ALLOW (monotonic — can only *reduce* over-denial);
  `/v1/policies` POST/PUT/DELETE require an admin session or internal SYSTEM caller.
- **D — the remaining dimensions become first-class** (`79f9c3633`): `evaluateConditions`
  gains `allowed/blocked_workflow_states`, `allowed_departments/wards/organisations`,
  `requires_provider_id` (dept/ward/org duty-token-authoritative); a `role_template_catalog`
  (V043) maps an opaque `roleTemplateId` → canonical policy role(s), folded at decision time.
- **E — prove + deploy + wire the introspect path** (`c654536e3`): the live proof, plus the
  two config gaps the live run surfaced (below).

## Proof (live, 14/14 green ×2)

`scripts/e2e/authz-workcontext-proof.sh` drives the **deployed** PDP directly (preview envoy
is a bare passthrough, so the PDP is not on the ingress path, and Tomcat won't expose a
`:path` pseudo-header — so the proof uses a fresh proof tenant + purpose-built rules keyed
on the fixed resource-type, exercising every dimension through the duty token + trust
headers):

1. **Loop closed** — a token proving `NURSE_ONCOLOGY_SNR` → catalog `CLINICIAN`, cardiology
   dept + provider id → **ALLOW**, with a `WORK_CONTEXT_MATCHED` governance audit.
2. **DENY-conditions fix** — the conditional DENY fires **only** when `X-Workflow-State`=
   `DISCHARGED` (403 POLICY_DENY); on `ACTIVE` the same rule stays silent and the request is
   allowed.
3–4. **Department + provider** dimensions gate as authored (oncology denied; missing
   provider id denied).
5–6. **Shadow binding never denies** — facility mismatch and a revoked token each emit the
   audit (`WORK_CONTEXT_MISMATCH`/`_REVOKED`) but do **not** produce a `WORK_TOKEN_*` denial.
7. **Guard** — unauthenticated `POST /v1/policies` → rejected.
8. **ENFORCE** — flipped to ENFORCE, a mismatched duty token on a mutating request →
   403 `WORK_TOKEN_CONTEXT_MISMATCH`; reverted to SHADOW.

Engine correctness is additionally locked by 157 authz + 21 identity unit tests (incl. new
conditional-DENY and dimension/role-template regression tests).

## Deployed (digest-pinned, preview)

- `tshepo-identity-service` @ `sha256:7317c7df…` (introspect returns claims + permits the
  internal introspect call)
- `tshepo-authz-service` @ `sha256:1ae09361…` (binding + DENY-fix + guard + dimensions;
  Flyway advanced V035→**V043**, the 8 pending migrations applied cleanly)
- `experience-bff` @ `sha256:804e3fdb…` (forwards the token + operational headers; built
  from a clean HEAD worktree to avoid shipping a concurrent session's uncommitted work)

Mode is left at **SHADOW**. **To flip: `TSHEPO_WORK_CONTEXT_MODE=ENFORCE`** on tshepo-authz.

## Notes / gaps the live run surfaced

- **`TSHEPO_IDENTITY_URL` was never set on the authz deploy** → introspection defaulted to
  `localhost:8181` and failed open (binding silently inert). Fixed in the runtime-values
  generator + generated file (+ `TSHEPO_WORK_CONTEXT_MODE=SHADOW`) so it survives a full-boot.
- **identity `/tokens/introspect` required a user JWT** → the service-to-service PDP call
  401'd. Now permitted (it only reports claims to a caller that already holds the token; it
  is not publicly routed).
- The **one-ui-shell** api-client change (inject the header) is committed but its browser
  wire-path only matters once the shell is redeployed; the header is inert under SHADOW and
  the API/DB proof does not depend on it. Shell redeploy is the one remaining deploy step.
- The **envoy public-lane strip** is correct for the production (ext_authz) config; the
  preview envoy is a minimal passthrough, so it is a no-op there.
