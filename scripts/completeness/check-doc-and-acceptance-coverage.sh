#!/usr/bin/env bash
# check-doc-and-acceptance-coverage.sh — Verify documentation and acceptance pack coverage.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FAILURES=0
WARNINGS=0

echo "═══════════════════════════════════════════════════════════════"
echo " Impilo vNext — Documentation & Acceptance Coverage Check"
echo " Run: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "═══════════════════════════════════════════════════════════════"

# ── 1. Required Acceptance Packs ──────────────────────────────────
echo ""
echo "▶ ACCEPTANCE PACKS"
echo "─────────────────────────────────────────────────────────────"
EXPECTED_ACCEPTANCE=(
  citizen-app-acceptance-pack.md
  provider-app-acceptance-pack.md
  support-app-acceptance-pack.md
  developer-partner-app-acceptance-pack.md
  mobile-program-acceptance-pack.md
  mobile-shared-foundations-acceptance-pack.md
  security-acceptance-pack.md
  offline-acceptance-pack.md
  ops-acceptance-pack.md
  experience-platform-acceptance-pack.md
  integration-platform-acceptance-pack.md
  data-platform-acceptance-pack.md
  developer-platform-acceptance-pack.md
  supply-iot-acceptance-pack.md
  resilience-ops-acceptance-pack.md
  full-platform-compliance-acceptance-pack.md
  reality-check-acceptance-pack.md
  dev-runtime-acceptance-pack.md
  completeness-audit-acceptance-pack.md
)
for pack in "${EXPECTED_ACCEPTANCE[@]}"; do
  if [ -f "$REPO_ROOT/docs/acceptance/$pack" ]; then
    size=$(wc -c < "$REPO_ROOT/docs/acceptance/$pack")
    if [ "$size" -lt 200 ]; then
      printf "  ⚠  %-50s EXISTS but tiny (%s bytes)\n" "$pack" "$size"
      WARNINGS=$((WARNINGS + 1))
    else
      printf "  ✅ %-50s (%s bytes)\n" "$pack" "$size"
    fi
  else
    printf "  ❌ %-50s MISSING\n" "$pack"
    FAILURES=$((FAILURES + 1))
  fi
done

# ── 2. Required Architecture Docs ────────────────────────────────
echo ""
echo "▶ ARCHITECTURE DOCS"
echo "─────────────────────────────────────────────────────────────"
EXPECTED_ARCH=(
  docs/architecture/v1.1/00-compliance-summary.md
  docs/architecture/v1.1/01-gap-analysis.md
  docs/architecture/v1.1/02-migration-plan.md
  docs/architecture/v1.1/03-architecture-diagram.md
  docs/architecture/v1.1/04-service-boundaries.md
  docs/architecture/v1.1/05-event-schema-template.md
  docs/architecture/v1.1/06-consistency-classes.md
  docs/architecture/v1.1/07-federation-protocol.md
  docs/plan/API_CONVENTIONS_V11.md
  docs/plan/EVENTING_AND_TOPICS.md
  docs/plan/SERVICE_CATALOG.md
  docs/plan/TESTING_CONVENTIONS.md
)
for doc in "${EXPECTED_ARCH[@]}"; do
  if [ -f "$REPO_ROOT/$doc" ]; then
    printf "  ✅ %s\n" "$doc"
  else
    printf "  ❌ %s  MISSING\n" "$doc"
    FAILURES=$((FAILURES + 1))
  fi
done

# ── 3. Completeness Audit Docs ───────────────────────────────────
echo ""
echo "▶ COMPLETENESS AUDIT DOCS"
echo "─────────────────────────────────────────────────────────────"
EXPECTED_COMPLETENESS=(
  docs/completeness/full-platform-completeness-audit.md
  docs/completeness/component-classification-matrix.md
  docs/completeness/fix-log.md
  docs/completeness/blockers-and-remaining-risks.md
)
for doc in "${EXPECTED_COMPLETENESS[@]}"; do
  if [ -f "$REPO_ROOT/$doc" ]; then
    printf "  ✅ %s\n" "$doc"
  else
    printf "  ❌ %s  MISSING\n" "$doc"
    FAILURES=$((FAILURES + 1))
  fi
done

# ── 4. Service-level READMEs ─────────────────────────────────────
echo ""
echo "▶ SERVICE READMEs"
echo "─────────────────────────────────────────────────────────────"
readme_count=0
no_readme_count=0
for svc_dir in "$REPO_ROOT"/services/*/; do
  svc_name=$(basename "$svc_dir")
  [ "$svc_name" = "shared-core" ] && continue
  if [ -f "$svc_dir/README.md" ]; then
    readme_count=$((readme_count + 1))
  else
    no_readme_count=$((no_readme_count + 1))
  fi
done
echo "  Services with README:    $readme_count"
echo "  Services without README: $no_readme_count"
if [ "$no_readme_count" -gt 40 ]; then
  echo "  ⚠  Most services lack READMEs"
  WARNINGS=$((WARNINGS + 1))
fi

# ── 5. Ops Docs ──────────────────────────────────────────────────
echo ""
echo "▶ OPS & PRODUCTION READINESS DOCS"
echo "─────────────────────────────────────────────────────────────"
EXPECTED_OPS=(
  docs/ops/observability-conventions.md
  docs/ops/security-baseline.md
  docs/dr/backup-and-restore-strategy.md
  docs/dr/rpo-rto-matrix.md
  docs/production-readiness/production-readiness-report.md
  docs/production-readiness/ring0-slo-sli-spec.md
  docs/production-readiness/sign-off-checklist.md
  docs/rollout/national-rollout-plan.md
)
for doc in "${EXPECTED_OPS[@]}"; do
  if [ -f "$REPO_ROOT/$doc" ]; then
    printf "  ✅ %s\n" "$doc"
  else
    printf "  ❌ %s  MISSING\n" "$doc"
    FAILURES=$((FAILURES + 1))
  fi
done

# ── Summary ──────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo " Failures: $FAILURES"
echo " Warnings: $WARNINGS"
if [ "$FAILURES" -gt 0 ]; then
  echo " Result:   ❌ FAIL"
else
  echo " Result:   ✅ PASS (with $WARNINGS warnings)"
fi
echo "═══════════════════════════════════════════════════════════════"

[ "$FAILURES" -gt 0 ] && exit 1
exit 0
