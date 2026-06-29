# Product Truth — Dura: Commodity, Stock, Inventory & Supply Service

> **Status:** Foundational — sovereign declared, build in waves (2026-06-29).
> **Realisation:** extends `inventory-service` ([ADR-0053](../adr/ADR-0053-dura-stock-sovereign-extends-inventory-service.md)).
> **This document is honest about what is built vs planned.** Legend: ✅ built · 🟡 partial · ⬜ planned.

## 1. Service definition

Dura is the native vNext **Commodity, Stock, Inventory & Supply Management** service:
ledger-first stock truth, storehouses, batch/lot/expiry, movements, reservations, ward/lab/
pharmacy/vaccine/outreach/emergency stock, supplier mode, and client/caregiver household
stock. **Dura is not Simba** (Simba = wellness only). **eLMIS/NatPharm are adapters**, not the
stock brain — Dura runs natively when external systems are unavailable.

- Module: `services/inventory-service` · Port: **8098** · DB: `inventory` · Sovereign group: **DURA**

## 2. Domain ownership

Owns: commodity catalogue & categories, stock locations/owners, stock ledger & balances,
batch/lot/expiry, serialised items, receiving/issuing/transfers, reservations/allocations,
counts/adjustments/wastage, quarantine/recalls, cold-chain events, supplier catalogues,
client home stock, refill planning, stock analytics, eLMIS/NatPharm sync-state.

Does **not** replace: PCT (clinical workflow), OROS (order workflow), Pharmacy (dispensing),
Madi (blood lifecycle), Procurement (POs), Msika (marketplace), Costa/MusheX (cost/pay), Rito
(quality cases), Butano (FHIR), Zibo (terminology), Tshepo (trust/policy).

## 3. Integration map

PCT (primary, §9) · OROS · Pharmacy · Madi · Rito · Khuluma · Nompilo · Butano/FHIR · Zibo ·
Tuso · Indawo · Ndila · Varapi · Vashandi · Vito · Nhume · eLMIS/NatPharm adapters.
Full table: [`DURA_BUILD_REPO_AUDIT.md §2`](../audits/DURA_BUILD_REPO_AUDIT.md).

## 4. End-to-end journeys (spec §29)

| # | Journey | Status |
|---|---|---|
| 1 | Facility receives stock | 🟡 (ledger receipt exists; quality/cold-chain gating ⬜) |
| 2 | Routine resupply / suggested order | 🟡 (requisitions exist; suggested-order calc ⬜) |
| 3 | Ward requests stock | 🟡 (transfer/handover exists) |
| 4 | Pharmacy dispenses from PCT prescription | 🟡 (Dura-side availability/reserve/consume→ledger built — Wave 4; PCT-side caller + UI pending) |
| 5 | Vaccinator administers vaccine | ⬜ |
| 6 | Lab consumes reagent/test kit | 🟡 (consumption posting exists) |
| 7 | Outreach kit issue/use/reconcile | ⬜ |
| 8 | Supplier fulfils order | 🟡 (supplier profile/catalogue/order lifecycle backend — Wave 8; Costa/MusheX/Nhume + UX pending) |
| 9 | Client tracks home medicine + refill | 🟡 (home stock + refill lifecycle backend — Wave 7; `/my-life/stock` UX + Khuluma reminders pending) |
| 10 | Recall affects dispensed batch | 🟡 (Dura freezes affected batches to RECALLED — Wave 5; client/provider notification + dispensed-client trace pending) |
| 11 | Cold-chain excursion | 🟡 (locations + temp logs + auto-excursion + resolve release/wastage — Wave 6; Rito/Khuluma escalation + auto-quarantine of affected batches pending) |
| 12 | Stock count variance | ✅ |
| 13 | eLMIS sync failure + reconciliation | 🟡 (explicit sync-state machine + retry/replay + error history — Wave 9; live adapter transport pending) |

## 5. Backend capabilities

✅ Ledger + balances · FEFO · counts/reconciliation · requisitions · handover · consumption
posting · barcode · item/store · **rich commodity catalogue (categories + Dura enrichment
attributes: pack/dispensing units, programme area, cold-chain, tracking flags, regulatory
status, GTIN, external codes) — Wave 2** · **first-class batch/lot/expiry with governance
status (active/quarantined/recalled/expired) + near-expiry queries — Wave 3** · **stock
reservations/allocations with available = on-hand − active reservations — Wave 3**. 🟡 eLMIS
reconcile · **PCT integration (Dura side): stock-aware availability with usable batches +
nearest expiry + approved substitutes when stocked-out, reserve-for-encounter, and
consume→ledger — Wave 4** · **recalls that freeze affected batches to RECALLED + quarantine via
batch status — Wave 5** · **cold-chain: locations (fridge/cold-room/freezer), temperature logs
(manual/IoT) with automatic excursion detection, excursion resolve (release/wastage) — Wave 6** · **client/caregiver household stock + refill
request lifecycle — Wave 7** · **supplier mode: profiles, published catalogue (price/availability),
customer orders (header+lines) with fulfilment lifecycle — Wave 8** · **eLMIS/NatPharm
sync-state machine: enqueue/synced/failed with capped auto-retry → RETRY/FAILED, per-attempt
error history, manual replay; native truth never mutated by sync — Wave 9**.
⬜ **PCT-side caller + embedded UI hooks** · web/mobile UX (`/work/dura/*`, `/my-life/stock`) ·
suggested orders / OROS · Rito/Khuluma/Costa/MusheX/Nhume hooks · outbox events.

## 6. Routes

- **Backend** `/api/v1/dura/*` — 🟡 (`/v1/dura/categories`, `/v1/dura/commodities` — Wave 2; `/v1/dura/batches`, `/v1/dura/reservations` (incl. availability) — Wave 3; `/v1/dura/pct/{availability,reserve,consume}` — Wave 4; `/v1/dura/recalls` — Wave 5; `/v1/dura/cold-chain` — Wave 6; `/v1/dura/client-stock` + `/v1/dura/refills` — Wave 7; `/v1/dura/suppliers` (+catalogue/orders) — Wave 8; `/v1/dura/external-sync` — Wave 9; remaining namespaces planned. Legacy inventory routes ✅)
- **BFF** `/internal/v1/dura/*`, `/internal/v1/dura/pct/*`, `/internal/v1/mobile/dura/*` — ⬜
- **Web** `/work/dura/*`, PCT-embedded stock panels, `/my-life/stock/*`, `/work/dura/supplier/*` — ⬜
  (existing `StockManagementPanel.tsx`, `useInventory.ts` 🟡 to be re-homed under Dura)
- **Mobile** scan/receive/issue/count/outreach/client-stock/refills/alerts — ⬜

Full route catalogue: [`DURA_BUILD_REPO_AUDIT.md §5`](../audits/DURA_BUILD_REPO_AUDIT.md).

## 7. Policy entries

OPA/Tshepo: client/caregiver household, stores officer, pharmacist, nurse/vaccinator, lab,
supplier admin, regulator/programme visibility, controlled-stock restrictions — ⬜ (consume
existing authz engine; authz-service code owned by concurrent workstream, not edited here).

## 8. Adapter status

eLMIS/NatPharm: **sync-state machine built — Wave 9** (`/v1/dura/external-sync`): explicit
state (PENDING/SYNCED/FAILED/RETRY), capped auto-retry, per-attempt error history, manual
replay; **native stock truth is never mutated by a sync failure.** Live adapter transport
(actual eLMIS/NatPharm HTTP exchange) remains ⬜.

## 9. Known gaps (honest)

- PCT↔Dura integration not yet built (highest-value next).
- Batch/lot, reservations, recalls, cold-chain, supplier, household are ⬜.
- Module directory still named `inventory-service` (Dura is the product/sovereign identity);
  optional physical rename tracked in ADR-0053.
- registry-maturity generator shows pre-existing drift on unrelated rows (not introduced by Dura).

## 10. Wave plan

See spec §37 and task tracker. Each wave is committed + pushed and updates this document with
honest ✅/🟡/⬜ status. No capability is marked ✅ without a real UX route or a documented
internal-only reason.
