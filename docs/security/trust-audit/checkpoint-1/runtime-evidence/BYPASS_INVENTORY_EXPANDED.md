# Expanded OAuth / internal bypass inventory — Checkpoint 1 closure

**Rule:** No bypass is removed or narrowed in this checkpoint.
**Consumer evidence:** Where callers are not proven, retain `PARTIAL` or `INSUFFICIENT_EVIDENCE`.

## Estate flag — IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS

| Field | Value |
|---|---|
| Flag | `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` (Spring `impilo.security.disable-oauth-for-tests`) |
| Code path (representative) | Service `SecurityConfig` `@Value` / `@ConditionalOnProperty` selecting a `permitAll` filter chain |
| Endpoint scope | Typically **all** HTTP endpoints on the service (resource-server disabled) |
| Operational purpose | Preview/dev unlock so BFF and east-west callers can reach services without per-service OAuth audiences |
| Known callers | experience-bff (primary), peer domain services, Jobs/CronJobs — **per-service caller matrices remain PARTIAL** |
| Replacement requirement | Unique workload identity + audience-restricted credentials + fail-closed resource-server per cohort |
| Removal blocker | Shared `impilo-backend` client, missing per-service audiences, Envoy/ext_authz off-path, incomplete consumer proof |

### Per-service rows (96 disabled, 2 enabled)

| Service | Flag value | Code path class | Endpoint scope | Purpose | Known callers | Owner | Replacement | Removal blocker | Consumer evidence |
|---|---|---|---|---|---|---|---|---|---|
| `abis-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `ai-model-registry-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `analytics-pipeline-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `asset-registry-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `audit-ledger-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `booking-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `butano-fhir` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `butano-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `campaigns-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `card-print-agent` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `channels-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `clinical-knowledge-platform-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `community-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `connector-fhir-adapter` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `costing-engine-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `coverage-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `credential-verification-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `daidzai-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `data-access-governance-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `data-governance-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `data-ingestion-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `data-pipeline-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `data-warehouse-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `developer-portal-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `dispatch-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `document-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `fhir-gateway-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `forms-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `general-ledger-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `guidance-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `hr-payroll-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `identity-assurance-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `indawo-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `inpatient-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `integration-hub` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `inventory-elmis-adapter` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `inventory-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `iot-ingestion-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `jobs-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `khuluma-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `landela-adapter-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `learning-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `live-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `llm-orchestration-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `madi-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `msika-apps-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `msika-flow-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `msika-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `mushe-wallet-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `mushex-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `mvumo-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `national-data-repository-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `ndila-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `ndr-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `nhume-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `notification-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `observability-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `offline-edge-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `offline-sync-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `organization-registry-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `oros-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `pacs-adapter-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `participation-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `patient-safety-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `pct-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `pharmacy-elmis-adapter` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `pharmacy-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `procurement-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `product-registry-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `referral-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `reporting-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `rito-quality-safety-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `rtc-gateway-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `rules-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `scheduling-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `schema-registry-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `search-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `security-hardening-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `share-slip-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `simba-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `support-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `surveillance-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `telemonitoring-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `tshepo-consent-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `tshepo-identity-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `tshepo-keys-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `tshepo-offline-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `tuso-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `ubomi-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `varapi-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `vashandi-workforce-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `vito-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `wellness-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `workflow-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `workforce-governance-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `zibo-service` | `true` (OAuth disabled) | SecurityConfig permitAll / disable-oauth-for-tests | all HTTP (typical) | preview unlock | BFF + peers (unenumerated) | UNKNOWN (registry owner_team not re-resolved per service in this closure) | workload identity + OAuth RS | cohort migration + consumer proof | PARTIAL |
| `tshepo-audit-service` | `false` (OAuth enabled) | real JWT resource-server chain | protected API surface | trust-plane services | BFF / Envoy(intended) / peers | trust plane | keep enabled; expand audiences | N/A (not a bypass) | SOURCE+RUNTIME |
| `tshepo-authz-service` | `false` (OAuth enabled) | real JWT resource-server chain | protected API surface | trust-plane services | BFF / Envoy(intended) / peers | trust plane | keep enabled; expand audiences | N/A (not a bypass) | SOURCE+RUNTIME |

## Additional explicit bypass / permit-all / anonymous flags

### ai-model-registry-service

| Field | Value |
|---|---|
| Flag | `AIR_SECURITY_ALLOW_INSECURE_PERMIT_ALL=true` |
| Code path | `services/ai-model-registry-service/.../config/SecurityConfig.java` |
| Endpoint scope | permit-all when flag true |
| Purpose | AI registry preview unlock |
| Known callers | INSUFFICIENT_EVIDENCE |
| Owner | UNKNOWN |
| Replacement | OAuth RS + workload identity |
| Removal blocker | unknown consumers + estate OAuth disable also true |
| Consumer evidence | INSUFFICIENT_EVIDENCE |

### llm-orchestration-service

| Field | Value |
|---|---|
| Flag | `LLM_SECURITY_ALLOW_INSECURE_PERMIT_ALL=true` |
| Code path | `services/llm-orchestration-service/.../config/SecurityConfig.java` |
| Endpoint scope | permit-all when flag true |
| Purpose | LLM orchestration preview unlock |
| Known callers | BFF/Nompilo assist paths (PARTIAL) |
| Owner | UNKNOWN |
| Replacement | OAuth RS + scoped roles |
| Removal blocker | assist fallback callers not fully inventoried |
| Consumer evidence | PARTIAL |

### iot-ingestion-service

| Field | Value |
|---|---|
| Flag | `IMPILO_SECURITY_MODE=permit-all` |
| Code path | `services/iot-ingestion-service/.../config/SecurityConfig.java` |
| Endpoint scope | permit-all mode |
| Purpose | IoT ingest preview |
| Known callers | INSUFFICIENT_EVIDENCE |
| Owner | UNKNOWN |
| Replacement | device/workload credentials |
| Removal blocker | device identity not ready |
| Consumer evidence | INSUFFICIENT_EVIDENCE |

### ndila-service

| Field | Value |
|---|---|
| Flag | `NDILA_ALLOW_ANONYMOUS=true` |
| Code path | `services/ndila-service/.../config/SecurityConfig.java` (+ `application.yml`) |
| Endpoint scope | anonymous-allowed routes when flag true |
| Purpose | Ndila public/anonymous surfaces |
| Known callers | public/UI paths (PARTIAL) |
| Owner | UNKNOWN |
| Replacement | progressive trust / public-lane policy |
| Removal blocker | public-lane requirements |
| Consumer evidence | PARTIAL |

### mushex-service

| Field | Value |
|---|---|
| Flag | `MUSHEX_SANDBOX_BYPASS_CREDENTIAL_CHECK=true` |
| Code path | `MushexSecurityStartupValidator` / `PaymentIntentService` |
| Endpoint scope | credential check bypass in sandbox |
| Purpose | payments sandbox |
| Known callers | MUSHEX sandbox flows (PARTIAL) |
| Owner | UNKNOWN |
| Replacement | real credential verification |
| Removal blocker | sandbox test dependency |
| Consumer evidence | PARTIAL |

### dispatch-service — label vs code discrepancy

| Field | Value |
|---|---|
| Reported env | `DISPATCH_SECURITY_OAUTH2_ENABLED=false` in generated Helm values |
| Code binding found | **No matching Java property binding located** in this closure pass |
| Actual bypass mechanism | Still covered by estate `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` |
| Classification | PARTIAL / label drift — do not treat named flag as proven code path |
| Consumer evidence | INSUFFICIENT_EVIDENCE for named flag |

### product-registry-service — label vs code discrepancy

| Field | Value |
|---|---|
| Reported env | `IMPILO_SECURITY_ALLOW_ANONYMOUS=true` in generated Helm values |
| Code binding found | `SecurityConfig` reads `impilo.security.disable-oauth-for-tests` and JWT issuer URI; anonymous flag binding **not found** |
| Actual bypass mechanism | Estate OAuth-disable + **empty JWT issuer URI** |
| Classification | PARTIAL / label drift |
| Consumer evidence | PARTIAL |

### experience-bff — hardening (not a bypass)

| Field | Value |
|---|---|
| Flags | `IMPILO_SECURITY_ALLOW_ANONYMOUS=false`, `AUTH_FALLBACK_ENABLED=false` |
| Classification | HARDENING — retain; not a removal candidate |

## OAuth-enabled exceptions

| Service | Notes |
|---|---|
| `tshepo-authz-service` | OAuth enabled; still off live ingress PEP path |
| `tshepo-audit-service` | OAuth enabled |

## Gate statement

Every bypass above still lacks a complete legitimate-consumer proof for removal. Estate OAuth expansion, Envoy cutover, and bypass retirement remain **blocked**.
