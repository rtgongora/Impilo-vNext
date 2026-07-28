#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
FAIL=0

echo "=== Backend spot tests ==="
cd "$REPO_PATH/services/experience-bff"
if mvn -q test -Dtest=MobileTelemedicineControllerTest -Dspring.profiles.active=test \
  -Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration 2>/dev/null; then
  echo "PASS experience-bff MobileTelemedicineControllerTest"
else
  echo "WARN experience-bff targeted test failed or skipped"
  FAIL=1
fi

echo "=== Frontend type-check (one-ui-shell) ==="
cd "$REPO_PATH/ui"
if npm -w one-ui-shell run type-check; then
  echo "PASS one-ui-shell type-check"
else
  echo "FAIL one-ui-shell type-check"
  FAIL=1
fi

exit "$FAIL"
