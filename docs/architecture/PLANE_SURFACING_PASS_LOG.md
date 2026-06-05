# Plane surfacing pass log

> Tracks plane-by-plane full surfacing execution. Regenerate ledger: `node scripts/architecture/generate-plane-capability-ledger.mjs`

## Pass summary (2026-06-05)

| Plane | Evidence | Maturity delta |
|-------|----------|----------------|
| **Trust** | `TrustGovernanceStrip` on `/settings/security` — policies, break-glass, devices, audit chain + recent decisions | Partial → deeper operator+user |
| **Registry** | `WorkspaceOpsHub` mounted on `/operations/facility-operations`; UBOMI verify tab product UI | Partial / Not Wired → Partial+ |
| **Clinical** | `/home/results` citizen lab results via `useCitizenHealthSummary` | New live citizen journey |
| **Data** | `/admin/data-governance` export eval success card (no QRP dump) | Partial → deeper |
| **Integration** | `/operations/workflows` `WorkflowInstancesTable` with select-for-transition | Partial → deeper |
| **Experience** | Workspace ops hub wiring; shared `DomainCollectionTable` | Partial → deeper |
| **Enterprise** | Workspace-ops billing/stock/HR live toggles; payer-ops reviews table | Partial → deeper |

## Golden path proofs

- Citizen results: `/home/results` → `useCitizenHealthSummary` → `/internal/v1/citizen/health-summary` → PCT/BFF
- Trust governance: `/settings/security` → `useTrustAuditLogs` → `/internal/v1/admin/trust/audit`
- Workspace billing: `BillingPanel` → `useCoverageClaimsList` → `/internal/v1/coverage/claims`
- Workspace stock: `StockManagementPanel` → `useInventoryOnHand` → `/internal/v1/inventory/on-hand`
- Workspace HR: `HRShiftsPanel` → `useStaffingRosterWeek` → `/internal/v1/staffing/roster-week`
- Payer ops reviews: `/finance/payer-ops` → `usePayerOpsReviews` → approve/reject mutations
- Workflows: `/operations/workflows` → `useWorkflowInstances` → transition console

## Remaining (honest partial)

- Finance payer-ops intent/receipts/attempts panels still use `QueryResultPanel` for operator JSON commands
- Marketplace `/marketplace/ops` QRP density
- ERP HR/GL QRP density
- Mobile parity per capability (Phase F)
- Full 87-service endpoint-level surfacing matrix rows still majority Partial

## Gates

```bash
node scripts/architecture/generate-plane-capability-ledger.mjs
node scripts/frontend/generate-parity-docs.mjs
bash scripts/guard/check-backend-frontend-parity.sh
cd ui/one-ui-shell && NO_STUB_STRICT=1 npm run test:no-stubs
```
