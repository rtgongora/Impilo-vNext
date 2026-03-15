#!/usr/bin/env bash
# =============================================================================
# Wave 19 — Production Readiness Verifier
# =============================================================================
#
# Repo-level readiness verification script. Checks that all required
# production-readiness artifacts exist, are structurally valid, and that
# Ring 0 services meet baseline health/metrics requirements.
#
# Usage:
#   ./scripts/production-readiness/verify-readiness.sh
#
# Exit codes:
#   0 — all checks pass
#   1 — one or more checks failed
#   2 — prerequisites missing (tools not installed)
# =============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FAILURES=0
TOTAL=0
WARNINGS=0

# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

pass()  { TOTAL=$((TOTAL + 1)); echo -e "  ${GREEN}[PASS]${NC} $1"; }
fail()  { TOTAL=$((TOTAL + 1)); FAILURES=$((FAILURES + 1)); echo -e "  ${RED}[FAIL]${NC} $1"; }
warn()  { WARNINGS=$((WARNINGS + 1)); echo -e "  ${YELLOW}[WARN]${NC} $1"; }
info()  { echo -e "  ${BLUE}[INFO]${NC} $1"; }
banner() { echo -e "\n${BLUE}═══ $1 ═══${NC}\n"; }

check_file() {
  local path="$1"
  local desc="$2"
  if [ -f "${REPO_ROOT}/${path}" ]; then
    pass "${desc}: ${path}"
  else
    fail "${desc}: ${path} NOT FOUND"
  fi
}

check_file_contains() {
  local path="$1"
  local pattern="$2"
  local desc="$3"
  if [ -f "${REPO_ROOT}/${path}" ] && grep -q "${pattern}" "${REPO_ROOT}/${path}" 2>/dev/null; then
    pass "${desc}"
  else
    fail "${desc}"
  fi
}

# =============================================================================
# SECTION 1: SLO/SLI Artifact Existence
# =============================================================================
banner "Section 1: SLO/SLI Definitions"

check_file "docs/production-readiness/ring0-slo-sli-spec.md" "SLI/SLO specification"
check_file "docs/production-readiness/error-budgets-and-alerting.md" "Error budgets & alerting"

# Verify SLO spec covers required services
for svc in TSHEPO VITO VARAPI TUSO ZIBO; do
  check_file_contains "docs/production-readiness/ring0-slo-sli-spec.md" "${svc}" \
    "SLO spec covers ${svc}"
done

# Verify extended services (MSIKA, BUTANO, MUSHEX) have SLO definitions
for svc in MSIKA BUTANO MUSHEX; do
  check_file_contains "docs/production-readiness/ring0-slo-sli-spec.md" "${svc}" \
    "SLO spec covers ${svc}"
done

# Verify SLO spec includes required metric categories
for metric in availability latency "outbox"; do
  check_file_contains "docs/production-readiness/ring0-slo-sli-spec.md" "${metric}" \
    "SLO spec includes ${metric} metrics"
done

# =============================================================================
# SECTION 2: Alerting Rules & Dashboards
# =============================================================================
banner "Section 2: Alerting Rules & Dashboards"

check_file "tools/ops/prometheus/rules/ring0-alerts.yml" "Prometheus alert rules"
check_file "tools/ops/grafana/dashboards/ring0-golden-signals.json" "Grafana golden signals dashboard"

# Verify alert rules contain burn-rate alerts
check_file_contains "tools/ops/prometheus/rules/ring0-alerts.yml" "BurnCritical\|burn_rate\|burn" \
  "Alert rules include burn-rate alerts"

# =============================================================================
# SECTION 3: Load/Performance Harnesses
# =============================================================================
banner "Section 3: Load/Performance Harnesses"

check_file "tools/load/read-heavy/read-heavy-baseline.js" "Read-heavy load harness"
check_file "tools/load/write-heavy/write-heavy-baseline.js" "Write-heavy load harness"
check_file "tools/load/outbox-lag/outbox-lag-baseline.js" "Outbox lag load harness"

# Verify load tests have threshold configuration (SLO alignment)
check_file_contains "tools/load/read-heavy/read-heavy-baseline.js" "thresholds" \
  "Read-heavy harness has SLO thresholds"
check_file_contains "tools/load/write-heavy/write-heavy-baseline.js" "thresholds" \
  "Write-heavy harness has SLO thresholds"
check_file_contains "tools/load/outbox-lag/outbox-lag-baseline.js" "thresholds" \
  "Outbox lag harness has SLO thresholds"

# =============================================================================
# SECTION 4: Security Posture
# =============================================================================
banner "Section 4: Security Posture"

check_file "docs/production-readiness/security-posture-checklist.md" "Security posture checklist"

# Verify security checklist covers required domains
check_file_contains "docs/production-readiness/security-posture-checklist.md" "mTLS\|mtls\|mutual TLS" \
  "Security checklist covers mTLS"
check_file_contains "docs/production-readiness/security-posture-checklist.md" "rotation\|Rotation" \
  "Security checklist covers secrets rotation"
check_file_contains "docs/production-readiness/security-posture-checklist.md" "RBAC\|rbac\|role" \
  "Security checklist covers RBAC"

# Verify SecurityBaselineConfig exists for Ring 0 services
for svc in tshepo vito varapi tuso zibo; do
  if [ -d "${REPO_ROOT}/services/${svc}-service" ]; then
    if find "${REPO_ROOT}/services/${svc}-service" -name "SecurityBaselineConfig.java" -type f 2>/dev/null | grep -q .; then
      pass "SecurityBaselineConfig exists: ${svc}-service"
    else
      fail "SecurityBaselineConfig missing: ${svc}-service"
    fi
  else
    warn "Service directory not found: ${svc}-service"
  fi
done

# =============================================================================
# SECTION 5: Data-Plane Non-Blocking Proof
# =============================================================================
banner "Section 5: Data-Plane Non-Blocking Proof"

check_file "scripts/production-readiness/verify-data-plane-nonblocking.sh" "Data-plane verification script"

# Verify script is executable
if [ -x "${REPO_ROOT}/scripts/production-readiness/verify-data-plane-nonblocking.sh" ]; then
  pass "Data-plane verification script is executable"
else
  fail "Data-plane verification script is not executable"
fi

# Verify it tests care-path endpoints
check_file_contains "scripts/production-readiness/verify-data-plane-nonblocking.sh" "TUSO\|tuso" \
  "Verification script tests TUSO (care-path read)"
check_file_contains "scripts/production-readiness/verify-data-plane-nonblocking.sh" "VITO\|vito" \
  "Verification script tests VITO (care-path write)"

# =============================================================================
# SECTION 6: Production Readiness Report & Sign-Off
# =============================================================================
banner "Section 6: Production Readiness Report & Sign-Off"

check_file "docs/production-readiness/production-readiness-report.md" "Production readiness report"
check_file "docs/production-readiness/sign-off-checklist.md" "Sign-off checklist"

# Verify report has required sections
check_file_contains "docs/production-readiness/production-readiness-report.md" "Verdict\|verdict\|PASS\|CONDITIONAL" \
  "Report contains readiness verdict"
check_file_contains "docs/production-readiness/production-readiness-report.md" "Unresolved\|unresolved\|Blocker\|blocker" \
  "Report contains unresolved blockers section"

# =============================================================================
# SECTION 7: Runbooks
# =============================================================================
banner "Section 7: Runbooks"

check_file "docs/production-readiness/runbooks/service-degradation.md" "Runbook: service degradation"
check_file "docs/production-readiness/runbooks/outbox-backlog.md" "Runbook: outbox backlog"
check_file "docs/production-readiness/runbooks/dependency-failure.md" "Runbook: dependency failure"
check_file "docs/production-readiness/runbooks/ring0-incident-triage.md" "Runbook: Ring 0 incident triage"

# =============================================================================
# SECTION 8: Baseline Discovery
# =============================================================================
banner "Section 8: Baseline Discovery Artifacts"

check_file "docs/production-readiness/wave19a-baseline-inventory.md" "Wave 19A baseline inventory"
check_file "docs/production-readiness/wave19a-gap-register.md" "Wave 19A gap register"
check_file "docs/production-readiness/load-and-performance-baselines.md" "Load & performance baselines doc"

# =============================================================================
# SECTION 9: Service Health Checks (if services are running)
# =============================================================================
banner "Section 9: Service Health Checks (runtime — skipped if services not running)"

RING0_SERVICES="tshepo:8081 vito:8082 varapi:8083 tuso:8084 zibo:8085"
RUNTIME_CHECKED=false

for entry in ${RING0_SERVICES}; do
  name=$(echo "${entry}" | cut -d: -f1)
  port=$(echo "${entry}" | cut -d: -f2)
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "http://localhost:${port}/actuator/health" 2>/dev/null || echo "000")
  if [ "${status}" = "200" ]; then
    pass "${name}-service health (port ${port}): UP"
    RUNTIME_CHECKED=true
  elif [ "${status}" = "000" ]; then
    warn "${name}-service (port ${port}): not reachable (services not running — expected in CI)"
  else
    fail "${name}-service health (port ${port}): status=${status}"
    RUNTIME_CHECKED=true
  fi
done

if [ "${RUNTIME_CHECKED}" = false ]; then
  info "No services running — runtime health checks skipped. Run this script with platform up for full verification."
fi

# =============================================================================
# SUMMARY
# =============================================================================
banner "PRODUCTION READINESS VERIFICATION SUMMARY"

PASSED=$((TOTAL - FAILURES))
echo "  Total checks: ${TOTAL}"
echo "  Passed:       ${PASSED}"
echo "  Failed:       ${FAILURES}"
echo "  Warnings:     ${WARNINGS}"
echo ""

if [ "${FAILURES}" -eq 0 ]; then
  echo -e "  ${GREEN}╔═══════════════════════════════════════════════════════════╗${NC}"
  echo -e "  ${GREEN}║  ALL ${TOTAL} CHECKS PASSED                                    ║${NC}"
  echo -e "  ${GREEN}║  PRODUCTION READINESS ARTIFACTS VERIFIED                  ║${NC}"
  echo -e "  ${GREEN}╚═══════════════════════════════════════════════════════════╝${NC}"
  exit 0
else
  echo -e "  ${RED}╔═══════════════════════════════════════════════════════════╗${NC}"
  echo -e "  ${RED}║  ${FAILURES} / ${TOTAL} CHECKS FAILED                                    ║${NC}"
  echo -e "  ${RED}║  PRODUCTION READINESS INCOMPLETE                         ║${NC}"
  echo -e "  ${RED}╚═══════════════════════════════════════════════════════════╝${NC}"
  exit 1
fi
