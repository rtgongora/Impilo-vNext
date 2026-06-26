# Phase A — Branch-Closeout Verification + Stacked Dry-Run Report

**Convergence gate session.** Verify-first, non-destructive. **No canonical mutation, no push to canonical, no branch deletion** was performed. This report hands back for an explicit go/no-go before Phase B.

- **Canonical (Product Truth):** `claude/staging-ux-orchestration-remediation-Yypyl` @ `6d522d291` — **untouched** (re-verified at session end).
- **Safety branch:** `safety/product-truth-before-recent-branch-closeout-20260625-1734` @ `6d522d291` — **present on origin, intact.**
- **Work vehicle:** isolated worktree `/opt/impilo/repos/impilo-closeout`, scratch branches `integration/closeout-staging` (full stack) and `dryrun/tier1-only` (Tier-1 only) — **both local-only, never pushed.** The ~13 live session worktrees were not touched.
- **Toolchain:** Maven 3.8.7 / Java 21 (Temurin) / Node 20.20.2, 9.7 GB offline `~/.m2`.

> **Reading note.** The dry-run conflict resolutions on `integration/closeout-staging` are *deliberately pragmatic* (mostly `--theirs`) to keep the stack moving and surface downstream conflicts. **They are NOT the proposed final merge.** The real resolution recipes are in §4.

---

## 1. Branch set (ahead/behind vs canonical — re-verified, behind=0 everywhere)

| Branch | HEAD | Ahead | Tier | Disposition |
|---|---|---|---|---|
| `integration/provider-clinical-place` (superset) | `39258312d` | 64 | 1 | **GO** (with gap note) |
| `intake/b3-dags-permit-key` | `c40153b67` | 2 | 1 | **GO** (but see DAGS note) |
| `intake/rito-design` (docs) | `8b478e101` | 6 | 1 | **GO** |
| `intake/branch-closeout-inventory` (report) | `2e5136aa1` | 3 | 1 | **GO** |
| `intake/oros-diagnostics-journey` | `b78a35496` | 177 | 2 | **CONDITIONAL** — needs deliberate code reconciliation |
| `intake/wave-b-tshepo-gdhcn-trust-primitives` (CDS) | `d99378846` | 110 | 2 | **GO** (additive) |
| `intake/khuluma-comms-hub` | `8ed3fddab` | 122 | 2 | **GO** |
| `intake/fundo-lms` | `0f761a4ab` | 27 | 2 | **GO** (independent) |

**Spine subsumption confirmed:** all 5 spine branches (`clinical-encounter-spine`, `facility-place-org`, `provider-experience`, `access-value-compensation`, `provider-clinical-place-design`) are **ancestors of the superset** (`git merge-base --is-ancestor` true for each). Merge the superset only; the 5 go 0-ahead afterward.

---

## 2. Stacked integration dry-run (scratch branch off canonical, in merge order)

| # | Merge | Result |
|---|---|---|
| 1 | superset | **CLEAN** |
| 2 | b3-dags | **CLEAN** |
| 3 | rito-design | **CLEAN** |
| 4 | branch-closeout-inventory | **CLEAN** |
| 5 | **OROS** | **14 conflicts** — see §3 (the real hotspot) |
| 6 | **CDS** (onto OROS) | **2 conflicts** — additive `AuthzProperties` + 1 doc (trivial union) |
| 7 | **Khuluma** | **CLEAN** (0 textual, 0 migration collisions) |
| 8 | **Fundo** | **CLEAN** |

### The predicted tshepo-authz Flyway/registration hotspot did **NOT** materialise
- **Migrations are clean sequential `V001`–`V017`, zero version collisions.** OROS owns the trust-primitive substrate (`V010` step-up, `V014` trust-authority, `V015` gdhcn); CDS adds `V016` TOTP; Khuluma net-adds only `V017` policy-rules. No two branches assign the same `Vxxx` to different content.
- **Why:** pairwise merge-bases prove a **shared fork substrate** — OROS↔CDS and OROS↔Khuluma share `3d40ec78b`; CDS↔Khuluma additionally share `45a4e8477`. Khuluma forked from OROS's substrate and CDS from a Khuluma point, so **merging OROS first contributes the shared migrations once** and CDS/Khuluma only stack their deltas. **The plan's OROS-first order is validated and is what neutralises the hotspot.**
- **`services-registry.yaml`:** 105 ids, **0 duplicates** (append-only merged cleanly). **`services/pom.xml`:** 109 modules, **0 missing dirs, 0 dups**. **`registryDrift`** in regenerated product-truth: `unregisteredModules:[]`, `missingOnDiskModules:[]` — clean.

---

## 3. OROS conflict — semantic divergence, **NOT** a mechanical merge (highest-risk finding)

Merge-base of OROS, the superset, and b3-dags is **canonical itself** (`6d522d291`). The superset/L4 and OROS each **forked from canonical and independently re-built the same domains**. The 14 conflicts:

**Generated / docs (low risk):** `reports/product/product-truth.json` (1, generated → regenerate), `docs/audits/*` (7) + `docs/product/service-completion-blueprints.md` (1, hand-authored audit reports → reconcile by union/intent).

**Code (HIGH risk — divergent reimplementations of security/finance logic):**
| File | Superset (HEAD) | OROS | Nature |
|---|---|---|---|
| `coverage/api/SubsidyController.java` | `SubsidyEnrolment*` / `enrolment` / `SubsidyEnrolmentService` | `SubsidyEnrollment*` / `enrollment` / `SubsidyEnrollmentEntity` + repo | **Two mutually-exclusive member-subsidy enrolment implementations** (different spelling, classes, wiring) |
| `dags/core/EnforcementService.java` (+Test) | b3-dags v1 (G003 fail-closed signing key) | v1 **+ v2** `verifyAndConsume` + replay repo + nonce (G003 **+ G056**) | **OROS is a superset of b3-dags** but textually divergent |
| `costa/.../ChargeRecordService.java` | `ingestTeleconsultCompleted(JsonNode, **UUID**)` + C1 double-charge guard | `ingestTeleconsultCompleted(JsonNode)` + tariff-pipeline | **Incompatible signatures**, both implement teleconsult billing |
| `pct/core/TelemedicineOrchestrationService.java` | inline value-event emit + `respondReferralStructured`/`routeReferral` | refactored to `emitTeleconsultValueTrigger()` helper | **Divergent API surface** |

**Proof it is not `--ours/--theirs`-resolvable:** the full-reactor compile of the dry-run stack (after taking OROS's side) **FAILS**:
- `costing-engine-service`: `CostaEventConsumer.java:697` calls `ingestTeleconsultCompleted(JsonNode, UUID)` — OROS's `ChargeRecordService` only has the 1-arg form → *cannot be applied*.
- `pct-service`: `TelemedicineController.java:99,108` call `respondReferralStructured` / `routeReferral` — **absent** from OROS's service.
- `pct-service`: `OutboxPublisher.java:200` **duplicate `case` label** — both branches appended a `case` to the same `switch`; **auto-merged with no git conflict** (silent), produced a compile error.

**Implication:** OROS overlaps the superset's L4/b3-dags work and must be reconciled at the **code** level (unify the two subsidy-enrolment models, the two teleconsult-billing paths, fold b3-dags v1 into OROS's v1+v2 DAGS, dedup the OutboxPublisher case). This is engineering work, not conflict-marker editing.

---

## 4. Resolution recipe (for Phase C — deliberate, never blind)

1. **`product-truth.json` (generated):** never hand-merge. After the stack lands, run `cd scripts/completeness && npm install && npm run product-truth`; commit the regenerated file. (Verified working in this session.)
2. **`docs/audits/*`, `service-completion-blueprints.md` (hand-authored):** union by intent — keep both registers' findings; do not drop either side's gap rows.
3. **Migrations:** already collision-free — **confirm** (not renumber) `V001`–`V017` after each tshepo merge; never let two branches give one service the same `Vxxx`.
4. **`AuthzProperties.java` (CDS):** purely additive `Totp` nested class — **union** (HEAD side empty in all 3 hunks).
5. **`SubsidyController` / subsidy-enrolment:** **domain decision required** — pick one canonical model (`enrolment` vs `enrollment`) or merge them; update *all* references so neither class set is orphaned. Highest-risk reconciliation.
6. **DAGS `EnforcementService`:** take OROS's v1+v2 (it strictly supersedes b3-dags's v1) **after confirming** b3-dags added nothing OROS lacks; b3-dags then becomes redundant for this file.
7. **COSTA `ChargeRecordService` + `CostaEventConsumer` + PCT `TelemedicineOrchestrationService`/`TelemedicineController`/`OutboxPublisher`:** unify the teleconsult-billing path so the consumer call-site and service signature agree, **preserving the superset's C1 double-charge guard**; dedup the `OutboxPublisher` switch case. Must compile + pass costa/pct suites before proceeding.
8. **`EXPECTED_ROUTE_COUNT`:** see §5 — set to the verified union (606) once net-new routes are confirmed page-backed. Never copy one branch's stale 589.

---

## 5. Silent-breakage audit (auto-merge clean, semantically broken)

**5a. Route parity FAILS on the stack (606 vs `EXPECTED_ROUTE_COUNT=589`).**
- Authoritative count = `routes.ts` + `administration-governance/route-registry.ts`, `path: "…"`.
- **Every branch (and canonical) still ships the constant `589`**, yet per-branch actual counts already diverge: **OROS=604, Khuluma=592, CDS=590** each **exceed 589 on their own HEAD** → those three already fail `route-parity-check.mjs` in isolation (a self-reported-green discrepancy). canonical=589 ✓, superset=589 ✓, Fundo=589 ✓.
- Stacked union = **606, with 0 duplicate paths** → legitimately additive. Recipe: set `EXPECTED_ROUTE_COUNT=606` after stacking and confirm each net-new route has a `page.tsx` (the parity script's second assertion). Tier-1-only parity **passes** (589, all routes page-backed).

**5b. product-truth `gapCounts.total = 8` > honesty baseline `6` → completeness test #13 FAILS.**
- The two product-truth gates **disagree** on the same tree: `scripts/guard/check-product-truth.sh` **PASSES** (filtered regex violations = 4 ≤ baseline 6) but `scripts/completeness/__tests__/product-truth-truth.test.mjs` test #13 **FAILS** (`gapCounts.total 8 > gapBaseline 6 — fix the gap, do not raise the baseline`).
- **Origin of the regression:** committed `gapCounts.total` — canonical **6**, **superset 8**, OROS 4, Fundo 6. **The superset alone raises the total to 8** (so the gate fails after Phase B, Tier-1-only, too). Full integrated stack also = **8** (`F:4 S:2 C:1 N:1`).
- The 8 integrated gaps: 4 are baseline known-debt (experience-bff F, mushe-wallet F, `/wellness/commodities` F, `/operations/facility-operations` F); **4 are net-new from the merge set** — `pct-service` (**high S**ecurity), `vashandi-workforce-service` (**high S**ecurity), `khuluma-service` (medium C), `khuluma-service` (medium N).
- **Decision required (do not auto-absorb):** either fix the 2 high-S gaps + 2 khuluma gaps, or make an explicit, documented baseline decision. The baseline ledger doctrine forbids ratcheting **up** to absorb new debt.

---

## 6. Runtime verification performed

| Check | Scope | Result |
|---|---|---|
| Full reactor `test-compile` (109 modules) | **Tier-1-only** | **EXIT 0 — GREEN** |
| Full reactor `test-compile` | **Full stack** | **FAILS** at costa/pct (the OROS divergence, §3) |
| `tshepo-authz -am test` (surefire unit) | **Full stack** | **98 tests, 0 failures, 0 errors, 0 skipped — GREEN** (StepUp TOTP + SUPERVISOR_APPROVAL + TotpEnrolment + TrustAuthority + Gdhcn all execute; "PDP unavailable/Connection refused" lines are asserted fail-closed negative paths, not infra errors) |
| `check-product-truth.sh` (guard) | Full stack | **PASS** (violations=4 ≤ 6, blockers=0/1) |
| route-parity | Tier-1 | **PASS**; Full stack **FAIL** (§5a) |
| completeness `node --test` (13 tests) | Tier-1/full | **12 pass / 1 fail** (test #13, §5b) |
| product-truth hollow probe | canonical → integrated | **NOT hollow**: services 92→93 (+`khuluma-service`), frontendSurfaces 614→650 (+36), registryDrift clean |

**Honest limitation:** `*IT.java` failsafe suites (DAGS enforcement, Gdhcn runtime-proof, trust-authority runtime-proof, etc.) require **live Postgres/Kafka/Redis**, which are not provisioned in this isolated worktree, so they were **not executed**. Unit (surefire) suites that need no infra were exercised on the convergence-critical service (tshepo-authz, green). A full per-service `mvn test` sweep across all owning services was likewise not run (infra + wall-clock); the **compile + targeted-suite + gate** evidence above is what backs the recommendations.

---

## 7. Go / No-Go recommendation

**Tier 1 — GO (merge first, fully safe at the merge level).**
- superset → b3-dags → rito-design → branch-closeout-inventory: all merge CLEAN, Tier-1 reactor compiles GREEN, route-parity passes.
- **Carry-forward before/with Phase B:** the superset alone makes `gapCounts.total=8>6` → the completeness gate will fail after the Tier-1 batch. Decide the 2 superset gaps (or baseline) explicitly; do not silently raise the baseline. Route constant stays 589 for Tier-1 (OK).

**Tier 2:**
- **CDS — GO.** Additive onto OROS substrate (TOTP `V016` + `AuthzProperties.Totp` union). tshepo-authz suite green.
- **Khuluma — GO.** Clean merge, net-adds `V017` + `khuluma-service`; introduces 2 medium gaps (track).
- **Fundo — GO.** Independent (learning-service); clean merge. (60-test claim not runtime-re-verified here — infra; re-verify in Phase C.)
- **OROS — CONDITIONAL GO / HOLD until reconciled.** 177 commits of real, milestone work, but it **overlaps and diverges from** the superset's L4/b3-dags in COSTA/Coverage/DAGS/PCT. It **cannot** be merged by conflict-marker resolution — naive resolution does not compile. Recommend OROS proceed **only after** a deliberate reconciliation pass (§4 items 5–7) is designed and the costa/pct/coverage/dags suites pass. Until then, **HOLD OROS** (and therefore the CDS/Khuluma stack that should sit on top of it in the validated order).

**Cross-cutting (Phase C):** reconcile `EXPECTED_ROUTE_COUNT` to the verified union (606) and resolve the gap-baseline decision; regenerate (never hand-merge) product-truth after the stack.

**Left open (Phase D, unchanged):** `intake/citizen-zero-to-one` (active, mid-P2), `intake/patient-safety-pv`, `intake/rito-quality-safety` (just-started); `czo-ws-*` blocked-until-pushed; `staging` / `ioptime/dev` not-ours → human review.

---

*Phase A complete. Awaiting explicit go signal before any canonical mutation.*
