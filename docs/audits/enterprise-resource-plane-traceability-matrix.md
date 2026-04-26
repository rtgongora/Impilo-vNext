# Enterprise Resource Plane — Traceability Matrix

**Date:** 2026-04-12  
**Columns:** Capability · Backend Service · API Endpoint (canonical) · Frontend Surface · Mobile Surface · Events · Audit Trail · Clinical/Facility Integration · Status · Remaining Gap · Test Coverage  

**Status legend:** `Live` wired in prod path · `BFF` via experience-bff · `UI` shell route exists · `Gap` not implemented · `N/A` not applicable  

---

| Capability | Backend Service | API Endpoint | Frontend Surface | Mobile Surface | Events | Audit Trail | Clinical/Facility Integration | Status | Remaining Gap | Test Coverage |
|------------|-----------------|---------------|------------------|----------------|--------|-------------|--------------------------------|--------|---------------|---------------|
| Commodities | inventory-service, msika-service | `GET /v1/onhand`, MSIKA catalog APIs | `/inventory`, `/inventory/items`, `/finance/msika-governance` | Apps use BFF / FHIR adapters | `inventory.*` Kafka | tshepo-audit on policy decisions | Formulary ↔ dispensing | **BFF + UI** | MSIKA SKELETON in catalog; unify product keys | Partial |
| Inventory | inventory-service | `/v1/onhand`, `/v1/ledger`, `/v1/items` | `/internal/v1/inventory/**` BFF → `/inventory/*` | Offline packs (future) | Outbox `inventory.*` | Audit on POST ledger | Ward / programme stock views | **Live** | Ward/programme dimensions in UI | `inventory/page.test.tsx` |
| Pharmacy Stock | pharmacy-service | `/v1/...` dispense/stock | `/pharmacy/stock`, `/pharmacy/dispense` | Provider mobile (roadmap) | Pharmacy outbox | Dispense audit | Patient / encounter context | **UI + P** | Full FEFO UI parity with service | Limited |
| Requisitions | inventory-service | `/v1/requisitions` POST lifecycle | `/inventory/requisitions` | — | Requisition events | Service audit | Replenishment from theatre / ward | **BFF + UI** | List GET + structured create UX | `requisitions/page.test.tsx` |
| Procurement | procurement-service (via BFF) | `/internal/v1/erp/procurement/*` | `/erp/procurement`, `/enterprise` cards | — | TBD Kafka | Document + finance audit | Low-stock trigger automation | **BFF + UI** | Approval workflow UI | None |
| Suppliers | procurement + document-service | procurement supplier endpoints; `POST` documents | `/erp/procurement` | — | — | Signed supplier docs | Credential verify (future) | **P** | Performance scorecards | None |
| Contracts | document-service + rules | Object store + rule evaluation | `/enterprise` → finance/docs | — | — | Versioned artifacts | Link to procurement PO | **Gap** | Contract object model in UI | None |
| Warehousing | integration-hub, inventory | Hub dispatch APIs (env-specific) | `/enterprise/warehousing` | — | Hub events | Audit | Facility supply | **Gap** | No unified WH service | None |
| Distribution | msika-flow-service | Order state APIs | `/marketplace/vendor` | Vendor apps | `msika_flow.*` | Order audit | Delivery status to facility | **P** | POD object not universal | Some marketplace tests |
| Fleet | — | — | `/enterprise/fleet` | — | — | — | Emergency logistics | **Gap** | Bounded context missing | None |
| Assets | asset-registry-service | `/internal/v1/asset-registry/**` | `/operations/assets`, enterprise dashboard | — | Asset events | Registry audit | Tuso facility linkage | **BFF + P** | Biomedical taxonomy depth | None |
| Equipment | tuso-service, asset-registry | Facility resource APIs | `/operations/equipment` | — | Telemetry (TUSO) | — | Imaging / telemedicine availability | **Gap** | Downtime SLAs in shell | None |
| Finance (COSTA) | costing-engine-service | Tariffs, bills, claims pack | `/finance/*`, costa-console | — | COSTA outbox | Financial audit | Charge sheet | **P** | Full ABAC on patient bills in shell | Several finance tests |
| Billing | costing-engine-service | Bill lifecycle | `/finance/billing` | Citizen wallet views | Billing events | Immutable ledger | Patient encounter | **P** | Real-time eligibility everywhere | Partial |
| Claims | costing + mushex | Claims pack / submit | `/finance/claims` | — | Claims events | Payer audit | Pre-auth | **P** | Coverage-service SKELETON | Partial |
| Payments | mushex-service | Intents, settlement | `/finance/payments`, `/wallet` | Mushe mobile | `mushex.*` | Double-entry audit | Co-pay at point of care | **P** | Rail-specific reconciliation UI | Partial |
| Revenue | costing + reporting | Reporting APIs | `/finance/reports`, `/reports` | — | Pipeline | Report run audit | Programme funding | **Gap** | “Today / month” needs reporting join | finance reports tests |
| Receipting | mushex-service | Receipt APIs | `/finance/payments` | — | Wallet events | — | Cash point | **P** | Unified receipt artefact in chart | Limited |
| Tariffs | costing-engine-service | Tariff CRUD / version | `/finance/tariffs` | — | — | Version audit | Exemptions | **UI** | ZIBO mapping UX depth | `tariffs/page.test.tsx` |
| Costing | costing-engine-service | Engines / rulesets | COSTA console + finance | — | — | Rule decision logs | Per-procedure cost | **P** | Charge engine transparency in encounter | Limited |
| Charge Sheet | pct + costing + inventory | Encounter + line items + ledger POST | `/enterprise/charge-sheet` (guidance) | — | Composite | Clinical + financial audit | Consumables decrement | **Gap** | Full shell widget in encounter | None |
| Reconciliation | mushex + finance BFF | `/finance/reconciliation` | `/finance/reconciliation` | — | Settlement events | Payer ops audit | Bank ↔ claims | **P** | Auto-match rules UI | settlements tests |
| Audit | tshepo-audit-service | Query/export | `/admin/audit` | — | All planes | Hash chain | Break-glass | **Live** | Cross-plane correlation views | admin tests (partial) |

---

## Notes

- **COSTA vs MusheX:** costing-engine owns **computation & claims packing**; MusheX owns **payment capture & settlement**. The matrix keeps them on separate rows but surfaces must **cross-link** in finance workspace.  
- **Mobile Surface:** Impilo mobile workspaces are evolving; matrix marks **roadmap** unless a concrete app path exists in repo.  
- **Tests:** Shell Vitest coverage should expand for `/enterprise` navigation filtering and dashboard empty states (added 2026-04-12).  
