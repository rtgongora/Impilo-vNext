# Runtime Image Strategy Reclassification Plan

> **Doctrine:** Dockerfile is not the doctrine. Repeatable **runtime image strategy** is the doctrine.

## Previous full image build (legacy pipeline)

| Metric | Value |
|--------|-------|
| Duration | ~2h39m |
| Image builds passed | ~75 |
| Image builds failed | ~11 |
| Missing Dockerfile findings | ~23 |
| Exit | 127 (summary script bug after last MISSING) |

Legacy pipeline treated missing Dockerfile as failure.

## Why missing Dockerfile is not always a failure

- UI workspaces (`ui/*-web`, `ehr`, `portal`, etc.) are often **bundled** into `one-ui-shell` — not independent K8s deployments.
- **Internal packages** (`shared-core`, `shared-ui`) are libraries, not runtime workloads.
- **Mobile apps** ship as Expo artifacts, not container images.
- **Generated clients** and **external dependencies** are contract-only.
- **Infrastructure** (Postgres, Redis, Kafka, Keycloak, OPA, Envoy) should use **official upstream images or Helm charts**, not repo Dockerfiles.

## Canonical image strategies

| Strategy | Meaning |
|----------|---------|
| `dockerfile` | Dedicated Dockerfile + `docker build` |
| `shared-dockerfile-template` | Pre-built JAR + `scripts/build/templates/impilo-jre-runtime.Dockerfile` |
| `jib` | Maven Jib (preferred over Maven-in-Alpine Dockerfiles) |
| `buildpacks` | Optional `pack build` for standalone UI (usually skipped) |
| `official-upstream-image` | Pull upstream image (no local build) |
| `official-helm-chart` | Deploy via Helm chart reference |
| `not-required-*` | Non-runtime component — skip image build |
| `unknown-needs-review` | Advisory until classified |
| `missing-required-image-strategy` | **Blocking** for required full-boot services |

## Blocking vs advisory

**Blocking:** required runtime service lacks valid strategy; strategy build fails; required official image/chart undefined.

**Advisory:** internal package, mobile, generated client, doctrine-only, bundled UI workspace, optional service with valid skip reason.

## Current classification snapshot

**Total components:** 160

- **shared-dockerfile-template**: 82
- **jib**: 21
- **not-required-internal-package**: 17
- **buildpacks**: 15
- **not-required-generated-client**: 9
- **official-helm-chart**: 8
- **not-required-doctrine-only-component**: 5
- **not-required-mobile-artifact**: 2
- **dockerfile**: 1

**Runtime image required:** 22

**Missing required strategy:** 0

Regenerate: `node scripts/full-boot/generate-full-boot-artifacts.mjs`
