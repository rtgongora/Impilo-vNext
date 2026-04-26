# Mobile Parity Matrix (Tier-3 wave 7)

**Generated**: 2026-04-26T01:29:26.845Z
**Source**: `ui/one-ui-shell/src/lib/routes.ts`
**Tier**: tier3-wave7
**Scope**: provider-professional-channels-hub
**UI status**: done_ui=7 missing_ui=0 total=7
**Contract**: contract_ok=7 contract_partial=0 contract_missing=0

| Web route | Title | Guard | Mobile files | UI | Contract |
|---|---|---|---|---|---|
| `/coverage` | Coverage Operations | `role` | `apps/mobile/provider-app/src/screens/provider/ProfessionalChannelsHubScreen.tsx` | DONE | contract_ok |
| `/home/credentials` | Credentials & CPD | `auth` | `apps/mobile/provider-app/src/screens/provider/ProfessionalChannelsHubScreen.tsx` | DONE | contract_ok |
| `/omnichannel` | Omnichannel Hub | `role` | `apps/mobile/provider-app/src/screens/provider/ProfessionalChannelsHubScreen.tsx` | DONE | contract_ok |
| `/public-health/campaigns` | Campaigns | `role` | `apps/mobile/provider-app/src/screens/provider/ProfessionalChannelsHubScreen.tsx` | DONE | contract_ok |
| `/public-health/site-registry` | Site Registry | `role` | `apps/mobile/provider-app/src/screens/provider/ProfessionalChannelsHubScreen.tsx` | DONE | contract_ok |
| `/public-health/site-registry/[siteId]` | Site Profile | `role` | `apps/mobile/provider-app/src/screens/provider/ProfessionalChannelsHubScreen.tsx` | DONE | contract_ok |
| `/public-health/surveillance` | Surveillance | `role` | `apps/mobile/provider-app/src/screens/provider/ProfessionalChannelsHubScreen.tsx` | DONE | contract_ok |

## Notes

- Tier-3 wave 7 maps **omnichannel**, **coverage**, **credentials**, and **public-health** drill-down web routes to provider **Tools → CX+** plus a thin BFF hub endpoint.
