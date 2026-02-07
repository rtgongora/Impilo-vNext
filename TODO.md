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
- [x] `infra/envoy/vito-routes.yaml` — public/internal route split with rate limiting
- [ ] Route split: `imaging.yaml`
- [ ] Rate limiting configuration for remaining services

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
> WHO-compliant Identity Node: Impilo ID issuance, HMAC alias, PII custodian,
> SMART Card management, health payments wallet, probabilistic matching.

### Skeleton (complete)
- [x] `pom.xml` (HAPI FHIR R4, Bouncy Castle, Nimbus JOSE, Redis, Kafka)
- [x] `Dockerfile`, `application.yml`, `VitoApplication.java`
- [x] `SecurityConfig.java` (dual-mode: platform + external identity consumers)
- [x] `VitoProperties.java` — @ConfigurationProperties for Argon2id/HMAC/DID/Card/Matching
- [x] `VitoServiceConfig.java` — wires Argon2id (m=64MB, t=3, p=4) + HmacService
- [x] Flyway V001–V005 (client, aliases, dedup, provisional, ops)
- [x] Flyway V006–V010 (smart_card, wallet, biometric, matching, health_summary)
- [x] Helm chart (port 8082, resources bumped for Argon2id)

### Domain Model (complete)
- [x] `SovereignIdGenerator.java` — W3C DID `did:impilo:[sha256]` (Black Box, stateless)
- [x] Enums: `IdentityStatus`, `CardStatus`, `RevocationReason`, `BiometricModality`, `MatchDisposition`
- [x] 10 JPA entities: Client, IdentityAlias, SmartCard, Wallet, WalletJournal, BiometricTemplate, MatchResult, DedupCase, ProvisionalId, EventOutbox
- [x] 10 Repositories (with pessimistic locking on wallet)

### Core Services (complete)
- [x] `ImpiloIdAliasService.java` — HMAC lookup_hash + Argon2id verifier (VITO-specific params)
- [x] `IdentityService.java` — WHO SMART L3/L4 lifecycle (PROVISIONAL→VERIFIED→ACTIVE→INACTIVE→DECEASED→MERGED)
- [x] `BiometricService.java` — ITU-T X.1081, SHA-256 integrity, quality scoring
- [x] `MatchingEngine.java` — Jaro-Winkler weighted scoring, auto-link ≥0.95, manual ≥0.70
- [x] `CardLifecycleService.java` — SMART Card state machine + one-active-card enforcement
- [x] `WalletService.java` — double-entry ledger, pessimistic locking, offline JWS signatures
- [x] `RecoveryService.java` — Secure Handover (revoke → new card → transfer wallet)
- [x] `HealthSummaryService.java` — JWS compact serialization (HS256, upgradable)
- [x] `OpenCrAdapter.java` — FHIR Patient/$match client with graceful degradation
- [x] `StandaloneRegistryService.java` — provisional ID reconciliation, dedup cases
- [x] `VitoOutboxPublisher.java` — scheduled Kafka publisher (vito.identity, vito.cards, vito.wallet)

### Controllers (complete)
- [x] `ClientController.java` — client CRUD (EXTERNAL reads, INTERNAL writes)
- [x] `IdIssuanceController.java` — register, resolve (anti-enumeration), rotate
- [x] `BiometricController.java` — enroll, query (INTERNAL only)
- [x] `MatchController.java` — FHIR $match, pending queue, resolve
- [x] `CardController.java` — full lifecycle (request/print/activate/inactivate/revoke)
- [x] `WalletController.java` — create/topup/pay/offline, balance, journal
- [x] `RecoveryController.java` — handover, SHS create/verify
- [x] `RegistryAdminController.java` — registry mode, provisional IDs, dedup, OpenCR $match

### Impilo ID Format (complete)
- [x] `ImpiloIdFormat.java` — 9 digits + check letter (#########X), weighted positional sum mod 24
- [x] Unit tests: 12 tests including @RepeatedTest(100), transposition detection

### Flyway V011–V015 (complete)
- [x] `V011__alter_clients_add_jsonb_and_crid.sql` — CRID UUID, impilo_id, demographics/contacts/address JSONB
- [x] `V012__proofing_events.sql` — proofing_event table (method, assurance_level, artifact_refs)
- [x] `V013__issuance_requests.sql` — issuance_request table, one-active-issuance partial unique index
- [x] `V014__delegated_pickup.sql` — delegated_pickup table (HMAC token hash, Argon2id OTP hash, max attempts)
- [x] `V015__dedup_actions_and_merge_history.sql` — dedup_action audit trail, merge_history provenance

### Extended Entities & Repositories (complete)
- [x] Enums: IssuanceType, IssuanceChannel, IssuanceState, PickupStatus, ProofingMethod
- [x] Entities: ProofingEventEntity, IssuanceRequestEntity, DelegatedPickupEntity, DedupActionEntity, MergeHistoryEntity
- [x] Updated ClientEntity (crid UUID, impiloId, demographics/contacts/address JSONB)
- [x] Updated DedupCaseEntity (risk, reasons JSONB, assignedTo)
- [x] 5 new repositories with paged query support

### Extended Core Services (complete)
- [x] `IssuanceStateMachineService.java` — SUBMITTED→PROOFING→APPROVED→ISSUED→DELIVERED (+REJECTED/EXPIRED)
- [x] `ProofingService.java` — proofing event tracking (5 methods, assurance levels 0–3)
- [x] `DelegatedPickupService.java` — OTP/QR delegation with max-attempts + auto-revocation
- [x] `MergeService.java` — record merge/unmerge with full provenance and alias transfer
- [x] `QrSigningService.java` — Ed25519 JWS compact tokens (sign/verify/public key)
- [x] `PdfGeneratorService.java` — Emergency Capsule + Pickup Slip (A5 PDFBox)

### Extended Controllers (complete)
- [x] `PortalController.java` — 7 citizen-facing endpoints with anti-enumeration responses
- [x] `IssuanceController.java` — internal issuance workflow (submit/proofing/approve/issue/deliver/reject)
- [x] `PrintJobController.java` — print job intake with step-up requirement
- [x] `QrResolverController.java` — QR token resolution + public key exposure
- [x] `SlipController.java` — PDF endpoints (emergency-capsule.pdf, pickup.pdf)
- [x] `InternalSearchController.java` — PII-masked client search + full record access
- [x] `DedupController.java` — score, cases queue, merge, unmerge
- [x] `AuditController.java` — event outbox read-only feed for operators

### Security (complete)
- [x] `TrustHeaderFilter.java` — rejects missing mandatory headers (x-tenant-id, x-correlation-id, x-actor-id, x-actor-type)
- [x] `StepUpRequired.java` + `StepUpAspect.java` — AOP step-up enforcement for sensitive operations

### Tests (complete)
- [x] `ImpiloIdFormatTest.java` — 12 tests including repeated validation and transposition detection
- [x] `ImpiloIdAliasServiceTest.java` — 5 Mockito tests verifying no-plaintext storage
- [x] `IssuanceStateMachineTest.java` — 7 tests for state machine guards and transitions
- [x] `QrSigningServiceTest.java` — 7 tests for Ed25519 sign/verify/expiry/tamper detection
- [x] `PortalControllerSecurityTest.java` — 2 anti-enumeration contract tests

### card-print-agent (complete)
- [x] `CardPrintAgentApplication.java`, `pom.xml`, `Dockerfile`, `application.yml`
- [x] `PrintJobListener.java` — Kafka listener on vito.print, generates CR-80 card PDFs
- [x] `CardPayloadGenerator.java` — 85.6mm×53.98mm card layout
- [x] `HealthController.java` — GET /api/status
- [x] Helm chart (port 8091, stateless, Kafka consumer group card-print-agent)

### Remaining
- [ ] Integration tests (registration → issuance → card → wallet → handover → recovery)
- [ ] MockMvc tests for portal anti-enumeration responses
- [ ] Kafka integration tests (outbox → publisher → consumer)

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
- [x] `apiClient.ts` (14 trust headers injected), `contracts.ts` (re-exports from shared-ui)
- [x] Hooks: `useSession.ts`, `useContext.ts`, `usePolicyDecision.ts`
- [ ] `src/app/layout.tsx` — root layout with shell, nav, context bar
- [ ] `src/components/shell/AppShell.tsx`
- [ ] `src/components/context/ContextBar.tsx`, `StepUpModal.tsx`
- [ ] Route stubs: WORK, CONTROL, My Professional, My Life

### Shared UI Library
- [x] `package.json`, `tokens.css`, `index.ts`
- [x] Trust contracts: `lib/contracts.ts` (TRUST_HEADERS, ApiEnvelope, PagedResponse — shared across all apps)
- [x] Base components: Button, Card, DataTable, Badge, StatusIndicator

### Ops Console — Port 3001
- [x] `package.json`
- [x] `apiClient.ts` — trust-aware API client (injects all trust headers via Zustand session store)
- [x] `sessionStore.ts` — operator session & work context store
- [x] VITO Dashboard: registry overview, match-queue, cards management, config (4 pages)
- [x] `vitoApi.ts` — typed VITO API client (15 types, 20+ API functions)
- [x] Client search page — PII-masked results with status badges
- [x] Dedup case review — score display, merge/reject actions
- [x] Issuance queue — state-based tab filters, workflow actions
- [x] Provisional reconciliation — verify button per client
- [x] Audit event viewer — expandable JSON payloads, type filter
- [ ] Remaining route stubs (tshepo, varapi, tuso, zibo, oros, pct, mushex)

### EHR App — Port 3002
- [x] `package.json`
- [ ] Full Next.js config + route stubs (dashboard, patient, encounter, orders, results)

### Portal App — Port 3003
- [x] `package.json`, `next.config.js`, `tailwind.config.ts`, `tsconfig.json`
- [x] `portalApi.ts` — trust-aware API client (CITIZEN actor, EXTERNAL mode, step-up token support)
- [x] Citizen layout with navigation (Request ID, Recovery, My QR, Pickup)
- [x] Request ID page — form with type/name/DOB/sex, anti-enumeration response
- [x] Recovery page — two-step flow with step-up handling
- [x] My QR page — signed Health ID QR display with registration check
- [x] Delegated Pickup page — create (step-up) + redeem tabs
- [x] Helm chart (vito-web, port 3003, VITO_API_URL env)
- [ ] Route stubs: timeline, wallet, consent

---

## API Contracts

- [x] `contracts/openapi/vito.openapi.yaml` — 56 endpoints, 14 tags, shared schemas
- [ ] OpenAPI specs for remaining 8 sovereign services
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
