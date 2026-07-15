# Money-Stack UI Closure Wave (MU1–MU6) — 2026-07-15

Branch: `claude/staging-ux-orchestration-remediation-Yypyl`. **Not deployed** (awaits authorization).

## Why

The money stack was built backend-deep this session (Stage 0/1 integrity + Stage 2 rails:
vendor payouts, collection-intent cash-in, Paynow aggregator, bank→wallet matching), all
runtime-proven — but several capabilities landed backend-only or thin-UI. A grep-verified
audit found 4 features with real endpoints and **zero** UI consumers and 3 thin surfaces.
This wave closes them, reusing the shell's existing page/hook/route/BFF patterns.

## What shipped

| # | Gap closed | Surface |
|---|-----------|---------|
| MU1 | Citizen couldn't see or cancel deposits | `wallet/deposit` — "Your deposits" table + Cancel on PENDING; BFF `POST /funding/deposits/{id}/cancel` |
| MU2 | Merchant page showed only balance; `useTransactions` ignored its walletId | `wallet/merchant` — "Earnings — payouts received" (SETTLEMENT credits); BFF owner/merchant-owner-guarded `GET /wallets/{id}/transactions` |
| MU3 | `statements/match` rail had no BFF/UI | `finance/bank-reconciliation` — paste statement lines → MATCHED/UNMATCHED; BFF finance-gated `POST /finance/statement-match` |
| MU4 | COSTA dead-letter replay unproxied, no UI | `finance/failed-money-events` — list + Replay; BFF `GET/POST /finance/failed-money-events[/{id}/replay]` |
| MU5 | `pendingPricing` + `allowUnpriced` existed but unused | `finance/billing/[id]` — pending-pricing badges + governed override checkbox; BFF threads `allowUnpriced` param |
| MU6 | PENDING_TERMS plans had no controller at all | **new** COSTA `InsurancePlanController` (list + configure→ACTIVE); BFF passthrough; `finance/insurance-plans` page |

## Doctrine held

- **Never invent money amounts.** MU3 never force-credits (unmatched lines come back with a
  reason). MU5 default stays fail-closed — unpriced lines block finalize until an explicit
  override. MU6 requires finance-entered coverage/co-pay terms, range-checked 0–100, never derived;
  a PENDING_TERMS placeholder opens a **blank** form (its default 80/20 figures mean nothing yet).
- **Finance-ops gating.** MU3/MU4/MU6 BFF routes gate on X-Actor-Type
  (`FINANCE_OPS_ROLES` = FINANCE/FINANCE_ADMIN/FACILITY_FINANCE/PAYER_OPS/SYSTEM/SYSTEM_ADMIN → 403 FINANCE_ROLE_REQUIRED);
  `/finance/*` pages sit behind the finance folder role layout.
- **Reuse over new.** No new service; MU6's single new COSTA controller sits over the existing
  `InsurancePlanRepository`/`InsurancePlanEntity`. Vendor earnings reuse SETTLEMENT wallet txns.

## Gates (all green)

- Shell: `tsc --noEmit` clean; **44/44** vitest across the 6 changed/new page tests + `routes.test`
  (`EXPECTED_ROUTE_COUNT` 706→710: +MU3/MU4/MU6 routes; MU1/MU2/MU5 extend existing pages).
- COSTA: `InsurancePlanControllerTest` 6/6; regression `BillFinalizePendingPricingTest` 3/3,
  `MoneyEventDlqTest` 5/5 — `mvn -o test` BUILD SUCCESS.
- BFF: `FinanceControllerTest` 8/8 + `WalletControllerTest` 19/19 unaffected — BUILD SUCCESS.
- Endpoints consumed were already runtime-proven (money-rails 15/15, wallet-pay 15/15, HPA fee 16/16);
  this wave is UI wiring over proven APIs, so no new rig.

## Deferred (explicit)

- **MR3 Paynow express-checkout UX** (MSISDN capture + `browserUrl` redirect/return): the rail is
  credential-gated and can't be exercised E2E without a Paynow merchant sandbox. Deferred with the
  same rationale as the rail itself; everything internal is built.

## Commits

```
68d2694ca feat(ui): deposit history + cancel on the wallet deposit page            (MU1)
3c442dbee feat(bff): specific-wallet transactions route (owner/merchant-owner)     (MU2)
e515b4430 feat(ui): vendor earnings — payouts-received section on merchant page    (MU2)
a3615e40d feat(bff,ui): bank→wallet statement reconciliation surface               (MU3)
10ee3a742 feat(bff,ui): failed money-event dead-letter replay surface              (MU4)
5ee1cbf08 feat(bff,ui): governed zero-price finalize override on the bill          (MU5)
f46b6a0fe feat(costa,bff): insurance-plan terms governance endpoint                (MU6)
0d143096a feat(ui): insurance-plan terms configuration page                        (MU6)
```
