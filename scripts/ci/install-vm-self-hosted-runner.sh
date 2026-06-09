#!/usr/bin/env bash
# Bootstrap a GitHub Actions self-hosted runner on the Impilo preview VM.
# Product owner runs once with a PAT or registration token from GitHub → Settings → Actions → Runners.
#
# Usage:
#   GITHUB_RUNNER_TOKEN=<token> bash scripts/ci/install-vm-self-hosted-runner.sh
#
# After install, vm-local-gates.yml jobs with runs-on: [self-hosted, impilo-vm] will execute on this host.
set -euo pipefail

REPO_SLUG="${GITHUB_REPOSITORY:-rtgongora/Impilo-vNext}"
RUNNER_VERSION="${RUNNER_VERSION:-2.321.0}"
RUNNER_DIR="${RUNNER_DIR:-/opt/impilo/actions-runner}"
RUNNER_LABELS="${RUNNER_LABELS:-self-hosted,impilo-vm,linux,x64}"
RUNNER_NAME="${RUNNER_NAME:-impilo-preview-vm}"

if [[ -z "${GITHUB_RUNNER_TOKEN:-}" ]]; then
  echo "ERROR: set GITHUB_RUNNER_TOKEN (registration token from GitHub Actions runner setup UI)" >&2
  exit 1
fi

sudo mkdir -p "$(dirname "$RUNNER_DIR")"
if [[ ! -d "$RUNNER_DIR" ]]; then
  sudo mkdir -p "$RUNNER_DIR"
  sudo chown "$(whoami):$(whoami)" "$RUNNER_DIR"
  cd "$RUNNER_DIR"
  curl -fsSL -o actions-runner.tar.gz \
    "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz"
  tar xzf actions-runner.tar.gz
else
  cd "$RUNNER_DIR"
fi

./config.sh --url "https://github.com/${REPO_SLUG}" \
  --token "$GITHUB_RUNNER_TOKEN" \
  --name "$RUNNER_NAME" \
  --labels "$RUNNER_LABELS" \
  --unattended \
  --replace

sudo ./svc.sh install
sudo ./svc.sh start

echo "Self-hosted runner installed: $RUNNER_NAME"
echo "Labels: $RUNNER_LABELS"
echo "Workflow: .github/workflows/vm-local-gates.yml"
