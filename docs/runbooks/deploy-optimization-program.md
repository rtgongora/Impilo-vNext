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

### #8 — Blocking pre-deploy backup + complete PV retention

Every existing preview estate now creates and proves a fresh `pg_dumpall` backup
before Helm changes anything. Failure to create or complete the backup blocks the
deploy. The evidence is written under `reports/full-boot/predeploy-evidence/`.

`retain-data-volumes.sh` covers every Kubernetes data PVC, including the later-added
`redroid-data`, and `verify-persistence.sh` validates it when present. Routine deploy
remains in-place; no namespace deletion or Helm uninstall is permitted as a rollback
shortcut.

## Remaining (need a real deploy/CI cycle to land safely — do NOT commit untested)

## 2026-07-24 authorized fullboot observations

These are measured opportunities from the first non-destructive, digest-pinned full
estate cycle. Keep them as work items until each change has its own regression proof.

### Avoid duplicate UI production builds

The local quality pipeline runs `next build`, then the image build runs the same
production compilation again inside Docker. For the 693-route shell, the first build
spent several minutes compiling, prerendering, and collecting traces while the image
builder could not safely share the same output directory.

Opportunity: make the pipeline produce a content-addressed standalone UI artifact
(`source commit + lockfile hash + strict UI bundle hash`) and let the Docker image stage
copy that artifact. The image build must verify the artifact metadata before reuse and
fall back to a clean build on any mismatch.

### Generate the contract matrix once per workspace state

`run-api-contract-checks.sh` computes the contract implementation matrix, then the
advisory implementation check computes it again. Each scan is CPU-bound across the full
repository and took minutes in this cycle.

Opportunity: generate the matrix once to a temporary, commit-keyed artifact and pass it
to both checks. Invalidate it when controller sources, OpenAPI files, the service
registry, or completeness scripts change.

### Make changed-service detection independent of unrelated dirty files

`changed-services-since.sh` intentionally includes working-tree changes. On this VM an
unrelated dirty `services/oros-service/id_file` therefore added `oros-service` to a
commit-to-commit rebuild calculation even though the deployed delta only changed the
shell.

Opportunity: add an explicit `--committed-only <base> <head>` mode for release builds.
Keep the current dirty-aware mode as the default for developer builds. The release mode
must fail if a changed tracked runtime source is outside the selected commit range
rather than silently incorporating it.

### Make detached SSH jobs detach completely

The current `nohup ... &` launch returned control on the VM, but the initiating SSH
client remained open until its local timeout. The job itself continued correctly, yet
the timeout looked like a failed launch and required a second status check.

Opportunity: provide a checked-in job launcher that closes inherited descriptors,
writes PID/log/exit files atomically, and returns only after proving the child is alive.
Expose a matching status command so operators never infer job state from an SSH exit.

### Preserve unchanged image digests during incremental releases

Only `one-ui-shell` runtime source changed after the full-estate image batch. Rebuilding
all application images solely to stamp the new repository commit would waste hours and
disk. Runtime truth already enforces target/registry/pod digest alignment and reports
OCI source revision separately.

Opportunity: publish a release manifest containing each service digest plus its own
source commit and input hash. Permit mixed source commits only when the changed-service
classifier proves the service inputs are unchanged. This makes incremental provenance
explicit instead of relying on a global release commit.

### Separate discovery output from the developer working tree

Fullboot discovery and product-truth checks rewrite many tracked generated reports.
That obscures the small implementation delta and can contaminate later changed-service
classification.

Opportunity: default CI/deploy discovery output to an ignored, run-scoped directory,
then promote canonical generated documents in a distinct reviewed commit. Gates that
test drift should compare generated output without rewriting tracked files in place.

### Make browser auth outcomes deterministic

The persistence suite waited for either the enterprise shell or login, discarded which
one won, and then performed a second instantaneous login check. Under concurrent build
load this produced one false failure: the captured page was visibly the login screen,
while the follow-up check raced a navigation transition. Eighteen other journeys passed.

Opportunity: retain the result of every ready-vs-auth race and make the branch decision
from that result. The corrected suite passed all 19 journeys in an isolated rerun.

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
