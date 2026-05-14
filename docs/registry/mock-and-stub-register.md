# Mock and Stub Register

Classification:

- `test-only`: isolated to tests or non-production harnesses.
- `demo-only-remove-from-prod`: acceptable only in explicit demo paths.
- `accidental-production-risk`: appears in production runtime paths/config and must be remediated.
- `unknown-review-required`: ambiguous intent; architecture decision required.
- `remediated-in-this-pass`: previously risky path now fail-closed or replaced with real implementation.

| Path | Type | Classification | Notes |
|---|---|---|---|
| `services/notification-service/src/main/java/.../EmailStubProvider.java` | stub provider | accidental-production-risk | `matchIfMissing=true` log-only default. |
| `services/notification-service/src/main/java/.../SmsStubProvider.java` | stub provider | accidental-production-risk | `matchIfMissing=true` log-only default. |
| `services/notification-service/src/main/java/.../MockProvider.java` | mock provider | accidental-production-risk | unconditional component fallback. |
| `services/mushex-service/src/main/resources/application.yml` | sandbox adapter toggle | accidental-production-risk | sandbox mode default enabled in baseline config. |
| `services/experience-bff/src/main/java/.../DeviceRegistryController.java` | placeholder endpoint | accidental-production-risk | synthetic response path in production controller. |
| `services/experience-bff/src/main/java/.../IdentityAssuranceController.java` | placeholder endpoint | accidental-production-risk | placeholder assurance state fallback. |
| `services/experience-bff/src/main/java/.../ProviderActivationController.java` | placeholder fallback | accidental-production-risk | fallback placeholder payload on upstream failure. |
| `services/experience-bff/src/main/java/.../PatientController.java` | local fallback write | accidental-production-risk | local patient creation fallback on upstream failure. |
| `services/experience-bff/src/main/java/.../TempIdReviewController.java` | placeholder queue | accidental-production-risk | hardcoded placeholder queue rows. |
| `services/mvumo-service/src/main/java/.../MvumoInternalController.java` | formerly stub/not-implemented endpoint behavior | remediated-in-this-pass | remote session actions and template create/update are now fully implemented and persisted; no production success stubs or permanent 501 remain for intended MVUMO trust flows. |
| `services/mvumo-service/src/main/java/.../MvumoService.java` | previously stubbed trust decision path | remediated-in-this-pass | `/internal/v1/mvumo/evaluate` now delegates to live `tshepo-consent-service /v1/consent/evaluate` (no stub response). |
| `services/tshepo-service/src/main/java/.../SecurityConfig.java` | compatibility security posture | remediated-in-this-pass | legacy monolith default authorization tightened to `anyRequest().authenticated()` with regression guard test; compatibility risk remains due route overlap, not permit-all auth. |
| `services/tshepo-service/src/main/java/.../LegacyRouteDeprecationFilter.java` | compatibility deprecation telemetry | remediated-in-this-pass | legacy `/v1/*` accesses now emit deprecation headers and usage telemetry to support retirement gates. |
| `services/tshepo-authz-service/src/main/java/.../LegacyPolicyCompatibilityController.java` | migration compatibility proxy | tracked-and-constrained | temporary compatibility proxy routes for policy consumer migration to authz entrypoint; explicitly transitional and gated for retirement after zero-legacy-use window. |
| `contracts/openapi/tshepo-authz.openapi.yaml` legacy policy compatibility paths | compatibility contract marker | tracked-and-constrained | compatibility paths are now explicitly marked deprecated in OpenAPI; removal governed by `docs/architecture/tshepo-legacy-retirement-checklist.md`. |
| `test/integration/trust-fullstack-runtime.sh` and `test/integration/trust-fullstack-runtime.ps1` | runtime harness | test-only | full-stack trust runtime harness for CI/local cutover evidence; not a production path and does not inject fake success into service runtime. |
| `libs/tech-companion-mock/**` | mock app | test-only | harness/mocked connector service. |
| `services/**/src/test/**` | unit/integration mocks | test-only | MockMvc/WireMock/test doubles. |
| `ui/**/__tests__/**` and `apps/mobile/**/__tests__/**` | frontend mocks | test-only | isolated test mocks. |
| `impilo-structure/src/data/mockClinicalData.ts` | mock data | demo-only-remove-from-prod | prototype dataset not for production paths. |
| `ui/one-ui-shell/src/app/auth/login/page.tsx` demo personas | UI fixture | demo-only-remove-from-prod | should be dev-only gated. |
| `ui/experience/src/components/public-health/publicHealthDemoFixtures.ts` | demo fixtures | unknown-review-required | mixed with live public-health tabs; isolate/remove. |

## Mandatory Remediation Rules

- No mock/stub provider may be default-selected in production profile.
- Placeholder responses in production routes must be replaced with hard-fail + observable error handling or real implementations.
- Demo fixtures must be isolated behind non-production toggles and excluded from production builds/routes.

## Trust-plane note (current)

- No known MVUMO production-path stub-success or permanent 501 placeholder remains for intended trust capabilities reviewed in this pass.
