#!/usr/bin/env bash
# Impilo vNext — one-time VM bootstrap for remote dev workspace + k3s preview.
# Idempotent where practical. Run as user robert with sudo.
set -euo pipefail

SUDO_PASS="${SUDO_PASS:-}"
if [[ -n "$SUDO_PASS" ]]; then
  echo "$SUDO_PASS" | sudo -S -v
fi

REPO_URL="${REPO_URL:-https://github.com/rtgongora/Impilo-vNext.git}"
REPO_BRANCH="${REPO_BRANCH:-claude/staging-ux-orchestration-remediation-Yypyl}"
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
SSH_PORT="${SSH_PORT:-2276}"

log() { echo "[$(date '+%H:%M:%S')] $*"; }

log "=== Phase 2: base packages ==="
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update -qq
sudo apt-get install -y -qq \
  git curl wget unzip jq htop tree ca-certificates gnupg lsb-release \
  build-essential software-properties-common openssl ufw fail2ban \
  make rsync net-tools lsof tmux nano vim apt-transport-https \
  conntrack iproute2

log "=== Phase 2: Java 21 (Temurin) ==="
if ! java -version 2>&1 | grep -q '21\.'; then
  sudo install -d -m 0755 /etc/apt/keyrings
  wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo tee /etc/apt/keyrings/adoptium.asc >/dev/null
  echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo $VERSION_CODENAME) main" | \
    sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null
  sudo apt-get update -qq
  sudo apt-get install -y -qq temurin-21-jdk
fi
java -version 2>&1 | head -1

log "=== Phase 2: Maven ==="
if ! command -v mvn >/dev/null 2>&1; then
  sudo apt-get install -y -qq maven
fi
mvn -version | head -1

log "=== Phase 2: Node.js 20 ==="
if ! command -v node >/dev/null 2>&1 || ! node -v | grep -q '^v20'; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
  sudo apt-get install -y -qq nodejs
fi
node -v && npm -v

log "=== Phase 3: filesystem ==="
sudo mkdir -p /opt/impilo/{repos,env,preview,scripts,backups,logs}
sudo chown -R robert:robert /opt/impilo

log "=== Phase 4: clone repo ==="
if [[ ! -d "$REPO_PATH/.git" ]]; then
  git clone --branch "$REPO_BRANCH" "$REPO_URL" "$REPO_PATH"
else
  cd "$REPO_PATH"
  git fetch origin "$REPO_BRANCH" || true
  git checkout "$REPO_BRANCH" || true
  git pull --ff-only origin "$REPO_BRANCH" || true
fi
cd "$REPO_PATH"
git remote -v
git branch --show-current
git rev-parse HEAD

log "=== Phase 8: Docker ==="
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker robert
fi
docker --version
docker compose version

log "=== Phase 7: k3s ==="
if ! command -v k3s >/dev/null 2>&1; then
  curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--write-kubeconfig-mode 644" sh -
fi
mkdir -p "$HOME/.kube"
sudo cp /etc/rancher/k3s/k3s.yaml "$HOME/.kube/config" 2>/dev/null || true
sudo chown robert:robert "$HOME/.kube/config" 2>/dev/null || true
export KUBECONFIG="$HOME/.kube/config"
kubectl get nodes
kubectl get pods -A
kubectl get storageclass

log "=== Phase 7: Helm ==="
if ! command -v helm >/dev/null 2>&1; then
  curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
fi
helm version --short

log "=== Phase 7: namespaces ==="
kubectl create namespace impilo-preview --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace impilo-infra --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace impilo-observability --dry-run=client -o yaml | kubectl apply -f -

log "=== Phase 16: UFW (SSH ${SSH_PORT} first) ==="
sudo ufw allow "${SSH_PORT}/tcp" comment 'SSH' || true
sudo ufw allow 80/tcp comment 'HTTP preview ingress' || true
sudo ufw allow 443/tcp comment 'HTTPS preview ingress' || true
echo "y" | sudo ufw enable || true
sudo ufw status verbose || true

log "=== Bootstrap complete ==="
