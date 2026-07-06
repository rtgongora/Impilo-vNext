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

# oros-service was in no gate (CI or VM) — its dcm4che DICOM modality-worklist
# code and the WS-P6-B modality changes were never test-verified. The 32 unit
# tests (surefire *Test — incl. DicomMwlTest / ImagingWorkflowServiceTest) need
# no DB; the single OrosGoldenContractIT (failsafe *IT) is not run by `mvn test`.
# dcm4che resolves from https://maven.dcm4che.org (reachable on the VM).
gate_run "oros-imaging-tests" bash -c '
  cd services && mvn test -pl oros-service -am -q
' || FAIL=1

# Full-reactor unit-test gate (Phase 2 of the CI-coverage closure). `mvn test`
# runs surefire (*Test) only — never failsafe (*IT) — so this is the H2/Mockito
# unit set across every reactor module and needs no Docker/Kafka/Postgres. The
# full-reactor baseline (anchor cc302e5dc, after the pharmacy getOrderId() fix)
# proved this set green: 112 modules, 3248 tests, 0 failures. Before this gate,
# 96 of 113 modules ran in NO gate at all — the gap that let the pharmacy compile
# break onto the anchor unseen.
#
# The three excluded modules each have a dedicated gate above whose special
# handling this generic run would not reproduce:
#   !oros-service                            — dcm4che external repo (oros-imaging-tests)
#   !experience-bff                          — needs test,test-ci profile + datasource
#   !zw.gov.mohcc.impilo:tshepo-authz-service — dedicated tshepo-authz-tests gate
# Quarantine list is otherwise EMPTY. If a future module's *Test needs infra this
# gate cannot provide, add it here with a one-line reason (Phase 3 burns it down).
gate_run "backend-reactor-tests" bash -c '
  cd services && mvn test \
    -pl "!oros-service,!experience-bff,!zw.gov.mohcc.impilo:tshepo-authz-service" -q
' || FAIL=1

exit "$FAIL"
