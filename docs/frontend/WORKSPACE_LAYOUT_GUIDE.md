# Workspace Layout Guide (Wave 20)

> UX packaging patterns for Impilo one-ui-shell production readiness

## PlaneWorkspaceShell

Location: `ui/one-ui-shell/src/components/workspace/PlaneWorkspaceShell.tsx`

Use for any **plane hub or sub-workspace** that needs:

- Plane label + maturity badge
- Optional horizontal tabs
- Main content + right rail (context, related services, Nompilo)

## ContextRail

Shows actor, facility, workspace, shift, and optional patient/encounter/transaction labels from `ExperienceEntryProvider`.

## RelatedServicesPanel

Cross-plane deep links — use for journey handoffs (e.g. Rx → finance → Nhume).

## TrustContextBanner

Show on P0 transaction workspaces; links to trust admin.

## NompiloContextPanel

Embeds contextual `/ask` link with `?from=` pathname and optional `plane=` query.

## Production Command Centre

- Route: `/production-command-centre`
- Tile registry: `src/features/production-command-centre/tile-registry.ts`
- Update tiles when adding P0 routes; regenerate parity docs

## When to create a new shell vs link

- **New shell**: scattered capabilities same user journey (inpatient, data-intelligence)
- **Link only**: mature standalone page (e.g. `/pharmacy`) — add to command centre tiles

## Maturity rules

All shells must show `FeatureMaturityBadge` when data may be Partial/Blocked. Never imply Live on fixture data.
