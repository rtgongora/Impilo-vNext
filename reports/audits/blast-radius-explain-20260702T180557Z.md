# Blast Radius Explanation

- **Generated:** 2026-07-02T18:05:57Z
- **Base ref:** `HEAD~1`
- **HEAD:** `7e8d636f8`
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

- `ui/one-ui-shell/src/app/auth/context-chooser/page.tsx`
- `ui/one-ui-shell/src/lib/__tests__/identity-context.test.ts`
- `ui/one-ui-shell/src/lib/identity-context.ts`

## Recommended command

```bash
bash scripts/preview/targeted-deploy.sh --dry-run
bash scripts/preview/targeted-deploy.sh --execute
# Type: AUTHORIZE TARGETED PREVIEW DEPLOY
```

JSON: `/opt/impilo/repos/Impilo-vNext/reports/audits/blast-radius-7e8d636f8.json`
