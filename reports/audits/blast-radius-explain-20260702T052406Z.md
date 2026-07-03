# Blast Radius Explanation

- **Generated:** 2026-07-02T05:24:06Z
- **Base ref:** `HEAD~1`
- **HEAD:** `6f51c708a`
- **Changed files:** 24

## Decision

| Question | Answer |
| -------- | ------ |
| Change class | **G** (G, I) |
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

- Change class G+I requires full boot

## Rationale

- Registry or classification metadata changed
- Helm/deploy/infrastructure configuration changed

## Changed files (sample)

- `config/full-boot-service-classification.yml`
- `deploy/helm/impilo-vnext/values-full-preview-runtime.generated.yaml`
- `docs/architecture/FULL_VNEXT_SERVICE_CATALOG.md`
- `docs/architecture/VNEXT_API_CONTRACT_CATALOG.md`
- `docs/architecture/VNEXT_DOCTRINE_COMPLIANCE_MATRIX.md`
- `docs/architecture/VNEXT_DOCTRINE_INDEX.md`
- `docs/architecture/VNEXT_SEVEN_PLANE_ARCHITECTURE.md`
- `docs/environment/FULL_BOOT_INFRASTRUCTURE_DEPENDENCY_MATRIX.md`
- `docs/environment/FULL_BUILD_MATRIX.md`
- `docs/environment/FULL_CONTAINERIZATION_MATRIX.md`
- `docs/environment/FULL_HELM_DEPLOYABILITY_MATRIX.md`
- `docs/environment/IMAGE_STRATEGY_RECLASSIFICATION_PLAN.md`
- `docs/environment/JAVA_SERVICE_IMAGE_STRATEGY_REVIEW.md`
- `docs/frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md`
- `docs/frontend/FRONTEND_IMPLEMENTATION_STATUS.md`
- `reports/full-boot/build-targets.json`
- `reports/full-boot/build-targets.tsv`
- `reports/full-boot/discovery-summary.json`
- `reports/full-boot/full-boot-runtime-report.json`
- `reports/full-boot/full-boot-runtime-report.md`
- `reports/full-boot/image-strategy-targets.json`
- `reports/full-boot/non-runtime-components.json`
- `reports/full-boot/registry-inventory-contract.json`
- `reports/full-boot/repo-deployable-candidate-files.txt`

## Recommended command

```bash
bash scripts/preview/full-boot.sh
# Type: AUTHORIZE FULL BOOT PREVIEW DEPLOY
```

JSON: `/opt/impilo/repos/Impilo-vNext/reports/audits/blast-radius-6f51c708a.json`
