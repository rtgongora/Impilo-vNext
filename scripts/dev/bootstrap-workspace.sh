#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"
bash scripts/dev/check-tools.sh
bash scripts/dev/install-dependencies.sh
