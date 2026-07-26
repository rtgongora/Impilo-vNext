# experience-bff test non-determinism — diagnosis and fix

**Measured 2026-07-26.** Companion to
[`experience-bff-empty-200-honesty-register.md`](experience-bff-empty-200-honesty-register.md).

Three consecutive identical runs of `cd services && mvn -pl experience-bff -am test -o` on
`8301c2d2e` gave three different results:

| Run | Result |
|---|---|
| 1 | `Tests run: 1205, Failures: 0, Errors: 20, Skipped: 2` |
| 2 | `Tests run: 1205, Failures: 4, Errors: 0, Skipped: 4` |
| 3 | `Tests run: 1205, Failures: 0, Errors: 0` |

## The cause

**The integration tests read the host's loopback, not a fixture.**

`ServiceClientConfig.ServiceEndpoints` defaults ~85 downstream base URLs to
`http://localhost:<dev port>` — PCT on 8088, OROS on 8089, inventory-service on 8098,
tshepo-audit on 8183, and so on down the port map. Nothing in the `test` profile overrode them,
and `ServiceClientConfig.testServiceEndpoints()` — the helper 118 unit-test files build their
clients from — passed all nulls, which let the record's compact constructor apply exactly those
same local-development defaults.

So a test that asserts "the downstream is unreachable" was not asserting anything about the code.
It was asserting that nothing happened to be listening on that port on that machine at that
moment. On a developer box that also runs the preview estate, that is not a stable fact: a service
started by hand, a `kubectl port-forward` that comes and goes, another project's server. At the
time of writing, this host has an unrelated nginx answering on **8089**, which is OROS's port, and
live `kubectl port-forward` processes holding 8153, 18160 and 20000.

### The merge is exonerated, but not uninvolved

The defect is pre-existing: the base-URL defaults and the `testServiceEndpoints()` helper are
byte-identical either side of `8301c2d2e`, and the merge did not touch the Testcontainers/Docker
harness at all. It changed assertions only.

But the sweep is what made the defect *visible*, and the mechanism is worth stating precisely.
The old assertions — "200 with a `data` array" — were satisfied by the BFF's own fabricated empty
payload **whatever the downstream did**, so they were accidentally insensitive to the host. The
honest assertions — "502, because the downstream could not be reached" — are only true if the
downstream really is unreachable. The sweep did not introduce the flakiness; it removed the
fabrication that had been masking it.

## Reproduction

Load and ordering are not the variable. Twenty-three clean full-suite runs were recorded first —
four on `8301c2d2e`, four on `10e99389c`, and fifteen more across five worktrees running
concurrently — plus twenty-one targeted runs of the six Spring integration classes at 5×
concurrency. All clean. Surefire runs single-fork, no parallelism, and the BFF has no database.

The variable is the loopback. Binding a stub that answers `200 {"data": []}` on
`127.0.0.1:8098` and re-running `GoldenPathIntegrationTest` on `8301c2d2e`, with no code change:

```
[ERROR] Tests run: 13, Failures: 1, Errors: 0, Skipped: 0
[ERROR]   GoldenPathIntegrationTest.inventoryItems:368 Status expected:<502> but was:<200>
```

Binding eleven more of the ports the suite reaches for (8082, 8084, 8088, 8096, 8098, 8100, 8108,
8122, 8176, 8183, 8292) reproduces run 2 of the original evidence exactly — same totals, same
skip count:

```
[ERROR] Tests run: 1205, Failures: 4, Errors: 0, Skipped: 4
[ERROR]   GoldenPathIntegrationTest.inventoryItems:368 Status expected:<502> but was:<200>
[ERROR]   GoldenPathIntegrationTest.pathC_listPatients:183 Status expected:<503> but was:<200>
[ERROR]   GoldenPathIntegrationTest.pathD_auditLog:257 Status expected:<502> but was:<200>
[ERROR]   StructuredHistoryApiIntegrationTest.structuredHistoryGoldenPatient:66 Status expected:<502> but was:<200>
```

Occupying all 94 dev ports named in `application.yml` adds two more, one of them outside the
Spring integration classes entirely:

```
[ERROR]   RbacIntegrationTest.bedWardsListPassesRbacAndFailsCleanWhenInpatientUnavailable:172 Status expected:<502> but was:<200>
[ERROR]   GuidanceControllerTest.askGuidanceReturnsBadGatewayWhenGuidanceUnavailable:47 expected: <502> but was: <200>
```

## The fix

Three commits, no retries, nothing disabled, no assertion loosened.

1. **`ClosedLoopbackDownstreamsEnvironmentPostProcessor`** (test scope, registered via
   `src/test/resources/META-INF/spring.factories`). Repoints every `impilo.*` URL property that
   resolves to loopback at `http://127.0.0.1:1` — privileged, never bound, connection-refused
   immediately. It matches by name pattern rather than an enumerated list, so a base URL added for
   a new sovereign service is neutralised automatically instead of quietly reopening the hole. A
   class that needs a real stub still wins: `@DynamicPropertySource` is applied later and also
   inserts first, so `ExperienceBffSovereignWireMockSupport` and
   `ExperienceBffReportingWireMockSupport` keep redirecting their endpoints to WireMock.

2. **`testServiceEndpoints()` now returns `http://127.0.0.1:1` in every slot** instead of nulls,
   closing the unit-test half — 118 files' worth. `TusoServiceClientUpdateTest` took its expected
   URI from the old host and now derives it from the constant.

3. **`GuidanceControllerTest.FailingGuidanceClient` stubs the overload the controller calls.**
   See below.

**Proof:** five consecutive full-suite runs with all 94 dev ports occupied by stubs — the
condition that produced six failures before the fix — `Tests run: 1211, Failures: 0, Errors: 0,
Skipped: 4`, exit 0. (1211 = 1205 at `8301c2d2e`, +2 from `FacilityResourceMappingTest` landed
since, +4 for the new post-processor's own tests.)

## Green for a reason that is not the code being correct

Found while running this down; the first is fixed, the rest are reported, not changed.

- **`GuidanceControllerTest.askGuidanceReturnsBadGatewayWhenGuidanceUnavailable`** — *fixed.*
  `FailingGuidanceClient` overrode `ask(String, boolean)`; `GuidanceController` calls
  `ask(String, boolean, Map)`. Nothing was stubbed. The test passed because the real HTTP call to
  guidance-service's dev port was refused. The same shape may exist wherever one of the 118 files
  subclasses a client to stub it — a missed overload is now a fast connection-refused rather than
  a live call, but it is still not the stub the test author intended.

- **The Docker probe silently deleted 44 integration tests** — *fixed.* See below.

- **`*IT.java` classes never run.** `GoldenContractIT`, `ImagingExperienceWireMockIT` and
  `MobileProviderTier2ResponseShapeIT` are `@SpringBootTest` classes that surefire does not pick
  up and no failsafe execution runs — the known repo-wide dead-`*IT*` pattern. Not changed here:
  those three have been rewired alongside the six live classes so they do not rot further, but
  making them execute is a separate decision about the gate's scope.

# Run 1 — `Errors: 20, Skipped: 2`

The first diagnosis pass inferred this from arithmetic and **got it wrong**. The inference was
that `ExperienceV11ComplianceTest` (7) + `GoldenPathIntegrationTest` (13) failing at class level
gives 20 errors, and that the nested `@Disabled` `ExperienceV11ComplianceTest$OutboxFields` would
report its two skips as errors. Simulating the failure showed otherwise: that combination yields
`Errors: 18, Skipped: 4`. A nested `@Disabled` class is never entered when its parent's context
fails, so its skips survive.

Forcing a `redis.start()` failure in all six classes and reading the surefire XML gives the real
per-class accounting:

| Class | Tests | On context-load failure |
|---|---|---|
| `ExperienceBffIntegrationTest` | 10 | **10 errors, 0 skips** — its two `@Disabled` *methods* become errors |
| `GoldenPathIntegrationTest` | 13 | 13 errors |
| `RbacIntegrationTest` | 8 | 8 errors |
| `ExperienceV11ComplianceTest` | 7 | 5 errors, 2 skips preserved (nested `@Disabled` class not entered) |
| `StaffingApiIntegrationTest` | 4 | 4 errors |
| `StructuredHistoryApiIntegrationTest` | 2 | 2 errors |

Only `ExperienceBffIntegrationTest` can move the skip count from 4 to 2, so it must be in the
set; the remaining 10 errors have exactly one decomposition over `{5, 13, 8, 4, 2}` — `8 + 2`.

> **Run 1 was `ExperienceBffIntegrationTest` (10) + `RbacIntegrationTest` (8) +
> `StructuredHistoryApiIntegrationTest` (2) = 20 errors, `Skipped: 4 → 2`, total unchanged at 1205.**

Surefire's run order is `GoldenPath → Staffing → ExperienceBffIntegrationTest →
StructuredHistory → Rbac`. The three implicated classes are **consecutive and the last three** —
consistent with a Docker daemon that degraded partway through the run and stayed degraded, which
is exactly what six per-class container starts expose and one shared container does not.

## Second wave of fixes

**One Redis container per JVM, one database per class.** The six classes no longer each start and
stop their own container. A single container starts once and is reaped by Ryuk at JVM exit — one
start to fail instead of six, and a failure that is total and obvious rather than partial and
ordering-dependent. Stopping in `@AfterAll` is gone too: it killed the container while the Spring
context pointing at it stayed in the TestContext cache, and with random host-port mapping a later
container could be handed the port a stopped one had just released.

Isolation is not sacrificed to get this. Sharing one server would leak idempotency keys,
rate-limiter counters and OTPs between classes — trading a startup hazard for an ordering hazard —
so each class gets its own Redis logical database. Verified against an external Redis: after a run,
keys sit in `db0`, `db1`, `db2`, `db3` and `db5`, where every class previously shared `db0`.

**The gate fails loudly when the environment is missing.** `DockerOrExternalPostgresCondition`
became `IntegrationEnvironmentCondition` (there is no Postgres in this service, and the behaviour
is no longer "skip if unsupported"). With no Docker and no external Redis it now throws with a
message naming all three ways out, instead of disabling 44 tests and exiting 0.

Skipping is still available but must be asked for: `EXPERIENCE_BFF_SKIP_INTEGRATION=true`. That
check runs **first**, so it is honoured on a machine that does have Docker — checked last, as it
was first written, it would only have applied once the environment was already broken, which is
not an opt-out.

Both behaviours were proven by forcing them, not by reading the code:

| Condition | Before | After |
|---|---|---|
| No Docker, no external Redis, no opt-out | 44 skipped, **exit 0** | 6 errors, **exit 1**, message names the fix |
| `EXPERIENCE_BFF_SKIP_INTEGRATION=true`, Docker present | ignored, tests ran | 40 skipped, no container started, exit 0 |

CI (`ci.yml`, `backend-test`) runs on `ubuntu-latest` with Docker present and sets neither the
external-Redis nor the skip variable, so it keeps running all 44 through Testcontainers.

**Final proof:** five consecutive full-suite runs with all 94 dev ports occupied by stubs —
`Tests run: 1216, Failures: 0, Errors: 0, Skipped: 4`, exit 0, **one** Redis container per run
(six before).

Test-count accounting, so the total is not a mystery: 1205 at `8301c2d2e`, +2 from
`FacilityResourceMappingTest` landed since, +4 for the closed-loopback post-processor's tests,
+1 for the per-class Redis database allocation, +4 net for the condition's tests (7 replacing 3).
`Skipped: 4` throughout is the by-design set — two `@Disabled` methods in
`ExperienceBffIntegrationTest`, two in the `@Disabled` nested `ExperienceV11ComplianceTest$OutboxFields`.
