# 03 — Component Inventory (Index & Contract)

> **Document type**: Index document — points to canonical spec sources for UI component definitions.
> **Last updated**: 2026-03-11
> **Status**: CANONICAL INDEX — enforced by `scripts/spec-integrity-check.sh`

---

## Purpose

This document indexes the component inventory for the Experience Platform UI. The actual component implementations are the source of truth; this file maps the component architecture to its canonical locations.

## Summary Constraints

- **4 layout variants**: AppLayout, EHRLayout, AuthLayout, MinimalLayout
- **6 layout components**: AppLayout, AppSidebar, AppHeader, EHRLayout, TopBar, EncounterMenu
- **11 sidebar contexts**: dynamically resolved from URL path
- Component groups: authentication flows, home/dashboard views, EHR clinical interfaces, patient search, navigation shells, notification systems, emergency workflows
- Each component: props interface, internal state, composition relationships

## Canonical Sources

### Component Implementations (Source of Truth)

All UI components live under the Experience UI source tree:

| Directory | Component Group | Description |
|-----------|----------------|-------------|
| `ui/experience/src/components/layouts/` | Layout variants | AppLayout, EHRLayout, AuthLayout, MinimalLayout |
| `ui/experience/src/components/navigation/` | Navigation shells | AppSidebar, ZoneNavigation, breadcrumbs |
| `ui/experience/src/components/shared/` | Shared UI | PageShell, DataTable, FormFields, Dialogs |
| `ui/experience/src/app/auth/` | Auth flows | Login, registration, password reset pages |
| `ui/experience/src/app/home/` | Dashboard | Home dashboard, activity feed |
| `ui/experience/src/app/ehr/` | EHR clinical | Encounter views, clinical forms, patient timeline |
| `ui/experience/src/app/queue/` | Queue | Patient queue management |
| `ui/experience/src/app/admin/` | Admin/governance | User management, audit, role assignment |

### Design System Foundation

| Technology | Purpose |
|-----------|---------|
| shadcn/ui | Component primitives (built on Radix UI) |
| Tailwind CSS 3.4 | Utility-first styling |
| Lucide icons | Icon system |
| Framer Motion | Animations and transitions |
| TanStack Query | Server state management, cache invalidation |
| Zustand | Client state management (4 stores) |

### Layout Architecture

The 4 layout variants and their composition:

| Layout | Wrapper | Sidebar | Header | Use Case |
|--------|---------|---------|--------|----------|
| `app` | AppLayout | AppSidebar (3-zone) | AppHeader | Main application pages |
| `ehr` | EHRLayout | Narrow nav | TopBar + EncounterMenu | Clinical encounter pages |
| `auth` | AuthLayout | None | None | Login, registration |
| `minimal` | MinimalLayout | None | Minimal header | Error pages, loading |

### Sidebar Context Resolution

11 sidebar contexts dynamically resolved from URL path:

| Context | URL Prefix | Nav Items |
|---------|-----------|-----------|
| `main` | `/home`, `/reports` | Home, Reports, Quick actions |
| `facility` | `/facility` | Facility details, resources, calendar |
| `workspace` | `/workspace`, `/pharmacy`, `/inventory` | Workspace tools, patient lists |
| `shift` | `/shift` | Shift details, handover |
| `queue` | `/queue` | Queue filters, priority views |
| `ehr` | `/ehr` | Patient chart, encounter tools, clinical forms |
| `admin` | `/admin` | Users, Roles, Audit, Settings |
| `registry` | `/registry` | Providers, Facilities, Products |
| `marketplace` | `/marketplace` | Orders, Catalog, Vendors |
| `finance` | `/finance` | Claims, Payments, Ledger |
| `settings` | `/settings` | Profile, Preferences, System |

### Architectural Spec References

| Canonical File | What It Provides |
|----------------|-----------------|
| [IMPILO_VNEXT_BUILD_PLAN.md](../../plan/IMPILO_VNEXT_BUILD_PLAN.md) | Bundle X component list, dependency ordering |
| [SERVICE_CATALOG.md](../../plan/SERVICE_CATALOG.md) | Service definitions informing component boundaries |
| [API_CONVENTIONS_V11.md](../../plan/API_CONVENTIONS_V11.md) | API patterns that drive data-fetching component design |
| [TESTING_CONVENTIONS.md](../../plan/TESTING_CONVENTIONS.md) | Component testing patterns |
| [EVENTING_AND_TOPICS.md](../../plan/EVENTING_AND_TOPICS.md) | Event-driven UI update patterns |
| [03-architecture-diagram.md](../../architecture/v1.1/03-architecture-diagram.md) | System topology driving layout decisions |
| [04-service-boundaries.md](../../architecture/v1.1/04-service-boundaries.md) | Ring boundaries informing UI zone separation |
| [experience-platform-acceptance-pack.md](../../acceptance/experience-platform-acceptance-pack.md) | Component verification through golden path smoke tests |

## Spec Conflicts

- **Conflict #6** (from [compose/experience/SPEC_CONFLICTS.md](../../../compose/experience/SPEC_CONFLICTS.md)): Original `03_component_inventory.md` stated "each component entry defines its props interface, internal state, and composition relationships" but provided no actual specs. Components were implemented based on layout descriptions from summaries.

## Contract Statement

> The component implementations under `ui/experience/src/` are the source of truth for component behavior, props, and composition. This index document describes the architectural structure. Any future fully-detailed component inventory must be generated from or reconciled against the actual TypeScript source files.
