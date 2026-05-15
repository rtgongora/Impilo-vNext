# Clinical To Enterprise Financial Flow Map

Date: 2026-05-15

| Clinical trigger | Clinical owner | Enterprise owner | Event/API linkage | Status | Blocker |
|---|---|---|---|---|---|
| encounter-to-charge | `pct-service` | `costing-engine-service` | encounter context consumed by COSTA billing lifecycle via BFF finance routes | partial | full event-level charge completeness proof pending |
| orders-to-charge | `oros-service` | `costing-engine-service` | order/result context influences billing/claims bundles | partial | explicit order-to-bill trace IDs not uniformly surfaced |
| prescription-to-charge | `pharmacy-service` | `costing-engine-service` + `mushex-service` | prescription dispense linked to bill/payment flows | partial | remittance reconciliation depth still partial |
| procedure-to-charge | procedure/encounter owners | `costing-engine-service` | procedure context contributes tariffed charge paths | partial | procedure aggregate boundary ADR still open |
| inpatient bed-day to charge | `inpatient-service` | `costing-engine-service` | admission/bed-day context contributes inpatient charging | partial | ward-round and nursing-plan depth still partial |
| consumables-to-charge | `inventory-service` + clinical consumers | `costing-engine-service` / `procurement-service` touchpoints | stock movement contributes cost and replenishment flows | partial | inventory-to-finance reconciliation evidence incomplete |
| virtual consult-to-charge | `pct-service` teleconsult context | `costing-engine-service` + `coverage-service` | teleconsult outcomes feed billing/coverage checks | partial | real-time transport intentionally unavailable |
| referral-to-claim | `pct-service` referral workflows | `coverage-service` + `mushex-service` | referral outcome contributes claim lifecycle and remittance path | partial | adjudication and remittance convergence remains split across services |
