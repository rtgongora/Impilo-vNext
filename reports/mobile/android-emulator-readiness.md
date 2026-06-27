# Android Emulator Readiness

**Environment:** `impilo-mobile-android-sandbox` (`41.57.127.218`)  
**Status:** **NOT RUN** — emulator setup blocked pending 218 SSH access  
**Updated:** 2026-06-27

## KVM (pre-validated at activation)

| Check | Result |
|-------|--------|
| Nested virtualisation | Enabled |
| `/dev/kvm` | Present |
| `facility` in `kvm` group | Yes |
| KVM readable/writable | Yes |

## Emulator checklist

| Item | Status |
|------|--------|
| Android SDK installed | NOT RUN |
| AVD `impilo-phone-api35` | NOT RUN |
| Headless boot | NOT RUN |
| `adb devices` | NOT RUN |
| `sys.boot_completed` | NOT RUN |
| KVM acceleration in emulator | NOT RUN |

## Run on 218

```bash
bash scripts/mobile/maestro-vm-bootstrap.sh
emulator -avd impilo-phone-api35 -no-window -no-audio &
adb wait-for-device
adb shell getprop sys.boot_completed
```
