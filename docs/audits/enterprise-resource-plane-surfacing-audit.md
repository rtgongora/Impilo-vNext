# Enterprise Resource Plane — Surfacing Audit

**Branch context:** Impilo vNext (Health OS)  
**Date:** 2026-04-12  
**Scope:** Commodities, inventory, pharmacy, procurement, finance (COSTA / MusheX), assets, logistics, contracts, tariffs, costing, revenue, reconciliation, and their surfacing in **One UI Shell** (`ui/one-ui-shell`) vs legacy sidecars.

---

## 1. Executive summary

| Theme | Finding |
|--------|---------|
| **Canonical shell** | `ui/one-ui-shell` is the primary actor-facing orchestration layer per `AGENTS.md`. Legacy apps (`inventory-web`, `pharmacy-web`, `costa-console`, MusheX consoles, etc.) remain as **continuity / deep ops** surfaces until fully retired. |
| **Real vs mock** | Inventory BFF proxies (`/internal/v1/inventory/**`) and procurement BFF (`/internal/v1/erp/procurement/**`) are **real API shapes**. Several Experience pages previously mixed **hardcoded KPIs / sample stock rows**; `inventory/stock-management` was flagged and remediated to bind to TanStack queries where possible. |
| **Gaps** | End-to-end **procurement → GRN → invoice match → payment** is not fully orchestrated in one UI. **Fleet / cold chain / district aggregates** lack sovereign read models in many environments. **Charge sheet → inventory decrement** requires explicit workflow contracts (COSTA + pharmacy + inventory). |

---

## 2. Service inventory (backend)

Sources: `docs/plan/SERVICE_CATALOG.md`, `services/*/pom.xml`, BFF controllers under `services/experience-bff`.

| Service | Ring | Status (catalog) | ERP-relevant APIs (high level) | Notes |
|---------|------|------------------|--------------------------------|-------|
| **inventory-service** | 2 | LIVE | `/v1/onhand`, `/v1/ledger`, `/v1/items`, `/v1/counts`, `/v1/requisitions`, `/v1/reconcile` | BFF: `InventorySupplyBffController`. Kafka outbox for inventory events. |
| **inventory-elmis-adapter** | 2 | LIVE | Sync / bridge to national eLMIS | OAuth2 resource server in hardened stacks. |
| **pharmacy-service** | 1 | LIVE | Dispense, FEFO, substitutions, stock views | Integrates with inventory ledger patterns where wired. |
| **pharmacy-elmis-adapter** | 2 | LIVE | External pharmacy LMIS | |
| **msika-service** | 0 | SKELETON | Catalog, tariffs, packs | UI often uses **Msika governance** via BFF rather than direct service in dev. |
| **msika-flow-service** | 1 | LIVE | Orders, fulfilment, vendor flows | **Commerce plane**; ties to procurement-adjacent marketplace fulfilment. |
| **costing-engine-service** | 1 | LIVE | Tariffs, charge rules, bills, claims packing | **COSTA** — billing computation spine. |
| **mushex-service** | 0 | LIVE | Payments, settlement, rails | **MusheX** — payment / wallet / remittance; must stay separate from COSTA bill computation. |
| **coverage-service** | 1 | SKELETON | Eligibility, pre-auth | Finance/clinical surfaces need guarded reads only. |
| **tuso-service** | 0 | SKELETON | Facilities, resources, control tower | Asset / equipment **location** truth over time. |
| **oros-service** | 1 | LIVE | Orders / results | Consumables ordering for lab; charge hooks via COSTA rules (when configured). |
| **pct-service** | 1 | LIVE | Queues, encounters | Entry point for **charge capture** UX (shell routes under clinical). |
| **notification-service** | 2 | LIVE | Templates, delivery | Alerts for stockouts, approvals, settlement exceptions. |
| **rules-service** | 2 | LIVE | Decision logging | Can enforce approval chains (procurement / billing) when policies exist. |
| **integration-hub** | 2 | LIVE | Routing / dispatch | National / cross-facility logistics placeholders. |
| **document-service** | 2 | LIVE | Object store | GRN scans, contracts, POD attachments. |
| **credential-verification-service** | 2 | LIVE | Signed PDF / QR | Supplier credential verification (future tightening). |

---

## 3. UI applications

| UI | Port (catalog) | Role in ERP plane | One UI visible? | Data fidelity |
|----|------------------|-------------------|-----------------|---------------|
| **one-ui-shell** | 3000 | **Canonical** enterprise + clinical fusion | Yes | Mix: **real** where BFF + hooks exist; **empty states** where services absent. |
| **shared-ui** | — | Design system | N/A (library) | — |
| **inventory-web** | 3011 | Legacy ops | Partially absorbed (`/inventory/*`) | Prefer shell routes. |
| **pharmacy-web** | 3010 | Legacy ops | Partially absorbed (`/pharmacy/*`) | |
| **msika-web** | 3019 | Catalog admin | Governance via `/finance/msika-governance` | |
| **msika-flow-ops / vendor** | 3013–3014 | Fulfilment | Shell `/marketplace/*` | |
| **costa-console** | 3015 | COSTA deep ops | Shell finance + `/erp` hub | |
| **mushex-* consoles** | 3016–3018 | Payments deep ops | Shell `/finance/*` + wallet | |
| **ops-console** | 3001 | Platform ops | Shell `/operations/*` | |

---

## 4. Capability matrix (shell)

Legend: **R** real hook / BFF, **P** partial / behind feature flag, **M** mock removed or must not ship in prod, **—** not surfaced.

| Capability | Backend | Shell route / component | Role-aware | Clinical / facility link | Status |
|------------|---------|-------------------------|------------|----------------------------|--------|
| Commodities / catalog lines | inventory + MSIKA | `/inventory/items`, MSIKA governance | Facility + catalog roles | Ties to dispensing catalog | **P** |
| Stock on hand / movements | inventory-service | `/inventory`, `/inventory/movements`, `useInventory*` | Facility guard | Pharmacy + ward consumption | **R** |
| Receipts / issues / transfers | inventory ledger | BFF POST `/internal/v1/inventory/ledger/*` | Auth | Should decrement on charge post (gap) | **P** |
| Stock counts / reconcile | inventory | `/inventory/counts`, reconcile pending API | Facility | Control tower reporting | **P** |
| Expiries / FEFO | pharmacy + inventory | `/pharmacy/stock`, near-expiry API | DISPENSER + facility | Patient safety | **P** |
| Requisitions (inter-store) | inventory | `/inventory/requisitions` | Facility | Needs structured create body | **P** |
| Procurement pipeline | procurement BFF | `/erp/procurement`, `useProcurement` | Finance / commerce | Trigger from low stock (rules gap) | **P** |
| Suppliers / contracts | procurement + documents | Procurement UI + document vault | Commerce / finance | | **P** |
| Warehousing / distribution | integration-hub, msika-flow | `/enterprise/warehousing` (placeholder) | Ops | | **—** |
| Fleet / logistics | — (no single service) | `/enterprise/fleet` (placeholder) | Ops | | **—** |
| Assets & equipment | asset-registry + tuso | `/operations/assets`, `useAssets` | Admin / facility | PACS / telemedicine availability (gap) | **P** |
| Finance / billing / claims | costing + mushex | `/finance/*` | FINANCE / payer ops | Clinical: limited billing widgets | **R/P** |
| Tariffs / costing | costing-engine | `/finance/tariffs`, COSTA console | Finance | Charge sheet rules | **P** |
| Revenue / receipting | mushex + costing | Finance dashboards, reports | Finance | | **P** |
| Reconciliation | mushex / finance BFF | `/finance/reconciliation` | Payer ops | | **P** |
| Charge sheet | costing + pct + inventory | `/enterprise/charge-sheet` (guidance route) | Clinical | Inventory decrement policy | **—** |
| Enterprise dashboard | composite | `/enterprise` | Composite visibility | Aggregates facility-scoped APIs | **R** (partial metrics) |

---

## 5. Role model (shell)

Keycloak realm roles (non-exhaustive) are grouped in `ui/one-ui-shell/src/lib/auth/role-groups.ts`. Enterprise surfacing uses **any-of** role lists per nav item:

- **Storekeeper / facility supply:** `FACILITY_ADMIN`, `SYSTEM_ADMIN`, `DEVELOPER`, and clinical staff with facility context.
- **Pharmacist:** `PHARMACIST`, `FACILITY_ADMIN`, … (`DISPENSER` group).
- **Procurement:** `COMMERCE` group and/or `FINANCE`.
- **Finance:** `FINANCE`, `PAYER_OPS` subsets.
- **Clinician (limited):** `CLINICAL` — charge / coverage / stock availability surfaces only.

---

## 6. Missing endpoints / product gaps (prioritised)

1. **Requisition listing** — inventory API is lifecycle POST-heavy; list views may need read models or BFF aggregation.  
2. **Purchase order ↔ inventory receipt** — automated bridge when GRN posts (event-driven) — partial.  
3. **Charge sheet → ledger** — policy-driven decrement not universally enforced in UI.  
4. **District / national resource intelligence** — needs NDR / reporting read APIs with ABAC; not in basic shell.  
5. **Fleet / POD** — no single sovereign “logistics service” in catalog; use Integration Hub + Msika Flow until bounded context exists.  

---

## 7. Test coverage (current)

| Area | Automated tests | Gap |
|------|-----------------|-----|
| Inventory pages | `inventory/page.test.tsx`, `requisitions/page.test.tsx` | Need enterprise nav + dashboard tests (added in this wave). |
| Finance | Multiple `*.test.tsx` under `/finance` | E2E for cross-plane flows still thin. |
| ERP procurement | None | Should add contract tests when procurement service stabilises. |

---

## 8. References

- `docs/plan/SERVICE_CATALOG.md`  
- `docs/doctrine/health-os-doctrine.md`  
- `services/experience-bff/.../InventorySupplyBffController.java`  
- `services/experience-bff/.../ErpProcurementBffController.java` (if present)  
- `ui/one-ui-shell/src/hooks/queries/useInventory.ts`, `useProcurement.ts`, `useAssets.ts`

---

## 9. Change log

| Date | Change |
|------|--------|
| 2026-04-12 | Initial audit document; enterprise workspace route `/enterprise`; removal of production-misleading sample KPI rows on stock management where applicable. |
