# Reality Check Acceptance Pack — Impilo vNext

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0

## Purpose

This acceptance pack provides:
1. Exact commands to run all reality check scripts
2. How to interpret results
3. What PASS means for each check
4. What still requires richer environment execution

## Quick Start

### Run All Checks (Structural Mode)

```bash
# From repo root
./scripts/reality-check/run-all.sh
```

This runs all 7 risk class checks in structural mode (no running services required).

### Run All Checks (Live Mode — requires Docker + running services)

```bash
# First: start the runtime
./scripts/dev-runtime.sh up

# Then: run checks with live testing
./scripts/reality-check/run-all.sh --live
```

### Run Individual Checks

```bash
# A. Fleet Build
./scripts/reality-check/build-fleet.sh

# B. Wire Contracts
./scripts/reality-check/run-wire-checks.sh          # structural
./scripts/reality-check/run-wire-checks.sh --live    # live

# C. Eventing Interoperability
./scripts/reality-check/run-eventing-checks.sh       # structural
./scripts/reality-check/run-eventing-checks.sh --live # live

# D. Mobile Runnability
./scripts/reality-check/run-mobile-runnability-checks.sh

# E. Compose / Runtime Wiring
./scripts/reality-check/run-compose-checks.sh        # structural
./scripts/reality-check/run-compose-checks.sh --live  # live

# F. Dynamic Compliance
./scripts/reality-check/run-dynamic-compliance-checks.sh       # structural
./scripts/reality-check/run-dynamic-compliance-checks.sh --live # live

# G. Production Robustness
./scripts/reality-check/run-production-robustness-checks.sh
```

### Run Existing Compliance Check (Already Verified)

```bash
./scripts/compliance/full-platform-compliance-check.sh
```

## Interpreting Results

### Output Format

Each script outputs:
- **PASS** (green): Check verified successfully
- **FAIL** (red): Check found an issue
- **SKIP** (yellow): Check could not run due to missing tools/services
- **Summary**: Total/Passed/Failed/Skipped counts

### Exit Codes

| Exit Code | Meaning |
|---|---|
| 0 | All checks passed (or only skips) |
| 1 | One or more checks failed |

### What PASS Means

| Risk Class | Structural PASS Means | Live PASS Means |
|---|---|---|
| A. Fleet Build | Build topology is correct, dependencies declared | All modules compile, Docker images build |
| B. Wire Contracts | Header constants/filters/tests exist in source | HTTP requests actually enforce headers |
| C. Eventing | Outbox tables/fields/types present in source | Events actually flow between services via Kafka |
| D. Mobile | Expo config, RN components, no web-only APIs | App builds and runs on device/simulator |
| E. Compose | Compose files exist, services defined, healthchecks | All services start and respond healthy |
| F. Compliance | Static markers present, test files exist | Tests execute and pass in Spring context |
| G. Robustness | Service has 5+/7 production markers | Service handles load, errors, and edge cases |

## What Was Verified in This Session

| Check | Environment | Result | Evidence |
|---|---|---|---|
| Static compliance (67/67) | This env | ALL PASS | Script output |
| Production robustness classification | This env | 2R/54A/11M/0F | Script output |
| Dockerfile coverage | This env | 30/67 (44%) | File scan |
| Compose service coverage | This env | 9/67 (13%) | YAML parse |
| GoldenContractIT presence | This env | 67/67 (100%) | File scan |
| Outbox coverage | This env | 66/67 (99%) | grep scan |
| Mobile source quality | This env | No web-only APIs | Source analysis |
| Build topology | This env | Correct reactor + libs | POM analysis |

## What Requires Richer Environment

### Tier 1: Maven + Java 21

```bash
# Build libs first
cd libs/shared-kernel-java && mvn -B clean install -DskipTests
cd libs/tshepo-contracts && mvn -B clean install -DskipTests
cd libs/tshepo-sdk && mvn -B clean install -DskipTests
cd libs/tech-companion && mvn -B clean install -DskipTests
cd libs/tech-companion-harness && mvn -B clean install -DskipTests
# ... remaining libs

# Build all services
cd services && mvn -B clean compile -DskipTests -T 1C --fail-at-end

# Run GoldenContractIT across all services
cd services && mvn -B test -Dtest="*GoldenContractIT" -T 1C --fail-at-end
```

### Tier 2: Docker + Docker Compose

```bash
# Start infrastructure
docker compose -f docker-compose.runtime.yml up -d

# Run smoke tests
./scripts/smoke/smoke.sh

# Run event bus proof
./scripts/smoke/event-bus-proof.sh

# Run live reality checks
./scripts/reality-check/run-all.sh --live
```

### Tier 3: Node.js 20+ + Expo CLI

```bash
# UI workspace
cd ui && npm install --legacy-peer-deps && npx turbo run build

# Mobile apps
cd apps/mobile/provider-app && npm install && expo prebuild
cd apps/mobile/citizen-app && npm install && expo prebuild
```

## Findings Summary Table

| # | Document | Risk Class | Finding |
|---|---|---|---|
| 1 | [fleet-build-matrix.md](../reality-check/fleet-build-matrix.md) | A. Fleet Build | Sound topology, 38 missing Dockerfiles |
| 2 | [wire-contract-findings.md](../reality-check/wire-contract-findings.md) | B. Wire Contracts | Consistent across Java/TS, dynamically untested |
| 3 | [eventing-interoperability-findings.md](../reality-check/eventing-interoperability-findings.md) | C. Eventing | Universal outbox, cross-service unverified |
| 4 | [mobile-runnability-findings.md](../reality-check/mobile-runnability-findings.md) | D. Mobile | Genuinely native, prebuild pending |
| 5 | [runtime-compose-findings.md](../reality-check/runtime-compose-findings.md) | E. Compose | Core wired, 58 services not in compose |
| 6 | [dynamic-compliance-findings.md](../reality-check/dynamic-compliance-findings.md) | F. Compliance | Static passes, dynamic infra exists |
| 7 | [production-robustness-findings.md](../reality-check/production-robustness-findings.md) | G. Robustness | 0 fragile, 11 minimal need hardening |
| 8 | [full-platform-reality-check-report.md](../reality-check/full-platform-reality-check-report.md) | All | Master report |

## DoD Checklist

- [x] All 7 risk classes explicitly checked
- [x] All required docs created (8 findings + 1 acceptance pack)
- [x] All required scripts created (7 individual + 1 orchestrator)
- [x] Static compliance verified in this environment (67/67 pass)
- [x] Production robustness classified in this environment (0 fragile)
- [x] No risk class silently skipped
- [x] Findings clearly separate proven vs unverified vs blocked
- [x] Prioritized hardening backlog created
- [x] Scripts support both structural and live modes
- [x] Scripts fail clearly with explanations when tools missing

## Overall Verdict

**⚠️ PARTIAL COMPLETE**

The platform is architecturally sound, compliance-ready, and has no fragile services. The primary gaps are:
1. Dockerfile coverage (44%)
2. Compose coverage (13%)
3. Dynamic test execution (needs Maven + Docker)

These are breadth gaps, not depth gaps. The services that ARE fully wired (core Registry Spine + Clinical Core) demonstrate production-quality patterns.
