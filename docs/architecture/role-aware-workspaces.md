# Role-Aware Workspaces

## Role Navigation Model

- Client navigation points to person journey routes.
- Provider navigation points to queue/ehr/workspace routes.
- Manager navigation points to operations/finance/reporting/governance routes.

## Implementation

- Centralized in `ui/one-ui-shell/src/lib/ui-route-journey-map.ts`.
- Rendered by `ui/one-ui-shell/src/components/navigation/RoleJourneyNavigation.tsx`.
- Added in `AppLayout` to keep route discoverability consistent.

## Why

- Prevent module-list disorientation.
- Make route groupings visible without deleting existing paths.
- Preserve all current domain pages while improving user mental model.

