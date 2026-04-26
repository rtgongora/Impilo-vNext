# Mobile Parity Matrix (Tier-3 wave 5)

**Generated**: 2026-04-26T01:29:26.841Z
**Source**: `ui/one-ui-shell/src/lib/routes.ts`
**Tier**: tier3-wave5
**Scope**: provider-developer-portal-hub
**UI status**: done_ui=4 missing_ui=0 total=4
**Contract**: contract_ok=4 contract_partial=0 contract_missing=0

| Web route | Title | Guard | Mobile files | UI | Contract |
|---|---|---|---|---|---|
| `/developer` | Developer Portal | `role` | `apps/mobile/provider-app/src/screens/provider/DeveloperHubScreen.tsx` | DONE | contract_ok |
| `/developer/api-catalog` | API Catalog | `role` | `apps/mobile/provider-app/src/screens/provider/DeveloperHubScreen.tsx` | DONE | contract_ok |
| `/developer/clients` | Client Registration | `role` | `apps/mobile/provider-app/src/screens/provider/DeveloperHubScreen.tsx` | DONE | contract_ok |
| `/developer/sandbox` | Sandbox | `role` | `apps/mobile/provider-app/src/screens/provider/DeveloperHubScreen.tsx` | DONE | contract_ok |

## Notes

- Tier-3 wave 5 maps web **developer** portal landings to provider **Tools → Dev** plus a thin BFF hub endpoint.
