# Maestro VM Toolchain Setup Plan (218)

**Status:** Prepared — **do not execute** until product owner approves toolchain install on `41.57.127.218`.

**Target host:** `ssh facility@41.57.127.218 -p 2027`  
**Repo:** `/opt/impilo/repos/Impilo-vNext`  
**API:** `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235`

## Prerequisites (validated)

- [x] Ubuntu 24.04.4 LTS, 20 vCPU, 48 GiB RAM, 1 TB disk
- [x] KVM nested virt, `/dev/kvm`, `facility` in `kvm` group
- [x] Full sudo for `facility`
- [ ] Repo cloned at canonical path
- [ ] Doc/rules alignment committed (this pass)

## Planned package install (218)

Base tooling:

```bash
sudo apt-get update
sudo apt-get install -y git curl ca-certificates gnupg unzip zip build-essential
```

Java (Android / Gradle):

```bash
sudo apt-get install -y openjdk-17-jdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

Node.js LTS + pnpm 9:

```bash
# NodeSource LTS or nvm — match repo expectation (Node 20)
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
sudo corepack enable
corepack prepare pnpm@9.15.0 --activate
```

Android SDK (command-line tools):

```bash
export ANDROID_HOME="${HOME}/Android/Sdk"
export PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator"
# Download cmdline-tools from developer.android.com
# sdkmanager: platform-tools, build-tools, emulator, system-images;android-XX;google_apis;x86_64
# avdmanager create avd ...
```

Mobile monorepo deps:

```bash
cd /opt/impilo/repos/Impilo-vNext/apps/mobile
pnpm install --frozen-lockfile
pnpm mobile:typecheck
```

Expo / EAS (if repo assessment confirms — apps/mobile uses Expo):

```bash
npm install -g eas-cli
# Or: pnpm dlx expo --version
```

Maestro:

```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
maestro --version
```

## Post-install validation

```bash
bash scripts/mobile/verify-maestro-vm-kvm.sh
adb devices
cd /opt/impilo/repos/Impilo-vNext && bash scripts/mobile/verify-maestro-flows.sh
```

Update:

- `reports/mobile/android-emulator-readiness.md`
- `reports/mobile/citizen-runtime-smoke.md`
- `reports/mobile/provider-runtime-smoke.md`
- `reports/mobile/MAESTRO_MOBILE_RUNTIME_CLOSURE_REPORT.md`

## Blockers to resolve before runtime closure

From Mobile Closure Wave (`46254765`):

- Expo export: `react-native-web@^0.21.0` may be required for export path
- Prior prebuild: citizen `app.config.ts` syntax — verify on 218 before APK build
- Costa citizen: remains truthfully blocked at runtime (expected)

## Authorization

Proceed with install only when product owner explicitly approves. This document is the plan only.
