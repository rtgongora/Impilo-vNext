#!/usr/bin/env bash
# Impilo vNext — dependency installation results (update after running install-dependencies.sh on VM)
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
REPORT="$REPO_PATH/docs/environment/DEPENDENCY_INSTALLATION_REPORT.md"

{
  echo "# Dependency Installation Report"
  echo ""
  echo "**Generated:** $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "**Host:** $(hostname)"
  echo ""
  echo "## Tool Versions"
  echo "| Tool | Version |"
  echo "|------|---------|"
  echo "| Java | $(java -version 2>&1 | head -1) |"
  echo "| Maven | $(mvn -version 2>/dev/null | head -1 || echo missing) |"
  echo "| Node | $(node -v 2>/dev/null || echo missing) |"
  echo "| npm | $(npm -v 2>/dev/null || echo missing) |"
  echo "| Docker | $(docker --version 2>/dev/null || echo missing) |"
  echo ""
  echo "## Frontend"
  echo "- Package manager: **npm** (package-lock.json, packageManager npm@10.8.2)"
  echo "- Command: \`cd ui && npm ci --legacy-peer-deps\`"
  if [[ -d "$REPO_PATH/ui/node_modules" ]]; then
    echo "- Status: **ui/node_modules present**"
  else
    echo "- Status: **ui/node_modules missing**"
  fi
  echo ""
  echo "## Backend"
  echo "- Build tool: **Maven**"
  echo "- Command: \`cd services && mvn install -pl shared-core,shared-kernel-java -am -DskipTests\`"
  echo ""
  echo "Run \`bash scripts/dev/install-dependencies.sh\` and re-run this script to refresh."
} > "$REPORT"
echo "Wrote $REPORT"
