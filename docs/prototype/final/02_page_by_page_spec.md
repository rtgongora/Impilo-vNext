# 02 — Page-by-Page Specification (Index & Contract)

> **Document type**: Index document — points to canonical spec sources for page-level implementation details.
> **Last updated**: 2026-03-11
> **Status**: CANONICAL INDEX — enforced by `scripts/spec-integrity-check.sh`

---

## Purpose

This document indexes the page-by-page specification for all 98 routes in the Experience Platform. Detailed page implementations live in the codebase; architectural and behavioral specs live in the canonical plan docs.

## Summary Constraints

- **98 routes** with full page implementations
- Each page: display strings, layout assignment, component tree, icon usage
- Data interactions: API calls, mutations, cache invalidation
- Toast messages, form validation rules, and confirmation dialogs per page

## Canonical Sources

### Page Implementations (Source of Truth for UI Behavior)

All page files live under the Next.js App Router directory:

- **`ui/experience/src/app/`** — 125 TypeScript/TSX files total
  - Each zone has a directory (e.g., `auth/`, `home/`, `facility/`, `ehr/`, `admin/`)
  - Each route has a `page.tsx` defining its component tree, data fetching, and UI behavior
  - Dynamic routes use `[id]/` directory convention

### Route-to-Page Mapping

The authoritative mapping from route paths to page files is defined in:

- **[ui/experience/src/lib/routes.ts](../../../ui/experience/src/lib/routes.ts)** — complete route registry
- **[ui/experience/scripts/route-parity-check.mjs](../../../ui/experience/scripts/route-parity-check.mjs)** — verifies every route has a corresponding `page.tsx`

### API Integration Patterns

Page-level data interactions follow the patterns defined in:

| Canonical File | What It Provides |
|----------------|-----------------|
| [API_CONVENTIONS_V11.md](../../plan/API_CONVENTIONS_V11.md) | v1.1 header contract, error envelope format, pagination, idempotency |
| [ui/experience/src/lib/api-client.ts](../../../ui/experience/src/lib/api-client.ts) | Trust header injection, BFF API client, request/response handling |

### BFF Endpoints (Backend Behavior)

The BFF controller layer defines the server-side behavior for each page's data needs:

| Controller | Path | Covers |
|-----------|------|--------|
| `AuthController.java` | `/internal/v1/auth/*` | Login, session management |
| `FacilityController.java` | `/internal/v1/facilities/*` | Facility CRUD, selection |
| `WorkspaceController.java` | `/internal/v1/workspaces/*` | Workspace management |
| `ShiftController.java` | `/internal/v1/shifts/*` | Shift lifecycle |
| `PatientController.java` | `/internal/v1/patients/*` | Patient search, demographics |
| `QueueController.java` | `/internal/v1/queue/*` | Queue management |
| `EncounterController.java` | `/internal/v1/encounters/*` | Clinical encounters |
| `ProviderController.java` | `/internal/v1/providers/*` | Provider registry browse |
| `ReportController.java` | `/internal/v1/reports/*` | Report generation |
| `AdminController.java` | `/internal/v1/admin/*` | Admin/governance |

All controllers are in: `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/`

### Behavioral Spec References

| Canonical File | What It Provides |
|----------------|-----------------|
| [TESTING_CONVENTIONS.md](../../plan/TESTING_CONVENTIONS.md) | Testing patterns for page-level behavior verification |
| [SPEC_CONFLICTS.md](../../../compose/experience/SPEC_CONFLICTS.md) | Conflict #1: pages reconstructed from summaries, not detailed specs |
| [experience-platform-acceptance-pack.md](../../acceptance/experience-platform-acceptance-pack.md) | Golden path smoke checklist for manual page-by-page verification |

## Spec Conflicts

- **Conflict #1**: Original `02_page_by_page_spec.md` described "complete UI inventory" but contained no page specs. Pages were reconstructed from zone count, golden path flows, and service catalog.
- **Conflict #6**: Component props and state shapes were not specified; implemented based on layout descriptions from summaries.

## Contract Statement

> The page implementations under `ui/experience/src/app/` are the source of truth for page-level behavior. The route registry (`routes.ts`) defines the route-to-page mapping. API behavior is defined by the BFF controllers. Any future "page-by-page spec" document that aims to be fully detailed must be reconciled against these implementation artifacts.
