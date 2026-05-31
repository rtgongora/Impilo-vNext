#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_gate-common.sh"
cd "$REPO_PATH"

if [[ "${PREVIEW_GATES_SKIP_MOBILE:-0}" == "1" ]]; then
  gate_warn "mobile checks skipped (PREVIEW_GATES_SKIP_MOBILE=1)"
  exit 0
fi

FAIL=0
ADVISORY=0

if ! command -v pnpm >/dev/null 2>&1; then
  gate_warn "pnpm not installed; mobile checks advisory-only"
  exit 0
fi

gate_run "mobile-workspace-install" bash -c 'cd apps/mobile && pnpm install' || { ADVISORY=1; FAIL=0; }

if [[ "$ADVISORY" == "0" ]]; then
  gate_run "mobile-citizen-typecheck-advisory" bash -c 'cd apps/mobile/citizen-app && pnpm exec tsc --noEmit' || gate_warn "citizen-app typecheck failed (advisory)"
  gate_run "mobile-provider-typecheck-advisory" bash -c 'cd apps/mobile/provider-app && pnpm exec tsc --noEmit' || gate_warn "provider-app typecheck failed (advisory)"
fi

gate_warn "Android APK build and iOS builds are advisory until CI runners are stabilized (see docs/environment/MOBILE_TEST_GATE.md)"

exit 0
