# Backend-to-Frontend Wiring Map

This map tracks user-facing capability wiring across UI, BFF/gateway, backend owner, and operational controls.

| Capability | UI screen/component | BFF route | Backend API/service owner | Data/event dependency | Authz/audit requirement | Status |
|---|---|---|---|---|---|---|
| Citizen profile | `apps/mobile/citizen-app/src/screens/personal/*` | `/internal/v1/mobile/citizen/profile` | `experience-bff` -> citizen profile controllers | profile DB + audit events | TSHEPO authz + audit required | wired |
| Wallet and finance summary | `apps/mobile/citizen-app/src/screens/personal/WalletSection.tsx` | `/internal/v1/wallet/*` | `experience-bff` -> `mushex-service` / wallet integration | enterprise finance DB + finance events | high (authz/audit + purpose-of-use) | partial (fallback paths exist) |
| Communication preferences + remote consent capture | citizen/provider communication preferences screens and assisted communication workflows | `/internal/v1/mvumo/*` | `experience-bff` -> `mvumo-service` -> `tshepo-consent-service` | mvumo consent DB/outbox + tshepo consent directives + audit events | critical (consent + authz + audit) | wired; remote-session/template backend implemented, UI/admin workflow parity still partial |
| Trust policy decisioning (platform-wide) | non-UI direct gateway enforcement (all channels) | Envoy `ext_authz` gRPC/HTTP checks (`/v1/authorize`) | `infra/envoy/envoy.yaml` -> `tshepo-authz-service` | TSHEPO policy DB + decision logs + revocation streams | critical (authz, break-glass, audit obligations) | wired, no direct frontend surface |
| Trust policy compatibility migration for registry/market consumers | backend trust consumers (VITO/VARAPI/MSIKA) | `/v1/biometric-policy/evaluate`, `/v1/patient-share-policy/evaluate`, `/v1/council-regulatory/evaluate` | `tshepo-authz-service` compatibility proxy -> `tshepo-service` temporary backend | legacy policy evaluator + migration telemetry | high | backend-only compatibility bridge; retirement gate active |
| Trust consent enforcement | indirect via BFF/service access checks | `/v1/consent/evaluate` (service-to-service) | `tshepo-authz-service` -> `tshepo-consent-service` | consent directives + audit + outbox events | critical | wired, legacy `/v1` contract still canonical |
| Trust audit operations | admin audit and forensic surfaces | `/internal/v1/admin/audit*` via BFF; trust services also expose `/v1/audit/*` | `experience-bff` -> `tshepo-audit-service` (+ `audit-ledger-service` for immutable ledger checks) | audit log stores + hash-chain evidence | critical | partial (mixed trust/integration ownership and route conventions) |
| Legacy TSHEPO route usage observability | trust ops/admin telemetry consumers | `/internal/v1/legacy/route-usage` | `tshepo-service` legacy telemetry endpoint | in-memory route hit counters + deprecation logs | high | backend-only by design (operational API; no user-facing UI required) |
| Trust runtime cutover proof harness | CI integration harness | `test/integration/trust-fullstack-runtime.(sh|ps1)` | Docker runtime stack (`mvumo` -> `tshepo-consent`/`tshepo-authz`/`tshepo-service` telemetry + persisted outbox evidence; `tshepo-audit` health in stack) | postgres/kafka/redis/keycloak + service health and persistence checks | critical | added; CI job wired, local run blocked when Docker daemon unavailable |
| TSHEPO legacy compatibility endpoints | no direct UI ownership (compatibility consumers only) | legacy `/v1/*` constrained behind auth | `tshepo-service` (legacy compatibility monolith) | tshepo legacy data + delegated trust services | critical | constrained legacy only; no new consumer onboarding allowed |
| Provider queue + encounter tools | `apps/mobile/provider-app/src/screens/provider/*` | `/internal/v1/mobile/provider/*`, `/internal/v1/encounters/*` | `experience-bff` + clinical services | clinical DB + clinical events | high | wired |
| Clinical tools knowledge search | `ui/experience/src/app/clinical-tools/page.tsx`, `ui/one-ui-shell/src/app/clinical-tools/page.tsx` | canonical `/internal/v1/clinical/*` (temporary alias `/internal/v1/clinical-knowledge/*`) | `experience-bff` -> `clinical-knowledge-platform-service` | clinical knowledge DB/index | high | wired (frontend migrated to canonical prefix) |
| Facility/registry lookup | provider and citizen facility screens | `/internal/v1/facilities`, `/internal/v1/registry/*` | `experience-bff` -> `vito/varapi/tuso` | registry stores | high | wired |
| Public health surfaces | `ui/experience/src/components/public-health/*` | `/internal/v1/public-health/*` | `experience-bff` -> surveillance/campaign backends | analytics/public health stores | high | partial (mixed live + demo fixtures) |
| Workflow orchestration | web orchestration surfaces | `/internal/v1/workflows/*` | `experience-bff` -> `workflow-service` | workflow DB + integration events | high | partial (backend route exists, UI usage sparse) |
| Dispatch operations | ops-oriented surfaces | `/internal/v1/dispatch/*` | `experience-bff` -> `dispatch-service` | dispatch DB + ops events | high | partial (backend route exists, direct UI references sparse) |

## Open Wiring Gaps

- Citizen `ConditionsSection` and provider discovery screens remain TODO/local placeholders and need API-backed implementation.
- SOAP notes save flow in provider tooling requires persistence endpoint wiring.
- Public-health tabs using demo fixtures must be isolated to test/demo-only routes or removed from production path.
