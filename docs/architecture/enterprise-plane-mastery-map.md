# Enterprise Plane Mastery Map

Date: 2026-05-15

## Enterprise Domains And Owners

| Domain | Primary owner(s) | Status | Notes |
|---|---|---|---|
| payments/remittances/reversals | `mushex-service` | implemented-or-partial | canonical payment orchestration |
| billing/charge lifecycle | `costing-engine-service` | implemented-or-partial | encounter-linked charging via COSTA |
| claims + adjudication touchpoints | `coverage-service` + `mushex-service` | partial | split responsibilities; must remain explicit |
| coverage/eligibility/preauth | `coverage-service` | implemented-or-partial | provider financing routes still maturing |
| costing/tariffs | `costing-engine-service` | implemented-or-partial | enterprise estimation and tariff basis |
| general ledger | `general-ledger-service` | partial | core APIs present, test depth limited |
| procurement-to-pay | `procurement-service` | partial | internal ERP APIs + BFF wiring, shallow UX |
| HR/payroll | `hr-payroll-service` | partial | internal ERP APIs + BFF wiring, shallow UX |
| marketplace operations | `msika-flow-service` | implemented-or-partial | order/fulfilment orchestration; BFF fail-close enforced |
| catalogue authority | `msika-service` + `product-registry-service` | implemented-or-partial | SoR for products/services only |
| reconciliation | `mushex-service` + `general-ledger-service` | partial | deeper cross-service runtime evidence pending |

## Lifecycle Flow Map

| Lifecycle flow | Primary enterprise path | Cross-plane dependency | Status | Blocker |
|---|---|---|---|---|
| encounter-to-charge | clinical event -> COSTA bill lifecycle -> enterprise settlement | `pct-service`, `oros-service`, `pharmacy-service`, `inpatient-service` | partial | deeper event-level charge completeness proof pending |
| pre-service eligibility/payment | coverage eligibility/preauth + COSTA estimate + MusheX payment intent | registry/trust context propagation | partial | full end-to-end gating policy proof pending |
| post-service billing | bill accumulation -> payment/claim -> remittance -> reconciliation | clinical completion and enterprise settlement dependencies | partial | long-tail route parity and reconciliation assertions |
| claims/remittance | coverage claim routing + mushex remittance ops | payer and trust governance dependencies | partial | adjudication status convergence not fully unified |
| marketplace fulfilment | catalogue select -> msika-flow order -> payment intent -> fulfilment | msika/product registry + mushex | partial | enterprise shell route parity drift |
| procurement-to-pay | procurement request -> approval -> PO/GRN/invoice -> payment + ledger | inventory + mushex + general-ledger | partial | no full runtime harness proof yet |
| payroll-to-ledger | payroll run -> payment/remittance -> ledger posting | workforce + ledger + finance | partial | limited test/evidence depth |
| reconciliation | payment vs bill/claim vs ledger consistency | data plane reporting/warehouse | partial | reconciled-state observability still incomplete |

## Cross-Plane Dependencies

- **Clinical:** chargeable work provenance from `pct-service`, `oros-service`, `pharmacy-service`, `inpatient-service`.
- **Registry:** identity/facility/catalogue context from `vito-service`, `varapi-service`, `tuso-service`, `msika-service`/`product-registry-service`.
- **Trust:** policy/authz/audit controls from TSHEPO services.
- **Data:** reporting and warehouse validation loops for enterprise reconciliation.
- **Integration:** adapters and hubs for payment rails, claims switching, and external settlement.
- **Experience:** `experience-bff` orchestration + `ui/experience` and `ui/one-ui-shell` enterprise surfaces.
