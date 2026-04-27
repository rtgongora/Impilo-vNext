# COSTA / MusheX — costing, billing timing, and settlement

## Core separation

1. **Costing is continuous** and may run **before, during, and after** care. Cost events and estimates can exist while no invoice has been issued.
2. **Billing is not assumed to be post-service only.** Invoices and payment requests may be raised pre-service, at point of care, post-service, on a period, by episode or package, via claims, or deferred.
3. **Settlement** may align with any billing phase: pre-service (e.g. deposit), partial during care, post-service finalisation, claim adjudication, deferred promise-to-pay, exemption/subsidy with no patient payment, or mixed partial settlement.

## COSTA responsibilities

- **Prospective and retrospective billing**: COSTA applies tariffs and rules to produce estimates, decisions, invoices, and claim packs regardless of whether the clinical moment has passed.
- **Service access gate**: `ServiceAccessDecision` records whether care may proceed without payment, requires payment/deposit/authorisation, is covered/exempt, allows deferred payment, or is blocked — including emergency override with captured reason.
- **Final bill reconciliation**: A final invoice or discharge financial closure reconciles pre-service payments, deposits, point-of-care charges, exemptions, claims, subsidies, waivers, and outstanding balances.

## MusheX responsibilities

MusheX carries payment intent lifecycles and rails semantics: pre-service payment, deposit, authorisation hold, co-pay, partial payment, payer authorisation, wallet debit, claim submission handoff, deferred payment tracking, refund, reversal, credit note, receipt, and settlement acknowledgement. **Intent status** remains the guarded state machine; **`intent_type`** (`PaymentIntentType`) classifies the business intent for reporting and policy.

## Enumerations (canonical names)

- **BillingTimingMode**: `PRE_SERVICE`, `POINT_OF_CARE`, `POST_SERVICE`, `PERIODIC`, `EPISODE_BASED`, `PACKAGE_BASED`, `CLAIM_BASED`, `DEFERRED`.
- **CostaInvoiceType**: `PRE_SERVICE_INVOICE`, `DEPOSIT_INVOICE`, `POINT_OF_CARE_INVOICE`, `FINAL_INVOICE`, `CLAIM_INVOICE`, `PACKAGE_INVOICE`, `PERIODIC_INVOICE`, `DEFERRED_PAYMENT_INVOICE`, `REVERSAL_INVOICE`, `REFUND_CREDIT_NOTE`.
- **PaymentIntentType** (MusheX): `PRE_SERVICE_PAYMENT`, `DEPOSIT`, `AUTHORISATION_HOLD`, `POINT_OF_CARE_PAYMENT`, `FINAL_PAYMENT`, `PARTIAL_PAYMENT`, `CO_PAYMENT`, `CLAIM_SUBMISSION`, `WALLET_DEBIT`, `REMITTANCE`, `REFUND`, `REVERSAL`, `DEFERRED_PAYMENT_PROMISE`.

## Charging rules JSON

Each rule object inside `costa_charging_rulesets.rules` may optionally include `billing_timing_mode`. The ruleset may set `default_billing_timing_mode` for tenant-wide defaults.

## Acceptance criteria (summary)

Billing is never assumed to be only after discharge; COSTA and MusheX cooperate across pre-service, point-of-care, post-service, claim-based, package, periodic, and deferred paths while costing continues independently until policies merge into financial closure.
