#!/usr/bin/env bash
# Smoke-check Maestro production-readiness flows (requires Maestro CLI + running mobile app).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${ROOT}/apps/mobile"

if ! command -v maestro >/dev/null 2>&1; then
  echo "Maestro CLI not installed — skip with: npm i -g @maestrohq/cli" >&2
  exit 0
fi

maestro test maestro/flows/provider-production-readiness.yaml
maestro test maestro/flows/citizen-production-readiness.yaml
