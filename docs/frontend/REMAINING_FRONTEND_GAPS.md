# Remaining Frontend Gaps

> Prioritized backlog after parity sweep 2026-05-28. Updated after gap-fix wave and follow-up completion.

**Mandatory policy:** [`GAP_CLOSURE_RULES.md`](./GAP_CLOSURE_RULES.md) — **no stubs, no mocks**; ship full functionality only or do not close the gap.

## P0 — High

| ID | Gap | Status | Notes |
|----|-----|--------|-------|
| GAP-001 | UBOMI BFF bridge | **Closed (UI)** | `UbomiController` + `/ubomi` tabs (birth/death/verify) + `useUbomi` hooks |
| GAP-002 | Core transaction mobile journey | **Partial** | `CoreTransactionCompositionService` + doctrine web shell; dispatch deliveries merged in composition; mobile handoff in Tools → Core Tx |
| GAP-003 | Dispatch vs Nhume dual paths | **Partial** | Nhume uses `useNhume` + nhume-service (restored); dispatch BFF for ops only — not interchangeable |
| GAP-004 | Citizen conditions/allergies BFF | **Closed** | `CitizenHealthSummaryService` + web `/internal/v1/citizen/health-summary` + `/home/conditions` & `/home/allergies` |
| GAP-005 | VARAPI verification queue | **Closed (UI)** | `/registry/providers/verification` + status filter/transitions |

## P1 — Medium

| ID | Gap | Status | Notes |
|----|-----|--------|-------|
| GAP-006 | TUSO control-tower / digital readiness | **Partial** | Facility ops hub shows TUSO registry readiness strip + control tower link |
| GAP-007 | Ndila ops map dashboards | **Partial** | `NdilaController` + `useNdila` + `OpsMapPanel`; tiles require live Ndila (no mock fallback) |
| GAP-008 | Comms template/campaign admin | **Partial** | Omnichannel **Campaigns** tab + BFF `GET/POST /omnichannel/campaigns` |
| GAP-009 | ZIBO in main shell | **Closed (link)** | Registry hub **ZIBO Studio** external card (`NEXT_PUBLIC_ZIBO_URL`) |
| ~~GAP-010~~ | ~~`ui/experience` fork drift~~ | **CLOSED** | Merged into `one-ui-shell` |

## P2 — Lower

| ID | Gap | Status | Notes |
|----|-----|--------|-------|
| GAP-011 | Social moderation admin | **Closed (prior)** | `/social/moderation` + `SocialController` + `useSocial` |
| GAP-012 | Notification template admin | **Closed (UI)** | `/admin/notifications/templates` + `NotificationTemplateController` |
| GAP-013 | Integration adapter templates | **Closed (UI)** | `/admin/integration-templates` + mapping-templates BFF |

## Route registry

- **400** routes in `src/lib/routes.ts` (`EXPECTED_ROUTE_COUNT = 400`)
- Recent: `/home/conditions`, `/home/allergies`, `/registry/providers/verification`, admin template routes

## Residual (non-blocking)

- Full Leaflet/MapLibre Ndila client (current: CSS tile preview + marker list)
- TUSO deep control-tower screens beyond readiness strip
- Omnichannel campaign authoring UX polish
- Citizen `/home/results` quick-action route (linked from home hub, page may still be stub)
