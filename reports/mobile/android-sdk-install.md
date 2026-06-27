# Android SDK Install

**Host:** Maestro VM `41.57.127.218`  
**Status:** **NOT RUN** — 218 not accessible from 235 agent session  
**Target SDK:** API **35** (matches `compileSdkVersion` / `targetSdkVersion` in citizen + provider apps)

## Planned configuration

| Variable | Value |
|----------|-------|
| `ANDROID_HOME` | `/home/facility/android-sdk` |
| Platform | `platforms;android-35` |
| Build-tools | `build-tools;35.0.0` |
| System image | `system-images;android-35;google_apis;x86_64` |
| AVD name | `impilo-phone-api35` |

Script: `scripts/mobile/maestro-vm-bootstrap.sh`

## Verification commands (after install on 218)

```bash
sdkmanager --version
sdkmanager --list_installed
adb version
emulator -version
avdmanager list avd
```
