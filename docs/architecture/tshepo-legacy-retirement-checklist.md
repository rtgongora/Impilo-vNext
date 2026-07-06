# TSHEPO Legacy Retirement Checklist

Date: 2026-05-14  
Scope: `tshepo-service` (`:8079`) retirement gates.

## Machine-Readable Checklist

| Item Type | Identifier | Migration Target | Migration Status | Telemetry Status | Removal Gate | Owner/Status | Blocker |
|---|---|---|---|---|---|---|---|
| legacy-route | `POST /v1/authorize` | `tshepo-authz-service POST /v1/authorize` | in-progress (compatibility still active) | covered by `LegacyRouteTelemetryTest` + `/internal/v1/legacy/route-usage` | zero-usage window (30d) + caller cutover complete | trust-platform / active | compatibility callers still present in smoke/runtime scripts |
| legacy-route | `POST /v1/biometric-policy/evaluate` | `tshepo-authz-service` compatibility route (temporary) then canonical authz policy endpoint | in-progress | explicit route in OpenAPI + compatibility tests | remove after all consumers use canonical route and proxy receives zero hits | trust-platform / active | canonical endpoint ownership still converging |
| legacy-route | `POST /v1/patient-share-policy/evaluate` | `tshepo-authz-service` compatibility route (temporary) then canonical authz policy endpoint | in-progress | explicit route in OpenAPI + compatibility tests | remove after all consumers migrate and zero-hit telemetry window | trust-platform / active | compatibility path still needed for migration safety |
| legacy-route | `POST /v1/council-regulatory/evaluate` | `tshepo-authz-service` compatibility route (temporary) then canonical authz policy endpoint | in-progress | explicit route in OpenAPI + compatibility tests | remove after council-regulatory consumers migrate and zero-hit telemetry window | trust-platform / active | downstream migration not yet fully evidenced |
| legacy-route | `/internal/v1/federation/*` | architecture decision required (canonical trust owner) | blocked | inventoried by guard tests | ADR + replacement route ownership approved | trust-architecture / blocked | ownership decision pending |
| runtime-consumer | `vito-service` policy client | `TSHEPO_AUTHZ_BASE_URL` (`:8081`) | migrated (legacy fallback removed from runtime defaults) | config guarded by `TshepoPolicyBaseUrlDefaultGuardTest` | maintain no-legacy-default guard in CI | registry-platform / complete-default-cutover | compatibility routes still active for endpoint-shape migration |
| runtime-consumer | `varapi-service` policy client | `TSHEPO_AUTHZ_BASE_URL` (`:8081`) | migrated (legacy fallback removed from runtime defaults) | config guarded by `TshepoPolicyBaseUrlDefaultGuardTest` | maintain no-legacy-default guard in CI | registry-platform / complete-default-cutover | compatibility routes still active for endpoint-shape migration |
| runtime-consumer | `msika-flow-service` policy client | `TSHEPO_AUTHZ_BASE_URL` (`:8081`) | migrated (legacy fallback removed from runtime defaults) | config guarded by `TshepoPolicyBaseUrlDefaultGuardTest` | maintain no-legacy-default guard in CI | enterprise-platform / complete-default-cutover | compatibility routes still active for endpoint-shape migration |
| deployment-config | `infra/k8s/config/shared-config.yaml` (`TSHEPO_SERVICE_URL`,`TSHEPO_BASE_URL`) | tshepo-authz service DNS (`:8081`) | migrated | N/A | keep aligned with trust contract matrix | platform-ops / complete | none |
| deployment-config | `services/experience-bff/helm/experience-bff/values.yaml` (`TSHEPO_BASE_URL`) | tshepo-authz service DNS (`:8081`) | migrated | N/A | keep aligned with trust contract matrix | experience-platform / complete | none |
| compatibility-proxy | `tshepo-authz-service` legacy policy proxy endpoints | canonical authz policy endpoints | active temporary bridge | unit tests + deprecation metadata in OpenAPI | remove when legacy route telemetry hits are zero and all callers classified as migrated | trust-platform / in-progress | required during migration window |

## Exit Conditions

1. Legacy route usage telemetry remains zero for the agreed cutover window.
2. No active runtime policy consumer defaults to `:8079`.
3. Compatibility proxy routes are removed after zero-hit evidence and consumer confirmation.
4. `/internal/v1/federation/*` ownership decision is ratified with explicit replacement path.

## Progress Note — 2026-07-06 (W2-RED: local-compose split-brain closed)

**What changed (safe, ungated subset only):**

- **Local docker-compose split-brain fixed.** In `infra/envoy/envoy-runtime.yaml` the
  authorize/policy/trust REST routes proven served by `tshepo-authz-service`
  controllers were re-pointed from the legacy `tshepo_service` cluster to a new
  HTTP `tshepo_authz_service` cluster (`tshepo-authz:8081`):
  - `/api/v1/authorize` → `AuthorizeController` (`/v1/authorize`)
  - `/api/v1/step-up` → `StepUpController` (`/v1/step-up`)
  - `/api/v1/break-glass` → `BreakGlassController` (`/v1/break-glass`)
  - `/api/v1/policies` → `PolicyController` (`/v1/policies`)
  - `/api/v1/devices` → `DeviceController` (`/v1/devices`)
  - `/actuator/health` → authz engine.
- **Fail-open PolicyEngine is off ALL live paths.** The engine is reachable only via
  `/v1/authorize` (AuthorizeController); with that route (and policies/devices/
  step-up/break-glass) re-pointed to `tshepo-authz`, the fail-open PDP no longer
  serves any request in any environment (already absent from prod/preview
  `deploy/helm/**`; now unreachable in local compose too).
- **Coordinator decision — legacy `tshepo` container RETAINED (not removed).** The
  worker initially removed it, but the 8 non-PDP route families below have **no
  other server in `docker-compose.runtime.yml`** (their canonical split-out
  services `tshepo-identity/consent/audit/keys/offline-service` exist in-tree but
  are NOT wired into this compose), so removal 503'd them locally. Since those
  routes are not the fail-open PDP, the container is kept as their local-only
  server while the PDP routes are severed. FOLLOW-UP (new item): add the split-out
  services to `docker-compose.runtime.yml`, re-point the 8 families to them, then
  remove this container.
- **Stale reference fixed.** `experience-bff/.../registry-downstream-services.yml`
  no longer defaults `tshepo-service` to `:8079`; redirected to `:8081`.
- **Guard added.** `EnvoyRuntimeNoLegacyTshepoRouteGuardTest` (experience-bff)
  fails if any live authorize/policy Envoy route targets `tshepo_service` again.

**Non-PDP routes still served locally by the retained container** (canonical owners
not yet in runtime compose — the FOLLOW-UP above): `/api/v1/identity`,
`/api/v1/consent`, `/api/v1/audit`, `/api/v1/keys`, `/api/v1/sign`,
`/api/v1/certificates`, `/api/v1/offline`, `/external/v1/`. These are NOT the
fail-open PDP; keeping them on the monolith locally does not reintroduce the
authz risk.

**Also out of this worker's scope (flagged, NOT changed):** other compose files still
build `services/tshepo-service` — `ops/runtime/docker-compose.kernel.yml`,
`compose/trust/docker-compose.trust-e2e.yml`, and `.github/workflows/deploy.yml`.

**Exit conditions remain OPEN — nothing below is met by this pass:**

- Exit condition **1** (30-day zero-hit telemetry windows for the `/v1/*` compat
  routes) is **STILL OPEN**. Not evidenced here.
- Exit condition **3** (compatibility proxy removal) is **STILL OPEN** and
  telemetry-gated. The compat proxy was **not** touched.
- Exit condition **4** (`/internal/v1/federation/*` ownership ADR) is **STILL
  BLOCKED**. Not resolved here.
- `services/tshepo-service` **source was NOT deleted** and remains in the
  `services/pom.xml` reactor (compiles under the backend-reactor-tests gate).
