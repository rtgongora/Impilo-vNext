# MOHCC Maestro Android Mobile Automation Sandbox

**Environment name:** `impilo-mobile-android-sandbox` / MOHCC Maestro VM  
**Hostname:** `ministryofhealth-HVM-domU`  
**Role:** Mobile Android Sandbox / Maestro runtime validation runner  
**Host:** `41.57.127.218`  
**SSH:** `ssh facility@41.57.127.218 -p 2027`  
**Active user:** `facility` (sudo available; member of `kvm` group)  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl` (same as Web Preview VM — sync via Git)  
**Mobile API target:** `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235`

> **Dual-VM model — not a replacement of the Web Preview VM.**  
> This is a **dual-VM development/testing model**. The Maestro VM **consumes** the preview API exposed by the Web Preview VM (`http://41.57.127.235`). It does **not** host the backend, full integration stack, production simulation lab, or production.

## Purpose

Dedicated Android emulator automation and mobile runtime validation for Impilo vNext citizen and provider apps. Runs Maestro smoke, APK build/install validation, logcat capture, and runtime evidence collection against the **Web Preview API** on `41.57.127.235`.

## Environment role

| Runs here | Does **not** run here |
|-----------|------------------------|
| Android emulator (KVM-backed) | k3s / Helm preview deploy |
| Maestro E2E smoke (`apps/mobile/maestro/flows/`) | Backend service deploy or image import |
| Expo prebuild / Gradle `assembleDebug` | Quality gates for web preview (run on 235) |
| APK install and runtime smoke | Production simulation lab |
| Screenshots, logcat, runtime reports | Real patient data or production secrets |
| Mobile parity runtime closure evidence | App-store publishing |
| | iOS native build claims (Android-only sandbox) |
| | Emulator load on Web Preview VM (235) |

## Relationship to Web Preview VM

The Maestro VM:

- **Consumes** API from `http://41.57.127.235`
- **Does not** host backend, web preview, full integration stack, production simulation, or production

| Item | Web Preview / Engineering Control (`impilo-web-preview`) | Mobile Android Sandbox (`impilo-mobile-android-sandbox`) |
|------|-----------------------------------|-------------------------|
| IP | `41.57.127.235` | `41.57.127.218` |
| SSH | `robert@41.57.127.235 -p 2276` | `facility@41.57.127.218 -p 2027` |
| Repo path | `/opt/impilo/repos/Impilo-vNext` | Same path (clone on Maestro VM) |
| Primary role | Engineering control, web preview, API readiness | Android emulator + Maestro |
| Preview/API | Hosts `http://41.57.127.235` | **Consumes** `http://41.57.127.235` |
| Git sync | Source-of-truth branch operations | Pull same branch; no divergent unpushed work |

Full pipeline ladder: [`docs/environment/VNEXT_ENVIRONMENT_LADDER.md`](../environment/VNEXT_ENVIRONMENT_LADDER.md) · Dual-VM model: [`docs/environment/DUAL_VM_OPERATING_MODEL.md`](../environment/DUAL_VM_OPERATING_MODEL.md).

## Validated KVM readiness

Validated at Maestro VM activation (2026-06-27):

| Check | Result |
|-------|--------|
| OS | Ubuntu 24.04.4 LTS |
| vCPU | 20 |
| RAM | 48 GiB |
| Disk | 1 TB |
| Nested virtualisation | Enabled |
| `egrep -c '(vmx\|svm)' /proc/cpuinfo` | 40 |
| `/dev/kvm` | Present |
| `facility` in `kvm` group | Yes |
| KVM readable / writable | Yes |

Re-verify after hypervisor or user-group changes:

```bash
egrep -c '(vmx|svm)' /proc/cpuinfo
ls -l /dev/kvm
groups facility | grep -q kvm && echo "kvm group OK"
test -r /dev/kvm && test -w /dev/kvm && echo "KVM RW OK"
```

## API target

All mobile runtime smoke on this VM must use:

```bash
export EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
```

Use a different endpoint **only** when explicitly provided (e.g. future formal integration/staging).

Before mobile smoke, confirm preview API readiness on 235:

```bash
curl -sf http://41.57.127.235/health/version || curl -sf http://41.57.127.235/
```

## What runs here

- Android SDK, platform-tools, build-tools
- Android emulator (KVM-backed), ADB
- Mobile runtime smoke (manual + automated)
- APK install / build validation
- Screenshots, logcat, runtime evidence
- Citizen app runtime validation
- Provider app runtime validation
- Maestro / Detox / Appium (when configured)
- Mobile runtime reports (committed back via Git)

## What must not run here

- Backend deploys or k3s/Helm preview operations
- Production services, production secrets, real patient data
- App-store / Play Store publishing
- Full integration stack or production simulation load
- Web preview hosting
- Fake iOS native build claims (Android/KVM only on Ubuntu)
- Replacing truthful blocked states with fake data

## Promotion role

Mobile work cannot move from **static/code closure** to **runtime/mobile preview closure** until this environment passes:

| Gate | Report |
|------|--------|
| Emulator readiness | `reports/mobile/android-emulator-readiness.md` |
| KVM / VM activation | `reports/mobile/maestro-vm-activation.md` |
| Citizen runtime smoke | `reports/mobile/citizen-runtime-smoke.md` |
| Provider runtime smoke | `reports/mobile/provider-runtime-smoke.md` |
| Mobile no-mock/no-stub guard | CI / `guard:mobile-parity` on 235 |
| Mobile parity/wiring guard | `pnpm guard:mobile-parity` |
| APK/build readiness | Debug APK or documented blocker |
| Runtime closure summary | `reports/mobile/MAESTRO_MOBILE_RUNTIME_CLOSURE_REPORT.md` |

See [`docs/environment/VNEXT_PROMOTION_GATES.md`](../environment/VNEXT_PROMOTION_GATES.md).

## Mobile Closure Wave → runtime closure

**Static closure achieved** (commit `46254765`, prior `0ce94f82`):

- Citizen/provider typecheck: PASS · tests: PASS (122 + 123 + 4 registry)
- Service parity, wiring, no-mocks (412 files), combined `guard:mobile-parity`: PASS
- Costa citizen: truthfully blocked (no citizen BFF route)
- Expo export: blocked by missing `react-native-web@^0.21.0`
- Runtime smoke, citizen/provider start, EAS/APK/iOS: **NOT RUN**

**Purpose of this VM:** move mobile from static/code closure to **runtime/mobile preview closure** by running emulator validation against `http://41.57.127.235`.

Details: [`docs/implementation/mobile-closure-wave.md`](../implementation/mobile-closure-wave.md).

## Expected reports

Commit or copy back to the repo branch (prefer commit via Git on 235 as source-of-truth):

| Report | Purpose |
|--------|---------|
| `reports/mobile/maestro-vm-activation.md` (+ `.json`) | VM activation evidence |
| `reports/mobile/android-emulator-readiness.md` | SDK/emulator/ADB readiness |
| `reports/mobile/citizen-runtime-smoke.md` | Citizen journey runtime PASS/FAIL |
| `reports/mobile/provider-runtime-smoke.md` | Provider journey runtime PASS/FAIL |
| `reports/mobile/mobile-preview-access.md` | Preview API reachability from mobile |
| `reports/mobile/MAESTRO_MOBILE_RUNTIME_CLOSURE_REPORT.md` | Runtime closure summary |
| `reports/mobile/mobile-runtime-smoke.md` | Legacy combined smoke rollup |
| `docs/implementation/mobile-runtime-smoke.md` | Checklist checkbox updates |

## Runtime closure responsibilities

1. **235 first:** Web preview API healthy; quality gates pass for the target commit.
2. **Git sync:** Maestro VM on same branch/commit as 235 (`git pull` before runs).
3. **Maestro VM:** Install toolchain per [`MAESTRO_VM_TOOLCHAIN_SETUP_PLAN.md`](./MAESTRO_VM_TOOLCHAIN_SETUP_PLAN.md) (after approval), prebuild, emulator, Maestro flows.
4. **Evidence:** Update `reports/mobile/mobile-runtime-smoke.md` with PASS/FAIL per journey.
5. **Commit on 235:** Push report artifacts from either VM via Git — no divergent unpushed work.

## Operator commands

### Session start (Maestro VM)

```bash
ssh facility@41.57.127.218 -p 2027
cd /opt/impilo/repos/Impilo-vNext
git fetch origin
git checkout claude/staging-ux-orchestration-remediation-Yypyl
git pull origin claude/staging-ux-orchestration-remediation-Yypyl
git rev-parse --short HEAD
export EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
```

### Static mobile gates (either VM; prefer 235 for CI parity)

```bash
cd apps/mobile
npx pnpm@9.15.0 install --frozen-lockfile
npx pnpm@9.15.0 mobile:typecheck
```

### Android prebuild + debug APK (Maestro VM only)

```bash
cd /opt/impilo/repos/Impilo-vNext/apps/mobile
export EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
npx pnpm@9.15.0 dlx expo prebuild --platform android --no-install --clean
# Per-app Gradle assembleDebug after prebuild — see apps/mobile/*/android/
```

### Maestro smoke

```bash
cd /opt/impilo/repos/Impilo-vNext
bash scripts/mobile/verify-maestro-flows.sh
# Or directly:
maestro test apps/mobile/maestro/flows
```

### KVM sanity (Maestro VM)

```bash
bash scripts/mobile/verify-maestro-vm-kvm.sh
```

## Acceptance criteria

Maestro VM is **operationally ready** when all of the following pass on `41.57.127.218`:

- [ ] Repo cloned at `/opt/impilo/repos/Impilo-vNext`, branch matches 235 HEAD
- [ ] KVM checks pass (`scripts/mobile/verify-maestro-vm-kvm.sh`)
- [ ] Android SDK + emulator installed; `adb devices` shows running emulator
- [ ] Maestro CLI installed (`maestro --version`)
- [ ] Citizen + provider debug APKs build and install
- [ ] `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235` — apps reach preview BFF
- [ ] Maestro production-readiness flows green for citizen and provider
- [ ] `reports/mobile/mobile-runtime-smoke.md` updated with runtime PASS evidence
- [ ] No backend deploy attempted from Maestro VM

## Security constraints

- No production secrets on the Maestro VM.
- No real patient data — Dev Preview Sandbox test data only.
- No app-store publishing from this environment.
- Do not claim iOS native builds from this Android-only sandbox.

## Related docs

- [`docs/environment/VNEXT_ENVIRONMENT_LADDER.md`](../environment/VNEXT_ENVIRONMENT_LADDER.md)
- [`docs/environment/DUAL_VM_OPERATING_MODEL.md`](../environment/DUAL_VM_OPERATING_MODEL.md)
- [`MOBILE_SANDBOX_ARCHITECTURE.md`](./MOBILE_SANDBOX_ARCHITECTURE.md)
- [`MOBILE_RUNTIME_SANDBOX_ARCHITECTURE.md`](./MOBILE_RUNTIME_SANDBOX_ARCHITECTURE.md)
- [`MAESTRO_VM_TOOLCHAIN_SETUP_PLAN.md`](./MAESTRO_VM_TOOLCHAIN_SETUP_PLAN.md)
- [`docs/implementation/mobile-closure-wave.md`](../implementation/mobile-closure-wave.md)
- [`docs/implementation/mobile-runtime-smoke.md`](../implementation/mobile-runtime-smoke.md)
- [`apps/mobile/maestro/README.md`](../../apps/mobile/maestro/README.md)
- [`reports/mobile/maestro-vm-activation.md`](../../reports/mobile/maestro-vm-activation.md)
