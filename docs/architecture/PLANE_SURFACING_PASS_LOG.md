# Plane surfacing pass log

> Tracks plane-by-plane full surfacing execution. Regenerate ledger: `node scripts/architecture/generate-plane-capability-ledger.mjs`

## Pass summary (2026-06-05)

| Plane | Evidence | Maturity delta |
|-------|----------|----------------|
| **Trust** | `TrustGovernanceStrip` on `/settings/security`; registration role assignment hardened in BFF | Partial → deeper operator+user |
| **Registry** | `WorkspaceOpsHub` on `/operations/facility-operations`; UBOMI verify tab product UI | Partial / Not Wired → Partial+ |
| **Clinical** | `/clinical/control-tower` live facility ops; `/home/results` citizen lab results | Partial → deeper |
| **Data** | `/admin/data-governance` export eval success card (no QRP dump) | Partial → deeper |
| **Integration** | `/operations/workflows` `WorkflowInstancesTable` with select-for-transition | Partial → deeper |
| **Experience** | `SUPER_ADMIN` visibility parity; `WorkplaceSelectionHub` facility entry | Partial → deeper |
| **Enterprise** | `/finance/payer-ops` + `/finance/workspace` typed entity panels (intent, attempts, invoices) | Partial → deeper |

## Proper UI Surfacing pass (2026-06-07 batch 3)

| Deliverable | Status |
|-------------|--------|
| P0 QRP clearance | Live — marketplace/ops, erp/hr, erp/gl, finance/reconciliation, registry/intake, payer-ops fraud |
| Shared `JsonApiDataTable` | Live — product tables from BFF payloads |
| Registration golden path | Partial+ — readiness probe, assurance BFF wire, E2E `citizen-signup-flow.spec.ts` |
| Honest scorecard | `docs/frontend/UI_SURFACING_MATURITY_SCORECARD.md` |

## Proper UI Surfacing pass (2026-06-05 batch 2)

| Deliverable | Status |
|-------------|--------|
| Hotspot register (`UI_SURFACING_HOTSPOT_REGISTER.md`) | Live |
| SUPER_ADMIN / platform override role gating (UI + BFF) | Live |
| Citizen registration error semantics + role rollback | Live |
| Finance payer-ops / workspace product tables | Partial+ (fraud flags QRP remains) |
| Control tower + workplace hub | Live (prior pass, verified) |

## Golden path proofs

- Citizen results: `/home/results` → `useCitizenHealthSummary` → `/internal/v1/citizen/health-summary` → PCT/BFF
- Trust governance: `/settings/security` → `useTrustAuditLogs` → `/internal/v1/admin/trust/audit`
- Workspace billing: `BillingPanel` → `useCoverageClaimsList` → `/internal/v1/coverage/claims`
- Workspace stock: `StockManagementPanel` → `useInventoryOnHand` → `/internal/v1/inventory/on-hand`
- Workspace HR: `HRShiftsPanel` → `useStaffingRosterWeek` → `/internal/v1/staffing/roster-week`
- Payer ops reviews: `/finance/payer-ops` → `usePayerOpsReviews` → approve/reject mutations
- Workflows: `/operations/workflows` → `useWorkflowInstances` → transition console

## Remaining (honest partial)

- `/marketplace/orders/[id]` — last P0 thin shell (5 QRP)
- ~35 P1 mixed routes — see hotspot register
- Mobile citizen onboarding screens — web journey proven; see `docs/audits/MOBILE_WEB_ONBOARDING_PARITY.md`
- Full-boot wave-0 only (13/84 microservices) — not full vNext runtime
- Full 87-service endpoint-level surfacing matrix rows still majority Partial

## Gates

```bash
node scripts/architecture/generate-plane-capability-ledger.mjs
node scripts/frontend/generate-parity-docs.mjs
bash scripts/guard/check-backend-frontend-parity.sh
cd ui/one-ui-shell && NO_STUB_STRICT=1 npm run test:no-stubs
```
