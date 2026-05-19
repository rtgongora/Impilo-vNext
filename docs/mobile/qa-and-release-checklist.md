# Mobile QA & release checklist

Use this checklist for every internal-distribution build of the Provider or
Citizen app. Promote to TestFlight / Play internal track only after every
box in **Phase 1** passes.

## Phase 1 — pre-build sanity (run locally / in CI)

- [ ] `pnpm install` clean from a fresh checkout succeeds.
- [ ] `pnpm --filter @impilo/mobile-* run build` (or `tsc -p .`) passes for
      every mobile package.
- [ ] `pnpm --filter ./apps/mobile/provider-app run typecheck` passes.
- [ ] `pnpm --filter ./apps/mobile/citizen-app run typecheck` passes.
- [ ] `pnpm --filter ./apps/mobile/provider-app run test` passes.
- [ ] `pnpm --filter ./apps/mobile/citizen-app run test` passes.
- [ ] `pnpm lint` passes for the mobile workspace.
- [ ] No `console.log("token")` / hardcoded secrets / `localhost` URLs in
      committed code (`rg "localhost|192\\.168\\.|10\\.0\\.2\\.2" -t ts` is
      empty outside `*.config.ts` defaults).
- [ ] `apps/mobile/*/app.config.ts` updated if a new permission, scheme or
      bundle suffix is needed.
- [ ] `apps/mobile/*/eas.json` profile env block updated if a new
      `EXPO_PUBLIC_*` was added.

## Phase 2 — EAS build (per profile)

- [ ] `npx eas build --platform android --profile preview` succeeds.
- [ ] `npx eas build --platform android --profile staging` succeeds.
- [ ] `npx eas build --platform ios --profile preview` succeeds (Ad Hoc).
- [ ] Build summary shows the expected bundle id suffix (`.dev`, `.preview`,
      `.staging`, none for production).
- [ ] Build summary shows the expected display name (`Impilo Provider
      Preview`, etc).
- [ ] Production profile **fails the safety guard** when the URLs are
      pointed at localhost (positive control).

## Phase 3 — smoke on a real device

For each variant (preview / staging) on Android, and TestFlight on iOS:

- [ ] App launches and the correct variant name appears under the icon.
- [ ] Login flow opens Keycloak, returns to the app, lands on the right
      home surface for the role.
- [ ] Pull-to-refresh on Home / Dashboard does not crash.
- [ ] Logout clears tokens — re-attempting a privileged request shows the
      login screen, not a stale 401 loop.
- [ ] Network status banner appears when airplane mode is toggled and clears
      when re-connected.
- [ ] Nompilo launcher opens; warning banner appears when LLM is offline.
- [ ] Provider App: `Apps → System status` lists at least one integration
      and degrades to "No integrations to show" when integration-hub is off.
- [ ] Citizen App: Home → *Track* opens NhumeTrackingScreen; closing returns
      to Home without state loss.
- [ ] Provider App in Courier mode: accept / decline buttons round-trip to
      the BFF.
- [ ] Map screen (where present): denying location permission shows the
      list-mode fallback, never a blank crash.

## Phase 4 — release readiness

Before promoting from internal to TestFlight beta / Play Store internal
track:

- [ ] Release notes drafted (see template in
      `docs/mobile/android-internal-install.md`).
- [ ] Bundle id, version, build number recorded in the release log.
- [ ] Privacy / permissions changelog reviewed if any new permission added.
- [ ] Store listing copy reviewed (Play Console / App Store Connect).
- [ ] Internal testers notified at least 24h before promotion.

## Phase 5 — post-release monitoring (first 24h)

- [ ] Crash-free session rate stays ≥ 99% in the device dashboard.
- [ ] No spike in `NHUME_UNAVAILABLE`, `INTEGRATION_HUB_UNAVAILABLE` or
      `KEYCLOAK_REDIRECT` errors in BFF logs.
- [ ] Sentry / equivalent surfaces no unexpected new exception classes.
- [ ] At least one Nompilo / Nhume / Integration status request from a real
      device is observed in correlation logs.
