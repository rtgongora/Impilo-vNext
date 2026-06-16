# Session Experience Authority & Remediation

> **Status:** DRAFT for Product Owner review. Documents only — no code, config, or cluster
> changes are authorized by this document. Implementation is held until the Product Owner
> approves the design and the phased roadmap.
>
> **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
> **HEAD at audit:** `c19fefd1`
> **Audit run:** 2026-06-16 (preview namespace `impilo-full-preview`, public ingress `http://41.57.127.235`)

This document is the canonical design and gap analysis for the post-login experience. It:

1. Reconciles the current preview against the prior Admin-Governance build and its
   preview-visibility audit (**Deliverable 0**), classified against the audit's A–F outcome
   taxonomy with concrete evidence.
2. States the doctrine-correct, **server-authoritative** target (**Deliverable 1**).
3. Inventories every client-side divergence and recent band-aid with a KEEP/REPLACE
   disposition (**Deliverable 1**).
4. Lays out a phased, independently-verifiable E2E remediation roadmap (**Deliverable 2**).
5. Designs the automated E2E verification harness and encodes the audit's Section K doctrine
   invariants as explicit acceptance gates (**Deliverable 4** + gates).

The companion seeding/bootstrap design is in
[`docs/environment/PREVIEW_ROLE_SEEDING_PLAN.md`](../environment/PREVIEW_ROLE_SEEDING_PLAN.md)
(**Deliverable 3**).

---

## 0. Deliverable 0 — Reconciliation against the prior build + preview-visibility audit

The prior thread on this branch deliberately built the Administration & Governance /
Bootstrap / Onboarding / Invitations / Bulk Import stack and authored a structured
preview-visibility audit. Both are treated here as **primary original-intent evidence**, not
background. This section verifies that build (it is **not** rebuilt) and classifies the live
preview against the audit's failure taxonomy.

### 0.1 Audit outcome taxonomy (diagnosis frame)

| Outcome | Meaning |
|---|---|
| **A** | Implemented, deployed, and correctly visible/authorized at runtime. |
| **B** | In git but the preview is not running it (stale images / deploy drift). |
| **C** | Running, but hidden by the Session Experience Contract / actor lacks authority. |
| **D** | Running, but a dependency is degraded (e.g. missing downstream URL / unreachable). |
| **E** | Route/endpoint missing or returns 404-for-implemented. |
| **F** | Tests pass but the runtime is broken (e.g. 500 on a real call). |

### 0.2 Implementation baseline — VERIFIED PRESENT (git evidence)

All expected baseline commits are present and reachable from `HEAD` (`c19fefd1`, 6 commits
past `fc30788d`):

| Commit | Subject |
|---|---|
| `b0c6d917` | feat(admin-governance): add bootstrap mode and delegated bulk onboarding |
| `eaa65c5f` | fix(admin-governance): clear preview gate blockers for bootstrap onboarding |
| `16b88421` | fix(admin-governance): resolve BFF compile and sidebar test regressions |
| `8510069f` | fix(test): restore home and session experience gate regressions |
| `a94e4fba` | feat(admin-governance): wire Keycloak activation and invitation delivery |
| `fc30788d` | feat(admin-governance): add invitation lifecycle UI and BFF endpoints |

Six later commits (`15efd19b` → `c19fefd1`) added BFF WGV/Health-ID/PO wiring and the
sovereign-national-admin regression test. **Working tree is dirty**: uncommitted edits exist
in exactly the divergence-relevant files — `AuthSessionController.java`, `ExperienceSidebar.tsx`,
`AuthGuardProvider.tsx`, `administration-governance/access.ts`, plus a new untracked
`ui/one-ui-shell/src/lib/provider-activation.ts`. **These uncommitted changes are in no built
image**, so the running preview can be at most `c19fefd1`.

> **Caveat for all "running commit" claims:** the deployed `experience-bff` image carries
> **no git build metadata** — `GET http://41.57.127.235/health/version` returns
> `{"service":"experience-bff","environment":"full-preview","branch":"","commit":"","buildDate":""}`.
> Commit attribution therefore relies on **behaviour probes + image digests**, never on
> `/health/version`. This is itself an audit finding (see gate D-2 in §5).

### 0.3 Deploy truth — image digests (running estate vs committed/reported)

Running pod images in `impilo-full-preview` (the public stack; `SINGLE_PUBLIC_STACK: yes`):

| Service | Running pod digest | `values-full-preview-digests.generated.yaml` | `runtime-image-truth.md` |
|---|---|---|---|
| `one-ui-shell` | `sha256:c6be550e…` | `sha256:0d4b4fad…` | `sha256:5d287d83…` |
| `experience-bff` | `sha256:2604e2c6…` | `sha256:9de8745d…` | `sha256:9efd0cef…` |
| `workforce-governance-service` | `sha256:7b219c72…` | `sha256:7b219c72…` ✅ | `sha256:7b219c72…` ✅ |

- **WGV is internally consistent** across running pod, committed generated digests, and the
  truth report. No drift.
- **`one-ui-shell` and `experience-bff` show three mutually-inconsistent digests.** The
  running pods match neither the committed generated digest file nor `runtime-image-truth.md`.
  The truth report was generated for commit `76e56c7e` (2026-06-15T19:10Z); the digest file
  was regenerated later (2026-06-15T22:46Z); the running pods are a third pair. This is a
  **deploy-truth integrity gap** — the committed/reported artifacts do not describe the
  running shell/BFF.

> **Outcome B risk (qualified):** We cannot prove the shell/BFF pods are *stale* without a
> registry→commit timestamp map (build metadata is absent). What is proven is that the
> recorded artifacts disagree with the running estate for shell + BFF. Behaviour probing
> (below) is therefore the authoritative signal, exactly as doctrine requires
> ("deployment truth is the running estate, not the deployment story").

### 0.4 Runtime behaviour & contract visibility (the core finding)

**Live Session Experience Contract** (`GET /internal/v1/session/experience`, trust headers
set):

| Actor passed | `identityType` | `tabs.work.visible` | `visibleManagementWorkspaces` | `visibleActions` | `contractVersion` |
|---|---|---|---|---|---|
| `superadmin` (intended admin) | `citizen` | `false` | `[]` (0) | `["personal.profile.view"]` | `1.1.0` |
| `b0000000-…-000000000010` (PO allowlist) | `citizen` | **`true`** | **26** (national_*, mohcc_*) | 3 | `1.1.0` |

Interpretation:

- The **only** path that produces governance authority in the live preview today is the
  **Product Owner email/actor allowlist** (`IMPILO_PREVIEW_PRODUCT_OWNER_ACCESS=true`,
  `IMPILO_PREVIEW_PRODUCT_OWNER_ALLOWED_ACTORS=b0000000-…-010,superadmin@impilo.gov.zw`).
  When the allowlisted actor logs in, 26 management workspaces light up.
- An actor *intended* to be the superadmin but **not** matching the allowlist string resolves
  to **citizen-only** with **zero** management workspaces, even though WGV holds seeded
  assignments. The contract does **not** derive authority from the seeded WGV chain for that
  actor → this is the seeding/identity-alignment gap (see seeding plan), surfaced as the
  contract rendering citizen-only.
- `previewProductOwnerAccess: true` **is** emitted in the runtime `policyMetadata`
  (`SessionExperienceService.java:172`) and asserted by `SessionExperienceServiceTest`, but is
  **absent from the TS `SessionExperienceContract` interface**
  (`contracts/trust/types/session-experience-contract.ts`) — a contract-shape drift.
- `contractVersion` is `1.1.0` at runtime
  (`SessionExperienceService.java:98,169`) versus
  `SESSION_EXPERIENCE_CONTRACT_VERSION = "1.2.0"` in the TS contract
  (`contracts/trust/types/session-experience-contract.ts:103`). **Confirmed version drift.**

This is the audit's **Outcome C** for the governance experience: the feature is running, but
the Session Experience Contract hides it for every non-allowlisted actor because authority is
not flowing from the seeded WGV chain.

### 0.5 Route + endpoint smoke

| Probe | Result | Classification |
|---|---|---|
| Shell route `GET /administration` | `307` (redirect, not 404) | A — route exists |
| BFF `GET /internal/v1/bootstrap/status` | `200` | A |
| BFF `GET /internal/v1/admin-governance/organisations` | `500` `INTERNAL_ERROR` | **F** — runtime broken |
| WGV `GET /v1/internal/governance/organisations` (in-cluster, no tenant hdr) | `500` `Missing X-Tenant-ID trust header` | probe artifact |
| WGV `GET /v1/internal/governance/organisations` (in-cluster, `X-Tenant-ID: default`) | `500` | **F** — see root cause |

**Root cause of the 500:** `GovernanceInternalController.tenant()` resolves
`TrustContextHolder.require().tenantId()` as a **UUID**
(`GovernanceInternalController.java:80–86`). A non-UUID tenant header value (`default`, which
is what the probe and the BFF forward) cannot be parsed to a `UUID`, so the request fails with
a 500 instead of a 400. The A&G **organisations listing path is genuinely broken at runtime**
because of tenant-ID format handling between the BFF and WGV — not because the capability is
missing. Endpoints that don't require the WGV org list (`bootstrap/status`) work.

### 0.6 Migration + dependency checks

- **WGV migration applied.** Live `workforce_governance` DB contains all expected tables:
  `wgv_access_request`, `wgv_hsc_employment`, `wgv_organisation_membership`,
  `wgv_authorised_representative`, `wgv_import_batch`, `wgv_import_row`,
  `wgv_import_exception` (plus `wgv_organisation`, `wgv_role_definition`, `wgv_assignment`,
  `wgv_bootstrap_account`, etc.). `V002__access_requests_hsc_membership.sql` and
  `V003__bootstrap_imports_authorised_reps.sql` are present in the image and materialized.
- **Seed state (confirms the plan's "current state"):** `wgv_organisation` = **2**,
  `wgv_role_definition` = **2**, `wgv_assignment` = **4**, `wgv_organisation_membership` = **0**.
- **Downstream wiring is present (Outcome D is NOT currently in play).** The running BFF env
  has `VARAPI_BASE_URL=http://varapi-service:8083`, `TUSO_BASE_URL=http://tuso-service:8084`,
  and `WORKFORCE_GOVERNANCE_BASE_URL=http://workforce-governance-service:8165` — all
  in-cluster, none localhost. `check-bff-downstream-mappings.sh` passes. The earlier
  `VARAPI_BASE_URL` patch is therefore effective and is a **KEEP**.

### 0.7 Deliverable 0 outcome summary

| Capability / surface | Outcome | Evidence |
|---|---|---|
| Admin-Governance build present in git | **A** | All 6 baseline commits reachable from `c19fefd1`. |
| Shell + BFF deploy-truth | **B (qualified)** | Running shell/BFF digests disagree with both committed digest file and truth report; no build metadata to confirm staleness. |
| WGV deploy-truth | **A** | Running digest `7b219c72…` matches committed + reported. |
| Governance experience visibility | **C** | Non-allowlisted admin actor → citizen-only, 0 management workspaces; only the PO allowlist surfaces the 26 workspaces. |
| Downstream dependency wiring | **A** (not D) | VARAPI/TUSO/WGV URLs present in-cluster; downstream guard passes. |
| A&G organisations listing | **F** | BFF + WGV `/organisations` return 500 due to non-UUID tenant header parsing. |
| Bootstrap status / A&G route | **A** | `bootstrap/status` 200; `/administration` 307 (exists). |
| WGV schema/migration | **A** | All `wgv_*` tables present; V002/V003 applied. |
| Contract version + PO field | **C/contract-drift** | Runtime `1.1.0` vs TS `1.2.0`; `previewProductOwnerAccess` emitted but missing from TS interface. |

**Net diagnosis.** The prior build is real, present, and (for WGV) deployed truthfully. The
"can't see it" symptom is **not** a missing feature (not E) and **not** a missing downstream
URL (not D, anymore). It is **Outcome C** — the Session Experience Contract only grants
governance authority via the preview PO allowlist, not via the seeded WGV chain — compounded
by an **Outcome F** runtime bug on the organisations listing (tenant-UUID parsing) and a
**deploy-truth integrity gap (B)** for the shell/BFF images. Each recent patch addressed one
of these in isolation; this document fixes the system instead.

---

## 1. Deliverable 1 — Canonical target & gap analysis

### 1.1 How it should work (server-authoritative)

The **BFF Session Experience Contract is the single source of truth** for what the shell
renders: tabs, zones, visible/blocked workspaces, visible/blocked actions, friendly
resolution state, and `defaultRoute`. **TSHEPO/OPA is authoritative** for action and API
enforcement. The client **renders the contract** and must **never re-derive visibility** from
Keycloak roles or provider-activation state.

```
Login → BFF resolves Session Experience Contract (identity + WGV + OPA)
      → Shell renders tabs/zones/workspaces/defaultRoute FROM the contract
      → Any action/API call → Envoy → TSHEPO ext_authz → service
```

Grounding: [`docs/doctrine/health-os-doctrine.md`](../doctrine/health-os-doctrine.md),
[`contracts/trust/types/session-experience-contract.ts`](../../contracts/trust/types/session-experience-contract.ts),
[`contracts/trust/resolver/session-experience-resolver.ts`](../../contracts/trust/resolver/session-experience-resolver.ts).

### 1.2 Three-tab + governance model

- **Personal** (Health ID) → always available; route `/home`.
- **Professional** (verified Provider/Worker ID) → route `/professional`.
- **Work** (active WorkAssignment) → route `/provider-workspace` and the `/work/*` surfaces.

A Work assignment's `subjectType` may be `system_admin`, `regulator`, or `marketplace_actor`.
**Administration & Governance derives from organisation type, not clinical licensure**
([`contracts/trust/resolver/organisation-governance.ts`](../../contracts/trust/resolver/organisation-governance.ts)).
An operator must reach A&G via a Staff/Admin identity + WGV org assignment — **not** via
`/provider/activate` (which is clinical).

### 1.3 The convergence insight (this is NOT a redesign)

Administration & Governance was **already built** to the contract-authoritative model:

- `hasAdministrationGovernanceEntry(contract)` — derives the A&G entry purely from
  `contract.tabs.work.visible` + `contract.visibleManagementWorkspaces`
  (`ui/one-ui-shell/src/lib/administration-governance/access.ts:8–13`).
- `canAccessAdministrationPath(contract, pathname)` — per-prefix workspace gating off the
  contract (`access.ts:92–125`).
- `tileEnabled` / `workspaceAllowed` / friendly blocked states — all contract-driven
  (`access.ts:29–90`). There is **no generic global "User Management."**

So the remediation is **converging the rest of the shell onto a pattern this feature already
follows**. The divergence is that the surrounding shell still computes visibility from
`resolveIdentityContext`/`isCitizenOnly` (client-side) instead of the contract — and then
needs a special-case band-aid (`isWorkZoneGrantedBySession`) to re-admit the A&G case it just
excluded.

### 1.4 Divergence inventory (client overrides that beat the contract)

| # | File | Divergence | Disposition |
|---|---|---|---|
| 1 | `ui/one-ui-shell/src/lib/resolve-post-login-destination.ts` | Post-login routing runs entirely off `resolveIdentityContext` (`isCitizenOnly`, `hasWorkAccess`, …), not `contract.defaultRoute`/tabs. | **REPLACE** (P1) |
| 2 | `ui/one-ui-shell/src/app/auth/resolving/page.tsx` | Resolving screen drives destination from the client identity context. | **REPLACE** (P1) |
| 3 | `ui/one-ui-shell/src/lib/identity-context.ts` | `resolveIdentityContext` + `isCitizenOnly` is the de-facto authority for shell visibility. | **DEMOTE** to offline/dev fallback only (P1/P3) |
| 4 | `ui/one-ui-shell/src/components/navigation/ExperienceSidebar.tsx:460–461` | `citizenOnly = isCitizenOnly(user)` (client) then `sessionWorkZone = isWorkZoneGrantedBySession(contract)` band-aid to re-admit A&G. | **REPLACE** (P1) — render zones from contract; remove the special case |
| 5 | `ui/one-ui-shell/src/providers/AuthGuardProvider.tsx` | Guards consult client identity context / `isCitizenOnly`. | **REPLACE** (P1) — use `sessionContractAllowsRoute` + `canAccessAdministrationPath` |
| 6 | `ui/one-ui-shell/src/app/home/page.tsx` | Home composes from client identity rather than contract tabs/workspaces. | **REPLACE** (P1) |
| 7 | WorkspaceContextSwitcher | Context options derived client-side. | **REPLACE** (P1) |
| 8 | Nompilo route context (`ui/one-ui-shell/src/lib/nompilo-route-context.ts`) | Re-derives identity/visibility. | **ALIGN** to contract (P1) |
| 9 | `/auth/context-chooser` | Context list not contract-driven. | **REPLACE** (P1) |
| 10 | `ui/one-ui-shell/src/hooks/useSessionExperienceContract.ts:33–36` | **Silent `catch` fallback** to `resolveLocalSessionContract`, which **drops `previewProductOwnerAccess`** and any server-only authority. In preview this can mask an authorization failure as a (wrong) success. | **REPLACE** (P3) — fail loud in preview; fallback dev/offline only |
| 11 | `provider/activate` as a governance gate | Operators forced through clinical provider activation to reach governance. | **REPLACE** (P2) |
| 12 | `lib/administration-governance/access.ts:15–27` `isWorkZoneGrantedBySession` | Band-aid that exists only because the sidebar excludes work zone via `isCitizenOnly`. | **REPLACE/REMOVE** once #4 is contract-driven (P3) |
| 13 | Unused `sessionContractAllowsRoute` | The contract-driven route gate exists but is not wired into guards. | **WIRE IN** (P1) |

### 1.5 Band-aid disposition (recent changes)

| Change | Disposition | Rationale |
|---|---|---|
| `VARAPI_BASE_URL` / `TUSO_BASE_URL` / `WORKFORCE_GOVERNANCE_BASE_URL` wiring in BFF env + committed Helm values | **KEEP** | Legitimate config; verified present + downstream guard passes. Move fully to config-as-code (P4). |
| Provider-listing response normalization | **KEEP** | Legitimate data-shape fix. |
| `previewProductOwnerAccess` allowlist as the path to governance | **REMOVE** | Per Product Owner directive (2026-06-16): do not bend the product to accommodate the PO. The `superadmin` account **is** the PO — one identity, not two. PO authority must come entirely from **seeded WGV assignments** on that single account. The email/actor allowlist short-circuit is deleted, not merely demoted. See doctrine gate K-1. |
| Sidebar `isWorkZoneGrantedBySession` special case | **REPLACE/REMOVE** | Only needed because the shell excludes work zone via `isCitizenOnly`. |
| `isCitizenOnly`-driven post-login routing | **REPLACE** | Contract `defaultRoute`/tabs must drive routing. |
| Silent contract fallback dropping PO override | **REPLACE** | Must not mask authorization in preview. |

### 1.6 Doctrine reconciliation (flagged + resolution)

- **Access dimensions: 12 vs 10.** Doctrine §20 enumerates 12 access dimensions; `CLAUDE.md`
  and `AGENTS.md` list 10. **Resolution:** treat doctrine §20 as canonical; the 10-dimension
  summaries are shorthand. The design proceeds on the doctrine set and the remediation must
  not assume only 10. (Documentation alignment is a docs follow-up, not code.)
- **Contract version drift: runtime `1.1.0` vs TS `1.2.0`.** **Resolution:** the BFF must emit
  the same version constant the TS contract declares; pin both to a single
  `SESSION_EXPERIENCE_CONTRACT_VERSION` source during P1.
- **`previewProductOwnerAccess` missing from the TS interface.** **Resolution:** add it to the
  TS `SessionExperienceContract.policyMetadata` (typed, optional, preview-only) so the client
  models what the BFF actually emits — then the silent fallback can stop dropping it. This is
  a *type* fix; the *authority* still moves to WGV (P2/P3).

---

## 2. Deliverable 2 — Phased E2E remediation roadmap (executed only after approval)

Each phase is independently verifiable by the harness in §4. No code is written until the
Product Owner approves.

- **Phase 1 — Make the contract authoritative in the shell.** Drive tabs/zones/route/guards
  from `contract.tabs.*`, `contract.visibleManagementWorkspaces`, `contract.defaultRoute`,
  and wire the unused `sessionContractAllowsRoute`. Demote `resolveIdentityContext` to an
  offline/dev fallback only. Reconcile the contract version (1.1.0↔1.2.0) and add
  `previewProductOwnerAccess` to the TS interface. *Verifiable by:* per-persona contract
  assertions + route-guard assertions matching the contract.
- **Phase 2 — Decouple governance from clinical provider activation.** Operator/governance
  personas reach A&G via Staff/Admin identity + WGV org assignment without `/provider/activate`;
  reserve provider activation for clinical routes. *Verifiable by:* an operator persona (no
  clinical provider) reaching `/work/*` governance from a seeded WGV assignment.
- **Phase 3 — Remove the band-aids** from §1.4 once P1–P2 cover them: delete the sidebar
  `isWorkZoneGrantedBySession` special case (#4, #12), and make the silent contract fallback
  (#10) fail loud in preview so it cannot mask authorization. *Verifiable by:* a no-stubs /
  no-silent-fallback assertion + the band-aid files no longer present.
- **Phase 4 — Config-as-code.** Ensure all BFF downstream URLs (VARAPI/TUSO/WGV) live in
  committed Helm values + the generated env file so no hand-patched live pod env is ever
  required again. *Verifiable by:* rendered Helm values equal the running env; downstream
  guard passes from a clean render.

**Also fix in P1/P2 (from Deliverable 0):**

- **Outcome F (tenant UUID):** make WGV `tenant()` reject a non-UUID `X-Tenant-ID` with a 400
  and ensure the BFF forwards a canonical UUID tenant. (Implementation held; tracked here.)
- **Deploy-truth integrity (B):** bake git commit/branch/buildDate into the BFF and shell
  images so `/health/version` is authoritative, and regenerate the digest artifacts from the
  running estate. (Implementation held; tracked here.)

---

## 3. Deliverable 3 — Seeding & bootstrap

See [`docs/environment/PREVIEW_ROLE_SEEDING_PLAN.md`](../environment/PREVIEW_ROLE_SEEDING_PLAN.md).
Deliverable 0 confirmed its premise: WGV holds 2 orgs / 2 role defs / 4 assignments / 0
memberships, and the only actor that renders governance authority is the PO allowlist entry.

---

## 4. Deliverable 4 — Automated E2E verification harness (design)

Adopt the prior audit's structured method (its Sections C–K) as the repeatable backbone,
replacing ad-hoc curl probing. Per seeded persona, the harness logs in, fetches
`/internal/v1/session/experience`, asserts the expected tabs/zones/management
workspaces/default route, then drives the UI journey headless.

Layered checks (mapped to the audit sections proven in Deliverable 0):

- **Deploy truth (audit C):** running pod image digests for `one-ui-shell`,
  `experience-bff`, `workforce-governance-service` match the deployed commit; no stale
  registry/containerd or cached browser bundle. (Deliverable 0 found this gap for shell/BFF.)
- **Contract visibility (audit D):** assert `visibleManagementWorkspaces` / `visibleActions` /
  `friendlyResolutionState` per persona; prove why A&G is shown or hidden. (Deliverable 0
  found citizen-only for non-allowlisted admin.)
- **Route + endpoint smoke (audit E/F):** A&G/bootstrap routes resolve (parity not 404) and
  `/internal/v1/admin-governance/*`, `/internal/v1/bootstrap/status`, WGV
  `/v1/internal/governance/*` return 200/401/403/`pending_backend` — never 404-for-implemented
  or fake success. (Deliverable 0 found 500 on `/organisations`.)
- **Migration + dependency (audit G/H):** WGV tables present; Keycloak/notification/Redis/WGV/
  Tshepo-audit/VARAPI reachability or expected degraded states.
- **Report format (audit J) + doctrine gates (audit K):** every run concludes with one of
  outcomes A–F and asserts the §5 invariants.

**Where it lives / how it runs.** A new harness (proposed `scripts/test/verify-session-experience-e2e.sh`
plus a Playwright project under `ui/one-ui-shell/e2e/personas/`) integrated as a stage in
`scripts/pipeline/run-local-quality-gates.sh` and surfaced in `cursor-local-feedback.sh`.
Per-persona acceptance matrix is defined in the seeding plan.

---

## 5. Doctrine gates (audit Section K) as explicit acceptance gates

Each gate must be asserted by the harness for every run.

| Gate | Invariant | How asserted |
|---|---|---|
| **K-1** | **No hidden permanent superuser, and no PO short-circuit.** Per PO directive, `superadmin` **is** the PO (one identity). The `previewProductOwnerAccess` allowlist is **REMOVED**, not retained. PO authority comes entirely from seeded WGV assignments on the `superadmin`/PO account. | After removal, the PO logs in and resolves full authority from WGV alone; no allowlist code path remains. |
| **K-2** | **Bootstrap closes.** `national_bootstrap_administrator` is bootstrapOnly / notPermanentSuperuser / closes after activation. | `bootstrap/status` transitions to closed after activation; no permanent bootstrap authority. |
| **K-3** | **Authorised representatives are org-scoped.** | Rep actions limited to their `wgv_authorised_representative` org. |
| **K-4** | **No fake Keycloak/notification/audit success.** | Failed downstreams surface real errors (no stubbed 200s). |
| **K-5** | **No spreadsheet auto-activation.** Bulk import does not auto-activate users. | Imported rows require explicit activation. |
| **K-6** | **Revoked invitations are unusable.** | Revoked invitation token rejected. |
| **K-7** | **A&G is contract-filtered.** | A&G entry/tiles driven only by `hasAdministrationGovernanceEntry` / `canAccessAdministrationPath`. |
| **D-2** | **Deploy truth is authoritative.** `/health/version` reports the real commit; running digests match committed artifacts. | Build metadata baked in; digest reconciliation passes. |

**Product Owner override disposition:** **REMOVE** (PO directive, 2026-06-16). The product is
not modified to accommodate the PO; instead the PO/`superadmin` account is seeded with enough
real WGV authority that the standard contract resolution grants the access. The
`previewProductOwnerAccess` allowlist and its env flags (`IMPILO_PREVIEW_PRODUCT_OWNER_ACCESS`,
`IMPILO_PREVIEW_PRODUCT_OWNER_ALLOWED_ACTORS`) are deleted once the seed is in place.

---

## 6. Acceptance criteria for the documents

- [x] Every divergence point and recent patch is listed with file path and KEEP/REPLACE
      disposition (§1.4, §1.5).
- [x] The canonical post-login sequence is unambiguous and cites doctrine + contract (§1.1–1.2).
- [ ] The seeding plan enumerates exact rows/users per persona and the chain that renders each
      correctly (see seeding plan; cross-checked here).
- [x] The remediation is phased so each phase is independently verifiable by the harness (§2, §4).
- [x] The prior Admin-Governance build is verified against the A–F taxonomy with concrete
      evidence — git, deploy digest, runtime route, contract, endpoint — never "implemented
      because tests passed" (§0).
- [x] Every audit Section K invariant is an explicit acceptance gate, incl. the PO override as
      preview-only / REPLACE (§5).

---

## 7. Explicitly out of scope until approval

No edits to BFF/UI/Helm/seed code, no image builds, no cluster changes. The live BFF env
(VARAPI/TUSO/WGV URLs) and the committed values change remain as-is and are documented as
**KEEP**. The 500 on `/organisations`, the deploy-truth integrity gap, and all REPLACE
band-aids are **tracked, not yet fixed**.
