# Branch retirement recommendations — 2026-07-29 canonical catch-up merge

**Canonical branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
**Canonical tip after this pass:** `b046d1a1a` (200 commits ahead of the pre-pass tip), with this
document's own commit on top
**Final pipeline verdict on that tip:** `PASS` — 27 of 27 phases, 0 failed, 0 advisory
**Canonical tip before this pass:** `9e4599fe6`
**Work branch used:** `coord/merge-catchup-20260729` in worktree `/opt/impilo/repos/wt-merge-catchup`

Nine fortnight lane branches that could not previously be merged were merged into canonical,
smallest first, so the resolution recipe was established before the two large lanes. **No branch
was deleted.** This document is a recommendation only.

## Containment proof

Every branch was checked with:

```bash
git merge-base --is-ancestor origin/claude/<branch> \
  origin/claude/staging-ux-orchestration-remediation-Yypyl
```

| # | Branch | Tip | Contained | Commits ahead of canonical | Net files carried |
|---|--------|-----|-----------|---------------------------|-------------------|
| 1 | `claude/nervous-fermi-22e321` | `6f14a1b98` | yes | 0 | 4 |
| 2 | `claude/trusting-chaplygin-48ca17` | `4ae10ccfd` | yes | 0 | 26 |
| 3 | `claude/ruvimbo-product-Yypyl` | `a305b1c7f` | yes | 0 | 1 |
| 4 | `claude/youthful-montalcini-536fee` | `a2a2dcd94` | yes | 0 | 23 |
| 5 | `claude/khuluma-hub-Yypyl` | `bf72baa93` | yes | 0 | 0 |
| 6 | `claude/post-deploy-bugfix-Yypyl` | `627e94dec` | yes | 0 | 0 |
| 7 | `claude/affectionate-joliot-aef493` | `dfc9cfffc` | yes | 0 | 2 |
| 8 | `claude/unruffled-cartwright-a85b36` | `0d2c37956` | yes | 0 | 197 |
| 9 | `claude/hungry-mestorf-a5cc9b` | `778e2c4a9` | yes | 0 | 7 |

"Net files carried" is `git diff --name-only <merge>^1 <merge>` — what the merge actually changed in
canonical, not the branch's own diff size. Because all nine are ancestors of canonical, their commit
objects are reachable from canonical: **deleting these refs loses no history.**

## The headline finding: canonical had already absorbed most of these lanes

Four of the nine changed almost nothing on landing. That is the honest result, and it is worth
recording so nobody re-litigates these lanes as "lost work":

- `khuluma-hub-Yypyl` and `post-deploy-bugfix-Yypyl` carried **zero** net files.
- `ruvimbo-product-Yypyl` carried **one** file, and it was a regenerated artefact
  (`registry-maturity.json`), not product code — the entire `/ruvimbo` surface was already in canonical.
- `affectionate-joliot-aef493` carried **two documentation files**; its code fixes were already in.

Only four lanes brought substantive code: `trusting-chaplygin-48ca17`, `youthful-montalcini-536fee`,
`hungry-mestorf-a5cc9b`, and above all `unruffled-cartwright-a85b36`.

## Per-branch detail

### 1. `claude/nervous-fermi-22e321` — +1 commit, 4 files — **RETIRE**

Carried the BREAK-GLASS emergency-access doctrine guard in TSHEPO authz (CZO-LEAD step 4.5).

- **`PolicyEngine.java`** — content conflict; both lanes had added break-glass commentary. Took the
  **union** so the merged comment explains both the emergency path and the audit obligation.
- **Migration version collision** — the branch shipped `V041__break_glass_governance_policy_rules.sql`
  while canonical already had a different `V041`. Flyway would have failed at startup. Renumbered to
  **`V056__break_glass_governance_policy_rules.sql`** and updated the in-file version references.
- **`PolicyEngineTest.java`** — the branch was written against an older `AuthzInternalRequest` arity.
  Added the `DutyContext.absent()` argument at the two call sites so it compiles against canonical's DTO.

### 2. `claude/trusting-chaplygin-48ca17` — +1 commit, 26 files — **RETIRE**

Classified `abis-service` and `matcher-engine` into the full-boot image build pipeline.

- **`config/full-boot-service-classification.yml`** — both lanes appended entries and both bumped
  `total_entries`. Kept canonical's entries, removed a **duplicated `participation-service` block**
  the merge would otherwise have introduced, and re-derived `total_entries` to **155** from the file
  rather than by arithmetic.
- **`ui/one-ui-shell/src/generated/registry-maturity.json`** — generated-file conflict (only the
  `generatedAt` timestamp truly differed). Took canonical's side then **regenerated** with
  `scripts/frontend/sync-registry-maturity.mjs` so the artefact matches its source registry.

### 3. `claude/ruvimbo-product-Yypyl` — +7 commits, 1 file — **RETIRE** (one dropped state, noted)

The `/ruvimbo` health-financing product surface: role workspaces R2–R5, cost estimator, digital
member card, performance aggregation. Canonical already contained all of it.

- **`RuvimboActionGrid.tsx`** — add/add conflict. The branch's version added a "Coming soon" state.
  Initially taken, then the merge commit was **amended back to canonical's version** because
  `run-change-safety-gates.sh` correctly flags "Coming soon" as placeholder copy. Dropping it is the
  policy-correct outcome, not an oversight.
- **`routes.ts`** — `EXPECTED_ROUTE_COUNT` conflict. Took canonical's `829` and verified with
  `node scripts/route-parity-check.mjs`: 829 expected, 829 found, 0 missing.
- Related: `coverage/member/page.tsx` is now a redirect to `/ruvimbo/member`, keeping existing deep
  links and the post-enrolment navigation working.

### 4. `claude/youthful-montalcini-536fee` — +7 commits, 23 files — **RETIRE**

Service-to-service trust: the BFF mints its own `client_credentials` service token, plus rota name
resolution as a BFF composition. This was the predicted "both lanes fixed it independently" shape.

- **`ExperienceBffSovereignWireMockSupport.java`** — two conflicts, both resolved as a **union**:
  imported both `patch` and `matching`, and registered all five downstream base URLs from both sides.
- **`ServiceClientConfigTest.java`** — three conflicting hunks; both lanes wrote
  `serviceRestTemplateCanIssuePatchOverRealHttp()`. Took canonical's version consistently, because it
  additionally exercises the trust interceptor and proves the token provider is *not* consulted.
  Confirmed with `mvn -o -f services/experience-bff/pom.xml -Dtest=ServiceClientConfigTest test`.
- **`SERVICE_TO_SERVICE_TRUST_PATTERN.md`** — took canonical's side, which keeps a provenance block
  the branch lacked.
- **`StaffingReadServiceTest.java`** — took the **union**: the branch's lenient `profileRepository`
  mock plus canonical's `setUp` stubbing.

### 5. `claude/khuluma-hub-Yypyl` — +4 commits, **0 files** — **RETIRE**

The first-class `/khuluma` front door, role-aware hub home, six workspaces, KH2/KH3 seams. Canonical
already contained the entire lane; the merge was a pure ancestry formalisation. Canonical's version is
also the better-factored one — it uses a thin `KhulumaHome` wrapper where the branch still inlined it.

### 6. `claude/post-deploy-bugfix-Yypyl` — +2 commits, **0 files** — **RETIRE** (verify one UX item)

Public welcome page colour and a back-to-landing nav.

- `welcome/page.tsx`, `PublicHeader.tsx`, `WelcomeHero.tsx` all resolved to canonical, which uses a
  thin `PublicLanding` wrapper and a more capable header. The branch's stated reason for its nav
  change — a `/` redirect loop — no longer exists.
- **Open item for the product owner:** confirm the public pages still offer an obvious route back to
  the landing page. The branch's intent was reasonable even though its implementation was superseded.

### 7. `claude/affectionate-joliot-aef493` — +3 commits, 2 files — **RETIRE** (intent superseded)

"Stop advertising 30 clinical tools that do not exist" and "remove the generic adder that faked a
score in 17 specialties". Only the two registry documents landed.

- **`MobileProviderExtendedController.java`** — the branch reworked `getSpecialtyWorkspaces` for
  honest availability; canonical had **removed the endpoint entirely**. Took canonical: removing the
  dishonest surface is strictly stronger than labelling it.
- **`MobileSpecialtyWorkspaceCatalogTest.java`** — the branch's new test auto-merged but called the
  now-deleted endpoint, so it would not compile. **Deleted**, as it tested removed functionality.
- `queueService.ts`, `SpecialtyWorkspacePanel.tsx`, `SpecialtyWorkspaceTools.test.ts` — took
  canonical's registry-driven approach over the branch's narrower fix.

### 8. `claude/unruffled-cartwright-a85b36` — +91 commits, 197 files — **RETIRE** (largest and most valuable)

This is a **test-resurrection lane**, and it is the most consequential merge in the pass. It renames
~95 `*IT` classes to `*Test` so surefire actually **runs** them, and activates further suites against
real PostgreSQL via an `it` profile and `it-containers`. Roughly 95 golden contract suites that were
silently skipped now execute on every build.

Conflict resolutions:

- **`services/tshepo-keys-service/.../SecurityConfig.java`** — the branch added a separate
  `@ConditionalOnProperty` `testFilterChain` bean; canonical uses `@Value` with internal branching.
  Took canonical's signature and dropped the branch's bean — canonical's form handles the signing
  endpoints correctly in preview and test.
- **`services/vito-service/.../SecurityConfig.java`** — the branch removed `permitAll()` for
  `/v1/internal/**`. Took canonical's version; the branch's change would have reintroduced a known
  401 regression.
- **`services/live-service/src/test/resources/application-test.yml`** — took the branch's side to
  remove a duplicated `spring.kafka` block that would otherwise raise a YAML `DuplicateKeyException`.
- **`services/oros-service/id_file`** — a stray build artefact with conflicting numbers. Took
  canonical's value (`3400`). **Recommendation: this file should not be tracked at all** — see findings.
- **`services/vito-service/src/test/.../TestCompanionConfig.java`** — accepted the branch's deletion.
  It was an obsolete in-memory idempotency shim; zero references remain in the tree.

Full deletion/rename accounting vs canonical: **95 renames, 1 deletion**, all explained. Three files
that looked like lost renames were verified as canonical's own newer work that the branch predated —
a migration renumber (`V41` → `V45__shr_artifact_linkage.sql`), a resource move of
`who_under5_lms.json` into `libs/paediatric-domain`, and `TshepoConsentDevInstanceIT` → `...Test`
which canonical guards with `@EnabledIfEnvironmentVariable(MVUMO_IT_TSHEPO_BASE)` so it skips safely.

Spot-checked resurrected suites, all green: `tshepo-authz-service` 16 tests, `varapi-service` 16,
`pharmacy-service` 16, `search-service` 16.

### 9. `claude/hungry-mestorf-a5cc9b` — +9 commits, 7 files — **RETIRE**

Subsidy-enrolment system-of-record consolidation: retire the legacy `cv_subsidy_enrollments` model,
carry `exemption_category` on subsidy enrolments, resolve patient billing category from the
consolidated enrolments. Eleven conflicted files.

- **Duplicate `V013` migration** — the branch shipped `V013__consolidate_subsidy_enrolments.sql`
  while canonical had `V013__subsidy_enrolment_unification.sql`. Deleted the branch's file and kept
  canonical's.
- **Silently deleted DTOs** — the branch deleted `EnrollMemberSubsidyRequest.java` and
  `SubsidyEnrollmentResponse.java`, which git applied without conflict, but canonical's
  `SubsidyController` still uses both. **Restored** them; without this the service would not compile.
- **`SubsidyEnrolmentEntity.java`** — both lanes added `exemptionCategory`, so the merge produced a
  duplicate field and duplicate accessors. Removed the redundant pair, keeping the documented one.
- **Ported additive work rather than discarding it** — the branch added a `POST
  /enrolments/{id}/end` lifecycle endpoint that canonical lacked, and its new
  `SubsidyEnrolmentConsolidationTest` depended on it. Ported `end()` into canonical's
  `SubsidyEnrolmentService` and `SubsidyController`, idempotent and tenant-scoped in canonical's style.
- Result: **89 coverage-service tests pass, 0 failures**, including the branch's end-to-end
  consolidation proof (enrol → consume → exemption → end).

## Findings and recommendations beyond retirement

### F1 — Stale `~/.m2` artefacts fake test failures (process trap, high value)

The coverage-service golden contract suite appeared to fail with `400` on
`HeaderEnforcement.allHeadersPresentSucceeds`. It was **not** a code defect. Per-service Maven runs
resolve `libs/*` from the local repository, so an out-of-date installed `tech-companion-harness`
was being used while branch 8 had changed the harness. After
`mvn -o -f libs/tech-companion-harness/pom.xml install -DskipTests`, the same suite passed 16/16
with no source change.

**Recommendation:** any per-service `mvn test` in this estate must be preceded by installing `libs/*`,
or run from the `services/` aggregator. A one-line note in the testing runbook would prevent a
recurrence; the failure is very convincing and points at entirely the wrong file.

### F2 — `check-dangerous-deletions.sh` blocked on deleted test files (fixed in this pass)

Commit `ee81409bc`. The `case` statement matched `services/*` before its own test-file branch, so any
deleted test under `services/` was reported as `service path deleted` and hard-failed — contradicting
the guard's documented policy that test deletions only warn. The test branch also used a bare `*test*`
glob, which matches production names such as `LatestReading.java`, so merely reordering it would have
**downgraded real deletions**. Now matched on the Maven/JS test roots and ordered first.

Mutation-proven both directions: a test-only deletion warns and exits 0; deleting a production service
file — including one named `Latest*` — still fails and exits 1.

### F5 — Guard self-reach: six guards fixed, five needed nothing

The brief expected eleven whole-tree guards to need `guard_assert_scanned`. Reading each one, **six**
could genuinely pass while scanning nothing, and **five** could not. Adding the assertion to all
eleven would have added noise and implied a defect where there was none.

Fixed (commits `d1490d648`, `014d2e8aa`), each mutation-proven by pointing its scan at an empty set:

| Guard | Vacuity found |
|---|---|
| `check-top-no-record-level-emit.sh` | Both scans: the pct-service publishing files, and the TOP migration pinned as `V435__*.sql` — a renumber would silently empty it |
| `check-confidential-lane-routing.sh` | Counted controllers and never asserted the count; detection is by entity **import**, so renaming a stamped entity empties the checked set while every message still reads OK |
| `check-source-text-integrity.sh` | Counted ~18,000 files and printed that count in its pass line without ever requiring it to be non-zero |
| `check-public-lane.sh` | Scan globs come from YAML; it would print `(0 files scanned)` as a PASS |
| `check-rmnp-capture-coverage.sh` | Exited **0** on "no RMNP packs yet" — honest when the guard was wired ahead of the content, but 17 packs now exist, so zero means the scan broke |
| `check-imnci-capture-coverage.sh` | Reads `rules`/`tables` via `dict.get`, so a key rename yields zero requirements — indistinguishable from full coverage |

Deliberately unchanged, with reasons:

- **`check-migration-version-collisions.sh`** already implements self-reach inline, and more
  thoroughly than the shared helper: it fails on zero migration directories, on any directory it
  cannot read, and on any directory that holds files but parses to zero versioned migrations.
- **`check-madi-surfacing.sh`** and **`check-doctrine-compliance.sh`** are fixed-list existence
  checkers. Every check is a positive assertion ("this file must exist"), which cannot pass
  vacuously — if the tree moves, they fail loudly.
- **`check-mobile-parity.sh`** and **`check-backend-frontend-parity.sh`** are orchestrators. Their
  own loops are delta-scoped, where an empty set is legitimate (nothing changed); the whole-tree
  scanning happens in the children they invoke (`check-*-mocks-and-stubs.sh`,
  `check-*-api-surfacing.sh`, and `npm run test:no-stubs`).

A seventh fix came out of the proving rather than the reading: the three guards that gained a
`source` line resolved the helper **after** `cd "$REPO_PATH"`, so any relative invocation from
outside the repository root died with `guard_assert_scanned: command not found`. The mutation probe
went red for the wrong reason, which is how it was caught. Now verified from the repository root,
from inside `scripts/guard`, and by absolute path.

### F6 — Four dev cryptographic seeds silently apply in production, and the estate never provisioned them (ESCALATED — owner decision)

This is the most serious finding in the pass, and it is why the secrets guard red was **not** treated
as baseline drift. `scripts/guard/check-committed-secrets.sh` reported nine new entries and one
stale. Triaged individually by reading each `@Value` default:

**Silently applies — a real risk, deliberately NOT baselined (5 entries):**

| Service / class | Property | Behaviour when unset |
|---|---|---|
| `vito-service` `crypto/ImpiloIdCipher` | `vito.identity.impilo-id-encryption-key` | Logs a warning, then encrypts Impilo IDs with the source-visible `DEV_KEY` |
| `vito-service` `qr/QrSigningService` | `vito.qr.signing-key-seed` | Logs a warning, then **signs** QR codes with the source-visible `DEV_SEED` |
| `vito-service` `card/CardAssertionVerifier` | `card-print.qr.public-key` | Logs a warning, then derives the **verify** key from the dev seed — so a card signed with the public dev seed is accepted as genuine |
| `card-print-agent` `QrAssertionService` | `card-print.qr.signing-key-seed` | Logs a warning, then signs printed-card assertions with the dev seed |
| `coverage-service` `EligibilityTokenService` | `ruvimbo.token.secret` | **Worst of the five.** The placeholder is the `@Value` default itself, with no length check and no warning at all. Eligibility tokens are HMAC'd with a secret anyone can read in the repository |

A warning in a log is not failing closed. Nobody reads a startup warning on the hundredth restart.

**The confirming evidence.** Searching `deploy/`, `infra/` and `compose/` for these properties finds
**exactly one** provisioned: `WALLET_CARD_ENCRYPTION_MASTER_KEY`, in
`deploy/helm/impilo-vnext/values-full-preview.yaml`. That is the one seed whose provider,
`MasterKeyCardDataKeyProvider`, **throws** `IllegalStateException` when the value is missing, weak, or
literally `change-me`.

So the only seed that got provisioned is the only one that refused to start without it. The four that
merely warn were never provisioned in any environment — which means the full-boot preview stack is
today signing card QRs, verifying card assertions, encrypting Impilo IDs and minting Ruvimbo
eligibility tokens with keys that are readable in this repository. That is the argument for failing
closed, demonstrated rather than asserted.

**Why they are not fixed in this pass.** Making them fail closed is the correct remediation, but it
would stop those four services from starting in preview the moment it landed, because nothing
provisions the values. That is a deploy-affecting change and needs provisioning via `secretKeyRef`
(`docs/security/secrets-management-migration-plan.md`) authorised alongside it. Recommended sequence:

1. Provision the four properties as sealed/external secrets in the preview and production values.
2. Then change each of the four to fail closed, matching `MasterKeyCardDataKeyProvider`.
3. Rotate anything already signed or encrypted with a dev seed — the current values are public.

The guard therefore **stays red on these five**, which is the honest state. It runs in GitHub Actions
(`.github/workflows/ci.yml:875`) and is not part of `run-change-safety-gates.sh` or
`run-local-quality-gates.sh`, so this does not mask either local gate.

**Fixed in this pass — scan-scope bug (1 entry).** `scripts/guard/gitleaks-diff-scan.sh` was reported
as containing a committed secret. It does not: line 7 is a *comment* describing the
`*-change-me-*` convention that the sibling scanner detects. The secrets guard was scanning its own
tooling. Extended the self-exclusion at `check-committed-secrets.sh` to name that scanner, rather
than adding a baseline row that would have looked like accepted debt. Excluded by filename, not by
directory, so a real secret pasted into any other guard script is still caught.

**Baselined — genuine non-secrets (3 entries), with reasons:**

- `mushe-wallet-service` `MasterKeyCardDataKeyProvider.java` — the `change-me` literal here is the
  value the constructor **rejects**. It is a rejection sentinel, not a key.
- `mushe-wallet-service` `CardHealthDataCryptoTest.java` — test fixture.
- `vito-service` `CardAssertionVerifierTest.java` — test fixture.

**Pruned — one stale row:** `mushe-wallet-service` `CardHealthDataService.java`, whose occurrence a
migration phase already removed. Edited by hand rather than by `--update-baseline`, because that flag
rewrites the baseline from the current scan and would have silently blessed all five real findings —
the blanket-baseline outcome this triage exists to avoid.

**Mutation-proven.** With a planted `planted-violation-change-me-now` token, the guard reported six
new entries instead of five and named the planted file, confirming it still discriminates a new
violation while the five known findings stand. Probe removed.

### F3 — `services/oros-service/id_file` should not be tracked

A stray build artefact containing a bare number, which produced a content conflict during merge 8.
It has no consumers. Recommend deleting it and adding an ignore rule; not done here because it is
outside this pass's scope.

### F4 — `.github/workflows/ci.yml:919` gates full backend tests on one branch name (documented, not changed)

Full backend tests only run on `claude/staging-ux-orchestration-remediation-Yypyl`, so **100 of 103
modules stop being tested on any other branch**. This matters much more now that branch 8 has
resurrected ~95 golden contract suites: the value of that lane is only realised on branches where the
tests actually run. Widening it is a runner-cost decision and this estate already has billing/runner
locks, so it is recorded here for the product owner's call rather than changed unilaterally.

### F7 — Three merge defects the guards could not see, found only by running the tests (fixed)

The change-safety guards passed the merged tip while three services could not pass their own test
suites. Guards check structure; only the backend suites caught these. All three are fixed in this pass,
and each is a distinct *kind* of merge hazard worth recognising.

**1. A seed row keyed on a value the runtime does not have** —
`fix(tshepo-authz): seed break-glass FACILITY_ADMIN rules with a real actor type`.
The break-glass rules from `nervous-fermi-22e321` keyed two `FACILITY_ADMIN` review rules on
`actor_type='STAFF'`, which is not a member of the runtime `ActorType` enum. The migration applied
cleanly and looked complete; the rows could simply never match a request, so the reviewer path they
exist to authorise was **dead in the database**. `PolicyRuleSeedVocabularyTest` is what caught it.
Fixed to `NULL`, the shape `V026` already uses for role-scoped rules.

**2. A test config that contradicts a mandatory architectural pattern** —
`test(ubomi): keep Kafka autoconfiguration so the outbox publisher can wire`.
`application-test.yml` excluded `KafkaAutoConfiguration` outright, but `UbomiOutboxPublisher` is an
unconditional `@Component` — and the outbox pattern is mandatory for every service in this estate. The
whole application context failed to load. Any service that gains an outbox publisher will hit this, so
the fix is the estate-standard shape: keep the autoconfiguration, set `listener.auto-startup: false`.
Building a `KafkaTemplate` needs no broker.

**3. Two lanes that made opposite decisions about the same fixture** —
`test(experience-bff): stop faking inpatient availability in the sovereign stub`. This one is the real
lesson. On 2026-07-14, `6b5fc41e4` stubbed `/internal/v1/beds/wards` so the RBAC test got a 200. On
2026-07-16, `bfdcc1d48` **reversed that on canonical**: it renamed the test to
`bedWardsListPassesRbacAndFailsCleanWhenInpatientUnavailable` and asserted the honest 502
`INPATIENT_UNAVAILABLE` that proves the request reached the sovereign client — dropping the stub and the
`inpatient-base-url` registration with it. Branch 8 still carried the 14th's version.

Resolving that file by taking the **union** of both sides' base-url registrations re-imported the
retired stub and turned canonical's honest 502 back into a 200. The union heuristic is correct when both
lanes fixed the same defect; it is **wrong when the lanes disagreed**, and then the later decision wins.
Nothing consumed the 200 — `RbacIntegrationTest` is the only caller of that path. Registrations that do
have consumers (`oros`, `reporting`, `varapi`) were kept.

> Practical rule for the next consolidation: before taking a union in a **test fixture**, check whether
> the two sides are additive or contradictory. A fixture that makes a dependency look available can
> silently delete another lane's honest-failure assertion, and the merge will still compile and the
> guards will still pass.

### F9 — A rejected form extraction returns 500 and loses the clinician's whole submission (ESCALATED — owner decision)

Found while fixing F7's third item. Not fixed here: it changes a clinical write path.

`FormExtractionService.extractAll` wraps each item's extraction in `try/catch (RuntimeException) ->
recordFailure(...)`, and the comment says why — "the form response itself must survive a rejected
extraction — losing the clinician's answers because one derived observation was malformed would be a
far worse outcome." That intent does not survive contact with Spring's transaction semantics.

When the failing call is a `@Transactional` service participating in the same transaction —
`problemService.add` is — its exception marks the shared transaction **rollback-only** before the catch
runs. Extraction continues, the item is recorded as FAILED, and then the outer commit throws
`UnexpectedRollbackException`. The client gets a 500 and the entire form response is rolled back:
exactly the outcome the catch exists to prevent.

Reproduction, and it is not exotic — a duplicate problem is the trigger:

1. Submit a consult form whose `primaryDiagnosis` maps to CONDITION for a patient who already has
   that problem ACTIVE on their list.
2. The duplicate-problem guard rejects it: *"A problem matching 'Malaria' is already on this patient's
   list (status ACTIVE). State whether this is the same problem returning or a distinct one."*
3. `POST /v1/forms/responses/{id}/submit` returns 500 and the submission is lost.

Chronic and recurring conditions make this a normal clinical situation, not an edge case. The guard
itself is good and should stay; the transaction handling around it is what fails.

Fixes worth considering, in the owner's order of preference — each needs a decision this pass should
not make alone: run each item extraction in its own transaction (`REQUIRES_NEW`) so a rejection is
genuinely isolated; or pre-check for the duplicate before calling, so no exception is raised on the
expected path; or surface the rejection to the clinician as a decision ("same problem returning, or
distinct?") rather than as a failure, which is what the guard's own message asks for.

### F8 — Consolidation lanes need a declared target ref, or the guards charge them the whole fortnight

Not a bug, but a sharp edge that will recur. Because `_guard_published_refs` excludes the branch's own
upstream, a lane whose upstream *is* canonical gets base resolved to a pre-fortnight commit and reviews
**1942 commits / 5722 files** of other lanes' work as if it authored them. Four checks go red on
inherited debt and the run is unreadable.

Recommend either setting `git config guard.upstreamRef origin/<canonical>` on consolidation branches, or
documenting `GUARD_UPSTREAM_REF` in the merge runbook. The evidence for treating the four reds as
inherited is in the next section — two-sided, not asserted.

**Made legible in this pass, not silently widened.** `resolve_base_ref` now prints an unconditional
`GUARD NOTE` when the fork-point base sits behind the checkout's tracking branch, stating how many
already-published commits are inside the review window and naming the two overrides. In this worktree it
reads *"base 6dfa1063c predates the tracking branch origin/claude/staging-ux-orchestration-remediation-Yypyl:
1959 already-published commit(s) from other lanes are inside this review window."* Anyone reading a red
run now learns the attribution rule from the output instead of from this document.

The exclusion itself was deliberately **left alone**. Git cannot distinguish a shared branch from a lane
branch pushed under a different name — and this estate does exactly that, local names against
`origin/claude/*` — so preferring the upstream would have started laundering lanes' own work into
"already reviewed" to fix a coordinator-worktree annoyance. The note goes to stderr, so callers that
capture the base on stdout are unaffected, and `tests/base-ref-resolution-test.sh` still passes 15/15.

### F10 — Five instrument defects found by running the full pipeline, each uncovered by fixing the last

The full local pipeline was the only thing that found these; every one of them had been passing or
silently skipping. They are listed in discovery order because each was hidden behind the one before,
which is the useful part of the story: a broken instrument conceals the next broken instrument.

**1. Three guards audited a different repository.** `check-registry-inventory-contract`,
`check-full-boot-waves` and `check-bff-downstream-mappings` defaulted `REPO` to the literal
`/opt/impilo/repos/Impilo-vNext` and `cd`'d there. Run from any worktree they inspected that other
working copy — which on this VM carries dirty files from unrelated lanes — and returned its verdict as
the caller's. `check-full-boot-waves` reported a pass over 116 runtime services from the shared
checkout while the tree under review had 119. `_guard-common.sh` already documents this failure mode in
a comment; these three predate the rule. Fixed and verified from the repo root and from `/`.

> The plan for this pass recorded "REPO_PATH: zero hardcoded occurrences remain; all script-relative."
> That is not the case: 53 occurrences remain across `scripts/`. The three in `scripts/guard/` are
> fixed because a guard that measures the wrong tree gives a false verdict. The rest are in
> build/deploy/operator scripts, where the practical risk is lower and the change is broader than this
> pass should take on. Worth a follow-up sweep.

**2. The full-boot artifact generator could not run at all.** `generate-full-boot-artifacts.mjs`
imports `js-yaml`; `scripts/full-boot/package.json` declares it, but nothing ever installed it, so in
any fresh tree the generator died on the import — and all five call sites wrapped it in `|| true`. The
pipeline reported "Full-boot artifact generation" as advisory-clean while generating nothing. It only
appeared to work on the shared checkout, where `node_modules` was installed by hand in May 2026 and
has sat there since. So the registry-driven artifacts it owns silently went stale for months, which is
precisely the failure `4ae10ccfd` set out to prevent.

**3. `surgery-service` was missing from `full-boot-waves.yml`.** Once the generator could run, it
refused immediately: a registered, runtime-enabled service belonged to no wave. Pre-existing (missing
at `9e4599fe6` too) and invisible only because nothing could report it. Assigned to wave 3
(optional-clinical) with its peers. This is what a first honest run is supposed to look like.

**4. Two generators fought over one output file.** `audit-helm-deployability.py` and
`generate-full-boot-artifacts.mjs` both wrote `FULL_HELM_DEPLOYABILITY_MATRIX.md` with incompatible
tables — a 22-row "required services, all helm_ready" summary versus the 155-row whole-estate audit of
which services have no chart at all. Both run in the same pipeline, so the committed file meant
whichever ran last, and each run destroyed the other's report. Separate files now.

**5. Fixing defect 2 armed a live regression.** `generate-full-boot-artifacts.mjs` invokes
`generate-full-preview-runtime-values.mjs`, which rewrites
`values-full-preview-runtime.generated.yaml` — and that file carried a hand-added `secretEnv` block
giving pct-service `KEYCLOAK_BACKEND_SECRET` plus the clinical-knowledge base URL and backend client
id. pct needs them because the clinical knowledge platform is an OAuth2 resource server and IMAM
admission routing runs from Kafka consumers and scheduled jobs, where there is no user token to
borrow. The file's own header warned that regenerating drops the block and asked the next person to
diff any regeneration — a warning that held only while the generator was too broken to run. Making it
runnable would have deleted a live capability on the next pipeline run of any tree.

The values now live in the generator's `specialEnv`/`specialSecretEnv`, beside the five services
already there, so regeneration reproduces them. Verified by diffing the parsed YAML against the
committed file: 100 services both sides, no service dropped, no `env`/`secretEnv`/`probes`/`resources`
key lost.

> The general lesson, and the reason defect 5 is the most dangerous of the five: a hand edit to a
> generated file is not a fix, it is a fix with an expiry date. It survives exactly until someone
> repairs the generator, and then it disappears silently at the moment everything else starts working.
> If a generated file must carry a value, the generator has to know it.

### F12 — Canonical could not build for production, and three green gates said otherwise (fixed)

With the env vars supplied, `next build` reached type validation and failed immediately:

```
src/app/my-life/page.tsx
Type error: Page "src/app/my-life/page.tsx" does not match the required types of a Next.js Page.
  "resolveMyLifeTarget" is not a valid Page export field.
```

`de5f22c06` — canonical, 2026-07-28, one day before this pass — added the `/my-life` shim and exported
its resolver so the test could call it. App Router accepts only the page fields it recognises. So
**canonical had been unbuildable for a day** while `lint`, `type-check` and the unit tests were all
green on that file: this constraint is enforced only inside `next build`, which was itself failing
earlier for the missing `API_GATEWAY_URL`. Two instruments broken in series, and the second hid a real
defect behind the first — the same pattern as F10.

Fixed by making the resolver private, matching the `/provider-workspace` shim, with the test asserting
through the render path. That is better coverage as well as legal: it proves the redirect actually
fires, which calling the resolver directly never did. `next build` completes; 3 tests pass.

### F13 — The rate limiter was throttling a test suite into failure (fixed for one service, 99 to go)

`DeidPipelineMockMvcTest` failed on `expected:<200> but was:<429>`, with `X-RateLimit-Remaining: 0` on
the response. The assertions were correct; the service was refusing its own test traffic.

The Wave 14 baseline gives every service `new RateLimitGuard(100, 2)` — a 100-token in-memory bucket
refilling at 2/s, keyed by actor with a fallback to remote address. Under MockMvc every request in a
Spring test context resolves to the same key, so the bucket is shared by the whole context, and a
module making more than 100 requests spends it. Refill does not save you: the suite runs far faster
than 2 requests/second.

Two things make this nasty out of proportion to the cause. The failure lands on **whichever test
follows the hundredth request**, so it presents as an unrelated assertion failure in innocent code —
and it moves as tests are added or reordered. And it is *latent*: the service is fine at 99 requests
and broken at 101, so it fires on the commit that adds a test, not on the commit that caused it. Here
the trigger was `e8af82066` (branch `unruffled-cartwright`) reviving the dead
`DataGovernanceGoldenContractTest`, whose requests joined the same bucket. The rename was correct.

Fixed: capacity and refill are now `@Value` properties defaulting to 100/2 — runtime behaviour
unchanged — and the test profile widens the bucket instead of removing the filter, so the filter stays
in the chain under test. The scaffolder emits the same shape for services created from now on.

**Recommended sweep, not done here:** the other 99 services carry the hardcoded literal. Each is one
suite-growth commit away from the same afternoon, and the diff is mechanical (the two `@Value`
parameters plus one import).

### F14 — The backend reactor runs fail-fast, so one failure hides the rest

`backend-reactor-tests` invokes `mvn test` with no `--fail-at-end`, so data-governance's failure
stopped the reactor and every module after it in reactor order went untested — the phase reported one
red module when the true count was unknown. Re-running the pass took a full pipeline cycle per fix.

Fail-fast is the right default for a feature branch, where you want the first error fast. On a
consolidation pass it is the wrong shape: you want the complete list. Recommend `-fae` for the reactor
phase, or at least a documented resume (`mvn … -rf :<module> -fae`, which is what was used here).

### F11 — Inherited debt this pass did not close, with proof it is inherited

Three services are registered and enabled but have no OpenAPI contract and no BFF downstream mapping:
**procedures-service**, **surgery-service** and **mental-health-service** (surgery also reports
`authzAudit: thin`). They are 68 routes across 22 controllers.

They keep two gates red against a baseline of 0 (`check-product-truth`: violations=6; 
`check-phase6-service-completion`: incomplete=3), and they are also what
`check-bff-downstream-mappings` reports now that it reads the right tree.

Proof they are inherited, not from this pass: the same two gates were run at pre-merge canonical
`9e4599fe6` in a throwaway worktree and reported **the same 6 gaps and the same 3 services**. The
merge's only effect was matcher-engine (F-above), which is closed.

Not closed here on purpose. The estate's established remedy for a C gap is a *real* OpenAPI spec
derived from the actual request records and entities — that is how `patient-safety.openapi.yaml` and
`participation.openapi.yaml` were done, and the ratchet log is emphatic that no number was gamed. Three
accurate specs over 68 routes is a delivery with domain review, not a tail-end task in a merge pass, and
a hurried inaccurate contract would be worse than the honest gap. **The baseline was left at 0 and
nothing was absorbed into it.**

**Closed after the owner decision** (hand-authored, not generated) —
[`contracts/openapi/procedures.openapi.yaml`](../../contracts/openapi/procedures.openapi.yaml) (14
operations), [`surgery.openapi.yaml`](../../contracts/openapi/surgery.openapi.yaml) (29) and
[`mental-health.openapi.yaml`](../../contracts/openapi/mental-health.openapi.yaml) (25). Every path,
field name, enumeration and required-ness was read out of the live handlers, the DTO records, the
core-service validation and the `CHECK` constraints in each service's Flyway migrations. Each spec's
header records the conventions that differ from the estate norm, because the differences are real:
procedures and surgery return bare DTOs rather than the `ApiResponse` envelope, surgery accepts free-form
JSON bodies and ignores unknown keys, and content codes travel as query parameters because a free-text
final path segment defeats `AuthzInternalRequest.deriveResourceType` in ext_authz.

### F15 — Closing the contract debt exposed three further gaps, all fixed rather than baselined

Each surfaced only once the C gaps stopped masking it, which is the same pattern as F10.

**1. surgery-service had Spring Security on the classpath and no filter chain — the worse of the two
states.** No `SecurityConfig`, so nothing registered `TrustContextFilter`, so the
`TrustContextHolder.require()` that opens every core service in the module would have thrown on the first
real request; and Boot's default chain put generated-password basic auth in front of an entirely internal
API. Neither fault could show up in the suite, because the unit tests set the holder themselves and the
test profile disables OAuth. Fixed by mirroring `procedures-service`'s chain rather than inventing a
second idiom, plus the generator's own `SecurityBaselineConfig` output so the service tracks the estate
template. This was reported as the Category N gap `surgery-service: auth/policy/audit gaps`.

**2. The product-truth generator did not recognise a client-side redirect shim.** It credited a server
`redirect()` with `route-delegation` but not a `"use client"` page that resolves a target, calls
`router.replace()` and renders nothing — so `/my-life` was reported as having no BFF backing when it
hands off to `/home`, which carries the backing. `/provider-workspace` is the identical shape and was
quiet only because it sits on the route allowlist. Fixed as a class, not with a second per-route
exemption: detection requires the no-JSX and `return null` pair together, so a real page that redirects
on one branch still has to prove its own backing. Across the whole app it matches exactly the three
shims that exist and nothing else.

**3. The public gateway promised a provider directory the estate has not committed to.** The discovery
surface's providers tab said "Provider directory is coming soon", which reads as placeholder UX and is a
date nobody has set. The panel is not a placeholder — it explains a real scope boundary and links to
practitioner verification, which works today — so it now states the boundary instead. Its test asserted
on the old string and was updated with it.

## Change-safety gate result for this pass

Run bare, `bash scripts/guard/run-change-safety-gates.sh` reports BLOCKED on four checks
(`check-dangerous-deletions`, `check-service-inventory`, `check-backend-frontend-parity`,
`check-mobile-parity`). Those four are **merge-base attribution artefacts**, and the mechanism is worth
recording because it will recur on every consolidation pass.

### Why the bare run is misleading here

`_guard_published_refs` in [`scripts/guard/_guard-common.sh`](../../scripts/guard/_guard-common.sh)
deliberately excludes the current branch's own upstream from the set of published refs. That is right
for a normal feature lane, but this lane's upstream *is* canonical, so canonical was excluded, no
published ancestor remained nearby, and the fallback resolved base to `6dfa1063c` — 2026-07-14,
before the fortnight. The gate then reviewed **1942 commits and 5722 files**: the entire consolidated
fortnight of every lane's work, charged to this one pass.

The guard already provides the lever for this shape (`_guard_target_ref`, checked before the fallback),
and it is what CI uses on a `pull_request` event:

```bash
GUARD_UPSTREAM_REF=origin/claude/staging-ux-orchestration-remediation-Yypyl \
  bash scripts/guard/run-change-safety-gates.sh
# base-ref rule: declared target …, base = merge base with it
# BASE: cb3447ee7 — under review: 3 commit(s), 3 file(s)
# CHANGE-SAFETY: PASSED
```

### Proof the four are not this pass's defects

Two-sided, rather than asserted. The same four checks were run at **pre-merge canonical
`9e4599fe6`** over the **same** wide base `6dfa1063c`, in a throwaway worktree:

| Check | Pre-merge `9e4599fe6` | Post-merge tip | Complaint set |
|---|---|---|---|
| `check-dangerous-deletions.sh` | FAIL | FAIL | identical |
| `check-service-inventory.sh` | FAIL | FAIL | identical |
| `check-backend-frontend-parity.sh` | FAIL | FAIL | identical |
| `check-mobile-parity.sh` | FAIL | FAIL | identical |

All four were already red before a single branch was merged, and the **net delta is empty** — the
merges added no new complaint to any of them. Scoped to the delta this pass authored, all 24 checks
pass; backend–frontend parity `VERDICT: PASS`, mobile parity `VERDICT: PASS WITH ADVISORY WARNINGS`.

The files the wide run names (`NeonatalGentamicinDosing.java`, `GetAppSurface.tsx`,
`clinical/chronic-registers/page.tsx`, `specialtyToolRegistry.ts`) are canonical's own pre-existing
debt. They are real items for their owning lanes and should not be dismissed — but they are not
regressions from this pass. See **F8** for the recommendation on the base-ref shape itself.

## Final gate results on the canonical tip

`bash scripts/pipeline/run-local-quality-gates.sh`, full run on the merged tip:

**`Verdict: PASS` — 27 passed, 0 failed, 0 advisory**, with `GUARD_UPSTREAM_REF` set to canonical per F8.

| | Count | |
|---|---|---|
| Passed | 27 | every phase: both parity gates, Change-safety, Frontend, Backend, Product Truth, Phase 6, Core transaction evidence, all six full-boot phases, Mobile build checks |
| Failed | 0 | |
| Advisory | 0 | |

The three contract reds are closed: Product Truth reports **violations=0 at the unchanged baseline of 0**
with `Gaps: 0`, Phase 6 reports **104/104 complete**, and the phase6 golden-thread unit test passes with
the rest of the frontend phase.

Run **bare** on the same tip, the pipeline instead reports 24/3 — Backend-to-frontend parity, Mobile
parity and Change-safety go red on the F8 attribution artefact. Recording both runs rather than only the
green one, because the bare run is what anyone re-running this will get by default. The proof it is an
artefact is the same shape as before: re-run with the review window scoped to what this lane actually
authored (`GUARD_BASE_REF=2929668c2`, the pushed canonical tip):

| Check | Bare run | Scoped to this lane's commits |
|---|---|---|
| `check-backend-frontend-parity.sh` | FAIL (blocking 1) | **exit 0**, `VERDICT: PASS` |
| `check-mobile-parity.sh` | FAIL (blocking 1) | **exit 0**, `VERDICT: PASS WITH ADVISORY WARNINGS` |
| `run-change-safety-gates.sh` | BLOCKED | **exit 0**, `CHANGE-SAFETY: PASSED` |

Every file the bare run names was traced to the commit that introduced it, and all four are ancestors of
the **pushed** canonical tip — other lanes' published work, not this pass's:
`GetAppSurface.tsx` from `3587cc0b8`, `clinical/chronic-registers/page.tsx` from `8e0dc77fb`,
`specialtyToolRegistry.ts` from `45b3e0ef9`, `landela/page.tsx` from `38d4b8e26`. They are real items for
their owning lanes and should not be dismissed.

**`scripts/guard/run-change-safety-gates.sh`: PASSED** — with `GUARD_UPSTREAM_REF` set to canonical, per
F8. Bare, it reports the attribution artefacts documented in the section above.

On the final tip the Frontend phase passes `lint`, `type-check`, `test:typecheck-e2e`, orphan-pages,
decorative-controls, the unit suite and `build`.

Every red found on the way to this result was chased to root cause and fixed, or proven inherited:

| Red | Root cause | Outcome |
|---|---|---|
| `frontend-build` | `API_GATEWAY_URL`/`BFF_URL` never supplied to the build gate | Fixed — gate exports in-cluster defaults |
| `next build` type error | `/my-life` exported a non-Page field (F12) | Fixed — resolver made private |
| `backend-reactor-tests` | rate limiter throttling the suite (F13) | Fixed — capacity is a property |
| 8 frontend unit tests | mocks left behind by `29e76f7b0`, plus a 25th branding entry | Fixed — 2767/2768 now pass |
| Full-boot phases | generator could not run at all (F10) | Fixed — dependency provisioned |
| Product Truth, Phase 6, phase6 golden thread | three services with no OpenAPI contract (F11) | Fixed — contracts hand-authored; three further gaps found and fixed (F15) |
| Parity gates, Change-safety | base-ref attribution (F8) | Explained and now self-announcing, not a defect |

### The contract debt: how it was closed, and what was refused

The owner decision was **hand-authored contracts with real schemas**, and that is what was delivered — 68
operations described from the code, with request and response schemas, enumerations taken from the
database `CHECK` constraints, and the per-service conventions documented in each spec header.

`scripts/completeness/sync-handler-routes-to-contract.mjs` was available and would have taken minutes: it
derives paths and methods from the Spring handlers, and 86 existing contracts already carry its output.
It was **not** used here. It emits `tags: [Generated-From-Handler]` with `200: Success` and no request or
response schemas, so it would have turned three gates green without describing a single payload — and
three brand-new clinical services are the worst place to start doing that.

Nothing was absorbed into a baseline anywhere in this closure: the product-truth baseline stayed at 0 and
went green on merit, and the two in-test allowlists (`KNOWN_IN_FLIGHT`, whose own comment says keep it
empty) that would have silenced the golden-thread test were left empty. The three follow-on gaps in F15
were fixed in the code rather than recorded as accepted debt.

## Recommendation summary

| Branch | Recommendation | Note |
|--------|----------------|------|
| `claude/nervous-fermi-22e321` | Retire | Migration renumbered to `V056` on landing |
| `claude/trusting-chaplygin-48ca17` | Retire | `total_entries` re-derived to 155 |
| `claude/ruvimbo-product-Yypyl` | Retire | "Coming soon" state deliberately dropped (placeholder policy) |
| `claude/youthful-montalcini-536fee` | Retire | Unions taken for S2S trust and staffing tests |
| `claude/khuluma-hub-Yypyl` | Retire | Fully superseded, zero net change |
| `claude/post-deploy-bugfix-Yypyl` | Retire | Confirm public back-to-landing nav still present |
| `claude/affectionate-joliot-aef493` | Retire | Endpoint removed outright in canonical; branch test deleted |
| `claude/unruffled-cartwright-a85b36` | Retire | Highest-value lane; see F4 before relying on it |
| `claude/hungry-mestorf-a5cc9b` | Retire | `/enrolments/{id}/end` ported into canonical |

All nine are ancestors of canonical, so retiring the refs loses no history. **Nothing was deleted in
this pass** — the decision is the product owner's.

## Decision record (close-seven-open-issues pass)

**Decision:** Retire all nine refs. Contained on canonical; no unique history remains on the branch tips.
**Actioned 2026-07-31:** all nine remote refs deleted via `git push origin --delete`. History remains
reachable from canonical.

| Branch | Decision | Date |
|--------|----------|------|
| `claude/nervous-fermi-22e321` | Retire | 2026-07-31 |
| `claude/trusting-chaplygin-48ca17` | Retire | 2026-07-31 |
| `claude/ruvimbo-product-Yypyl` | Retire | 2026-07-31 |
| `claude/youthful-montalcini-536fee` | Retire | 2026-07-31 |
| `claude/khuluma-hub-Yypyl` | Retire | 2026-07-31 |
| `claude/post-deploy-bugfix-Yypyl` | Retire | 2026-07-31 |
| `claude/affectionate-joliot-aef493` | Retire | 2026-07-31 |
| `claude/unruffled-cartwright-a85b36` | Retire | 2026-07-31 |
| `claude/hungry-mestorf-a5cc9b` | Retire | 2026-07-31 |

F4 (CI branch gating) is closed in the same pass by `backend-diff-scoped-tests` in `.github/workflows/ci.yml`
(diff-scoped `-pl -am` on non-canonical branches; full reactor remains on canonical push).

