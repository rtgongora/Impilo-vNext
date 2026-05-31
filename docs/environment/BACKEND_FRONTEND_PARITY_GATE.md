# Backend–frontend parity gate

## Command

```bash
bash scripts/guard/check-backend-frontend-parity.sh
```

Sub-checks: `check-frontend-mocks-and-stubs.sh`, `check-api-client-surfacing.sh`, parity doc sync.

## Blocking

1. New BFF controller without frontend parity documentation update.
2. New production page with placeholder / mock / no API client.
3. `npm run test:no-stubs` failure.
4. Parity docs out of sync with `generate-parity-docs.mjs`.

## Advisory

- Existing partial/Live gaps in matrix.
- Legacy “coming soon” routes documented in allowlist.
- Page simplification warnings (large line-count drop).

## Integration

| Runner | How |
|--------|-----|
| VM local pipeline | Phase `Backend-to-frontend parity` |
| Change-safety | `check-backend-frontend-parity.sh` |
| GitHub Actions | Job `Backend-to-Frontend Parity Gate` |

## Rule

No backend capability is **complete** unless frontend is API-connected and tested, or marked **internal/backend-only** in the matrix.
