# Dura ↔ Simba Stock Ownership Audit

**Date:** 2026-06-29 · **Scope:** Establish that **Dura** owns stock and **Simba** is
wellness-only, and confirm no duplicate stock system-of-record exists.

Related doctrine: [`docs/doctrine/dura-stock-doctrine.md`](../doctrine/dura-stock-doctrine.md)

---

## 1. Question

Product truth: *Dura owns stock management; Simba is wellness only.* Audit whether any
stock/inventory logic is (incorrectly) owned by Simba, and whether the native stock brain
already exists under another name.

## 2. Method

Static search across `services/`, `ui/`, `contracts/`, `compose/`, `docker-compose*.yml`,
and `docs/registry/` for stock terms (`stock`, `inventory`, `dispens*`, `batch`, `lot`,
`expiry`, `requisition`, `storehouse`, `consumption`, `warehouse`) cross-referenced with
`simba`, plus a capability review of `inventory-service`.

## 3. Findings

### 3.1 The native stock brain already exists — as `inventory-service`

`services/inventory-service` (port 8098, DB `inventory`, schema prefix `inv_`) is a mature,
well-structured native stock service: append-only idempotent ledger, FEFO, stock counts &
reconciliation, requisitions, handover, capability routing, barcode lookup, and consumption
posting. **This is Dura.** No separate `dura-service` existed; creating one would have
produced a forbidden duplicate system-of-record. Dura is therefore **canonicalized in-place**
on this module (sovereign identity = Dura; module dir retained to avoid churn).

### 3.2 Simba backend is clean of stock logic ✅

`grep` over `services/simba-service/src` for all stock terms returned **zero** matches.
Simba's domain is wellness/personal-health-data only (diet, sleep, exercise, mood, vitals,
goals, challenges, clubs, coaching, Health Connect). No migration, entity, repository,
service, or controller in Simba touches stock.

### 3.3 Only stock coupling to Simba is a correct read-only UI consumer ✅

The sole `simba`+`stock` co-occurrence in the repo:

```
ui/one-ui-shell/src/app/wellness/commodities/page.tsx:
  "Inventory-backed wellness commodities (SIMBA plane) — on-hand stock from
   inventory-service, not simba-service"
```

This wellness *commodities* surface reads on-hand stock **from Dura (inventory-service)** and
is explicitly labelled as such. It is a correct read-only consumer — Simba displays
wellness-relevant commodity availability but owns no stock. **No action required.**

### 3.4 Adapters are correctly separated ✅

`inventory-elmis-adapter` and `pharmacy-elmis-adapter` are distinct adapter modules. They are
integration partners, not the stock brain (see doctrine §2.2).

## 4. Verdict

| Claim | Result |
|---|---|
| Dura owns stock | ✅ Confirmed — `inventory-service` is the native stock brain, now canonicalized as Dura |
| Simba does not own stock | ✅ Confirmed — Simba backend is stock-free |
| No duplicate stock SoR | ✅ Confirmed — single ledger/on-hand truth in Dura |
| Simba/stock coupling | ✅ Correct read-only wellness-commodities consumer of Dura |
| eLMIS/NatPharm are adapters | ✅ Confirmed — separate adapter modules |

## 5. Actions taken / required

- **No migration of stock logic out of Simba** — none exists; nothing to move.
- **No rename of the wellness-commodities surface** — it is correctly attributed to Dura.
- **Canonicalize Dura** in the services registry (sovereign identity, expanded stock SoR) — Wave 1b.
- **Extend Dura** with batch/lot/expiry, reservations, and typed clinical consumption — Waves 2–3.
- **Wire & surface Dura** across clinical workflows and the experience shell — Waves 4–5.

## 6. Residual notes

- `inventory-service` is **not currently present in `docker-compose*.yml`** — flagged for the
  runtime/wiring wave (Wave 4) so Dura is independently runnable in local/compose topology.
