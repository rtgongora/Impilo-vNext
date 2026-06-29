# Dura Build — Repo Audit & Plan (Spec §1)

**Date:** 2026-06-29 · **Decision:** [ADR-0053](../adr/ADR-0053-dura-stock-sovereign-extends-inventory-service.md) ·
**Doctrine:** [dura-stock-doctrine](../doctrine/dura-stock-doctrine.md)

Pre-build audit of existing capability, integration partners, and the gaps Dura must close.
**Rule: do not duplicate existing capability — extend coherently.**

## 1. Existing code to EXTEND (the Dura core already exists)

`services/inventory-service` (port 8098, DB `inventory`, prefix `inv_`) — the native stock
brain. Already implemented:

| Capability | Class |
|---|---|
| Append-only idempotent ledger + on-hand projection | `LedgerService`, `inv_ledger_events` |
| FEFO picking | `FefoService` |
| Stock counts + reconciliation | `StockCountService`, `ReconciliationService` |
| Internal requisitions | `RequisitionService` |
| Inter-store handover | `HandoverService` |
| Capability-based routing | `CapabilityRoutingService` |
| Barcode lookup | `BarcodeLookupService` |
| Typed consumption (pharmacy/lab/clinical) | `ConsumptionPostingService` |
| Item & store management; v1.1 golden contract | `ItemService`, `StoreService` |

→ **Dura extends this module** (ADR-0053). New work is additive; the ledger is reused, not rebuilt.

## 2. Integration partners (own their domain — Dura must NOT absorb)

| Service | Owns | Dura relationship |
|---|---|---|
| `pharmacy-service` (180 files) | Dispensing workflow | Posts `dispense` ledger events through Dura |
| `madi` (167 files) | Blood/transfusion lifecycle | Dura gives commodity-ledger visibility only |
| `oros-service` | Service/order/requisition workflow | Dura owns availability/reservation/ledger; OROS owns order workflow |
| `procurement-service` (38 files) | Purchase orders/procurement | Dura receives against POs |
| `pct-service` | Clinical/encounter workflows | **Primary consumer** — availability/reserve/consume (spec §9) |
| `msika-service` | Marketplace discovery | Supplier availability publishing |
| `costa` / `mushex` | Costing / payments | Supplier billing/payment |
| `rito-quality-safety-service` | Quality/safety cases | Dura raises cases (expiry/cold-chain/recall/wastage) |
| `khuluma-service` / `guidance-service` (Nompilo) | Notifications / guided UX | Stock alerts + guided flows |
| `butano` (HAPI FHIR) | FHIR/SHR | Dura/PCT events → Medication*/Supply*/Immunization |
| `inventory-elmis-adapter`, `pharmacy-elmis-adapter` | eLMIS/NatPharm exchange | Adapters; sync through Dura sync-state |
| `tshepo-*` | Trust/authz/audit | Policy + audit (⚠ owned by concurrent `crazy-merkle` — coordinate, do not edit authz) |
| `tuso` / `indawo` / `ndila` / `varapi` / `vashandi` / `vito` | Facility / site / geo / provider / workforce / client context | Stock-location + actor context resolution |
| `nhume` | Dispatch/delivery | Supplier fulfilment dispatch |

## 3. Capability gaps Dura must close (build targets)

| Area | Gap vs existing inventory-service |
|---|---|
| Catalogue | Rich commodity catalogue (categories, programme/regulatory/cold-chain/controlled flags, GTIN, Zibo/eLMIS code maps) — currently minimal `inv_items` |
| Batch/lot | First-class `batch_lot` entity (expiry, serial) — currently batch is string on ledger |
| Reservations | `reservation`/`allocation` model + ledger types — **absent** |
| Recall / quarantine | Recall + affected-stock/affected-client model — **absent** |
| Cold-chain | Cold-chain locations, temperature logs, excursions — **absent** |
| Supplier mode | Supplier profile/catalogue/price-list/order/fulfilment — **absent** |
| Household stock | Client/caregiver home stock + refill requests — **absent** |
| PCT integration | `/api/v1/dura/pct/*` availability/reserve/consume + UI hooks — **absent** |
| Ordering | Suggested orders/replenishment beyond internal requisition — **partial** |
| eLMIS sync-state | Explicit external-sync state machine + retry/replay — **partial** |

## 4. Plan — services that must integrate (build order)

PCT (§9, primary) → OROS (§10) → Pharmacy (dispense) → Rito (§23) → Khuluma/Nompilo (§24) →
Butano (§32) → eLMIS adapters (§11) → Msika/Costa/MusheX/Nhume (supplier, §21) →
Tuso/Indawo/Ndila/Varapi/Vashandi/Vito (context, §8/§25). **Do not edit `tshepo-*` authz**
(concurrent workstream `crazy-merkle`); consume existing policy surfaces.

## 5. Plan — routes to add

- **Backend** (`/api/v1/dura/*`): commodities, categories, locations, balances, ledger, orders,
  requisitions, receipts, issues, transfers, dispense/administration/consumption-events, counts,
  adjustments, expiry, wastage, quarantine, recalls, cold-chain, suppliers, client-stock,
  refills, analytics, reports, external-sync. (spec §26)
- **BFF** (`/internal/v1/dura/*` + `/internal/v1/dura/pct/*` + `/internal/v1/mobile/dura/*`). (spec §26)
- **Web** (`/work/dura/*`, PCT-embedded panels, `/my-life/stock/*`, `/work/dura/supplier/*`). (spec §27)
- **Mobile** (scan/receive/issue/count/outreach-kit/client-stock/refills/alerts). (spec §27.5)

## 6. Plan — migrations to add

Additive Flyway `V005+` in `inventory-service`: extend `inv_ledger_events` (event types,
ref columns); new tables for commodity catalogue, batch_lots, reservations, recalls,
quarantine, cold-chain, suppliers, client_home_stock, refill_requests, external_sync_state.
Indexes per spec §30 (commodity, location, batch, expiry, owner/tenant, facility, programme,
ledger timestamp, sync status, recall batch, stock-out risk, near-expiry).

## 7. Plan — policies to add

OPA/Tshepo policy entries (consume existing engine; do **not** edit authz service code):
client/caregiver household stock, stores officer, pharmacist, nurse/vaccinator, lab user,
supplier admin, regulator/programme visibility, controlled-stock restrictions. (spec §25)

## 8. Plan — tests to add

Backend (ledger/balance/batch/FEFO/receive/issue/transfer/count/adjust/reserve/recall/
cold-chain/supplier/household/sync/idempotency/audit), PCT integration, OPA policy, frontend
route + workflow, mobile scan/offline. (spec §35)

## 9. Plan — Product Truth entries to update

- New: [`docs/product-truth/dura-stock-supply-service.md`](../product-truth/dura-stock-supply-service.md) (service definition + journeys + route/adapter status + honest gaps).
- Registry: elevate `inventory-service` to Dura sovereign.
- Route parity must remain green; no backend capability marked complete without a UX route or a documented internal-only reason.

## 10. Conflict-awareness

Concurrent branch `origin/claude/crazy-merkle-3ad1a1` owns: `tshepo-authz-service`,
`patient-safety-service`, `learning-service`, `ui/.../cadreEngine.ts`, keycloak realms,
migrations V019–V024. **Dura work avoids all of these.** Dura touches `inventory-service`,
new docs/registry, PCT integration points (additive), and `ui/one-ui-shell` Dura surfaces —
none overlapping the concurrent set.
