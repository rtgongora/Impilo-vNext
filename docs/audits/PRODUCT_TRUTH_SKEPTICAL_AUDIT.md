# Product-Truth Skeptical Audit

> **Date:** 2026-06-07  
> **Trigger:** Phase 4 metric promotion inflated `transaction-complete` from 18 → 45 without equivalent product depth.  
> **Action:** Downgraded 24 journeys from `COMPLETION_EVIDENCE`; retained 21 with substantive chains.

## Executive summary

| Metric | Before skeptical pass | After downgrade |
|--------|----------------------:|----------------:|
| `transaction-complete` | 45 | **21** |
| Ops-only waiver | 1 (`device-system-event`) | 1 |
| Honest completion rate | 98% (misleading) | **46%** |

The 45/46 figure was **metric-complete**, not **product-shippable**. Most Wave 2–4 promotions added source-file golden-thread vitest (filesystem `readFileSync` + `toContain`) and bulk `COMPLETION_EVIDENCE` entries without runtime BFF IT, hook tests, or Playwright coverage.

## Classification rubric

| Tier | Criteria | Count |
|------|----------|------:|
| **A — Shippable** | BFF IT or substantive controller test + UI route + runtime hook/page/e2e test | 21 |
| **B — Wired but thin** | Real BFF + route; gap override or stub UX remains | 22 |
| **C — Metric-only promotion** | Source golden-thread only; removed from evidence | 19 |
| **D — Known blockers** | Explicit stubs or RTC/media blocked; removed from evidence | 3 |
| **W — Waiver** | Ops-only by design | 1 |

## Downgraded journeys (24)

### D — Known blockers (3)

| Journey | Reason |
|---------|--------|
| `payment-billing-claim` | `finance/payer-ops` uses `BLOCKED_NOT_LIVE_CAPABLE` / `INITIATED` stubs |
| `telemedicine-encounter` | RTC gateway not wired; session page shows honest partial state |
| `consent-capture` | Mvumo admin workflow depth gap; only partial BFF test |

### C — Metric-only bulk promotion (19)

`chronic-care`, `patient-search-selection`, `facility-context-selection`, `workspace-context-selection`, `provider-login`, `credential-verification`, `provider-registry-onboarding`, `registry-administration`, `integration-sync-replay`, `notification-comms`, `reporting-dashboard`, `marketplace-order`, `dispatch-delivery`, `offline-clinical-queue`, `ai-guidance-nompilo`, `surveillance-outbreak`, `fundo-learning`, `social-community`, `public-health-outreach`, `crvs-ubomi`, `citizen-onboarding`

Evidence was: `*-golden-thread.test.ts` (static source scan) + thin or missing BFF IT.

## Retained transaction-complete (21)

Wave 0 baseline (18) plus Wave 1 product-truth fixes (3):

| Journey | Runtime proof upgraded this pass |
|---------|----------------------------------|
| `wallet-payment` | `WalletControllerTest.java` + `e2e/wallet-payment-flow.spec.ts` |
| `coverage-enrollment` | `CoverageControllerTest.java` + `useCoverage.test.ts` + `e2e/coverage-enroll-flow.spec.ts` |
| `document-upload` | `ClinicalDocumentsControllerTest.uploadAndIndex` + `documents/page.test.tsx` |

Remaining 18 Wave 0 journeys unchanged (encounter, lab, blood, wellness, health-id, citizen-monitoring, etc.).

## Preview walk notes (2026-06-07)

| Check | Result |
|-------|--------|
| `/health/version` on preview | **`c5f8ff43`** — stale vs repo `HEAD` |
| `/wallet/send`, `/coverage/enroll` | HTTP 307 (Keycloak auth redirect without session) |
| Browser MCP | Unavailable in agent session |
| Recommendation | Deploy after VM gates PASS + explicit user authorization |

## Next honest targets (not in scope of downgrade)

1. Replace remaining source-only golden threads with BFF IT or Playwright for Tier B journeys before re-promotion.
2. Remove payer-ops stubs before re-adding `payment-billing-claim`.
3. Wire RTC or document journey as permanently partial before telemedicine re-promotion.
