# Maestro VM Activation Report

**Activation date:** 2026-06-27T12:00:00+02:00  
**Status:** `activated` — KVM validated; mobile toolchain installation **pending approval**  
**Environment name:** `impilo-mobile-android-sandbox`  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`

## VM role

**MOHCC Maestro Android Mobile Automation Sandbox** — Android emulator automation and mobile runtime validation runner for Impilo vNext citizen/provider apps.

> Dual-VM model: consumes preview API on Web Preview VM (`41.57.127.235`). Does not host backend, web preview, full integration stack, production simulation, or production.

## SSH endpoint

```bash
ssh facility@41.57.127.218 -p 2027
```

| Field | Value |
|-------|-------|
| Host | `41.57.127.218` |
| Hostname | `ministryofhealth-HVM-domU` |
| Port | `2027` |
| User | `facility` |
| Sudo | Full sudo available |
| Groups | `kvm` (validated) |
| Repo path (expected) | `/opt/impilo/repos/Impilo-vNext` |

## Resource summary

| Item | Value |
|------|-------|
| OS | Ubuntu 24.04.4 LTS |
| vCPU | 20 |
| RAM | 48 GiB |
| Disk | 1 TB |

## KVM validation

| Check | Result |
|-------|--------|
| Nested virtualisation | Enabled |
| CPU virtualisation flags exposed | Yes (`egrep -c '(vmx\|svm)' /proc/cpuinfo` → 40) |
| `/dev/kvm` | Present |
| `facility` in `kvm` group | Yes |
| KVM readable | Yes |
| KVM writable | Yes |

Re-verify: `bash scripts/mobile/verify-maestro-vm-kvm.sh`

## API target

```bash
EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
```

Mobile runtime smoke on 218 must target the Web Preview API unless another endpoint is explicitly provided.

## Relationship to Web Preview VM

| | Web Preview (235) | Maestro (218) |
|--|-------------------|---------------|
| Environment | `impilo-web-preview` | `impilo-mobile-android-sandbox` |
| SSH | `robert@41.57.127.235 -p 2276` | `facility@41.57.127.218 -p 2027` |
| Hosts preview API | Yes | No (consumer) |
| k3s / backend deploy | Yes | No |
| Android emulator | No | Yes |

## Current status

- [x] VM provisioned and SSH accessible
- [x] KVM / nested virt validated
- [x] Documentation and operator rules aligned (2026-06-27)
- [ ] Repo clone at `/opt/impilo/repos/Impilo-vNext` on 218
- [ ] Toolchain install (see `docs/mobile/MAESTRO_VM_TOOLCHAIN_SETUP_PLAN.md`)
- [ ] Android SDK + emulator installed
- [ ] Maestro CLI installed
- [ ] Citizen + provider debug APK build
- [ ] Maestro production-readiness flows green
- [ ] Runtime closure reports populated

## Next required setup steps

1. On **218:** clone or pull repo; confirm branch matches 235 `HEAD`.
2. **Product owner approval** for toolchain install per `MAESTRO_VM_TOOLCHAIN_SETUP_PLAN.md`.
3. Install Android SDK, platform tools, emulator image; confirm `adb devices`.
4. Install Maestro CLI (`https://maestro.mobile.dev/`).
5. Resolve any Expo prebuild blockers (see `reports/mobile/mobile-runtime-smoke.md`).
6. Build/install citizen + provider debug APKs with `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235`.
7. Confirm preview API healthy on 235 before smoke runs.
8. Run `bash scripts/mobile/verify-maestro-flows.sh`; update runtime reports; commit via Git.

## Related docs

- [`docs/mobile/MOBILE_ANDROID_SANDBOX.md`](../../docs/mobile/MOBILE_ANDROID_SANDBOX.md)
- [`docs/environment/VNEXT_ENVIRONMENT_LADDER.md`](../../docs/environment/VNEXT_ENVIRONMENT_LADDER.md)
- [`docs/mobile/MAESTRO_VM_TOOLCHAIN_SETUP_PLAN.md`](../../docs/mobile/MAESTRO_VM_TOOLCHAIN_SETUP_PLAN.md)
- Machine-readable: [`maestro-vm-activation.json`](./maestro-vm-activation.json)
