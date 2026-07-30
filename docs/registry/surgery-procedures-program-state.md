# Surgery + Clinical Procedures — programme state

Last updated: 2026-07-30, at the close of the completion wave.

`iatg-surgery-procedures-leases.md` has cited "programme memory
(`surgery-procedures-program-state.md`)" since the wave index was first written, but the file had
never existed in the repository — the reference was dangling, and every wave that followed it
found nothing. This is that file. It is the short version; the lease is the long one.

## Where the programme stands

**Every named wave is closed.** Phase 0 (audit and baseline), Wave P-R and P-R2 (reachability),
Phase P (P0–P15, the clinical procedures pipeline), Phase S (S0–S3), the backlog-clearing batches
SB-1 through SB-6, and the completion wave that closed the gate run, SB-5 and the last three
demonstrations. Per-wave detail is in the lease §9–§29.

**All twenty demonstrations across both packs are closed or explicitly deferred with an owner.**
The two traceability documents (`docs/clinical/surgical-domain-pack/demonstrations-traceability.md`
and `docs/clinical/procedures-pipeline/demonstrations-traceability.md`) each carry a row per
demonstration with its mechanism and its proof.

**Nothing has ever been deployed or hit over real HTTP.** Wave P-R's definition of done is still
unmet. Everything above is verified by unit tests, route-shape tests against the real PDP
derivation, and rigs against real Postgres. No browser has loaded a surgery surface and no request
has crossed Envoy into surgery-service. Do not let "reachable" be read as "working" — the
distinction is the single most important thing in this document.

## Services and migration bands owned

| Service | Band claimed | Highest used |
|---|---|---|
| `surgery-service` | all | V012 |
| `inpatient-service` | V300+ | V305 |
| `tshepo-authz-service` | V300–V329 | V303 |
| `clinical-knowledge-platform-service` | V300 | V300 |
| `procedures-service` | all | see lease §3 |

Contracts: `contracts/openapi/surgery.openapi.yaml`, `contracts/openapi/procedures.openapi.yaml`.
UI: `ui/one-ui-shell/src/app/work/clinical/surgery` and `.../procedures`.
BFF: `SurgeryController` / `SurgeryServiceClient`, `ProceduresController` / `ProceduresServiceClient`.

## What the next agent needs to know before touching anything

**1. Run the ten theatre rigs before and after any `inpatient-service` change.** They are not
expensive — Docker plus `mvn package`, about twenty seconds of packaging on this VM — and the
belief that they required a packaged estate held them for five consecutive waves while theatre
intake was completely broken in the main branch. Run them serially: `theatre-elective` and
`theatre-persistence` both bind 28121.

```
mvn -f services/pom.xml -pl inpatient-service,madi-service,nhume-service,oros-service,rito-quality-safety-service -am package -DskipTests
for r in elective elective-completeness emergency persistence authz clinical-safety commodities alt queue-drainage recovery-reporting; do
  bash scripts/runtime-proof/theatre-$r-journeys.sh; done
```

Baseline: all green except `elective-completeness` at 14/16, whose two amber J-TE-8 board
assertions are the recorded baseline, not a regression.

**2. A green `inpatient-service` module suite says nothing about database constraints.**
`application-test.yml` sets `flyway.enabled: false` with `ddl-auto: create-drop`, so the tests
build their schema from the entities and are structurally blind to anything a migration adds. This
is how a `NOT NULL DEFAULT` column took down all theatre intake for five waves. Every DB-level
invariant needs a real-Postgres rig assertion.

**3. A `NOT NULL DEFAULT` column needs the same default on the entity field.** Hibernate names
every mapped column in its INSERT, so the database default never applies; an uninitialised field
sends an explicit null and violates the constraint it was supposed to satisfy. Pinned by
`ProcedureEpisodeColumnDefaultTest`.

**4. Never put a free-text or closed-vocabulary code in a route's final path segment.**
`AuthzInternalRequest.deriveResourceType` walks the path backward and returns the first segment
that is not blank, not `v1`/`api` and not a 36-character UUID — so the code itself becomes the
derived resource type, no policy row matches, and the route is permanently unreachable. Codes
travel as query parameters. This programme has now shipped this defect twice and caught it twice;
`SurgeryReachabilityRouteShapeTest` and `ProceduresRouteShapeTest` assert both the working shape
and the broken one for every route.

**5. Model authz negatives as correct-cadre-only ALLOW, never as a path-pinned DENY.** A
conditional DENY that fails its own pin is skipped, not enforced. And never end a `path_contains`
pin with a slash — `pathContainsSegment` requires the next character to be `/` or end-of-string, so
a trailing-slash pin can only match a path that ends exactly there.

**6. Do not run the registry generator.** `services-registry.yaml` has drifted well ahead of what
`scripts/registry/seed-registry.mjs` can reconstruct; a bare regeneration rewrote 894 lines for a
one-service addition and stripped curated fields from unrelated services. Hand-edit the YAML and
mirror into the override map. Lease §6 has the full rule.

**7. Work in an isolated worktree.** `/opt/impilo/repos/Impilo-vNext` carries other lanes'
uncommitted work — roughly sixty files at the time of writing. Never `git stash` there.

**8. Two rig-harness bugs are copied widely across `scripts/runtime-proof/`.** `kill "${SVC_PID:-0}"`
becomes `kill 0` before the service starts and signals the caller's whole process group, silently
killing a driver mid-loop. And `pg_isready -U impilo` asks over the UNIX socket that `initdb`'s
temporary server also answers on, so a `CREATE DATABASE` immediately after it hits "the database
system is shutting down". Fixed in `theatre-persistence` and `theatre-authz`; a rig that fails
oddly is more likely one of these than a real red.

## Open, with owners elsewhere

- **PCT holds two MDT systems of record** (`pct_mdt_decisions` V114, `pct_mdt_sessions` case items
  V051). Surgery references whichever applies via a source-tagged pair. Consolidation is a
  cross-lane decision.
- **`services-registry.yaml`'s status fields for `surgery-service` are stale** — `not-started` for
  authz/audit and API contract, `not-wired` for frontend, all three now false. The code-derived
  scan returns `wired`. Owner: whoever owns the registry generator.
- **FR17's offline surfaces.** Owner: `offline-sync-service` / `offline-edge-service` /
  `tshepo-offline-service`, not this programme.
- **Demonstration 10's active half** (a critical histology result actively reopening planning) and
  the cross-service legs of demonstrations 5/7/8, each named with its owner in the traceability
  documents.
- **Deployment and real-HTTP proof**, which is the next thing this programme needs and the only
  thing that can turn "reachable" into "working".
