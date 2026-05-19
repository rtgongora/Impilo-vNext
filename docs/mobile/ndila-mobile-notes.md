# Ndila mobile implementation notes

Ndila is the shared maps, geocoding and geospatial-intelligence service.
Mobile apps consume it as the **only** abstraction layer over map and
location providers. No mobile screen imports a vendor map SDK directly.

## SDK surface

`apps/mobile/packages/mobile-ndila` exposes:

- `ndilaMobile` — read-only client wrapping `apiClient`. Supported methods:
  - `mapConfig()` — current map provider + style + attribution.
  - `geocode(text)` / `reverseGeocode(lat, lng)`.
  - `validateLocation(lat, lng, scope)` — catchment / coverage check.
  - `route(from, to, profile)` — routing & distance.
  - `findNearby({ kind, lat, lng, radius_m })`.
  - `postTrackingEvent(...)` — courier ping (provider only).
- `useNearby`, `useRoute`, `useUserLocation`, `useMapConfig` — React hooks.
- `captureCurrentPosition({ accuracy })` — wraps `expo-location` with a
  consistent permission-denied story.

## Permissions and consent

| Surface | Permission |
| --- | --- |
| Citizen "nearby facilities" | `WhenInUse` only. Citizen may decline; the screen still works using a manual location picker. |
| Provider facility lookup | `WhenInUse` only. Falls back to the configured tenant facility centroid. |
| Courier dispatch tracking | `WhenInUse` only, **foreground tracking**. Background location is **not** enabled in either app. |

Privacy guards baked into the SDK:

- `captureCurrentPosition` always requests on-demand; we never call
  `requestBackgroundPermissionsAsync`.
- The user can opt-out at any time; map screens then fall back to the
  list view and ask for a manual location pick.
- The SDK throws a typed `NdilaPermissionDeniedError` instead of silently
  using stale coordinates.

## Map widget

Today the `Map` component is provider-neutral (renders the tile URL the BFF
gives us through `mapConfig()`). The configured provider can be Google Maps,
Mapbox, OpenStreetMap, HERE, ESRI or a future sovereign map provider — the
mobile code does not know or care. Switching providers requires no mobile
change.

The wave 1 build ships with:

- Citizen facility / service finder (list mode + map toggle).
- Provider facility lookup (list mode only — map overlay deferred).
- Nhume courier dashboard (list mode only — map overlay deferred).

## Offline / low-bandwidth fallback

- Every Ndila-backed screen has a "list mode" that does not require a map
  tile to render.
- The SDK caches the last successful `mapConfig()` in offline storage so the
  app does not stall waiting for the provider when reopened offline.
- When the user is offline and asks "find nearby pharmacies", we serve the
  last-known facility list with an "Offline — last synced at …" banner.

## What's deliberately **not** there

- No direct `react-native-maps`, `expo-maps`, `@react-native-mapbox-gl/maps`
  imports in screen code.
- No vendor API keys checked into the repo — keys live with the BFF.
- No background location tracking.
- No "always-on" tracking for citizens.
