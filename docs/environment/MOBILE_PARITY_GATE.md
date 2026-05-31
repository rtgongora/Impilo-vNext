# Mobile parity gate

## Command

```bash
bash scripts/guard/check-mobile-parity.sh
```

## Blocking

1. New mobile production mocks/stubs/placeholders without `config/parity-allowlist.yml` entry.
2. New web user-facing route without `MOBILE_PARITY_MATRIX.md` update.
3. New mobile screen without API/service integration.
4. Mobile tree changed and `pnpm install` fails.
5. Parity registry changed without matrix regeneration.

## Advisory

- Existing documented gaps in matrix and allowlist.
- Full Android APK / iOS TestFlight until stable runners.
- Deep emulator/Maestro tests.
- Preview API URL verification when not in app.json (see MOBILE_PREVIEW_TESTING.md).

## Integration

| Runner | How |
|--------|-----|
| VM local pipeline | Phase `Mobile parity` |
| Change-safety | `check-mobile-parity.sh` |
| GitHub Actions | Job `Mobile Parity Gate` |

## Docs

- [MOBILE_PARITY_MATRIX.md](../architecture/MOBILE_PARITY_MATRIX.md)
- [MOBILE_APP_INVENTORY.md](../architecture/MOBILE_APP_INVENTORY.md)
- [config/parity-allowlist.yml](../../config/parity-allowlist.yml)
