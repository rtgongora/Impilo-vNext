# Maestro VM Activation Report

**Activation date:** 2026-06-27T00:00:00Z (documented)  
**Status:** `activated` — KVM validated; mobile toolchain installation pending  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`

## VM role

**MOHCC Maestro Android Mobile Automation Sandbox** — Android emulator automation and mobile runtime validation runner for Impilo vNext citizen/provider apps.

> Dual-VM model: consumes preview API on Web Preview VM (`41.57.127.235`). Does not host backend, full integration stack, production simulation, or production.

## SSH endpoint

```bash
ssh facility@41.57.127.218 -p 2027
```

## Resource summary

| Item | Value |
|------|-------|
| OS | Ubuntu 24.04.4 LTS |
| vCPU | 20 |
| RAM | 48 GiB |
| Disk | 1 TB |
| SSH user | `facility` |
| SSH port | `2027` |
| Repo path (expected) | `/opt/impilo/repos/Impilo-vNext` |

## KVM validation

| Check | Result |
|-------|--------|
| Nested virtualisation | Enabled |
| `egrep -c '(vmx\|svm)' /proc/cpuinfo` | 40 |
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
| SSH | `robert@41.57.127.235 -p 2276` | `facility@41.57.127.218 -p 2027` |
| Hosts preview API | Yes | No (consumer) |
| k3s / backend deploy | Yes | No |
| Android emulator | No | Yes |

## Current status

- [x] VM provisioned and SSH accessible
- [x] KVM / nested virt validated
- [ ] Repo clone at `/opt/impilo/repos/Impilo-vNext` on 218 (operator)
- [ ] Android SDK + emulator installed
- [ ] Maestro CLI installed
- [ ] Citizen + provider debug APK build
- [ ] Maestro production-readiness flows green
- [ ] `reports/mobile/mobile-runtime-smoke.md` runtime PASS evidence

## Next required setup steps

1. On **218:** clone or pull repo; confirm branch matches 235 `HEAD`.
2. Install Android SDK, platform tools, emulator image; confirm `adb devices`.
3. Install Maestro CLI (`https://maestro.mobile.dev/`).
4. Fix any blocking `app.config.ts` / prebuild issues (see `reports/mobile/mobile-runtime-smoke.md`).
5. Build and install citizen + provider debug APKs with `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235`.
6. Confirm preview API healthy on 235 before smoke runs.
7. Run `bash scripts/mobile/verify-maestro-flows.sh`; commit updated runtime reports to Git.

## Related docs

- [`docs/mobile/MOBILE_ANDROID_SANDBOX.md`](../../docs/mobile/MOBILE_ANDROID_SANDBOX.md)
- [`docs/environment/DUAL_VM_OPERATING_MODEL.md`](../../docs/environment/DUAL_VM_OPERATING_MODEL.md)
- Machine-readable: [`maestro-vm-activation.json`](./maestro-vm-activation.json)
