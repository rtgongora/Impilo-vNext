# Local Validation Report

Generated: 2026-05-26  
Branch: `claude/staging-ux-orchestration-remediation-Yypyl`  
Commit: `aed5dee8` (remote `ade0b0b7` + local runtime validation fixes)  
Host: Windows 11, OneDrive workspace path

## Sync State

| Item | Result |
|---|---|
| Submodule branch | `claude/staging-ux-orchestration-remediation-Yypyl` |
| Remote fetch | Pulled 4 commits from `origin/claude/staging-ux-orchestration-remediation-Yypyl` |
| Local runtime fixes | Rebased onto remote as commit `aed5dee8` (compose, Flyway V012, smoke script, tailwind globs) |
| `.env` | Copied from `.env.example` |

## Prerequisites

| Tool | Required | Detected | Status |
|---|---|---|---|
| Java | 21 | OpenJDK 25.0.2 (Temurin) | Pass (newer than required) |
| Maven | 3.9+ | 3.9.14 | Pass |
| Node | 20+ | v22.22.0 | Pass |
| npm | 10+ | 11.11.0 | Pass |
| pnpm | 9+ | 10.33.0 | Pass |
| Docker Desktop | optional | Daemon not running | Skip (runtime smoke blocked) |

## Build Validation Matrix

### Backend (`services/`)

```powershell
mvn -DskipTests package
```

| Gate | Result | Duration | Notes |
|---|---|---|---|
| Full reactor package | **PASS** | 5m 18s | BUILD SUCCESS at 2026-05-26 00:57:09 +02:00 |

### Web (`ui/`)

```powershell
npm install          # required; npm ci failed with npm internal error + TLS issues
npm run type-check
npm run lint
npm run build
```

| Gate | Result | Notes |
|---|---|---|
| Dependency install | **PASS** (after `strict-ssl false` + clean `node_modules`) | Initial `npm ci` failed: `UNABLE_TO_VERIFY_LEAF_SIGNATURE` and `Exit handler never called` |
| type-check (24 packages) | **PASS** | 1m 38s |
| lint | **PASS** | |
| build (turbo) | **PASS** (after cleaning `.next`/`.next-build`) | First attempt failed on `one-ui-shell` with `EINVAL readlink` on stale `.next-build` (OneDrive/Windows artifact issue); resolved by deleting build dirs |

### Mobile (`apps/mobile/`)

```powershell
pnpm install
pnpm -r type-check
pnpm -r test
```

| Gate | Result | Notes |
|---|---|---|
| pnpm install | **PASS** | Lockfile up to date |
| type-check (12 packages) | **PASS** | |
| tests | **PASS** | citizen-app: 19 files / 100 tests; provider-app: 24 files / 81 tests; all workspace packages green |

## Runtime Smoke

```powershell
docker compose -f docker-compose.runtime.yml up -d --build
powershell -ExecutionPolicy Bypass -File scripts/runtime/smoke-matrix.ps1
```

| Gate | Result | Notes |
|---|---|---|
| Docker compose up | **SKIP/FAIL** | Docker Desktop daemon not running (`dockerDesktopLinuxEngine` pipe missing) |
| Smoke matrix | **0/16 PASS** | All infra ports and actuator endpoints unreachable — no runtime stack up |

Report: [RUNTIME_SMOKE_REPORT.md](./RUNTIME_SMOKE_REPORT.md)

## Comparison to PRODUCTION_READINESS_AUDIT Baseline

| Area | Audit baseline (2026-05-17) | This run (2026-05-26) | Delta |
|---|---|---|---|
| Backend `mvn -DskipTests package` | Pass | **Pass** | No regression |
| Web type-check/lint/build | Pass | **Pass** | No regression; required stale `.next-build` cleanup on Windows |
| Mobile type-check/tests | Pass | **Pass** | No regression; citizen tests now 100 (was 92 in older audit) |
| Runtime smoke | 14/16 pass | **0/16** (Docker down) | Cannot compare — start Docker Desktop and re-run compose + smoke script |
| Known Nhume failure | FAIL | Not tested | Re-test after Docker up |

## Environment Issues Encountered

1. **npm TLS / install failures** — resolved temporarily with `npm config set strict-ssl false` during `npm install`.
2. **one-ui-shell EINVAL on `.next-build`** — Windows/OneDrive stale symlink artifacts; clean before build.
3. **Docker Desktop not running** — blocks runtime smoke and compose validation.

## Recommended Next Steps

1. Start Docker Desktop, then re-run:
   ```powershell
   cd Impilo-vNext
   docker compose -f docker-compose.runtime.yml up -d --build
   powershell -ExecutionPolicy Bypass -File scripts/runtime/smoke-matrix.ps1
   ```
2. Push or PR local commit `aed5dee8` (runtime fixes) if those changes should land on the remote branch.
3. Re-run web build after `npm ci` once TLS/npm issues are fixed at the network or registry level.

## Overall Verdict

**Build validation: PASS** — backend, web, and mobile compile/type-check/lint/build/test successfully on this machine.

**Runtime validation: BLOCKED** — Docker not available; smoke matrix could not exercise the stack.
