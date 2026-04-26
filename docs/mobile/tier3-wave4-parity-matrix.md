# Mobile Parity Matrix (Tier-3 wave 4)

**Generated**: 2026-04-26T01:29:26.839Z
**Source**: `ui/one-ui-shell/src/lib/routes.ts`
**Tier**: tier3-wave4
**Scope**: provider-operations-reports-hub
**UI status**: done_ui=11 missing_ui=0 total=11
**Contract**: contract_ok=11 contract_partial=0 contract_missing=0

| Web route | Title | Guard | Mobile files | UI | Contract |
|---|---|---|---|---|---|
| `/operations` | Operations | `role` | `apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx` | DONE | contract_ok |
| `/operations/assets` | Asset Management | `role` | `apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx` | DONE | contract_ok |
| `/operations/butano` | SHR Operations | `role` | `apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx` | DONE | contract_ok |
| `/operations/equipment` | Equipment Management | `role` | `apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx` | DONE | contract_ok |
| `/operations/vito` | Identity Operations | `role` | `apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx` | DONE | contract_ok |
| `/reports` | Reports | `auth` | `apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx` | DONE | contract_ok |
| `/reports/[id]` | Report Details | `auth` | `apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx` | DONE | contract_ok |
| `/reports/clinical` | Clinical Reports | `auth` | `apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx` | DONE | contract_ok |
| `/reports/custom` | Custom Reports | `auth` | `apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx` | DONE | contract_ok |
| `/reports/facility` | Facility Reports | `auth` | `apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx` | DONE | contract_ok |
| `/reports/operational` | Operational Reports | `auth` | `apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx` | DONE | contract_ok |

## Notes

- Tier-3 wave 4 maps web **operations** and **reports** landings to provider **Tools → Ops+** plus a thin BFF hub endpoint.
