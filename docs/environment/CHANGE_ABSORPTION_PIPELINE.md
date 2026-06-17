# Change Absorption Pipeline

The Change Absorption Pipeline is a **separate branch-intake workflow**. It is not a replacement for CI/CD. It is not deployment logic. It is a controlled source-branch evaluation and selective absorption process.

## Purpose

Answer: **What, if anything, should we absorb from this selected source branch into the Product Truth branch?**

CI/CD and VM gates answer: **Is the current codebase safe to build, test, deploy, and promote?**

| System | Role |
|--------|------|
| **Change Absorption Pipeline** | Intake **decision** authority |
| **`scripts/pipeline/run-local-quality-gates.sh`** | Post-absorption **quality** authority |

## Fixed Product Truth Branch

Default base (not variable during normal sessions):

```text
claude/staging-ux-orchestration-remediation-Yypyl
```

GitHub: https://github.com/rtgongora/Impilo-vNext/tree/claude/staging-ux-orchestration-remediation-Yypyl

The **source branch varies** per session. One source branch per session — no branch inventory unless explicitly requested.

## Operator rhythm

1. Operator gives selected source branch (optional source group + expected-value brief).
2. Pipeline reviews source vs Product Truth (analysis mode — read-only).
3. Pipeline produces assessment and decision table.
4. Operator approves specific items (`approved-plan.json`).
5. Pipeline absorbs approved items only (absorb mode).
6. Pipeline runs verification (verify mode; optional `--run-gates`).
7. Pipeline produces final intake report (report mode).
8. Operator runs full VM gates before commit/deploy: `bash scripts/pipeline/run-local-quality-gates.sh`.

## Pipeline modes

| Mode | Script | Mutates tree? |
|------|--------|---------------|
| `analysis` | `analyze-branch-intake.sh` | **No** |
| `absorb` | `absorb-approved-changes.sh` | Yes (approved files only) |
| `verify` | `verify-intake.sh` | No (gates may build/test) |
| `report` | `report-intake.sh` | No |

Dispatcher: `bash scripts/absorption/run-change-absorption.sh <mode> [options]`

## Command examples

### Default analysis

```bash
bash scripts/absorption/run-change-absorption.sh analysis \
  --source origin/<selected-source-branch>
```

### Guided analysis

```bash
bash scripts/absorption/run-change-absorption.sh analysis \
  --source origin/<selected-source-branch> \
  --source-group FRONTEND_SURFACING \
  --expected-value "This branch mainly worked on surfacing backend functionality on the frontend."
```

### Base override (exceptional)

```bash
bash scripts/absorption/run-change-absorption.sh analysis \
  --base <override-base-branch> \
  --source origin/<selected-source-branch>
```

Reports always show `DEFAULT_PRODUCT_TRUTH_BRANCH` vs `OVERRIDDEN_BASE_BRANCH`.

### Absorb / verify / report

```bash
bash scripts/absorption/run-change-absorption.sh absorb \
  --source origin/<selected-source-branch> \
  --approval-file reports/absorption/approved-plan.json

bash scripts/absorption/run-change-absorption.sh verify \
  --source origin/<selected-source-branch> \
  --run-gates

bash scripts/absorption/run-change-absorption.sh report \
  --source origin/<selected-source-branch>
```

### Dirty tree (discouraged)

```bash
bash scripts/absorption/run-change-absorption.sh analysis \
  --source origin/<branch> --allow-dirty
```

Default: `ABSORPTION_REQUIRE_CLEAN_TREE=1` — refuse analysis/absorb on dirty tree.

## Branch purpose inference

If `--expected-value` is omitted, analysis infers purpose from:

- Branch name, commits, messages
- Changed/deleted/renamed paths
- Services, routes, mobile, contracts, tests, docs, config

Inferred purpose is marked **inferred** in reports. Operator-provided expected value is verified against the actual diff.

## Analysis gates (read-only)

1. **Branch safety** — clean tree, refs exist, no merge/rebase/cherry-pick in progress
2. **Difference discovery** — commits, files, directories, risk estimate
3. **Product truth alignment** — canonical services, registry, trust/login doctrine, parity
4. **Real wiring** — no fake flows, dead handlers, mock completion
5. **Regression risk** — deletions, route loss, test/guard weakening
6. **Duplicate implementation** — parallel screens/clients/workflows
7. **UX / Lovable fidelity** — strengthens wired product only
8. **Mobile parity** — citizen/provider, BFF wiring, no separate product reality
9. **Conflict forecast** — `git merge-tree` (no mechanical resolution)
10. **Absorption decision table** — per-path classification and branch verdict

## Approval file format

Copy `reports/absorption/approved-plan.template.json`. Required keys:

- `product_truth_branch`, `source_branch`, `approved_items`
- `allow_full_merge` must be `false` unless `ABSORPTION_ALLOW_FULL_MERGE=1`

Supported actions: `RESTORE_FILE_FROM_SOURCE`, `CHERRY_PICK_NO_COMMIT`, `APPLY_PATCH_FILE`, `MANUAL_ADAPTATION_MARKER`, `ACCEPT_SELECTED_HUNKS_MANUAL`, `DOCUMENT_ONLY`, `SKIP`.

## Report outputs

Generated under `reports/absorption/` (gitignored except templates):

- `latest-analysis.{md,json}`
- `latest-absorption.{md,json}`
- `latest-verification.{md,json}`
- `latest-intake-report.{md,json}`
- `touched-areas.json`

## Reuse of existing VM gates

Verification mode maps touched areas to:

- `scripts/test/run-static-checks.sh`
- `scripts/test/run-frontend-checks.sh`
- `scripts/test/run-backend-checks.sh`
- `scripts/guard/run-change-safety-gates.sh`
- `scripts/guard/check-backend-frontend-parity.sh`
- `scripts/guard/check-mobile-parity.sh`
- `scripts/test/run-api-contract-checks.sh`
- `scripts/pipeline/run-local-quality-gates.sh` with `PIPELINE_ONLY` from `touched-areas.json`

## Failure classification (verify mode)

| Class | Meaning |
|-------|---------|
| `CAUSED_BY_INTAKE` | Likely introduced by absorbed changes |
| `PRE_EXISTING` | Present before intake (requires baseline compare) |
| `UNKNOWN` | Preview/infra dependency or inconclusive |

## Safety rules — never run during intake analysis

- Deploy: `manual-authorized-preview-deploy.sh`, `full-boot-preview-deploy.sh`, `preview-deploy.sh`
- Cluster: `kubectl apply/delete`, `helm upgrade`, wave promotion
- Images: k3s import, `resolve-image-digests.sh`
- Git: merge, rebase, cherry-pick (except controlled absorb actions), commit, push

## Full-merge denial policy

Default: **selective absorption only**. Full branch merge requires `ABSORPTION_ALLOW_FULL_MERGE=1` and explicit operator authorization (not enabled by default).

## Worktree isolation policy

Analysis uses read-only `git diff` / `merge-tree`. Absorb applies approved paths to the working tree. Prefer a clean Product Truth checkout; use `--allow-dirty` only when unavoidable.

## Doctrine (summary)

- Source branch is **never blindly merged**
- Real wiring beats visual-only UI
- No fake flows, dead buttons, mock-driven completion
- No duplicate service realities
- No deleting current functionality without explicit approval
- Generated artifacts must not be blindly absorbed
- Existing VM gates remain post-absorption quality authority

## Examples by branch type

| Source type | Focus in analysis |
|-------------|-------------------|
| Frontend / Lovable UX | Gates 4, 7, real wiring, `test:no-stubs`, launchers |
| Mobile | Gate 8, mobile parity guards |
| BFF / backend surfacing | Gates 3–5, API contracts, parity matrix |
| QA / regression fix | Gate 5, regression + verify with targeted backend/frontend gates |

## Related docs

- [DUAL_MODE_TEST_PIPELINE.md](./DUAL_MODE_TEST_PIPELINE.md)
- [PREVIEW_TEST_GATES.md](./PREVIEW_TEST_GATES.md)
- [GAP_CLOSURE_RULES.md](../frontend/GAP_CLOSURE_RULES.md)
