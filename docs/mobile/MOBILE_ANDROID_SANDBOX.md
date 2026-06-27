# MOHCC Maestro Android Mobile Automation Sandbox

**Role:** Mobile Android Sandbox / Maestro runtime validation runner  
**Host:** `41.57.127.218`  
**SSH:** `ssh facility@41.57.127.218 -p 2027`  
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

| Item | Web Preview / Engineering Control | Mobile Android Sandbox |
|------|-----------------------------------|-------------------------|
| IP | `41.57.127.235` | `41.57.127.218` |
| SSH | `robert@41.57.127.235 -p 2276` | `facility@41.57.127.218 -p 2027` |
| Repo path | `/opt/impilo/repos/Impilo-vNext` | Same path (clone on Maestro VM) |
| Primary role | Engineering control, web preview, API readiness | Android emulator + Maestro |
| Preview/API | Hosts `http://41.57.127.235` | **Consumes** `http://41.57.127.235` |
| Git sync | Source-of-truth branch operations | Pull same branch; no divergent unpushed work |

Full dual-VM operating model: [`docs/environment/DUAL_VM_OPERATING_MODEL.md`](../environment/DUAL_VM_OPERATING_MODEL.md).

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

## Expected reports

Commit or copy back to the repo branch on the Web Preview VM when artifacts are documentation/report outputs:

| Artifact | Location |
|----------|----------|
| Maestro VM activation | `reports/mobile/maestro-vm-activation.md`, `.json` |
| Mobile runtime smoke | `reports/mobile/mobile-runtime-smoke.md` |
| Maestro run output | `reports/mobile/maestro-runs/` (per-run logs/screenshots as added) |
| Runtime closure checklist | `docs/implementation/mobile-runtime-smoke.md` (update checkboxes) |

## Runtime closure responsibilities

1. **235 first:** Web preview API healthy; quality gates pass for the target commit.
2. **Git sync:** Maestro VM on same branch/commit as 235 (`git pull` before runs).
3. **Maestro VM:** Install toolchain (see Next steps), prebuild, emulator, Maestro flows.
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

- [`docs/environment/DUAL_VM_OPERATING_MODEL.md`](../environment/DUAL_VM_OPERATING_MODEL.md)
- [`docs/implementation/mobile-runtime-smoke.md`](../implementation/mobile-runtime-smoke.md)
- [`apps/mobile/maestro/README.md`](../../apps/mobile/maestro/README.md)
- [`reports/mobile/maestro-vm-activation.md`](../../reports/mobile/maestro-vm-activation.md)
