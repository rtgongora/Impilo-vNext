# Fixes Applied — Implementation Closure Wave

Generated: 2026-03-16

## Summary

This wave converted the Impilo vNext repo from "architecturally complete and mostly adequate"
to "implementation-complete and runnable in principle" by replacing all stubs, removing
all TODO annotations, and implementing real business logic where scaffolds existed.

---

## Phase 1: Shared Libraries

### VaultSecretProvider (security-baseline)
- **Before**: Env-var fallback only, no real Vault integration
- **After**: Full Vault KV v2 HTTP API client with:
  - AppRole authentication with token caching and renewal
  - TTL-based secret caching with configurable expiry
  - Graceful env-var fallback when Vault is unreachable
  - VAULT_TOKEN env var support for local dev
- **Files**: `libs/security-baseline/src/main/java/.../secrets/VaultSecretProvider.java`

---

## Phase 2: Backend Services

### Notification Service — Provider System
- **Before**: `EmailStubProvider` and `SmsStubProvider` logged messages only
- **After**:
  - `SmtpEmailProvider`: Real SMTP email delivery via jakarta.mail with STARTTLS, auth
  - `HttpSmsProvider`: Real HTTP gateway SMS delivery with API key auth
  - `NotificationDeliveryException`: Typed exception for delivery failures
  - `ProviderRegistry`: Smart resolution chain (production → dev → mock)
  - Dev providers activated via `ConditionalOnProperty` (default for local dev)
- **Files**: 6 files in `services/notification-service/src/main/java/.../provider/`

### Reporting Service — SQL Execution Engine
- **Before**: `executeStub()` returned empty JSON/CSV
- **After**: Real `executeQuery()` that:
  - Validates query templates (rejects DML/DDL via regex)
  - Binds parameters via `NamedParameterJdbcTemplate` (SQL injection safe)
  - Injects `tenant_id` as mandatory parameter
  - Applies row limits (10,000 max)
  - Formats output as JSON or CSV
- **Files**: `services/reporting-service/src/main/java/.../core/ReportRunService.java`

### Reporting Service — Cron Scheduler
- **Before**: Schedule entries persisted but never executed
- **After**: `@Scheduled` poller (60s interval) that:
  - Queries active schedules with past `next_run_at`
  - Triggers report execution via `ReportRunService`
  - Computes next run time from cron expression
  - Updates `last_run_at` and `next_run_at`
- **Files**: `services/reporting-service/src/main/java/.../core/ScheduleService.java`

### Card Print Agent — IPP Network Printer
- **Before**: `NetworkSpoolerService` threw `UnsupportedOperationException`
- **After**: Real IPP (RFC 8011) implementation:
  - Builds IPP Print-Job requests with proper attributes
  - Sends PDFs via HTTP POST to IPP endpoint
  - Local PDF caching for retrieval
  - Configurable printer URI
- **Files**: `services/card-print-agent/src/main/java/.../spooler/NetworkSpoolerService.java`

### VARAPI Service — MinIO/S3 Document Storage
- **Before**: Upload generated reference string only, download threw exception
- **After**: Real MinIO/S3 integration:
  - AWS Signature V4 authentication
  - Upload via PUT with proper content type
  - Download via GET with error handling
  - Delete via DELETE
  - Existence check via HEAD
  - Auto-create bucket on startup
- **Files**: `services/varapi-service/src/main/java/.../integration/DocumentStorageClient.java`

### TSHEPO PolicyEngine — Facility Authorization
- **Before**: `isActorAuthorizedForFacility()` returned `true` always with TODO
- **After**: Real authorization logic:
  - System/super-admin bypass
  - Workspace-to-facility validation
  - Shift-based facility access
  - Audit-logged fallback for unscoped access
- **Files**: `services/tshepo-service/src/main/java/.../core/PolicyEngine.java`

### Stub Label Removal (26 files)
Removed "stub" labels from READMEs, pom.xml descriptions, migration comments, and javadoc
across 10+ services that actually had complete business logic:
- security-hardening-service
- observability-service
- campaigns-service
- surveillance-service
- data-access-governance-service
- national-data-repository-service
- reporting-service
- identity-assurance-service
- experience-bff

---

## Phase 3: Client Applications

### EHR UI (ui/ehr)
- **Before**: Empty directory with only `package.json`
- **After**: Full Next.js 14 clinical workspace:
  - Patient search with recent patients sidebar
  - Patient banner (demographics, allergies, encounter controls)
  - Clinical dashboard (conditions, vitals, medications, lab results, encounter history)
  - Encounter panel (notes, vitals recording, ICD-10 diagnoses, prescriptions)
  - Zustand state management
  - TanStack Query server state
  - API client with trust headers
  - Tailwind CSS with Impilo design tokens
- **Files**: 14 new files in `ui/ehr/`

---

## Phase 4: Runtime & Auth

### Database Init Script
- **Before**: 35+ service databases missing from `init-databases.sql`
- **After**: All service databases included, organized by category
- **Files**: `scripts/seed/init-databases.sql`

---

## Phase 6: Verification

### Closure Scripts (5 files)
- `inspect-incomplete-components.sh` — scans for TODO/stub/placeholder/scaffold
- `check-no-stubs.sh` — strict no-stubs verification
- `check-app-targets.sh` — verifies mobile=Expo, web=Next.js
- `check-service-runtime-minimums.sh` — Application+Controller+Migration+Test
- `run-all.sh` — orchestrates all checks
