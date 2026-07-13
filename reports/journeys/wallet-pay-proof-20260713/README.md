# Wallet Pay-Confirm Seam — Runtime Proof (2026-07-13)

Runtime proof for **Stage 1 money integrity — the closed pay-confirm seam**.
Rig: `scripts/runtime-proof/wallet-pay-journeys.sh`.

## What this proves

**The estate's first genuinely-real end-to-end payment.** Six services on
scratch infra (msika + msika-flow + mushex + mushe-wallet + costa +
experience-bff) with **`MUSHEX_SANDBOX_APPLY_SIMULATION_OUTCOME=false`** and
**`MSIKA_FLOW_PAYMENTS_SIMULATION_METADATA=false`** — no fabricated
settlement anywhere. The MusheX intent is asserted **still CREATED** after
creation (no auto-settle), and it reaches PAID only because MusheX debited the
citizen's CPID-keyed Mushe wallet through the new
`POST /payment-intents/{id}/pay-from-wallet` leg.

Result: **PASS=15 FAIL=0** (see `journal.txt`).

| Journey | Proves |
|---|---|
| **JW-1** | listing → cart → checkout → price → pay mints a REAL intent (stays CREATED) → wallet created+credited → BFF `/internal/v1/wallet/pay` (reference = intent) routes through the money SoR → **intent PAID from the REAL debit** → order PAID via `mushex.payment.status.changed` → COSTA charge SETTLED → wallet debited **exactly** the order total |
| **JW-2** | insufficient balance → confirm fails clean (503), intent NOT paid, order not PAID, balance untouched |
| **JW-3** | double-confirm → idempotent (already-PAID short-circuit), wallet debited exactly once |

Flag honesty: `MUSHEX_SANDBOX_ENABLED=true` and
`CREDENTIAL_PAYEE_VERIFICATION_ENABLED=false` remain ONLY because the
credential-verification sidecar is not part of this rig and mushex fail-closes
intent creation without it. Neither flag can settle an intent.

## Real bugs this rig caught (all fixed)

1. **Orders had no patient identity** — `mf_orders.patient_cpid` was always
   empty (cart-open only accepted an optional query param nobody sends), so
   intent metadata had no `patient_cpid` and the wallet leg couldn't resolve
   the payer. Fix: a PATIENT actor IS the buyer — cart defaults the CPID from
   the actor id (`CartService.getOrCreateOpenCart`).
2. **MusheWalletAdapter sent only X-Tenant-ID** — mushe-wallet's v1.1 header
   guard rejected every call (`MISSING_REQUIRED_HEADER`), then
   `IDEMPOTENCY_KEY_REQUIRED`. The 4th confirmed site of the service-to-service
   header defect family. Fix: full v1.1 header set; the debit's
   `Idempotency-Key` is **deterministic per intent** so a replay can never
   double-debit (JW-3 relies on it).
3. **ReceiptService NPE on facility-less intents** — citizen marketplace
   intents carry no facility; the receipt NPE killed settlement **after** the
   wallet was already debited (money out, order unpaid — worst partial state).
   Fix: null-safe receipt summary.

## Not deployed

Rig-only proof; no environment was touched. The wallet remains ledger-funded
(no external cash-in rail yet — Stage 2).
