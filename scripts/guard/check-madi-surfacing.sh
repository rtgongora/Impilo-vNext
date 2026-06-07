#!/usr/bin/env bash
# Ensures MADI backend cannot regress to invisible backend-only functionality.
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

FAIL=0
echo "=== MADI surfacing guard ==="

# 1. madi-service must exist in registry
if ! grep -q 'id: madi-service' docs/registry/services-registry.yaml; then
  echo "FAIL: madi-service missing from services-registry.yaml"
  FAIL=1
fi

# 2. OpenAPI contract must exist
if [[ ! -f contracts/openapi/madi.openapi.yaml ]]; then
  echo "FAIL: contracts/openapi/madi.openapi.yaml missing"
  FAIL=1
fi

# 3. Web hub and donor entry must exist
for f in \
  ui/one-ui-shell/src/lib/madi.ts \
  ui/one-ui-shell/src/hooks/queries/useMadi.ts \
  ui/one-ui-shell/src/app/madi/page.tsx \
  ui/one-ui-shell/src/app/madi/donor/page.tsx; do
  if [[ ! -f "$f" ]]; then
    echo "FAIL: required web surface missing: $f"
    FAIL=1
  fi
done

# 4. Madi pages must use canonical client (not raw fetch / mocks)
if grep -rl 'JSON\.stringify' ui/one-ui-shell/src/app/madi/ 2>/dev/null | grep -q page.tsx; then
  echo "FAIL: MADI pages must not use JSON.stringify as primary UI"
  FAIL=1
fi
if grep -rl 'STUB_PAGE\|MOCK_ONLY\|DEV_STUB' ui/one-ui-shell/src/app/madi/ 2>/dev/null; then
  echo "FAIL: stub markers found in MADI pages"
  FAIL=1
fi

# 5. BFF proxy must exist
for f in \
  services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/MadiServiceClient.java \
  services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/MadiController.java \
  services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/mobile/citizen/CitizenMadiController.java \
  services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/mobile/provider/ProviderMadiController.java; do
  if [[ ! -f "$f" ]]; then
    echo "FAIL: required BFF surface missing: $f"
    FAIL=1
  fi
done

# 6. Mobile service layers must exist
for f in \
  apps/mobile/citizen-app/src/services/madiService.ts \
  apps/mobile/provider-app/src/services/madiService.ts; do
  if [[ ! -f "$f" ]]; then
    echo "FAIL: required mobile client missing: $f"
    FAIL=1
  fi
done

# 7. Routes registry must include /madi hub
if ! grep -q 'path: "/madi"' ui/one-ui-shell/src/lib/routes.ts; then
  echo "FAIL: /madi route missing from routes.ts"
  FAIL=1
fi

# 8. Gap-closure surfaces (wave 2)
for f in \
  ui/one-ui-shell/src/components/madi/MadiDonorAssistPanel.tsx \
  ui/one-ui-shell/src/components/madi/MadiBedsideVerifyPanel.tsx \
  ui/one-ui-shell/src/app/madi/blood-bank/fridges/page.tsx \
  ui/one-ui-shell/src/app/madi/haemovigilance/national/page.tsx \
  services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/MadiDonorAssistController.java; do
  if [[ ! -f "$f" ]]; then
    echo "FAIL: MADI gap-closure surface missing: $f"
    FAIL=1
  fi
done

if ! grep -q 'pre-screening' contracts/openapi/madi.openapi.yaml; then
  echo "FAIL: madi.openapi.yaml missing pre-screening paths"
  FAIL=1
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "MADI-SURFACING: BLOCKED"
  exit 1
fi
echo "MADI-SURFACING: PASSED"
exit 0
