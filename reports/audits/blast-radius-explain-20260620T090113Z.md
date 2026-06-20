# Blast Radius Explanation

- **Generated:** 2026-06-20T09:01:13Z
- **Base ref:** `fbaf3670`
- **HEAD:** `9cf1f14b`
- **Changed files:** 3

## Decision

| Question | Answer |
| -------- | ------ |
| Change class | **B** (B) |
| Full boot required? | **no** |
| Targeted deploy allowed? | **yes** |

## Services

- **Direct:** one-ui-shell
- **Expanded:** one-ui-shell
- **Images to build:** one-ui-shell

## Pipeline phases

`PIPELINE_ONLY=change-safety,frontend,parity-web,security,static`

Plus mandatory guardrails: `security`, `change-safety` (always).

## Changed files (sample)

- `scripts/preview/_preview-common.sh`
- `scripts/preview/_validation-timing.sh`
- `ui/one-ui-shell/src/app/layout.tsx`

## Recommended command

```bash
bash scripts/preview/targeted-deploy.sh --dry-run
bash scripts/preview/targeted-deploy.sh --execute
# Type: AUTHORIZE TARGETED PREVIEW DEPLOY
```

JSON: `/opt/impilo/repos/Impilo-vNext/reports/audits/blast-radius-9cf1f14b.json`
