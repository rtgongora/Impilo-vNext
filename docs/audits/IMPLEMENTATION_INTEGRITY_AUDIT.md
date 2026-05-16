# Implementation Integrity Audit

Date: 2026-05-16  
Branch: `claude/staging-ux-orchestration-remediation-Yypyl`  
Scope: Web (`ui/one-ui-shell`), mobile (`apps/mobile/*`), contracts (`contracts/*`), BFF (`services/experience-bff`), and service surfacing.

## Audit Method

- Route/screen inventory scan across web and mobile.
- Mock/stub/placeholder marker scan (`TODO`, `fixture`, `mock`, `placeholder`, empty handlers, `href="#"`).
- Contract-to-frontend and contract-to-BFF usage tracing.
- BFF endpoint-to-frontend caller tracing.
- Service capability-to-UI surface tracing.
- Branch commit visibility check (implemented features vs reachable UI).

## Severity Rules

- `CRITICAL`: Fake functionality represented as real in clinical/identity/payment/support/reporting.
- `HIGH`: Implemented backend capability not surfaced; dead-end actions; parity drift.
- `MEDIUM`: Partial wiring, weak states, unclear fixture boundaries.
- `LOW`: Documentation mismatch or minor placeholder.

## Top Findings

| ID | Severity | Area | Finding | Web status | Mobile status | Parity |
|---|---|---|---|---|---|---|
| IMP-001 | CRITICAL | Core Transaction | `/core-transaction`, `/client-journey`, `/provider-workspace`, `/platform-journey` render fixture data only while BFF core-transaction endpoints exist. | MOCK | MISSING | WEB_MOCK_MOBILE_MISSING |
| IMP-002 | HIGH | Nompilo | Command/handoff APIs exist in BFF, but primary shell experience remains mostly presentation-level or fixture-backed for doctrine surfaces. | PARTIAL/MOCK | MISSING/PARTIAL | WEB_REAL_MOBILE_MISSING |
| IMP-003 | HIGH | Mobile Citizen Clinical | Citizen `Conditions`, `Allergies`, `ProviderDiscovery` had TODO-only backend wiring patterns. | N/A | MOCK/PARTIAL | MOBILE_ONLY |
| IMP-004 | HIGH | Backend Not Surfaced | Workflow/dispatch BFF capabilities present but not surfaced in primary web/mobile UX. | MISSING | MISSING | BACKEND_ONLY |
| IMP-005 | MEDIUM | Route Registry | `/home/referrals` existed as route/page redirect path but was not in registry; route inventory drift risk. | PARTIAL | N/A | WEB_ONLY |
| IMP-006 | MEDIUM | Contract Drift | `contracts/core-transaction.ts` not used by doctrine UI; local duplicate transaction types in shell. | PARTIAL | MISSING | SHARED_CONTRACT_ONLY |
| IMP-007 | MEDIUM | Identifier Contract Drift | `health-os-identifiers.ts` and shared-ui local contracts diverge (`ActorType` mismatch). | PARTIAL | PARTIAL | UNKNOWN |

## Completed Remediations In This Cycle

| Fix ID | Status | Files |
|---|---|---|
| FIX-001 | DONE | Added explicit fixture honesty banners to doctrine pages using `FeatureMaturityBadge` (`ui/one-ui-shell/src/app/core-transaction/page.tsx`, `client-journey/page.tsx`, `provider-workspace/page.tsx`, `platform-journey/page.tsx`). |
| FIX-002 | DONE | Added route registry entry for `/home/referrals`, updated expected route count (`ui/one-ui-shell/src/lib/routes.ts`). |
| FIX-003 | DONE | Added web reality-label component `ui/one-ui-shell/src/components/FeatureMaturityBadge.tsx`. |
| FIX-004 | DONE | Added mobile reality-label component `apps/mobile/packages/mobile-design-system/src/components/FeatureMaturityBadge.tsx` and export. |
| FIX-005 | DONE | Added explicit not-wired honesty states on mobile TODO sections (`ConditionsSection`, `AllergiesSection`, `ProviderDiscoveryScreen`). |
| FIX-006 | DONE | Added integrity scan utility `scripts/audit-ui-integrity.ts`. |

## Remaining High-Priority Gaps

1. Replace doctrine fixture pages with live BFF hooks (`/internal/v1/core-transactions/*`) and preserve fixture mode as explicit dev fallback.
2. Wire mobile provider messaging package endpoint mismatch (`/channels/messages` vs mobile provider messaging routes) to one canonical BFF path.
3. Surface workflow/dispatch capabilities (or explicitly document as admin-only backend readiness).
4. Converge shared contract usage for core transaction and identity enums.

## Related Detailed Audit Documents

- `docs/audits/FRONTEND_ROUTE_INVENTORY.md`
- `docs/audits/WEB_MOBILE_FRONTEND_INVENTORY.md`
- `docs/audits/WEB_MOBILE_PARITY_AUDIT.md`
- `docs/audits/MOBILE_EXPERIENCE_REALITY_CHECK.md`
- `docs/audits/COMPONENT_INTEGRITY_AUDIT.md`
- `docs/audits/CONTRACT_USAGE_AUDIT.md`
- `docs/audits/BFF_API_WIRING_AUDIT.md`
- `docs/audits/BACKEND_CAPABILITY_SURFACE_MAP.md`
- `docs/audits/COMMITTED_FEATURE_VISIBILITY_AUDIT.md`
- `docs/audits/MOCKS_STUBS_PLACEHOLDERS_REGISTER.md`
- `docs/audits/FLOATING_FRONTEND_REGISTER.md`
- `docs/audits/BACKEND_NOT_SURFACED_REGISTER.md`
- `docs/audits/NOMPILO_REALITY_CHECK.md`
- `docs/audits/CORE_TRANSACTION_UI_WIRING_AUDIT.md`
- `docs/audits/PAYMENT_CLAIMS_COSTING_REALITY_CHECK.md`
- `docs/audits/REPORTING_INTELLIGENCE_REALITY_CHECK.md`
- `docs/audits/PROVIDER_WORKSPACE_REALITY_CHECK.md`
- `docs/audits/CLIENT_MARKETPLACE_WELLNESS_REALITY_CHECK.md`
- `docs/audits/SUPPORT_FEEDBACK_REALITY_CHECK.md`
- `docs/audits/REMEDIATION_SUMMARY.md`
