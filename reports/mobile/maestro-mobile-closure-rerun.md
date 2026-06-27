# Maestro Mobile Closure Rerun

**Date:** 2026-06-27  
**Host:** `41.57.127.235` (engineering control — static gates; 218 blocked)  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Log:** `reports/mobile/maestro-mobile-closure-rerun.log`

## Results

| Gate | Result | Detail |
|------|--------|--------|
| Citizen typecheck | **PASS** | `tsc --noEmit` |
| Provider typecheck | **PASS** | `tsc --noEmit` |
| Citizen tests | **PASS** | 145 tests (32 files) — was 122 in prior wave |
| Provider tests | **PASS** | 135 tests (40 files) — was 123 in prior wave |
| Registry tests | **PASS** | 4 tests |
| Service parity | **PASS** | 21 canonical services |
| Service wiring | **PASS** | wiring + navigation verified |
| No-mock guard | **PASS** | 435 files scanned (was 412) |
| Combined `guard:mobile-parity` | **PASS** | |

## Differences from Mobile Closure Wave (`46254765`)

- Test counts increased (new tests added on branch since closure wave).
- No-mock scan file count increased 412 → 435.
- `react-native-web@^0.21.0` added to both apps for Expo export closure.
- Runtime smoke still **NOT RUN** (requires 218 emulator).

## Costa status (unchanged doctrine)

- Citizen Costa: truthfully blocked (no citizen BFF route).
- Provider Costa: partially wired via existing BFF.
