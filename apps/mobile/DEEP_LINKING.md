# Impilo mobile deep-linking

How the Impilo apps (`citizen-app`, `provider-app`) handle web → app handoff:
custom-scheme links, iOS Universal Links, and Android App Links.

## Identifiers

| App | Product name | Bundle / package | Custom scheme |
| --- | ------------ | ---------------- | ------------- |
| citizen-app | **Impilo** (`(Dev)`/`(Preview)`/`(Staging)` for non-prod) | `zw.gov.impilo.citizen` | `impilo-citizen://` |
| provider-app | **Impilo Provider** | `zw.gov.impilo.provider` | `impilo-provider://` |

Public host: `https://impilo.mohcc.gov.zw` (Traefik → public website at root).

## Two link classes

1. **Custom scheme** — `impilo-citizen://welcome/find-care`. Works today, no
   provisioning needed. Used for the OAuth callback (`…://auth/callback`) and as
   a deep-link fallback.
2. **Universal Links (iOS) / App Links (Android)** — a normal
   `https://impilo.mohcc.gov.zw/...` URL opens the app directly when installed.
   Requires OS-level verification against the website's `.well-known` files
   (see "Placeholders" below). Until verified, https links open in the browser.

## App configuration (already wired)

`app.config.ts` (authoritative) and `app.json` (kept in parity) in each app:

- iOS: `ios.associatedDomains: ["applinks:impilo.mohcc.gov.zw"]`
- Android: `android.intentFilters` with `autoVerify: true`, action `VIEW`,
  scheme `https`, host `impilo.mohcc.gov.zw`, and the per-app path prefixes.

The custom `scheme` in each config is unchanged, so custom-scheme links keep
working alongside the new https intent filters.

### Runtime routing

Neither app uses a React Navigation `NavigationContainer`; both navigate through
a Zustand `appStore` (active tab / mode + provider tab). The `linking`-config
equivalent lives in `src/navigation/deepLinks.ts` in each app:

- `DEEP_LINK_PREFIXES` — custom scheme + `https://impilo.mohcc.gov.zw/`.
- a pure `resolve*DeepLink(url)` mapping URL → in-app destination.
- `useDeepLinkRouting()` — `getInitialURL()` (cold start) + a `url` listener
  (warm foreground), mounted once in `AppNavigator`. The OAuth callback is left
  to `LoginScreen`.

## Context route map (web → app)

Only opaque context ids travel in links (facility id, feedback case reference);
no PII or clinical data. Both apps' Android intent filters and iOS AASA path
components are scoped to exactly these prefixes.

### Citizen app (`zw.gov.impilo.citizen`)

| Web URL (impilo.mohcc.gov.zw) | Opens in app |
| ----------------------------- | ------------ |
| `/welcome/find-care` (opt. `?facility=<id>`) | Health → Discover providers (selects facility if provided) |
| `/appointments` | Health → Appointments |
| `/bills` | Health → Finance (bills & payments) |
| `/welcome/report` (opt. `?ref=<case>`) | Health → Feedback; with `ref`, Track feedback seeded to that case |
| `/get-involved` | Public health (participation) |
| `/explore/regulatory` (or `/public/regulatory`) | Personal → Councils (public regulatory explore) |

### Provider app (`zw.gov.impilo.provider`)

| Web URL (impilo.mohcc.gov.zw) | Opens in app |
| ----------------------------- | ------------ |
| `/provider` | Provider mode → Dashboard |
| `/provider/<tab>` | Provider mode → matching tab (queue, patients, results, tools, apps, professional, diagnostics…) |
| `/work` | Provider mode → Worklist (queue) |
| `/professional/regulatory` | Provider mode → Professional → My Regulatory Affairs |
| `/professional/regulatory/contribute/<inviteId>` | Provider mode → Professional → My Regulatory (contributor invite prefilled) |

## `.well-known` files (hosted by the website)

In the website repo `impilo-website-recovered` under `public/.well-known/`
(Vite copies `public/` → `dist/`; the Dockerfile ships `dist/`; nginx serves
each as `application/json` with no redirect and no SPA fallback):

- `apple-app-site-association` — no extension, valid JSON. iOS Universal Links.
- `assetlinks.json` — JSON array. Android App Links.

Served at:
- `https://impilo.mohcc.gov.zw/.well-known/apple-app-site-association`
- `https://impilo.mohcc.gov.zw/.well-known/assetlinks.json`

## Placeholders blocking verified app-links

Verified https deep-linking (open the app with **no** browser/chooser) is
blocked on two provisioning values that cannot be fabricated. Custom-scheme
links and the OAuth flow are unaffected.

| Placeholder | Where | Fill with |
| ----------- | ----- | --------- |
| `TEAMID_PENDING` | `public/.well-known/apple-app-site-association` (website repo) | Apple Developer **Team ID** once the MoHCC Apple account is provisioned. IDs become `<TEAMID>.zw.gov.impilo.citizen` / `.provider`. |
| `PLACEHOLDER_PENDING_SIGNING_CERT` | `public/.well-known/assetlinks.json` (website repo) | SHA-256 fingerprint of the **Android release signing cert** (from EAS credentials / Play App Signing). |

After filling either, rebuild + redeploy the website, then re-trigger OS
verification (reinstall iOS app; `adb shell pm verify-app-links --re-verify
<package>` on Android). See `public/.well-known/README.md` in the website repo.

The EAS `appleTeamId` in `citizen-app/eas.json` / `provider-app/eas.json`
(currently `IMPILO_TEAM`) must be set to the same real Team ID at submit time.
