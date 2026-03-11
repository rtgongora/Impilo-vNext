# 01 — Site Map (Index & Contract)

> **Document type**: Index document — points to canonical spec sources for route and zone definitions.
> **Last updated**: 2026-03-11
> **Status**: CANONICAL INDEX — enforced by `scripts/spec-integrity-check.sh`

---

## Purpose

This document indexes the site map specification for the Impilo vNext Experience Platform. The detailed route registry is defined in code and derived from the canonical plan specs. This file maps the "what" to "where it lives."

## Summary Constraints

- **98 routes** across **15+ zones**
- **4 layout variants**: `app`, `ehr`, `auth`, `minimal`
- **11 sidebar contexts**: `main`, `facility`, `workspace`, `shift`, `queue`, `ehr`, `admin`, `registry`, `marketplace`, `finance`, `settings`
- **6 guard types**: `none`, `auth`, `facility`, `workspace`, `shift`, `role`
- Sidebar context resolved dynamically from URL path
- Route guard rules enforce access control at the navigation layer

## Canonical Sources

### Route Registry (Implementation Source of Truth)

The complete route table with all 98 entries is defined in:

- **[ui/experience/src/lib/routes.ts](../../../ui/experience/src/lib/routes.ts)** — 174 lines
  - Each entry: `path`, `zone`, `layout`, `sidebar`, `guard`, `requiredRole`, `pageTitle`, `navLabel`
  - Route parity check: `npm run test:routes` in `ui/experience/` (expects 98/98)

### Route Parity Verification

- **[ui/experience/scripts/route-parity-check.mjs](../../../ui/experience/scripts/route-parity-check.mjs)** — verifies `src/app/` directory structure matches route registry
- Expected output: `98/98 route parity`

### Zone and Service Architecture

The zone definitions and service-to-zone mappings are derived from:

| Canonical File | What It Provides |
|----------------|-----------------|
| [SERVICE_CATALOG.md](../../plan/SERVICE_CATALOG.md) | Service names, module paths, ports, rings, bundle assignments |
| [IMPILO_VNEXT_BUILD_PLAN.md](../../plan/IMPILO_VNEXT_BUILD_PLAN.md) | Bundle X (Experience) component list, dependency graph |
| [04-service-boundaries.md](../../architecture/v1.1/04-service-boundaries.md) | Ring definitions, service boundary rules |
| [03-architecture-diagram.md](../../architecture/v1.1/03-architecture-diagram.md) | System topology, 6-plane model |

### Zone List (15 zones)

Zones as implemented in `ui/experience/src/app/`:

| # | Zone | Route Prefix | Layout | Primary Sidebar |
|---|------|-------------|--------|----------------|
| 1 | Auth | `/auth/*` | auth | — |
| 2 | Home | `/home` | app | main |
| 3 | Facility | `/facility/*` | app | facility |
| 4 | Workspace | `/workspace/*` | app | workspace |
| 5 | Shift | `/shift/*` | app | shift |
| 6 | Queue | `/queue/*` | app | queue |
| 7 | EHR | `/ehr/*` | ehr | ehr |
| 8 | Admin | `/admin/*` | app | admin |
| 9 | Registry | `/registry/*` | app | registry |
| 10 | Marketplace | `/marketplace/*` | app | marketplace |
| 11 | Finance | `/finance/*` | app | finance |
| 12 | Settings | `/settings/*` | app | settings |
| 13 | Pharmacy | `/pharmacy/*` | app | workspace |
| 14 | Inventory | `/inventory/*` | app | workspace |
| 15 | Reports | `/reports/*` | app | main |

### App Directory Structure

All 98 routes have corresponding `page.tsx` files under `ui/experience/src/app/` following the Next.js App Router conventions. Dynamic segments use `[id]` folder syntax.

## Spec Conflicts

- **Conflict #1** (from [compose/experience/SPEC_CONFLICTS.md](../../../compose/experience/SPEC_CONFLICTS.md)): Original `01_site_map.md` said "98 routes" but listed none. Routes were reconstructed from zone count, auth model, golden paths, and codebase patterns.
- **Conflict #2**: Implementation has 96 explicit route paths; root `/` redirects to `/home`, total navigable paths = 98 counting the root redirect and `/` entry.

## Contract Statement

> The route registry at `ui/experience/src/lib/routes.ts` is the **single source of truth** for route definitions. Any discrepancy between this file and documentation must be resolved in favor of the route registry, then documentation updated to match. The route parity check (`npm run test:routes`) enforces this.
