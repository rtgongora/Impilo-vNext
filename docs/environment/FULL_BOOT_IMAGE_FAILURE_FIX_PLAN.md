# Full Boot Image Failure Fix Plan

## Current status (before this fix batch)

- Commit baseline: `4f59650c`
- Total classified components: **141**
- Runtime image required: **22**
- Missing required image strategy: **0**
- Unknown needs review: **0**
- Required image builds: **17 pass / 5 fail**

## Failing required services and hypotheses

| Service | Current strategy (baseline) | Root-cause hypothesis | Target strategy | Test command | Success condition |
|---|---|---|---|---|---|
| `butano-service` | `dockerfile` | Dockerfile runs `mvn` in `eclipse-temurin:21-jdk-alpine`; image lacks Maven (`mvn: not found`). | `jib`-candidate path implemented as **pre-built JAR + shared JRE runtime template**. | `bash scripts/build/build-full-vnext-images.sh --only butano-service` | Image tags `impilo/butano-service:preview` and `:${TAG}` build successfully without in-container Maven. |
| `one-ui-shell` | `dockerfile` | Full-boot builder used wrong context (`ui/one-ui-shell`) while Dockerfile expects repo-root paths (`COPY ui/...`, `COPY contracts`, `COPY registry-templates`). | Keep `dockerfile`, but force repo-root build context. | `bash scripts/build/build-full-vnext-images.sh --only one-ui-shell` | Docker build resolves all COPY paths and completes Next.js build. |
| `tshepo-audit-service` | `dockerfile` | Multi-stage Dockerfile runs `mvn` inside Alpine builder (`mvn: not found`). | `jib`-candidate path via **pre-built JAR + shared template**. | `bash scripts/build/build-full-vnext-images.sh --only tshepo-audit-service` | Image builds from `services/tshepo-audit-service/target/*.jar` with java runtime entrypoint only. |
| `tshepo-consent-service` | `dockerfile` | Dockerfile runs `mvn -B -DskipTests package` in Alpine builder (`mvn: not found`). | `jib`-candidate path via **pre-built JAR + shared template**. | `bash scripts/build/build-full-vnext-images.sh --only tshepo-consent-service` | Image builds from pre-built JAR, no in-container Maven step. |
| `tshepo-keys-service` | `dockerfile` | Dockerfile runs `mvn -B package -DskipTests` in Alpine builder (`mvn: not found`). | `jib`-candidate path via **pre-built JAR + shared template**. | `bash scripts/build/build-full-vnext-images.sh --only tshepo-keys-service` | Image builds from pre-built JAR, no in-container Maven step. |

## Validation sequence

1. `bash scripts/build/build-full-vnext.sh`
2. Targeted blockers:
   - `bash scripts/build/build-full-vnext-images.sh --only tshepo-audit-service`
   - `bash scripts/build/build-full-vnext-images.sh --only tshepo-consent-service`
   - `bash scripts/build/build-full-vnext-images.sh --only tshepo-keys-service`
   - `bash scripts/build/build-full-vnext-images.sh --only one-ui-shell`
   - `bash scripts/build/build-full-vnext-images.sh --only butano-service`
3. Required-only pass check: `bash scripts/build/build-full-vnext-images.sh --required-only`
4. Full image strategy run: `bash scripts/build/build-full-vnext-images.sh`
5. Completeness gate: `bash scripts/guard/check-full-boot-runtime-completeness.sh`

## Expected success condition

- All 22 required runtime image targets build successfully.
- `missing_required_image_strategy_count = 0` remains unchanged.
- `required_fail = 0` in `reports/full-boot/full-image-build-summary.json`.
- No full-boot deploy is executed and `impilo-preview` remains untouched.

## Execution outcome (this batch)

- `bash scripts/build/build-full-vnext-images.sh --only tshepo-audit-service` → **pass**
- `bash scripts/build/build-full-vnext-images.sh --only tshepo-consent-service` → **pass**
- `bash scripts/build/build-full-vnext-images.sh --only tshepo-keys-service` → **pass**
- `bash scripts/build/build-full-vnext-images.sh --only one-ui-shell` → **pass**
- `bash scripts/build/build-full-vnext-images.sh --only butano-service` → **pass**
- `bash scripts/build/build-full-vnext-images.sh --required-only` → **22 pass / 0 fail**
- `bash scripts/build/build-full-vnext-images.sh` → **94 pass / 0 fail / 23 skip (not-required buildpacks)**
