#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
bash scripts/dev/check-tools.sh
bash scripts/dev/install-dependencies.sh
