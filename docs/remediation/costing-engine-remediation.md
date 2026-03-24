# Costing Engine (COSTA) Deep Remediation Report

**Date:** 2026-03-24
**Verdict:** NO REMEDIATION NEEDED — Costing engine is fully implemented

## Why This Audit Was Triggered

A prior audit concern suggested the costing engine UI had stubs. This wave re-audited with maximum scrutiny.

## Findings

### Backend: services/costing-engine-service/

| Aspect | Status | Evidence |
|--------|--------|----------|
| Java source files | 103 files | Full domain model, services, controllers, infrastructure |
| Database tables | 16 tables | Flyway V001__init.sql with proper schema |
| Cost engines | 5 implementations | Tariff, Micro, ABC, Standard, StockAverage |
| REST controllers | 4 controllers | Bill, Estimate, Tariff, Ruleset, Audit |
| Kafka outbox | Real | OutboxPublisher with scheduled publishing |
| Trust integration | Real | TrustContextHolder in all controllers |

### UI: ui/costa-console/

| Page | Route | Real Fetch? | Real Mutation? | End-to-End? |
|------|-------|-------------|----------------|-------------|
| Bills | /bills | YES (GET /costa/v1/bills/{id}) | YES (recompute, approve, finalize) | YES |
| Tariffs | /tariffs | YES (GET /costa/v1/tariffs) | YES (CSV import POST) | YES |
| Rulesets | /rulesets | YES (GET /costa/v1/rulesets) | YES (publish POST) | YES |
| Simulate | /simulate | YES (POST /costa/v1/estimate) | YES (cost estimation) | YES |
| Audit | /audit | YES (GET /costa/v1/audit/bill/{id}) | READ-ONLY | YES |

### API Client: costaApi.ts

14+ real endpoints:
- `createEstimate`, `createDraft`, `getBill`, `postLine`, `recompute`
- `submitApproval`, `approve`, `finalize`, `issueInvoice`
- `createPaymentIntent`, `refund`, `listTariffs`, `listRulesets`
- `publishRuleset`, `auditBill`

All route through Envoy → TSHEPO with trust header injection.

### Experience UI: /finance/billing

| Route | Real Fetch? | Real Mutation? | Notes |
|-------|-------------|----------------|-------|
| /finance/billing | YES (GET /internal/v1/finance/billing) | READ-ONLY | Customer-facing invoice view — intentionally separate from admin COSTA console |

## Deep Remediation Table

| Route/Page | Prior Classification | New Classification | Real Fetch? | Real Mutation? | End-to-End? | Files Changed |
|-----------|---------------------|-------------------|-------------|----------------|-------------|---------------|
| costa-console/bills | Not audited | REAL | YES | YES | YES | None needed |
| costa-console/tariffs | Not audited | REAL | YES | YES | YES | None needed |
| costa-console/rulesets | Not audited | REAL | YES | YES | YES | None needed |
| costa-console/simulate | Not audited | REAL | YES | YES | YES | None needed |
| costa-console/audit | Not audited | REAL | YES | N/A | YES | None needed |
| experience/finance/billing | Suspected stub | REAL (read-only) | YES | N/A | YES | None needed |

## Conclusion

The costing engine concern was a false positive. COSTA is one of the most complete services in the platform with full vertical-slice implementation from UI through BFF to database persistence.
