# Impilo vNext — Sovereign Service Registry Build Tracker

> Status key: `[x]` done · `[ ]` pending
> Each unchecked item = one atomic commit when implemented.
> **9 Sovereign Dual-Mode Services** form the national-grade core.

---

## Foundation — Shared Infrastructure

### Root Configuration
- [x] `.gitignore`, `.env.example`, `README.md`, `CLAUDE.md`
- [x] `docker-compose.yml` (Postgres, Redis, Kafka, MinIO, Keycloak, HAPI FHIR, Orthanc)
- [x] `scripts/seed/init-databases.sql` (20 per-service databases)
- [x] `services/pom.xml` (parent POM / BOM — Java 21, Spring Boot 3.3.6)
- [x] `ui/package.json` + `turbo.json` (npm workspace root)
- [x] `infra/k8s/namespaces/` (trust, registry, clinical, finance, ops, ui)

### shared-core Library
- [x] `pom.xml` (jar, not bootable)
- [x] `Argon2IdService.java` — OWASP 2024 compliant (m=19456, t=2, p=1)
- [x] `HmacService.java` — HMAC-SHA256 with pepper, constant-time compare
- [x] `ApiResponse.java`, `ApiError.java`, `PagedResponse.java` — standard envelope
- [x] `AccessMode.java`, `TrustContext.java`, `TrustContextHolder.java` — dual-mode protocol
- [x] `TrustContextFilter.java` — OncePerRequestFilter, detects INTERNAL/EXTERNAL mode

### Envoy Gateway
- [x] `infra/envoy/envoy.yaml` (ext_authz HTTP + all service routes)
- [ ] Route split: `public.yaml`, `internal.yaml`, `imaging.yaml`
- [ ] Rate limiting configuration per service tier

---

## Sovereign Service 1 — TSHEPO (Identity & Trust)

> Port 8081 · Trust & Governance Plane
> The gatekeeper: every request flows through Envoy ext_authz → TSHEPO.

### Skeleton (complete)
- [x] `pom.xml`, `Dockerfile`, `application.yml`, `TshepoApplication.java`
- [x] `SecurityConfig.java`, `RateLimitConfig.java`
- [x] `TrustHeaders.java` (14 header constants — single source of truth)
- [x] `AuthorizeController.java` (Envoy ext_authz HTTP endpoint)
- [x] `PolicyEngine.java` (7-step evaluation + serialized audit chain)
- [x] `Decision.java`, `Obligations.java`, `PurposeOfUse.java`, `RiskScoring.java`
- [x] JPA entities + repositories (6 entities, 6 repos)
- [x] `AuditOutboxPublisher.java` (Kafka outbox poller)
- [x] Flyway V001–V004 (policy log, audit chain, consent, device risk + outbox)
- [x] Helm chart

### Remaining Controllers
- [ ] `StepUpController.java` — step-up authentication challenge/response
- [ ] `ConsentController.java` — CRUD consent directives
- [ ] `AuditController.java` — audit trail query endpoints
- [ ] `DeviceRiskController.java` — device profile management

### Integration
- [ ] Keycloak realm import script (`scripts/seed/keycloak-realm.json`)
- [ ] Redis session/rate-limit cache integration
- [ ] Kafka `tshepo.audit` topic producer tests

---

## Sovereign Service 2 — VITO (Client Registry)

> Port 8082 · Registry Spine Plane
> National client identity: Impilo ID issuance, HMAC alias, PII custodian.

### Skeleton
- [x] Flyway V001–V005 (client, aliases, dedup, provisional, ops)
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `VitoApplication.java`
- [ ] `SecurityConfig.java` (dual-mode: platform + external identity consumers)

### Controllers
- [ ] `ClientController.java` — client CRUD
- [ ] `IdIssuanceController.java` — Impilo ID issuance flow
- [ ] `RecoveryController.java` — alias recovery/rotation
- [ ] `DedupController.java` — dedup case management
- [ ] `OfflineController.java` — provisional ID handling
- [ ] `OpsConsoleController.java` — ops overrides

### Core Services
- [ ] `ImpiloIdAliasService.java` — HMAC lookup_hash + Argon2id verifier (uses shared-core)
- [ ] `IdentityProofingService.java` — proofing workflow
- [ ] `DedupMatchingService.java` — duplicate detection
- [ ] `ProvisionalIdService.java` — offline provisional IDs
- [ ] `MergeService.java` — record merge/unmerge
- [ ] JPA entities + repositories
- [ ] `IdentityOutboxPublisher.java`

---

## Sovereign Service 3 — VARAPI (Provider Registry)

> Port 8083 · Registry Spine Plane
> Practitioner identity, credentialing, council sync, privileging.

### Skeleton
- [x] Flyway V001
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `VarapiApplication.java`
- [ ] `SecurityConfig.java` (dual-mode: platform + external council systems)

### Controllers
- [ ] `ProviderController.java`
- [ ] `CouncilController.java`
- [ ] `CredentialingController.java`
- [ ] `CertificateController.java`
- [ ] `CpdController.java`
- [ ] `OpsConsoleController.java`

### Core Services
- [ ] `ProviderIdService.java`, `CouncilSyncService.java`, `PrivilegingService.java`
- [ ] `PractitionerRoleMapper.java`, `BiometricBindingService.java`
- [ ] JPA entities + repositories
- [ ] Additional migrations V002–V005

---

## Sovereign Service 4 — TUSO (Facility Registry)

> Port 8084 · Registry Spine Plane
> Facility hierarchy, workspaces, capability registry, control tower.

### Skeleton
- [x] Flyway V001
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `TusoApplication.java`
- [ ] `SecurityConfig.java` (dual-mode: platform + external facility consumers)

### Controllers
- [ ] `FacilityController.java`, `WorkspaceController.java`, `ResourceController.java`
- [ ] `RosterController.java`, `ControlTowerController.java`
- [ ] `ConfigController.java`, `OpsOverrideController.java`, `GofrSyncController.java`

### Core Services
- [ ] `CapabilityService.java`, `ResourceCalendarService.java`
- [ ] `ControlTowerRulesEngine.java`, `TelemetryIngestService.java`
- [ ] JPA entities + repositories
- [ ] Additional migrations V002–V006

---

## Sovereign Service 5 — MSIKA (Products & Services Registry)

> Port 8086 · Registry Spine Plane
> National tariff engine, product catalog, service catalog. MUSheX and EHR are strict consumers.

### Skeleton (complete)
- [x] `pom.xml`, `Dockerfile`, `application.yml`, `MsikaApplication.java`
- [x] `SecurityConfig.java` (dual-mode with TrustContextFilter)
- [x] `TariffController.java` (GET: both modes, POST: INTERNAL only)
- [x] Flyway V001 (product_catalog, tariff_entry, service_catalog, event_outbox)
- [x] Helm chart

### Remaining
- [ ] `ProductController.java` — product catalog CRUD
- [ ] `ServiceCatalogController.java` — service catalog CRUD
- [ ] `ProductService.java`, `TariffService.java`, `ServiceCatalogService.java`
- [ ] JPA entities + repositories
- [ ] `MsikaOutboxPublisher.java`

---

## Sovereign Service 6 — ZIBO (Terminology)

> Port 8085 · Registry Spine Plane
> Code systems, value sets, validation, terminology governance.

### Skeleton
- [x] Flyway V001
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `ZiboApplication.java`
- [ ] `SecurityConfig.java` (dual-mode: platform + external terminology consumers)

### Controllers
- [ ] `CodeSystemController.java`, `ValueSetController.java`
- [ ] `ValidationController.java`, `GovernanceController.java`

### Core Services
- [ ] `TerminologyStore.java`, `Validator.java`, `VersioningService.java`
- [ ] JPA entities + repositories
- [ ] Additional migration V002 (codesystems/valuesets)
- [ ] Seed data: `scripts/seed/zibo_seed.json`

---

## Sovereign Service 7 — BUTANO (Shared Health Record)

> Port 8090 · Clinical Execution Plane
> HAPI FHIR server — CPID-only, zero PII. PII stays in VITO.

### Configuration
- [x] Helm chart
- [ ] `Dockerfile` (HAPI FHIR 7.4 base image)
- [ ] `config/hapi.properties`
- [ ] `config/access-policies.md`

### FHIR Gateway Service
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `FhirGatewayApplication.java`
- [ ] `BundleController.java`, `QueryController.java`
- [ ] `TokenToCpidResolver.java`, `ZiboValidationInterceptor.java`, `ConsentEnforcementHook.java`

---

## Sovereign Service 8 — UBOMI (CRVS Interface)

> Port 8087 · Registry Spine Plane
> Birth/death notification, vital event verification, civil registry interop.

### Skeleton (complete)
- [x] `pom.xml`, `Dockerfile`, `application.yml`, `UbomiApplication.java`
- [x] `SecurityConfig.java` (dual-mode with TrustContextFilter)
- [x] `BirthNotificationController.java` (submit + approve, dual-mode)
- [x] `DeathNotificationController.java` (submit + certify, dual-mode)
- [x] `VerificationController.java` (vital event verification, both modes)
- [x] Flyway V001 (birth_notification, death_notification, verification_log, event_outbox)
- [x] Helm chart

### Remaining
- [ ] `BirthNotificationService.java`, `DeathNotificationService.java`
- [ ] `VerificationService.java` — civil registry interop client
- [ ] `CrvsOutboxPublisher.java` — BIRTH_REGISTERED / DEATH_REGISTERED events
- [ ] JPA entities + repositories
- [ ] VITO integration: publish events so VITO can issue newborn IDs / flag deceased

---

## Sovereign Service 9 — MUSheX (Payments)

> Port 8088 · Finance Plane
> Payment orchestration, claims switching, wallet, reconciliation.

### Skeleton
- [x] Flyway V001
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `MushexApplication.java`
- [ ] `SecurityConfig.java` (dual-mode: platform + external payment providers)

### Controllers
- [ ] `PaymentIntentController.java`, `PaymentCallbackController.java`
- [ ] `ClaimsSwitchController.java`, `WalletController.java`

### Core Services
- [ ] `IdempotencyService.java`, `ReconciliationJobs.java`
- [ ] JPA entities + repositories

---

## Clinical Execution Plane (non-sovereign)

### PCT Service (Patient Care Tracker) — Port 8088
- [x] Flyway V001, Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `PctApplication.java`
- [ ] Controllers: Arrival, Queue, Encounter, Disposition, Timeline, Metrics
- [ ] Core: EncounterStateMachine, QueueAssignmentEngine, HandoffService, BedSnapshotService
- [ ] Additional migrations V002–V004

### OROS Service (Orders & Results) — Port 8089
- [x] Flyway V001, Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `OrosApplication.java`
- [ ] Controllers: Orders, Worklist, Results, Acknowledgement, ImagingHook
- [ ] Core: OrderStateMachine, WorklistBuilder, AdapterRouter, ReconciliationService
- [ ] Additional migrations V002–V004

### Pharmacy Service
- [x] Flyway V001, Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `PharmacyApplication.java`
- [ ] Controllers: Dispense, Substitution, Backorder, Pickup
- [ ] Core: PartialFillEngine, BarcodeService, BillingHook, InventoryHook

### Inpatient Service
- [x] Flyway V001, Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `InpatientApplication.java`
- [ ] Controllers: Admission, Ward, DeathDischarge
- [ ] Core: BedManager, DischargeWorkflow

---

## Finance Plane (non-sovereign)

### Costing Engine Service
- [x] Flyway V001, Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `CostingApplication.java`
- [ ] Controllers: Estimate, Methodology
- [ ] Core: CostModelEngine, RuleApplicationEngine, ChargeSheetEngine

---

## Integration / Ops Plane

### Inventory Service
- [x] Flyway V001, Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `InventoryApplication.java`
- [ ] Controllers: Stock, StockCount, Wastage, Returns, Barcode, ElmisAdapter
- [ ] Core: FefopickEngine, CapabilityRouter, ShadowQueueService

### Document Service
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `DocumentApplication.java`
- [ ] Core: MinioClientFacade, PdfGenerator, SignatureService

### PACS Adapter Service
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `PacsAdapterApplication.java`
- [ ] Core: StudyCorrelationEngine, OrthancProxy, ButanoPushService

### Notification Service
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `NotificationApplication.java`

### Jobs Service
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `JobsApplication.java`

### Integration Hub
- [x] Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `IntegrationHubApplication.java`
- [ ] Adapters: eLMIS, LIMS, DHIS2

### Offline Sync Service
- [x] Flyway V001, Helm chart
- [ ] `pom.xml`, `Dockerfile`, `application.yml`, `OfflineSyncApplication.java`

---

## Experience Plane (UI)

### One UI Shell — Port 3000
- [x] `package.json`, `next.config.js`, `tsconfig.json`, `tailwind.config.ts`
- [x] `apiClient.ts` (14 trust headers injected), `contracts.ts`
- [x] Hooks: `useSession.ts`, `useContext.ts`, `usePolicyDecision.ts`
- [ ] `src/app/layout.tsx` — root layout with shell, nav, context bar
- [ ] `src/components/shell/AppShell.tsx`
- [ ] `src/components/context/ContextBar.tsx`, `StepUpModal.tsx`
- [ ] Route stubs: WORK, CONTROL, My Professional, My Life

### Shared UI Library
- [x] `package.json`, `tokens.css`, `index.ts`
- [ ] Base components: Button, Card, DataTable, Badge, StatusIndicator

### Ops Console — Port 3001
- [x] `package.json`
- [ ] Full Next.js config + route stubs (tshepo, vito, varapi, tuso, zibo, oros, pct, mushex)

### EHR App — Port 3002
- [x] `package.json`
- [ ] Full Next.js config + route stubs (dashboard, patient, encounter, orders, results)

### Portal App — Port 3003
- [x] `package.json`
- [ ] Full Next.js config + route stubs (health-id, timeline, wallet, consent, recovery)

---

## API Contracts

- [ ] OpenAPI specs for all 9 sovereign services
- [ ] AsyncAPI spec: `impilo.events.asyncapi.yaml`
- [ ] Shared schemas: event-envelope, audit-event, errors, obligations
- [ ] Shared docs: headers, purpose-of-use, error-codes, id-standards

## Infrastructure

- [ ] Network policies (baseline + allowlists)
- [ ] TLS certificates (root CA + issuers)
- [ ] Helmfile for orchestrated deployment
- [ ] Observability: Prometheus, Grafana dashboards, Loki, OTel collector

## Scripts & Seed Data

- [x] `scripts/seed/init-databases.sql`
- [ ] `scripts/seed/tuso_seed.sql`, `scripts/seed/zibo_seed.json`
- [ ] Keycloak realm import script
- [ ] Smoke tests for each plane
