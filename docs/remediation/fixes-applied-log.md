# Fixes Applied Log — Adversarial Remediation Wave

**Date:** 2026-03-24
**Branch:** claude/review-project-manifest-jb5O0

## Fix Log

### Fix 1: MSIKA Flow Ops — Orders Page
- **File:** `ui/msika-flow-ops/src/app/(ops)/orders/page.tsx`
- **Before:** Shell with static text "Use the search bar to find orders..."
- **After:** Full order search with query input, status filter, paginated results table, order detail view
- **API wiring:** `opsApi.searchOrders()` → `GET /v1/orders?q=...&status=...&page=...&size=...`
- **API wiring:** `opsApi.getOrder()` → `GET /v1/orders/{id}`
- **Supporting change:** Added `searchOrders` method to `ui/msika-flow-ops/src/lib/opsApi.ts`

### Fix 2: MSIKA Flow Ops — Vendors Page
- **File:** `ui/msika-flow-ops/src/app/(ops)/vendors/page.tsx`
- **Before:** Shell with static text "Vendor list will appear here"
- **After:** Vendor list with status filtering, suspend (with reason) and reinstate actions, pagination
- **API wiring:** `opsApi.listVendors()` → `GET /v1/vendors?status=...&page=...&size=...`
- **API wiring:** `opsApi.suspendVendor()` → `POST /v1/vendors/{id}/suspend`
- **API wiring:** `opsApi.reinstateVendor()` → `POST /v1/vendors/{id}/reinstate`
- **Supporting change:** Added `listVendors`, `getVendor`, `reinstateVendor` methods to opsApi.ts

### Fix 3: MSIKA Flow Ops — Audit Page
- **File:** `ui/msika-flow-ops/src/app/(ops)/audit/page.tsx`
- **Before:** Shell with static text about Kafka topics
- **After:** Paginated audit event log with event type filtering, payload viewer, expandable details
- **API wiring:** `opsApi.listAuditEvents()` → `GET /v1/ops/audit?eventType=...&page=...&size=...`
- **Supporting change:** Added `listAuditEvents` method and `AuditEvent` type to opsApi.ts

### Fix 4: MSIKA Flow Portal — Browse Page
- **File:** `ui/msika-flow-portal/src/app/(portal)/browse/page.tsx`
- **Before:** Hardcoded `SAMPLE_ITEMS` array with 8 fake products
- **After:** Real catalog search with text query, kind filter, paginated grid, cart with validation
- **API wiring:** `msikaFlowApi.searchCatalog()` → `GET /v1/catalog/search?q=...&kind=...&page=...&size=...`
- **Supporting change:** Added `searchCatalog` method and `CatalogItem`, `PagedCatalogResponse` types to msikaFlowApi.ts

### Fix 5: MSIKA Flow Portal — Cart/Checkout Page
- **File:** `ui/msika-flow-portal/src/app/(portal)/cart/page.tsx`
- **Before:** Empty state with "Your cart is empty" and no functionality
- **After:** Full checkout flow: order lookup → validate → price → pay → cancel
- **API wiring:** `msikaFlowApi.getOrder()`, `validateOrder()`, `priceOrder()`, `payOrder()`, `cancelOrder()`
- **Lifecycle:** CREATED → VALIDATED → PRICED → PAID/CANCELLED

### Fix 6: MSIKA Flow Portal — Substitutions Page
- **File:** `ui/msika-flow-portal/src/app/(portal)/substitutions/page.tsx`
- **Before:** Empty state with "No pending substitution requests"
- **After:** Substitution approval workflow with status filtering, approve/reject actions with reason
- **API wiring:** `msikaFlowApi.listSubstitutions()` → `GET /v1/rx/substitutions?status=...`
- **API wiring:** `msikaFlowApi.approveSubstitution()` → `POST /v1/rx/{orderId}/substitution/approve`
- **API wiring:** `msikaFlowApi.rejectSubstitution()` → `POST /v1/rx/{orderId}/substitution/reject`
- **Supporting change:** Added `listSubstitutions`, `rejectSubstitution` methods and `SubstitutionRequest` type

### Fix 7: VITO Dashboard — Recent Registrations
- **File:** `ui/ops-console/src/app/(ops)/vito/page.tsx`
- **Before:** Placeholder text "Client list and search functionality will be rendered here"
- **After:** Real recent registrations table from VITO API with client count, status badges, link to full client search
- **API wiring:** `vitoApi.listClients(0, 10)` → `GET /api/v1/clients?page=0&size=10`

### Fix 8: BUTANO Reconciliation Queue — API-Backed List
- **File:** `ui/butano-web/src/app/(ops)/reconciliation/page.tsx`
- **Before:** Jobs loaded from `sessionStorage` (browser-only, lost on refresh, not shared)
- **After:** Jobs loaded from backend API with server-side pagination
- **API wiring:** `butanoApi.listReconciliationJobs()` → `GET /v1/internal/reconciliation?page=...&size=...`
- **Supporting change:** Added `listReconciliationJobs` method to butanoApi.ts
- **Preserved:** Auto-refresh every 10s for in-progress jobs

## Files Modified (Complete List)

| File | Action | Description |
|------|--------|-------------|
| ui/msika-flow-ops/src/lib/opsApi.ts | MODIFIED | Added 5 API methods + 2 types |
| ui/msika-flow-ops/src/app/(ops)/orders/page.tsx | REWRITTEN | Shell → real order management |
| ui/msika-flow-ops/src/app/(ops)/vendors/page.tsx | REWRITTEN | Shell → real vendor management |
| ui/msika-flow-ops/src/app/(ops)/audit/page.tsx | REWRITTEN | Shell → real audit log |
| ui/msika-flow-portal/src/lib/msikaFlowApi.ts | MODIFIED | Added 3 API methods + 3 types |
| ui/msika-flow-portal/src/app/(portal)/browse/page.tsx | REWRITTEN | Hardcoded → real catalog search |
| ui/msika-flow-portal/src/app/(portal)/cart/page.tsx | REWRITTEN | Empty → real checkout flow |
| ui/msika-flow-portal/src/app/(portal)/substitutions/page.tsx | REWRITTEN | Empty → real substitution approvals |
| ui/ops-console/src/app/(ops)/vito/page.tsx | REWRITTEN | Placeholder → real registrations |
| ui/butano-web/src/lib/butanoApi.ts | MODIFIED | Added listReconciliationJobs |
| ui/butano-web/src/app/(ops)/reconciliation/page.tsx | REWRITTEN | sessionStorage → API-backed |
