# Impilo vNext — Skeleton Build Tracker

> Status key: `[x]` done · `[ ]` pending
> Each unchecked item = one atomic commit when implemented.

---

## Phase 0 — Root Configuration

- [x] `.gitignore`
- [x] `.env.example`
- [x] `README.md`
- [x] `CLAUDE.md` (project rules)
- [x] `docker-compose.yml` (Postgres, Redis, Kafka, MinIO, Keycloak, HAPI FHIR, Orthanc)
- [x] `scripts/seed/init-databases.sql` (per-service databases)
- [x] `services/pom.xml` (parent POM / BOM)
- [x] `ui/package.json` (npm workspace root)
- [x] `ui/turbo.json` (Turbo build orchestration)

## Phase 1 — Trust & Governance Plane

### TSHEPO Service (complete)
- [x] `pom.xml`, `Dockerfile`, `application.yml`
- [x] Flyway migrations V001–V004 (policy log, audit chain, consent, device risk + outbox)
- [x] `TshepoApplication.java`
- [x] `SecurityConfig.java`, `RateLimitConfig.java`
- [x] `TrustHeaders.java` (14 header constants — single source of truth)
- [x] `AuthorizeController.java` (Envoy ext_authz HTTP endpoint)
- [x] `PolicyEngine.java` (7-step evaluation + serialized audit chain writes)
- [x] `Decision.java`, `Obligations.java`, `PurposeOfUse.java`, `RiskScoring.java`
- [x] JPA entities + repositories (6 entities, 6 repos)
- [x] `AuditOutboxPublisher.java` (Kafka outbox poller)
- [x] Helm chart (`Chart.yaml`, `values.yaml`)

### TSHEPO — Remaining
- [ ] `StepUpController.java` — step-up authentication challenge/response
- [ ] `ConsentController.java` — CRUD consent directives
- [ ] `AuditController.java` — audit trail query endpoints
- [ ] `DeviceRiskController.java` — device profile management

### Envoy Gateway
- [x] `infra/envoy/envoy.yaml` (ext_authz HTTP + all service routes)
- [ ] `infra/envoy/routes/public.yaml` — public route split
- [ ] `infra/envoy/routes/internal.yaml` — internal service routes
- [ ] `infra/envoy/routes/imaging.yaml` — PACS/imaging routes
- [ ] `infra/envoy/ext_authz/tshepo.yaml` — ext_authz config extract

## Phase 2 — Registry Spine Plane

### VITO Service (Client Registry)
- [x] Flyway migrations V001–V005 (client, aliases, dedup, provisional, ops)
- [x] Helm chart
- [ ] `pom.xml`
- [ ] `Dockerfile`
- [ ] `application.yml`
- [ ] `VitoApplication.java`
- [ ] `ClientController.java` — client CRUD
- [ ] `IdIssuanceController.java` — Impilo ID issuance flow
- [ ] `RecoveryController.java` — alias recovery/rotation
- [ ] `DedupController.java` — dedup case management
- [ ] `OfflineController.java` — provisional ID handling
- [ ] `OpsConsoleController.java` — ops overrides
- [ ] `ImpiloIdAliasService.java` — HMAC lookup + Argon2id verification
- [ ] `IdentityProofingService.java` — proofing workflow
- [ ] `DedupMatchingService.java` — duplicate detection
- [ ] `ProvisionalIdService.java` — offline provisional IDs
- [ ] `MergeService.java` — record merge/unmerge
- [ ] JPA entities + repositories (Client, Alias, DedupCase, Provisional)
- [ ] `IdentityOutboxPublisher.java`

### VARAPI Service (Provider Registry)
- [x] Flyway migration V001
- [x] Helm chart
- [ ] `pom.xml`
- [ ] `Dockerfile`
- [ ] `application.yml`
- [ ] `VarapiApplication.java`
- [ ] `ProviderController.java`
- [ ] `CouncilController.java`
- [ ] `CredentialingController.java`
- [ ] `CertificateController.java`
- [ ] `CpdController.java`
- [ ] `OpsConsoleController.java`
- [ ] Core services (ProviderIdService, CouncilSyncService, PrivilegingService, PractitionerRoleMapper, BiometricBindingService)
- [ ] JPA entities + repositories
- [ ] Additional migrations V002–V005 (secret token verifier, roles, councils, certificates/CPD)

### TUSO Service (Facility Registry)
- [x] Flyway migration V001
- [x] Helm chart
- [ ] `pom.xml`
- [ ] `Dockerfile`
- [ ] `application.yml`
- [ ] `TusoApplication.java`
- [ ] `FacilityController.java`
- [ ] `WorkspaceController.java`
- [ ] `ResourceController.java`
- [ ] `RosterController.java`
- [ ] `ControlTowerController.java`
- [ ] `ConfigController.java`
- [ ] `OpsOverrideController.java`
- [ ] `GofrSyncController.java`
- [ ] Core services (CapabilityService, ResourceCalendarService, ControlTowerRulesEngine, TelemetryIngestService)
- [ ] JPA entities + repositories
- [ ] Additional migrations V002–V006 (workspaces/resources, roster, capabilities, configs/flags, telemetry)

### ZIBO Service (Terminology)
- [x] Flyway migration V001
- [x] Helm chart
- [ ] `pom.xml`
- [ ] `Dockerfile`
- [ ] `application.yml`
- [ ] `ZiboApplication.java`
- [ ] `CodeSystemController.java`
- [ ] `ValueSetController.java`
- [ ] `ValidationController.java`
- [ ] `GovernanceController.java`
- [ ] Core services (TerminologyStore, Validator, VersioningService)
- [ ] JPA entities + repositories
- [ ] Additional migration V002 (codesystems/valuesets)

### Product Registry Service
- [x] Flyway migration V001
- [x] Helm chart
- [ ] `pom.xml`
- [ ] `Dockerfile`
- [ ] `application.yml`
- [ ] `ProductRegistryApplication.java`
- [ ] Controllers + core services + repositories

## Phase 3 — Clinical Execution Plane

### BUTANO (HAPI FHIR — Shared Health Record)
- [x] Helm chart
- [ ] `Dockerfile`
- [ ] `config/hapi.properties`
- [ ] `config/access-policies.md`

### FHIR Gateway Service
- [x] Helm chart
- [ ] `pom.xml`
- [ ] `Dockerfile`
- [ ] `application.yml`
- [ ] `FhirGatewayApplication.java`
- [ ] `BundleController.java`
- [ ] `QueryController.java`
- [ ] Core services (TokenToCpidResolver, ZiboValidationInterceptor, ConsentEnforcementHook)

### PCT Service (Patient Care Tracker)
- [x] Flyway migration V001
- [x] Helm chart
- [ ] `pom.xml`
- [ ] `Dockerfile`
- [ ] `application.yml`
- [ ] `PctApplication.java`
- [ ] `ArrivalController.java`
- [ ] `QueueController.java`
- [ ] `EncounterController.java`
- [ ] `DispositionController.java`
- [ ] `TimelineController.java`
- [ ] `MetricsController.java`
- [ ] Core services (EncounterStateMachine, QueueAssignmentEngine, HandoffService, BedSnapshotService)
- [ ] JPA entities + repositories
- [ ] Additional migrations V002–V004 (queue, encounters, handover)

### OROS Service (Orders & Results)
- [x] Flyway migration V001
- [x] Helm chart
- [ ] `pom.xml`
- [ ] `Dockerfile`
- [ ] `application.yml`
- [ ] `OrosApplication.java`
- [ ] `OrdersController.java`
- [ ] `WorklistController.java`
- [ ] `ResultsController.java`
- [ ] `AcknowledgementController.java`
- [ ] `ImagingHookController.java`
- [ ] Core services (OrderStateMachine, WorklistBuilder, AdapterRouter, ReconciliationService)
- [ ] JPA entities + repositories
- [ ] Additional migrations V002–V004 (orders, results, worklists)

### Pharmacy Service
- [x] Flyway migration V001
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `PharmacyApplication.java`
- [ ] Controllers (Dispense, Substitution, Backorder, Pickup)
- [ ] Core services (PartialFillEngine, BarcodeService, BillingHook, InventoryHook)

### Inpatient Service
- [x] Flyway migration V001
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `InpatientApplication.java`
- [ ] Controllers (Admission, Ward, DeathDischarge)
- [ ] Core services (BedManager, DischargeWorkflow)

## Phase 4 — Finance Plane

### MUSheX Service (Payments)
- [x] Flyway migration V001
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `MushexApplication.java`
- [ ] Controllers (PaymentIntent, PaymentCallback, ClaimsSwitch, Wallet)
- [ ] Core services (IdempotencyService, ReconciliationJobs)

### Costing Engine Service
- [x] Flyway migration V001
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `CostingApplication.java`
- [ ] Controllers (Estimate, Methodology)
- [ ] Core services (CostModelEngine, RuleApplicationEngine, ChargeSheetEngine)

## Phase 5 — Integration / Ops Plane

### Inventory Service
- [x] Flyway migration V001
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `InventoryApplication.java`
- [ ] Controllers (Stock, StockCount, Wastage, Returns, Barcode, ElmisAdapter)
- [ ] Core services (FefopickEngine, CapabilityRouter, ShadowQueueService)

### Document Service
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `DocumentApplication.java`
- [ ] Controllers (Upload, Download, SlipPdf, SignedUrl)
- [ ] Core services (MinioClientFacade, PdfGenerator, SignatureService)

### PACS Adapter Service
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `PacsAdapterApplication.java`
- [ ] Controllers (OrthancWebhook, RecentStudies, ViewerToken, CorrelationOps)
- [ ] Core services (StudyCorrelationEngine, PdfExtractionService, SrToPdfRenderer, ButanoPushService, OrthancProxy)

### Notification Service
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `NotificationApplication.java`
- [ ] Controllers (Template, Send)
- [ ] Core services (DeliveryRouter)

### Jobs Service
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `JobsApplication.java`
- [ ] `ScheduledJobs.java`

### Integration Hub
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `IntegrationHubApplication.java`
- [ ] Adapters (eLMIS: Rest/Csv/Kafka, LIMS: Rest/Csv/Kafka, DHIS2: Export)
- [ ] `CanonicalTransforms.java`, `RouterController.java`

### Offline Sync Service
- [x] Flyway migration V001
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `OfflineSyncApplication.java`
- [ ] `SyncController.java`
- [ ] Core services (ConflictResolver, PackBuilder)

## Phase 6 — Experience Plane (UI)

### One UI Shell (primary app)
- [x] `package.json`, `next.config.js`, `tsconfig.json`, `tailwind.config.ts`
- [x] `apiClient.ts`, `contracts.ts`
- [x] Hooks: `useSession.ts`, `useContext.ts`, `usePolicyDecision.ts`
- [ ] `postcss.config.js`
- [ ] `src/app/layout.tsx` — root layout with shell, nav, context bar
- [ ] `src/app/(auth)/layout.tsx` — authenticated layout
- [ ] `src/app/(auth)/work/page.tsx` — WORK dashboard
- [ ] `src/app/(auth)/control/page.tsx` — CONTROL dashboard
- [ ] `src/app/(auth)/my-professional/page.tsx`
- [ ] `src/app/(auth)/my-life/page.tsx`
- [ ] `src/components/shell/AppShell.tsx` — left nav + context bar + status strip
- [ ] `src/components/nav/PrimaryNav.tsx`
- [ ] `src/components/context/ContextBar.tsx` — tenant/facility/workspace/shift selector
- [ ] `src/components/context/StepUpModal.tsx` — step-up challenge modal

### Shared UI Library
- [x] `package.json`, `tokens.css`, `index.ts`
- [ ] `tsconfig.json`
- [ ] Base components (Button, Card, DataTable, Badge, StatusIndicator)

### Ops Console
- [x] `package.json`
- [ ] `next.config.js`, `tsconfig.json`, `tailwind.config.ts`
- [ ] `src/app/(ops)/layout.tsx`
- [ ] Route stubs: tshepo, vito, varapi, tuso, zibo, oros, pct, mushex, pacs

### EHR App
- [x] `package.json`
- [ ] `next.config.js`, `tsconfig.json`, `tailwind.config.ts`
- [ ] `src/app/(ehr)/layout.tsx`
- [ ] Route stubs: dashboard, patient/[cpid], encounter/[encounterId], orders, results, summaries

### Portal App
- [x] `package.json`
- [ ] `next.config.js`, `tsconfig.json`, `tailwind.config.ts`
- [ ] `src/app/(portal)/layout.tsx`
- [ ] Route stubs: health-id, timeline, wallet, imaging, consent, recovery

## Phase 7 — API Contracts

- [ ] `contracts/openapi/tshepo.openapi.yaml`
- [ ] `contracts/openapi/vito.openapi.yaml`
- [ ] `contracts/openapi/varapi.openapi.yaml`
- [ ] `contracts/openapi/tuso.openapi.yaml`
- [ ] `contracts/openapi/zibo.openapi.yaml`
- [ ] `contracts/openapi/product-registry.openapi.yaml`
- [ ] `contracts/openapi/capability-registry.openapi.yaml`
- [ ] `contracts/openapi/pct.openapi.yaml`
- [ ] `contracts/openapi/oros.openapi.yaml`
- [ ] `contracts/openapi/pharmacy.openapi.yaml`
- [ ] `contracts/openapi/inpatient.openapi.yaml`
- [ ] `contracts/openapi/mushex.openapi.yaml`
- [ ] `contracts/openapi/costing.openapi.yaml`
- [ ] `contracts/openapi/document-service.openapi.yaml`
- [ ] `contracts/openapi/notification.openapi.yaml`
- [ ] `contracts/openapi/jobs.openapi.yaml`
- [ ] `contracts/openapi/pacs-adapter.openapi.yaml`
- [ ] `contracts/openapi/fhir-gateway.openapi.yaml`
- [ ] `contracts/asyncapi/impilo.events.asyncapi.yaml`
- [ ] `contracts/schemas/event-envelope.json`
- [ ] `contracts/schemas/audit-event.json`
- [ ] `contracts/schemas/errors.json`
- [ ] `contracts/schemas/obligations.json`
- [ ] `contracts/shared/headers.md`
- [ ] `contracts/shared/purpose-of-use.md`
- [ ] `contracts/shared/error-codes.md`
- [ ] `contracts/shared/id-standards.md`

## Phase 8 — Infrastructure

### Kubernetes
- [x] Namespaces (trust, registry, clinical, finance, ops, ui)
- [ ] `infra/k8s/networkpolicies/baseline.yaml`
- [ ] `infra/k8s/networkpolicies/allowlists.yaml`
- [ ] `infra/k8s/certs/root-ca.yaml`
- [ ] `infra/k8s/certs/issuers.yaml`
- [ ] `infra/helmfile.yaml`

### Observability
- [ ] `infra/observability/prometheus/` config
- [ ] `infra/observability/grafana/` dashboards
- [ ] `infra/observability/loki/` config
- [ ] `infra/observability/otel/` collector config

## Phase 9 — Scripts & Seed Data

- [x] `scripts/seed/init-databases.sql`
- [ ] `scripts/seed/tuso_seed.sql` — facility seed data
- [ ] `scripts/seed/zibo_seed.json` — terminology seed data
- [ ] `scripts/smoke/00_gateway.sh`
- [ ] `scripts/smoke/01_tshepo.sh`
- [ ] `scripts/smoke/02_registry_spine.sh`
- [ ] `scripts/smoke/03_clinical_plane.sh`
- [ ] `scripts/smoke/04_finance_plane.sh`
- [ ] `scripts/smoke/05_ui_shell.sh`

## Phase 10 — Documentation

- [ ] `docs/architecture/planes.md`
- [ ] `docs/architecture/contracts.md`
- [ ] `docs/architecture/threat-model.md`
- [ ] `docs/architecture/data-governance.md`
- [ ] `docs/architecture/offline-strategy.md`
- [ ] `docs/runbooks/deployment.md`
- [ ] `docs/runbooks/incident.md`
- [ ] `docs/runbooks/data-recovery.md`
