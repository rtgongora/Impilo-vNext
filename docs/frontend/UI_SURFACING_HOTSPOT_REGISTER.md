# UI Surfacing Hotspot Register

> Generated: 2026-06-05. Regenerate: `node scripts/frontend/generate-ui-surfacing-hotspot-register.mjs`

Surfaces that need meaningful product UI (not route parity alone). Aligned with [GAP_CLOSURE_RULES.md](./GAP_CLOSURE_RULES.md).

## Summary

| Metric | Count |
|--------|-------|
| Pages scanned | 444 |
| Hotspots (P0/P1 or QRP) | 42 |
| P0 thin shells (5+ QRP) | 6 |
| P1 mixed/fixture | 36 |

## Priority legend

| Priority | Meaning |
|----------|---------|
| P0-thin-shell | 5+ QueryResultPanel instances — operator console, not product UI |
| P1-mixed | 1–4 QRP or fixture/mock risk — extend in place |
| P2-live-candidate | Hooks present, no QRP — verify chain + doctrine |
| P3-review | Manual review |

## Hotspot table

| route | qrpCount | priority | file |
|-------|----------|----------|------|
| /marketplace/ops | 13 | P0-thin-shell | `ui/one-ui-shell/src/app/marketplace/ops/page.tsx` |
| /erp/hr | 10 | P0-thin-shell | `ui/one-ui-shell/src/app/erp/hr/page.tsx` |
| /erp/gl | 8 | P0-thin-shell | `ui/one-ui-shell/src/app/erp/gl/page.tsx` |
| /finance/reconciliation | 6 | P0-thin-shell | `ui/one-ui-shell/src/app/finance/reconciliation/page.tsx` |
| /marketplace/orders/:id | 5 | P0-thin-shell | `ui/one-ui-shell/src/app/marketplace/orders/[id]/page.tsx` |
| /registry/intake | 5 | P0-thin-shell | `ui/one-ui-shell/src/app/registry/intake/page.tsx` |
| /admin/integration-status | 4 | P1-mixed | `ui/one-ui-shell/src/app/admin/integration-status/page.tsx` |
| /coverage | 4 | P1-mixed | `ui/one-ui-shell/src/app/coverage/page.tsx` |
| /erp/assets | 4 | P1-mixed | `ui/one-ui-shell/src/app/erp/assets/page.tsx` |
| /finance/costa | 4 | P1-mixed | `ui/one-ui-shell/src/app/finance/costa/page.tsx` |
| /finance/refunds | 4 | P1-mixed | `ui/one-ui-shell/src/app/finance/refunds/page.tsx` |
| /finance/settlements | 4 | P1-mixed | `ui/one-ui-shell/src/app/finance/settlements/page.tsx` |
| /marketplace/pickup | 4 | P1-mixed | `ui/one-ui-shell/src/app/marketplace/pickup/page.tsx` |
| /registry/provider-council/self-service | 4 | P1-mixed | `ui/one-ui-shell/src/app/registry/provider-council/self-service/page.tsx` |
| /access | 3 | P1-mixed | `ui/one-ui-shell/src/app/access/page.tsx` |
| /admin/clinical-curation | 3 | P1-mixed | `ui/one-ui-shell/src/app/admin/clinical-curation/page.tsx` |
| /ehr/:patientId/ips | 3 | P1-mixed | `ui/one-ui-shell/src/app/ehr/[patientId]/ips/page.tsx` |
| /ehr/:patientId/preferences/communications | 3 | P1-mixed | `ui/one-ui-shell/src/app/ehr/[patientId]/preferences/communications/page.tsx` |
| /finance/ledger | 3 | P1-mixed | `ui/one-ui-shell/src/app/finance/ledger/page.tsx` |
| /finance/my-account | 3 | P1-mixed | `ui/one-ui-shell/src/app/finance/my-account/page.tsx` |
| /finance/payer-claims/:claimId | 3 | P1-mixed | `ui/one-ui-shell/src/app/finance/payer-claims/[claimId]/page.tsx` |
| /finance/payer-ops | 3 | P1-mixed | `ui/one-ui-shell/src/app/finance/payer-ops/page.tsx` |
| /marketplace/catalog | 3 | P1-mixed | `ui/one-ui-shell/src/app/marketplace/catalog/page.tsx` |
| /marketplace/substitutions | 3 | P1-mixed | `ui/one-ui-shell/src/app/marketplace/substitutions/page.tsx` |
| /marketplace/vendor/orders | 3 | P1-mixed | `ui/one-ui-shell/src/app/marketplace/vendor/orders/page.tsx` |
| /operations/vito/registry-admin | 3 | P1-mixed | `ui/one-ui-shell/src/app/operations/vito/registry-admin/page.tsx` |
| /registry/facility-lifecycle | 3 | P1-mixed | `ui/one-ui-shell/src/app/registry/facility-lifecycle/page.tsx` |
| /registry/mvumo | 3 | P1-mixed | `ui/one-ui-shell/src/app/registry/mvumo/page.tsx` |
| /registry/provider-council/council-workspace | 3 | P1-mixed | `ui/one-ui-shell/src/app/registry/provider-council/council-workspace/page.tsx` |
| /registry/providers/:id | 3 | P1-mixed | `ui/one-ui-shell/src/app/registry/providers/[id]/page.tsx` |
| /registry/providers/new | 3 | P1-mixed | `ui/one-ui-shell/src/app/registry/providers/new/page.tsx` |
| /registry/terminology | 3 | P1-mixed | `ui/one-ui-shell/src/app/registry/terminology/page.tsx` |
| /registry/terminology/:id | 3 | P1-mixed | `ui/one-ui-shell/src/app/registry/terminology/[id]/page.tsx` |
| /reports/:id | 3 | P1-mixed | `ui/one-ui-shell/src/app/reports/[id]/page.tsx` |
| /wellness/connect | 3 | P1-mixed | `ui/one-ui-shell/src/app/wellness/connect/page.tsx` |
| /enterprise/fleet | 1 | P1-mixed | `ui/one-ui-shell/src/app/enterprise/fleet/page.tsx` |
| /client-journey | 0 | P1-fixture-risk | `ui/one-ui-shell/src/app/client-journey/page.tsx` |
| /operations/dispatch | 0 | P1-fixture-risk | `ui/one-ui-shell/src/app/operations/dispatch/page.tsx` |
| /operations/workflows | 0 | P1-fixture-risk | `ui/one-ui-shell/src/app/operations/workflows/page.tsx` |
| /platform-journey | 0 | P1-fixture-risk | `ui/one-ui-shell/src/app/platform-journey/page.tsx` |
| /provider-workspace | 0 | P1-fixture-risk | `ui/one-ui-shell/src/app/provider-workspace/page.tsx` |
| /wellness/routes | 0 | P1-fixture-risk | `ui/one-ui-shell/src/app/wellness/routes/page.tsx` |

## High-value flows (plan phase 3)

1. Workspace selection — facility context + operations hub
2. Registration — `/auth/register` chain
3. Control tower / queue — `/clinical/control-tower`, worklist
4. Finance ops — `/finance/payer-ops`, `/finance/workspace`

## Related

- [PLANE_CAPABILITY_LEDGER.md](../architecture/PLANE_CAPABILITY_LEDGER.md)
- [REMAINING_FRONTEND_GAPS.md](./REMAINING_FRONTEND_GAPS.md)
- [BACKEND_NOT_SURFACED_REGISTER.md](../audits/BACKEND_NOT_SURFACED_REGISTER.md)
