# Adult Medicine — routed / external blockers (Wave completion tracking)

Tracked for pack §25 DoD. **Do not absorb these as adult-medicine SoR.**

| Item | Owner | Smallest unblock | Pack status (2026-07-31) |
|---|---|---|---|
| §20 offline READ gate (`READ_PROBLEM` / `READ_PROGRAMME` / `READ_CONDITION`) | `tshepo-offline-service` `OfflineRulesEngine.READ_ACTIONS` + READ_PATIENT-covers branch | Add three reads; no token change if done with covers branch | **NOT BUILT** — filed here; medicine consumes when live |
| §20 offline replay breadth | `offline-edge-service` `OfflineEdgeService.replayActions` | Generalise beyond two vitals FHIR action types | **NOT BUILT** |
| §15 measurement domains (symptoms, PROs, peak flow, …) | `telemonitoring-service` | Extend programmes/thresholds inside TM | **ROUTED** — spineLinks only |
| §15 enrolment UI | telemonitoring + BFF | Plan create/approve + device issue surfaces | **ROUTED** — backend-only today |
| §15 pack-owned PCT problem anchor | telemonitoring (Wave 5.2) | Done: `V006` + `PctProblemContributionClient` on approve | **BUILT** |
| Estate product-truth / phase6 gate debt | Other lanes | Fix gaps; do not raise baseline | Blocks `deploy_recommended` until cleared or `AUTHORIZE DEPLOY WITH VM GATES` |
| Dual MDT V051↔V114 FK wiring on V051 side | Telemedicine lane | Nullable `case_item_id` already on V114 (V120); V051 untouched | Pack side ready; session SoR still telemedicine |

## §25 DoD evidence checklist

- [ ] Clinician walkthrough COMPLETE on HEAD-matched preview (`AUTHORIZE DEPLOY WITH VM GATES` + `/health/version`)
- [ ] Problems/medicines/tests/procedures reconciled in product UI (not service-only)
- [ ] Multimorbidity coherent end-to-end (no silent UNKNOWN where SoR exists)
- [ ] Emergency / surgical handoffs proven in product
- [ ] Offline (§20) unblocked by owners **or** explicit accepted external blocker (this file)
- [x] Guard regressions: no fake Validated / empty-as-none / silence-as-confirmation (medicine 110+ tests; scenario tests added)

When every box is evidenced, flip §25 in [`completion-register.md`](completion-register.md).
