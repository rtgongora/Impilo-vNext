#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_gate-common.sh"
cd "$REPO_PATH"
FAIL=0

if [[ "${PREVIEW_GATES_SKIP_BACKEND:-0}" == "1" ]]; then
  gate_warn "backend checks skipped (PREVIEW_GATES_SKIP_BACKEND=1)"
  exit 0
fi

gate_run "maven-shared-libs" bash -c '
  cd services && mvn install -pl shared-core -am -DskipTests -q
  cd ../libs/shared-kernel-java && mvn install -DskipTests -q
' || FAIL=1

gate_run "maven-tech-companion" bash -c '
  cd libs/tech-companion && mvn install -DskipTests -q
  cd ../tech-companion-harness && mvn install -DskipTests -q
' || FAIL=1

gate_run "experience-bff-tests" bash -c '
  cd services/experience-bff
  mvn test -Dspring.profiles.active=test,test-ci \
    -Dspring.datasource.url="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/test_db}" \
    -Dspring.datasource.username="${SPRING_DATASOURCE_USERNAME:-test}" \
    -Dspring.datasource.password="${SPRING_DATASOURCE_PASSWORD:-test}" -q
' || FAIL=1

gate_run "tshepo-authz-tests" bash -c '
  cd services && mvn test -pl zw.gov.mohcc.impilo:tshepo-authz-service -q
' || FAIL=1

exit "$FAIL"
