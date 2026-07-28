#!/usr/bin/env bash
# changed-services-since.sh [baseline_commit]
#
# Prints the set of service ids whose source changed since a baseline (working
# tree vs baseline, so uncommitted changes count) — for incremental image builds.
# Prints the single token "ALL" when a shared input changed (parent pom, shared-core,
# shared-kernel, image templates), because those affect every image and demand a full
# estate rebuild.
#
# Baseline default = the last certified/deployed commit
# (reports/full-boot/full-estate-certified-commit.txt). With no baseline and no
# certified file, prints ALL (safe: first build / unknown state = build everything).
#
# Usage:
#   ids=$(scripts/build/changed-services-since.sh)
#   if [ "$ids" = ALL ]; then
#     bash scripts/build/build-full-vnext-images.sh --full-estate
#   elif [ -n "$ids" ]; then
#     bash scripts/build/build-full-vnext-images.sh $(printf ' --only %s' $ids)
#   else
#     echo "nothing changed — no build needed"
#   fi
set -uo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

BASE="${1:-}"
if [ -z "$BASE" ] && [ -f reports/full-boot/full-estate-certified-commit.txt ]; then
  BASE="$(tr -d '[:space:]' < reports/full-boot/full-estate-certified-commit.txt)"
fi
# Unknown baseline, or baseline not a valid commit → full rebuild (safe default).
if [ -z "$BASE" ] || ! git rev-parse --verify --quiet "$BASE^{commit}" >/dev/null 2>&1; then
  echo ALL; exit 0
fi

CHANGED="$(git diff --name-only "$BASE" -- 2>/dev/null)"
[ -z "$CHANGED" ] && exit 0   # nothing changed

# Shared inputs that affect every image → full rebuild.
# NB: use here-strings, NOT `printf | grep -q` — under `set -o pipefail`, grep -q closes
# the pipe on first match, printf gets SIGPIPE (141), and the pipeline reports non-zero
# even on a MATCH, silently defeating the force-full check (ships stale dependents).
if grep -qE '^(services/shared-core/|services/pom\.xml$|libs/shared-kernel(-java)?/|scripts/build/templates/|scripts/build/build-runtime-image-from-jar\.sh$|scripts/build/build-full-vnext-images\.sh$)' <<<"$CHANGED"; then
  echo ALL; exit 0
fi

# Map changed paths to service ids (dir name under services/, plus UI/BFF surfaces).
{
  sed -nE 's#^services/([^/]+)/.*#\1#p' <<<"$CHANGED"
  grep -qE '^ui/one-ui-shell/' <<<"$CHANGED" && echo one-ui-shell
  grep -qE '^services/experience-bff/' <<<"$CHANGED" && echo experience-bff
} | grep -v '^shared-core$' | sort -u | tr '\n' ' ' | sed 's/ $//'
echo
