# Blast Radius Explanation

- **Generated:** 2026-07-03T03:36:46Z
- **Base ref:** `HEAD~1`
- **HEAD:** `e1b3bf54c`
- **Changed files:** 1

## Decision

| Question | Answer |
| -------- | ------ |
| Change class | **G** (G) |
| Full boot required? | **yes** |
| Targeted deploy allowed? | **no** |

## Services

- **Direct:** none
- **Expanded:** none
- **Images to build:** none

## Pipeline phases

`PIPELINE_ONLY=change-safety,security`

Plus mandatory guardrails: `security`, `change-safety` (always).

## Refusal reasons (targeted deploy)

- Change class G requires full boot

## Rationale

- Helm/deploy/infrastructure configuration changed

## Changed files (sample)

- `deploy/helm/impilo-vnext/values-full-preview.yaml`

## Recommended command

```bash
bash scripts/preview/full-boot.sh
# Type: AUTHORIZE FULL BOOT PREVIEW DEPLOY
```

JSON: `/opt/impilo/repos/Impilo-vNext/reports/audits/blast-radius-e1b3bf54c.json`
