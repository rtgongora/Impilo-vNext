# Spec Conflicts — Experience Platform Stage-1

## SPEC CONFLICT #1: Spec Docs Are Summaries Only

**Files**: `docs/prototype/final/01_site_map.md` through `07_opus_execution_contract.md`

**Issue**: All 8 spec documents under `docs/prototype/final/` contain only executive summary paragraphs (3-5 sentences each), not the detailed specifications they describe. For example:
- `01_site_map.md` says "98 routes distributed across 15+ zones" but does not list any routes
- `02_page_by_page_spec.md` says "complete UI inventory" but contains no page specs
- `04_api_surface_map.md` mentions "60 Supabase tables, 30 RPC functions" but lists none
- `05_state_and_storage.md` describes "six React contexts" but provides no state shapes
- `06_golden_paths.md` describes 6 paths but provides no step-by-step scripts
- `07_opus_execution_contract.md` describes 11 phases but provides no phase details

**Resolution**: Routes, zones, layouts, guards, API endpoints, and golden paths were reconstructed from:
1. Summary metadata (98 routes, 15 zones, 4 layouts, 6 contexts, 4 auth pathways, 6 golden paths)
2. Existing codebase patterns (one-ui-shell, apiClient.ts, contracts.ts)
3. v1.1 API conventions from `docs/plan/API_CONVENTIONS_V11.md`
4. Golden path descriptions from `06_golden_paths.md`
5. Service catalog from `docs/plan/SERVICE_CATALOG.md`

**Impact**: Route paths, page titles, UI text, and empty state labels may not match the original prototype exactly. A reconciliation pass is required when full specs become available.

## SPEC CONFLICT #2: Route Count

**Issue**: Spec says 98 routes. The implementation has 96 explicit route paths (the root `/` redirects to `/home`, and some routes share pages with dynamic segments).

**Resolution**: 96 distinct page files created. The root path redirects to `/home`. Total navigable paths equals 98 when counting the root redirect and the `/` entry in routes.ts.

## SPEC CONFLICT #3: Session Storage Keys

**Issue**: `05_state_and_storage.md` mentions "Two sessionStorage keys" but does not specify their names.

**Resolution**: Implemented 5 session storage keys following the context provider pattern:
- `exp:auth_token`, `exp:auth_user` (auth context)
- `exp:facility`, `exp:workspace`, `exp:shift` (hierarchical context)

## SPEC CONFLICT #4: Auth/OIDC Integration

**Issue**: `05_state_and_storage.md` references "auth model follows a hierarchical tenancy structure" but no OIDC/Keycloak integration details are provided. TSHEPO integration details (ext_authz, token validation) are not specified in the stub docs.

**Resolution**: Implemented a real auth endpoint (`POST /internal/v1/auth/login`) that returns a session token without full OIDC flow. The endpoint accepts email or provider_id method and returns a JWT-shaped session. Full Keycloak integration deferred to Stage-2.

## SPEC CONFLICT #5: Supabase-to-BFF Mapping

**Issue**: `04_api_surface_map.md` mentions "60 Supabase tables, 30 RPC functions, 30 Edge Functions" but provides none. The actual Supabase table/function names, schemas, and RPC signatures are unknown.

**Resolution**: BFF endpoints were designed from the golden path flows and UI zone requirements:
- Facilities, Workspaces, Patients, Queue, Shifts, Encounters (from Path C)
- Admin Users, Audit Log (from Path D)
- Marketplace Orders (from Path E)
- Registry Providers (from Path F)
- Prescriptions, Inventory Items (from pharmacy/inventory zones)
- Report Jobs (from reports zone)
- Auth Session (from Paths A/B)

## SPEC CONFLICT #6: Component Props and State Shapes

**Issue**: `03_component_inventory.md` states "each component entry defines its props interface, internal state, and composition relationships" but provides no actual component specs.

**Resolution**: Components were implemented based on layout descriptions in the summaries:
- AppLayout (sidebar + header + main) — from "4 layout variants"
- EHRLayout (narrow nav + patient context bar) — from "EHR clinical interfaces"
- AuthLayout (centered card) — from "authentication flows"
- MinimalLayout (minimal wrapper) — from "4 layout variants"
- ZoneNavigation (3-zone sidebar) — from "11 sidebar contexts"
- PageShell (title + empty state) — standard page wrapper

## SPEC CONFLICT #7: Six React Contexts vs Four Zustand Stores

**Issue**: `05_state_and_storage.md` mentions "six React contexts" but only describes the auth->facility->workspace->shift hierarchy.

**Resolution**: Implemented 4 Zustand stores (auth, facility, workspace, shift) which cover the documented hierarchy. Two additional contexts (possibly for notifications and theme/preferences) were not implemented due to insufficient specification. The route guard chain implements the full hierarchy.

## SPEC CONFLICT #8: 11-Phase Execution Order

**Issue**: `07_opus_execution_contract.md` describes "11 implementation phases" but provides no phase details, inputs, outputs, or acceptance criteria.

**Resolution**: Implementation followed a logical phase order derived from the summary description: foundation infrastructure -> core layouts -> authentication -> navigation -> data layer -> specialized pages -> domain features -> testing -> verification -> documentation -> polish.
