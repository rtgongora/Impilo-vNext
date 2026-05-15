# Enterprise Plane Dependency Map

Date: 2026-05-15

## Primary Enterprise Dependencies

| Enterprise owner | Depends on | Purpose |
|---|---|---|
| `mushex-service` | TSHEPO trust services, coverage/costa contexts, integration adapters | authz/audit context, coverage and billing settlement orchestration |
| `costing-engine-service` | clinical workload provenance (`pct`, `oros`, `pharmacy`, `inpatient`) | derive costs/charges from performed work |
| `coverage-service` | TSHEPO, registry context, payer integrations | eligibility/coverage policy checks and claim routing |
| `general-ledger-service` | enterprise posting sources (`mushex`, `costa`, payroll/procurement) | accounting posting and period controls |
| `procurement-service` | inventory and finance/ledger touchpoints | procurement-to-stock-to-pay flow |
| `hr-payroll-service` | workforce governance + finance/ledger touchpoints | payroll execution and accounting linkage |
| `msika-flow-service` | `msika-service`/`product-registry-service`, `mushex-service` | catalogue-informed orders and payment orchestration |
| `experience-bff` enterprise controllers | all enterprise services above | UI orchestration with typed fail-close behavior |

## Cross-Plane Constraints

- **Clinical plane** provides performed-work truth; enterprise plane prices/bills/reconciles it.
- **Registry plane** remains SoR for patient/provider/facility/terminology/catalog context.
- **Trust plane** remains SoR for policy/authz/audit authority.
- **Data plane** consumes enterprise outcomes for reporting/warehouse analytics and reconciliation dashboards.
