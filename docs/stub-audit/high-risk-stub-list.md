# High-Risk Stub List

**Date**: 2026-03-23
**Scope**: All components flagged as STUB, MOCK-DATA-DRIVEN, SHELL-ONLY, or THIN

---

## Risk Classification

| Risk Level | Definition |
|-----------|------------|
| **HIGH** | Non-functional route that a user could navigate to; no fallback |
| **MEDIUM** | Page works but with hardcoded data instead of real integration |
| **LOW** | Minor incomplete section within an otherwise functional page |
| **INFO** | Intentional design (e.g., navigation shell, redirect) |

---

## Flagged Items

### 1. SHELL-ONLY: msika-flow-ops Orders Page
- **File**: `ui/msika-flow-ops/src/app/(ops)/orders/page.tsx`
- **Risk**: HIGH
- **Lines**: 15 total
- **Problem**: Route `/orders` exists in navigation but renders only static text: "Use the search bar to find orders by ID, patient CPID, or status." There is no search bar, no API integration, no functionality.
- **Expected**: Should query msika-flow-service `/v1/orders` with search, pagination, and status filtering.
- **Impact**: Ops users navigating to this page see a dead-end.

### 2. MOCK-DATA-DRIVEN: msika-flow-portal Browse Page
- **File**: `ui/msika-flow-portal/src/app/(portal)/browse/page.tsx`
- **Risk**: MEDIUM
- **Lines**: 139 total
- **Problem**: `SAMPLE_ITEMS` array (lines 14-23) contains 8 hardcoded OTC items (Paracetamol 500mg, Ibuprofen 400mg, etc.) with fixed prices. Filtering is done against this local array. The page does call `msikaFlowApi.validateCart()` for cart validation, so backend integration is partial.
- **Expected**: Should fetch catalog items from msika-service or msika-flow-service catalog API.
- **Impact**: Users see only 8 fixed items regardless of actual catalog content.

### 3. SHELL-ONLY: ops-console VITO Dashboard Section
- **File**: `ui/ops-console/src/app/(ops)/vito/page.tsx`
- **Risk**: LOW
- **Lines**: 67 total
- **Problem**: The "Recent Registrations" section (line 56-63) displays: "Client list and search functionality will be rendered here. Connect to VITO API: /v1/clients" — a developer TODO that shipped to UI.
- **Expected**: Should show recent client registrations from vito-service API.
- **Impact**: Low — other VITO sub-pages (clients, cards, dedup, issuance, match-queue, config) are all fully functional.

### 4. THIN: EHR Encounter Creation
- **File**: `ui/ehr/src/stores/ehrStore.ts`
- **Risk**: LOW
- **Lines**: 92 total
- **Problem**: `startEncounter()` (lines 77-89) creates an encounter object locally with `crypto.randomUUID()` instead of posting to the backend. Patient search uses real API via `@tanstack/react-query`.
- **Expected**: Encounters should be created via pct-service or experience-bff API.
- **Impact**: Low — this EHR app appears to be superseded by the much larger `ui/experience` app which handles encounters properly.

### 5. INFO: alert() Error Handling in msika-web
- **Files**: `ui/msika-web/src/app/catalogs/page.tsx:46,57`, `items/page.tsx:50`, `import/page.tsx:13,19`, `publish/page.tsx:40`, `mappings/page.tsx:42`
- **Risk**: INFO
- **Problem**: Uses `alert(String(e))` for error display in catch blocks. Workflows themselves are real.
- **Expected**: Should use toast/notification component for error display.
- **Impact**: UX only — no data integrity issue.

---

## Remediation Priority Queue

| Priority | Item | Effort | Dependency |
|----------|------|--------|-----------|
| 1 | msika-flow-ops orders page | ~2h | msika-flow-service API |
| 2 | msika-flow-portal browse page | ~2h | msika-service catalog API |
| 3 | ops-console VITO dashboard section | ~1h | vito-service /v1/clients API |
| 4 | EHR encounter creation | ~1h | pct-service API (or document as superseded) |
| 5 | msika-web alert() → toast migration | ~1h | Shared UI toast component |

---

## Items NOT Flagged (False Positives Excluded)

| Item | Why Excluded |
|------|-------------|
| developer-console SAMPLE_PAYLOADS | Intentional — sample API payloads for a sandbox testing tool |
| experience home QUICK_ACTIONS | Navigation links, not mock data |
| one-ui-shell | Intentional shell — app frame by design |
| Redirect pages (experience /, costa-console /) | Standard Next.js routing |
| placeholder= attributes in input fields | HTML input placeholders, not content placeholders |
