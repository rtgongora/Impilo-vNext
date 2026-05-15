# Experience Plane Production Readiness Final Report

Date: 2026-05-14  
Scope: Experience, Workflow and Orchestration plane (`experience-bff`, `ui/one-ui-shell`, `ui/experience`).

## Plane-Level Verdict

**NOT READY (closure pass progressed, explicit blocker-class gaps remain).**

The pass removed additional production-path synthetic-success behavior in finance/coverage/integration-hub/mobile-clinical/BFF parity routes and tightened shell fixture governance, but the plane still has unresolved backend readiness and portfolio-wide convergence gaps.

## Surfaces Reviewed

- BFF service: `services/experience-bff`
- Frontend shells: `ui/one-ui-shell`, `ui/experience`
- Route inventory sources:
  - `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/**/*Controller.java`
  - `ui/one-ui-shell/src/app/**/page.tsx`
  - `ui/experience/src/app/**/page.tsx`

## High-Risk Findings Remediated In This Pass

1. **Provider activation no longer returns placeholder success**
   - `ProviderActivationController` now resolves via real VARAPI health-id lookup and fail-closes with explicit `502` on upstream failure.
2. **Staffing routes no longer return local stubs/fake persistence**
   - `StaffingController` fail-closes with explicit `502` instead of local seeded on-call/swap fallback behavior.
3. **Mobile provider notices are wired to real registry backend**
   - `MobileNoticesController` now calls VARAPI notices endpoint and returns explicit `502` on dependency failure.
4. **Mobile provider reports no longer emit stubbed KPI payloads**
   - `ProviderReportsController` now delegates to `ReportingServiceClient` for list/run operations and fail-closes on reporting dependency errors.
5. **Additional BFF orchestration controllers now fail-close instead of empty-success**
   - `DataAccessGovernanceController`, `AccessChannelsController`, `OmnichannelController`, `PublicHealthController`, and `NotificationController` now return typed `502` envelopes on upstream failures instead of synthetic empty payloads.
6. **Active UI submit paths no longer treat backend failure as success**
   - Public-health forms in `OutbreaksTab`, `CampaignsTab`, `FieldOperationsTab`, `InspectionsTab`, and comms actions in `NotificationsCommsHub` now surface explicit unavailable errors and do not pretend writes succeeded.
7. **Finance/Coverage/Integration-hub BFF convergence hardened**
   - `FinanceController`, `CoverageController`, and `IntegrationHubController` now fail-close with typed `502` upstream-unavailable envelopes instead of returning empty-success payloads.
8. **Selected mobile clinical routes now fail honestly**
   - `MobileResultsController`, `MobileLabController`, `MobileScheduleController`, and `MobileTelemedicineController` now return typed `502` upstream errors on dependency failure.
   - `MobilePrescriptionController` now returns explicit `501` not-implemented envelopes for write/cancel paths instead of synthetic success.
9. **Disabled/prototype public-health tabs no longer surface demo fixture data**
   - `InspectionsTab` and `SurveillanceTab` disabled sub-tabs now show explicit unavailable messaging and no longer render demo fixture tables/KPIs.
10. **Prescription BFF parity tightened on active web and mobile flows**
   - `PharmacyController` no longer returns synthetic create/cancel success and no longer hides upstream failures with empty-success payloads.
   - `MobilePrescriptionController` remains explicit `501` for write/cancel with a precise backend blocker marker (`pharmacy-service-prescription-write-cancel-missing`).
11. **Portfolio error/header parity converged for high-use orchestration controllers**
   - `CommunicationController`, `GuidanceController`, `SearchController`, and `FhirInteropController` now return typed `502` upstream failures with request/correlation metadata instead of synthetic `200` fallbacks.
12. **Shell fixture governance tightened for clinical assessment**
   - `AssessmentSection` in both `ui/experience` and `ui/one-ui-shell` no longer renders hardcoded SOCRATES/ICD content and now gates the prototype full-exam tab behind explicit unavailable messaging.

## Tests Added/Updated

- `ProviderActivationControllerTest`
- `StaffingControllerTest`
- `MobileNoticesControllerTest`
- `ProviderReportsControllerTest`
- `DataAccessGovernanceControllerTest`
- `AccessChannelsControllerTest`
- `OmnichannelControllerTest`
- `PublicHealthControllerTest`
- `NotificationControllerTest`
- `FinanceControllerTest`
- `IntegrationHubControllerTest`
- `MobileResultsControllerTest`
- `MobileLabControllerTest`
- `MobileScheduleControllerTest`
- `MobileTelemedicineControllerTest`
- `MobilePrescriptionControllerTest`
- `PharmacyControllerTest`
- `CommunicationControllerTest`
- `GuidanceControllerTest`

## Validation Commands Run

- `mvn -pl experience-bff -am test` (from `services`) -> **PASS**
- `npm run lint` (one-ui-shell) -> **FAIL (pre-existing lint backlog outside this change-set)**
- `npm run lint` (experience-ui) -> **PASS**
- `npm run type-check` (experience-ui) -> **PASS**
- `npm run test` (experience-ui) -> **PASS**
- `npm run build` (experience-ui) -> **PASS**
- `node scripts/completeness/generate-completeness-report.mjs` -> **PASS**
- `node scripts/completeness/openapi-contracts.mjs` -> **PASS**

### Narrow validation cleanup pass (2026-05-14)

- `ui/experience/src/app/finance/billing/page.test.tsx` mock setup fixed to preserve `@tanstack/react-query` exports and include `useQueryClient`/`useMutation` test doubles; targeted and full suite now pass.
- `ui/experience/src/app/layout.tsx` migrated off `next/font/google` Inter runtime fetch to avoid external Google Fonts dependency during build in restricted TLS/network environments.
- `npm run build` (experience-ui) now succeeds without remote font fetch dependency.

## Remaining Blockers

1. **Prescription write/cancel backend capability is still absent in pharmacy-service**
   - `experience-bff` now reports this explicitly (`501` with backend blocker metadata) on both mobile and web prescription write/cancel edges.
2. **Plane-wide canonical error/header parity remains partial**
   - High-use orchestration routes are converged, but long-tail controllers still require route-by-route envelope/status/header harmonization verification.
3. **Shell-wide fixture governance remains partial**
   - Active assessment prototype risk was gated, but additional shell routes still require full fixture/demonstration governance closure.
4. **Route-level contract and wiring tests remain incomplete**
   - Newly hardened routes are covered; full Experience route portfolio still lacks comprehensive route-matrix assertions.

The Experience plane remains **NOT READY** due to functional blockers, not validation tooling:

1. Mobile prescription write/cancel backend API capability is still not implemented (explicit `501` at BFF edge).
2. Portfolio-wide error/header parity across all Experience routes remains incomplete.
3. Shell-wide fixture governance/isolation requires completion beyond the currently hardened tabs.

## Production Go/No-Go Recommendation

**NO-GO** for Experience plane READY declaration.

The plane has meaningful production hardening progress, but blocker-class fallback/mock patterns still exist and must be eliminated or isolated before a READY verdict.
