# Android Emulator Readiness (218)

**Environment:** `impilo-mobile-android-sandbox`  
**Host:** `41.57.127.218`  
**Status:** `PENDING` — toolchain not yet installed  
**Updated:** 2026-06-27

## Checklist

| Item | Status | Notes |
|------|--------|-------|
| KVM validated | PASS | See `maestro-vm-activation.md` |
| Android SDK installed | NOT RUN | |
| `ANDROID_HOME` configured | NOT RUN | |
| platform-tools / adb | NOT RUN | |
| build-tools | NOT RUN | |
| emulator + system image | NOT RUN | |
| AVD created and booted | NOT RUN | |
| `adb devices` shows emulator | NOT RUN | |

## Commands (after toolchain install)

```bash
bash scripts/mobile/verify-maestro-vm-kvm.sh
adb devices
```

Plan: [`docs/mobile/MAESTRO_VM_TOOLCHAIN_SETUP_PLAN.md`](../../docs/mobile/MAESTRO_VM_TOOLCHAIN_SETUP_PLAN.md)
