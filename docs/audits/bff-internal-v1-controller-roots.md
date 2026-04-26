# Experience BFF — `/internal/v1/*` controller root paths

This appendix lists **class-level** `@RequestMapping` roots under `services/experience-bff/src/main/java`. Method-level sub-paths (for example `GET /internal/v1/queue/entries`) are not expanded here; see each controller.

**Ingress reminder:** the One UI dev server rewrites `/internal/v1/*` to `NEXT_PUBLIC_BFF_URL` (default `:8160`). Envoy (`infra/envoy/envoy-runtime.yaml`) can also route the **same paths** to `experience_bff` on `:10000` — identical BFF, different hop.

| Path prefix | Controller |
|-------------|------------|
| `/internal/v1` | `AuthSessionController`, `CareEmergencyInpatientController` (additional routes on class) |
| `/internal/v1/access` | `AccessChannelsController` |
| `/internal/v1/admin/reports/jobs` | `AdminReportJobController` |
| `/internal/v1/assistant` | `AssistantNotificationsController` |
| `/internal/v1/client-registry` | `ClientRegistryController` |
| `/internal/v1/clinical/shr-artifacts` | `ClinicalShrArtifactController` |
| `/internal/v1/commerce` | `CommerceFlowController` |
| `/internal/v1/conditions` | `ConditionsController` |
| `/internal/v1/ehr` | `StructuredHistoryController` |
| `/internal/v1/encounters` | `EncounterController` |
| `/internal/v1/facilities` | `FacilityController` |
| `/internal/v1/facility-registry` | `FacilityRegulatoryController` |
| `/internal/v1/finance/billing-workspace` | `FinanceBillingWorkspaceController` |
| `/internal/v1/finance/costa-intel` | `CostaIntelBffController` |
| `/internal/v1/finance/mushex-platform` | `FinanceMushexPlatformController` |
| `/internal/v1/identity/biometric/varapi` | `VarapiBiometricBffController` |
| `/internal/v1/identity/biometric/vito` | `VitoBiometricBffController` |
| `/internal/v1/imaging` | `ImagingExperienceController` |
| `/internal/v1/intelligence-plane` | `HealthIntelligenceController` |
| `/internal/v1/inventory` | `InventoryController` |
| `/internal/v1/learning` | `LearningController` |
| `/internal/v1/marketplace` | `MarketplaceController` |
| `/internal/v1/mobile/citizen` | `CitizenLongtailController` |
| `/internal/v1/mobile/citizen/appointments` | `CitizenAppointmentController` |
| `/internal/v1/mobile/citizen/consents` | `CitizenConsentController` |
| `/internal/v1/mobile/citizen/coverage` | `CitizenCoverageController` |
| `/internal/v1/mobile/citizen/feed` | `CitizenFeedController` |
| `/internal/v1/mobile/citizen/marketplace` | `CitizenMarketplaceController` |
| `/internal/v1/mobile/citizen/messaging` | `CitizenMessagingController` |
| `/internal/v1/mobile/citizen/prescriptions` | `CitizenPrescriptionController` |
| `/internal/v1/mobile/citizen/profile` | `CitizenProfileController` |
| `/internal/v1/mobile/citizen/records` | `CitizenRecordsController` |
| `/internal/v1/mobile/citizen/reminders` | `CitizenRemindersController` |
| `/internal/v1/mobile/citizen/results` | `CitizenResultsController` |
| `/internal/v1/mobile/citizen/summary` | `CitizenHealthSummaryController` |
| `/internal/v1/mobile/citizen/support` | `CitizenSupportController` |
| `/internal/v1/mobile/citizen/telehealth` | `CitizenTelehealthController` |
| `/internal/v1/mobile/citizen/timeline` | `CitizenTimelineController` |
| `/internal/v1/mobile/provider` | `MobileProviderExtendedController` |
| `/internal/v1/mobile/provider/admin-registry` | `ProviderAdminRegistryController` |
| `/internal/v1/mobile/provider/developer` | `ProviderDeveloperHubController` |
| `/internal/v1/mobile/provider/labs` | `MobileLabController` |
| `/internal/v1/mobile/provider/labs/results` | `MobileResultsController` |
| `/internal/v1/mobile/provider/messaging` | `MobileMessagingController` |
| `/internal/v1/mobile/provider/notices` | `MobileNoticesController` |
| `/internal/v1/mobile/provider/offline` | `MobileOfflineController` |
| `/internal/v1/mobile/provider/ops-reports` | `ProviderOpsReportsController` |
| `/internal/v1/mobile/provider/prescriptions` | `MobilePrescriptionController` |
| `/internal/v1/mobile/provider/professional-channels` | `ProviderProfessionalChannelsHubController` |
| `/internal/v1/mobile/provider/professional-settings` | `ProviderProfessionalSettingsHubController` |
| `/internal/v1/mobile/provider/profile` | `MobileProfileController` |
| `/internal/v1/mobile/provider/referrals` | `MobileReferralController` |
| `/internal/v1/mobile/provider/reports` | `ProviderReportsController` |
| `/internal/v1/mobile/provider/schedule` | `MobileScheduleController` |
| `/internal/v1/mobile/provider/search` | `MobileProviderSearchController` |
| `/internal/v1/mobile/provider/supervisor` | `MobileSupervisorController` |
| `/internal/v1/mobile/provider/support` | `MobileSupportController` |
| `/internal/v1/mobile/provider/tasks` | `MobileTaskController` |
| `/internal/v1/mobile/provider/telemedicine` | `MobileTelemedicineController` |
| `/internal/v1/mobile/provider/triage` | `MobileTriageController` |
| `/internal/v1/mobile/provider/vitals` | `MobileVitalsController` |
| `/internal/v1/msika` | `MsikaGovernanceController` |
| `/internal/v1/notifications` | `NotificationController` |
| `/internal/v1/omnichannel` | `OmnichannelController` |
| `/internal/v1/pacs` | `PacsController` |
| `/internal/v1/patients` | `PatientController` |
| `/internal/v1/profile` | `VisibilityProfileController` |
| `/internal/v1/public-health` | `PublicHealthController` |
| `/internal/v1/registry` | `RegistryController`, `RegistryGeoLocalityController` |
| `/internal/v1/registry-intake` | `RegistryIntakeController` |
| `/internal/v1/registry/coverage` | `CoverageRegistrationPreviewController` |
| `/internal/v1/registry/zibo` | `ZiboRegistryProxyController` |
| `/internal/v1/reports` | `ReportJobController` |
| `/internal/v1/search` | `SearchController` |
| `/internal/v1/shell/file-catalog` | `ShellFileCatalogController` |
| `/internal/v1/shell/workspace-state` | `ShellWorkspaceStateController` |
| `/internal/v1/shifts` | `ShiftController` |
| `/internal/v1/staffing` | `StaffingController` |
| `/internal/v1/teleconsult` | `TeleconsultController` |
| `/internal/v1/trust` | `BiometricPolicyBffController` |
| `/internal/v1/workforce-governance` | `WorkforceGovernanceController` |

## Also present (grep `LabOrdersController`, `QueueController`, …)

These controllers use explicit roots and are easy to miss in a single-pattern grep:

| Path prefix | Controller |
|-------------|------------|
| `/internal/v1/lab-orders` | `LabOrdersController` |
| `/internal/v1/queue` | `QueueController` |
| `/internal/v1/referrals` | `ReferralsController` |
| `/internal/v1/timeline` | `ClinicalTimelineController` |

Additional domains (pharmacy, vitals, triage, telehealth, workspaces, trust admin, payer ops, etc.) live in other classes under `controller/` — regenerate this table with IDE search on `@RequestMapping("/internal/v1` when adding major surfaces.

## Envoy row (same URI, alternate ingress)

| Client | URI | Target |
|--------|-----|--------|
| Next.js browser | `http://localhost:3099/internal/v1/...` | BFF `:8160` (rewrite) |
| Gateway / mobile | `http://localhost:10000/internal/v1/...` | `experience_bff` cluster → `:8160` (`envoy-runtime.yaml`) |
