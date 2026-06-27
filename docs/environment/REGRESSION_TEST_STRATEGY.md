# Regression Test Strategy

## What exists

| Layer | Location | Protects |
|-------|----------|----------|
| HTTP baseline | `tests/regression/preview-http-regression.sh` | Root, auth, shell zones, BFF health/version |
| Playwright E2E | `ui/one-ui-shell` (`npm run e2e`) | Deeper UI flows (CI `e2e-test` job) |
| Route parity | `ui/one-ui-shell` `npm run test:routes` | Route inventory vs filesystem |
| No-stubs | `ui/one-ui-shell` `npm run test:no-stubs` | Gap-closure rule enforcement |

## How to run

```bash
# Local / VM against preview
PREVIEW_BASE_URL=http://41.57.127.235 bash tests/regression/preview-http-regression.sh

# Via master gates
bash scripts/test/run-preview-gates.sh
```

## Protected surfaces (HTTP baseline)

- `/`, `/auth/login`, `/home`
- Registry, clinical, enterprise, data planes
- Nompilo (`/ask`), Fundo (`/learning`), Ndila, Nhume (`/dispatch`), MusheX (`/finance/mushex-platform`)
- BFF `/actuator/health`, `/health/version`

## Not yet covered

- Authenticated session flows end-to-end
- Write/mutation workflows (prescribe, claim, payment)
- Mobile app UI regression — Maestro on **Maestro VM (218)** or CI; see `docs/mobile/MOBILE_ANDROID_SANDBOX.md`
- Cross-service integration beyond BFF health
- Performance and visual regression

## Adding tests

1. Add route to `preview-http-regression.sh` if HTTP-only check suffices.
2. Add Playwright spec under `ui/one-ui-shell` for interaction-heavy flows.
3. Update `docs/architecture/FRONTEND_ROUTE_INVENTORY.md` via `scripts/architecture/sync-pipeline-inventories.sh`.
