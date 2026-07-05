# COSTA / MusheX / Coverage — Repo-Grounded Capability Map

**Workstream**: MusheX / COSTA / Coverage pipeline (Fable Seven Pipeline Parallel Delivery Board, P1)
**Branch**: `cursor/e2e-costa-mushex-coverage` (base: `claude/web-session-anchor-nnnkf6` @ `98d43b1cd`)
**Date**: 2026-07-05
**Status legend**: ✅ real and wired · 🟡 partially wired · 🔶 honest stub/seam · ❌ missing · ⚠️ hazard

> Note: the board audit anchored at `d44bb6022`; the anchor has since advanced to
> `98d43b1cd` (W1/W2 telemedicine estate work — no finance files touched between the
> two tips, verified via `git diff --name-only d44bb6022..98d43b1cd`).

---

## 1. Financial journey — what exists per stage

| Journey stage | Status | Where |
|---|---|---|
| Clinical encounter / service event | ✅ | PCT encounter lifecycle (`pct-service`) |
| Billable items / charge capture | ✅ | `costa BillService.postLine` (cost engine + charging rules); clinical/order Kafka consumers auto-post lines (`costa CostaEventConsumer`) |
| COSTA pricing / tariff / invoice | ✅ | `TariffCostEngine` (tariff list → `TariffEntity` by msikaCode + effective window + facility/patient category), `PaymentIntegrationService.issueInvoice` |
| Coverage / funder adjudication | ✅ (internal) | `costa ExemptionEngine.split()` + `CoverageServiceClient` → `coverage-service` eligibility + claims; **no external medical-aid integration — adjudication is internal/scripted** |
| Covered + rejected + patient shortfall | 🟡 | `BillHeaderEntity.patientPayable/insurerPayable/subsidyPayable/writeOff` computed on `applyCoverage`; **no dedicated shortfall entity**, and the shortfall amount is never used to derive payment intents server-side |
| MusheX payment intent | ✅ | Synchronous handoff: `costa PaymentIntegrationService.createPaymentIntent` → `MushexPaymentIntentClient` → `mushex PaymentIntentService.createIntent` (idempotency key `COSTA_PAYMENT:{paymentId}`, unique `idempotencyKey` column) |
| Payment confirmation / receipt | ✅ (sandbox) | `mushex PaymentIntentService.recordPayment` → `ReceiptService` (idempotent per intent); status loop back via `mushex.payment.status.changed` → `costa PaymentIntegrationService.handlePaymentStatusUpdate` |
| Reconciliation / ledger / GL | ✅ | `mushex ReconciliationService` (statement import, 1% tolerance match), BFF triple-match compose; `general-ledger-service GlKafkaIntegrationListener` posts journals from `costa.bill.finalized` + `mushex.payment.status.changed` |
| Clinical billing visibility | ❌→✅ this workstream | Was: no billing panel in the EHR encounter journey (only aggregate `EncounterCareChainRail` counters behind FINANCE-role endpoints) |
| Patient-facing billing | 🟡 | `/finance/my-account`, `/citizen/wallet/payments` (one-ui-shell); mobile citizen `GET /internal/v1/mobile/citizen/costa/charges/pending`; **mobile provider finance routes referenced in app code do not exist in the BFF**. Fixed in this workstream: the wallet payments card queried COSTA with invalid status `INVOICED` (card was permanently unavailable) — now queries `FINAL` and the page shows the coverage split (coverage pays / patient shortfall / coverage status) with real COSTA statuses |
| Audit / reporting | ✅ | `costa /costa/v1/audit/bill/{id}`; outbox events on every lifecycle step; `FinancialReportsBffController` |

## 2. Classification detail

### Real and wired (preserve — do not replace)
- COSTA bill lifecycle: `services/costing-engine-service/.../service/BillService.java:87-393`
  (draft idempotent per encounter via `findActiveBillForEncounter` — DRAFT/ACCUMULATING dedup).
- Coverage split: `.../exemptions/ExemptionEngine.java:48-186` (exemption rules → subsidy/write-off;
  insurance plan → deductible/coverage%/copay/annual cap → insurerPayable; remainder → patientPayable).
- Claim handoff at finalize when `insurerPayable > 0`: `BillService.java:365-385` →
  `CoverageServiceClient.submitClaim` (idempotency header `costa-claim:{coverageId}:{encounterId}`).
- MusheX intents/receipts/recon: `mushex PaymentIntentService` (dedup by idempotency key at
  `PaymentIntentService.java:157-161`), `ReceiptService` (idempotent per intentId), `ReconciliationService`.
- GL journals: `general-ledger-service/.../GlKafkaIntegrationListener.java:78-108`.
- Finance UI (canonical): `ui/one-ui-shell` `/finance/**` routes incl. `finance/billing/[id]`
  (lifecycle actions + payment), `finance/costa/encounter/[encounterId]` timeline, payer-ops,
  settlements, reconciliation, refunds, ledger.

### Partially wired
- **Encounter→bill trigger**: exists only in BFF mobile close
  (`MobileEncounterController.java:131-148`), mobile discharge (`MobileDischargeController.java:62-78`,
  draft-only) and teleconsult complete (`TeleconsultController.java:1596-1615`, draft→approve→finalize
  auto-chain; note it reads `billDraft.has("id")` where COSTA returns `billId` — the auto-chain likely
  never fires; mobile paths read `billId` correctly). **The standard web encounter close/discharge
  path (`EncounterController`) had no trigger** — closed by this workstream (draft-only, non-blocking,
  COSTA-side idempotent).
- **BFF `V17` `costa_bill_id` bridge column**: `services/experience-bff/.../V17__encounter_costa_bill_bridge.sql`
  adds `encounters.costa_bill_id` but **no code ever writes it** — verdict: dead column. Bill linkage
  travels in API response meta/attributes only; canonical linkage is COSTA's own `bill.encounterId`
  (`BillHeaderRepository.findByEncounterId`). Recommend either wiring write-back or dropping in a
  future additive migration (deferred; not required once bill-by-encounter reads exist).
- **Payment-intent amount**: caller-supplied end-to-end (BFF `FinanceController.createPaymentIntent`
  requires `amount`; COSTA `PaymentIntegrationService.createPaymentIntent` accepts it verbatim).
  UI pre-fills from `patientPayable`, but the server never enforces the shortfall. Closed at the BFF
  by this workstream (server-derives from the bill's coverage split when the caller omits `amount`).
- **Patient shortfall**: implicit in `BillHeader.patientPayable`; no dedicated entity. Not changed here.

### Honest stubs (keep honest — do not fake)
- **All MusheX payment rails**: `PaymentRailAdapter.liveCapable()` defaults `false`
  (`mushex/rail/PaymentRailAdapter.java:38-40`); `SandboxMockAdapter` returns synthetic
  `"simulated": true` results; real rail adapters (mobile money, card, bank) log + return `PENDING`
  and do not override `liveCapable()`. `AdapterReadinessService` reports `READY_SANDBOX` /
  `CREDENTIALS_MISSING` / `DISABLED` honestly. **No live money movement anywhere.**
- **Coverage/medical aid**: `coverage-service` is a real internal registry + eligibility + claims
  ledger, but adjudication is internal (HTTP script endpoint `ClaimController.adjudicateClaim`
  or Kafka from mushex). There is **no external payer/medical-aid connector**. Tariff/coverage UX
  must label coverage as internally adjudicated.

### Hazard (RED, quarantined)
- ⚠️ **`mushex kafka/CostaEventConsumer.onBillFinalized` (lines 47-76)** is a deliberate no-op
  observer. If naively turned into an intent creator it would:
  1. double-bill — COSTA already creates the MusheX intent synchronously at
     `create-payment-intent` time;
  2. bill the FULL bill amount, not the patient shortfall (the `BILL_FINALIZED` payload carries
     `patientPayable`/`insurerPayable` but the old consumer code read a nonexistent `amount`);
  3. crash on `TrustContextHolder.require()` on the Kafka listener thread.
  **Decision (Safe outcome A + tripwire)**: keep quarantined as observer; documented in
  `docs/registry/mock-and-stub-register.md`; a guard test
  (`mushex CostaEventConsumerBillFinalizedQuarantineTest`) now proves replayed
  `costa.bill.finalized` events never create payment intents. Any future activation must go
  through the Fable serialized queue item R3 with bill-ID-scoped intent uniqueness.

### Missing (gaps this workstream closes or defers)
- ❌→✅ **EHR clinical billing visibility** — closed: read-only `GET /internal/v1/encounters/{id}/billing`
  (BFF composition over COSTA bill-by-encounter + payments) + `EncounterBillingPanel` in the EHR
  encounter page and Visit Outcome (discharge) flow, with honest states
  (`NOT_BILLED`, `BILLING_PENDING`, coverage pending/eligible/ineligible, shortfall due,
  payment pending/paid, `BILLING_UNAVAILABLE` fail-closed).
- ❌ **e2e journey test spanning encounter→coverage→payment→receipt** — deferred to
  WS-P1-C (Zen partial-coverage journey spec) per the board.
- ❌ **Mobile provider finance BFF routes** (`/internal/v1/mobile/provider/finance/*` referenced by
  `apps/mobile/provider-app/src/services/financeService.ts`) — not implemented; deferred, needs
  product decision (mobile provider finance scope).
- ❌ **Virtual/live payment rails, external payer connectors, external GL export** — integration
  work outside this repo's current honest seams; deferred.

## 3. Tariff / Zimbabwe pricing readiness

- Tariff resolution: `costa TariffCostEngine.java:44-104` — tariff-list item override
  (`costa_tariff_list_id` context) → `TariffEntity` by `msikaCode` + effective window → best match by
  facility category + patient category specificity. Versioned (`version`, `effectiveFrom/To`, `status`).
- Import: `POST /costa/v1/tariffs/import` (CSV) + tariff intel vertical (`/api/costa/tariff-lists`,
  upload batches) surfaced via `CostaIntelBffController` and `/finance/tariffs` UI.
- **No Zimbabwe tariff data is hard-coded** and none was added. Tariff ingestion remains an
  operator/data exercise via the existing import pipeline. Readiness: pipeline ✅, national data ❌ (not loaded).

## 4. Ownership map (verified — unchanged by this workstream)

| Function | Owner |
|---|---|
| Bills, pricing, invoices, charge capture, patient accounts, payment plans | COSTA (`costing-engine-service`) |
| Payment intents, attempts, rails, receipts, refunds, reconciliation, settlements | MusheX (`mushex-service`) |
| Coverage plans, members, eligibility, claims adjudication, subsidies, preauth | `coverage-service` |
| GL journals, periods, trial balance | `general-ledger-service` |
| Encounter truth | PCT (`pct-service`) — bills reference `encounterId`, never own clinical state |
| Experience composition | `experience-bff` `/internal/v1/finance/**` + `/internal/v1/encounters/{id}/billing` (read-only compose) |

## 5. Security posture (relevant to clinical visibility)

- `/internal/v1/finance/**` → FINANCE roles (`FACILITY_ADMIN`, `FINANCE`, platform overrides);
  payer-ops/reconciliation → `FINANCE`; patient self-service accounts → `authenticated` (ABAC binds cpid).
- The new `GET /internal/v1/encounters/{id}/billing` is **read-only** and falls under the
  authenticated catch-all — clinical staff get billing *visibility* without gaining any finance
  *mutation* rights (all lifecycle mutations remain behind FINANCE roles).

## 6. Known follow-ups for Fable (not addressed here)

1. **R3 (RED, serialized)**: `CostaEventConsumer.onBillFinalized` architecture decision —
   event-driven intent creation needs bill-ID-scoped uniqueness + trust-context propagation design.
2. Teleconsult auto-chain field mismatch (`billDraft.has("id")` vs COSTA `billId`) — the
   draft→approve→finalize chain in `TeleconsultController.triggerTeleconsultBilling` likely
   short-circuits after draft. Owned by the teleconsult/W-lane; flagged, not patched here
   (W0/W2 telemedicine surface is leased).
3. `V17` `costa_bill_id` dead column — wire write-back or drop (additive migration), low priority.
4. Payment-intent amount derivation inside COSTA itself (sovereign side) — the BFF now derives
   the amount when callers omit it (`REMAINDER` → `patientPayable`, `FULL` → `totalPayable`,
   zero-payable rejected with `NOTHING_PAYABLE`, `amount_source` surfaced in meta), but
   COSTA still accepts arbitrary caller amounts on `/costa/v1/bills/{id}/create-payment-intent`.
   Consider a `REMAINDER` server-side enforcement in COSTA (needs Worker A / coordinator sign-off).
5. Mobile provider finance endpoints referenced by the provider app do not exist in the BFF.
