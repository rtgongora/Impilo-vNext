#!/usr/bin/env bash
# Full boot / full estate validation — quality gates + full-boot-preview-deploy + audit report.
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
source scripts/preview/_preview-common.sh
source scripts/deploy/_preview-deploy-metadata.sh

FORCE_GATES=0
DRY_RUN=0
AUTH_PHRASE="AUTHORIZE FULL BOOT PREVIEW DEPLOY"

usage() {
  cat <<EOF
Usage: $0 [--force-gates | --dry-run | --help]

  Full estate preview deploy with mandatory quality gates and audit report.

  --force-gates  Re-run run-local-quality-gates.sh even if valid report exists for HEAD
  --dry-run      Run gates + blast explain only; do not deploy

Requires phrase on deploy: $AUTH_PHRASE

Delegates deploy to: scripts/deploy/full-boot-preview-deploy.sh
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --force-gates) FORCE_GATES=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 2 ;;
  esac
done

SHORT_SHA="$(preview_short_sha)"
REPORT_PATH="$PREVIEW_AUDIT_DIR/preview-full-boot-deploy-${SHORT_SHA}.md"
STATUS="FAIL"

echo "=== Full Boot Preview (scripts/preview/full-boot.sh) ==="
preview_preflight_common 0 0 || { preview_write_deploy_report "$REPORT_PATH" "FAIL" "full-boot" "" "Preflight failed."; exit 1; }

# Quality gates
if [[ "$FORCE_GATES" == "1" ]] || ! preview_vm_gates_valid_for_head; then
  echo "--- Running quality gates ---"
  if ! bash scripts/pipeline/run-local-quality-gates.sh; then
    preview_write_deploy_report "$REPORT_PATH" "FAIL" "full-boot" "" "Quality gates failed."
    exit 1
  fi
else
  echo "OK: VM quality gates already valid for HEAD (use --force-gates to re-run)"
fi

if [[ "$DRY_RUN" == "1" ]]; then
  bash scripts/preview/explain-blast-radius.sh || true
  preview_write_deploy_report "$REPORT_PATH" "DRY-RUN" "full-boot" "" "Dry-run: gates passed; deploy not executed."
  echo "DRY-RUN PASS: gates valid; use full-boot-preview-deploy.sh or re-run without --dry-run"
  exit 0
fi

resolve_preview_deploy_metadata
echo ""
echo "Deploy metadata: branch=$PREVIEW_DEPLOY_BRANCH commit=${PREVIEW_DEPLOY_COMMIT:0:8}"
echo "Authorization phrase required: $AUTH_PHRASE"
echo ""

DEPLOY_LOG="$(mktemp)"
trap 'rm -f "$DEPLOY_LOG"' EXIT

set +e
bash scripts/deploy/full-boot-preview-deploy.sh 2>&1 | tee "$DEPLOY_LOG"
DEPLOY_RC=${PIPESTATUS[0]}
set -e

EXTRA=""
if [[ -f "$REPO_PATH/reports/full-boot/runtime-image-truth.md" ]]; then
  EXTRA="See [runtime-image-truth.md](../../reports/full-boot/runtime-image-truth.md) for digest alignment."
fi
if [[ -f "$REPO_PATH/reports/full-boot/full-boot-runtime-report.md" ]]; then
  EXTRA="${EXTRA}
See [full-boot-runtime-report.md](../../reports/full-boot/full-boot-runtime-report.md) for estate completeness."
fi

if [[ "$DEPLOY_RC" -eq 0 ]]; then
  STATUS="PASS"
else
  STATUS="FAIL"
fi

preview_write_deploy_report "$REPORT_PATH" "$STATUS" "full-boot" "" "$EXTRA"
echo ""
echo "=== Final: $STATUS ==="
echo "Branch: $(preview_branch)"
echo "HEAD: $SHORT_SHA"
echo "Preview URL: $PREVIEW_URL"
echo "Report: $REPORT_PATH"
exit "$DEPLOY_RC"
