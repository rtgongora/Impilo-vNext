#!/usr/bin/env bash
set -euo pipefail
FAIL=0

check() {
  if command -v "$1" >/dev/null 2>&1; then
    echo "OK  $1: $($1 --version 2>/dev/null | head -1 || $1)"
  else
    echo "MISSING  $1"
    FAIL=1
  fi
}

check java
check node
check npm
check python3
check git
check curl

if command -v mvn >/dev/null 2>&1 || [[ -x "${REPO_PATH:-.}/services/mvnw" ]]; then
  echo "OK  maven: $(mvn -version 2>/dev/null | head -1 || echo ./services/mvnw)"
else
  echo "MISSING  mvn"
  FAIL=1
fi

for opt in docker kubectl helm; do
  if command -v "$opt" >/dev/null 2>&1; then
    echo "OK  $opt (optional for full integration)"
  else
    echo "ADVISORY  $opt not installed (integration/deploy phases may be limited)"
  fi
done

if command -v rg >/dev/null 2>&1; then
  echo "OK  rg"
else
  echo "ADVISORY  rg not installed (guards use grep fallback)"
fi

if [[ "$FAIL" -ne 0 ]]; then
  exit 1
fi
exit 0
