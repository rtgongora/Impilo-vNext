# Money Rails — Runtime Proof (2026-07-13)

Runtime proof for the **Stage 2 tech-ready money rails** wave (MR1–MR5). Rig:
`scripts/runtime-proof/money-rails-journeys.sh`. Result: **PASS=15 FAIL=0**.

Boots mushex + mushe-wallet on scratch infra with
`MUSHEX_SANDBOX_APPLY_SIMULATION_OUTCOME=false` — no fabricated settlement; the
payer pays from a real wallet.

| Journey | Proves |
|---|---|
| **JV-1** | tier-1 vendor payout LIVE: a marketplace-shaped intent (metadata `provider_id=vendor:X`, `payee_amount`=share) paid from a real payer wallet → run settlement → release-payouts → the vendor's MERCHANT wallet is credited **exactly the payee share (42.50), not the gross (43.14)**; the wallet payout batch is COMPLETED |
| **JD-1** | bank→wallet cash-in: request deposit → PENDING intent + reference code → a bank statement line quoting the code (matching amount) → MATCHED → deposit CONFIRMED → wallet credited exactly |
| **JD-2** | an amount-mismatch statement line stays UNMATCHED and the wallet is untouched (never force-credited) |
| **JH-1** | adapter readiness reports the internal WALLET rail READY_LIVE and the external CARD_GATEWAY (Paynow) rail NOT live — the exact signal the BFF uses to disable external payment methods when no aggregator credentials are configured |

## What this wave built (tech-ready, credential-gated)

Everything here is production technology; the external rails light up on
credentials/agreements, which was the standing assumption ("assume all
regulatory approvals will be obtained when needed").

- **MR1 — vendor payouts LIVE.** `SettlementService` aggregates by payee (share,
  not gross) and `releasePayouts` actually disburses through a `DisbursementRail`
  seam; `WalletDisbursementRail` credits merchant wallets today, external B2C
  rails drop in behind the same seam. Unregistered rails stay PENDING (fail-closed).
- **MR2 — collection-intent cash-in.** External deposits create PENDING intents
  that credit only on confirmed arrival; the BFF funding routes (previously dead)
  are wired self-wallet-only; the deposit UI shows the honest PENDING state.
- **MR3 — Paynow aggregator rail.** Real wire protocol (SHA-512 hash, express
  USSD push + browser checkout, hash-verified poll + result post) covering
  EcoCash/OneMoney/InnBucks/ZimSwitch/cards behind ONE integration; `liveCapable()`
  gated on real credentials; `AttemptStatusPoller` backstops lost webhooks.
- **MR4 — bank→wallet matching.** Statement lines matched to pending deposits by
  reference code + exact amount; unmatched lines surfaced, never force-credited.
- **MR5 — payment-method honesty.** BFF enablement driven by adapter readiness,
  not a hardcoded `true`.

## Not proven here (external dependency, honest)

The Paynow LIVE pay-in leg is unit-proven at the protocol level
(`PaynowProtocolTest`) but not driven end-to-end in this rig — it needs a Paynow
provider sandbox (merchant credentials). Once credentials exist, the same adapter
runs unchanged. NOT deployed.
