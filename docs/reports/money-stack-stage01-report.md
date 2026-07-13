# Money Stack — Stage 0 + Stage 1 Remediation Report (2026-07-13)

Follow-through on the Mushe / MusheX / COSTA readiness audit (three-agent sweep
@ `100ed2d2b`). Verdict recap: **preview-ready, real-money NOT ready** — no PSP
rails, no cash-in rail, pay-confirm seam open, plus integrity/security gaps.
Stages 0 and 1 (everything with no external dependency) are now **built,
tested, and runtime-proven**. Stage 2 (PSP rails, cash-in, payouts, funder
remittance, licensed AHFOZ tariffs) remains gated on PO dependencies.

## Stage 0 — deploy-now hygiene (all landed)

| # | Fix | Where |
|---|---|---|
| 1 | Custodial wallet credit/debit restricted to platform-finance actor types (was: ANY mesh-trusted caller could mint balance) + 403 mapping + tests | mushex `WalletPlatformService` / `WalletPlatformController` |
| 2 | Blank/dev HMAC pepper refuses boot outside a declared sandbox posture (was: silent empty-key HMACs) | mushex `MushexSecurityStartupValidator` |
| 3 | `credential-payee-verification-enabled=false` (fail-open) refuses boot outside sandbox | same validator |
| 4 | Stub rails report `STUB_NOT_LIVE_CAPABLE`, never `READY_LIVE` (was: operator shown a "ready" rail the safety gate blocks) | mushex `AdapterReadinessService` |
| 5 | `disburseToBatch` returns per-item failures + `PARTIAL` payout status (was: silent under-disbursement) | mushex `MusheWalletAdapter` |
| 6 | `values-prod-money.yaml` pins every simulation flag OFF as an artifact (no production values profile existed) | `deploy/helm/impilo-vnext/` |

## Stage 1 — money integrity (all landed)

| # | Fix | Where |
|---|---|---|
| 1 | Money events retry (3×) then dead-letter to `costa.money.dlq`; DLQ persisted (`V021 costa_failed_money_events`) with list + replay ops endpoints (was: catch→ack permanently dropped settlements) | COSTA `CostaEventConsumer` (14 lanes), `MoneyDlqConsumer`, `FailedMoneyEventController` |
| 2 | Zero-price guardrail: lines priced to zero by ABSENCE (`pending_pricing`, V022) block finalize, `allowUnpriced` override for free lines (was: unmapped codes finalized \$0 bills silently) | COSTA `BillService` |
| 3 | **Pay-confirm seam CLOSED**: `POST /payment-intents/{id}/pay-from-wallet` + BFF wallet-pay routing through the money SoR; fail-clean, idempotent | mushex + experience-bff + `useCommerceFlow.ts` |
| 4 | `coverage.plans` consumed → `PENDING_TERMS` placeholder plan (terms NEVER invented; finance gets a work item instead of a counter surprise) | COSTA `CoveragePlanConsumer` |
| 5 | Refund matching requires refund-id or exact-amount correlation (was: "any PENDING refund" could mis-attribute) | COSTA `CostaEventConsumer` |
| 6 | `wallet-pay-journeys.sh` rig **15/15** + mushe-wallet money-invariant tests | see below |

## The flagship proof — first genuinely-real E2E payment

[reports/journeys/wallet-pay-proof-20260713/](../../reports/journeys/wallet-pay-proof-20260713/)
— **JW-1..3, 15/15 PASS** on six services with
`MUSHEX_SANDBOX_APPLY_SIMULATION_OUTCOME=false` and
`MSIKA_FLOW_PAYMENTS_SIMULATION_METADATA=false`: the intent is asserted NOT
auto-settled and reaches PAID **only** because MusheX debited the citizen's
CPID-keyed Mushe wallet; order PAID via Kafka; COSTA charge SETTLED; wallet
debited exactly the order total; insufficient balance fails clean; double-confirm
debits exactly once.

The rig caught **three real bugs** (all fixed): orders carried no patient
identity (`mf_orders.patient_cpid` always empty); `MusheWalletAdapter` sent only
`X-Tenant-ID` (4th site of the v1.1 header defect family — and its debit now
uses a deterministic idempotency key so replays can never double-debit); a
receipt NPE on facility-less intents killed settlement *after* money left the
wallet.

## Gates

mushex **232/232** · costa **144/144** · msika-flow **72/72** · experience-bff
**925/925** · mushe-wallet **11/11** · shell tsc clean · wallet rig **15/15**.

## Honest remaining (Stage 2 — PO-gated)

No external PSP rails (EcoCash/bank/card adapters remain stubs behind the
safety gate); wallet funding is still ledger-only (no cash-in rail);
vendor/provider payouts recorded-only; claims have no funder remittance return
leg; licensed AHFOZ tariffs + SI 78 amounts await governed import;
USD/ZWL currency decision open. NOT deployed.
