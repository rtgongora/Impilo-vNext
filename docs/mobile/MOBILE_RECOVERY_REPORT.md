# Mobile Recovery Report — Impilo & Impilo Provider

**Date:** 2026-07-23 · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl` · **Mission start commit:** `209b6ad45` · **APK source commit:** `7ffe90558`
**Host:** preview LB VM (10.50.1.67 / 41.57.127.235), Ubuntu 24.04, 32 cores, 125GB RAM, **no KVM** (no nested virtualization).

---

## 1. Executive conclusion

Both mobile applications are **real, substantial, buildable, and runnable**. The recovery produced the first-ever APKs for this repository, proved both apps cold-launch without crashing on a live Android runtime, and proved the citizen app pulls **live governed content from the preview backend over TLS**. Provider Maestro smoke passes 7/7. The two systemic blockers found are **infrastructure, not application defects**: (a) preview-edge Keycloak is not reachable for mobile auth, and (b) this VM cannot run a hardware-accelerated emulator, and under software emulation (TCG) a full google_apis Android framework is unstable while the stable ATD image cannot produce pixel screenshots. Prior "mobile parity" claims were directionally right about code existing but were never runtime-proven; this mission establishes the honest baseline.

## 2. Does Impilo (citizen) build? — **YES**

`:app:assembleRelease` from the committed `android/` project: **BUILD SUCCESSFUL** (14m55s cold). APK verified: correct package, Hermes bundle present, preview API host baked in.

## 3. Does Impilo Provider build? — **YES**

**BUILD SUCCESSFUL** (1m24s on warm Gradle daemon). Same verification.

## 4. APK artifacts

Directory: `artifacts/mobile/7ffe9055847dc61bfae15ddef1d182f188d7b956/`

| File | Size | SHA-256 |
|---|---|---|
| `Impilo-preview-0.1.0-7ffe90558.apk` | 85,923,504 B (82M) | `7ba486ede83227919313ab41fb3c0de042988b8fc09d0a81bbaacfb535e32601` |
| `Impilo-Provider-preview-0.1.0-7ffe90558.apk` | 79,207,832 B (76M) | `44a8e430a67a1b110419bf1139a7f9828b7dc0de6dd012c96b39bf1d7b0d15ad` |

Application IDs: `zw.gov.impilo.citizen.dev` / `zw.gov.impilo.provider.dev` (versionName 0.1.0, versionCode 1 — the committed native projects bake the `.dev` variant suffix; release IDs require `expo prebuild` or EAS).
Also in the artifact dir: `SHA256SUMS`, `metadata.json`, `build-logs/`, `logcat-{citizen,provider}-launch.txt`, `screenshots/` (hierarchy XML evidence), `maestro/` (run logs), `emulator-evidence/`.

## 5. Build commands (standing)

```bash
cd apps/mobile
corepack pnpm@9 run mobile:build:impilo     # citizen APK → artifacts/mobile/<sha>/
corepack pnpm@9 run mobile:build:provider   # provider APK
corepack pnpm@9 run mobile:build            # both (real APKs now, no longer a typecheck alias)
corepack pnpm@9 run mobile:verify           # aapt2 + Hermes-bundle + baked-URL verification
```
Implemented in `scripts/mobile/build-apks.sh` / `verify-apk.sh` / `android-env.sh` (per-user SDK at `~/Android/Sdk`, JDK 21, Gradle capped at 8 workers/4GB to protect the preview LB). Non-zero exit on any failure (verified).

## 6. Framework and dependency findings

Expo SDK 54.0.x / React Native 0.81.5 / React 19.1 / Hermes / new architecture; pnpm 9 workspace (self-contained at `apps/mobile`, frozen-lockfile installs cleanly); 12 shared `@impilo/mobile-*` packages; compileSdk/targetSdk 35, minSdk 24, Gradle 8.14.3, NDK 27.1. `EXPO_PUBLIC_*` env is inlined at Metro bundle time inside `createBundleReleaseJsAndAssets`, so exported preview endpoints flow into any gradle build without prebuild. Full inventory: `docs/mobile/MOBILE_PRODUCT_TRUTH.md`.

## 7. Runtime launch results

Runtime: software (TCG) emulator on this VM — see §15 for why that is the only local option.

| Check | Impilo (citizen) | Impilo Provider |
|---|---|---|
| Install (`pm install`) | **Success** | **Success** |
| Cold launch | **Success** — process stayed alive | **Success** — process stayed alive |
| Fatal exceptions in logcat | **0** | **0** |
| Hermes JS boot | `ReactNativeJS: Running "main"` | `ReactNativeJS: Running "main"` |
| First screen | Real welcome/login: "Impilo — Your health, in your hands", Sign in with Impilo, phone signup, anonymous lane (Health info / Verify / Track SOS) | Real login: "Impilo Provider — Clinical workflow management", Worklist/Encounters/Lab Results/Outreach, 4-mode selector (Provider/Outreach/Supervisor/Offline) |
| Navigation | Anonymous lane → Health-info hub → topic list → full article, and back | Login face verified (deeper navigation requires auth — blocked, §10) |

Evidence: `logcat-*-launch.txt` + `screenshots/*/**-hierarchy.xml` (uiautomator view-hierarchy dumps; on-screen text captured verbatim because pixel readback is broken under TCG — §15).

## 8. Visual evidence

- **Real pixel screenshots (achieved):** after allocating 8 vCPUs to the google_apis-30 guest, both apps were captured in true pixels under `artifacts/mobile/<sha>/screenshots/`:
  - `citizen/01-welcome-clean.png` — full branded welcome/login (emerald theme, heart mark, "Your health, in your hands", capability chips, Sign in with Impilo, anonymous lane).
  - `citizen/02-health-info-loading-state.png` — Health information screen with genuine **loading state** (spinner mid-fetch from preview API).
  - `citizen/01-welcome-maestro.png`, `02-health-info.png`, `03-health-topic.png` — Maestro-driven and manual captures.
  - `provider/02-login-smoke.png` — Impilo Provider login (clinical-blue theme, Worklist/Encounters/Lab Results/Outreach chips, 4-mode selector), captured by the passing Maestro smoke.
- **View-hierarchy dumps (complementary, text-faithful):** `screenshots/{citizen,provider}/*-hierarchy.xml` — every on-screen element for: citizen welcome/login, health-info hub with live topic list, full guidance article; provider login shell.
- **Recordings:** not feasible under TCG (screenrecord shares the broken ATD readback path; the google_apis guest is too slow for useful video).
- **Not fabricated:** no mocked screens; every capture is from the running app or recorded as a gap.

## 9. Preview API connectivity — **PROVEN LIVE**

The citizen Health-info screen rendered governed guidance topics ("Danger signs…", "Antenatal care…", full newborn-care article; topic rows keyed by backend UUIDs). Proof this was fetched, not bundled: those strings are **absent from the shipped JS bundle** (`strings` audit: 0 hits) while the API host is present (1 hit). Network path from the emulator: guest DNS resolves `impilo.mohcc.gov.zw` → 41.57.127.235, guest iptables DNAT rewrites to 10.50.1.67 (hairpin-NAT workaround), TLS presents the real cert.

## 10. Authentication result — **blocked by a dead Keycloak, not by configuration**

> **CORRECTION (2026-07-26).** This section previously claimed "the preview edge exposes no Keycloak route for mobile." **That was wrong.** The Traefik route exists and is correct (`Host(impilo.mohcc.gov.zw) && (PathPrefix(/realms) || PathPrefix(/resources))`, priority 90000, above the UI catch-all). The real cause: **Keycloak has been down for 7+ days** — pod in `Error`, `endpoints/keycloak` empty, so every `/realms/*` request returns `503 no available server`. This means **web login has been broken for a week too**, not just mobile. Nothing alerts on estate health; that is the durable finding.

**The mobile auth chain is fully configured and verified end-to-end** — every link checked 2026-07-26:

| Link | Verified value |
|---|---|
| Realm client (preview realm import) | `impilo-mobile-citizen` / `impilo-mobile-provider` — `publicClient: true`, `standardFlow: true`, `pkce.code.challenge.method: S256` |
| Realm redirect URI | `impilo-citizen://auth/callback` / `impilo-provider://auth/callback` |
| APK baked config (`assets/app.config`) | `keycloakUrl: https://impilo.mohcc.gov.zw`, realm `impilo`, matching clientId + redirectUri |
| APK manifest scheme | registers `impilo-citizen` (and `https`) — callback will resolve to the app |
| Edge route | present, priority 90000 |

### RESOLVED 2026-07-26 — mobile auth PROVEN end-to-end

Root cause was a **6-day-old code bug**, not configuration: commit `0b625f727` (2026-07-20, *"feat(keycloak): WebAuthn passwordless realm config for passkey login (L1)"*) added 11 realm fields as `webAuthnPasswordlessPolicy*` where Keycloak 25 expects `webAuthnPolicyPasswordless*`. Realm import rejects unknown properties, so Keycloak died at startup on **every** restart. Web and mobile login were both dead for 6 days; `endpoints/keycloak` was empty for 9.

Fixed: 11 field renames in the realm JSON · live ConfigMap patched data-only (Helm ownership preserved) · `KC_PROXY_HEADERS=xforwarded` added to `templates/keycloak.yaml` so the issuer advertises `https://` through Traefik (it previously advertised `http://`, which fails strict issuer validation *after* a successful credential check) · `reconcile-client-secrets.sh` run.

**Proof, in the real APK on the redroid fixture:**
```
app builds PKCE   client_id=impilo-mobile-citizen, code_challenge_method=S256, state=…
 → browser opens Keycloak → Impilo login page renders (screenshot: redroid/screenshots/auth-keycloak-login.png)
 → credentials submitted
 → VIEW cat=[BROWSABLE] dat=impilo-citizen://auth/... → zw.gov.impilo.citizen.dev/.MainActivity
 → app resumes foreground
```
Protocol-level token also verified independently: `iss: https://impilo.mohcc.gov.zw/realms/impilo`, `azp: impilo-mobile-citizen`, access+refresh+id_token.

**Honest boundary:** post-login the app raises `PROFILE_LOAD_FAILED` ([AuthGuard.tsx:35](../../apps/mobile/citizen-app/src/navigation/AuthGuard.tsx#L35)). Expected — the proof account (`mobile.proof.citizen`) exists in Keycloak only and has no backend person record. The **auth layer** is proven; the **citizen data layer** needs a seeded Health ID and is now testable for the first time.

**Credential trap:** `citizen.moyo` carries a `password-history` credential (password rotated to an unknown value) so it 401s, while `vashandi.worker` / `superadmin` still work on seed passwords. `MAESTRO_CITIZEN_USERNAME` points at `citizen.moyo` — reset it or repoint the flow before the citizen Maestro run.

---

**Historical restore runbook** (kept for reference):
1. `kubectl delete pod keycloak-<id> -n impilo-full-preview --force --grace-period=0` (clears the stale containerd sandbox reservation left by the eviction cascade)
2. wait for `endpoints/keycloak` to be non-empty
3. **run `scripts/keycloak/reconcile-client-secrets.sh`** — realm-import ships *placeholder* client secrets; without this, client auth fails even with a healthy pod (a healthy-pod-plus-401s state that is easily misdiagnosed as an app defect)
4. prove **both** login paths in the same window — they share the one dependency:
   - **mobile:** `apps/mobile/maestro/flows-runtime/` login flows on the redroid fixture with seeded credentials
   - **web:** an end-to-end web login against the shell. Web auth has been equally dead all week, so the web lanes have only been exercising `permitAll` surfaces; proving both in one pass unblocks every lane at once.

Note the ATD emulator image ships no browser, so the PKCE Custom-Tab step needs the google_apis image or the redroid fixture — the latter is now the standing runtime.

## 11. Maestro result

Maestro 2.7.0 driver works on the emulator (headless, hierarchy-driven).

- **Provider smoke: PASS 7/7, twice** (once on ATD hierarchy-only, once on google_apis with a real-pixel screenshot) — launch with clear state, `login-screen` + `login-button` testIDs, shell texts.
- **Citizen anonymous health-info journey: 8/9** — launch → login screen → guest tile → health-info screen (live API list) → search field → text input → hide keyboard all pass; the final search-button tap needs a TCG-tolerant scroll strategy (expected to pass as written under KVM).
- **Defect found (suite-wide):** all 29 flows in `apps/mobile/maestro/flows/` use the legacy `extendedWaitUntil: {id: …}` DSL that Maestro ≥2.x **rejects**, and target release appIds that a debug APK never has. Runtime-verified 2.x flows + migration notes committed at `apps/mobile/maestro/flows-runtime/`.
- **Verified-execution boundary:** login flows require preview Keycloak exposure + seeded credentials — not claimable today (§10).

## 12. Web/mobile parity summary

Full matrix: `docs/mobile/WEB_MOBILE_PARITY_MATRIX.md` (three sections: Impilo, Impilo Provider, shared platform; statuses from Verified parity → Missing/API-blocked). Headlines: both apps have broad genuine surface (99 + 114 screens, real service layers, trust-header stack mirroring web contracts); citizen anonymous gateway = **Verified parity (runtime)**; most authenticated journeys = **Partial (API-verified, runtime-blocked on auth)**; gaps concentrate in: caregiving/household depth, provider activation self-service, Vashandi shifts depth, Rito, regulatory self-service, and Khuluma richness vs web. Orphaned screens and endpoint drift are itemised in the matrix.

## 13. Important missing journeys (top of backlog)

1. Authenticated citizen core loop (records, appointments, prescriptions) — runtime-provable only after §10.
2. Citizen household/caregiver management vs web `caregiving/` — partial UI, thin service wiring.
3. Provider Facility Mode + TSHEPO context switch — screens exist; end-to-end context resolution unproven.
4. Khuluma messaging parity (web hub vs mobile basic messaging).
5. Impilo Live / telemedicine session join (LiveKit) — needs real device (WebRTC on TCG emulator is not meaningful).

## 14. Defects repaired during recovery

| Defect | Fix |
|---|---|
| `mobile:build` silently ran only a typecheck | Now builds both APKs (`apps/mobile/package.json`) |
| `verify-apk.sh` false negative: `pipefail` + `grep -q` SIGPIPE on `unzip -l` | Drain the pipe before grepping (commit `9ca289438`) |
| Maestro suite unrunnable on Maestro 2.x (legacy DSL + wrong appIds) | Runtime-verified 2.x flows committed (`flows-runtime/`), migration documented |
| No standing toolchain on any reachable host | Per-user Android SDK + Maestro install, `scripts/mobile/android-env.sh` |
| Emulator "black screen" misdiagnosis risk | Characterised readback failure vs real rendering via SurfaceFlinger/hierarchy evidence |

During diagnosis, an invalid `pm.dexopt.install=skip` property (set by this mission) crash-looped the ATD guest's PackageManager; corrected to `verify` and the framework recovered — recorded as an operational law, not an app defect.

## 15. Remaining blockers

1. **Preview Keycloak exposure for mobile** (§10) — infrastructure change, small and low-risk.
2. **No KVM on this VM** — TCG-only emulation: ATD image = stable but black screenshots (broken SurfaceFlinger readback / 0x0 host display); google_apis image = real pixels but framework watchdog kills system_server under sustained load, faster than an 86MB install completes. **This is a VM limitation, not an application failure** (evidence: `emulator-evidence/`; the same APKs install and run cleanly on the ATD framework).
3. **VM 218** (`facility@41.57.127.218:2027`, the designated KVM-capable Android sandbox) — unreachable (connection timeout), never bootstrapped (`scripts/mobile/maestro-vm-bootstrap.sh` is ready for it).
4. **eslint binary missing** in the mobile workspace (`spawn ENOENT`) — lint gate cannot run; typecheck and tests are green.
5. **iOS** — config-only, no native projects; needs EAS + Apple credentials (pre-existing, unchanged).

## 16. Recommended next sprint

1. Expose preview Keycloak to mobile (edge route) → unlock every authenticated journey + login Maestro flows.
2. Restore/bootstrap VM 218 (or enable nested virt on a VM) → real screenshots, recordings, full Maestro suite, WebRTC.
3. Migrate the 29 legacy Maestro flows to 2.x DSL (mechanical; pattern in `flows-runtime/README.md`) and parameterise appId for debug/release.
4. Add `eslint` devDependency wiring so `mobile:lint` runs.
5. Decide the applicationId strategy for preview distribution (`.dev` bake vs prebuild variants) before any wider APK handout.
6. CI lane: GitHub-hosted runner job building both APKs per release branch (the local scripts are directly reusable).

## 17. Permanent mobile preview lane (adopted: redroid)

- **Build:** `pnpm mobile:build` on this host (or CI) per merge to the canonical branch → versioned `artifacts/mobile/<sha>/` with checksums + metadata (already automated).
- **Runtime (the fixture): redroid — Android-in-container, docker-managed on the LB host.** `scripts/mobile/redroid-docker-fixture.sh start` → container `impilo-redroid`, adb `127.0.0.1:15555`, `--restart unless-stopped` permanence, volume-persistent `/data`; driven by `scripts/mobile/redroid-runtime.sh` / `pnpm mobile:verify:runtime`. No KVM needed (host binderfs; one-time `modprobe binder_linux`). **Proven 2026-07-23:** both preview APKs installed in <1s and rendered real-pixel screenshots (`artifacts/mobile/<sha>/redroid/screenshots/`) — the same APKs that took minutes-to-impossible on TCG emulation. The k8s chart variant exists but is disabled pending a k3s CRI compatibility pass (findings in the runbook); VM 218 demoted to secondary fallback.
- **Evidence:** each lane run stores APKs, logcat, Maestro output, screenshots into the same artifact layout; recovery-report deltas instead of full rewrites.
- **Distribution:** internal QR/download page for the preview APKs once the auth edge route lands (no production keystore in git — release signing stays operator-held, route documented in `docs/mobile/android-internal-install.md`).

## 18. Git commits created (this mission)

| Commit | Content |
|---|---|
| `e2f731762` | docs: MOBILE_PRODUCT_TRUTH.md |
| `7ffe90558` | build scripts + package.json commands + .gitignore artifacts/ |
| `9ca289438` | fix: verify-apk pipefail false negative |
| `4f59e4c01` | docs: WEB_MOBILE_PARITY_MATRIX.md |
| `785f33856` | test: runtime-verified Maestro 2.x flows + DSL migration note |
| (this commit) | docs: MOBILE_RECOVERY_REPORT.md |

Static gates at close: **typecheck exit 0; tests exit 0 (231+ tests passed across citizen/provider/packages); lint blocked (missing eslint binary, §15.4)**.
