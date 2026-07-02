# Handoff — TUSO Facility Configuration Console (parallel session)

**For:** a separate Opus/Cursor session dedicated to the TUSO Facility Configuration Console
(Service Points, Workspaces, Queues, Readiness). **Do not run this in the session that produced the
facility-absorption + TUSO→PCT materialisation slices** — that session is intentionally stopping
TUSO-depth work to avoid file conflicts.

## Start state

- **Branch:** `claude/web-session-anchor-nnnkf6` (latest tip at handoff: `167388cd`).
- **Product Truth base to rebase on:** `origin/claude/staging-ux-orchestration-remediation-Yypyl`
  (moves often — `git fetch` + `git rebase` before every push).
- Consider branching a new feature branch off the current tip for the console work, then integrating,
  to keep the two workstreams cleanly separable.

## Scope for this session

Facility setup overview · service points · workspaces · queue definitions · readiness checklist ·
downstream materialisation status · audit/configuration history (where supported) · BFF/admin routes ·
web admin configuration console · tests + product-truth gates.

## What already EXISTS (verified — reuse, do not rebuild)

**TUSO entities** (`services/tuso-service/.../persistence/entity`): `FacilityEntity` (has canonical
`facilityUuid` + numeric `id`), `ServicePointEntity` (`id` UUID, `facilityId` Long, `servicePointType`,
`queueId` String, `status`, `active`, `facilityUnitId`, metadata), `WorkspaceEntity`, `FacilityUnitEntity`,
`FacilityReadinessEntity` (connectivity/power/devices/EHR), `FacilityCapabilityEntity`,
`FacilityContactEntity`, `FacilityIdentifierEntity` + `FacilityIdentifierSystem` (external-id taxonomy),
`FacilityRegulatoryStatus` (lifecycle incl. `IMPORTED_PENDING_CONFIGURATION`).

**TUSO controllers/repos:** `ServicePointController`, `WorkspaceController`, `FacilityUnitController`,
`FacilitySetupController` (wizard: departments → service-points → queues → workflows → workforce →
oros-routing → khuluma-channels → fundo-readiness → go-live), `FacilityRegulatoryController`,
`FacilityModeController`, `FacilityController`. Repos: `ServicePointRepository`
(`findByFacilityIdAndActiveTrueOrderByCreatedAtAsc`, `countByFacilityIdAndActiveTrue`),
`WorkspaceRepository`, `FacilityUnitRepository`, `FacilityReadinessRepository`,
`FacilityRepository.findByFacilityUuid`.

**TUSO events/outbox:** `TusoOutboxPublisher` (scheduled Kafka poller). Emitted today: `tuso.facility.*`,
`SERVICE_POINT_CREATED/RETIRED`, `FACILITY_UNIT_CREATED`, `tuso.workspace.created`,
`tuso.workspace.updated`, `tuso.config.*`.

**BFF:** admin RBAC is automatic under `/internal/v1/admin/**` (`SecurityConfig`, roles
FACILITY_ADMIN/SYSTEM_ADMIN/DEVELOPER/SUPER_ADMIN). `TusoServiceClient` (RestTemplate → JsonNode,
`extractData` strips the `data` envelope), `PctServiceClient`.

**Web:** `/facility`, `/facility/[id]`, `/facility/[id]/setup` (wizard), `/facility/[id]/cockpit`,
`/facility/[id]/control-tower`, `/facility/[id]/regulators`, `/registry/facility-lifecycle`. Route
registry `ui/one-ui-shell/src/lib/routes.ts` (`EXPECTED_ROUTE_COUNT` currently **680** — bump by 1 per
new route + add a real `page.tsx`).

## Files the ABSORPTION/materialisation session OWNS — coordinate / avoid editing these

Prefer creating NEW files/routes. If you must edit these, coordinate (they belong to the other
workstream):

- **TUSO:** `FacilityMasterImportController`, `FacilityMasterImportService`, `FacilityProvenanceService`,
  `FacilityProvenanceController`, `FacilityQueueDefinitionController`,
  `FacilityImportRow*` / `FacilityImportRun*` (entities/repos/DTOs), `FacilityIdentifierSystem`,
  `FacilityRepository` (the `findByFacilityUuid` line), migrations `V011`–`V015`.
- **BFF:** `AdminFacilityImportController`, `AdminFacilityQueueController`, `TusoServiceClient`
  (import + `getFacilityImportProvenance` + `getQueueDefinitions*` methods), `PctServiceClient`
  (`getQueueMaterializationStatus`/`reconcileQueues`).
- **Web:** `src/app/admin/facility-imports/**`, `src/app/admin/queues/page.tsx`,
  `src/hooks/queries/useFacilityImports.ts`, `src/hooks/queries/useFacilityQueues.ts`,
  `src/components/admin/FacilityImportRowBrowser.tsx`, `src/app/facility/[id]/page.tsx` (the
  import-provenance section).
- **PCT:** `QueueMaterializationService`, `QueueReconciliationController`, `QueueEntity`,
  `QueueRepository`, `PctEventConsumer`, `TusoIntegration`, migration `V028`.

**Safe surfaces for the console:** the TUSO `ServicePoint`/`Workspace`/`FacilityUnit`/`Readiness`
controllers (extend read-models), a NEW BFF admin facility-config controller (e.g.
`/internal/v1/admin/facilities/{id}/configuration`), NEW web pages (e.g.
`/facility/[id]/configuration` or `/admin/facility-config/[id]`) + NEW hooks. Reuse the existing
`/admin/queues` materialisation surface rather than duplicating queue status.

## Conventions to follow

- **TUSO controllers:** `@RequestMapping("/v1/internal/...")`, `TrustContextHolder.require()`,
  `ApiResponse.ok(dto, ctx.correlationId().toString())`, `ResponseStatusException` for 400/404/409,
  tenant-scope by `ctx.tenantId()`.
- **BFF admin:** put routes under `/internal/v1/admin/**` (auto RBAC). Proxy via `TusoServiceClient`;
  wrap `Map.of("data", data, "meta", meta(requestId, correlationId))`; propagate downstream 4xx with
  `HttpStatusCodeException` (don't mask as 502). Tests = plain JUnit with a subclassed stub client
  (see `AdminFacilityImportControllerTest` / `AdminFacilityQueueControllerTest`).
- **Web:** route entries `zone:"admin", guard:"role", requiredRole:"ADMIN"` (or facility zone as
  appropriate); hooks `apiClient.get<ApiResponse<T>>(path)`; `FeatureMaturityBadge`
  (`@/components/FeatureMaturityBadge`); tests vitest `// @vitest-environment jsdom` + `renderHook` +
  `vi.mock("@/lib/api-client")`; **run from `ui/one-ui-shell`** with `../node_modules/.bin/vitest run …`.
- **Gates:** `node one-ui-shell/scripts/route-parity-check.mjs` (bump `EXPECTED_ROUTE_COUNT` per new
  route), `node one-ui-shell/scripts/no-stub-guard.mjs`,
  `REPO_PATH=$(pwd) bash scripts/guard/check-product-truth.sh` (discard generated `docs/audits/**`,
  `reports/product/**`, `docs/product/service-completion-blueprints.md` after running).
- **Build in sandbox:** Maven internal SNAPSHOT libs are installed in `~/.m2`; PCT/BFF may need an
  online first-fetch for some third-party deps. Real DB validation: `pg_ctlcluster 16 main start` +
  `scripts/operator/validate-facility-import-db.sh --manage-local-cluster` (pattern to mirror).

## Boundaries (binding)

1. **TUSO remains the source of truth** for facility configuration.
2. **PCT must not become the queue editor.**
3. **PCT only materialises** TUSO-owned service-point/workspace/queue configuration (already built:
   `QueueMaterializationService` + `consumeTusoWorkspaceUpdated` + `/v1/internal/queues/reconcile`).
4. **Seed/demo queues stay clearly labelled** non-production (`source=SEED_DEMO`; surfaced on
   `/admin/queues`).
5. **Do not claim live end-to-end proof** until the VM/preview service smoke is run
   (`scripts/operator/smoke-facility-import-service.sh`; runbook
   `docs/tuso/facility-import-service-smoke.md`). No running TUSO/PCT/BFF stack exists in the sandbox.
6. **Avoid unrelated MVP-completion work** unless required.
7. **Coordinate to avoid file conflicts** with the absorption/materialisation session (owned-files list
   above); rebase on Product Truth frequently.

## Suggested build order

1. TUSO read-models: facility configuration overview (setup state + counts of service points /
   workspaces / units / readiness) — likely a new `GET /v1/internal/facilities/{id}/configuration`
   composing existing repos; expose audit/config history where `FacilityConfigVersion`/audit tables
   support it.
2. BFF admin proxy: `/internal/v1/admin/facilities/{id}/configuration` (+ sub-resources as needed),
   RBAC, envelope, tests.
3. Web admin configuration console: overview + service points + workspaces + queue definitions
   (reuse `/admin/queues` materialisation surface) + readiness checklist + downstream materialisation
   status; hooks + tests; route-parity/no-stub/product-truth gates.
4. Honest reporting: unit-tested vs live-runtime-gated; nothing claimed against a live stack.
