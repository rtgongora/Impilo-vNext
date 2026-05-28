# Wave 20/21 — Production Readiness Report

> Date: 2026-05-28  
> Scope: Experience compose slice + sovereign Rx-path overlay  
> Classification: **CONDITIONAL PASS** — seven demo journeys demonstrable in compose; Ndila/Nhume remain host-only

---

## Executive summary

Wave 20 established seven production-readiness journeys (web + mobile), demo seeds, parity matrix export, and Playwright/Maestro coverage. Wave 21 hardened the sovereign overlay and closed WARN probes for wellness, inventory, and public-health field tasks when running with `-SovereignHost`.

| Profile | Command | Journey probes |
|---------|---------|----------------|
| Experience (default) | `.\tools\dev\up.ps1 -Build` | Core clinical, inpatient, wellness, inventory, hub — PASS; Ndila/Nhume SKIP |
| + Sovereign overlay | `.\tools\dev\up.ps1 -SovereignHost -Build` | Above + Rx path + field tasks — PASS |

Golden patient: **CPID-ZW-00001** · Golden tenant: **00000000-0000-4000-8000-000000000001**

---

## Verification evidence

| Check | Tool | Expected |
|-------|------|----------|
| BFF + journey probes | `verify-demo-journeys.mjs` | No FAIL rows |
| Sovereign stack | `wait-sovereign-health.mjs` | All services UP |
| Sovereign journeys | `verify-demo-journeys.mjs --sovereign` | Rx + field tasks ≥1 demo row |
| Parity export | `generate-wave20-parity-matrix.mjs` | JSON + MD artifacts |
| Web e2e | Playwright `production-readiness.spec.ts` | 9/9 on compose :3000 |
| Mobile provider | Maestro `provider-production-readiness.yaml` | Seven journey tabs |
| Mobile citizen | Maestro `citizen-production-readiness.yaml` | Seven journey sections |

---

## Journey status

| # | Journey | Compose-only | + SovereignHost |
|---|---------|:------------:|:---------------:|
| 1 | Core clinical / Rx | PASS (queue) | PASS (+ pharmacy, billing, MusheX) |
| 2 | Inpatient | PASS | PASS |
| 3 | Wellness | PASS | PASS |
| 4 | Enterprise / inventory | PASS | PASS |
| 5 | Telemedicine → dispatch | PASS/WARN | PASS (+ dispatch) |
| 6 | Public health + geo | SKIP (Ndila) | PASS (field tasks) |
| 7 | Data & intelligence | PASS | PASS |

---

## Known exceptions

- **Ndila** and **Nhume** are not in the monorepo; probes SKIP in compose-only mode.
- **Telemedicine sessions** may WARN when PCT telehealth depth is limited; BFF V40 local seed exists.
- **Fresh DB resets** may be required after Flyway renumbering — see `BUILD_RUN_STATUS.md` Slice 13 note.

---

## Sign-off checklist

- [x] Sovereign overlay documented and scripted (`docker-compose.sovereign.yml`, `up.ps1 -SovereignHost`)
- [x] Demo seeds for pharmacy, MusheX, Costa, dispatch, surveillance, wellness (V44), inventory (V31)
- [x] CI: `e2e-compose-smoke` + `e2e-sovereign-smoke`
- [x] Parity matrix generated in CI
- [ ] Full Ndila/Nhume in compose (future — external services)

See [HOW_TO_DEMO_PRODUCTION_READINESS.md](./HOW_TO_DEMO_PRODUCTION_READINESS.md) and [PRODUCTION_GAPS_AND_NEXT_WAVE.md](./PRODUCTION_GAPS_AND_NEXT_WAVE.md).
