# Working in the shared checkout — concurrency runbook

Up to ten agent sessions work `/opt/impilo/repos/Impilo-vNext` (== `/home/robert/Impilo-vNext`, a
**symlink**, not a second clone) at the same time, plus worktree sessions under `.claude/worktrees/`.
They share **one git index**, **one working tree**, **one `HEAD`**, and **one `~/.m2/repository`**.

Every hazard below was hit for real on 2026-07-26 — several of them more than once, by different
lanes, each rediscovering it from scratch. That repetition is what this file exists to stop.

> `git worktree list` does **not** reveal the sessions sharing the main tree. Use the session-listing
> tool to see peers before assuming a change is yours.

---

## 1. The git index is shared — commit by pathspec, always

```bash
git commit -m "msg" -- <path> [<path>…]     # correct
git commit -m "msg"                          # takes the WHOLE index, including peers' staged files
git add -A ; git add .                       # never — hoovers up peers' working-tree edits
```

`git commit` with no pathspec commits the entire index no matter how many shell invocations you used
or who staged what. Six separate incidents on one day, including a UI bugfix commit that carried nine
foreign backend files to origin under someone else's message.

**Before every push:** `git show --stat HEAD` and compare the **file count** against what you intended.
A silently-skipped or silently-added file produces a plausible-looking stat block.

**Untracked files:** `git commit -- <path>` **silently skips** paths git does not already track (exit
0, no error). `git add <exact-path>` them first, then commit by pathspec in the same breath.

**Never stash or discard a peer's work.** If a rebase demands an autostash: diff the incoming commits
against the dirty set first, confirm zero overlap, then autostash — and verify every file returns.

## 2. `~/.m2` is shared — and it lies in two different ways

| Symptom | Cause | Fix |
|---|---|---|
| `ZipException: invalid LOC header`, all `@SpringBootTest` classes error at once | a peer is **rewriting** a snapshot jar while your JVM reads it | re-run when `pgrep -af mvn` is quiet |
| `cannot find symbol` / `package does not exist` for a class you can see in the source | **stale** jar predating a peer's new class | `mvn -o install -pl libs/<that-lib>` or use `-am` (below) |
| A reproducible **wrong value**, with source that reads correct | **poisoned** jar installed from a *different branch* | check the jar's provenance before the diff |

**The poisoned case is the dangerous one.** A lane installed `libs/paediatric-domain` from a feature
branch; every other lane's build silently used its constants, and a growth test went red on source that
was entirely self-consistent. The change existed **only in a binary, on no branch the affected lanes
could read** — two sessions mis-attributed it before anyone inspected the jar.

**LAW: before believing a red test in a module you did not touch, inspect the installed jar** —
`javap -p -constants -cp <jar> <Class>` against your source. One command settles what an afternoon of
reasoning cannot.

### How to build so you neither read stale jars nor poison peers

```bash
mvn -f services/pom.xml -pl <svc> -am test    # PREFERRED for verification
# or, equivalently:  cd services && mvn -pl <svc> -am test
```

⚠ **There is no aggregator pom at the repo root** — the reactor root is `services/pom.xml`. So
`mvn -pl services/<svc> …` run from the repo root fails with *"Could not find the selected project in
the reactor"*. Use one of the forms above. (`mvn -f services/<svc>/pom.xml test` also works but builds
the module alone, so it reads `~/.m2` jars for its dependencies and loses the point of `-am`.)

⚠⚠ **Never pipe the build and then judge the exit status.** `mvn … | grep … | tail` reports *`tail`'s*
exit code, so a run that never resolved the module — or never compiled — reads as a pass. Either don't
pipe, or check `${PIPESTATUS[0]}`. This exact masking hid the reactor error above, and the same bug
reported a rejected `git push` as successful the same day.

`-am` builds the dependency modules **in the same reactor**, so resolution uses their freshly compiled
`target/classes` rather than `~/.m2` — you get current code **and** write nothing to the shared repo
(the `test` phase does not install). Slower; correct.

```bash
mvn install -pl libs/<lib>             # publishes to EVERY session — announce it or avoid it
mvn -o -pl services/<svc> test         # fast, but trusts whatever jar is in ~/.m2 right now
mvn … -Dmaven.repo.local=<scratch>     # full isolation for a long verification run
```

⚠ **`cp -al` does not isolate a repo.** maven-install-plugin overwrites jars **in place**, so hard
links leak in both directions. Only a real copy (distinct inodes) isolates.

⚠ **Constants inline.** Changing a `static final` in a lib requires `mvn clean` in dependents —
compiled test classes keep asserting the old value with no source disagreeing.

## 3. `HEAD` moves under you

`HEAD` is shared: a peer's pull or commit moves it mid-operation. Observed moving eight times during
one four-commit wave.

- `git pull --ff-only` (never `--rebase` on this tree unless you own every dirty file).
- Re-verify immediately before pushing; if canonical moved, re-merge and **re-run the gates on the
  merged tree**, not on the tree you tested ten minutes ago.
- `if git push …` — **never** `git push … | tail`, which reports `tail`'s exit status and therefore
  always looks successful. A rejected non-fast-forward push has been reported as "PUSHED" this way.

## 4. A merge blocked by a file you never touched

```
error: Your local changes to the following files would be overwritten by merge: <file>
```

The file may be **byte-identical to HEAD**. Git compares stat metadata before content, so a stale
index stat entry — left by a peer who staged the file before the content converged — blocks the merge.

```bash
git update-index --refresh          # recomputes stat metadata ONLY
git diff HEAD -- <file>             # empty ⇒ it was never a real change
```

`--refresh` cannot alter, stage, unstage or discard content, so it is safe on a shared tree even while
peers hold real staged WIP. Diagnose before escalating; do not stash.

## 5. Only the owning lane lands its own branch

Merging a peer's in-flight branch into canonical propagates **unfinished work under someone else's
name**, with no owner watching it. This happened with a pre-fix clinical constant; it superseded
cleanly only because the owner's HEAD happened to be strictly ahead.

## 5a. Run the guards BEFORE you push, not after

`scripts/guard/run-change-safety-gates.sh` runs in CI — **after** your commit is already on canonical.
Because every lane pushes straight to the shared branch, a guard that only runs in CI is a *detector*,
not a *control*: it tells you what already landed.

**Two duplicate `V432` migrations reached canonical on 2026-07-26 while a guard for exactly that defect
existed and was correctly wired.** Run against the tree afterwards it failed immediately, naming both
files. Nobody had run it before pushing.

```bash
REPO_PATH=$PWD bash scripts/guard/run-change-safety-gates.sh            # before every push
REPO_PATH=$PWD bash scripts/guard/check-migration-version-collisions.sh # if you touched a migration
```

🚨 **`REPO_PATH=$PWD` is MANDATORY from a worktree, and its absence produces a FALSE GREEN.**
`run-change-safety-gates.sh` line 3 is `REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"`
followed by `cd "$REPO_PATH"` — so run from a worktree **it gates the main checkout**, which is sitting
on canonical, i.e. it gates canonical against itself and passes. A lane hit exactly this: `CHANGE-SAFETY:
PASSED` with **`Files changed` EMPTY** and `Branch: …-Yypyl` — their merge was never gated at all. With
`REPO_PATH=$PWD` the same run reported their own branch, their own nine files, and passed for real.

**The tell is an empty (or foreign) file list.** Always read the `Head:`/`Branch:`/`Files changed` lines
the gate prints and confirm they are *yours*. A guard that reports on someone else's tree is the
[[build-script-ignores-worktrees]] hazard recurring inside a *guard* rather than a build — and it is the
same self-checking-reach problem as a discovery rule that silently matches nothing.

**And stop on what it says.** A second lane later ran the scan, watched it print `V432`, and pushed in
the same command — the push added no new duplicate, but a known one was walked past. So:

> **Running a guard and pushing in the same command is not running the guard.** Stop on ANY hit,
> including one that is not yours. Route it, then push.

Never `&&` a guard into a push. A control you do not stop for is only a detector with extra steps.

**Which changes the gates consider yours.** The base is the newest ancestor of `HEAD` that is already
published on a remote branch other than your own branch's — so on a **merge** the commits canonical
brought in are not charged to you, on a **fast-forward** nothing is, and on a **lane branch** every
commit you authored is under review, not just the last one. Pushing your *own* branch does not make
work published for this purpose: it is unreviewed until it reaches canonical.

That rule replaced a plain `HEAD~1` on 2026-07-27, and the old one failed in *both* directions at
once. On a lane that had deleted a service file and then merged 72 canonical commits, the gates named
three of canonical's long-shipped files — a growth-engine consolidation, a landing surface, a mobile
tool registry — and **did not name the lane's own deletion**, which sat in `HEAD~1`. That is how a
guard gets muted: it fires on work that is not yours while missing the work that is.

```bash
GUARD_BASE_REF=<sha>       # review exactly this range (escape hatch; CI sets it)
GUARD_UPSTREAM_REF=<ref>   # name the branch your lane is measured against
```

`scripts/guard/tests/base-ref-resolution-test.sh` asserts the rule over throwaway repositories and
runs first inside the gate suite; the rule itself is written out in `scripts/guard/_guard-common.sh`.
Guards run from a **worktree** now review that worktree — `REPO_PATH` no longer defaults to the main
checkout — so a lane no longer gates somebody else's tree by accident.

## 5b. Landing on a fast-moving canonical

Combining §3, §5 and §5a into one procedure, because landing your own branch on this shared branch
is where they all bite at once. Canonical moved **four times** during one vitals landing on
2026-07-26 — every time the gates and module tests finished, the tip had advanced, so a naive
"test then push" never converged and each attempted push was a rejected non-fast-forward.

The loop that does converge:

```bash
CANON=origin/claude/staging-ux-orchestration-remediation-Yypyl
git fetch origin claude/staging-ux-orchestration-remediation-Yypyl
git merge "$CANON" --no-edit                      # re-merge the LATEST tip, not the one you tested
git merge-base --is-ancestor "$(git rev-parse $CANON)" HEAD || exit 1   # the ancestor LAW
bash scripts/guard/run-change-safety-gates.sh || exit 1     # already scoped to your work — see §5a
```

Then two rules that make it terminate instead of looping forever:

1. **Re-run only the module the new delta touched.** After each re-merge, diff the incoming commits
   against your last-tested tip: `git diff --name-only <last-tested> $CANON | grep -E '<your modules>'`.
   Empty ⇒ your compile surface is unchanged and the green you already have still holds; push. Non-empty
   ⇒ re-run that module's tests (`cd services && mvn -pl <svc> -am test`, never piped) before pushing.
   Re-running the whole suite on every tick guarantees the target outstruns you.
2. **Merge and push in the *same* block, gated on a clean delta**, so nothing moves in the gap:

   ```bash
   git merge "$CANON" --no-edit
   DELTA=$(git diff --name-only <last-tested> "$(git rev-parse $CANON)" | grep -E '<your modules>')
   if [ -z "$DELTA" ] && git merge-base --is-ancestor "$(git rev-parse $CANON)" HEAD; then
     git push origin HEAD:claude/staging-ux-orchestration-remediation-Yypyl; echo "rc=$?"   # §3: check rc directly
   fi
   ```

**The `GUARD_BASE_REF="$(git rev-parse $CANON)"` this step used to carry is no longer needed.** It
existed because the gate's default base was `HEAD~1`, which on a merge commit attributed every merged
canonical commit to you; §5a is now the default and computes the same scope by itself. Setting it by
hand is harmless but redundant — and it is still the escape hatch if you ever need to pin a range.
If a run blocks on a file outside your diff, treat that as a real hit, not a base artifact: confirm
(`git diff "$CANON"..HEAD -- <path>` empty) and get it cleared before pushing. Do not silently push
past a blocked gate on your own judgement.

**A merge lands source, not a running fix.** If the regression you are closing is user-visible, the
merge does not close it — the deployed image is still the pre-merge build until someone rebuilds and
rolls it. Say so explicitly in the landing note so it is not left for "whoever comes next" (§7).

## 6. Migration numbers

- Reserve **above every committed claim** in every `docs/registry/iatg-*-leases.md` — not above the
  highest number you can see on disk.
- Check against the **migration directory on the merged tree** at commit time, never against a lease's
  "Consumed" column. The frontier moves while you merge.
- Cutting a number needs **both** `ls .../db/migration | sort -V | tail` **and**
  `git status --porcelain` over that directory — the dangerous neighbour is the untracked one.
- A cross-lane **dependency** (FK, INSERT/ALTER against a higher-band table, a seed resolving against
  one) lives in the band of the lane owning the **referenced** object; a lower band always applies
  first and can never depend on a higher one.
- `scripts/guard/` carries a duplicate-version guard — two files sharing one version merge without a
  git conflict and are hidden by `validate-on-migrate: false`, surfacing only on a deployed database.

## 7. Deploys

- Before any single-service deploy: `git merge-base --is-ancestor <origin canonical tip> HEAD`.
  A deploy from a stale tree **silently un-ships** whatever merged since — no error, no 404. A
  config-only change is the easiest thing in the estate to un-ship this way; only the image's
  source-commit label reveals it.
- `mvn clean package` after any migration rename, and **list the jar** — stale `target/classes` has
  shipped two copies of one migration version, which Flyway refuses to boot on.
- **Repairing an applied migration after a rename: move the HISTORY ROW, never drop the table.**

  ```sql
  UPDATE <schema>.flyway_schema_history SET version = '<new>' WHERE version = '<old>';
  ```

  The table already has the right shape; only the version label needs to move. The destructive reflex —
  `DROP TABLE`, delete the history row, redeploy — **silently deletes co-tenant rows**, because `pct`,
  `inpatient` and friends are written by several lanes at once. One lane's renumber repair on
  2026-07-26/27 dropped two shared `pct` tables twice and took every lane's rows in them, then read as
  "an estate repair wiped the fixture". Synthetic preview data re-seeds, but the misattribution costs
  another lane an investigation into data *they* think they lost.

- A migration is **landed** when `flyway_schema_history` says so **on the target, in the right
  schema** (`<service>.flyway_schema_history`, not `public` — an unqualified query reads exactly like
  a missing table). It is **correct** when a constraint it declared can be shown to bite there.

---

Related memory-level laws: mutate the probe as well as the code; a guard must be proved in both
directions; a repoint is not done until the response shape is checked; enumerating a removed symbol's
callers must include `src/test`.

## 8. Proving an auth fix on preview — the edge cannot discriminate

`experience-bff` runs with `IMPILO_SECURITY_ALLOW_ANONYMOUS=true` on preview (startup logs
*"JWT validation DISABLED … All endpoints are open."*). So the obvious negative control —
*call the BFF without a token, expect 401* — **returns 200 and proves nothing**. It tests the estate's
posture, not your fix, and yields a false green.

**Aim the negative control at something that actually refuses:**

| Target | Refuses an ordinary user token? | Use it for |
|---|---|---|
| BFF ingress | ❌ no (anonymous allowed on preview) | nothing — it cannot discriminate |
| Keycloak Admin API | ✅ 403 | proving admin-authority fixes |
| An enforcing downstream (e.g. ndila in-cluster) | ✅ 401 | proving a service token is really required |

**Assert the negative FIRST.** Without it the positive is vacuous — a 2xx that would have happened
anyway proves nothing. And prefer a positive that cannot be faked by a hollow 2xx: create an account,
then *log in as it independently*.

## 9. The gate does not compile the shell — so TypeScript errors live on canonical forever

`run-change-safety-gates.sh` does not run `next build` (or `tsc --noEmit`) over `ui/one-ui-shell`.
Combined with the fact that **nobody builds the shell unless they are deploying it**, a type error can
sit on canonical indefinitely.

Found 2026-07-27: canonical `one-ui-shell` would not build — a prop-type mismatch on
`ehr/[patientId]/programmes/page.tsx` had been on canonical for ~10 hours, blocking **every** lane's
ability to produce a shell image. It surfaced only because a lane tried to deploy one.

This is the same failure the estate met three times the same day, at three different layers:
- a test suite that did not compile reported **zero failures**
- a guard that never matched anything reported **green**
- a bundle nobody built reported **nothing at all**

**Before pushing anything under `ui/`:**
```bash
cd ui/one-ui-shell && npx tsc --noEmit     # fast; catches this class
# or the real thing, if you are about to deploy:
npm --prefix ui/one-ui-shell run build
```

**And add it to the gate.** Until then, treat "the shell builds" as an untested claim: a green
change-safety run says nothing about whether the estate can produce a shell image at all.
