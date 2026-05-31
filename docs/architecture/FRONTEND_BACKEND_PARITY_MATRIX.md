# Frontend ↔ backend parity matrix

> Alias of the canonical matrix: [`docs/frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md`](../frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md)

## Parity gate (blocking in VM local pipeline)

| Check | Script |
|-------|--------|
| Parity docs in sync | `check-backend-frontend-parity.sh` |
| No production mocks/stubs | `check-frontend-mocks-and-stubs.sh` |
| New API clients surfaced | `check-api-client-surfacing.sh` |
| No-stub CI guard | `ui/one-ui-shell` → `npm run test:no-stubs` |

## Classifications

| Status | Meaning |
|--------|---------|
| **Live** | Route → hook → BFF → service; real data |
| **Partial** | Some workflows wired; gaps documented in matrix |
| **Fixture** | Must not ship as Live in production paths |
| **Internal/backend-only** | No citizen/provider UI required; documented in matrix |

## New work

Adding BFF controllers, OpenAPI paths, or major routes requires updating the matrix (via `generate-parity-docs.mjs`) in the same change.

See [`docs/environment/BACKEND_FRONTEND_PARITY_GATE.md`](../environment/BACKEND_FRONTEND_PARITY_GATE.md).
