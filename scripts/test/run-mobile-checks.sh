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

# A failed install is an environment problem (registry, lockfile, disk), not a code defect, so it
# stays advisory — but it must say plainly that the typechecks did not run, otherwise a green gate
# reads as "mobile types are clean" when nothing was checked.
if ! gate_run "mobile-workspace-install" bash -c 'cd apps/mobile && pnpm install'; then
  ADVISORY=1
  gate_warn "mobile workspace install failed — typechecks did NOT run (advisory: environment, not code)"
fi

if [[ "$ADVISORY" == "0" ]]; then
  # Blocking. A type error in a shipped app is a code defect. These used to be downgraded to a
  # warning and the script exited 0 regardless, so a mobile app that did not compile could not fail
  # this gate — the check ran, reported, and was ignored.
  gate_run "mobile-citizen-typecheck" bash -c 'cd apps/mobile/citizen-app && pnpm exec tsc --noEmit' || FAIL=1
  gate_run "mobile-provider-typecheck" bash -c 'cd apps/mobile/provider-app && pnpm exec tsc --noEmit' || FAIL=1
fi

gate_warn "Android APK build and iOS builds are advisory until CI runners are stabilized (see docs/environment/MOBILE_TEST_GATE.md)"

exit "$FAIL"
