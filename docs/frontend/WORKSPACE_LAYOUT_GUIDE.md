# Workspace Layout Guide (Wave 20 + Adaptive Workspace remediation)

> UX packaging patterns for Impilo one-ui-shell production readiness

## Adaptive workspace principles (2026-07 remediation)

Screen space is a limited operational resource. Every page must give the
active task the largest practical workspace:

- **Forms**: use `FormGrid` / `FormField` / `FormSection` (shared-ui) — short
  fields side by side (2–3 columns ≥sm/≥lg), narrative fields `span="full"`,
  single column on phones. Never stretch a date/code/select across the page.
- **Long forms/wizards**: keep terminal actions reachable with
  `StickyActionBar` (taskbar + safe-area aware, one per screen). Use the
  shared `Stepper` for multi-stage flows — horizontal ≥sm, automatic compact
  step-counter variant on phones.
- **Cards**: size to content. `Card density="compact"` (or `padding="xs"`) for
  title/status/action content; `AdaptiveGrid` for dashboards (auto-fit
  columns, no oversized fixed grids).
- **Overflow**: when a page genuinely scrolls, make it obvious — `MoreBelow`
  cue (hides at end, tap scrolls forward, never hover-only).
- **Split layouts**: `SplitView` for work + context panel (stacks on narrow
  viewports, primary pane keeps reading order). Full-height apps (tables,
  boards, maps, studios) use `FullHeightWorkspace` + `WorkspaceScrollPane` —
  ONE internal scroll region, no nested/competing scrollbars.
- **Shell behaviour** (automatic — don't fight it): the NavRail expands on
  landing/hub surfaces and auto-compacts to icons inside opened applications
  (`src/lib/shell/workspace-context.ts` decides; user preference and focus
  mode override). `PageShell` heroes auto-compact on focused-work routes.
  The taskbar minimises to a restore handle (Ctrl+Alt+B / focus mode).
- **State preservation hard gate**: collapsing navigation, minimising the
  taskbar or Nompilo, resizing, or rotating must never clear entered data,
  reset a wizard, or lose patient/facility context. Keep layout state in
  `useLayoutPrefsStore` / `useAssistantUiStore`, never in page state.

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
