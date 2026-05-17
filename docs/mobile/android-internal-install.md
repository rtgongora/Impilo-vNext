# Android internal installation guide

This guide covers **internal distribution before Play Store publication** for
the Impilo Provider and Citizen Android apps.

Audience: internal testers (clinical pilots, regional coordinators, MOHCC ICT
staff) and the build engineer cutting the binaries.

## 1. Build profiles

The two apps share four EAS profiles defined in `apps/mobile/{provider,citizen}-app/eas.json`:

| Profile | Distribution | Output | Use case |
| --- | --- | --- | --- |
| `development` | internal, dev client | `.apk` debug | Local development on emulator / dev device. |
| `preview` | internal | `.apk` | Single APK testers can sideload — earliest preview channel. |
| `staging` | internal | `.apk` | Pre-production parity with staging APIs. |
| `production` | store | `.aab` | Play Store / unlisted internal track. |

Per-profile bundle suffixes (`.dev`, `.preview`, `.staging`) and a per-profile
display name (`Impilo Provider Dev`, `Impilo Provider Preview`, …) mean
testers can install all four side-by-side on the same phone without
collision.

## 2. Building APKs with EAS

Run these from the repo root or from `apps/mobile/{provider,citizen}-app`:

```bash
# Provider preview APK
cd apps/mobile/provider-app
npx eas build --platform android --profile preview

# Provider staging APK
npx eas build --platform android --profile staging

# Citizen preview APK
cd ../citizen-app
npx eas build --platform android --profile preview
```

When EAS finishes, copy the **public download URL** from the build summary
and either:

- paste it into the internal testers' channel, or
- generate a QR code from that URL and post the QR.

## 3. Tester installation

Send testers this short script:

> 1. Open the link / scan the QR on your Android phone.
> 2. The browser will download an `.apk` file.
> 3. The first time you install an APK from a browser, Android asks for
>    permission: **Settings → Apps → Special access → Install unknown
>    apps → Chrome (or your browser) → Allow**.
> 4. Open the downloaded `.apk` and tap *Install*.
> 5. The app will appear as **Impilo Provider Preview** (or
>    **Impilo Provider Staging** / Dev), so it cannot be confused with the
>    production app.

Permissions Impilo apps request on first launch and why:

| Permission | Why we need it |
| --- | --- |
| Internet & network state | Required for all API calls and offline detection. |
| Camera | Scanning patient QR codes; capturing delivery proof photos. |
| Location (fine + coarse) | Ndila facility lookup; Nhume courier tracking. **Foreground only** — no background location is collected. |
| Biometrics / fingerprint | Optional re-auth shortcut. |
| Microphone (citizen only) | Telehealth audio. |
| Notifications | Delivery / appointment / messaging alerts. |

## 4. Production safety guard

The `production` profile is hard-locked to:

- `EXPO_PUBLIC_API_BASE_URL = https://api.impilo.gov.zw`
- `EXPO_PUBLIC_KEYCLOAK_URL = https://auth.impilo.gov.zw`

If anyone overrides these to a non-HTTPS URL or a private LAN host, the app
**refuses to start** with a clear `Refusing to start: production build has
unsafe configuration` error. See
`apps/mobile/{provider,citizen}-app/src/config.ts → assertSafeProductionUrls`.

This is the binary-level equivalent of the env safety check the web app
already enforces in CI.

## 5. Release notes template

Use the same template for every preview build (paste this into the
tester channel along with the QR / download URL):

```
Build:     Impilo Provider Preview · 0.1.0 · build #<EAS build number>
Channel:   preview
APIs:      api-preview.impilo.gov.zw
Auth:      auth-preview.impilo.gov.zw

What's new
- (one line)
- (one line)

Known issues
- (one line)

How to install
1. Open this link on your phone: <download URL>
2. Allow installation from your browser (one-time).
3. Tap the downloaded APK to install.
```

## 6. Versioning

- `version` in `app.config.ts` is the marketing version.
- Android `versionCode` is auto-incremented by EAS for `production` builds
  (`autoIncrement: true`).
- Preview and staging builds reuse `versionCode = 1` because they are not
  uploaded to Play; the EAS build number disambiguates them.

## 7. Promotion to Play Store

The `production` profile builds an `.aab` and is wired in `submit.production`
to push to Play Console **internal track** (`track: "internal"`). Promote
through Play Console UI from internal → closed → open → production after
each successful test cycle.

The Play Store does **not** see the preview / staging APKs — they are
side-loaded only.
