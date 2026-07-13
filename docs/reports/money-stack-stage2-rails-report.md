# Money Stack — Stage 2 Rails (tech-ready) Report (2026-07-13)

Directive: *"Let's build a ready technology and assume that all regulatory
approvals will be obtained when needed."* This wave builds the production
technology for real money in and out across Zimbabwe's actual rail landscape —
not just EcoCash — behind MusheX's gateway-neutrality model. External rails are
credential-gated: the code is real and tested; a rail lights up when its
merchant agreement/credentials arrive, with no further code change.

## What landed

| # | Capability | Where |
|---|---|---|
| **MR1** | **Vendor payouts LIVE** — settlement aggregates by payee (share, not gross); `releasePayouts` disburses via a `DisbursementRail` seam; `WalletDisbursementRail` credits merchant wallets today; unregistered external rails stay PENDING (fail-closed) | mushex `SettlementService`, `service/disbursement/*`, `AdapterType.WALLET`; msika-flow `payee_amount` metadata |
| **MR2** | **Collection-intent cash-in** — external deposits credit only on confirmed arrival (V003 deposit_intents, quotable reference code); cashier cash immediate + trail; BFF funding routes (were dead) wired self-wallet-only; deposit UI shows honest PENDING | mushe-wallet `FundingService`/`FundingController`; bff `FundingBffController`; shell deposit page |
| **MR3** | **Paynow aggregator rail** — one integration = EcoCash/OneMoney/InnBucks/ZimSwitch/Visa/Mastercard. Real wire protocol (SHA-512 hash, express USSD push + browser checkout, hash-verified poll + result post); `liveCapable()` gated on credentials; `AttemptStatusPoller` backstops lost webhooks; refunds fail honestly | mushex `integration/paynow/*`, `PaynowGatewayAdapter`, `PaynowResultController`, `AttemptStatusPoller` |
| **MR4** | **Bank→wallet cash-in** — ZIPIT/bank statement lines matched to pending deposits by reference code + exact amount; unmatched surfaced, never force-credited | mushe-wallet `StatementMatchingService`/`StatementController` |
| **MR5** | **Payment-method honesty** — BFF enablement driven by real adapter readiness, not hardcoded `true` | bff `WalletController.getPaymentMethods` |

## The neutrality principle, made real

Nothing upstream of a rail changed across this wave. A pay-in rail is one
`PaymentRailAdapter` (Paynow registered as CARD_GATEWAY via `@ConditionalOnProperty`);
a payout rail is one `DisbursementRail`. That is why "integrate all" is N adapters
against one proven seam, not a rewrite:

- **Pay-in**: the Paynow aggregator covers mobile money + ZimSwitch + cards
  through one agreement. Direct per-rail adapters (e.g. EcoCash's own API) can
  later supersede the aggregator route for a high-volume method without touching
  anything upstream.
- **Cash-out / payouts**: `WalletDisbursementRail` is live; a ZIPIT B2C / EcoCash
  B2C bulk-payment rail drops in behind `releasePayouts`'s batch seam.
- **Bank→wallet**: statement-reference matching is live now (needs only a
  collection account + statement feed); ZimSwitch pull and aggregator "pay by
  bank" arrive with the Paynow agreement.
- **International/diaspora**: card acquiring is the Paynow adapter; remittance
  operators (Mukuru/WorldRemit) and the Mushe card as a ZimSwitch-scheme card
  are the same seam, later phases.

## Proof + gates

`scripts/runtime-proof/money-rails-journeys.sh` — **15/15** (vendor payout
LIVE with payee-share correctness, bank→wallet cash-in + mismatch-safety,
readiness honesty). Paynow LIVE pay-in is protocol-unit-proven (`PaynowProtocolTest`)
and rig-deferred pending a provider sandbox. Gates: mushex **241/241**,
mushe-wallet **22/22**, experience-bff **928** (1 pre-existing env-dependent
bed/wards test flagged for a hermetic fix, unrelated to this wave), msika-flow
**72/72**, shell tsc clean.

## What each rail still needs (the standing assumption)

Aggregator merchant agreement (Paynow — unlocks pay-in for all mobile money +
ZimSwitch + cards, and aggregator bank collection); a bank collection account +
statement feed (bank→wallet); ZIPIT/EcoCash B2C bulk-payment contracts
(cash-out); remittance-operator APIs (diaspora); ZimSwitch issuing programme
(Mushe card at national POS/ATM). Each is one adapter + one provider-sandbox rig.
**NOT deployed.**
