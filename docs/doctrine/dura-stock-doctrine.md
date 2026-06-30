# Dura — Native Stock, Commodity & Supply Doctrine

> **Dura** (Shona: *granary / storehouse*) is the **native sovereign stock brain** of Impilo
> vNext. It owns stock truth, the stock ledger, storehouses, batch/lot/expiry, movements,
> issues, receipts, transfers, adjustments, reservations, ward stock, dispensing and
> administration consumption, procedure/theatre consumption, outreach stock, emergency
> stock, and referral stock needs — and it informs stock-aware clinical decisions.

Status: **Canonical** · Established: 2026-06-29 · Plane: `clinical` · Implementation module: `services/inventory-service`

---

## 1. One-line doctrine

> **One stock truth, one ledger, many storehouses, many contexts.** Dura is the single
> source of record for *what stock exists, where it is, in what batch, and how it moves.*

## 2. Ownership boundary (product truth)

| Concern | Owner | Not owner |
|---|---|---|
| Stock truth / on-hand position | **Dura** | Simba, Msika, Pharmacy, eLMIS |
| Stock ledger (append-only movements) | **Dura** | — |
| Storehouses, stores, bins, locations | **Dura** | — |
| Batch / lot / expiry tracking | **Dura** | — |
| Receipts, issues, transfers, adjustments, wastage, returns | **Dura** | — |
| Reservations / allocations | **Dura** | — |
| Ward stock, dispensing & administration consumption | **Dura** (ledger) | Pharmacy posts *through* Dura |
| Procedure / theatre / outreach / emergency / referral stock | **Dura** | — |
| Wellness commodities **display** | Simba (read-only consumer of Dura) | Simba owns no stock |
| Product catalogue / item master reference | Product Registry (canonical item reference) | Dura references, does not duplicate |
| Procurement / purchase orders | Procurement | Dura receives against, does not own POs |
| Enterprise / financial ledgering | General Ledger | Dura is a stock ledger, not a money ledger |
| eLMIS / NatPharm exchange | Adapters (`inventory-elmis-adapter`, `pharmacy-elmis-adapter`) | Adapters integrate; Dura is the brain |

### 2.1 Simba is wellness only

Simba **does not own stock**. The only stock coupling in Simba is the wellness
*commodities* surface (`ui/one-ui-shell/src/app/wellness/commodities/page.tsx`), which is an
explicit **read-only consumer** of Dura on-hand data — it is already labelled
"*on-hand stock from inventory-service, not simba-service*". No stock logic exists in the
`simba-service` backend. See [`docs/audits/DURA_SIMBA_STOCK_OWNERSHIP_AUDIT.md`](../audits/DURA_SIMBA_STOCK_OWNERSHIP_AUDIT.md).

### 2.2 eLMIS / NatPharm are adapters, not the brain

`inventory-elmis-adapter` and `pharmacy-elmis-adapter` are **integration partners**. They
exchange deliveries, requisitions, and stock-status with national systems. They **route all
stock state changes through Dura's ledger** — they never hold an independent stock truth.

## 3. Canonical model

Dura's implementation lives in `services/inventory-service` (port **8098**, DB `inventory`,
JPA schema prefix `inv_`). The native module name is retained to avoid destructive churn and
duplicate system-of-record; the **product/sovereign identity is `Dura`**.

### 3.1 Stock ledger (append-only)

Every stock change is an immutable `LedgerEventEntity` carrying a signed `qtyDelta` and an
idempotency key. The on-hand position is a denormalized projection updated atomically with
each event. Event types (`LedgerEventType`):

`RECEIPT · ISSUE · TRANSFER_OUT · TRANSFER_IN · ADJUSTMENT · WASTAGE · RETURN · COUNT · COUNT_ADJUST`

> **Extension types (added in Dura waves):** `RESERVE · RELEASE` (reservations),
> consumption is recorded via `ISSUE` carrying a typed `refType` context.

### 3.2 Consumption contexts

Consumption is posted through `ConsumptionPostingService`, which delegates to the ledger with
a typed reference. Canonical `refType` values:

`PHARMACY · LAB · WARD · THEATRE · PROCEDURE · OUTREACH · EMERGENCY · REFERRAL · ADMINISTRATION`

### 3.3 Storehouse hierarchy

`Facility → Store (storehouse) → Bin`. Stores carry a `capability` profile
(`CapabilityRoutingService`) so requisitions and transfers route to the correct storehouse.

## 4. Current capabilities (baseline, 2026-06-29)

Already implemented in `inventory-service` and now governed under Dura:

- Append-only ledger with idempotency + on-hand projection (`LedgerService`)
- FEFO (first-expiry-first-out) suggestion engine (`FefoService`)
- Stock counts + reconciliation (`StockCountService`, `ReconciliationService`)
- Requisitions (create/approve) (`RequisitionService`)
- Inter-store handover (`HandoverService`)
- Capability-based routing (`CapabilityRoutingService`)
- Barcode lookup (`BarcodeLookupService`)
- Consumption posting for pharmacy / lab / generic clinical (`ConsumptionPostingService`)
- Item & store management; v1.1 golden contract

## 5. Capability gaps → Dura build waves

| Wave | Capability |
|---|---|
| 2 | First-class **batch / lot / expiry** entity + **reservations / allocations** |
| 3 | Typed clinical **consumption contexts**: ward, theatre/procedure, outreach, emergency, referral |
| 4 | Cross-service wiring: PCT, OROS, pharmacy, referrals, Experience BFF |
| 5 | Experience-shell UX: stock management, ward stock, batch/expiry, reservations |

## 6. Integration map

Dura integrates with: **PCT** (encounter consumption, stock-aware ordering), **OROS**
(lab/diagnostic consumption), **Pharmacy** (dispensing consumption), **wards & inpatient**
(ward stock & administration), **theatre/procedures** (consumption), **referrals** (stock
need signalling), **Khuluma** (stock alerts/notifications), **Nompilo** (stock-aware guidance,
never overriding provider judgement, always auditable), **Rito** (quality/safety on
expiry/wastage), **Tuso**, **Vashandi** (workforce stock duties), and relevant
**registry/trust** services (Product Registry for item reference; Tshepo for authz/audit).

## 7. Guardrails

- Dura is the **only** stock system-of-record. No service may hold a parallel on-hand truth.
- Every stock-changing action produces a ledger event with authz + audit meaning.
- Dura references the Product Registry item master; it never duplicates the catalogue.
- Dura is a **stock** ledger, not a financial ledger — money lives in General Ledger / Costa / MUSheX.
- Nompilo stock guidance is advisory and auditable; it never auto-executes irreversible stock actions.
