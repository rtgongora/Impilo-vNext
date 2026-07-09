#!/usr/bin/env bash
#
# High-entropy secret gate (P5 / P0-tail of the secrets-migration plan). Scans
# only the NEW commits in a push/PR range with gitleaks — fast (ms) and inherently
# ignores the already-committed preview placeholders, so no findings-baseline is
# needed. Complements scripts/guard/check-committed-secrets.sh (which catches the
# low-entropy *-change-me-* convention gitleaks does not).
#
# Usage: gitleaks-diff-scan.sh <base-sha> [head-sha]
#   base-sha empty / invalid / shallow  -> scans just the HEAD commit.
# Requires `gitleaks` on PATH (CI installs it) and a non-shallow checkout for a
# real range (fetch-depth: 0).
set -euo pipefail

BASE="${1:-}"
HEAD="${2:-HEAD}"
CONFIG="${GITLEAKS_CONFIG:-.gitleaks.toml}"

if [[ -z "$BASE" || "$BASE" =~ ^0+$ ]] || ! git rev-parse -q --verify "${BASE}^{commit}" >/dev/null 2>&1; then
  echo "gitleaks: no valid base ($BASE) — scanning HEAD commit only"
  LOGOPTS="-1 ${HEAD}"
else
  echo "gitleaks: scanning range ${BASE}..${HEAD}"
  LOGOPTS="${BASE}..${HEAD}"
fi

gitleaks git . --log-opts="$LOGOPTS" --config "$CONFIG" --redact --exit-code 1
echo "gitleaks: no new high-entropy secrets in range."
