# Full Boot Runtime Completeness Report

**Status:** `FULL_BOOT_FAIL`

> Checks **runtime image strategy**, not Dockerfile presence alone.

| Metric | Value |
|--------|-------|
| Total discovered | 141 |
| Required full boot | 22 |
| Required with valid image strategy | 22 |
| Runtime image required | 22 |
| Missing required strategy | 0 |
| Failing required image builds | 5 |
| Unknown needs review | 0 |
| Not-required classified | 24 |
| Official image/chart defined | 8 |
| Image pass / fail | 17 / 5 |
| Blocking failures | 5 |

## Failing required image builds
- `tshepo-keys-service`
- `one-ui-shell`
- `butano-service`
- `tshepo-consent-service`
- `tshepo-audit-service`

## Image strategies
- **buildpacks**: 23
- **dockerfile**: 6
- **jib**: 5
- **not-required-doctrine-only-component**: 1
- **not-required-generated-client**: 9
- **not-required-internal-package**: 12
- **not-required-mobile-artifact**: 2
- **official-helm-chart**: 8
- **shared-dockerfile-template**: 75
