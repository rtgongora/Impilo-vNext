# Mobile Test Gate

| Gate | Blocking? | Command | Status |
|------|-----------|---------|--------|
| pnpm install | Advisory | `scripts/test/run-mobile-checks.sh` | Implemented |
| lint / typecheck | Advisory | per-app if `package.json` scripts exist | Partial |
| unit tests | Advisory | per-app `test` script | Partial |
| Android preview APK | Advisory | EAS local/preview profile | Not blocking until stable |
| iOS build | Advisory | Document only | Requires macOS + certs |

## When blocking

Promote to blocking only when:

1. `run-mobile-checks.sh` is reliable on VM and CI runners.
2. Preview backend env is pinned and documented.
3. At least one app has green lint + test on every PR.

## Fix failures

```bash
cd apps/mobile && pnpm install
cd apps/mobile/citizen-app && pnpm run lint  # if defined
bash scripts/test/run-mobile-checks.sh
```
