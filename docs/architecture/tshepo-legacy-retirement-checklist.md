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

## Progress Note — 2026-07-06 (W2-RED-FOLLOWUP: split-out services wired, 7/8 non-PDP families migrated)

**What changed (config wiring + docs + guard test only — no service source touched):**

- **Split-out services wired into `docker-compose.runtime.yml`.** Added five
  canonical non-PDP services, mirroring the existing service-block pattern
  (build context, postgres/redis/kafka/keycloak env, `SERVER_PORT`, `mem_limit`,
  `depends_on … service_healthy`, actuator healthcheck):
  - `tshepo-identity` — container `:8181`, host `18181` (host `8181` is taken by OPA)
  - `tshepo-consent` — container `:8182`, host `18182`
  - `tshepo-audit` — container `:8183`, host `18183`
  - `tshepo-keys` — container `:8184`, host `18184`
  - `tshepo-offline` — container `:8185`, host `18185`

- **7 of 8 non-PDP families re-pointed off the legacy monolith** in
  `infra/envoy/envoy-runtime.yaml`, each to the split-out cluster PROVEN (by
  `@RequestMapping` controller, context-path `/`) to own the path:

  | Route prefix (rewrite) | Split-out service (cluster) | Proven controller(s) | Port |
  |---|---|---|---|
  | `/api/v1/identity` → `/v1/identity` | `tshepo-identity` (`tshepo_identity_service`) | `ResolutionController`, `ReconciliationController`, `CpidController`, `MosipController`, `TokenController` @ `/v1/identity*` | 8181 |
  | `/api/v1/consent` → `/v1/consent` | `tshepo-consent` (`tshepo_consent_service`) | `ConsentController`, `ConsentEvaluationController`, `PortalConsentController`, `ShareLinkController` @ `/v1/consent*` | 8182 |
  | `/api/v1/audit` → `/v1/audit` | `tshepo-audit` (`tshepo_audit_service`) | `ChainIntegrityController`, `AuditQueryController`, `AuditIngestController`, `AuditExportController`, `AccessHistoryController` @ `/v1/audit*` | 8183 |
  | `/api/v1/keys` → `/v1/keys` | `tshepo-keys` (`tshepo_keys_service`) | `KeyManagementController`, `JwksController` @ `/v1/keys*` | 8184 |
  | `/api/v1/sign` → `/v1/sign` | `tshepo-keys` (`tshepo_keys_service`) | `SigningController` @ `/v1/sign` | 8184 |
  | `/api/v1/certificates` → `/v1/certificates` | `tshepo-keys` (`tshepo_keys_service`) | `CertificateController` @ `/v1/certificates` | 8184 |
  | `/api/v1/offline` → `/v1/offline` | `tshepo-offline` (`tshepo_offline_service`) | `OfflineActionController`, `ReconciliationController`, `CapabilityController`, `OfflinePackController` @ `/v1/offline*` | 8185 |

- **Legacy `tshepo` container + `tshepo_service` cluster RETAINED (partial, not full removal)**
  — HONEST PARTIAL COMPLETION. The **one residual family `/external/v1/`** has **no
  single split-out owner**: repo-wide, `/external/v1/*` is served across
  `data-governance-service`, `channels-service`, `indawo-service`,
  `asset-registry-service` and `data-warehouse-service` at distinct sub-paths —
  none of them a tshepo split-out — and the legacy `tshepo-service` source itself
  has NO `/external` controller (its only mappings are `/v1/authorize`,
  `/v1/biometric-policy`, `/v1/patient-share-policy`, `/v1/council-regulatory`,
  `/internal/v1/federation`, `/internal/v1/legacy`). Re-pointing `/external/v1/`
  to any one split-out service would 404; dropping the cluster would 503. Per the
  "do NOT create 503s" rule, the container + cluster are kept SOLELY for
  `/external/v1/`. Assigning a canonical owner for that prefix is a coordinator
  decision and is the only remaining blocker to full container removal.

- **Guard test tightened.** `EnvoyRuntimeNoLegacyTshepoRouteGuardTest`
  (experience-bff) gained `migratedNonPdpRoutesMustTargetSplitOutClustersNotLegacy`,
  asserting each of the 7 migrated prefixes resolves to its split-out cluster and
  never to `tshepo_service`. The legacy cluster is NOT asserted absent (it is
  intentionally retained for `/external/v1/`).

**Exit conditions remain OPEN — nothing below is met by this pass** (these gate
SOURCE deletion + compat-proxy removal + federation ownership, none of which this
worker performed):

- Exit condition **1** (30-day zero-hit telemetry windows for the `/v1/*` compat
  routes) — **STILL OPEN**. Not evidenced here.
- Exit condition **3** (compatibility proxy removal) — **STILL OPEN**, telemetry-gated.
  The compat proxy was NOT touched.
- Exit condition **4** (`/internal/v1/federation/*` ownership ADR) — **STILL BLOCKED**.
  Not resolved here.
- `services/tshepo-service` **source was NOT deleted**; the container is still built
  for the residual `/external/v1/` family. `tshepo-authz` decision logic and the
  compat proxy were NOT modified. Other compose files that build
  `services/tshepo-service` (`ops/runtime/docker-compose.kernel.yml`,
  `compose/trust/docker-compose.trust-e2e.yml`, `.github/workflows/deploy.yml`)
  remain out of this worker's scope.
