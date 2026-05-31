#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_gate-common.sh"
cd "$REPO_PATH"
FAIL=0

gate_run "secret-scan-no-private-keys" bash -c '
  hits="$(git grep -l "BEGIN RSA PRIVATE KEY\|BEGIN OPENSSH PRIVATE KEY\|BEGIN EC PRIVATE KEY" -- \
    ":(exclude)node_modules" ":(exclude).git" ":(exclude)scripts/test/run-security-checks.sh" 2>/dev/null || true)"
  test -z "$hits"
'

gate_run "secret-scan-no-ghp-tokens" bash -c '
  hits="$(git grep -E "ghp_[A-Za-z0-9]{20,}|github_pat_" -- \
    ":(exclude)node_modules" ":(exclude).git" ":(exclude)scripts/test/run-security-checks.sh" 2>/dev/null || true)"
  test -z "$hits"
'

gate_run "no-committed-env-files" bash -c '
  test $(git ls-files | grep -iE "^\\.env$|\\.env\\.production|credentials\\.json$" | wc -l) -eq 0
' || FAIL=1

gate_run "bff-yml-no-literal-passwords" bash -c '
  f=services/experience-bff/src/main/resources/application.yml
  bad=$(grep "password.*=" "$f" | grep -v "\${" | grep -v test || true)
  test -z "$bad"
' || FAIL=1

exit "$FAIL"
