# Enterprise Transaction State Machine

Date: 2026-05-15

## Payment

`initiated -> pending_authorisation -> authorised -> captured -> reconciled -> closed`

Exceptional transitions:
- `initiated|pending_authorisation -> failed|cancelled`
- `captured -> reversed|refunded -> reconciled`

## Claim

`draft -> submitted -> accepted -> adjudicated -> paid -> remitted -> reconciled -> closed`

Exceptional transitions:
- `submitted -> rejected|queried`
- `queried -> submitted`

## Invoice/Bill

`draft -> issued -> partially_paid -> paid -> reconciled`

Exceptional transitions:
- `draft|issued -> cancelled`
- `issued|partially_paid -> written_off`

## Procurement

`requested -> approved -> ordered -> partially_received -> received -> invoiced -> paid -> closed`

Exceptional transitions:
- `requested|approved|ordered -> cancelled`

## Payroll

`draft -> approved -> processed -> paid -> posted_to_ledger -> reconciled`

Exceptional transitions:
- `draft|approved -> cancelled`

## Marketplace Order

`cart -> submitted -> pending_payment -> paid -> fulfilling -> delivered -> closed`

Exceptional transitions:
- `submitted|pending_payment|fulfilling -> cancelled`
- `paid|delivered -> refunded`

## Current Implementation Alignment

- `mushex-service`, `costing-engine-service`, `coverage-service`, and `msika-flow-service` expose meaningful state progression APIs.
- `procurement-service`, `hr-payroll-service`, and `general-ledger-service` have bounded state surfaces but still require deeper runtime evidence and tests.
- BFF now fail-closes on enterprise upstream failures in high-risk paths remediated in this pass; no synthetic order or billing success remains on touched routes.
