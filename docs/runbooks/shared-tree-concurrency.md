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
mvn -pl services/<svc> -am test        # PREFERRED for verification
```

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
- A migration is **landed** when `flyway_schema_history` says so **on the target, in the right
  schema** (`<service>.flyway_schema_history`, not `public` — an unqualified query reads exactly like
  a missing table). It is **correct** when a constraint it declared can be shown to bite there.

---

Related memory-level laws: mutate the probe as well as the code; a guard must be proved in both
directions; a repoint is not done until the response shape is checked; enumerating a removed symbol's
callers must include `src/test`.
