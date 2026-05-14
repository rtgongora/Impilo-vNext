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
