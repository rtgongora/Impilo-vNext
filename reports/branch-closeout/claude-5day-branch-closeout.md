# Claude 5-Day Branch Closeout Report

Date: 2026-06-26
Canonical Product Truth branch: `claude/staging-ux-orchestration-remediation-Yypyl`
Final Product Truth HEAD: `5e99cf340` (integration tip; this report commit becomes the new tip — see `git log` / final validation)
Scope: Claude-created or Claude-worked branches with activity in the last 5 days (commit activity ≥ 2026-06-21).

> **Important baseline note.** The local Product Truth worktree (`/opt/impilo/repos/impilo-closeout`)
> was found **79 commits AHEAD of `origin/claude/staging-ux-orchestration-remediation-Yypyl`**
> (`6d522d291`) — a prior closeout session had merged real work into Product Truth locally but
> **never pushed it**. Those 79 commits (dated 2026-06-24 → 2026-06-26) are themselves inside the
> 5-day window and are fast-forward-safe. They were adopted as the working baseline (`19121074f`),
> and the closeout proceeded on top of them. The final push publishes the prior 79 commits **plus**
> this closeout's integrations.

## Summary

- Candidate Claude branches reviewed: **23**
- Branches merged: **2** (`intake/rito-quality-safety`, `intake/fundo-lms`)
- Branches cherry-picked: **0** (the 4 small-fix branches that were candidates for cherry-pick turned out already-absorbed by content — see below)
- Branches already absorbed: **14**
- Branches superseded: **1** (`intake/rito-design`)
- Branches abandoned: **0**
- Branches deleted: **17** (14 already-absorbed + 1 superseded + 2 merged)
- Branches left open for human review: **6**

Plus two operator-created safety/preservation branches (not candidates): a pre-closeout safety
snapshot and a WIP-preservation branch (kept, see Risks).

## Candidate identification method

All commits in this repo are authored/committed under the single git identity
"Robert Tawanda Gongora", so **author/committer name does not distinguish Claude from human**.
Claude-worked branches were therefore identified by **branch-naming convention** and
**commit-message style**, cross-checked against the 5-day activity window:

1. **Window**: `git for-each-ref refs/remotes/origin --sort=-committerdate` → kept only branches whose
   last commit date is ≥ 2026-06-21 (5 days before 2026-06-26).
2. **Claude evidence**: `intake/*`, `integration/*`, `claude/*` naming; Conventional-Commit subjects
   carrying wave/gap markers from the Claude waves (e.g. `L2 W6`, `L3 W5`, `G003`, `GAP-2`, `C8/C9`,
   `(M1,M2,M3)`, `de-fabrication`, `honest gaps`).
3. **Exclusions**: the canonical Product Truth branch itself; operator safety/WIP branches I created
   this session; non-Claude human branches (`origin/staging` by `tndangana`, `origin/ioptime/dev` by
   `fwdali1824`, `peter/*`, `mbaradza/*`, `fix-*`); and branches whose last activity is ≤ 2026-06-18
   (outside the window, no Claude activity within it).
4. **Real-delta refinement**: SHA-based ahead/behind (`git rev-list --left-right`) **overstates**
   real deltas because the same logical work landed via different SHAs. Each candidate was therefore
   re-measured by **patch-id** (`git cherry <PT> <branch>`): `+` = genuinely new content, `-` = already
   present in PT by content. Classification used the patch-id result, not the SHA count.

## Branch inventory

Ahead/behind are SHA-based vs local PT baseline `19121074f`. "New(+)" is the patch-id count of
genuinely-unabsorbed commits.

| Branch | Last commit | Author | Committer | Ahead/Behind | New(+) | Classification | Action taken | Notes |
|---|---:|---|---|---:|---:|---|---|---|
| intake/rito-quality-safety | 2026-06-26 | R.T. Gongora | R.T. Gongora | 13/79 | 13 | MERGE_NOW | Merged `b61683873` | New svc 8391; compiles; registry +1 |
| intake/fundo-lms | 2026-06-26 | R.T. Gongora | R.T. Gongora | 27/79 | 27 | MERGE_NOW | Merged `5e99cf340` | LMS expansion; clean auto-merge; compiles |
| intake/rito-design | 2026-06-26 | R.T. Gongora | R.T. Gongora | 0/73 | 0 | SUPERSEDED | Deleted | Design docs absorbed; superseded by rito-quality-safety |
| intake/branch-closeout-inventory | 2026-06-26 | R.T. Gongora | R.T. Gongora | 0/76 | 0 | ALREADY_ABSORBED | Deleted | Merged into PT baseline (the 79) |
| integration/provider-clinical-place | 2026-06-26 | R.T. Gongora | R.T. Gongora | 0/15 | 0 | ALREADY_ABSORBED | Deleted | Merged into PT baseline |
| intake/access-value-compensation | 2026-06-26 | R.T. Gongora | R.T. Gongora | 0/70 | 0 | ALREADY_ABSORBED | Deleted | Tip SHA is inside PT |
| intake/facility-place-org | 2026-06-26 | R.T. Gongora | R.T. Gongora | 0/66 | 0 | ALREADY_ABSORBED | Deleted | Tip SHA is inside PT |
| intake/provider-experience | 2026-06-26 | R.T. Gongora | R.T. Gongora | 0/64 | 0 | ALREADY_ABSORBED | Deleted | Tip SHA is inside PT |
| intake/clinical-encounter-spine | 2026-06-26 | R.T. Gongora | R.T. Gongora | 0/68 | 0 | ALREADY_ABSORBED | Deleted | Tip SHA is inside PT |
| intake/provider-clinical-place-design | 2026-06-26 | R.T. Gongora | R.T. Gongora | 0/70 | 0 | ALREADY_ABSORBED | Deleted | Tip SHA is inside PT |
| intake/b3-dags-permit-key | 2026-06-24 | R.T. Gongora | R.T. Gongora | 0/77 | 0 | ALREADY_ABSORBED | Deleted | Merged into PT baseline |
| claude/product-truth-recovery | 2026-06-23 | R.T. Gongora | R.T. Gongora | 0/104 | 0 | ALREADY_ABSORBED | Deleted | Fully contained in PT |
| intake/product-truth-scanner-honesty | 2026-06-23 | R.T. Gongora | R.T. Gongora | 6/99 | 0 | ALREADY_ABSORBED | Deleted | Scanner machinery already in PT; 0 new content by patch-id |
| intake/vito-demographics-update-parity | 2026-06-23 | R.T. Gongora | R.T. Gongora | 2/93 | 0* | ALREADY_ABSORBED | Deleted | fix+test absorbed; only generated PT-ratchet differs |
| intake/a2-golden-thread-partials | 2026-06-24 | R.T. Gongora | R.T. Gongora | 2/82 | 0 | ALREADY_ABSORBED | Deleted | feat+doc absorbed by content |
| intake/clinical-knowledge-placeholder | 2026-06-23 | R.T. Gongora | R.T. Gongora | 3/93 | 1* | ALREADY_ABSORBED | Deleted | fix+test absorbed; `+` = generated PT-ratchet only |
| intake/community-moderation-authz | 2026-06-23 | R.T. Gongora | R.T. Gongora | 3/93 | 1* | ALREADY_ABSORBED | Deleted | authz fix+test absorbed; `+` = generated PT-ratchet only |
| intake/patient-safety-pv | 2026-06-26 | R.T. Gongora | R.T. Gongora | 8/79 | 8 | HUMAN_REVIEW | Left open | New svc 8202; UI routes/branding not registered (orphaned nav) + shared surveillance consumer |
| intake/oros-diagnostics-journey | 2026-06-26 | R.T. Gongora | R.T. Gongora | 177/79 | 176 | HUMAN_REVIEW | Left open | 176 new; tshepo trust-crypto + identity-assurance + costa/coverage billing; not build-verifiable here; holds preserved WIP |
| intake/citizen-zero-to-one | 2026-06-26 | R.T. Gongora | R.T. Gongora | 135/79 | 134 | HUMAN_REVIEW | Left open | OPA rego rewrite + PolicyEngine + LOA/consent; tip admits "uncompilable rego + SHADOW" |
| intake/khuluma-comms-hub | 2026-06-25 | R.T. Gongora | R.T. Gongora | 122/79 | 121 | HUMAN_REVIEW | Left open | 22+ SecurityConfig edits, khuluma.rego, tshepo-authz seeding; cross-cutting auth/policy |
| intake/wave-b-tshepo-gdhcn-trust-primitives | 2026-06-25 | R.T. Gongora | R.T. Gongora | 110/79 | 109 | HUMAN_REVIEW | Left open | Trust-crypto/step-up/GDHCN + 17 migrations; mixed trust + oros-cds strands |
| integration/closeout-staging | 2026-06-26 | R.T. Gongora | R.T. Gongora | 246/4 | 238 | HUMAN_REVIEW | Left open | Prior mega-integration; its own phase-A report flags OROS semantic divergence (non-compilable as-is) |

`*` Patch-id `+` count of 0–1 for the small-fix branches is a **generated `product-truth-baseline.json` ratchet
commit only**; the substantive fix+test commits are all `-` (already in PT). The canonical regen supersedes the ratchet.

## Merged branches

| Branch | Merge commit | Reason |
|---|---|---|
| intake/rito-quality-safety | `b61683873` | Self-contained additive Rito Quality/Safety/Client-Voice service (port 8391) + BFF personas + web/mobile surfaces + fresh V001 migration. 13 new commits, 0 absorbed. Module compiles offline; experience-bff compiles. Only conflicts were generated artifacts + a stale-vs-current `vashandi` registry entry (resolved to PT's canonical enterprise-plane definition). |
| intake/fundo-lms | `5e99cf340` | Additive Fundo LMS (learning-service) expansion: catalogue/courses/enrolment/progress/assessments/certificates/cohorts/sessions + 56 UI pages + 12 new learning-service migrations + additive BFF passthrough. 27 new commits, 0 absorbed. Clean auto-merge (no conflicts). learning-service + experience-bff compile offline. Documented partials accepted (notification dispatch STUB; Tshepo/Varapi signals queued). |

## Cherry-picked branches

| Branch | Commits picked | Reason |
|---|---|---|
| _(none)_ | — | The four small fix branches initially scoped for cherry-pick (`vito-demographics-update-parity`, `a2-golden-thread-partials`, `clinical-knowledge-placeholder`, `community-moderation-authz`) were found **already absorbed by content** (`git cherry` marks their fix/test commits `-`). The only `+` commits are generated `product-truth-baseline.json` ratchets, superseded by the canonical regen. Nothing required cherry-picking. |

## Already absorbed

| Branch | Evidence |
|---|---|
| intake/branch-closeout-inventory | `git cherry` 0 `+`; tip SHA `2e5136aa1` is in PT (it is the first of the prior 79 commits) |
| integration/provider-clinical-place | 0 ahead; merged into PT via `92096b000` |
| intake/access-value-compensation | 0 ahead; tip `bd5d4da8b` present in PT |
| intake/facility-place-org | 0 ahead; tip `0bce58db1` present in PT |
| intake/provider-experience | 0 ahead; tip `cd66fd52b` present in PT |
| intake/clinical-encounter-spine | 0 ahead; tip `e27a91abd` present in PT |
| intake/provider-clinical-place-design | 0 ahead; tip `87502a234` present in PT |
| intake/b3-dags-permit-key | 0 ahead; merged into PT via `bfc75755b` |
| claude/product-truth-recovery | 0 ahead; fully contained in PT |
| intake/product-truth-scanner-honesty | `git cherry` = 6 `-`, 0 `+`; the honest-scanner machinery (`__tests__/product-truth-truth.test.mjs`, baseline-ratchet) is already in PT |
| intake/vito-demographics-update-parity | fix `b14498e25` + test `abd3720c3` both `-` (already in PT) |
| intake/a2-golden-thread-partials | feat `a3c6cd7b1` + doc `def5ae043` both `-` |
| intake/clinical-knowledge-placeholder | fix `a7b5a21f5` + test `ca37bbc70` both `-`; only ratchet `7428e31fd` is `+` (generated) |
| intake/community-moderation-authz | fix `93ae89a31` + test `dae8da5a9` both `-`; only ratchet `f785c235d` is `+` (generated) |

## Superseded branches

| Branch | Superseded by | Reason |
|---|---|---|
| intake/rito-design | intake/rito-quality-safety (merged `b61683873`) | Design-only docs were absorbed into the PT baseline (0 ahead); the built service on `rito-quality-safety` is the successor and is now in Product Truth. |

## Abandoned branches

| Branch | Reason |
|---|---|
| _(none)_ | No candidate was broken/empty/experimental enough to abandon outright. |

## Deleted branches

Deleted only after Product Truth was pushed, and only where re-verified as 0-ahead / fully merged / superseded.

| Branch | Reason safe to delete |
|---|---|
| intake/rito-quality-safety | Merged into PT (`b61683873`); 0 ahead after push |
| intake/fundo-lms | Merged into PT (`5e99cf340`); 0 ahead after push |
| intake/rito-design | Superseded + design docs absorbed (0 ahead) |
| intake/branch-closeout-inventory | Already absorbed (0 ahead) |
| integration/provider-clinical-place | Already absorbed (0 ahead) |
| intake/access-value-compensation | Already absorbed (0 ahead) |
| intake/facility-place-org | Already absorbed (0 ahead) |
| intake/provider-experience | Already absorbed (0 ahead) |
| intake/clinical-encounter-spine | Already absorbed (0 ahead) |
| intake/provider-clinical-place-design | Already absorbed (0 ahead) |
| intake/b3-dags-permit-key | Already absorbed (0 ahead) |
| claude/product-truth-recovery | Already absorbed (0 ahead) |
| intake/product-truth-scanner-honesty | Already absorbed (0 new content by patch-id) |
| intake/vito-demographics-update-parity | Code absorbed; residual delta was a generated baseline-ratchet only |
| intake/a2-golden-thread-partials | Code absorbed by content |
| intake/clinical-knowledge-placeholder | Code absorbed; residual `+` was generated baseline-ratchet only |
| intake/community-moderation-authz | Code absorbed; residual `+` was generated baseline-ratchet only |

> Note: several deleted remote branches are still checked out in **local worktrees** under
> `/opt/impilo/repos/impilo-*`. Deleting the remote ref does not affect those working copies; they can
> be pruned separately (`git worktree remove`) when convenient. This was intentionally left out of scope.

## Branches left open

| Branch | Reason |
|---|---|
| intake/patient-safety-pv | HUMAN_REVIEW: additive new pharmacovigilance service (8202) is real, but UI routes/serviceBranding are not registered (pages orphaned from nav) and it adds a consumer to the shared surveillance-service. Finish nav wiring + build-verify before merge. |
| intake/oros-diagnostics-journey | HUMAN_REVIEW: 176 new commits spanning oros-diagnostics **and** a finance/billing strand plus tshepo trust-crypto + identity-assurance; cannot be full-build/CI-verified in this environment. Also currently holds the preserved uncommitted SecurityConfig WIP lineage. |
| intake/citizen-zero-to-one | HUMAN_REVIEW: rewires authorization (OPA rego), PolicyEngine, LOA/assurance and re-homes consent to Mvumo; branch tip self-admits "uncompilable rego + SHADOW seam". Security/policy sign-off required. |
| intake/khuluma-comms-hub | HUMAN_REVIEW: real comms hub, but lands cross-cutting auth changes (22+ SecurityConfig edits, `khuluma.rego`, tshepo-authz seeding) and claims port 8390; needs policy review + conflict-managed integration. |
| intake/wave-b-tshepo-gdhcn-trust-primitives | HUMAN_REVIEW: trust-crypto/step-up/GDHCN primitives + 17 migrations across sensitive services; mixed with an oros-cds strand. The CDS strand is independently cherry-pickable but the trust strand needs architecture review. |
| integration/closeout-staging | HUMAN_REVIEW: prior mega-integration (238 new); its own embedded Phase-A report documents OROS semantic divergence that is **not merge-resolvable** (compile fails without code-level reconciliation). |

## Gates run

Baseline measured on clean PT `19121074f` before any merge; re-run after the two merges.

| Gate | Baseline result | Post-merge result | Notes |
|---|---|---|---|
| `scripts/guard/check-product-truth.sh` | PASS — gaps=4 ≤ baseline 6, blockers=0 ≤ 1 | **PASS** — gaps=4 ≤ 6, blockers=0 ≤ 1 | No regression. Regen output identical (see below). |
| `scripts/guard/check-phase6-service-completion.sh` | PASS — incomplete=2 ≤ baseline 2 | **PASS** — incomplete=2 ≤ 2 | No regression. |
| `node --test scripts/completeness/__tests__/` | 12 pass / 1 **fail** (#13) | 12 pass / 1 **fail** (#13) | **Pre-existing** failure, value unchanged: "gap total 8 exceeds baseline 6". The gate script counts 4; this test's stricter regeneration path counts 8 — a known heuristic-scanner inconsistency present on clean PT, NOT introduced by this closeout. |
| Targeted Maven compile (offline) | n/a | **PASS** | `services/rito-quality-safety-service` ✓, `services/learning-service` ✓, `services/experience-bff` ✓ (the latter exercises both rito + fundo BFF additions). |

Full multi-service Maven build / integration-test suites were **not** run (no live infra; cross-service
build is out of scope for this environment). This is recorded as a residual risk below — the three
directly-affected modules were compile-verified, but end-to-end/IT verification is deferred to CI.

## Product Truth final state

- Final HEAD: integration tip `5e99cf340`; the closeout-report commit becomes the published tip (see final validation in the session).
- Services (registry): 93 (rito-quality-safety-service added; net +1). Product-truth heuristic dataset: 92 — the
  generator filters the experience-plane rito entry, so the canonical `product-truth.json` count is unchanged
  and the regen produced no diff. This is a **known scanner-heuristic limitation**, not a missing implementation:
  rito's code is merged, compiles, and is registered in `services/pom.xml` + `services-registry.yaml`.
- Gaps: 4 (≤ baseline 6); blockers: 0 (≤ baseline 1). No-stub/no-regression: the merges added **zero** gaps
  (failing test #13 value unchanged at 8).
- Tests passed: product-truth gate, phase6 gate, 12/13 completeness unit tests, and offline compiles of the 3
  affected Maven modules.
- Tests skipped / not run (with reasons): completeness test #13 fails pre-existing (heuristic dual-count, not
  caused here); full Maven reactor build + integration tests not run (no infra in this environment).

## Risks / follow-up

1. **Unpushed prior closeout (79 commits)** — adopted as baseline and published by this push. If any of those
   prior merges were intentionally held back, this publishes them. Safety snapshot exists at
   `safety/product-truth-before-claude-5day-closeout-20260626-2021` (`19121074f`) for rollback.
2. **Preserved uncommitted WIP** — the working tree on `intake/oros-diagnostics-journey` had unique, uncommitted
   work (a `dispatch-service` `SecurityConfig` test/prod `@ConditionalOnProperty` filter-chain refactor found
   **nowhere else in history**). It is durably preserved on `wip/oros-intake-uncommitted-preserve-20260626-2016`
   (`bd9a0daab`). **Do not delete that branch** until the SecurityConfig change is reviewed and landed. (It also
   captured an embedded `.claude/worktrees/…` gitlink — cosmetic, WIP-branch only.)
3. **6 HUMAN_REVIEW branches left open** carry genuinely-unabsorbed work (≈ 786 new commits combined) that is
   auth/policy/trust/migration-heavy and not build-verifiable here. They are the real remaining sprawl and need
   architect/security sign-off + CI before landing. `integration/closeout-staging` and `citizen-zero-to-one`
   self-admit non-compilable/SHADOW states.
4. **Heuristic scanner inconsistency** — the product-truth gate (counts 4) and completeness test #13 (counts 8)
   disagree on the clean baseline. Pre-existing; recommend reconciling the two counting paths so the gate and the
   unit test agree before ratcheting baselines further.
5. **Full build not run** — only the 3 directly-affected modules were compile-verified offline. CI should run the
   full reactor + IT suites on the pushed Product Truth.
6. **Local worktrees** for deleted remote branches remain on disk under `/opt/impilo/repos/impilo-*`; prune when
   convenient.

### Going-forward rule (adopted)

No Claude task branch is considered complete unless its work has either **landed in Product Truth** or the branch
is **explicitly documented as superseded or abandoned**. Product Truth (`claude/staging-ux-orchestration-remediation-Yypyl`)
remains the single source of implementation truth.

---

## Continuation — Phase 0 wins landed (2026-06-26, same day)

Following the closeout, a user-approved plan began driving the 6 HUMAN_REVIEW branches + the WIP to closure
(`/home/robert/.claude/plans/whats-the-plan-mossy-journal.md`). The **low-risk, fully-verifiable Phase 0** is done
and pushed (`cfd4d3bd9 → db48bfe74`, fast-forward, no force). Safety snapshot: `safety/pt-before-phase0-20260626-2120`.

| Item | Action | Verify | Result |
|---|---|---|---|
| **WIP SecurityConfig** (`wip/oros-intake-uncommitted-preserve-…`) | Applied only `dispatch-service/SecurityConfig.java` (`@ConditionalOnProperty` test/prod chains) — `f86d7cbf7`; completes a half-wired change (PT already set `disable-oauth-for-tests=true`). | `mvn -o dispatch-service test` | **GREEN**; wip branch **deleted** (unique work landed). |
| **patient-safety-pv** | Merged (`31029901e`) + registered nav/branding/registry (`db48bfe74`): 5 page-backed `/work/patient-safety/**` routes, `serviceBranding` entry, `services-registry.yaml` entry (clinical plane, port 8202). | `mvn -o patient-safety-service compile` ✓, `experience-bff test-compile` ✓ (ServiceClientConfig union resolved), product-truth/phase6/route-inventory gates ✓ | **GREEN**; branch **deleted** (0-unabsorbed). |
| **wave-b CDS strand** (Phase 0c) | **RECLASSIFIED — not a clean cherry-pick.** Aborted after 2 of 8 commits: the strand restructures `ClinicalEvaluationContext` / `ClinicalContextEnricher` / `ClinicalRulesEngine` in the **same files the already-absorbed a2-golden-thread + clinical-knowledge work modified**. It is a genuine reconciliation of **clinical-safety alert logic** (AKI/hyperkalaemia/critical-lab), not an additive pick. Backed out cleanly to `db48bfe74`. | — | **Deferred** to a dedicated reconciliation with full ckp test verification (see below). |

Gate state at `db48bfe74` unchanged from baseline (no regression): product-truth gaps 4 ≤ 6, phase6 incomplete 2 ≤ 2,
completeness 12/13 (pre-existing test #13 still 8).

## Continuation — Phase 1 trust substrate LANDED (2026-06-27, with human sign-off)

The Phase-1 trust substrate was prepared on a local `prep/phase1-trust-substrate` branch (PT untouched until
sign-off), verified, **explicitly approved by the user**, then merged and pushed: **`6d8da4779 → fc4d014ee`**
(fast-forward, no force). Safety snapshot: `safety/pt-before-phase1-20260627-0245`.

- **Method:** curated **file-level extraction** of the substrate dirs from `intake/wave-b-tshepo-gdhcn-trust-primitives`
  (NOT a wholesale wave-b merge — that would have unregistered rito/patient-safety from `services/pom.xml`). PT had
  **zero** changes to these dirs since the merge-base, so wave-b's versions are clean supersets. The `tshepo-trust-crypto`
  lib module was added to `services/pom.xml` surgically (rito + patient-safety preserved).
- **Contents:** `libs/tshepo-trust-crypto` (B4 JWS + canonical trust error model); `tshepo-authz-service` (real step-up
  verification, TOTP/SMS-OTP/supervisor-approval seams, Trust Authority registry B5, GDHCN readiness B6, Flyway
  V010/V014/V015/V016); `tshepo-keys-service` (purpose-scoped signing); `tshepo-offline-service` (capability-token JWS +
  offline JWKS cache w/ iss/aud/scope checks); runtime-proof scripts + GDHCN architecture doc.
- **Verification:** `tshepo-trust-crypto` install ✓; `tshepo-keys`/`tshepo-offline` tests ✓; **`tshepo-authz` 98/98 tests, 0 failures** ✓;
  gates unchanged (product-truth 4 ≤ 6, phase6 2 ≤ 2, completeness 12/13 pre-existing #13 still 8 — no regression).
- **Security review** (`/security-review`, scoped to the substrate diff after correcting the skill's worktree mis-targeting):
  **no high-confidence vulnerabilities.** Confirmed constant-time compares, AES-256-GCM with fresh random IVs, EdDSA-only
  allowlist (no `alg:none`/alg-confusion), signature-verified-before-claims, parameterized JPQL, server-side actor/role
  derivation, dual-control supervisor approval with self-approval prevention, fail-closed providers, persisted lockout/replay
  rejection. One non-blocking config note: `AuthzProperties.ESignet.clientSecret="changeme"` placeholder (eSignet disabled
  by default, config-overridden; pre-existing) — tighten as config hygiene.

`intake/wave-b-tshepo-gdhcn-trust-primitives` stays **open** (its trust strand is now substantially in PT, but its Wave C–I
de-fab strand + CDS strand are not; close it only after those are reconciled or explicitly dropped).

## Continuation — Phase 2 OROS reconciliation PREPARED (2026-06-27, awaiting sign-off)

The OROS diagnostics features were reconciled on a local `prep/phase2-oros` branch (off PT `3538860ee`).
**Not yet merged to PT, not pushed** — held for sign-off (same discipline as Phase 1). Safety snapshot for the
eventual merge: `safety/pt-before-phase1-…` plus a fresh one will be cut at merge time.

Merged `origin/intake/oros-diagnostics-journey` (176 new commits: oros-service diagnostics/imaging journey,
identity-assurance, costa/coverage/tuso billing-category, dags permit verification). **19 conflicts** resolved:

- **Named #1 coverage** `SubsidyController` — **unioned both** features (PT annual-cap `/enrolments` + OROS
  exemption-category `/enrollments`); neither lost; both deps wired.
- **Named #2 dags** `EnforcementService` — took OROS **superset** (v1 issuance + new `verifyAndConsume`:
  constant-time signature → expiry → bound-claim → nonce-replay; closes G056). Confirmed it retains the
  G003 fail-closed signing-key check.
- **Named #3 costa** `ChargeRecordService` — took OROS **full tariff+rules pricing** (single `clinical.teleconsult.value`
  path); removed the now-dead PT `onTeleconsultLifecycle` listener (the 2-arg compile trap) + its helper.
- **Named #4 pct** `TelemedicineOrchestrationService` — took OROS's `emitTeleconsultValueTrigger` helper;
  fixed the `OutboxPublisher` duplicate `TELECONSULT_COMPLETED` case label.
- `experience-bff ServiceClientConfig` — unioned rito + patientSafety + **identityAssurance** params (wiring verified:
  `IdentityAssuranceServiceClient`/`Controller` consume it).
- `tshepo-authz` (AuthzProperties, StepUpVerificationDispatcher) — kept the **Phase-1 substrate** (newer TOTP-enrolment
  version) over OROS's older inlined one.
- Deleted obsolete PT `ChargeRecordTeleconsultTest` (tested the removed 2-arg API; OROS `ChargeRecordServiceTest`
  covers the new pricing richer); adopted the G048-aligned `DisplaySettingsControllerTest` (OROS shipped the old no-arg one).
- **Flyway collisions** renumbered (same-Vxxx/different-content from the merge): coverage `subsidy_enrollments` V010→V012,
  pct `referral_billing_category_context` V015→V021, vashandi `leave_balance` V002→V004 (order-safe). Pre-existing BFF V41
  collision left (no datasource).

**Verification (offline):**
- **Full reactor `mvn install -DskipTests`: BUILD SUCCESS** (entire monorepo compiles with the merge).
- Tests green: costing-engine **75**, coverage **41**, pct, data-access-governance (dags `verifyAndConsume`), and
  **oros-service 154** (incl. 2 new IDOR regression tests). 0 failures.
- Gates unchanged (no regression): product-truth 4 ≤ 6, phase6 2 ≤ 2, route-inventory pass, completeness 12/13 (#13 still 8).
- Frontend parity docs regenerated for OROS routes.

**Security review** (`/security-review`, scoped to the OROS reconciliation): the hand-merged resolutions were clean; the
dags permit-verify, identity-assurance, and costa pricing were verified sound (constant-time sig-before-claims, server-derived
assurance with dual-control, tariff/rule-priced charges with idempotency — no double-charge/forged-amount). **One HIGH-severity
finding in OROS's own code — IDOR / missing tenant isolation** on by-id order/result access (cross-tenant PHI read + result
forgery via partially-predictable ULID orderIds). **FIXED** (`OrderStateMachine.getOrder` now enforces trust-context tenant;
`getResults`/`cancelOrder` route through it; `findByTenantIdAndOrderId` added; regression tests added) — per the "fix all issues
necessary for a successful merge" directive.

**Status: complete and verified on the prep branch; awaiting human sign-off to merge into PT + push** (billing/auth/policy/PHI-
sensitive). On approval: cut a `safety/pt-before-phase2-…` snapshot, `--no-ff` merge, regenerate truth, re-run gates, FF push.

### Remaining work (Phases 3–5) — gated on human sign-off + CI
These were **intentionally not executed** in this automated pass because the approved plan gates them on
`/security-review` + a real cross-service test run + human architect/security sign-off, and they are large,
security-sensitive, and hard to reverse:
- **Phase 3** — `citizen-zero-to-one` (OPA rego must compile first — tip admits "uncompilable + SHADOW").
- **Phase 4** — `khuluma-comms-hub` (V017 + khuluma.rego + 22 SecurityConfig edits).
- **Phase 5** — retire `integration/closeout-staging` once OROS/CDS/khuluma land.
- **CDS strand reconciliation** (was Phase 0c) — fold into the clinical-rules reconciliation; verify with
  `clinical-knowledge-platform-service` unit tests (InterpretationEngineTest/RangeResolverTest/ClinicalRulesEngineTest).

The `intake/oros-diagnostics-journey`, `intake/citizen-zero-to-one`, `intake/khuluma-comms-hub`,
`intake/wave-b-tshepo-gdhcn-trust-primitives`, and `integration/closeout-staging` branches remain **open** pending those phases.
