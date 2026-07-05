# Mobile Runtime Truth Report — WS-M (2026-07-05)

Branch: `fable/mobile-runtime-truth` (anchor `74c22f480`).
Doctrine: **never claim runtime success without runtime evidence.** This report
records exactly what was proven, what was fixed, and what remains blocked.

## 1. Apps

| App | Path | Package | Framework |
|---|---|---|---|
| Impilo Health (citizen) | `apps/mobile/citizen-app` | `zw.gov.impilo.citizen` | Expo SDK ~54, React Native 0.81.5, committed `android/` (gradle wrapper, debug-only signing via committed `debug.keystore`) |
| Impilo Provider | `apps/mobile/provider-app` | `zw.gov.impilo.provider` | same |

## 2. Build truth — commands run and real exit codes

Container: node 22, pnpm 10.33.0, JDK 21, gradle at `/opt/gradle`, **no
ANDROID_HOME / adb / emulator / KVM**. Outbound HTTPS goes through the agent
proxy.

| Command | Exit code | Result |
|---|---|---|
| `curl -fSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip` | curl error 22 | `curl: (22) The requested URL returned error: 403` |
| retry (`curl -sS -o /dev/null -w "%{http_code}"` same URL) | 56 | `curl: (56) CONNECT tunnel failed, response 403`; proxy status log: `gateway answered 403 to CONNECT (policy denial or upstream failure)` for `dl.google.com:443` |
| `pnpm install --frozen-lockfile` (apps/mobile) | **0** | 15.9s, lockfile respected |
| `pnpm mobile:typecheck` (at anchor) | **2** | 8 pre-existing TS errors (5 citizen emergency screens, 3 provider screens) — typecheck was **not** green at anchor |
| `pnpm mobile:typecheck` (after fixes, commit `6b9e0e732`) | **0** | green |
| `pnpm mobile:test` | **0** | green (before and after changes) |
| `./gradlew assembleDebug` | **not runnable** | blocked: no Android SDK can be installed in-container (`dl.google.com` denied by proxy, verbatim above). No APK, no size, no sha256 — none exists. |
| `python3 -c "import yaml,...; yaml.safe_load(open('.github/workflows/ci.yml'))"` | **0** | ci.yml valid YAML |
| `bash -n scripts/mobile/runtime-truth.sh` | **0** | shellcheck is not installed in this container (`command -v shellcheck` → 1), so shellcheck-cleanliness is asserted only by inspection |
| credential grep gate (`git grep -iE '(password|secret)\s*[:=]\s*["'\''][^"'\'']+'` over `apps/mobile/maestro` + `scripts/mobile`) | 1 (no matches) | nothing sensitive committed |

**In-container build ceiling: blocked.** The build/runtime paths are (a) the
`mobile-e2e-maestro` CI job (GitHub-hosted runner has the Android SDK) and
(b) the operator VM via `scripts/mobile/runtime-truth.sh`.

## 3. Config resolution chain (verified end-to-end)

Priority per key, in `<app>/src/config.ts`:

1. `Constants.expoConfig.extra.*` — populated at build time by `app.config.ts`
   from `EXPO_PUBLIC_*` env (EAS profile env or shell), with variant-aware
   fallbacks (`EXPO_PUBLIC_APP_VARIANT`: dev/preview/staging/production).
2. `process.env.EXPO_PUBLIC_*` read directly (Metro-inlined).
3. Hardcoded fallback: citizen API `http://192.168.100.211:8160`, KC
   `http://192.168.100.211:8480`; provider API `http://192.168.100.211:8000`
   (config.ts) vs `:8160` (app.config.ts extra — extra wins when present), KC
   `http://192.168.100.211:8080`.
4. `REDIRECT_URI` is always derived at runtime via `Linking.createURL("auth/callback")`
   (never from extra), so Expo Go and standalone builds each get a URI that the
   auth session can intercept.
5. `assertSafeProductionUrls()` (both apps, read and untouched): throws at
   module load for `production` variant if API/KC URLs are not `https://` or
   point at localhost/LAN — production remains guarded.

Preview truth: `app.config.ts` preview extra → API `http://41.57.127.235`
(Envoy), auth `http://41.57.127.235:8480` (Keycloak), LiveKit
`ws://41.57.127.235:7880` — **plain HTTP**, matching
`deploy/helm/impilo-vnext/values-full-preview.yaml`.

### Fixes made (committed on this branch)

| Fix | Commit |
|---|---|
| `app.json` schemes `impilo.citizen`/`impilo.provider` → `impilo-citizen`/`impilo-provider` (match authoritative `app.config.ts` + committed manifests; dotted schemes would break the PKCE redirect) | `076824ee` |
| `network_security_config.xml` per app + `android:networkSecurityConfig` on the **main** manifest `application` tag: cleartext denied by default, allowed ONLY for `41.57.127.235` and `10.0.2.2`. Debug manifests untouched (they keep `usesCleartextTraffic=true`). Release variants can now reach the plain-HTTP preview and nothing else over cleartext | `1b7097af` |
| `eas.json` preview profile of both apps → `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235`, `EXPO_PUBLIC_KEYCLOAK_URL=http://41.57.127.235:8480` (was the non-resolving `https://api-preview.impilo.gov.zw` / `auth-preview.…`) | `10095339` |
| `eas.json` preview `EXPO_PUBLIC_KEYCLOAK_CLIENT_ID` → `impilo-mobile-citizen` / `impilo-mobile-provider`. The preview realm defines **only** these mobile clients (public, PKCE S256, redirect `impilo-citizen://auth/callback` / `impilo-provider://auth/callback`); the previous `citizen-app` / `provider-app` IDs do not exist in the realm and every preview login would have failed with *Client not found* | `29956c9f` |
| Typecheck restored to green: Badge `tone`→`variant`, Button `danger`→`destructive`, SOS selectable chips → small Buttons (with testIDs), EmptyState given required `title` | `6b9e0e73` |

**DNS/TLS migration intent** (eas.json cannot hold comments, recorded here):
the preview profile intentionally points at a raw IP over plain HTTP today.
When preview gets DNS + TLS (`api-preview.impilo.gov.zw`,
`auth-preview.impilo.gov.zw`), update both `eas.json` preview profiles to the
`https://` hostnames and **delete** the `41.57.127.235` domain-config entries
from both `network_security_config.xml` files in the same change.

## 4. Auth model — architecture observation for the IATG trust program

Both apps authenticate by **direct Keycloak PKCE**
(`packages/mobile-auth/src/keycloakClient.ts`): the app opens the Keycloak
authorization endpoint in a browser Custom Tab
(`WebBrowser.openAuthSessionAsync`), exchanges the code itself, and stores
tokens in `expo-secure-store`. Provider identity is then activated in-app
(`ProviderActivationScreen` → `x-provider-id` header).

**Observation (flagged, not fixed here):** mobile bypasses the Experience BFF
for the entire auth handshake — token issuance, refresh, and storage happen on
the device against Keycloak directly, unlike the web shell where the BFF/Envoy
ext_authz chain mediates the session. Consequences: mobile token lifetimes and
client policy are governed only by realm client config; no BFF-side session
revocation/enrichment; trust headers are assembled client-side. This is a
deliberate-looking design (public PKCE clients exist in the realm), but it
should be explicitly reviewed by the IATG trust program against the 10-dimension
access model and the ext_authz doctrine (Golden Thread).

## 5. Maestro coverage

- **Before:** 18 flows in `apps/mobile/maestro/flows/`, **none** exercised
  login — every flow assumed an already-authenticated (or auth-bypassed) app
  state, so runtime "passes" proved navigation, not identity.
- **After (+4, commit `c073018d`):**
  - `citizen-login.yaml`, `provider-login.yaml` — drive the real UI:
    `login-screen` → `login-button` testIDs, then the Keycloak hosted form in
    the Custom Tab (default theme: "Username or email", "Password", "Sign In"),
    with `clearState: true` so cached sessions can't mask a broken login.
  - `citizen-journey-smoke.yaml` — login → home (`health-id-card`) → Health tab
    (`personal-screen`) → Messages (`messaging-inbox-screen`) → Home.
  - `provider-journey-smoke.yaml` — login → provider activation
    (`provider-id-input`/`activate-provider-button`) → facility
    (`select-facility-*`) → workspace (`select-workspace-*`) → `provider-tabs`
    → Patients → search (`patient-search-input`/`patient-search-btn`) →
    `patient-search-results` → Encounter tab (`encounter-screen`).
  - All credentials come from `MAESTRO_*` env (Maestro auto-forwards
    MAESTRO_-prefixed shell vars). Nothing hardcoded; grep gate clean.
  - testID added: `patient-search-results` wrapper in
    `PatientLookupScreen.tsx` (per-patient testIDs are dynamic UUIDs and
    unusable for stable selection). SOS chips gained
    `sos-category-*`/`sos-severity-*` testIDs as part of the typecheck fix.
  - Known risk: Custom-Tab web form automation depends on Chrome exposing the
    Keycloak form in the accessibility tree; must be validated on the VM.

## 6. Seeded users (preview realm `realm-impilo-preview.json`)

Usernames only — passwords exist in the realm file (non-temporary) and must be
taken from there / the operator secret store, never from this repo's docs:

- `citizen.moyo` — CITIZEN (citizen-app candidate)
- `vashandi.facility` — FACILITY_ADMIN + CLINICIAN, has `facility_id` attribute (provider-app candidate)
- `vashandi.worker` — NURSE + CLINICIAN (provider-app alternative)
- `vashandi.national`, `vashandi.hsc`, `vashandi.reviewer` — ADMIN/CLINICIAN
- `superadmin` — all roles

No "seed users required" blocker: one citizen and one provider user exist.
Remaining data dependency: `provider-journey-smoke.yaml` needs at least one
**searchable seeded patient** (set `MAESTRO_PATIENT_QUERY`), one facility from
`GET /internal/v1/facilities`, and one workspace.

## 7. Per-app status (honest)

| App | Status | Evidence basis |
|---|---|---|
| citizen-app | **NOT_PROVEN** | No APK was ever produced in this container (SDK install blocked — verbatim blocker in §2). Typecheck+tests green is code truth, not build or runtime truth. |
| provider-app | **NOT_PROVEN** | Same blocker. |

The in-container ceiling would have been BUILDS_ONLY even with an SDK (no
adb/emulator/KVM). CI (`mobile-e2e-maestro`) and the VM runbook are the
designated paths to BUILDS_ONLY and PROVEN_RUNTIME respectively.

## 8. Ranked blockers

1. **Container cannot build Android** — `dl.google.com` CONNECT denied by the
   agent proxy (403, verbatim §2). Unblock: proxy allowlist, or rely on CI/VM.
2. **No runtime substrate in container** — no adb/emulator/KVM; runtime proof
   must come from the CI emulator job or the VM.
3. **Preview builds must be rebuilt** with the corrected preview profile
   (client IDs + URLs fixed here); any previously distributed preview APK
   still targets a non-existent realm client and dead DNS names.
4. **Custom-Tab login automation unvalidated** — Maestro driving the Keycloak
   web form needs one VM run to confirm selector strategy.
5. **Journey data dependencies in preview** — seeded searchable patient,
   ≥1 facility + workspace (provider journey); citizen profile bootstrap via
   BFF must succeed or AuthGuard parks on "Loading your profile…".
6. **Plain-HTTP preview** — acceptable only under the scoped
   network-security-config exception; DNS+TLS migration intent recorded in §3.

## 9. VM handoff checklist (human operator)

1. Get APKs: download `citizen-debug-apk` / `provider-debug-apk` artifacts
   from a `mobile-e2e-maestro` CI run of this branch, **or** build on the VM:
   `cd apps/mobile && pnpm install && cd citizen-app/android && ./gradlew assembleDebug`
   (repeat for provider-app; needs ANDROID_HOME with platform-tools,
   platforms;android-35, build-tools;35.0.0).
2. Connect a device or start an emulator (`adb devices` shows `device`).
3. Export credentials (values from the preview realm seed / secret store):
   `MAESTRO_CITIZEN_USERNAME`, `MAESTRO_CITIZEN_PASSWORD`,
   `MAESTRO_PROVIDER_USERNAME`, `MAESTRO_PROVIDER_PASSWORD`,
   `MAESTRO_PROVIDER_ID` (e.g. `PRV-2024-00147`), `MAESTRO_PATIENT_QUERY`
   (a seeded patient's name/NID). Install Maestro if absent:
   `curl -Ls https://get.maestro.mobile.dev | bash`.
4. Run: `scripts/mobile/runtime-truth.sh [citizen.apk] [provider.apk]`
   (args optional if built in-repo).
5. Send back: the two `VERDICT …` lines, `reports/mobile/runtime-truth/<ts>/run.log`,
   `*-maestro.log`, `*-logcat-excerpt.txt`, and the `*.png` screenshots.
   The verdict lines map 1:1 to the status enum in §7 and replace NOT_PROVEN.

## 10. Next-wave recommendation

Run `mobile-e2e-maestro` on this branch to convert NOT_PROVEN → BUILDS_ONLY
with artifact hashes (upload steps added here); configure the
`MAESTRO_PREVIEW_CREDENTIALS` secret to attempt PROVEN_RUNTIME in CI; in
parallel, execute the VM runbook (§9) against the real preview. Then take the
§4 BFF-bypass observation to the IATG trust program, and schedule the preview
DNS/TLS migration (§3) so the cleartext exception can be deleted.
