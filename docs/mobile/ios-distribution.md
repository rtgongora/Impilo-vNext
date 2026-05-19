# iOS distribution & internal testing

Apple does **not** allow open APK-style sideloading in most regions,
including Zimbabwe. This document records the realistic iOS distribution
options for the Impilo Provider and Citizen apps and points at which one to
use in each phase of the rollout.

## Decision tree

| Phase | Use this | Why |
| --- | --- | --- |
| Engineers + design review | **EAS internal + iOS Simulator** (`simulator: true`) | Free, no provisioning, fastest iteration. |
| Wider internal alpha (≤ 100 devices) | **Ad Hoc distribution** via EAS | Each tester's UDID must be registered with Apple Developer; only those devices can install. |
| Pre-launch beta (broader testers) | **TestFlight** | Up to 10 000 external testers via email or public link; 90-day expiry. |
| Long-running employees-only build | **Apple Business Manager — private custom apps** | Distribute privately to MOHCC-managed Apple IDs without TestFlight expiry. |
| Closed institutional in-house | **Apple Developer Enterprise Program** | Only if MOHCC qualifies (>100 employees, intended for employees only). Not suitable for citizen-app distribution. |
| Public launch | **App Store** (or unlisted App Store) | Public discovery via App Store, or a private link for unlisted apps. |
| Region-specific alternative stores | **Alternative app distribution** | Only in EU markets where Apple permits it; **not** relevant to Zimbabwe at this time. |

## What we have configured

`apps/mobile/{provider,citizen}-app/eas.json` defines:

- `development.ios.simulator = true` — free simulator builds.
- `preview.ios.resourceClass = m-medium` — fast internal/Ad Hoc builds.
- `staging.ios.resourceClass = m-medium` — pre-prod.
- `production.ios.autoIncrement = true` — build number auto-increments per
  TestFlight / App Store submission.
- `submit.production.ios.{appleId, ascAppId, appleTeamId}` — wired to the
  MOHCC Apple Developer team (placeholder values — replace with real IDs
  before the first submission).

## Ad Hoc distribution (early testers)

1. Collect testers' device UDIDs (`Settings → General → About → scroll for
   the long UUID-like string`, or use Apple Configurator on macOS).
2. Register each UDID at <https://developer.apple.com/account/resources/devices/list>.
3. From the app folder run:

   ```bash
   npx eas build --platform ios --profile preview
   ```

4. Distribute the resulting `.ipa` via the EAS download link **only** to the
   registered device's owner. The build will install but only on the
   registered devices.

## TestFlight (beta)

1. Build with the production profile:

   ```bash
   npx eas build --platform ios --profile production
   ```

2. Submit to App Store Connect:

   ```bash
   npx eas submit --platform ios --profile production
   ```

3. In App Store Connect, set the build to **TestFlight Beta**, add testers
   by email or via a public link.
4. Testers install the **TestFlight** app from the App Store, accept the
   invitation, and install the beta. Builds expire 90 days after upload.

TestFlight is the recommended path for citizen-app pilot testing because
TestFlight invitations are tracked by Apple ID, not UDID.

## Apple Business Manager private apps

If MOHCC enrols in Apple Business Manager (ABM), private "custom apps" can
be assigned to managed Apple IDs and devices without going through TestFlight
expiry. This is the right answer for **long-running internal pilots** where
device replacement and tester rotation should not require re-inviting via
TestFlight every 90 days.

Setup is one-off:

1. Enrol MOHCC in <https://business.apple.com>.
2. In App Store Connect, set the app's distribution to **"Custom App for
   private distribution through Apple Business Manager"**.
3. Tag the build accordingly and assign in ABM.

This is **out of scope for the current rollout wave** but recorded here so
the team picks it up when pilot users hit the TestFlight 90-day wall.

## Apple Developer Enterprise Program

Only relevant if MOHCC qualifies (>100 employees, legitimate internal-only
use). It allows sideloading of an `.ipa` to any device without UDID
registration, but Apple prohibits using it for citizen-facing apps. **Not
suitable for the Citizen App.** Provider App could in principle use it for
MOHCC staff devices; we recommend ABM private apps instead because it does
not require yearly re-signing.

## What we deliberately do **not** do

- We do **not** publish a "download this .ipa and install it" page for
  ordinary citizens. There is no legal path to that in Zimbabwe today and
  pretending otherwise would mislead the team.
- We do **not** assume Google Play / Play Store flows for iOS.
- We do **not** distribute production-signed `.ipa` files directly via
  links; only TestFlight / Ad Hoc / ABM through Apple-mediated channels.

## Production safety guard

Same guard as Android: the production iOS build refuses to start unless
`EXPO_PUBLIC_API_BASE_URL` and `EXPO_PUBLIC_KEYCLOAK_URL` are HTTPS public
hosts. See `apps/mobile/{provider,citizen}-app/src/config.ts →
assertSafeProductionUrls`.

## Suggested rollout

1. Internal engineers → Simulator + Ad Hoc.
2. MOHCC pilot clinics (Provider) → TestFlight beta with named testers.
3. First citizen pilot cohort → TestFlight public link, capped at the cohort
   size, expiry 90 days, refresh per cycle.
4. Long-running pilots → migrate to ABM private apps.
5. National launch → App Store (or unlisted App Store with a private
   distribution link).
