# Coordinator handover — 2026-07-28

**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl` (canonical for this sprint)
**Estate:** `impilo-full-preview`, single-node k3s, public at `http://41.57.127.235` /
`impilo.mohcc.gov.zw`
**Written by:** the sprint-coordinator session, at weekly usage limit. Successor: Fable 5 / Opus 5.

Read §7 (traps) before touching anything. Most of the day's cost was instruments lying, not code
being wrong.

---

## 1. State right now

| | |
|---|---|
| Pods | **116 Running, 0 not-running** |
| Site | `/` **200**, `/auth/login` **200** |
| Change-safety gate | **PASSED** (19 guards + 3 new) |
| My work | **fully landed**, 0 ahead / 0 dirty |
| Estate pinning | ⚠️ **8 services UNPINNED — see §3.1** |

---

## 1b. Security status — consolidated

Four separate things get called "the security issue". Their states differ.

| item | state |
|---|---|
| **S2S internal service tokens** (the original ask) | ✅ **FIXED, deployed, live-proven with a negative control** |
| **A2 batch — 6 merged-but-unshipped fixes** | ✅ **SHIPPED** (tuso, org-registry, document-service, experience-bff); both migration sequences verified applied |
| **The parked auth wave (Phase D)** | ❌ **OPEN — see §3.3.** Investigated, nothing changed |
| **`check-committed-secrets.sh` RED on canonical** | ⚠️ baseline drift, **not a leak** — see §3.6 |

**The S2S issue** was the sprint's opening request: internal service-to-service calls were not
carrying their own credentials. Fixed, merged, deployed, and proven live — including a negative
control confirming the check discriminates, which matters because on preview the global auth bypass
means most "authorization proofs" prove nothing (§3.3).

**The A2 batch** shipped six live fixes that were merged but never imaged: an ungated endpoint
letting any authenticated internal caller mark a facility operational; verification decisions gated
on a caller-supplied `X-Actor-Type` header; cross-tenant read/mutate of facility data-gap tasks;
**cross-tenant document reads including signed-URL minting**; plaintext invitation tokens; and an
ended appointment revoking nothing. *Merged ≠ landed applies to security work.*

**What remains open is §3.3**, and it is the largest standing risk on the estate: 98 deployments
running with authentication disabled on the single public HTTP stack, and the safety interlock that
would make that state unreachable present in only 2 services of 74.

## 1c. Companion handover documents

- **`docs/handover/2026-07-28-rmnp-lane-handover.md`** — the RMNP lane's own brief. Carries the live
  digests, next free migration numbers (pct V438, CKP V042), the ordered confidentiality flip list
  with its two non-engineering blockers, and the **`/confidential/` path trap**: the routing guard
  gates the lane on a path substring, so an endpoint mounted elsewhere would post-flip withhold every
  stamped record from every requester *including its author*. It also records the design decisions a
  successor will be tempted to "fix" — stamp-fails-open vs read-fails-closed chief among them.

  **Two items it flags as having no owner:** Surgery's stale lease (also §3.6 here), and the
  RMNP-owned BFF endpoints for the citizen SMBP and CHW postnatal surfaces — both of which must mount
  under `/confidential/`.

## 2. What landed this sprint

### 2.1 The payload-shape class (the biggest product-integrity find)

`experience-bff` had two populations of read endpoints: some built a real JSON:API row, others piped
the upstream `JsonNode` straight into `data`. **The TypeScript hooks declared `attributes` for
both**, so on every passthrough endpoint the declared type was a claim about a payload nothing
produced.

Live crashes fixed, verified against real rows on preview:
- **allergies** — PCT returns flat snake_case; `PatientBanner` renders on *every* EHR screen and
  reads `a.attributes.severity`. It crashed **precisely on patients who HAVE a severe allergy** —
  clean for everyone else, which is why nobody saw it.
- **immunizations** — PCT returns `{items,page,size,total}`, hook declared an array. `?? []` cannot
  rescue a non-nullish object; `.filter is not a function` fired on **every** load.
- **encounters / timeline** — PCT returns journeys with nested encounters; ~35 pages plus the banner
  deref `.attributes.status`. Threw for every patient who had ever had a journey.
- referrals, clinical notes, lab orders, inpatient admissions, queue, telemedicine — same class.

`support/JsonApiRows.java` normalises at the boundary. `scripts/guard/check-typed-hook-passthrough.sh`
blocks regression.

**Second defect found while testing:** PCT only ever sets `STARTED`/`COMPLETED`/`ON_HOLD`; the shell
asked for `IN_PROGRESS`/`ACTIVE` in 37 places. `activeEncounter` was **undefined for every patient in
the country** — silent, nothing threw. Fixed by deriving `isOpen` at the BFF (PCT's vocabulary stays
canonical) and sweeping the 37 sites.

### 2.2 Phase B — eleven clinical fabrications

Surfaces reporting success while calling nothing. Each had a real service behind it that nothing
invoked. Full table in `memory/phase-b-clinical-fabrications-cleared.md`. The worst:

- **Triage** combined the clinician's acuity with an ad-hoc points score via `Math.max` — **opposite
  scales**, so bad vitals *demoted* the patient; a score >5 failed `@Max(5)` and the screen had **no
  `onError`**, so the sickest arrivals silently got no triage record.
- **Drug interactions** hardcoded "No known interactions found" in **both** the BFF and the UI,
  independently, while a real engine sat uncalled.
- **"Save SOAP Note"** saved to local state, on the default tab, beside a dictation button.
- **Shift lifecycle** — `/v1/shifts/{current,start,end}` served by nothing; duty is an authorization
  input, so duty state could never be established. Records were in `tuso.shift` all along.
- **Citizen account closure** answered 204 unconditionally on a statutory DSR surface.

### 2.3 Guards built (all negative-controlled — I broke each to confirm red)

| guard | finds |
|---|---|
| `check-typed-hook-passthrough.sh` | BFF passthrough where the hook declares `attributes` |
| `check-cross-tree-node-modules.sh` | the symlink that wiped the shell 3× (§7.1) |
| `orphan-page-check.mjs` | pages that exist but nothing links to — **78 found** |
| `decorative-control-check.mjs` | `<button>` with no handler — **70 found, now 67** |
| `route-parity-check.mjs` (improved) | route-count merge trap (§7.4) |
| `guard_assert_repo_path` / `guard_assert_scanned` | gates inspecting the wrong tree / matching nothing |

### 2.4 Other

- **58 scripts** defaulted `REPO_PATH` to the main checkout — a gate run from any worktree inspected
  the wrong tree. All now script-relative.
- **e2e specs type-checked for the first time** — 28 real defects, incl. `route.fulfill(emptyList)`
  putting the object in fulfill's *options* position, so five coverage mocks returned an **empty
  body** and every spec relying on them asserted against nothing.
- **pct Spring context now boots under test** — a JPQL `CURRENT_TIMESTAMP` on an `OffsetDateTime`
  path made every `@SpringBootTest` in pct fail. All six are named `*IT` and excluded from surefire,
  so the context couldn't boot *and nothing was trying to*.
- **Surveillance case investigation + contact tracing + line list** — built end to end (migration →
  service → BFF → UI). See §4.1.
- **4 guards were blind to untracked files** (`git ls-files` lists only tracked).
- **Security batch shipped**: 6 merged-but-unshipped fixes across tuso, org-registry, document-service,
  experience-bff. Migrations verified applied.

---

## 3. Outstanding — needs a decision, not just work

### 3.1 ⚠️ HELM REPIN — the standing landmine

**Eight services run digests the committed `values-full-preview-digests.generated.yaml` does not
name**: experience-bff, pct, oros, tuso, vito, one-ui-shell, clinical-knowledge-platform, forms.

**A `helm upgrade` against `impilo-full-preview` reverts three lanes' work and reports success.**

Held deliberately by the PO until all sessions finish. When lifted: **generate the pin FROM THE LIVE
ESTATE, not from any worktree** — a worktree's view would revert services other lanes deployed. Then
run `check-runtime-image-truth.sh`. Detail in `memory/helm-repin-held-pending-sessions.md`.

### 3.2 Five governed reports that cannot execute

`reporting.rpt_report_definitions` has 7 ACTIVE rows. **Five query `varapi.*` from the `reporting`
database** — a separate database, no `postgres_fdw`, no `dblink`. Proven live:

```
select count(*) from varapi.provider_applications;
ERROR:  relation "varapi.provider_applications" does not exist
```

One is `STATUTORY`, one `PUBLIC_INTEREST`. **A statutory return that silently produces nothing is
worse than one that is absent** — absence gets noticed, an empty result reads as "no activity".

Found independently by **three** lanes. None will touch it, correctly: rewriting another lane's
governed statutory definitions is a design decision. The fork is executor / FDW / move the definitions
to the services that own the data (two lanes already did the last for their own).

**There is no executability check anywhere.** `ReportRunService.validateQuerySafety` checks only for
disallowed SQL *keywords*. Reachability references: zero.

### 3.3 Phase D — the parked security wave (started, not landed)

`IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` is injected by helm for `global.environment ==
"full-preview"`. **98 deployments run with it on; 74 services read it.**

**The finding:** the correct safety interlock already exists — in **2 services of 74**.

```java
} else {
    if (!allowInsecurePermitAll) {
        throw new IllegalStateException("Refusing insecure permit-all mode: …");
    }
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
}
```

`ai-model-registry-service` and `llm-orchestration-service` have it. **The other 72 collapse straight
to `permitAll()` with no second gate.** Someone built the right pattern; it was never propagated.

**Next step, unstarted:** propagate the interlock to the 72, and inject the confirming flag for
`full-preview` **in the same change** — otherwise all 72 refuse to start on next restart. Same
coupling shape as RMNP's V437. Do it in a quiet window; other lanes deploy continuously.

Also in this wave, all unstarted: OPA is doctrinal PDP but `AuthzProperties.opaMode` defaults `"OFF"`;
`SafeDisclosureService` (khuluma PHI redaction) has **zero production callers**; Postgres
`sslmode=verify-full`, Redis TLS and Kafka SSL absent from Helm values. Tenant work sequences inside
it: incoherent writes → mint a `tenant_id` claim → enforce. **Enforcing without a claim buys "the
caller cannot choose", not isolation.**

> "Only preview" is not reassurance — full-preview **is** the single public HTTP stack.

### 3.4 Category B — 67 dead controls remaining

PO ruling: **A = wire it (capability exists), B = build it (capability doesn't).** No disabled-state
interim.

Surveillance was the first B and cost a migration, two entities, a service, a controller, four BFF
endpoints, four hooks and the UI — for **3 controls**. The remaining 67 are a programme at that rate.

Baselined in `ui/one-ui-shell/scripts/decorative-control-baseline.mjs`; the guard blocks *new* ones and
reports entries that become stale. **The baseline can only shrink.** Highest-consequence remaining:
`OutcomeSection` "Save Draft"/"Preview Summary" (discharge — a theatre-scoped draft path exists, may
be Category A), `BillingPanel` "Resubmit", EHR tab "Edit"/"Add Member"/"New Assessment".

### 3.5 78 orphan pages

Pages that build, type-check, and are reachable by nothing. **Registration should follow completeness,
not precede it** — registering an unfinished page exposes incomplete functionality. Baselined the same
way.

### 3.6 Cross-lane items with no owner

- **Surgery's stale lease** — `iatg-surgery-procedures-leases.md:253-256` says P5 is blocked on a
  confidentiality seam that **landed 2026-07-26**. Adult Medicine already corrected theirs. I had no
  channel to Surgery. *One message unblocks a lane.*
- **W5c offline** — two confirmed blockers in services no active lane owns: `tshepo-offline`
  `READ_ACTIONS` has no problem/programme read, and offline-edge replays `/fhir/Observation` only.
  Until they land, **an HIV/TB clinician offline cannot see whether a patient is on ART**. Adult
  Medicine will consume the seam immediately. (Their third claimed blocker — `createCollection` has
  no production callers — is **false**; `useOfflineStore<Household>("households")` is live in the
  outreach screens.)
- **§23 demonstrations** — PO-only. Adult Medicine proposed ten grounded in shipped behaviour, each
  naming the failure it would expose.
- **`check-committed-secrets.sh` is RED on canonical** — pre-existing baseline drift (dev `change-me`
  placeholders moved between files). Not a leak. **But a security guard sitting red is how people
  learn to ignore one.** Prune the baseline.

### 3.7 Unmerged branches — **39 remote branches are NOT merged into canonical**

Measured with `git branch -r --no-merged origin/claude/staging-ux-orchestration-remediation-Yypyl`.
This is the authoritative list; a worktree audit is not the same thing and undercounts.

**MY OWN WORK IS FULLY MERGED.** Everything this coordinator session produced is on canonical
(0 ahead, 0 dirty, verified in both the worktree and the shared checkout).

**Recent — likely live sessions. ASK THE OWNER BEFORE MERGING.**

    +1     12 hours ago   claude/adoring-torvalds-f773c8
    +9     16 hours ago   impilo-learning-staging
    +1     22 hours ago   claude/gallant-darwin-f9ac27
    +1     2 days ago     claude/wonderful-elgamal-a3517e
    +2     2 days ago     claude/gallant-taussig-5f58e7
    +2     2 days ago     claude/zen-goodall-6f6b21
    +7     2 days ago     claude/youthful-montalcini-536fee
    +1     2 days ago     claude/strange-cerf-9b2cff
    +3     2 days ago     claude/affectionate-joliot-aef493

**Last ~2 weeks — probably wound down, verify before assuming abandoned.**

    +4     7 days ago     claude/khuluma-hub-Yypyl
    +7     7 days ago     claude/ruvimbo-product-Yypyl
    +4     8 days ago     claude/post-deploy-bugfix2-Yypyl
    +2     8 days ago     claude/post-deploy-bugfix-Yypyl
    +1     8 days ago     claude/trusting-chaplygin-48ca17
    +1     10 days ago    claude/nervous-fermi-22e321
    +3     11 days ago    claude/optimistic-fermat-d43c88
    +1     2 weeks ago    claude/upbeat-mccarthy-8a77ac
    +91    2 weeks ago    claude/unruffled-cartwright-a85b36

**Older than 2 weeks: 21 more branches**, including `claude/unruffled-cartwright-a85b36` (+91,
2 weeks) and long-lived lines that are almost certainly not merge candidates at all —
`production` (+52), `peter/vnext-1.0` (+54), `claude/staging-ux-orchestration-remediation-jb5O0`
(+64, the previous sprint's branch), `staging`, `ioptime/dev`, and several `split/*` and `local/*`.

**Do not bulk-merge.** Several of these predate architectural decisions that have since landed, and a
directory-pathspec commit or a blind merge is exactly what produced the `90e64207f` incident (§7.3).
The right sequence is: ask the owner → merge canonical INTO their branch → let them resolve and push →
then it merges cleanly. `impilo-learning-staging` (+9, 16h) and `claude/adoring-torvalds-f773c8`
(+1, 12h) are the two most recent and most likely to matter.

Regenerate this list at any time with:

```
git fetch origin --prune
git branch -r --no-merged origin/claude/staging-ux-orchestration-remediation-Yypyl
```

---

## 4. Work in flight

### 4.1 Surveillance — COMPLETE (migration → service → BFF → UI)

`V012` + `CaseInvestigationService` + `CaseInvestigationController` + 4 BFF endpoints + 4 hooks + the
panel. **Not yet deployed** — the surveillance-service image has not been rebuilt. Deploy with the
recipe in §7.6.

Design decisions worth preserving: updates are a **history**, not a column (a retrospective asks *when
did we know*); contacts do **not** require a registered patient (tracing precedes identity); closed
vocabularies with unrecognised values **rejected**, because one row reading "Confirmed" among
"CONFIRMED" drops silently out of the count that triggers a response.

### 4.2 Phase D — investigated, nothing changed yet

See §3.3. No code written.

---

## 5. Honest feedback

**The code was mostly fine. The instruments were the problem.** Every error this sprint — mine, and
three other lanes' — was *a check that couldn't fail or a measurement that lied*, never a wrong
opinion about the code.

My own, all self-caught, all the same shape:

1. A `REPO_PATH` assertion that compared a value to itself — **could never fail**, in the commit adding it.
2. A guard whose "nothing installed" early return sat **above** the violation report — reported PASS on a tree I'd just watched fail.
3. A case-sensitive regex that nearly **refuted a true finding** from another lane.
4. `grep -E "^(D|R)"` matching the `Date:` header — 25 false positives auditing my own commits.
5. Grepping `useQuery|apiClient` and concluding a working surface was static — it fetches via a domain hook.
6. Planting a V437 duplicate in a checkout that has no V437, so the negative control proved nothing.

**The rate isn't the worry; not catching them would be.** The rule that saved every one:
**prove the instrument on a known positive before trusting a clean result.**

**What worked:** three lanes checking each other's claims rather than accepting them. RMNP corrected
two of my statements after testing them live — I'd said a stale pod would silently under-protect, and
a CHECK constraint makes it fail loudly. Adult Medicine corrected their own report publicly rather
than quietly. That reciprocal verification caught more than any individual review.

**What I'd do differently:** I asked the PO the same sequencing question three times instead of
picking and stating the reasoning. And I spent too long on a browser walk that my own tooling kept
breaking — three sign-ins burned before I concluded my `navigate` was destroying the session.

---

## 6. Laws worth keeping

All in `~/.claude/projects/-opt-impilo-repos-Impilo-vNext/memory/`. The load-bearing ones:

- **`registered-is-not-executable`** — a registry row is not evidence of execution. Check for the executor.
- **`pct-problems-never-reach-the-shr`** — readable-but-never-written: read-side enumerations are not evidence of ingestion.
- **`a-red-in-one-environment`** — a failure in one env is not proof of failure in another, exactly as a pass isn't.
- **`grep-the-consumer-facing-api`** — an internal factory's caller count says nothing about production use.
- **`bff-passthrough-breaks-typed-hooks`** — a declared type is a claim about a payload, verified by nothing.
- **`shared-index-commit-law`** — **worktree > exact-file pathspec > directory pathspec > bare commit.** A directory pathspec commits another lane's deletion. A swept deletion leaves no residue to review.
- **`dont-skirt-incomplete-functionality`** — build it, or surface it precisely. A register of gaps is skirting.
- **`a-check-can-outlive-its-guard`** — prove every check by breaking what it guards.

---

## 7. Traps a successor will hit

**7.1 Never symlink `node_modules` across trees.** `next build`'s standalone output contains a
workspace symlink and its cleanup walks *through* it, deleting source in the other checkout. It wiped
`ui/one-ui-shell` (2,885 files) **three times**. I swept 46 such links across 8 checkouts. Both levels
matter — workspace deps hoist to `ui/`, so linking only the inner path fails to resolve and the
"obvious fix" is the dangerous one. Do a real install.

**7.2 The shared checkout has ~143 uncommitted files from other lanes.** Do not `git pull` there, do
not `git stash`. **Work in a worktree** — it also makes the directory-pathspec footgun structurally
unreachable.

**7.3 `git commit -- <paths>` is only safe with EXACT FILES.** Audit recipe:
`git show <sha> --name-status -M -C --format="" | grep -P "^(D|R\d*)\t"` — note `--format=""` and the
tab anchor; without them `^D` matches `Date:`. **Prove the filter on a known positive** (`90e64207f`)
before trusting a clean run. Estate-wide audit of the last 24h: **149 commits, exactly 1 deletion**.

**7.4 `EXPECTED_ROUTE_COUNT` is a merge trap.** Two lanes each adding one route and each setting the
same count merges cleanly while the array gained both. Re-derive from the file after every merge,
including one a retry loop performs.

**7.5 The pct test profile uses H2, runtime uses Postgres.** They differ in at least two ways
(`CURRENT_TIMESTAMP` typing; `value` is reserved in H2). A green under H2 is weaker than it looks — a
red is too.

**7.6 Deploy recipe** (`memory/single-service-hotfix-redeploy-recipe.md`):
`mvn -o -q -DskipTests package` → **verify the jar contains your change** → `build-runtime-image-from-jar.sh`
→ push → resolve the digest → **check it is non-empty** → `kubectl set image deploy/$SVC "*=…@$DIG"`.

The digest lookup needs the **image INDEX** Accept header:
`application/vnd.oci.image.index.v1+json`. The manifest and docker-v2 headers both return **EMPTY**.
`docker push` output / `docker inspect RepoDigests` needs no negotiation and is more robust — use both
for corroboration. **The non-empty check has already prevented `InvalidImageName` pods twice.**

**7.7 Batch deploys and prune between them.** Single-node estate; image churn took it down once.

**7.8 Two tenant planes.** REGISTRY `0000…0001` (facility/provider masters) vs CARE
`0000…4000-8000-0001` (encounters/claims). **Neither is "canonical".** The browser sends CARE. Seven
clinical proof scripts still seed on REGISTRY and their green attests to data no clinician can see —
deliberately not repointed until the authoritative-tenant work lands, because aiming fixtures at
"whatever the browser sends today" chases a moving value.

---

## 8. Suggested first moves

1. **Ask the PO to lift the helm hold** and repin from the live estate. Everything else is safer after.
2. **Deploy surveillance** (§4.1) — built, tested, unshipped.
3. **Route §3.6** — Surgery's lease and W5c need one message each.
4. **Phase D interlock** (§3.3) in a quiet window, helm + code together.
5. **Prune the committed-secrets baseline** so a security guard stops sitting red.

Do not start Category B's remaining 67 before Phase D unless the PO overrides — a public stack with
auth disabled outranks any individual dead button.

---

## Addendum — Adult Medicine lane, corrections before handover (2026-07-28, adult-medicine session)

The coordinator asked each lane to correct its own state here, because the successor will read this
brief as true. Three corrections, one of which corrects a claim of *mine* that this brief has
already carried forward into fleet law.

**1. The lane state in §3 is substantially out of date.** It records "Waves 0–5 complete plus the
BUTANO gap; remaining and genuinely not started — inpatient medical-ward workspace, analytics/offline
surfaces, §23 demonstrations". Since then the pack has been rebuilt against the brief, which **was
supplied by the PO and is now committed** at `docs/clinical/adult-medicine-domain-pack/brief.md`.

Landed since: §9 multimorbidity engine · chronic-disease registers and the estate's first cohort read
· §7 examination framework · §8's thirteen specialty workspaces · `libs/medicine-domain` · §21
analytics · §14 consultation and MDT · §19 `ClinicalImpression` and `DetectedIssue` producers · §11
result action · the ten §23 demonstrations proven at the record layer (35/35).

**Authoritative status is per-section in
[`docs/clinical/adult-medicine-domain-pack/completion-register.md`](../clinical/adult-medicine-domain-pack/completion-register.md):
3 DONE · 18 PARTIAL · 2 NOT BUILT, §25 Definition of Done NOT MET.** Full successor brief:
[`docs/clinical/adult-medicine-domain-pack/handover.md`](../clinical/adult-medicine-domain-pack/handover.md).

**2. §3.6's §23 line is stale.** "PO-only. Adult Medicine proposed ten" — the PO supplied the brief.
The real §23 is ten *clinical journeys*; my ten proposals were a different kind of thing (assertions
about the pack's own safety properties) and are **retired**, preserved in `demonstrations.md`'s
appendix mapped to the tests that carry them. Do not hand the successor my proposals as the
requirement.

**3. My own correction, which this brief carries in §3.6, is itself half wrong.** It reads
*"`useOfflineStore<Household>("households")` is live in the outreach screens"* — plural. **It is live
in one screen**: `apps/mobile/provider-app/src/screens/outreach/HouseholdListScreen.tsx:27`.
`ScreeningScreen.tsx:21` *imports* the hook and only ever calls `useSyncEngine()`; the import is
unused. The two blockers are still real and still correctly attributed — but the read gate is in
**`tshepo-offline-service`** and is **two-layered** (`OfflineRulesEngine.READ_ACTIONS:51` *and* the
capability token's `allowed-offline-actions` filter at mint time), and in `offline-edge-service` the
client is not the constraint — `OfflineEdgeService.replayActions:143` only dispatches to FHIR for two
vitals action types.

This one matters beyond the fact: I over-corrected an overstated blocker, and the over-correction
propagated into a fleet document within hours. **A correction is a claim and needs the same evidence
as the thing it corrects.**

**4. Two defects the successor should see before any planned work** — both verified verbatim:
- **`/clinical-tools` ships sixteen calculators that compute nothing** (`page.tsx:536`) — MELD,
  Child-Pugh, eGFR CKD-EPI, SOFA among them — claiming to be "connected to Clinical Knowledge
  Platform (port 8270)" when they are connected to nothing, under a **"Validated"** badge. This is
  Category B's shape at its worst, and it is clinical.
- **A duplicate MDT system of record, which this lane created**: `pct V051__mdt_board_sessions`
  (telemedicine, TM-B15) predates our `V114 pct_mdt_decisions`. Two tables, two BFF controllers.
  Consolidation is a joint decision with the telemedicine lane; V051 is not ours to alter.

**On the synthesis credited to this lane in §5** — *every error was a check that couldn't fail or a
measurement that lied, never a wrong opinion about the code* — it held all day and then found its own
limit. The errors above are a third kind: **a claim about absence made without a search.** I built
the duplicate MDT table by treating my own demonstration rig's `CANNOT "MDT has NO record at all"`
line as evidence. It reads like a finding because running something produced it, but a CANNOT asserts
absence, and absence is what a search proves and an assumption does not. Worth adding to §5 as the
third shape.
