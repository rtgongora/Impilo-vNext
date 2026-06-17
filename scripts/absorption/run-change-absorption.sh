#!/usr/bin/env bash
# vNext Change Absorption Pipeline — main dispatcher.
# Intake decision authority; VM gates remain post-absorption quality authority.
set -euo pipefail

REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/absorption/_absorption-common.sh
source "$SCRIPT_DIR/_absorption-common.sh"

usage() {
  cat <<'EOF'
Impilo vNext Change Absorption Pipeline

Usage:
  bash scripts/absorption/run-change-absorption.sh <mode> [options]

Modes:
  analysis   Read-only review of source branch vs Product Truth branch
  absorb     Apply approved items only (requires --approval-file)
  verify     Map touched areas to VM gates; optional --run-gates
  report     Consolidate analysis/absorption/verification reports

Options:
  --base REF              Product Truth branch (default: claude/staging-ux-orchestration-remediation-Yypyl)
  --source REF            Selected source branch (required for all modes)
  --source-group GROUP      Optional operator grouping label
  --expected-value TEXT     Optional operator intent brief
  --approval-file PATH      Required for absorb mode
  --report-dir PATH         Report output directory (default: reports/absorption)
  --worktree-dir PATH       Optional isolated worktree path
  --allow-dirty             Allow dirty working tree (default: refuse)
  --dry-run                 Print actions without mutating worktree/files
  --run-gates               Run recommended VM gates (verify mode)
  --help                    Show this help

Environment:
  ABSORPTION_BASE_REF, ABSORPTION_SOURCE_REF, ABSORPTION_SOURCE_GROUP,
  ABSORPTION_EXPECTED_VALUE, ABSORPTION_REPORT_DIR, ABSORPTION_WORKTREE_DIR,
  ABSORPTION_REQUIRE_CLEAN_TREE=1, ABSORPTION_ALLOW_FULL_MERGE=0,
  ABSORPTION_RUN_GATES=0, ABSORPTION_DRY_RUN=1

Examples:
  bash scripts/absorption/run-change-absorption.sh analysis --source origin/feature/foo
  bash scripts/absorption/run-change-absorption.sh analysis \
    --source origin/feature/foo --source-group FRONTEND_SURFACING \
    --expected-value "Surfacing backend capabilities on the frontend"
  bash scripts/absorption/run-change-absorption.sh absorb \
    --source origin/feature/foo --approval-file reports/absorption/approved-plan.json
  bash scripts/absorption/run-change-absorption.sh verify --source origin/feature/foo --run-gates
  bash scripts/absorption/run-change-absorption.sh report --source origin/feature/foo

Product Truth branch (fixed default):
  claude/staging-ux-orchestration-remediation-Yypyl

Doctrine:
  docs/environment/CHANGE_ABSORPTION_PIPELINE.md
EOF
}

MODE=""
CLI_BASE=""
CLI_SOURCE=""
CLI_SOURCE_GROUP=""
CLI_EXPECTED=""
CLI_APPROVAL=""
CLI_REPORT_DIR=""
CLI_WORKTREE=""
CLI_ALLOW_DIRTY=0
CLI_DRY_RUN=0
CLI_RUN_GATES=0

parse_common_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --base) CLI_BASE="${2:-}"; shift 2 ;;
      --source) CLI_SOURCE="${2:-}"; shift 2 ;;
      --source-group) CLI_SOURCE_GROUP="${2:-}"; shift 2 ;;
      --expected-value) CLI_EXPECTED="${2:-}"; shift 2 ;;
      --approval-file) CLI_APPROVAL="${2:-}"; shift 2 ;;
      --report-dir) CLI_REPORT_DIR="${2:-}"; shift 2 ;;
      --worktree-dir) CLI_WORKTREE="${2:-}"; shift 2 ;;
      --allow-dirty) CLI_ALLOW_DIRTY=1; shift ;;
      --dry-run) CLI_DRY_RUN=1; shift ;;
      --run-gates) CLI_RUN_GATES=1; shift ;;
      --help|-h) usage; exit 0 ;;
      *)
        absorption_die "Unknown argument: $1 (try --help)"
        ;;
    esac
  done
}

apply_cli_to_env() {
  [[ -n "$CLI_BASE" ]] && ABSORPTION_BASE_REF="$CLI_BASE"
  [[ -n "$CLI_SOURCE" ]] && ABSORPTION_SOURCE_REF="$CLI_SOURCE"
  [[ -n "$CLI_SOURCE_GROUP" ]] && ABSORPTION_SOURCE_GROUP="$CLI_SOURCE_GROUP"
  [[ -n "$CLI_EXPECTED" ]] && ABSORPTION_EXPECTED_VALUE="$CLI_EXPECTED"
  [[ -n "$CLI_APPROVAL" ]] && ABSORPTION_APPROVAL_FILE="$CLI_APPROVAL"
  [[ -n "$CLI_REPORT_DIR" ]] && ABSORPTION_REPORT_DIR="$REPO_PATH/$CLI_REPORT_DIR"
  [[ -n "$CLI_WORKTREE" ]] && ABSORPTION_WORKTREE_DIR="$CLI_WORKTREE"
  [[ "$CLI_ALLOW_DIRTY" == "1" ]] && ABSORPTION_ALLOW_DIRTY=1
  [[ "$CLI_DRY_RUN" == "1" ]] && ABSORPTION_DRY_RUN=1
  [[ "$CLI_RUN_GATES" == "1" ]] && ABSORPTION_RUN_GATES=1
  ABSORPTION_MODE="$MODE"
  ABSORPTION_STARTED_AT="$(absorption_now)"
}

main() {
  if [[ $# -lt 1 ]]; then
    usage
    exit 2
  fi

  case "$1" in
    analysis|absorb|verify|report) MODE="$1"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) absorption_die "First argument must be mode: analysis|absorb|verify|report" ;;
  esac

  parse_common_args "$@"

  [[ -n "$CLI_SOURCE" || -n "${ABSORPTION_SOURCE_REF:-}" ]] || absorption_die "--source is required"

  apply_cli_to_env
  absorption_ensure_report_dir

  export REPO_PATH ABSORPTION_MODE ABSORPTION_REPORT_DIR ABSORPTION_WORKTREE_DIR
  export ABSORPTION_BASE_REF ABSORPTION_SOURCE_REF ABSORPTION_SOURCE_GROUP ABSORPTION_EXPECTED_VALUE
  export ABSORPTION_REQUIRE_CLEAN_TREE ABSORPTION_ALLOW_FULL_MERGE ABSORPTION_RUN_GATES ABSORPTION_DRY_RUN
  export ABSORPTION_ALLOW_DIRTY ABSORPTION_APPROVAL_FILE ABSORPTION_STARTED_AT

  local base_arg="${CLI_BASE:-$DEFAULT_PRODUCT_TRUTH_BRANCH}"
  local source_arg="${CLI_SOURCE:-$ABSORPTION_SOURCE_REF}"

  case "$MODE" in
    analysis) bash "$SCRIPT_DIR/analyze-branch-intake.sh" "$base_arg" "$source_arg" ;;
    absorb)   bash "$SCRIPT_DIR/absorb-approved-changes.sh" "$base_arg" "$source_arg" ;;
    verify)   bash "$SCRIPT_DIR/verify-intake.sh" "$base_arg" "$source_arg" ;;
    report)   bash "$SCRIPT_DIR/report-intake.sh" "$base_arg" "$source_arg" ;;
  esac
}

main "$@"
