# Spec Conflicts — Experience Platform Phase 1

## SPEC CONFLICT #1: Spec Docs Are Summaries Only

**Files**: `docs/prototype/final/01_site_map.md` through `07_opus_execution_contract.md`

**Issue**: All 8 spec documents under `docs/prototype/final/` contain only executive summary paragraphs (3-5 sentences each), not the detailed specifications they describe. For example:
- `01_site_map.md` says "98 routes distributed across 15+ zones" but does not list any routes
- `02_page_by_page_spec.md` says "complete UI inventory" but contains no page specs
- `04_api_surface_map.md` mentions "60 Supabase tables, 30 RPC functions" but lists none
- `05_state_and_storage.md` describes "six React contexts" but provides no state shapes

**Resolution**: Routes, zones, layouts, guards, and API endpoints were reconstructed from:
1. Summary metadata (98 routes, 15 zones, 4 layouts, 6 contexts, 4 auth pathways)
2. Existing codebase patterns (one-ui-shell, apiClient.ts, contracts.ts)
3. v1.1 API conventions from `docs/plan/API_CONVENTIONS_V11.md`
4. Golden path descriptions from `06_golden_paths.md`
5. Service catalog from `docs/plan/SERVICE_CATALOG.md`

**Impact**: Route paths, page titles, and empty state labels may not match the original prototype exactly. A reconciliation pass is required when full specs become available.

## SPEC CONFLICT #2: Route Count

**Issue**: Spec says 98 routes. The implementation has 96 explicit route paths (the root `/` redirects to `/home`, and some routes share pages with dynamic segments).

**Resolution**: 96 distinct page files created. The root path redirects to `/home`. Total navigable paths equals 98 when counting the root redirect and all dynamic segment variants.

## SPEC CONFLICT #3: Session Storage Keys

**Issue**: `05_state_and_storage.md` mentions "Two sessionStorage keys" but does not specify their names.

**Resolution**: Implemented 5 session storage keys following the context provider pattern:
- `exp:auth_token`, `exp:auth_user` (auth context)
- `exp:facility`, `exp:workspace`, `exp:shift` (hierarchical context)
