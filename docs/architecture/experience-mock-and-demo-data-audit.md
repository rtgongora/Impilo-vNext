# Experience Mock and Demo Data Audit

Date: 2026-05-14

## Classification

- `remediated-in-this-pass`
- `production-risk-open`
- `demo-only-remove-from-prod`
- `test-only`

## Findings

| Path | Finding | Classification | Action |
|---|---|---|---|
| `services/experience-bff/.../ProviderActivationController` | Placeholder provider payload on VARAPI failure | remediated-in-this-pass | Removed placeholder; fail-close with explicit error. |
| `services/experience-bff/.../StaffingController` | Local seeded/synthetic on-call and swap fallback behavior | remediated-in-this-pass | Removed local stubs and fake persistence; fail-close with explicit error. |
| `services/experience-bff/.../mobile/MobileNoticesController` | Always returned empty-success list | remediated-in-this-pass | Wired to VARAPI notices; explicit upstream failure behavior. |
| `services/experience-bff/.../mobile/provider/ProviderReportsController` | Stub report catalog/data payloads | remediated-in-this-pass | Wired to reporting service and fail-close on dependency failure. |
| `services/experience-bff/.../DataAccessGovernanceController` | empty-success fallback on upstream failure | remediated-in-this-pass | Converged to typed `502` upstream-unavailable envelope. |
| `services/experience-bff/.../AccessChannelsController` | empty-success fallback on upstream failure | remediated-in-this-pass | GET/POST proxy paths now fail-close with typed `502`. |
| `services/experience-bff/.../OmnichannelController` | empty-success fallback/default success paths | remediated-in-this-pass | Callback/journey/disclosure routes now fail-close on dependency failure. |
| `services/experience-bff/.../PublicHealthController` | mixed empty-success fallback behavior | remediated-in-this-pass | Proxy helper paths now return explicit `502` error envelopes. |
| `services/experience-bff/.../NotificationController` | list/read/preferences empty-success fallback behavior | remediated-in-this-pass | Notification proxy now fails closed with typed `502` envelopes. |
| `services/experience-bff/.../FinanceController` | finance list routes returned empty-success on COSTA failure | remediated-in-this-pass | billing/payments/refunds/tariffs/claims list routes now fail-close with typed `502`. |
| `services/experience-bff/.../CoverageController` | coverage list routes returned empty-success on coverage failure | remediated-in-this-pass | list routes now fail-close with typed `502` envelopes. |
| `services/experience-bff/.../IntegrationHubController` | integration-hub list routes returned empty-success map | remediated-in-this-pass | list routes now fail-close with typed `502` envelopes. |
| `services/experience-bff/.../mobile/MobileResultsController` | mobile lab results returned empty-success list on OROS failure | remediated-in-this-pass | fail-close with typed `502 OROS_UNAVAILABLE`. |
| `services/experience-bff/.../mobile/MobileLabController` | synthetic local lab order success and empty-success read fallbacks | remediated-in-this-pass | create/list/get/cancel now fail honestly (`502` or `400` for missing query inputs). |
| `services/experience-bff/.../mobile/MobileScheduleController` | empty-success schedule fallback on TUSO failure | remediated-in-this-pass | fail-close with typed `502 TUSO_UNAVAILABLE`. |
| `services/experience-bff/.../mobile/MobileTelemedicineController` | synthetic local session success and empty-success read fallbacks | remediated-in-this-pass | create/list/join/end now fail honestly with typed `502` and validation errors. |
| `services/experience-bff/.../mobile/MobilePrescriptionController` | synthetic create/cancel success | remediated-in-this-pass | create/cancel now explicit `501` not-implemented until backend write endpoints are wired. |
| `services/experience-bff/.../PharmacyController` | synthetic prescription create/cancel success and empty-success read fallbacks | remediated-in-this-pass | create/cancel now explicit `501` with backend blocker metadata; list/dispense/upstream routes now fail-close with typed envelopes. |
| `services/experience-bff/.../CommunicationController` | synthetic success on announcement/page/message actions when community upstream failed | remediated-in-this-pass | active routes now return typed `502 COMMUNITY_UNAVAILABLE` with request/correlation metadata. |
| `services/experience-bff/.../GuidanceController` | fallback synthetic guidance/consent payloads on upstream failure | remediated-in-this-pass | ask/reminders/education/search/consent routes now fail-close with typed `502` error envelopes. |
| `services/experience-bff/.../SearchController` | empty-success fallback on search failure | remediated-in-this-pass | search/document routes now return typed `502 SEARCH_UNAVAILABLE` with correlation metadata. |
| `services/experience-bff/.../FhirInteropController` | synthetic "unavailable" success payloads on upstream failure | remediated-in-this-pass | metadata/search/read routes now fail-close with typed `502 FHIR_GATEWAY_UNAVAILABLE`. |
| `ui/one-ui-shell/src/app/auth/login/page.tsx` | demo persona fixture references | demo-only-remove-from-prod | isolate from production login path or remove. |
| `ui/experience/src/components/public-health/publicHealthDemoFixtures.ts` | public-health demo fixtures for disabled/prototype tabs | demo-only-remove-from-prod | disabled tabs now render unavailable states without fixture data; keep file isolated from production route imports. |
| `ui/experience/src/components/notifications/NotificationsCommsHub.tsx` | local "stub send" behavior in compose/page actions | remediated-in-this-pass | send/page actions now use real `/internal/v1/notifications/send` API and fail-close when preference/service checks fail. |
| `ui/experience/src/components/ehr/sections/AssessmentSection.tsx` | hardcoded SOCRATES/ICD values and prototype full-exam behavior in active shell path | remediated-in-this-pass | static values removed; full-exam prototype tab now explicitly disabled/unavailable. |
| `ui/one-ui-shell/src/components/ehr/sections/AssessmentSection.tsx` | hardcoded SOCRATES/ICD values and prototype full-exam behavior in active shell path | remediated-in-this-pass | static values removed; full-exam prototype tab now explicitly disabled/unavailable. |
| `ui/**/__tests__/**` | test mocks | test-only | keep isolated. |

## Conclusion

Material risk reduction completed for key Experience orchestration paths, but broader route-by-route contract convergence and backend capability completeness still block READY status.
