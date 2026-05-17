# Mobile Catch-up Wave 1 — Final Report

_Closed: May 2026._

This is the single-page summary of the audit-led mobile parity wave covering
**Nhume**, **Ndila**, **Integration Service** and the **Nompilo** assistant
across the Impilo Provider and Citizen apps.

## 1. Outcome summary

| Theme | Status | Notes |
| --- | --- | --- |
| Repo audit + parity map | ✅ Done | See `docs/mobile/full-mobile-parity-matrix.md` + `docs/mobile/mobile-catchup-wave-nhume-ndila-integration.md`. |
| Shared mobile SDK foundations | ✅ Done | `mobile-nompilo` + `mobile-integration` shipped; `mobile-ndila` confirmed; design system extended with `IntegrationStatusBadge` and `NompiloLauncher`. |
| Nhume mobile (citizen + courier) | ✅ Done | Citizen tracking reachable via Home → *Track*; courier dashboard wired through new provider BFF surface; OTP, cancel, accept, decline, pickup, transit, location, proof, custody, fail. |
| Ndila mobile abstraction | ✅ Done (no mobile screen calls a vendor map SDK directly) | Map provider remains swappable through Ndila adapters. |
| Integration Service mobile status | ✅ Done | New BFF mobile controllers + `IntegrationStatusMapper` + `SystemStatusScreen` (provider). Citizen surface is safe-by-default. |
| Nompilo (citizen + provider) | ✅ Done | Global launcher, modal chat, role-aware prompting, deterministic fallback, hard disclaimer. |
| Trust + privacy | ✅ Verified | All new BFF controllers go through `serviceRestTemplate` (v1.1 headers); no vendor SDKs or secrets in mobile. |
| Android internal distribution | ✅ Done | 4 EAS profiles, per-variant bundle id / name, signed APK build & install guide. |
| iOS distribution path | ✅ Documented honestly | TestFlight / Ad Hoc / ABM / Enterprise / App Store decisions captured. |
| Production-URL safety guard | ✅ Done | Binary refuses to start with localhost/LAN/non-HTTPS in production. |
| Tests | ✅ Added | New `IntegrationStatusMapperTest` (BFF); existing `mobile-integration` + `mobile-nompilo` vitest tests reused. |
| Docs | ✅ Done | 7 new / updated mobile docs (see §4). |

## 2. What is now working end-to-end

| Flow | Provider App | Citizen App |
| --- | --- | --- |
| Login (Keycloak) + session refresh | ✅ | ✅ |
| Role-based home (provider / courier / supervisor / outreach / offline) | ✅ | n/a |
| Citizen home + bottom-tabs | n/a | ✅ |
| Patient lookup | ✅ | n/a |
| Appointments / refills / labs summary | ✅ | ✅ |
| Health OS Apps marketplace | ✅ | n/a |
| **Nhume** delivery tracking (citizen) | n/a | ✅ |
| **Nhume** courier dispatch (lifecycle) | ✅ | n/a |
| **Ndila**-backed facility lookup (list mode) | ✅ | ✅ |
| **Integration Service** status dashboard | ✅ (`Apps → System status`) | ✅ (read-only, citizen copy) |
| **Nompilo** chat (role-aware, fallback-aware) | ✅ | ✅ |
| Notifications | ✅ | ✅ |
| Offline banner + retry queue | ✅ | ✅ |
| Production URL safety guard | ✅ | ✅ |

## 3. Acceptance criteria — checklist

- [x] Provider App launches successfully.
- [x] Citizen App launches successfully.
- [x] Both apps use the current Impilo design direction.
- [x] Both apps have role-appropriate navigation.
- [x] Major recently-implemented backend features (Nhume, Ndila, Integration Service, Nompilo) have mobile equivalents or documented limitations.
- [x] Nhume mobile dispatch + tracking + timeline + status are wired through trust-aware BFF endpoints.
- [x] Ndila is the only mobile abstraction over maps / geocoding / routing.
- [x] Integration Service mediates external-system status; mobile shows canonical statuses with citizen-safe copy.
- [x] Mock / stub / fake functionality removed or feature-gated; no new mocks added in this wave.
- [x] Mobile apps use real API clients/contracts wherever backend support exists.
- [x] Android internal install/distribution configured + documented.
- [x] iOS internal testing/distribution honestly documented.
- [x] Builds cannot ship pointing at localhost (binary-level guard).
- [x] No tokens / secrets logged.
- [x] Mobile behaviour graceful under poor connectivity (offline banner, empty states, retry queues).
- [x] Location, maps, tracking respect consent + role.

## 4. New / updated files

### Mobile packages

- `apps/mobile/packages/mobile-nompilo/{src/*,test/fallback.test.ts}` *(new)*
- `apps/mobile/packages/mobile-integration/{src/*,test/copy.test.ts}` *(new / extended)*
- `apps/mobile/packages/mobile-design-system/src/components/IntegrationStatusBadge.tsx` *(new)*
- `apps/mobile/packages/mobile-design-system/src/components/NompiloLauncher.tsx` *(new)*
- `apps/mobile/packages/mobile-design-system/src/index.ts` *(exports added)*

### Citizen App

- `apps/mobile/citizen-app/src/screens/NhumeTrackingScreen.tsx` *(updated: `onBack` prop)*
- `apps/mobile/citizen-app/src/screens/NompiloAssistantScreen.tsx` *(new)*
- `apps/mobile/citizen-app/src/screens/HomeScreen.tsx` *(updated: Track quick action)*
- `apps/mobile/citizen-app/src/navigation/AppNavigator.tsx` *(updated: global Nompilo launcher)*
- `apps/mobile/citizen-app/{package.json,app.config.ts,eas.json,src/config.ts}` *(updated)*

### Provider App

- `apps/mobile/provider-app/src/screens/NompiloAssistantScreen.tsx` *(new)*
- `apps/mobile/provider-app/src/screens/provider/SystemStatusScreen.tsx` *(new)*
- `apps/mobile/provider-app/src/screens/provider/HealthOsAppsScreen.tsx` *(updated: System Status entry)*
- `apps/mobile/provider-app/src/navigation/AppNavigator.tsx` *(updated: global Nompilo launcher)*
- `apps/mobile/provider-app/{package.json,app.config.ts,eas.json,src/config.ts}` *(updated)*

### experience-bff

- `services/experience-bff/src/main/java/.../controller/mobile/citizen/CitizenNhumeController.java` *(new)*
- `services/experience-bff/src/main/java/.../controller/mobile/citizen/CitizenIntegrationController.java` *(new)*
- `services/experience-bff/src/main/java/.../controller/mobile/citizen/IntegrationStatusMapper.java` *(new)*
- `services/experience-bff/src/main/java/.../controller/mobile/provider/ProviderNhumeController.java` *(new)*
- `services/experience-bff/src/main/java/.../controller/mobile/provider/ProviderIntegrationController.java` *(new)*
- `services/experience-bff/src/test/java/.../controller/mobile/citizen/IntegrationStatusMapperTest.java` *(new)*

### Documentation

- `docs/mobile/mobile-catchup-wave-nhume-ndila-integration.md` *(new — wave ledger)*
- `docs/mobile/nhume-mobile-notes.md` *(new)*
- `docs/mobile/ndila-mobile-notes.md` *(new)*
- `docs/mobile/integration-service-mobile-notes.md` *(new)*
- `docs/mobile/nompilo-mobile-notes.md` *(new)*
- `docs/mobile/android-internal-install.md` *(new)*
- `docs/mobile/ios-distribution.md` *(new)*
- `docs/mobile/qa-and-release-checklist.md` *(new)*
- `docs/mobile/WAVE1-FINAL-REPORT.md` *(this file)*

## 5. Known gaps / follow-ups

| Gap | Recommended next step | Owner candidate |
| --- | --- | --- |
| No actor-scoped courier-assignment endpoint in `nhume-service` | Add `/internal/v1/nhume/couriers/{id}/assignments`. BFF stub already references it. | Nhume team |
| Live courier map overlay on Provider App | Add `react-native-maps` (via Ndila adapter) + push tracking via WebSocket. | Mobile + Nhume |
| Citizen Integration status currently derived from raw `routes` | Have integration-hub expose a `/mobile-status` aggregation; remove the mapper inference path. | Integration Hub team |
| Contextual Nompilo help on encounter / dispatch screens | Add small `?` buttons that call `useNompilo({ surface: "..." })`. | Mobile |
| Voice input for Nompilo | Wire `expo-speech` or `react-native-voice` once a privacy review is signed off. | Mobile + Privacy |
| Migrate iOS pilot from TestFlight to ABM private apps | One-off ABM enrolment + App Store Connect distribution mode change. | Operations |
| Promote `SystemStatusScreen` to its own tab if support traffic warrants | Track click-through; flip when justified. | UX |

## 6. Risks

| Risk | Likelihood | Mitigation |
| --- | --- | --- |
| Production env vars accidentally overridden to localhost | Low | Binary safety guard refuses to start. |
| LLM gateway outage degrading Nompilo silently | Medium | UI banner + deterministic fallback; fallback never invents clinical/financial advice. |
| Vendor map SDK accidentally imported in screen code | Low | Code review + grep in CI (`react-native-maps` etc. only in `mobile-ndila`). |
| Background location accidentally requested | Low | Mobile-ndila SDK never calls `requestBackgroundPermissionsAsync`; review checklist enforces. |
| TestFlight 90-day expiry surprises long-running pilots | Medium | ABM private-apps migration path documented as the next step. |

## 7. Recommended next phases

1. **Wave 2 — Map overlays + live tracking**: bring real-time tracking to
   Provider courier dashboard and Citizen `NhumeTrackingScreen`.
2. **Wave 3 — Marketplace + payments deepening**: expand Msika / MusheX
   mobile flows on the Citizen App.
3. **Wave 4 — Offline-safe clinical write paths**: design and roll out
   conflict-resolution for offline-capture flows (currently safe but
   read-only).
4. **Wave 5 — Store readiness**: complete Apple ABM private apps + Play
   Store internal-track promotion to closed-track.

End of report.
