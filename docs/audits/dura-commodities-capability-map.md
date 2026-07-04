# Dura Commodities — Capability Map (Wave 0)

**Workstream**: Fable Seven Pipeline Parallel Delivery Board — Pipeline P4 (Dura Commodities).
**Branch**: `cursor/e2e-dura-commodities` (worktree `/opt/impilo/repos/wt-cursor-dura-commodities`).
**Base**: `origin/claude/web-session-anchor-nnnkf6` @ `3411d6ca2` (anchor has moved one commit past
the board-recorded `d44bb6022`; `3411d6ca2` is a W0 lane-state/evidence commit on top of it).
**Coordination**: board §7 + register WS-P4-A / WS-P4-B (`claude/impilo-vnext-coordination-75fzl0`
@ `b0bb68a91`). This branch executes the P4 scope (telemetry wiring, pharmacy-eLMIS stub honesty,
consumer/controller tests, stockout + sync-status UI) as a single coordinated Cursor stream.

Doctrine: **Dura is the native commodity, stock, inventory, and supply-management service.**
Dura owns stock truth and the append-only ledger. eLMIS/NatPharm are adapters, not the stock
brain. Simba is wellness and owns no stock.

Legend: ✅ real and wired · 🟡 partially wired · 🔶 honest stub / adapter seam only · ❌ missing

## 1. Backend — inventory-service (Dura by name and by fact)

| Capability | Status | Evidence |
|---|---|---|
| Append-only ledger + atomic on-hand projection + idempotency + outbox | ✅ | `core/LedgerServiceImpl.postEvent` (idempotency key check, `inv_ledger_events` insert, on-hand upsert, `LEDGER_EVENT_CREATED` outbox row in one tx) |
| Movement types | ✅ | `domain/LedgerEventType`: RECEIPT, ISSUE, TRANSFER_OUT/IN, ADJUSTMENT, WASTAGE, RETURN, COUNT, COUNT_ADJUST |
| Commodity catalogue identity | ✅ | `ItemEntity`: code, name, uom, category, generic/brand, form/strength, pack size, stocking/dispensing unit, programme area, cold-chain min/max, batch/expiry/serial tracking flags, controlled flag, substitution group, essential-medicine flag, GTIN, ZIBO refs (`ziboRefs`), external codes (`externalCodes`) — no duplicate terminology universe needed |
| Facility/store/bin/batch model | ✅ | `FacilityEntity`, `StoreEntity` (+`StoreType` incl. PHARMACY), `BinEntity`, `BatchLotEntity` (+status), `OnHandEntity` keyed facility/store/item/batch/expiry |
| Balance derived from ledger | ✅ | on-hand projection updated only inside `postEvent`; `getBalance` sums ledger deltas |
| Reservations | ✅ | `ReservationService` + `StockReservationEntity` + `DuraReservationController` |
| FEFO | ✅ | `FefoService` + `FefoController` |
| Stock counts / reconciliation | ✅ | `StockCountService`, `ReconciliationService`, `CountController`, `ReconcileController` |
| Requisitions, handover, suppliers, recalls, cold chain, household stock | ✅ | dedicated services/controllers, migrations `V005–V011__dura_*` |
| Stockout query | ✅ | `OnHandController GET /v1/onhand/stockouts` → `OnHandRepository.findStockouts` |
| Near-expiry query | ✅ | `/v1/onhand/near-expiry`, `/v1/dura/batches/near-expiry` |
| Low-stock/stockout **event emission** | ✅ (this branch) | was 🟡: `LedgerServiceImpl.publishStockLevelTelemetrySnapshot` defined but never called; `STOCKOUT_RISK` topic routed (`inventory.stockout.risk`) with zero producers. Wired in this branch: telemetry + stockout-risk outbox rows emitted from `postEvent` after the on-hand projection updates |
| Reorder/min/max levels per item | ❌ | no reorder-level columns on `ItemEntity`/`OnHandEntity`; stockout = qty ≤ 0 only. Threshold-based low-stock requires an additive Dura migration — **deferred** (register forbids Dura migrations in this parallel wave) |

## 2. Clinical consumption hooks

| Hook | Status | Evidence |
|---|---|---|
| PCT availability/reserve/consume | ✅ | `DuraPctController /v1/dura/pct/*` → `StockAvailabilityService` (on-hand − reserved, substitutes on stockout), `ReservationService`, `ConsumptionPostingService.postClinicalConsumption` (real ISSUE ledger posting, encounter/procedure ref retained) |
| Pharmacy dispensing → Dura ledger | ✅ | Kafka `pharmacy.stock.movement.requested` → `events/PharmacyConsumer` → `postPharmacyConsumption` (ISSUE, refType=PHARMACY, idempotent per order/item/batch; non-UUID pharmacy store codes resolved to the facility PHARMACY store) |
| Lab consumption entry point | ✅ (API) | `ConsumptionPostingService.postLabConsumption` |
| Kafka consumption path tests | ✅ (this branch) | was ❌ — no tests for `PharmacyConsumer` or `DuraPctController`; added in this branch |
| PCT prescribing | ❌ (out of Dura scope) | pct-service has no prescribe controller — owned by P3 (WS-P3-A), not duplicated here |
| Ward/theatre/outreach-specific consumption UIs | 🟡 | backend `postClinicalConsumption` accepts any refType; dedicated surfaces not built |

## 3. eLMIS / NatPharm adapter seams

| Seam | Status | Evidence |
|---|---|---|
| Dura external sync state machine | ✅ | `ExternalSyncServiceImpl` (PENDING/SYNCED/FAILED/RETRY, attempts, error queue) + `DuraExternalSyncController /v1/dura/external-sync` + `V011__dura_external_sync.sql` |
| inventory-elmis-adapter connector | 🔶 honest | real fail-closed connector; NOT_LIVE on placeholder URL — does not fake success |
| pharmacy-elmis-adapter dispense sync | 🔶 stub | `DispenseSyncService.triggerSync` persists a RUNNING row and performs **no synchronization**; no transition to COMPLETED/FAILED. Recorded in `docs/registry/mock-and-stub-register.md` (this branch). Do not mark complete |
| External item/facility code mapping | ✅ (model) | `ItemEntity.externalCodes`, adapter `ElmisAdapterProperties.Mapping` code systems |
| Sync status surfaced to operators | ✅ (this branch) | was ❌ — no BFF route/UI for `/v1/dura/external-sync`; added read-only BFF proxy + Dura page panel with honest states |

## 4. Experience layer (BFF + UI)

| Surface | Status | Evidence |
|---|---|---|
| BFF Dura proxy | ✅ | `DuraBffController /internal/v1/dura/**` (categories, commodities, near-expiry, recalls, cold-chain, pct availability) |
| BFF inventory supply proxy | ✅ | `InventorySupplyBffController /internal/v1/inventory/**` (on-hand, near-expiry, stockouts, ledger, …) |
| `/work/dura` page | ✅ | `ui/one-ui-shell/src/app/work/dura/page.tsx` + `hooks/queries/useDura.ts` — catalogue search, near-expiry, recalls, cold-chain excursions; honest empty states, no fake numbers |
| Stockout visibility on Dura page | ✅ (this branch) | was ❌ — `/stockouts` endpoint existed end-to-end but was not rendered |
| eLMIS/NatPharm sync status panel | ✅ (this branch) | was ❌ |
| Mobile inventory | ✅ | `MobileInventoryController` (stockouts + near-expiry, honest empty fallback) |

## 5. No-touch boundaries respected

- No Dura/shared DB migrations, no Kafka topic/schema changes, no `PharmacyConsumer` contract
  changes, no ledger posting-semantics changes (telemetry emission is additive outbox rows in the
  same tx), no eLMIS connector behaviour changes, no `routes.ts`/`app-registry.ts`/`api-client.ts`
  core edits, no helm/compose/root-config edits, no W0 telemedicine surface.

## 6. Deferred (honest gaps, with reasons)

| Gap | Reason deferred |
|---|---|
| Reorder/min/max thresholds + threshold-based low-stock | needs additive Dura migration — forbidden for this parallel wave (register WS-P4-A); stockout-risk (qty ≤ 0) emission wired now as the safe subset |
| pharmacy-eLMIS real dispense sync | external eLMIS/NatPharm configuration + real connector work; stub recorded honestly, sync-status UI shows it as a deferred seam |
| PCT prescribing → dispensing chain | prescribing absent in pct-service; owned by P3 (WS-P3-A) — not duplicated from the Dura side |
| Khuluma/notification fan-out of stockout events | `inventory.stockout.risk` now has a producer; consumer wiring is a cross-service decision (R5 topic governance) — flagged to Fable |
| Ward/theatre/outreach kit dedicated UI surfaces | backend seams exist (`postClinicalConsumption`, handover, requisitions); UI surfaces are follow-on work, not faked |
