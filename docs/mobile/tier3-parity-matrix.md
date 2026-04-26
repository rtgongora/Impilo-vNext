# Mobile Parity Matrix (Tier-3 wave 1)

**Generated**: 2026-04-26T01:29:26.831Z
**Source**: `ui/one-ui-shell/src/lib/routes.ts`
**Tier**: tier3
**Scope**: provider=facility/workspace/shift + /scheduling*; citizen=facility
**UI status**: done_ui=13 missing_ui=0 total=13
**Contract**: contract_ok=13 contract_partial=0 contract_missing=0

## Citizen app

| Web route | Title | Zone | Guard | Mobile files | UI | Contract |
|---|---|---|---|---|---|---|
| `/facility` | Select Facility | `facility` | `auth` | `apps/mobile/citizen-app/src/screens/FacilityDirectoryScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/FacilityDetailScreen.tsx` | DONE | contract_ok |
| `/facility/[id]` | Facility Details | `facility` | `auth` | `apps/mobile/citizen-app/src/screens/FacilityDirectoryScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/FacilityDetailScreen.tsx` | DONE | contract_ok |

## Provider app

| Web route | Title | Zone | Guard | Mobile files | UI | Contract |
|---|---|---|---|---|---|---|
| `/facility` | Select Facility | `facility` | `auth` | `apps/mobile/provider-app/src/screens/SelectFacilityScreen.tsx` | DONE | contract_ok |
| `/facility/[id]` | Facility Details | `facility` | `auth` | `apps/mobile/provider-app/src/screens/SelectFacilityScreen.tsx` | DONE | contract_ok |
| `/scheduling` | Scheduling | `queue` | `workspace` | `apps/mobile/provider-app/src/screens/provider/ScheduleScreen.tsx` | DONE | contract_ok |
| `/scheduling/noticeboard` | Provider Noticeboard | `queue` | `workspace` | `apps/mobile/provider-app/src/screens/provider/ScheduleScreen.tsx` | DONE | contract_ok |
| `/scheduling/on-call` | On-Call Schedule | `queue` | `workspace` | `apps/mobile/provider-app/src/screens/provider/ScheduleScreen.tsx` | DONE | contract_ok |
| `/scheduling/roster` | Staff Roster | `queue` | `workspace` | `apps/mobile/provider-app/src/screens/provider/ScheduleScreen.tsx` | DONE | contract_ok |
| `/shift` | Start Shift | `shift` | `workspace` | `apps/mobile/provider-app/src/screens/provider/ScheduleScreen.tsx`<br/>`apps/mobile/provider-app/src/screens/provider/ShiftHandoffScreen.tsx` | DONE | contract_ok |
| `/shift/active` | Active Shift | `shift` | `facility` | `apps/mobile/provider-app/src/screens/provider/ScheduleScreen.tsx`<br/>`apps/mobile/provider-app/src/screens/provider/ShiftHandoffScreen.tsx` | DONE | contract_ok |
| `/shift/handover` | Shift Handover | `shift` | `facility` | `apps/mobile/provider-app/src/screens/provider/ScheduleScreen.tsx`<br/>`apps/mobile/provider-app/src/screens/provider/ShiftHandoffScreen.tsx` | DONE | contract_ok |
| `/workspace` | Select Workspace | `workspace` | `facility` | `apps/mobile/provider-app/src/screens/SelectWorkspaceScreen.tsx` | DONE | contract_ok |
| `/workspace/[id]` | Workspace Details | `workspace` | `facility` | `apps/mobile/provider-app/src/screens/SelectWorkspaceScreen.tsx` | DONE | contract_ok |

## Notes

- Tier-3 wave 1 is intentionally scoped to ops foundation (provider) + facility baseline (citizen).

- **Contract** uses typed components in `contracts/openapi/mobile-*.openapi.yaml` (facilities/workspaces + envelopes); `contract_partial` / `contract_missing` indicate drift from those schemas.
