# Android Emulator Readiness

**Environment:** `impilo-mobile-android-sandbox` (`41.57.127.218`)  
**Status:** **BLOCKED** — `ANDROID_EMULATOR_SANDBOX_PROVISIONING_BLOCKER`  
**Updated:** 2026-06-28  
**Blocker report:** [`android-emulator-sandbox-provisioning-blocker-20260628T070318Z.md`](./android-emulator-sandbox-provisioning-blocker-20260628T070318Z.md)

## KVM (pre-validated at activation; runtime unstable)

| Check | Result |
|-------|--------|
| Nested virtualisation | Enabled at activation |
| `/dev/kvm` | Present at activation |
| `facility` in `kvm` group | Yes at activation |
| Runtime stability under emulator load | **FAIL** — Xen domU re-init observed |

## Emulator checklist

| Item | Status |
|------|--------|
| Android SDK installed | Partial (attempted on 218) |
| AVD `impilo-x86-api35` | **Corrupted** — `config.ini` 0 bytes |
| Headless boot | **FAIL** — no durable qemu/adb |
| `adb devices` | **FAIL** — no device registered |
| `sys.boot_completed` | **NOT CAPTURED** |
| Runtime logs | **Empty (0 bytes)** — orchestration failure |

## Classification

Do **not** mark emulator readiness as passed. Remediate host provisioning before retry.
