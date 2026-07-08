#!/usr/bin/env bash
#
# Verify the Impilo vNext Cursor Remote SSH development workspace on the VM.
# Run from the repo on the VM:
#   cd /opt/impilo/repos/Impilo-vNext && bash scripts/dev/verify-remote-cursor-workspace.sh
#
# Hard failures (FAIL) block development; soft issues (WARN) are advisory.

REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
EXPECTED_BRANCH="${EXPECTED_BRANCH:-claude/staging-ux-orchestration-remediation-Yypyl}"
EXPECTED_REMOTE="rtgongora/Impilo-vNext"
PREVIEW_URL="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
NS="impilo-preview"
FAIL=0

ok()   { echo "OK    $*"; }
warn() { echo "WARN  $*"; }
fail() { echo "FAIL  $*"; FAIL=1; }
have() { command -v "$1" >/dev/null 2>&1; }

echo "=== Cursor Remote SSH workspace verification ==="
echo "Host: $(hostname)   User: $(whoami)"
echo "Expected repo:   $REPO_PATH"
echo "Expected branch: $EXPECTED_BRANCH"
echo "Expected remote: github.com/$EXPECTED_REMOTE"
echo

# ---- Repo location -------------------------------------------------------
echo "--- Repository ---"
if [[ "$(pwd)" == "$REPO_PATH"* ]]; then
  ok "shell cwd is under repo path"
else
  warn "open the Cursor folder at $REPO_PATH (current: $(pwd))"
fi

if [[ -d "$REPO_PATH/.git" ]]; then
  ok "git repo present at $REPO_PATH"
else
  fail "git repo missing at $REPO_PATH"
fi

GIT="git -C $REPO_PATH"

# ---- Remote --------------------------------------------------------------
if [[ -d "$REPO_PATH/.git" ]]; then
  remote_url="$($GIT remote get-url origin 2>/dev/null || echo '')"
  if echo "$remote_url" | grep -qi "$EXPECTED_REMOTE"; then
    ok "origin -> $remote_url"
  else
    fail "origin remote is '$remote_url' (expected github.com/$EXPECTED_REMOTE)"
  fi

  # ---- Branch ------------------------------------------------------------
  branch="$($GIT branch --show-current 2>/dev/null || echo '?')"
  if [[ "$branch" == "$EXPECTED_BRANCH" ]]; then
    ok "branch: $branch"
  else
    warn "branch is '$branch' (expected '$EXPECTED_BRANCH' unless explicitly changed)"
  fi

  # ---- Commit SHA --------------------------------------------------------
  ok "commit: $($GIT rev-parse --short HEAD 2>/dev/null || echo '?')"

  # ---- Working tree ------------------------------------------------------
  if [[ -z "$($GIT status --porcelain 2>/dev/null)" ]]; then
    ok "working tree clean"
  else
    warn "working tree has uncommitted changes:"
    $GIT status --short | sed 's/^/      /'
  fi
fi
echo

# ---- Toolchain -----------------------------------------------------------
echo "--- Toolchain ---"
if have java; then ok "Java: $(java -version 2>&1 | head -1)"; else warn "Java not installed"; fi
if have node; then ok "Node: $(node -v)"; else warn "Node not installed"; fi

pm=""
for c in pnpm npm yarn; do if have "$c"; then pm="$pm $c=$($c -v 2>/dev/null)"; fi; done
if [[ -n "$pm" ]]; then ok "Package manager(s):$pm"; else warn "no Node package manager (pnpm/npm/yarn) found"; fi

if have mvn; then ok "Maven: $(mvn -v 2>/dev/null | head -1)";
elif [[ -x "$REPO_PATH/mvnw" ]]; then ok "Maven wrapper (./mvnw) present";
else warn "Maven not found"; fi
if have gradle; then ok "Gradle: $(gradle -v 2>/dev/null | awk '/Gradle/{print $2; exit}')";
elif [[ -x "$REPO_PATH/gradlew" ]]; then ok "Gradle wrapper (./gradlew) present"; fi

if have docker; then ok "Docker: $(docker --version 2>/dev/null)";
elif have nerdctl; then ok "nerdctl image builder present";
elif have buildah; then ok "buildah image builder present";
else warn "no image builder (docker/nerdctl/buildah) found"; fi
echo

# ---- Kubernetes / Helm ---------------------------------------------------
echo "--- Kubernetes / Helm ---"
if have kubectl; then ok "kubectl: $(kubectl version --client -o yaml 2>/dev/null | awk -F': ' '/gitVersion/{print $2; exit}')"; else warn "kubectl not installed"; fi
if have helm; then ok "Helm: $(helm version --short 2>/dev/null)"; else warn "Helm not installed"; fi

if have kubectl; then
  if kubectl get nodes >/dev/null 2>&1; then
    notready="$(kubectl get nodes --no-headers 2>/dev/null | grep -vc ' Ready' || true)"
    if [[ "$notready" -eq 0 ]]; then ok "k3s node(s) Ready"; else warn "$notready node(s) not Ready"; fi
    kubectl get nodes --no-headers 2>/dev/null | sed 's/^/      /'
  else
    warn "cannot reach k3s API (check kubeconfig / k3s service)"
  fi

  # ---- Preview namespace / workloads -----------------------------------
  if kubectl get ns "$NS" >/dev/null 2>&1; then
    ok "namespace '$NS' exists"
    echo "      Pods:"
    kubectl get pods -n "$NS" --no-headers 2>/dev/null | sed 's/^/        /'
    running="$(kubectl get pods -n "$NS" --no-headers 2>/dev/null | grep -c ' Running' || true)"
    notrun="$(kubectl get pods -n "$NS" --no-headers 2>/dev/null | grep -vc ' Running' || true)"
    if [[ "$notrun" -eq 0 && "$running" -gt 0 ]]; then ok "all $running pod(s) Running"; else warn "$notrun pod(s) not Running"; fi
    echo "      Services:"
    kubectl get svc -n "$NS" --no-headers 2>/dev/null | sed 's/^/        /'
    echo "      Ingress:"
    kubectl get ingress -n "$NS" --no-headers 2>/dev/null | sed 's/^/        /' || echo "        (none)"
  else
    warn "namespace '$NS' missing — deploy with scripts/deploy/preview-deploy.sh"
  fi
fi
echo

# ---- Preview reachability ------------------------------------------------
echo "--- Preview reachability ---"
if have curl; then
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$PREVIEW_URL" 2>/dev/null || echo 000)"
  if [[ "$code" =~ ^(200|301|302|307|308)$ ]]; then ok "$PREVIEW_URL -> HTTP $code"; else warn "$PREVIEW_URL -> HTTP $code (not reachable from VM)"; fi
else
  warn "curl not available — skipping preview reachability"
fi
echo

# ---- Summary -------------------------------------------------------------
if [[ "$FAIL" -eq 0 ]]; then
  echo "Workspace ready for Cursor Remote SSH development."
else
  echo "Workspace verification found blocking issues (FAIL) — resolve them before development."
fi
exit "$FAIL"
