#!/usr/bin/env bash
# Remote preview deploy invoked from GitHub Actions over SSH (see deploy-preview.yml, ci.yml).
set -euo pipefail

BRANCH="${DEPLOY_BRANCH:?DEPLOY_BRANCH is required}"
REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
TARGET_SHA="${DEPLOY_COMMIT_SHA:-}"

cd "$REPO"
git fetch origin "$BRANCH"
git checkout "$BRANCH"
git pull --ff-only origin "$BRANCH" || true
if [[ -n "$TARGET_SHA" ]]; then
  git checkout "$TARGET_SHA"
fi

export DEPLOY_BRANCH="$BRANCH"
export DEPLOY_COMMIT_SHA="$(git rev-parse HEAD)"

bash scripts/dev/install-dependencies.sh
bash scripts/dev/build-all.sh
bash scripts/deploy/preview-build-images.sh
bash scripts/deploy/preview-deploy.sh
bash scripts/deploy/preview-smoke-test.sh

echo "Deployed branch=$(git branch --show-current) commit=$(git rev-parse --short HEAD)"
curl -sf http://127.0.0.1/health/version || true
