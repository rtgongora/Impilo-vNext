# TSHEPO Legacy Retirement Execution Plan

Date: 2026-05-14  
Scope: `tshepo-service` compatibility monolith only.

Machine-readable gate checklist: `docs/architecture/tshepo-legacy-retirement-checklist.md`

## 1) Retained Route Inventory and Classification

| Route base | Classification | Notes |
|---|---|---|
| `/v1/authorize` | compatibility-only | superseded by `tshepo-authz-service /v1/authorize`; retain only while consumers migrate |
| `/v1/biometric-policy` | delegate target or retire/review | migrate to canonical trust policy ownership (`tshepo-authz-service`) |
| `/v1/patient-share-policy` | delegate target or retire/review | migrate to canonical trust policy ownership |
| `/v1/council-regulatory` | delegate target or retire/review | move policy ownership to canonical trust service |
| `/internal/v1/federation` | compatibility-only (active) | retain while federation migration path is formalized |
| `/internal/v1/council-regulatory/learning` | compatibility relay | explicit relay to learning path, deprecate when upstream consumers migrate |
| `/internal/v1` probe routes | active compatibility infra | health/test probes |
| `/internal/v1/legacy/route-usage` | retirement control route | operational telemetry for legacy usage gates |

## 2) Current Consumers (Repo Evidence)

| Reference | Classification | Status |
|---|---|---|
| `services/vito-service` policy clients | active runtime consumer | migrated default target to `TSHEPO_AUTHZ_BASE_URL` (legacy fallback retained) |
| `services/varapi-service` policy clients | active runtime consumer | migrated default target to `TSHEPO_AUTHZ_BASE_URL` (legacy fallback retained) |
| `services/msika-flow-service` policy client | active runtime consumer | migrated default target to `TSHEPO_AUTHZ_BASE_URL` (legacy fallback retained) |
| `infra/k8s/config/shared-config.yaml` `TSHEPO_SERVICE_URL`/`TSHEPO_BASE_URL` | deployment wiring | moved to authz service host for migration-forward default |
| `services/experience-bff/helm/experience-bff/values.yaml` `TSHEPO_BASE_URL` | deployment wiring | moved to authz service host |
| smoke scripts (`scripts/smoke/*.sh`) | test-only/ops-only | retained; still reference `/api/v1/authorize` compatibility checks |
| architecture/docs references to legacy routes | documentation-only | retained with deprecation context |

Migration status (this pass):
- `vito-service`, `varapi-service`, and `msika-flow-service` policy-base defaults moved to `TSHEPO_AUTHZ_BASE_URL` with fallback to legacy `TSHEPO_POLICY_BASE_URL`.
- defaults are guarded by new tests:
  - `vito-service` `TshepoPolicyBaseUrlDefaultGuardTest`
  - `varapi-service` `TshepoPolicyBaseUrlDefaultGuardTest`
  - `msika-flow-service` `TshepoPolicyBaseUrlDefaultGuardTest`
- `varapi-service` fallback property default updated to `http://localhost:8081` (legacy `:8079` default removed from runtime property object).
- `tshepo-authz-service` now exposes temporary compatibility proxy routes for legacy policy surfaces so active runtime consumers can migrate off direct `tshepo-service` host dependencies.
- remaining hard legacy references are retained in docs/scripts/test harnesses and are explicitly classified.

## 3) Migration Targets

| Legacy route | Canonical replacement | Migration action | Test requirement |
|---|---|---|---|
| `/v1/authorize` | `tshepo-authz-service /v1/authorize` | route all callers to authz service DNS/config | ext_authz + caller contract tests |
| `/v1/biometric-policy/evaluate` | `tshepo-authz-service` trust policy endpoint set | replace caller base URLs | policy deny/allow integration tests |
| `/v1/patient-share-policy/evaluate` | `tshepo-authz-service` policy route set | migrate VITO/VARAPI callers | end-to-end patient-share policy tests |
| `/v1/council-regulatory/evaluate` | canonical trust policy service | migrate council policy clients | policy evaluation regression tests |
| `/internal/v1/council-regulatory/learning/*` | direct learning-service route | remove relay dependency | relay removal compatibility tests |
| `/internal/v1/federation/*` | canonical federation owner (ADR follow-up) | decide retain/migrate target | ownership ADR + migration tests |

## 4) Technical Constraints Implemented

Implemented in this pass:

1. `tshepo-service` production security default is authenticated for non-public routes.
2. Legacy `/v1/*` route access now emits deprecation telemetry:
   - response headers `Deprecation: true`, `Sunset: 2026-12-31`
   - warning logs on legacy route usage
   - in-process usage counters + recent event evidence (route/method/timestamp/actor/caller/correlation) exposed at `/internal/v1/legacy/route-usage`
3. Route inventory guard test added to prevent unclassified base route additions.
4. Security guard test retained to prevent fallback to default `anyRequest().permitAll()`.
5. Legacy telemetry route behavior is now tested via `LegacyRouteTelemetryTest`.

## 5) Retirement Gates

Gate-1: **Consumer migration complete**
- all known service/config consumers moved to canonical services.

Gate-2: **Legacy usage at zero**
- `/internal/v1/legacy/route-usage` and logs show zero legacy `/v1/*` hits across the agreed window.

Gate-3: **Canonical route confidence**
- canonical trust route integration tests pass in CI.

Gate-4: **Compatibility routes explicitly deprecated**
- route deprecation telemetry in place and documented.

Gate-5: **Removal release defined**
- concrete release version/date for route removal with rollback plan.

Gate-6: **Compatibility proxy decommission**
- all `Legacy-Compatibility` authz proxy routes marked deprecated in OpenAPI and removed only after zero-hit telemetry window.

## 6) Rollback Plan

- Re-enable only bounded compatibility routing if migration regressions occur.
- Do not reintroduce default permit-all behavior.
- Keep deprecation telemetry enabled during rollback period to avoid hidden dependency growth.
