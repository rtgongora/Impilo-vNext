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

- **The Docker probe silently deletes 44 integration tests.**
  `DockerOrExternalPostgresCondition` disables `GoldenPathIntegrationTest`,
  `ExperienceV11ComplianceTest`, `RbacIntegrationTest`, `StaffingApiIntegrationTest`,
  `StructuredHistoryApiIntegrationTest` and `ExperienceBffIntegrationTest` when
  `DockerClientFactory` and a 25-second `docker info` subprocess both fail. The build then reports
  success. A transient daemon hiccup — or a machine without Docker — removes every integration
  test from the only gate on this service and says nothing. This should fail loudly with an
  explicit opt-out (`EXPERIENCE_BFF_SKIP_INTEGRATION=1`) rather than skip by default, but that is
  a policy change to the gate and is left for a decision.

- **Six Redis containers per run, each stopped while its Spring context stays alive.** Each of the
  six classes starts its own Testcontainers Redis in `@DynamicPropertySource` and stops it in
  `@AfterAll`, while the Spring context that uses it stays in the TestContext cache for the rest
  of the JVM. Testcontainers assigns random host ports, so a container started later can be given
  the port a stopped one just released. This is six container lifecycles where one would do, and
  each is a fresh chance to time out under docker-daemon or disk load. **Not changed**: collapsing
  to one shared container would trade this for cross-class state leakage through Redis unless the
  store is flushed between classes, and that is a real isolation change to make deliberately
  rather than on the way past.

- **`*IT.java` classes never run.** `GoldenContractIT`, `ImagingExperienceWireMockIT` and
  `MobileProviderTier2ResponseShapeIT` are `@SpringBootTest` classes that surefire does not pick
  up and no failsafe execution runs — the known repo-wide dead-`*IT*` pattern.

## What is *not* explained

Run 1 of the original evidence — `Errors: 20, Skipped: 2` — was **not** reproduced. The arithmetic
identifies it confidently: the stable skip count is 4 (two `@Disabled` methods in
`ExperienceBffIntegrationTest`, two in the `@Disabled` nested `ExperienceV11ComplianceTest$OutboxFields`),
and `ExperienceV11ComplianceTest` has 7 tests to `GoldenPathIntegrationTest`'s 13. A class-level
failure in both — which reports the nested class's two skips as errors instead — gives exactly
20 errors and exactly `Skipped: 2`, with the total unchanged at 1205.

A class-level failure of that shape is a context-load failure, and the only thing in these classes
that can fail at context-load time is `redis.start()`. That points at the Testcontainers churn
described above, not at the loopback — so **the fix landed here does not address run 1**. It
remains a live risk until the container lifecycle is consolidated.
