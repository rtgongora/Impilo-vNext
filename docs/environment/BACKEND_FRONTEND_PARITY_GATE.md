# Backend–frontend parity gate

## Purpose

Ensure backend capabilities are not marked complete without corresponding real frontend surfaces (or documented internal-only status).

## Commands

```bash
bash scripts/guard/check-backend-frontend-parity.sh
bash scripts/guard/check-frontend-mocks-and-stubs.sh
bash scripts/guard/check-api-client-surfacing.sh
```

## Blocking in

- `scripts/pipeline/run-local-quality-gates.sh` (phase: Backend-to-frontend parity)
- GitHub Actions via same script (frontend-lint / preview-pipeline jobs)

## Detects

- Parity documentation drift vs embedded capability registry
- New BFF controllers without matrix/doc update
- Placeholder pages (“coming soon”, `JSON.stringify` dumps)
- Production mocks/stubs (`npm run test:no-stubs`)
- New hooks with no route/feature import (warn)

## Allowlisted gaps

Pre-existing gaps documented in the matrix with **Partial** / **Fixture** maturity remain until closed; **new** gaps without matrix updates fail the gate.
