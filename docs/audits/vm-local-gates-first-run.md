# VM Local Gates — the first execution, recorded

`vm-local-gates.yml` was added as the canonical fallback when GitHub-hosted runners went
billing-locked. Until 2026-08-08 **it had never executed once**: `gh api
repos/rtgongora/Impilo-vNext/actions/runners` returned `total_count: 0`, and 8 runs sat queued, the
oldest since 2026-08-07 14:44.

A self-hosted runner (`impilo-vm-runner`, labels `self-hosted,Linux,X64,impilo-vm`) was registered
on the preview VM. The 7 superseded queued runs were cancelled and the main tip was allowed to run.

## The run

| | |
|---|---|
| Run | `31245295836` |
| Commit | `b1845e1be` (main) |
| Started / ended | 2026-08-08 08:55 → 09:38 UTC (**43 min**) |
| Verdict | **FAIL**, process exit **128** |
| Gates | **25 passed, 9 failed, 0 advisory** |
| Summary artifact | **not produced** — see defect 3 |

**Failed (9):** Static checks · Backend-to-frontend parity · Mobile parity · Preview sandbox runtime
smoke · Preview persistence E2E · Change-safety gates · Mobile build checks · `frontend (no result
recorded)` · `backend (no result recorded)`

Red was the expected and correct outcome. **It was not fixed by weakening any gate.** What the run
established is that most of the red was the lane misreading itself.

## What the run proved

### 1. Seven of the nine failures were the lane, not the code

`actions/checkout` defaults to a **depth-1 shallow clone**. Verified directly in the runner
workspace: `git rev-list --count HEAD` = 1, `.git/shallow` present, `HEAD^1` absent. Every guard
resolves its review window through `resolve_base_ref`, which then reports, verbatim:

```
base-ref rule: HEAD has no parent, base = empty tree (whole tree is new)
```

So every diff-scoped guard reviewed **the entire repository as newly added**. All five change-safety
violations, plus `deprecated-surface-guard`, backend-frontend parity and mobile parity, fired on
files added months earlier — `account-deletion/page.tsx` on **2026-04-26**, `GetAppSurface.tsx` on
**2026-07-17**. Nothing was new.

Fixed with `fetch-depth: 0`.

### 2. The concurrent branch never recorded its gates

The summary contains a flat contradiction: **"Frontend checks" and "Backend checks" appear in
Passed(25)**, while **`frontend (no result recorded)` and `backend (no result recorded)` appear in
Failed(9)**.

The concurrent frontend+backend branch — the default path — calls `pipeline_phase_pass` but never
`_pipeline_record_gate`. At `b1845e1be` that block contained **0** record calls, so both mandatory
gates produced no record on every concurrent run. Independently found and fixed before this run
finished.

### 3. The run destroyed the one artifact that records what it did

Exit **128**, not 1 — raised *after* every gate finished and *before* any report was written.

`CHANGED="$(git diff --name-only HEAD~1...HEAD 2>/dev/null | wc -l)"` let git's 128 through
`pipefail`, and `set -e` — which `run-local-quality-gates.sh` deliberately does **not** set, but
inherits because sourcing `_pipeline-common.sh` leaks `set -euo pipefail` into the parent shell —
turned it into an exit. `pipeline_write_reports` never ran, `latest-summary.{json,md}` never
existed, and the upload step's `if-no-files-found: ignore` rendered that silence as a **green**
"Upload pipeline summary" step.

Reproduced deliberately in the runner's own workspace: the script prints `before`, never `after`,
and exits 128.

### 4. The governance pack was skipped while reporting as out-of-scope

Recorded `NOT_APPLICABLE` with the reason "no governance paths changed" — having never been
consulted. Its path selection asks `git diff HEAD~1...HEAD 2>/dev/null`, which on a shallow clone
errors, prints nothing and matches nothing. A 719-line verifier with 38 assertions was skipped by a
suppressed error.

⚠️ The `governance.log` and `governance-cwd.log` files present in `/tmp/impilo-pipeline-gates`
during this run were **20 hours stale and belonged to another session** — that directory is shared
between CI and every developer session on this VM. Reading them would have produced the opposite
conclusion. CI gate logs now live in the job workspace.

### 5. The product-truth defect reproduced in CI, independently

The gate ran and reported **`Services: 104 | Gaps: 0` … GUARD PASS**, and sits in the Passed(25)
list. This is the same result measured locally, produced by real CI against the real main tip —
confirmation that the `Gaps: 0` was the gate's own four dead detectors, not a local artifact. See
[`product-truth-gate-coverage.md`](product-truth-gate-coverage.md).

## Genuinely open, not lane artifacts

- **`contract implementation violations=76` (threshold 0)** — a whole-repo count, unaffected by base-ref
  resolution. Real standing debt.
- **Preview sandbox runtime smoke / persistence E2E** — need a reachable preview estate; not yet
  triaged.
- **Mobile build checks** — not yet triaged.

## Still to do

1. **The runner is not durable.** There is no passwordless sudo on this box, so `svc.sh install`
   was unavailable. It runs under `setsid nohup ./run.sh` and will **not survive a reboot**.
   Installing it as a systemd service needs a sudo password.
2. **`fetch-depth: 0` is committed but unpushed.** The `gh` OAuth token carries `gist, read:org,
   repo` and lacks the `workflow` scope, so `.github/workflows/**` cannot be updated from here.
   Needs `gh auth refresh -h github.com -s workflow`.
3. **Do not enable a required-checks ruleset yet.** Until the lane is green, it would make `main`
   permanently unmergeable.
