# Android Emulator Sandbox Provisioning Blocker

**Classification:** `ANDROID_EMULATOR_SANDBOX_PROVISIONING_BLOCKER`  
**Report date:** 2026-06-28T07:03:18Z (UTC)  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Commit (static/export evidence):** `146de566d`  
**Environment:** MOHCC Maestro Android sandbox VM (`41.57.127.218`)  
**Hostname:** `ministryofhealth-HVM-domU`  
**SSH:** `facility@41.57.127.218 -p 2027`  
**Mobile API target:** `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235`

---

## Executive summary

Static mobile closure and Expo export are **closed**. Android emulator runtime closure on the 218 sandbox VM is **blocked** by unstable Xen nested-virtualization / emulator orchestration. Multiple launch strategies (direct SSH, Cursor agent sessions, tmux, systemd, nohup, detached supervisor) failed to produce a stable emulator boot, adb device registration, or durable runtime logs. This is an **infrastructure provisioning blocker**, not a mobile application code failure.

---

## 1. Static mobile closure — PASS

Evidence: `reports/mobile/maestro-mobile-closure-rerun.md`, `reports/mobile/maestro-mobile-closure-rerun.log`

| Gate | Result |
|------|--------|
| Citizen typecheck | **PASS** |
| Provider typecheck | **PASS** |
| Citizen tests | **PASS** — 145 tests |
| Provider tests | **PASS** — 135 tests |
| Registry tests | **PASS** — 4 tests |
| Service parity / wiring | **PASS** |
| No-mock guard | **PASS** — 435 files scanned |
| Combined `guard:mobile-parity` | **PASS** |

---

## 2. Expo export closure — PASS

| Check | Result | Evidence |
|-------|--------|----------|
| Citizen export log contains `Exported: dist` | **PASS** | `reports/mobile/expo-export-citizen.log` (final line) |
| `apps/mobile/citizen-app/dist` exists | **PASS** | Verified on engineering VM after export |
| Provider export log contains `Exported: dist` | **PASS** | `reports/mobile/expo-export-provider.log` (final line) |
| `apps/mobile/provider-app/dist` exists | **PASS** | Verified on engineering VM after export |

Expo export closure detail: `docs/implementation/mobile-expo-export-closure.md`, `reports/mobile/expo-export-closure.md`

---

## 3. Android emulator runtime closure — BLOCKED (218 sandbox)

### Attempted orchestration modes (all failed to complete runtime closure)

| Mode | Outcome |
|------|---------|
| Direct SSH interactive session | SSH session reset around emulator launch |
| Cursor Remote SSH agent session | Session lost during emulator boot window |
| tmux detached session | No durable boot evidence; session/process lost |
| systemd unit fallback | Unit did not yield stable adb device |
| nohup background launch | Process observed briefly, then absent |
| Detached supervisor script | `qemu-system-x86_64-headless -avd impilo-x86-api35` seen running briefly; later absent |

### Post-attempt inspection (218)

| Observation | Result |
|-------------|--------|
| Emulator / qemu process | **None** running |
| `adb devices` | **No device** — adb server only |
| AVD metadata | **Corrupted** |
| Runtime phase logs | **Empty (0 bytes)** |

### AVD corruption detail

| Path | Status |
|------|--------|
| `~/.android/avd/impilo-x86-api35.ini` | Present |
| `~/.android/avd/impilo-x86-api35.avd/config.ini` | **0 bytes** (corrupted) |

A zero-byte `config.ini` prevents reliable AVD boot and indicates the emulator launch path did not complete provisioning cleanly.

### Empty runtime logs (0 bytes — orchestration failure, not success)

These files were created as log targets but captured **no durable output**:

- `reports/mobile/direct-emulator-supervisor.log`
- `reports/mobile/direct-emulator-trace.log`
- `reports/mobile/direct-emulator-stdout.log`
- `reports/mobile/direct-emulator-stderr.log`
- `reports/mobile/maestro-runtime-phase.log`
- `reports/mobile/emulator-boot.log`

**Interpretation:** Empty logs mean the orchestration layer never reached a stable, observable emulator boot — not that runtime smoke passed silently.

---

## 4. Journal evidence

| Finding | Detail |
|---------|--------|
| VM type | Xen **HVM domU** (`ministryofhealth-HVM-domU`) |
| Emulator-attempt window | Kernel/system journal shows **fresh Xen boot initialization** coincident with emulator launch attempts |
| Successful emulator boot | **Not captured** |
| adb device registration | **Not captured** |

The correlation between emulator launch attempts and Xen domU re-initialization strongly suggests nested virtualization / KVM stability issues on this host under load, rather than an application-level defect.

---

## 5. Classification

```
ANDROID_EMULATOR_SANDBOX_PROVISIONING_BLOCKER
```

---

## 6. Explicit non-claims

- This is **not** a mobile app code failure.
- Android runtime smoke (citizen/provider) is **not** marked passed.
- iOS / native / app-store closure is **not** claimed.
- Physical-device validation is **not** claimed.
- APK/debug build success is **not** claimed.
- Maestro E2E flows are **not** claimed green.

---

## 7. Recommended next action

1. **Provision or repair** a hardened Android emulator runner with stable nested virtualization/KVM — outside Xen HVM domU instability if necessary.
2. **Prefer** a physical-device Maestro runner or a dedicated bare-metal/KVM emulator host if Xen nested virt on 218 remains unstable.
3. **Keep 218 classified as inconclusive/blocked** for emulator runtime closure until it can reliably:
   - boot an AVD with non-corrupt `config.ini`,
   - register a device in `adb devices`,
   - capture non-empty logs and screenshots,
   - run citizen and provider runtime smoke against `http://41.57.127.235`.
4. **Do not rerun** emulator closure loops on 218 until hypervisor/emulator host remediation is complete.
5. Runtime scripts now enforce `RUN_STARTED` markers, treat empty phase/log/pid files as orchestration failure, and emit formal blocker reports on early exit (`scripts/mobile/maestro-runtime-orchestration.sh`).

---

## Pipeline status after this blocker

| Phase | Status |
|-------|--------|
| Static / code closure | **CLOSED** |
| Expo export | **CLOSED** |
| Android emulator runtime closure | **BLOCKED** — provisioning |
| Citizen/provider runtime smoke | **NOT RUN** |
| Cross-surface testing | **Not ready** |

---

## Related artifacts

- Static closure: `reports/mobile/maestro-mobile-closure-rerun.md`
- Expo export: `reports/mobile/expo-export-closure.md`
- Emulator readiness (prior): `reports/mobile/android-emulator-readiness.md`
- Maestro VM activation: `reports/mobile/maestro-vm-activation.md`
- Orchestration scripts: `scripts/mobile/maestro-vm-bootstrap.sh`, `scripts/mobile/maestro-vm-runtime-closure.sh`, `scripts/mobile/maestro-runtime-orchestration.sh`
