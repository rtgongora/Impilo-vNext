# Deploy Optimization Program

Tracks the move from the destructive, hours-long full-boot to a fast, non-destructive,
provenance-stamped deploy. Born from the 2026-07 incident where a fullboot wiped the
public site's TLS/edge and Keycloak client secrets, and a manual `--only` build shipped
a stale jar.

## Done (landed on `claude/staging-ux-orchestration-remediation-Yypyl`)

### #1 — In-place deploy by default (`scripts/operator/fullboot.sh`)
The deploy already ran `helm upgrade --install`; the unconditional
`kubectl delete namespace` before it was the only forced teardown. Now opt-in:
- **Default**: in-place — helm rolls only changed Deployments; everything else (pods,
  TLS secret, IngressRoutes, acme svc) stays up. No estate-wide downtime.
- **`FULL_BOOT_CLEAN_REBUILD=1`**: destructive clean-room wipe + redeploy from zero.

### #4 — Incremental builds (`scripts/build/changed-services-since.sh`)
Prints changed service ids since the last certified commit, or `ALL` when a shared
input changed. Drive the build:
```sh
ids=$(scripts/build/changed-services-since.sh)
[ "$ids" = ALL ] && bash scripts/build/build-full-vnext-images.sh --full-estate \
  || [ -n "$ids" ] && bash scripts/build/build-full-vnext-images.sh $(printf ' --only %s' $ids)
```

### #6 — Image provenance (parent pom + both build scripts)
`git-commit-id-maven-plugin` stamps `BOOT-INF/classes/git.properties` into every jar;
both image build paths set OCI `org.opencontainers.image.revision=<commit>`. Freshness
is now a read, not jar archaeology:
```sh
docker inspect <img> --format '{{.Config.Labels}}' | grep revision   # image commit
unzip -p <jar> BOOT-INF/classes/git.properties                        # jar commit
```

### #7 — Stale-jar guard (both build strategies)
Before Dockerizing, compare the jar's stamped commit to the build target; warn, or fail
under `IMPILO_STRICT_JAR_FRESHNESS=1`. The jar-runtime path also keeps its mtime guard.

### Edge / identity self-heal (deploy tail)
`scripts/tls/restore-public-edge.sh` (IngressRoutes + acme svc/endpoints) and
`scripts/keycloak/reconcile-client-secrets.sh` (confidential client secrets) run after
rollout. Idempotent no-ops in-place; full restore after a clean rebuild. The one
root-only step is printed: `sudo /usr/local/bin/sync-mohcc-gov-tls.sh`.

## Remaining (need a real deploy/CI cycle to land safely — do NOT commit untested)

### #2 — Edge in a stable namespace  *(priority dropped: #1 already preserves the edge on routine deploys; this only matters for `FULL_BOOT_CLEAN_REBUILD=1`)*
Move `impilo-mohcc-gov-zw-tls`, the `.gov.zw` IngressRoutes, `acme-host-nginx`, and
`public-website` into a never-wiped `impilo-edge` namespace; route cross-namespace to
`one-ui-shell`/`experience-bff` (Traefik IngressRoute can target a `Service` in another
namespace via an `ExternalName` shim or a `TraefikService`). Validate: `FULL_BOOT_CLEAN_REBUILD=1`
deploy must leave the site up throughout.

### #3 — Build+push images in CI (`.github/workflows/`)
Add an image build+push job keyed off `changed-services-since.sh` on merge to the
deploy branch; push commit-tagged images to the registry. The VM deploy then only runs
`helm upgrade` (minutes), no building on the deploy host. Blocked on: registry
reachability from CI + credentials. Draft, then dry-run on a throwaway branch.

### #5 — Blue/green for clean rebuilds
When `FULL_BOOT_CLEAN_REBUILD` is truly needed, deploy into `impilo-full-preview-green`,
smoke it, then flip the edge IngressRoutes (in the #2 stable namespace) from blue→green.
Near-zero downtime + instant rollback. Depends on #2.

### Website → CI + branch reconciliation
`website-recovery/impilo-website-recovered` is a manual, digest-pinned deploy on a
diverged branch (`adaptive-layout-remediation` vs `main`, split Jan 2025). Fold its
`npm build → docker build (Dockerfile now committed) → push → repin` into CI, and
resolve the branch divergence, so new website content can't silently freeze again.
See [[public-tls-ingress-architecture]] (WEBSITE DEPLOY DRIFT).
