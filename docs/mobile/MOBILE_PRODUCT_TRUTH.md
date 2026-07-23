# Mobile Product Truth — Impilo & Impilo Provider

**Date:** 2026-07-23
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
**Anchor commit:** `209b6ad45736e0bf27005ed310bbb42bb7873cf4`
**Status of this document:** Ground truth established by direct inspection (not from prior sprint claims). Supersedes any earlier "mobile parity" statement as evidence of buildability or runtime health. Companion runtime evidence: `docs/mobile/MOBILE_RECOVERY_REPORT.md`.

## 1. Executive truth

There are **two genuine, separately-packaged mobile applications**, both substantially implemented (far past shell stage), living in a self-contained pnpm monorepo at `apps/mobile/`. Until this recovery, **no APK or AAB had ever been produced anywhere in the repo**, `pnpm mobile:build` was only a typecheck alias, and the designated Android sandbox VM (218) was never bootstrapped and is unreachable. Mobile *source* development has been continuously active (174 commits touching `apps/mobile`, latest 2026-07-23 TM-B18), but runtime proof was absent.

## 2. The two applications

| | **Impilo** (citizen) | **Impilo Provider** (health worker) |
|---|---|---|
| Path | `apps/mobile/citizen-app` | `apps/mobile/provider-app` |
| npm name | `@impilo/citizen-app` | `@impilo/provider-app` |
| Expo slug / scheme | `impilo-citizen` / `impilo-citizen://` | `impilo-provider` / `impilo-provider://` |
| Android applicationId (committed `android/`) | `zw.gov.impilo.citizen.dev` | `zw.gov.impilo.provider.dev` |
| applicationId by variant (`app.config.ts`) | `.dev` / `.preview` / `.staging` / none (prod) suffix on `zw.gov.impilo.citizen` | same pattern on `zw.gov.impilo.provider` |
| versionName / versionCode | `0.1.0` / `1` | `0.1.0` / `1` |
| Screens (`src/screens/**/*.tsx`) | ~99 across 18 domains (largest: `personal` 43) | ~114 across 11 domains (largest: `provider` 76) |
| Navigation | 8-tab `CitizenTabs` (Home, Health, Feed, Services, Messages, Khuluma, Care, Public) | `ModeRouter` with 5 modes: Provider (~11 tabs), Outreach, Supervisor, Offline, Courier |
| Service modules (`src/services/`) | ~80 | ~79 |

They are **separate apps**, not flavours of one app: distinct package IDs, src trees, navigation, Keycloak clients, and deep-link schemes. Both consume the same 12 shared workspace packages.

## 3. Framework & toolchain

- **Expo SDK** `~54.0.34`, **React Native** `0.81.5`, **React** `19.1.0`, react-native-web `^0.21.0`
- **Navigation** `@react-navigation/*` v7; **state** zustand; **data** TanStack Query v5
- **TypeScript** `~5.9.2`; **tests** vitest `^2.1.0` (not jest); **Node** >= 20
- **Package manager:** pnpm 9.15.0 (corepack), single lockfile `apps/mobile/pnpm-lock.yaml`, workspace defined in `apps/mobile/package.json` + `pnpm-workspace.yaml`. There is **no repo-root package.json** — the mobile workspace is self-contained.
- **Android (committed native projects in both apps):** Gradle wrapper **8.14.3**, compileSdk/targetSdk **35**, minSdk **24**, buildTools **35.0.0**, NDK **27.1.12297006** (Expo default), **newArchEnabled + Hermes**, Kotlin/AGP versions resolved by Expo's `expo-root-project` plugin (not pinned in-file). JDK **17+** required (21 works; host uses Temurin 21).
- **Signing:** debug keystore committed (`android/app/debug.keystore`, standard `android`/`androiddebugkey` credentials — a public well-known debug key, not a secret). **Release buildType also signs with the debug keystore** — so `:app:assembleRelease` produces a directly-installable APK with bundled JS. No production keystore exists in the repo (correct; release signing route is via EAS or an operator-held keystore, see `docs/mobile/android-internal-install.md`).
- **`:app:assembleDebug` does NOT bundle JS** (RN default `debuggableVariants`) and there is **no expo-dev-client dependency** — debug APKs require a Metro server. Standalone builds must use `assembleRelease`.

## 4. iOS truth

**No iOS native projects exist** (no `ios/` dir, no Podfile). iOS is config-only in `app.config.ts` (bundle IDs, deploymentTarget 15.1, associatedDomains, usage strings) with placeholder Apple Team ID `IMPILO_TEAM` in `eas.json`. iOS builds would require `expo prebuild` / EAS with real Apple credentials. **Recorded as a platform gap.**

## 5. API base URL configuration

Resolution chain (per app): `Constants.expoConfig.extra.*` (set in `app.config.ts` from `EXPO_PUBLIC_*` env at **bundle time**) → `EXPO_PUBLIC_*` fallbacks → hardcoded LAN fallback (`http://192.168.100.211:8160`). All app traffic routes through the **experience-bff** (port 8160 in dev; `/internal/v1/mobile/*` + citizen/provider BFF surfaces).

| Variant | API base | Keycloak |
|---|---|---|
| dev (fallback) | `http://192.168.100.211:8160` | `http://192.168.100.211:8480` |
| development (eas.json) | `http://10.0.2.2:8160` | `http://10.0.2.2:8080` |
| **preview** | `https://impilo.mohcc.gov.zw` | `https://impilo.mohcc.gov.zw:8480` ← **dead config, see below** |
| staging / production | `https://api[-staging].impilo.gov.zw` | `https://auth[-staging].impilo.gov.zw` |

Because `EXPO_PUBLIC_*` is inlined by Metro at bundle time, the effective endpoints of any built APK are **frozen into the binary**; exporting env at build time is the control point.

### Preview reality check (verified live 2026-07-23)
- `https://impilo.mohcc.gov.zw` (Traefik-terminated TLS on this estate) → **200**, real preview UI/BFF. Reachable.
- **Keycloak is NOT externally exposed.** The k8s service `impilo-full-preview/keycloak` is ClusterIP-only (8080); nothing listens on `:8480` on the LB. The eas.json/app.config preview Keycloak URL `https://impilo.mohcc.gov.zw:8480` **cannot work from any device**. Web login works only because the shell authenticates server-side in-cluster (`http://keycloak:8080`).
- Preview realm import (`deploy/helm/impilo-vnext/files/realm-impilo-preview.json`) **does** define the mobile public clients `impilo-mobile-citizen` and `impilo-mobile-provider` with redirect URIs `impilo-citizen://auth/callback` / `impilo-provider://auth/callback` — matching the apps' fixed schemes. Client config is correct; **network exposure is the missing piece** (see recovery report for the route taken).

## 6. Authentication & TSHEPO integration

- **Keycloak PKCE** (auth-code + S256, public client, no secret): `packages/mobile-auth/src/keycloakClient.ts` builds endpoints as `${baseUrl}/realms/${realm}/protocol/openid-connect` (no OIDC discovery dependency). Token lifecycle in `tokenManager.ts` / `authStore.ts`; biometric unlock via expo-local-authentication + expo-secure-store.
- **Trust headers:** `packages/mobile-trust/src/headers.ts` mirrors `services/tshepo-service/.../TrustHeaders.java` and `ui/shared-ui/lib/contracts.ts` — full TRUST_HEADERS set plus HARD_REQUIRED_HEADERS (tenant, pod, request-id, correlation-id). Injection, ApiEnvelope parsing, idempotency and retry live in `packages/mobile-api-client/src/client.ts`. Anonymous public-lane requests use the golden tenant `…-4000-8000-…001` / pod `national-spine` defaults, mirroring the BFF's `PublicGatewayAnonymousDefaultsFilter`.

## 7. Shared workspace packages (12)

`mobile-design-system` (46 src files), `mobile-auth` (11), `mobile-ndila` (9), `mobile-api-client` (8), `mobile-offline` (7), `mobile-messaging` (6), `mobile-integration` (5), `mobile-nompilo` (5), `mobile-timeline` (5), `mobile-trust` (5), `mobile-registry` (4), `mobile-session` (4, LiveKit telehealth). Mobile does **not** import web `ui/shared-ui` directly — it maintains mobile-native mirrors of the web contracts by design.

## 8. Tests, flows, guards

- **Vitest:** ~115 test files (citizen ~49, provider ~66) + per-package suites. All green as of this recovery (see recovery report for counts).
- **Maestro:** 29 flow YAMLs at `apps/mobile/maestro/flows/` (11 citizen, 18 provider) — authored but never executed against a real APK before this recovery.
- **Guards:** `pnpm guard:mobile-parity` (service parity, wiring, no-mocks over 510 source files) — passing.

## 9. Build & runtime infrastructure truth

| Host | Role | State |
|---|---|---|
| **VM 235** (`41.57.127.235` / `10.50.1.67`) | Engineering control + preview Traefik LB | Node 20, JDK 21. As of this recovery: per-user Android SDK at `~/Android/Sdk` (platform-tools, platforms 34/35, build-tools 35.0.0, NDK 27.1, emulator, android-34 google_apis x86_64 image), Maestro 2.x at `~/.maestro`, pnpm 9.15 shim at `~/.local/bin`. **No KVM and no CPU virtualization** — only software (TCG) emulation possible; slow but functional for evidence capture. |
| **VM 218** (`facility@41.57.127.218:2027`) | Designated Mobile Android Sandbox (KVM, per `docs/mobile/MOBILE_ANDROid_SANDBOX.md`) | **Never bootstrapped; SSH unreachable (connection timeout, verified 2026-07-23).** External blocker — owning team must restore access, then run `scripts/mobile/maestro-vm-bootstrap.sh`. |

Historical context: the 2026-07-05 `mobile-runtime-truth-report.md` recorded `dl.google.com` 403-blocked in-container. From VM 235 directly, `dl.google.com` is reachable (verified 200) — the block was a container-proxy artifact, which is why this recovery could install the SDK and build APKs locally.

## 10. Last meaningful mobile commits (at anchor)

- `47c6bbbf6` 2026-07-23 — feat(mobile+bff): TM-B18 mobile parity for A–D teleconsult capabilities
- `44197a3b9` / `59723fc75` / `ced23eea7` — deep-linking + App Links wave
- `f43b4f206` — canonicalize product name "Impilo"
- Cadence: 2026-03: 11, 04: 38, 05: 16, 06: 57, 07: 52 commits — **continuously active**, concentrated June–July.

## 11. Known defects found at recovery start

1. **Stale lockfile (committed defect):** `citizen-app/package.json` gained `@noble/curves`, `@noble/hashes`, `expo-local-authentication`, `react-native-qrcode-svg`, `react-native-svg` (smart-card wave) without a lockfile update → `pnpm install --frozen-lockfile` failed; typecheck red with 7 module-not-found errors. Repaired in this recovery (lockfile regenerated, additive-only diff).
2. **`mobile:build` was a lie:** aliased to typecheck only. Repaired — now builds real APKs (see `mobile:build:*` scripts).
3. **Preview Keycloak URL baked into configs is unreachable** (`:8480` not exposed) — see §5.
4. **No artifact lane:** no APKs, no artifact directory convention, no checksums, no build metadata — established by this recovery under `artifacts/mobile/<commit>/`.
