# Backend capability inventory

> **Canonical surfacing matrix:** [`docs/frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md`](../frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md)  
> Regenerate: `node scripts/frontend/generate-parity-docs.mjs`

This inventory lists BFF and sovereign backend capabilities and their expected experience surfaces.
The matrix is the source of truth for backend-to-frontend parity gates.

| Plane | Domain | Capability | Backend path | Contract | Web maturity |
|-------|--------|------------|--------------|----------|--------------|
| (see matrix) | — | Full registry embedded in matrix file | — | `contracts/openapi/*.yaml` | Live / Partial / Fixture |

**Gate:** `bash scripts/guard/check-backend-frontend-parity.sh`

**Rule:** No capability is *complete* without real frontend surfacing, tests, and preview visibility, or an explicit **internal/backend-only** note in the matrix.
