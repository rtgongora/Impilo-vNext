#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_gate-common.sh"
cd "$REPO_PATH"
FAIL=0

gate_run "preview-http-regression" bash tests/regression/preview-http-regression.sh || FAIL=1

if [[ -n "${PREVIEW_BASE_URL:-}" ]]; then
  gate_run "live-preview-health" bash -c "
    curl -sf \"${PREVIEW_BASE_URL}/health/version\" | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get(\"status\")==\"ok\"'
  " || FAIL=1
else
  gate_warn "PREVIEW_BASE_URL not set; skipping live preview integration checks"
fi

exit "$FAIL"
