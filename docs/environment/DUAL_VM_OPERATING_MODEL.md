# Dual-VM Operating Model (Web Preview + Mobile Android Sandbox)

Impilo vNext development and runtime validation uses **two active VMs** with distinct roles. GitHub (`rtgongora/Impilo-vNext`) is the sync layer; both VMs work the same branch without divergent unpushed work.

> **This is a dual-VM development/testing model, not a replacement of the Web Preview VM.**  
> The Maestro VM consumes the preview API exposed by the Web Preview VM. It does **not** host the backend, full integration stack, production simulation lab, or production.

## VM roles

### 1. Web Preview / Engineering Control — `41.57.127.235`

| Item | Value |
|------|-------|
| SSH | `ssh -p 2276 robert@41.57.127.235` |
| Repo | `/opt/impilo/repos/Impilo-vNext` |
| Preview / API | `http://41.57.127.235` |
| Branch | `claude/staging-ux-orchestration-remediation-Yypyl` |

**Runs here:** Cursor primary workspace, quality gates, k3s preview deploy, backend/API readiness, web smoke, image builds, branch source-of-truth operations.

**Does not run here:** Android emulator load, Maestro runtime smoke (use 218).

### 2. Mobile Android Sandbox / MOHCC Maestro — `41.57.127.218`

| Item | Value |
|------|-------|
| SSH | `ssh facility@41.57.127.218 -p 2027` |
| Repo | `/opt/impilo/repos/Impilo-vNext` (clone; sync via Git) |
| Mobile API | `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235` |
| Branch | Same as 235 |

**Runs here:** Android emulator (KVM), Maestro E2E, APK build/install validation, logcat, mobile runtime reports.

**Does not run here:** Backend deploy, k3s/Helm, preview promotion, production simulation.

Full Maestro VM runbook: [`docs/mobile/MOBILE_ANDROID_SANDBOX.md`](../mobile/MOBILE_ANDROID_SANDBOX.md).

## Sync rules

1. **Git is the sync layer** — commit and push from one VM before relying on the other.
2. **No divergent unpushed work** across VMs.
3. **235 owns deploy authorization** — preview deploy only from Web Preview VM after gates + user approval.
4. **218 targets 235 API** — mobile tests use `http://41.57.127.235` unless another endpoint is explicitly provided.
5. **Reports flow back** — Maestro/runtime artifacts committed or copied into the repo branch.

## Environment ladder (dev-test)

| Tier | Host | Purpose | Promotion gate |
|------|------|---------|----------------|
| Engineering control | 235 | Build, test, deploy preview | VM quality gates + user deploy auth |
| Dev preview API | 235 (`http://41.57.127.235`) | Web + BFF integration target | Smoke + `/health/version` |
| Mobile runtime validation | 218 | Android emulator + Maestro | KVM ready + Maestro flows + runtime report |
| Future formal staging | TBD | Not on either VM today | See `FUTURE_FORMAL_TEST_STAGING_REQUIREMENTS.md` |

## Testing strategy by environment

| Test type | Where | Script / doc |
|-----------|-------|--------------|
| VM quality gates | 235 | `scripts/pipeline/run-local-quality-gates.sh` |
| HTTP preview regression | 235 (against 235 URL) | `tests/regression/preview-http-regression.sh` |
| Playwright / web E2E | 235 or CI | `ui/one-ui-shell` |
| Mobile typecheck | 235 or 218 | `apps/mobile` → `pnpm mobile:typecheck` |
| Android prebuild / APK | **218 only** | Expo prebuild + Gradle on Maestro VM |
| Maestro mobile smoke | **218 only** | `scripts/mobile/verify-maestro-flows.sh` |
| GitHub CI mobile job | CI runners | `.github/workflows/ci.yml` (Maestro + emulator) |

## Historical note on `41.57.127.218`

On 2026-05-30, documentation recorded that `41.57.127.218` was retired after a public IP reassignment to `41.57.127.235`. **That note is superseded (2026-06-27):** `41.57.127.218` has been reactivated/provisioned as the **MOHCC Maestro Android Mobile Automation Sandbox** and is part of the active vNext dev-test pipeline. The two IPs now refer to **different VMs** with different roles.

## Operator discovery

- Activation report: [`reports/mobile/maestro-vm-activation.md`](../../reports/mobile/maestro-vm-activation.md)
- Web VM baseline: [`VM_BASELINE_AUDIT.md`](./VM_BASELINE_AUDIT.md)
- Agent rules: [`.cursor/rules/remote-cursor-workspace.mdc`](../../.cursor/rules/remote-cursor-workspace.mdc), [`.cursor/rules/mobile-android-sandbox.mdc`](../../.cursor/rules/mobile-android-sandbox.mdc)
