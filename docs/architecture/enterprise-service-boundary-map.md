# Enterprise Service Boundary Map

Date: 2026-05-15

## Canonical Boundaries

| Service | Owns | Must not own |
|---|---|---|
| `mushex-service` | payment orchestration, remittance, reversal/refund workflow, transaction state | clinical records, identity SoR, facility registry, consent/policy authority |
| `costing-engine-service` | costing, tariff models, charge derivation, bill lifecycle basis | clinical encounter SoR, payment ledger SoR |
| `coverage-service` | eligibility, plans, member coverage, preauth/utilisation rules, claim intake | patient/provider/facility identity SoR, final payment ledger SoR |
| `general-ledger-service` | accounting journals, posting controls, period/account controls, financial postings | clinical source data, direct claims adjudication ownership |
| `procurement-service` | requisition/PO/supplier/workflow states, goods receipt linkage | product catalogue SoR, clinical workflow ownership |
| `hr-payroll-service` | payroll workflow states, compensation processing, payroll events | provider identity SoR, financial ledger SoR |
| `msika-flow-service` | marketplace order and fulfilment orchestration | product catalogue SoR, payment ledger SoR |
| `msika-service` + `product-registry-service` | product/service catalogue authority | payment/claims/ledger ownership |
| `inventory-service` | stock quantities and movement events, procurement/costing touchpoints | product catalogue SoR, payment authority |
| `credential-verification-service` | credential verification evidence for claims/contracting | provider identity SoR (VARAPI remains SoR) |
| `experience-bff` | UI orchestration, typed error handling, trust header propagation | synthetic enterprise success, enterprise ledger/payment source-of-truth |

## Billing / Claims Split (Current)

- **Billing derivation and charge lifecycle:** `costing-engine-service`.
- **Coverage and member claim context:** `coverage-service`.
- **Payment/remittance operational rail:** `mushex-service`.
- **Financial posting and period controls:** `general-ledger-service`.
- **BFF role:** orchestration only; no synthetic fallback success.

This split remains valid for current architecture and must remain explicit in API contracts and readiness docs.
