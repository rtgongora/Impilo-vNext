# Mobile Catch-up Wave — Nhume, Ndila & Integration Service

_Last updated: May 2026 (Mobile Catch-up Wave 1)._

This document records the **mobile catch-up and parity implementation wave**
that brought the Provider and Citizen apps up to date with the Nhume, Ndila
and Integration Service surfaces, plus distribution and safety guards.

It is a **complement** (not a replacement) to:

- `docs/mobile/full-mobile-parity-matrix.md`
- `docs/mobile/tier3-wave7-parity-matrix.md`
- `docs/mobile/citizen-parity-gap-analysis.md`
- `docs/mobile/provider-parity-gap-analysis.md`

If anything below contradicts those documents, the matrix files are the
historical record; this file is the active "what changed in this wave"
ledger.

## 1. What changed in this wave

### Shared mobile foundations

| Package | Change |
| --- | --- |
| `@impilo/mobile-nompilo` | New SDK — role-aware client + React hook for the Nompilo assistant. Talks to the BFF `/internal/v1/llm/chat`, gracefully falls back when the model is offline. |
| `@impilo/mobile-integration` | New SDK — canonical mobile-facing integration status enum, friendly copy helpers, read-only client, and `useIntegrationStatuses` hook. |
| `@impilo/mobile-ndila` | Already existed — confirmed as the only mobile-side abstraction over map providers. No mobile screen calls a vendor map SDK directly. |
| `@impilo/mobile-design-system` | New `IntegrationStatusBadge` and `NompiloLauncher`; both apps adopt them. |

### Mobile-specific BFF surface (experience-bff)

| Controller | Path | Purpose |
| --- | --- | --- |
| `CitizenNhumeController` | `/internal/v1/mobile/citizen/nhume/**` | Citizen delivery list, timeline, tracking, OTP confirm, cancel. |
| `ProviderNhumeController` | `/internal/v1/mobile/provider/nhume/**` | Courier accept/decline/pickup/start/location/proof/custody/fail. |
| `CitizenIntegrationController` | `/internal/v1/mobile/citizen/integration/statuses` | Citizen-friendly integration status list (no vendor names, no correlation IDs). |
| `ProviderIntegrationController` | `/internal/v1/mobile/provider/integration/statuses` | Provider variant — includes adapter name, correlation id, last-sync time. |
| `IntegrationStatusMapper` | (helper) | Maps raw integration-hub route/adapter health into the canonical mobile enum. |

All four controllers use the existing `serviceRestTemplate` bean (which
already forwards v1.1 trust headers downstream) and **degrade gracefully**:
GETs return an empty list rather than a 5xx if upstream is down, POSTs
return a 502 envelope with `NHUME_UNAVAILABLE` / safe message.

### Citizen App

- `NhumeTrackingScreen` is now reachable from a new "Track" quick action on
  `HomeScreen`. It accepts an `onBack` prop and degrades gracefully.
- Global `NompiloLauncher` + `NompiloAssistantScreen` inside a `BottomSheet`
  on `AppNavigator`. Citizen tone, role auto-derived from session.

### Provider App

- Global `NompiloLauncher` + `NompiloAssistantScreen` inside a `BottomSheet`
  on `AppNavigator`. Provider tone, role derived from `mode`
  (`PROVIDER` / `COURIER` / `SUPERVISOR` / `OUTREACH`).
- New `SystemStatusScreen` shows integration health grouped by domain (PACS,
  LIMS, eLMIS, telemedicine, MusheX, Comms Hub, Nhume, Ndila, registries,
  CRVS, insurance). Accessible from `HealthOsAppsScreen` → "System status".
- `CourierDashboardScreen` already existed; it now talks to the real
  `/internal/v1/mobile/provider/nhume` surface (no more `?` paths).

## 2. Build profile and environment hygiene

Both apps now expose 4 EAS build profiles with per-profile env injection:

| Profile | Distribution | API base | Keycloak |
| --- | --- | --- | --- |
| `development` | internal (with dev client) | `http://10.0.2.2:*` | local |
| `preview` | internal (APK / iOS internal) | `https://api-preview.impilo.gov.zw` | preview realm |
| `staging` | internal | `https://api-staging.impilo.gov.zw` | staging realm |
| `production` | store-track | `https://api.impilo.gov.zw` | production realm |

Per-variant app names and bundle identifiers are auto-derived in
`app.config.ts` (`Impilo Provider Dev`, `Impilo Provider Preview`, `Impilo
Provider Staging`, `Impilo Provider`). Bundle suffixes (`.dev` / `.preview` /
`.staging`) mean all four can be installed side-by-side on a single device.

A **production safety guard** in `src/config.ts` throws at startup if a
`production` build resolves to:

- `http://` (anything not `https://`)
- `localhost` / `127.0.0.1` / `10.0.2.2`
- a private LAN host such as `192.168.*` / `10.*`

This is the same kind of check we already have in CI for web env, but
enforced at the binary level for mobile.

## 3. Trust, security and privacy

- Mobile clients never talk to a vendor directly. Maps go through Ndila,
  payments through MusheX, lab/imaging through Integration Service.
- The shared `apiClient` already adds the v1.1 trust headers (purpose of use,
  actor id/type, tenant, facility, workspace, shift, correlation id, device
  fingerprint where available). New SDKs use that client.
- `mobile-nompilo` enforces a hard disclaimer (`NOMPILO_DISCLAIMER`) and a
  deterministic fallback that **never** invents clinical or financial
  advice when the LLM gateway is offline.
- `IntegrationStatusMapper` strips vendor strings and correlation IDs for
  the citizen surface and only includes them for the provider surface.

## 4. Known gaps + follow-ups

| Gap | Impact | Recommended next step |
| --- | --- | --- |
| No per-courier "assigned to me" endpoint in nhume-service | `ProviderNhumeController#assigned` currently falls back to `status=ASSIGNED` listing. | Add `/internal/v1/nhume/couriers/{id}/assignments` and filter by actor id. |
| Live courier tracking is not yet end-to-end real | Provider courier map view shows "last updated at…" not real-time. | Push WebSocket from nhume → BFF → mobile when fleet adapters are live. |
| Citizen integration status today comes from raw integration-hub `routes` | Status mapping happens in the BFF (`IntegrationStatusMapper`). | When integration-hub exposes a dedicated `/mobile-status` aggregation, switch the BFF to call that. |
| Provider App `SystemStatusScreen` is reached from Apps tab | Discoverable but not its own tab. | If feedback says it is high-traffic, promote to a tab in `ProviderTabs`. |
| Biometric unlock | Already wired in auth flow but not exercised in this wave. | Smoke-test in preview and document in `docs/mobile/security-notes.md`. |

## 5. QA / smoke checklist (per build)

After each EAS preview build, verify on a real device:

1. App launches and shows the variant-correct name (e.g. *Impilo Provider Preview*).
2. Sign in completes and lands on the correct landing surface.
3. Pull-to-refresh on Home / Dashboard does not crash.
4. **Nompilo**: open from launcher; confirm the warning banner appears when
   the LLM gateway is offline (kill `llm-orchestration-service` to simulate).
5. **Integration status** (provider): open `Apps → System status`. Kill
   `integration-hub-service`. The screen must show "No integrations to show"
   not a red error banner.
6. **Nhume citizen**: tap *Track* on Home; the list loads or shows empty. Open
   one delivery and verify timeline renders.
7. **Nhume courier**: in Provider mode switch to Courier; verify assignments
   list loads and `Accept` / `Decline` round-trip to the BFF.
8. **Production guard**: build the `development` profile with
   `EXPO_PUBLIC_APP_VARIANT=production EXPO_PUBLIC_API_BASE_URL=http://localhost`
   set; the app must refuse to start with the expected error.
9. Logout clears tokens (verify by retrying a privileged request).
10. Offline banner appears when airplane mode is toggled.

See `docs/mobile/android-internal-install.md` and
`docs/mobile/ios-distribution.md` for distribution details.
