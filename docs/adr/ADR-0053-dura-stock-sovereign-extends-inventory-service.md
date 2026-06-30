# ADR-0053 — Dura is the sovereign stock brain, realised by extending inventory-service

## Status

Proposed (engineering baseline — 2026-06-29). **PO may veto §Decision option.**

## Context

Impilo vNext requires **Dura** — the native, sovereign Commodity, Stock, Inventory &
Supply Management service: ledger-first stock truth, storehouses, batch/lot/expiry,
movements, reservations, recalls, cold-chain, supplier mode, household stock, deep PCT
integration, and eLMIS/NatPharm adapter-readiness (full spec: the Dura build brief, 2026-06-29).

A repo audit (`docs/audits/DURA_BUILD_REPO_AUDIT.md`) found an existing **`inventory-service`**
(port 8098, DB `inventory`, schema prefix `inv_`) that already self-describes as
*"Supply Chain, Stock Ledger & Warehouse"* and implements the exact Dura core: an
append-only event-sourced ledger with on-hand projections, FEFO picking, stock counts,
reconciliation, internal requisitions, handovers, capability routing, barcode lookup, typed
consumption posting (pharmacy/lab/clinical), and eLMIS reconciliation hooks.

Two governing documents **forbid duplication**:
- CLAUDE.md guardrail: *"Do not create duplicate system-of-record functionality."*
- The Dura spec non-negotiables: *"Do not duplicate existing services… Extend coherently."*

The spec §2 itself permits the resolution: *"**Create or extend** a sovereign service called
`dura-stock-service`."* ADR-0042 sets precedent for choosing one extensible service over new
modules when capability already exists.

## Decision

**Realise Dura by elevating and extending the existing `inventory-service` into the sovereign
Dura stock brain — do not create a second stock service.**

1. **Sovereign identity.** Registry: `inventory-service` → `product_names: [Dura, Inventory]`,
   `sovereign: true`, `sovereign_group: DURA`, expanded stock system-of-record. Dura is
   first-class in Product Truth. It is **not** under Simba.
2. **Module id retained.** The maven module / directory stays `inventory-service` for
   continuity (no churn to `services/pom.xml`, imports, packages, compose, generated
   artifacts, golden-contract IT). A physical rename to `dura-stock-service` is recorded as an
   **optional, mechanical follow-up** (see Consequences) — not done now to keep the diff
   reviewable and minimise conflict surface with the concurrent `crazy-merkle` workstream.
3. **API namespace.** New Dura capabilities are served under `/api/v1/dura/*` (and BFF
   `/internal/v1/dura/*`), alongside the retained legacy inventory routes.
4. **One ledger.** New capability **extends** the existing `inv_ledger_events` ledger (new
   event types, reference columns) and adds genuinely-new tables (batch/lot, reservations,
   recalls, quarantine, cold-chain, suppliers, client home-stock, sync-state). **No parallel
   ledger** is created. The spec's `dura_*` table names are "suggested"; physical tables keep
   the module's `inv_`/cohesive prefix to avoid a split schema.
5. **Integrate, don't absorb, neighbours.** Dispensing stays in `pharmacy-service`, blood in
   `madi`, order workflow in `oros`, procurement in `procurement-service`, marketplace in
   `msika`, FHIR in `butano` — each posts stock movements **through** Dura's ledger. Dura owns
   stock truth only.

## Consequences

- **Positive:** zero duplicate system-of-record; reuses a mature ledger; smallest reviewable
  diff; no conflict with the concurrent security workstream; Dura is unambiguously sovereign
  in Product Truth and registry.
- **Negative:** directory name (`inventory-service`) differs from the spec's literal
  `dura-stock-service` until the optional rename; some legacy `inv_`-named artifacts persist.
- **Follow-up (optional, PO-gated):** a mechanical module rename `inventory-service →
  dura-stock-service` (directory, artifactId, package, registry id, classification, compose,
  generated maturity, golden-contract IT) can be executed as a single isolated wave if the PO
  requires the literal folder name. Tracked, not blocking.

## Alternatives considered

- **New `services/dura-stock-service` module alongside inventory-service** — rejected: creates
  two stock services / two ledgers = duplicate system-of-record, explicitly forbidden by both
  CLAUDE.md and the spec. Convergence-later does not avoid the interim duplication.
- **Immediate physical rename of inventory-service → dura-stock-service** — deferred: large,
  high-conflict diff (pom, packages, imports, compose, generated artifacts, IT) as a Wave-1
  step; provides naming purity but not capability, and raises conflict risk with concurrent
  work. Offered as an optional follow-up instead.
