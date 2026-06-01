# Runtime Image Strategy Doctrine (Full Boot)

> **Dockerfile is not the doctrine. Repeatable runtime image strategy is the doctrine.**

No required runtime service is full-boot ready unless it has a **valid image strategy** and that strategy has been **verified** (build, pull, or chart reference).

Regenerate classification: `node scripts/full-boot/generate-full-boot-artifacts.mjs`

## Allowed strategies

| Strategy | When to use |
|----------|-------------|
| `dockerfile` | Dedicated Dockerfile copying **pre-built** artifacts (JAR/dist) |
| `shared-dockerfile-template` | Java: reactor build + `scripts/build/build-runtime-image-from-jar.sh` |
| `jib` | Maven Jib when configured (preferred over Maven-in-Alpine Dockerfiles) |
| `buildpacks` | Optional Node UI (`pack build`) — usually **not** blocking |
| `official-upstream-image` | Infra pull (Postgres, Redis, Kafka, Keycloak, MinIO, Envoy, HAPI, OPA) |
| `official-helm-chart` | Infra via Helm subchart / `impilo-vnext` values |
| `not-required-internal-package` | Libraries (`libs/*`, shared modules) |
| `not-required-mobile-artifact` | Expo/mobile — not K8s images |
| `not-required-generated-client` | Contract-only / external registry entries |
| `not-required-doctrine-only-component` | Future/deprecated placeholders |
| `not-required-static-docs` | Docs/config only |
| `not-required-test-fixture` | Test fixtures |
| `unknown-needs-review` | Advisory until classified |
| `missing-required-image-strategy` | **Blocking** for required full-boot services |

## Blocking vs advisory

**Blocking:**

- Required runtime service lacks valid image strategy
- Required image build fails
- Required official image/chart reference missing

**Not blocking:**

- Internal package, mobile artifact, generated client, doctrine-only component
- UI workspace bundled into `one-ui-shell` without its own Dockerfile
- Official upstream image/chart (no local build)

## Commands

```bash
bash scripts/build/discover-build-targets.sh
bash scripts/build/build-full-vnext-images.sh
bash scripts/guard/check-full-boot-runtime-completeness.sh
```

## Java guidance

- Run `bash scripts/build/build-full-vnext.sh` on the VM, then image via shared template or thin Dockerfile.
- **Avoid** `mvn package` inside Alpine runtime Dockerfiles unless explicitly justified.

## Infrastructure guidance

Use upstream images and Helm charts from `deploy/helm/impilo-vnext` — do not fork Postgres/Redis/Keycloak unless required.

See also: [`IMAGE_STRATEGY_RECLASSIFICATION_PLAN.md`](IMAGE_STRATEGY_RECLASSIFICATION_PLAN.md), [`MISSING_DOCKERFILE_RECLASSIFICATION.md`](MISSING_DOCKERFILE_RECLASSIFICATION.md).
