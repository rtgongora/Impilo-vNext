# Change Absorption Pipeline

The Change Absorption Pipeline is a **separate branch-intake workflow**. It is not a replacement for CI/CD. It is not deployment logic. It is a controlled source-branch evaluation and selective absorption process.

## Purpose

Answer three questions for every source-branch intake:

1. **Can this code merge?** — technical classification, conflict forecast, wiring heuristics.
2. **Is this change actually good for the product?** — Product Value / Improvement Gate.
3. **What extra Product Truth work is needed for accepted changes to work?** — Accepted Change Completion / Enablement Gate.

CI/CD and VM gates answer: **Is the current codebase safe to build, test, deploy, and promote?**

| System | Role |
|--------|------|
| **Change Absorption Pipeline** | Intake **decision** authority — product value + completion enablement |
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
3. Pipeline produces **plain-language product-owner summary** plus technical assessment and decision tables.
4. Operator approves specific items (`approved-plan.json` with product value + completion metadata).
5. Pipeline absorbs approved items only (absorb mode — warns if completion metadata missing; does not block on browser QA).
6. Pipeline runs verification (verify mode; optional `--run-gates`).
7. Pipeline produces final intake report with completion tracking table (report mode).
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
10. **Product Value / Improvement Gate** — plain-language “is this good?” per bucket
11. **Accepted Change Completion / Enablement Gate** — what Product Truth still needs after accept
12. **Absorption decision table** — per-path technical classification and branch verdict

### Product Value / Improvement Gate

For every meaningful bucket, analysis answers in plain language:

- Is this an improvement over Product Truth?
- Can it be accepted as-is?
- If valuable but not acceptable as-is, what modifications are needed?
- If not beneficial: regression, duplication, stale implementation, or risky platform change?

**Value classifications:** `IMPROVEMENT_ACCEPT`, `IMPROVEMENT_BUT_NEEDS_MODIFICATION`, `USEFUL_IDEA_BAD_IMPLEMENTATION`, `NO_CLEAR_VALUE`, `REGRESSION_REJECT`, `DUPLICATE_REJECT`, `RISKY_DEFER`, `NEEDS_PRODUCT_OWNER_DECISION`.

Each bucket includes: **Product-owner meaning:** one or two sentences explaining whether the change is good, bad, risky, or incomplete.

Analysis reports include:

| Area | Change | Product value judgement | Acceptability | Modification needed | Completion needs | Risk if ignored | Recommendation |

### Accepted Change Completion / Enablement Gate

For items classified `ACCEPT_DIRECT`, `ACCEPT_SELECTED_HUNKS`, `ACCEPT_WITH_ADAPTATION`, `CHERRY_PICK_COMMIT`, `REIMPLEMENT_IDEA`, or `REGRESSION_RESTORATION`, analysis and approved-plan metadata track missing:

- frontend route / navigation entry
- backend endpoint / BFF proxy / hook / client
- migration / seed data
- role / permission / trust context
- registry / contract / tests / mobile parity
- browser QA / preview smoke / deploy config / documentation

**Completion classifications:** `COMPLETE_AS_ABSORBED`, `NEEDS_UI_WIRING`, `NEEDS_BACKEND_WIRING`, `NEEDS_BFF_WIRING`, `NEEDS_MIGRATION_OR_SEED_DATA`, `NEEDS_ROLE_POLICY_OR_TRUST_CONTEXT`, `NEEDS_ROUTE_OR_NAVIGATION`, `NEEDS_TESTS`, `NEEDS_BROWSER_QA`, `NEEDS_MOBILE_PARITY`, `NEEDS_CONFIG_OR_DEPLOYMENT_REVIEW`, `NEEDS_SEPARATE_INTAKE`, `INCOMPLETE_DO_NOT_ABSORB_YET`.

Final intake reports include:

| Accepted change | Absorbed? | Works end-to-end? | Additional Product Truth work needed | Owner | Status |

**Absorb mode:** missing `completion_needs` in `approved-plan.json` produces warnings and placeholders — absorb is **not** blocked solely because browser QA is listed.

## Approval file format

Copy `reports/absorption/approved-plan.template.json`. Required keys:

- `product_truth_branch`, `source_branch`, `approved_items`
- `allow_full_merge` must be `false` unless `ABSORPTION_ALLOW_FULL_MERGE=1`

Recommended per-item fields (product-owner + enablement metadata):

- `product_value_judgement`, `acceptability`, `modification_required`
- `completion_needs` (array), `risk_if_ignored`, `product_owner_notes`

If `completion_needs` is omitted, absorb mode warns and records placeholders — it does not block absorb.

Supported actions: `RESTORE_FILE_FROM_SOURCE`, `CHERRY_PICK_NO_COMMIT`, `APPLY_PATCH_FILE`, `MANUAL_ADAPTATION_MARKER`, `ACCEPT_SELECTED_HUNKS_MANUAL`, `DOCUMENT_ONLY`, `SKIP`.

## Report outputs

Generated under `reports/absorption/` (gitignored except templates):

- `latest-analysis.{md,json}` — includes Product Owner Decision Summary + value/completion tables
- `latest-absorption.{md,json}` — includes completion tracking + metadata warnings
- `latest-verification.{md,json}` — includes completion gate verification view
- `latest-intake-report.{md,json}` — executive summary + completion tracking table
- `touched-areas.json`

Every analysis and final report executive summary answers:

1. Is this branch valuable?
2. What is the main improvement?
3. What should be absorbed?
4. What must be modified before acceptance?
5. What should be rejected?
6. What should become a separate intake?
7. What additional Product Truth work is needed after absorption?
8. Final product-owner recommendation.

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
