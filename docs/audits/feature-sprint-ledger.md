# Feature Sprint Ledger — sequential pop-out workstreams

> **Read this FIRST in every pop-out feature session, after the universal preamble.** It is the shared
> memory across the separate sessions: what already exists, what each prior workstream extended/built,
> and the frozen allocations — so no workstream builds-on-top-and-duplicates the estate *or a prior
> workstream*. Workstreams run **sequentially, one at a time**; a later workstream can and must see what
> earlier ones already landed.

## Operating rules

- **Extend before creating.** Prove no existing service (or prior workstream row below) already owns the
  capability. If one does → extend it. A new service requires a documented "why no existing service owns this".
- **Single-writer for shared files.** `docs/registry/services-registry.yaml`, `docs/registry/system-of-record-map.md`,
  `docs/runbooks/port-allocation.md`, and shared `contracts/*` are **coordination-owned**. A workstream that
  needs to change them records the need in its final report under "coordination items" — it does **not** edit
  them unilaterally (avoids cross-session collisions on shared state).
- **Frozen allocations.** Each workstream's owning service, port(s), and migration version range are recorded
  below the first time it runs and are fixed thereafter. New ports come from `docs/runbooks/port-allocation.md`
  (no collisions); new migrations continue that service's existing `V0xx` sequence.
- **Append, don't rewrite.** Each completed workstream appends its row to the status table. Do not edit other rows.

## Canonical SoR reuse map (who owns what — extend these)

person/client identity → **Vito** · provider identity → **Varapi** · facility/site → **Tuso** ·
clinical/FHIR/SHR → **Butano** · civil registration/vital events → **Ubomi** · care-delivery workflows → **PCT** ·
orders/service requests → **Oros** · commodity/stock/ledger → **Dura** · maps/location/routing → **Ndila** ·
dispatch/field movement → **Nhume** · workforce/roster/assignment → **Vashandi** · learning/CPD/protocols → **Fundo** ·
marketplace/billing/payments → **Msika / Costa / MusheX** · trust/context/authz → **Tshepo** ·
communication/notifications → **Khuluma** · on-platform guided experience → **Nompilo**.

## Workstream roster (8 — sequential)

| # | Workstream | Source | Owning-service decision | Status |
|---|-----------|--------|-------------------------|--------|
| 1 | _TBD (your #1)_ | PO | — | pending |
| 2 | _TBD (your #2)_ | PO | — | pending |
| 3 | _TBD (your #3)_ | PO | — | pending |
| 4 | Wellness depth (diet/sleep/fitness/clubs/coaching) | suggested | — | pending |
| 5 | Health marketplace (products/services lane) | suggested | — | pending |
| 6 | Assets / devices / equipment + IoT | suggested | — | pending |
| 7 | Nompilo as a first-class intelligent layer | suggested | — | pending |
| 8 | Unified person health wallet | suggested | — | pending |

> Ordering is the sequence we run them in; adjust as the PO directs. Workstreams 1–3 are the PO's own three
> (names pending); 4–8 are the suggested set the PO accepted.

## Completed-workstream status (each session appends one row)

| # | Workstream | Branch | Owning service(s) | Migrations | Ports | Routes added | Tests | Parity | Commit | Coordination items |
|---|-----------|--------|-------------------|------------|-------|--------------|-------|--------|--------|--------------------|
| — | _(none yet)_ | — | — | — | — | — | — | — | — | — |
