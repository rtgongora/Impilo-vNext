# Mobile E2E smoke (Maestro)

This folder contains **Tier-1** mobile smoke flows for:

- Citizen app (`zw.gov.impilo.citizen`)
- Provider app (`zw.gov.impilo.provider`)

## Where to run

| Environment | Host | Use for |
|-------------|------|---------|
| **Web Preview / Engineering Control** | `robert@41.57.127.235:2276` | Code, typecheck, Git — not emulator load |
| **Mobile Android Sandbox (Maestro VM)** | `facility@41.57.127.218:2027` | Emulator, prebuild, APK, Maestro smoke |

Set API target before runtime smoke:

```bash
export EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
```

> Dual-VM model: Maestro VM consumes the preview API on 235. It does not host backend or production simulation. See `docs/mobile/MOBILE_ANDROID_SANDBOX.md`.

## Run on Maestro VM (Android)

Prereqs (on **218**):

- Android Studio SDK + KVM-backed emulator (`bash scripts/mobile/verify-maestro-vm-kvm.sh`)
- Maestro CLI (see https://maestro.mobile.dev/)

Run:

```bash
cd /opt/impilo/repos/Impilo-vNext
export EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
bash scripts/mobile/verify-maestro-flows.sh
# Or:
maestro test apps/mobile/maestro/flows
```

