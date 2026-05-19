# Enterprise Plane Endpoint Inventory

Date: 2026-05-15

## Core Enterprise Services

| Service | Contract file | Key route families |
|---|---|---|
| `mushex-service` | `contracts/openapi/mushex.openapi.yaml` | payer ops, payment attempts, claims, remittance, refunds, reconciliation, ledger |
| `costing-engine-service` | `contracts/openapi/costa.openapi.yaml` | billing lifecycle, tariffs, estimates, payment intents, claims packs |
| `coverage-service` | `contracts/openapi/coverage.openapi.yaml` | eligibility, plans, members, claims, preauth, remittances, provider contracts/networks |
| `general-ledger-service` | `contracts/openapi/general-ledger.openapi.yaml` | accounts, journals, periods, trial balance, financial statements |
| `procurement-service` | `contracts/openapi/procurement.openapi.yaml` | requisitions, PO, supplier, GRN, invoice |
| `hr-payroll-service` | `contracts/openapi/hr-payroll.openapi.yaml` | employee records, payroll runs, payslips, deductions |
| `msika-flow-service` | `contracts/openapi/msika-flow.openapi.yaml` | marketplace order/fulfilment/payment coordination |
| `product-registry-service` | `contracts/openapi/product-registry.openapi.yaml` | product/service registry APIs |
| `msika-service` | `contracts/openapi/msika-core.openapi.yaml` | catalogue/search/governance |

## Enterprise BFF Surfaces

| BFF route family | Controller |
|---|---|
| `/internal/v1/finance/*` | `FinanceController`, `FinanceBillingWorkspaceController`, `FinancialDocumentFinanceBffController`, `FinancialReportsBffController`, `PatientAccountFinanceBffController`, `PaymentPlanFinanceBffController`, `FinanceMushexPlatformController`, `PayerOpsController`, `PayerClaimsController`, `FinanceLedgerController`, `SettlementController`, `ReconciliationController`, `RefundOpsController` |
| `/internal/v1/coverage/*` | `CoverageController` |
| `/internal/v1/provider-contracts*`, `/internal/v1/provider-networks*` | `ProviderFinancingController` |
| `/internal/v1/marketplace/*` | `MarketplaceController` |
| `/internal/v1/commerce/*` | `CommerceFlowController`, `MarketplaceOpsController` |
| `/internal/v1/erp/gl/*` | `ErpGlBffController` |
| `/internal/v1/erp/procurement/*` | `ErpProcurementBffController` |
| `/internal/v1/erp/hr/*` | `ErpHrBffController` |
| `/internal/v1/wallet/*` | `WalletController` |
