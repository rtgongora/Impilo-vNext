#!/usr/bin/env bash
# Shared helpers for the vNext Change Absorption Pipeline.
# Intake decision authority — reuses VM gates as post-absorption verification primitives.
set -euo pipefail

DEFAULT_PRODUCT_TRUTH_BRANCH="${DEFAULT_PRODUCT_TRUTH_BRANCH:-claude/staging-ux-orchestration-remediation-Yypyl}"
DEFAULT_REPORT_DIR="${DEFAULT_REPORT_DIR:-reports/absorption}"
DEFAULT_REQUIRE_CLEAN_TREE="${DEFAULT_REQUIRE_CLEAN_TREE:-1}"
DEFAULT_ALLOW_FULL_MERGE="${DEFAULT_ALLOW_FULL_MERGE:-0}"

REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
ABSORPTION_MODE="${ABSORPTION_MODE:-}"
ABSORPTION_REPORT_DIR="${ABSORPTION_REPORT_DIR:-$REPO_PATH/$DEFAULT_REPORT_DIR}"
ABSORPTION_WORKTREE_DIR="${ABSORPTION_WORKTREE_DIR:-}"
ABSORPTION_BASE_REF="${ABSORPTION_BASE_REF:-}"
ABSORPTION_SOURCE_REF="${ABSORPTION_SOURCE_REF:-}"
ABSORPTION_SOURCE_GROUP="${ABSORPTION_SOURCE_GROUP:-}"
ABSORPTION_EXPECTED_VALUE="${ABSORPTION_EXPECTED_VALUE:-}"
ABSORPTION_REQUIRE_CLEAN_TREE="${ABSORPTION_REQUIRE_CLEAN_TREE:-$DEFAULT_REQUIRE_CLEAN_TREE}"
ABSORPTION_ALLOW_FULL_MERGE="${ABSORPTION_ALLOW_FULL_MERGE:-$DEFAULT_ALLOW_FULL_MERGE}"
ABSORPTION_RUN_GATES="${ABSORPTION_RUN_GATES:-0}"
ABSORPTION_DRY_RUN="${ABSORPTION_DRY_RUN:-0}"
ABSORPTION_ALLOW_DIRTY="${ABSORPTION_ALLOW_DIRTY:-0}"
ABSORPTION_APPROVAL_FILE="${ABSORPTION_APPROVAL_FILE:-}"
ABSORPTION_BASE_MODE="${ABSORPTION_BASE_MODE:-DEFAULT_PRODUCT_TRUTH_BRANCH}"
ABSORPTION_STARTED_AT="${ABSORPTION_STARTED_AT:-}"
ABSORPTION_INFERRED_PURPOSE="${ABSORPTION_INFERRED_PURPOSE:-}"

# Paths populated by absorption_init_context
ABSORPTION_BASE_COMMIT=""
ABSORPTION_SOURCE_COMMIT=""
ABSORPTION_MERGE_BASE=""

absorption_log() { echo "[absorption] $*"; }
absorption_info() { echo "INFO  $*"; }
absorption_warn() { echo "WARN  $*"; }
absorption_error() { echo "ERROR $*" >&2; }

absorption_die() {
  absorption_error "$1"
  exit "${2:-1}"
}

absorption_now() { date -u +%Y-%m-%dT%H:%M:%SZ; }

absorption_ensure_repo() {
  if [[ ! -d "$REPO_PATH/.git" ]]; then
    absorption_die "Not a git repository: $REPO_PATH"
  fi
  cd "$REPO_PATH"
}

absorption_ensure_report_dir() {
  mkdir -p "$ABSORPTION_REPORT_DIR"
}

absorption_report_path() {
  local name="$1"
  echo "$ABSORPTION_REPORT_DIR/$name"
}

absorption_json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$1"
}

absorption_ref_exists() {
  local ref="$1"
  git rev-parse --verify "${ref}^{commit}" >/dev/null 2>&1
}

absorption_resolve_ref() {
  local ref="$1"
  if absorption_ref_exists "$ref"; then
    git rev-parse "$ref"
    return 0
  fi
  if absorption_ref_exists "origin/$ref"; then
    git rev-parse "origin/$ref"
    return 0
  fi
  return 1
}

absorption_resolve_branch_name() {
  local ref="$1"
  if [[ "$ref" == origin/* ]]; then
    echo "$ref"
    return 0
  fi
  if absorption_ref_exists "origin/$ref"; then
    echo "origin/$ref"
    return 0
  fi
  if absorption_ref_exists "$ref"; then
    echo "$ref"
    return 0
  fi
  echo "$ref"
}

absorption_worktree_dirty() {
  [[ -n "$(git status --porcelain 2>/dev/null)" ]]
}

absorption_git_op_in_progress() {
  [[ -d .git/rebase-apply || -d .git/rebase-merge || -f .git/MERGE_HEAD || -f .git/CHERRY_PICK_HEAD ]]
}

absorption_check_branch_safety() {
  local label="${1:-Branch safety}"
  absorption_ensure_repo
  absorption_info "$label — repo: $(pwd)"
  absorption_info "$label — branch: $(git branch --show-current 2>/dev/null || echo detached)"
  absorption_info "$label — HEAD: $(git rev-parse --short HEAD 2>/dev/null || echo unknown)"

  if absorption_git_op_in_progress; then
    absorption_die "$label FAILED: merge, rebase, or cherry-pick in progress"
  fi

  if absorption_worktree_dirty; then
    if [[ "$ABSORPTION_ALLOW_DIRTY" == "1" || "$ABSORPTION_REQUIRE_CLEAN_TREE" == "0" ]]; then
      absorption_warn "$label — working tree has uncommitted changes (allowed by flag)"
    else
      absorption_die "$label FAILED: working tree is dirty. Commit/stash or pass --allow-dirty"
    fi
  else
    absorption_info "$label — working tree clean"
  fi
}

absorption_safe_fetch() {
  absorption_ensure_repo
  if [[ "$ABSORPTION_DRY_RUN" == "1" ]]; then
    absorption_info "DRY RUN — skipping git fetch"
    return 0
  fi
  if git remote get-url origin >/dev/null 2>&1; then
    git fetch --prune origin 2>/dev/null || absorption_warn "git fetch failed (continuing with local refs)"
  fi
}

absorption_resolve_context() {
  local base_input="${1:-$DEFAULT_PRODUCT_TRUTH_BRANCH}"
  local source_input="${2:?source ref required}"

  absorption_ensure_repo
  absorption_safe_fetch

  ABSORPTION_BASE_REF="$(absorption_resolve_branch_name "$base_input")"
  if ! ABSORPTION_BASE_COMMIT="$(absorption_resolve_ref "$ABSORPTION_BASE_REF")"; then
    absorption_die "Base ref not found: $base_input"
  fi

  ABSORPTION_SOURCE_REF="$(absorption_resolve_branch_name "$source_input")"
  if ! ABSORPTION_SOURCE_COMMIT="$(absorption_resolve_ref "$ABSORPTION_SOURCE_REF")"; then
    absorption_die "Source ref not found: $source_input"
  fi

  if [[ "$ABSORPTION_BASE_REF" == "$DEFAULT_PRODUCT_TRUTH_BRANCH" || "$base_input" == "$DEFAULT_PRODUCT_TRUTH_BRANCH" ]]; then
    if [[ "$base_input" == "$DEFAULT_PRODUCT_TRUTH_BRANCH" ]]; then
      ABSORPTION_BASE_MODE="DEFAULT_PRODUCT_TRUTH_BRANCH"
    else
      ABSORPTION_BASE_MODE="OVERRIDDEN_BASE_BRANCH"
    fi
  else
    ABSORPTION_BASE_MODE="OVERRIDDEN_BASE_BRANCH"
  fi

  if [[ "$base_input" == "$DEFAULT_PRODUCT_TRUTH_BRANCH" && "$ABSORPTION_BASE_REF" != "$base_input" ]]; then
    : # resolved to origin/... still default if input was default branch name
    if [[ "$base_input" == "$DEFAULT_PRODUCT_TRUTH_BRANCH" ]]; then
      ABSORPTION_BASE_MODE="DEFAULT_PRODUCT_TRUTH_BRANCH"
    fi
  fi

  ABSORPTION_MERGE_BASE="$(git merge-base "$ABSORPTION_BASE_COMMIT" "$ABSORPTION_SOURCE_COMMIT" 2>/dev/null || true)"
  [[ -n "$ABSORPTION_MERGE_BASE" ]] || absorption_die "No merge-base between base and source"

  absorption_info "Product Truth (base): $ABSORPTION_BASE_REF @ ${ABSORPTION_BASE_COMMIT:0:12}"
  absorption_info "Source branch: $ABSORPTION_SOURCE_REF @ ${ABSORPTION_SOURCE_COMMIT:0:12}"
  absorption_info "Merge-base: ${ABSORPTION_MERGE_BASE:0:12}"
  absorption_info "Base mode: $ABSORPTION_BASE_MODE"
}

absorption_changed_files() {
  git diff --name-only "$ABSORPTION_BASE_COMMIT...$ABSORPTION_SOURCE_COMMIT" 2>/dev/null || true
}

absorption_unique_commits() {
  git log --oneline "$ABSORPTION_BASE_COMMIT..$ABSORPTION_SOURCE_COMMIT" 2>/dev/null || true
}

absorption_deleted_files() {
  git diff --diff-filter=D --name-only "$ABSORPTION_BASE_COMMIT...$ABSORPTION_SOURCE_COMMIT" 2>/dev/null || true
}

absorption_renamed_files() {
  git diff --diff-filter=R --name-status "$ABSORPTION_BASE_COMMIT...$ABSORPTION_SOURCE_COMMIT" 2>/dev/null || true
}

absorption_added_files() {
  git diff --diff-filter=A --name-only "$ABSORPTION_BASE_COMMIT...$ABSORPTION_SOURCE_COMMIT" 2>/dev/null || true
}

absorption_diff_stat() {
  git diff --stat "$ABSORPTION_BASE_COMMIT...$ABSORPTION_SOURCE_COMMIT" 2>/dev/null || true
}

# Hard-block deploy/cluster mutation commands during analysis (and as guard for all modes).
absorption_block_destructive_command() {
  local cmd="$*"
  local block_re='(^|[[:space:]/])(git[[:space:]]+(merge|rebase|cherry-pick|commit|push)|helm[[:space:]]+upgrade|kubectl[[:space:]]+(apply|delete|rollout)|scripts/deploy/|k3s-import|resolve-image-digests|full-boot-preview-deploy|manual-authorized-preview-deploy|preview-deploy\.sh|wave-gates\.sh|import.*k3s-images)'
  if echo "$cmd" | grep -qiE "$block_re"; then
    absorption_die "Blocked command during absorption intake: $cmd"
  fi
}

absorption_run_safe() {
  absorption_block_destructive_command "$*"
  "$@"
}

absorption_worktree_create() {
  local wt_dir="$1"
  local ref="$2"
  if [[ -d "$wt_dir/.git" || -f "$wt_dir/.git" ]]; then
    absorption_info "Worktree already exists: $wt_dir"
    return 0
  fi
  if [[ "$ABSORPTION_DRY_RUN" == "1" ]]; then
    absorption_info "DRY RUN — would create worktree at $wt_dir for $ref"
    return 0
  fi
  mkdir -p "$(dirname "$wt_dir")"
  git worktree add --detach "$wt_dir" "$ref" >/dev/null
  absorption_info "Created worktree: $wt_dir @ $ref"
}

absorption_worktree_remove() {
  local wt_dir="$1"
  [[ -z "$wt_dir" || ! -e "$wt_dir" ]] && return 0
  if [[ "$ABSORPTION_DRY_RUN" == "1" ]]; then
    return 0
  fi
  git worktree remove --force "$wt_dir" 2>/dev/null || rm -rf "$wt_dir"
  git worktree prune 2>/dev/null || true
}

absorption_cleanup_trap() {
  local wt="$ABSORPTION_WORKTREE_DIR"
  if [[ -n "$wt" && "${ABSORPTION_KEEP_WORKTREE:-0}" != "1" ]]; then
    absorption_worktree_remove "$wt"
  fi
}

absorption_write_json_metadata() {
  local out="$1"
  local mode="$2"
  local verdict="$3"
  local extra_py="${4:-}"

  export ABS_JSON_OUT="$out" ABS_JSON_MODE="$mode" ABS_JSON_VERDICT="$verdict"
  export ABS_JSON_BASE="$ABSORPTION_BASE_REF" ABS_JSON_SOURCE="$ABSORPTION_SOURCE_REF"
  export ABS_JSON_BASE_MODE="$ABSORPTION_BASE_MODE"
  export ABS_JSON_GROUP="$ABSORPTION_SOURCE_GROUP" ABS_JSON_EXPECTED="$ABSORPTION_EXPECTED_VALUE"
  export ABS_JSON_INFERRED="$ABSORPTION_INFERRED_PURPOSE"
  export ABS_JSON_STARTED="${ABSORPTION_STARTED_AT:-$(absorption_now)}"
  export ABS_JSON_FINISHED="$(absorption_now)"

  python3 <<'PY'
import json, os, pathlib

extra = {}
extra_py = os.environ.get("ABS_JSON_EXTRA", "")
if extra_py.strip():
    exec(extra_py, {}, extra)

doc = {
    "mode": os.environ["ABS_JSON_MODE"],
    "product_truth_branch": os.environ["ABS_JSON_BASE"],
    "source_branch": os.environ["ABS_JSON_SOURCE"],
    "base_mode": os.environ["ABS_JSON_BASE_MODE"],
    "source_group": os.environ.get("ABS_JSON_GROUP") or None,
    "expected_value": os.environ.get("ABS_JSON_EXPECTED") or None,
    "inferred_source_purpose": os.environ.get("ABS_JSON_INFERRED") or None,
    "started_at": os.environ["ABS_JSON_STARTED"],
    "finished_at": os.environ["ABS_JSON_FINISHED"],
    "verdict": os.environ["ABS_JSON_VERDICT"],
    "checks_run": extra.get("checks_run", []),
    "decisions": extra.get("decisions", []),
    "conflicts_forecast": extra.get("conflicts_forecast", []),
    "verification": extra.get("verification", {}),
    "product_owner_decisions": extra.get("product_owner_decisions", []),
    "final_recommendation": extra.get("final_recommendation", os.environ["ABS_JSON_VERDICT"]),
}
for k, v in extra.items():
    if k not in doc:
        doc[k] = v

pathlib.Path(os.environ["ABS_JSON_OUT"]).write_text(json.dumps(doc, indent=2) + "\n")
PY
}

absorption_print_next_steps() {
  local mode="$1"
  echo ""
  echo "── Next commands ──"
  case "$mode" in
    analysis)
      echo "  Review: $(absorption_report_path latest-analysis.md)"
      echo "  Approve items in: $(absorption_report_path approved-plan.json) (copy from approved-plan.template.json)"
      echo "  Absorb:  bash scripts/absorption/run-change-absorption.sh absorb --source $ABSORPTION_SOURCE_REF --approval-file reports/absorption/approved-plan.json"
      echo "  Verify:  bash scripts/absorption/run-change-absorption.sh verify --source $ABSORPTION_SOURCE_REF --run-gates"
      echo "  Report:  bash scripts/absorption/run-change-absorption.sh report --source $ABSORPTION_SOURCE_REF"
      ;;
    absorb)
      echo "  Review: $(absorption_report_path latest-absorption.md)"
      echo "  Verify: bash scripts/absorption/run-change-absorption.sh verify --source $ABSORPTION_SOURCE_REF --run-gates"
      echo "  Report: bash scripts/absorption/run-change-absorption.sh report --source $ABSORPTION_SOURCE_REF"
      echo "  Commit manually when ready (pipeline does not auto-commit)"
      ;;
    verify)
      echo "  Review: $(absorption_report_path latest-verification.md)"
      echo "  Full VM gates: bash scripts/pipeline/run-local-quality-gates.sh"
      echo "  Report: bash scripts/absorption/run-change-absorption.sh report --source $ABSORPTION_SOURCE_REF"
      ;;
    report)
      echo "  Final report: $(absorption_report_path latest-intake-report.md)"
      ;;
  esac
}
