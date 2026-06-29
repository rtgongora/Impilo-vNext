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
| 4 | Pharmacy dispenses from PCT prescription | ⬜ (PCT↔Dura integration) |
| 5 | Vaccinator administers vaccine | ⬜ |
| 6 | Lab consumes reagent/test kit | 🟡 (consumption posting exists) |
| 7 | Outreach kit issue/use/reconcile | ⬜ |
| 8 | Supplier fulfils order | ⬜ |
| 9 | Client tracks home medicine + refill | ⬜ |
| 10 | Recall affects dispensed batch | ⬜ |
| 11 | Cold-chain excursion | ⬜ |
| 12 | Stock count variance | ✅ |
| 13 | eLMIS sync failure + reconciliation | 🟡 (reconciliation exists; explicit sync-state machine ⬜) |

## 5. Backend capabilities

✅ Ledger + balances · FEFO · counts/reconciliation · requisitions · handover · consumption
posting · barcode · item/store. 🟡 catalogue (minimal) · eLMIS reconcile. ⬜ rich catalogue ·
batch/lot entity · reservations · recalls/quarantine · cold-chain · supplier mode · household
stock · PCT integration · suggested orders · sync-state machine.

## 6. Routes

- **Backend** `/api/v1/dura/*` — ⬜ (legacy inventory routes ✅; Dura namespace to be added)
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

eLMIS/NatPharm: adapter-ready architecture planned (§11). **Dura remains functional without
eLMIS; sync failures must not corrupt native stock truth; sync-state explicit/auditable/retryable.** — 🟡/⬜

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
