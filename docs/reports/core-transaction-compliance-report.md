# Core Transaction Compliance Report

- Total checks: **12**
- Passed: **11**
- Failed: **1**

## Check Results

- **PASS** `doctrine-docs` - Core doctrine documents exist (All required files exist)
- **PASS** `canonical-contracts` - Canonical core transaction contracts exist (All required files exist)
- **PASS** `checklist-artifacts` - Checklist artifacts exist (All required files exist)
- **PASS** `bff-dual-surface` - Experience BFF dual-surface routes (Internal + alias routes found)
- **PASS** `ci-core-transaction-jobs` - CI includes core transaction gates (All core transaction jobs present)
- **FAIL** `checklist-service-coverage` - Checklist coverage for all active services (Missing checklist entries: llm-orchestration-service, ndila-service, nhume-service)
- **PASS** `dual-emit-pct-service` - pct-service dual-emits core.transaction.events (Publisher + config present)
- **PASS** `dual-emit-costing-engine-service` - costing-engine-service dual-emits core.transaction.events (Publisher + config present)
- **PASS** `dual-emit-oros-service` - oros-service dual-emits core.transaction.events (Publisher + config present)
- **PASS** `dual-emit-pharmacy-service` - pharmacy-service dual-emits core.transaction.events (Publisher + config present)
- **PASS** `dual-emit-msika-flow-service` - msika-flow-service dual-emits core.transaction.events (Publisher + config present)
- **PASS** `dual-emit-mushex-service` - mushex-service dual-emits core.transaction.events (Publisher + config present)
