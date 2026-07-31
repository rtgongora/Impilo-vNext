#!/usr/bin/env bash
# affected-maven-modules.sh [baseline_commit]
#
# Prints a comma-separated Maven -pl list for modules whose sources changed since
# baseline (working tree vs baseline). Used by CI to run diff-scoped backend tests
# on non-canonical branches without the full reactor cost of preview-pipeline-gates.
#
# Prints ALL when shared reactor inputs change (parent pom, shared-core, shared-kernel,
# security-baseline) or when the baseline is unknown — callers should then run the
# full reactor or skip with an explicit note.
#
# Includes libs/* modules (e.g. medicine-domain) so library-only changes still get tested.
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

BASE="${1:-}"
if [ -z "$BASE" ]; then
  if [ -n "${GITHUB_BASE_SHA:-}" ]; then
    BASE="$GITHUB_BASE_SHA"
  elif [ -n "${GITHUB_EVENT_BEFORE:-}" ] && [ "$GITHUB_EVENT_BEFORE" != "0000000000000000000000000000000000000000" ]; then
    BASE="$GITHUB_EVENT_BEFORE"
  fi
fi
if [ -z "$BASE" ] || ! git rev-parse --verify --quiet "$BASE^{commit}" >/dev/null 2>&1; then
  echo ALL
  exit 0
fi

CHANGED="$(git diff --name-only "$BASE"...HEAD -- 2>/dev/null || git diff --name-only "$BASE" --)"
[ -z "$CHANGED" ] && { echo ""; exit 0; }

if grep -qE '^(services/pom\.xml$|services/shared-core/|libs/shared-kernel(-java)?/|libs/security-baseline/)' <<<"$CHANGED"; then
  echo ALL
  exit 0
fi

{
  sed -nE 's#^services/([^/]+)/.*#\1#p' <<<"$CHANGED"
  sed -nE 's#^libs/([^/]+)/.*#\1#p' <<<"$CHANGED"
} | grep -vE '^(shared-core)?$' | sort -u | paste -sd, -
