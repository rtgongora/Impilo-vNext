# Product Truth — Full Gap Register

> Master tracker for the full gap-discovery pass (2026-06-23). The automated
> product-truth **gate** enforces only the regex-detectable subset (`gapBaseline`
> in `reports/product/product-truth-baseline.json`, currently 6). This register
> tracks the **complete** set, including semantic security/trust/clinical/legal
> defects that compile, persist, and return success — which no scanner can detect.
>
> **Methodology:** four-way structured read-only sweep (backend a–l, backend m–z,
> experience-bff + mobile, one-ui-shell) + widened deterministic detectors. Each row
> has a stable ID, file:line, category, severity, a **decision**
> (fix / honest-degrade / defer-with-marker / reject) and the **roadmap block**
> that closes it (see the remediation roadmap plan).
>
> **Honest counts:** deterministic gate = **6**. Full register = **~59 findings (G001–G055)**:
> ~15 blocker/critical, ~14 high, ~20 medium, ~9 low. The gap between 6 and ~58 is
> the finding — the scanner is honest about its *method* but incomplete in *coverage*.

## CRITICAL / BLOCKER — security · trust · clinical · legal

| ID | Finding | file:line | Cat | Decision | Block |
|----|---------|-----------|-----|----------|-------|
| G001 | ~~Step-up accepts **any non-blank code**~~ **DEFECT FIXED + UNIT-PROVEN (B1, `276da9db`):** mode-dispatched verification, no accept-any path; `StepUpServiceTest` 14/14. **NOT runtime-proven** (`@SpringBootTest` needs Postgres). **Live residuals:** only SUPERVISOR_APPROVAL is live-capable and it has **no controller endpoint yet**; TOTP fails closed until an enrolment-secret store is wired; SMS-OTP (ties G033) + biometric are `NOT_LIVE_CAPABLE` seams. Maturity: **unit-proven, not REAL_PROVEN; feature not operationally complete**. | `tshepo-authz-service/.../stepup/*`, `StepUpService.java` | unimpl/crypto | defect fixed; seams open | B1 |
| G002 | ~~Offline capability token **JWS signature never verified**~~ **DEFECT FIXED + UNIT-PROVEN (B2, `fefe6422`):** real Ed25519 JWS verification (alg-allowlist + kid + signature + exp/nbf) before any claim is trusted; `CapabilityTokenJwsVerifierTest` 6/6 + service 16/16. **NOT runtime-proven** (JWKS + repo mocked; GoldenContractIT needs Postgres). **Residuals:** verification fetches JWKS **online per verify** → **not yet truly offline** (needs cached/distributed JWKS); issuer/audience/scope claim checks **not** done (only alg/kid/sig/exp/nbf + DB tenant); no integration proof. Maturity: **unit-proven, not REAL_PROVEN**. | `tshepo-offline-service/.../CapabilityTokenJwsVerifier.java`, `CapabilityTokenService.java` | crypto | defect fixed; offline+claims open | B2 |
| G003 | DAGS permit-token HMAC key defaults to literal **"change-me-in-prod"** — forgeable if env unset | `data-access-governance-service/.../EnforcementService.java:21` | hardcoded/crypto | fix (fail-closed) | B3 |
| G004 | Identity-assurance **risk assessment is a client-trusted pass-through** | `identity-assurance-service/.../RiskAssessmentController.java:27`, `RiskAssessmentService.java:25` | unimpl | fix | C1 |
| G005 | Null defaults fabricate a verdict: omitted fields → score 0 / LOW / **ALLOW** | `identity-assurance-service/.../RiskAssessmentService.java:35` | hardcoded | fix | C1 |
| G006 | Identity-assurance **attestation client-trusted** — caller self-asserts `VERIFIED, confidence=1.0` | `identity-assurance-service/.../AttestationController.java:29`, `AttestationService.java:25` | unimpl | fix | C1 |
| G007 | FHIR gateway **never forwards** — returns SUCCESS, no HTTP call to BUTANO/HAPI; payload dropped | `fhir-gateway-service/.../GatewayForwardService.java:62`, `GatewayRouteController.java:81` | write-no-persist | fix | F1 |
| G008 | Payment `verifyWebhook` returns true for **any non-blank signature** (no HMAC) | `mushex-service/.../MobileMoneyAdapter.java:~63` (+ Card/Bank) | crypto | fix | E1 |
| G009 | BFF IdentityAssurance GET `/status` **fully fabricated** (no client injected) | `experience-bff/.../IdentityAssuranceController.java:69` | hardcoded | fix | C2 |
| G010 | BFF IdentityAssurance POST `/upgrade/request` mints fake id, **persists nothing** | `experience-bff/.../IdentityAssuranceController.java:168` | write-no-persist | fix | C2 |
| G011 | BFF TempIdReview queue **fabricated** | `experience-bff/.../TempIdReviewController.java:73` | hardcoded | fix | C2 |
| G012 | BFF TempIdReview approve/reject **persist nothing**, no VITO promotion | `experience-bff/.../TempIdReviewController.java:123` | write-no-persist | fix | C2 |
| G013 | Multi-tenant breach — inpatient clinical reads/writes hardcode `DEFAULT_TENANT`/`DEFAULT_FACILITY` | `inpatient-service/.../InpatientClinicalService.java:25`, `WardRoundService.java:21` | hardcoded | fix | D1 |
| G014 | Multi-tenant leak — dispatch snapshot JPQL has **no tenant filter** | `dispatch-service/.../DispatchJobRepository.java:23`, `SnapshotController.java:45` | hardcoded | fix | D2 |
| G015 | BFF GDPR account-deletion **persists nothing**; status always null | `experience-bff/.../AccountDeletionController.java:38` | write-no-persist | fix | G1 |

## HIGH

| ID | Finding | file:line | Cat | Decision | Block |
|----|---------|-----------|-----|----------|-------|
| G016 | Refund large-amount **step-up not enforced** (logs, proceeds to save + ledger) | `mushex-service/.../RefundService.java:108` | write-no-enforce | fix | E2 |
| G017 | Provider biometric match = exact SHA-256 equality; confidence hardcoded; "(placeholder matcher)" | `varapi-service/.../ProviderBiometricService.java:188` | crypto | fix | H3 |
| G018 | License certificate download throws `UnsupportedOperationException` on a live route → 500 | `varapi-service/.../LicenseService.java:186`, `PortalController.java:120` | unimpl | fix | H3 |
| G019 | Document signature provider defaults to **NOOP** — random sig, status SIGNED, no crypto | `document-service/.../NoopSignatureProvider.java:20`, `SignatureService.java:75`, `application.yml:64` | placeholder | fix (fail-closed default) | F2 |
| G020 | Document OCR provider defaults to **NOOP** — literal "OCR placeholder", job COMPLETED | `document-service/.../NoopOcrProvider.java:20`, `application.yml:58` | placeholder | fix (fail-closed default) | F2 |
| G021 | hr-payroll mutating routes have **no method-level authz** | `hr-payroll-service/.../InternalHrApi.java` | hardcoded | fix | H2 |
| G022 | Leave-request write doesn't decrement balance | `hr-payroll-service/.../InternalHrApi.java:97` | write-no-persist | fix | H2 |
| G023 | inventory-elmis-adapter is a **CRUD shell** — no eLMIS client/scheduler ever completes | `inventory-elmis-adapter/.../SyncStateService.java:42` | unimpl | fix or mark | H4 |
| G024 | BFF privacy-prefs PUT **echoes body, no persistence** (claims `privacy_display_preference` V39) | `experience-bff/.../PrivacyPreferencesController.java:40` | echo | fix | G2 |
| G025 | Clinical drug-interaction checker is a **hardcoded finite set** — can return false "no interactions" | `ui/one-ui-shell/.../MedscapeTools.tsx:35,251,262` | component-fixture | fix (real terminology) | F3 |
| G026 | BillingPanel financial fixtures; only Claims tab live, charges/invoices/payments/revenue have no live path | `ui/.../workspace-ops/BillingPanel.tsx:17,24,39,45,54` | component-fixture | fix or NOT_LIVE | H1 |
| G027 | HRShiftsPanel `ACTIVE_SHIFTS/LEAVE_REQUESTS/PENDING_HANDOVERS` fixtures, no live path | `ui/.../workspace-ops/HRShiftsPanel.tsx:29,35,41` | component-fixture | fix or NOT_LIVE | H1 |
| G028 | StockManagementPanel `PURCHASE_ORDERS/PENDING_RECEIPTS/TRANSFERS` fixtures drive a KPI | `ui/.../workspace-ops/StockManagementPanel.tsx:43,51,56` | component-fixture | fix or NOT_LIVE | H1 |
| G029 | AIDiagnosticAssistant **silently falls back to fabricated** differentials (orphaned/unmounted today) | `ui/.../ehr/AIDiagnosticAssistant.tsx:37,83` | component-fixture | fix or reject(delete) | F3/I |

## MEDIUM

| ID | Finding | file:line | Cat | Decision | Block |
|----|---------|-----------|-----|----------|-------|
| G030 | Wallet card crypto key = **SHA-256 of a key-reference string** (deterministic), not tshepo-keys | `mushe-wallet-service/.../CardHealthDataService.java:251` | crypto | fix | E3 |
| G031 | Wallet encounter event summary written as literal `"{}"` | `mushe-wallet-service/.../WalletEventConsumer.java:241` | placeholder | fix | I |
| G032 | SMART card public key stored as `"DEV-PLACEHOLDER-"+uuid` | `vito-service/.../CardLifecycleService.java:80` | placeholder/crypto | fix | I |
| G033 | SMS/EMAIL default to log-only stub providers — notifications silently dropped out-of-box | `notification-service/.../ProviderRegistry.java:40`, `SmsStubProvider.java:15` | placeholder | fix (config default) | I |
| G034 | Pickup slip returns hardcoded JSON instead of a PDF | `msika-flow-service/.../PickupController.java:67` | hardcoded | fix | I |
| G035 | BFF StructuredHistory fabricates clinical history ("Dr. Stub") for golden patient | `experience-bff/.../StructuredHistoryController.java:286` | hardcoded | honest-degrade/fix | I |
| G036 | BFF Inventory `/requisitions` demo fallback with **no** dev-mode flag | `experience-bff/.../InventoryController.java:141` | hardcoded | fix (gate behind flag) | I |
| G037 | Fabricated provenance — literal `completedBy`/`recordedBy` pollute clinical audit | `inpatient-service/.../ProcedureEpisodeService.java:360,538,603,628` | hardcoded | fix | I |
| G038 | Handover `outgoingStaff`="outgoing-nurse"; history row "Impilo Facility" | `inpatient-service/.../InpatientClinicalService.java:646`, `ProcedureEpisodeService.java:709` | hardcoded | fix | I |
| G039 | Conversational `ask()` confidence two-value constant; no NLP | `guidance-service/.../GuidanceService.java:49` | hardcoded | defer (LLM later) | I |
| G040 | DAGS policy `conditions` persisted but no engine reads them | `data-access-governance-service/.../PolicyService.java:42` | dead | fix | B3/I |
| G041 | Identity-assurance has no GET endpoint — stored records unreachable | `identity-assurance-service/.../RiskAssessmentController.java` | dead | fix | C1 |
| G042 | hr-payroll attendance stored but never computes hours/overtime | `hr-payroll-service/.../InternalHrApi.java:118` | dead | fix | H2 |
| G043 | No earnings model beyond `basicSalary` (no allowances/overtime/tax) | `hr-payroll-service/.../PayrollService.java:61` | hardcoded | fix | H2 |
| G044 | live-service media defaults to in-memory `local-dev` (`DEV-TOKEN-NOT-FOR-PRODUCTION`) | `live-service/.../MediaProviderConfig.java:13` | placeholder | fix (config default) | I |
| G045 | External PACS connector throws on get* (config-gated EXTERNAL backend) | `pacs-adapter-service/.../ExternalPacsClient.java:66` | unimpl | defer-with-marker | I |
| G046 | `oauth2-enabled=false → permitAll` off-switch disables all auth (defaults true) | `dispatch-service/.../SecurityConfig.java:48` (representative) | placeholder | fix (remove off-switch) | I |
| G055 | Client demographics-**UPDATE** has no consumer above Vito — Vito `PUT /v1/clients/{healthId}` (fixed in 3A to preserve all 9 fields) is reachable by no BFF method, no BFF controller, and no UI edit-demographics flow. Backend-correct but consumer-less; completing the thread = building the edit feature (UI + BFF endpoint + `VitoServiceClient` PUT method). | `experience-bff/.../client/VitoServiceClient.java` (no PUT to `/v1/clients/{id}`); no UI edit flow | missing-surface | **build (committed feature block H5)** | **H5** |

## LOW

| ID | Finding | file:line | Cat | Decision | Block |
|----|---------|-----------|-----|----------|-------|
| G047 | Share-slip PDF prints token as text — no QR rendered | `share-slip-service/.../ShareSlipPdfService.java:125` | placeholder | fix | I |
| G048 | BFF DisplaySettings GET defaults / PUT echoes — prefs don't persist | `experience-bff/.../DisplaySettingsController.java` | echo | fix | I |
| G049 | BFF Vitals listing without ids returns empty — `TODO: wire to PctServiceClient` | `experience-bff/.../VitalsController.java:78` | todo | fix | I |
| G050 | Search relevance hardcoded 1.0 (no ranking) | `guidance-service/.../GuidanceApiController.java:105` | hardcoded | defer | I |
| G051 | Empty `ifPresent` lambda + redundant re-query (dead block) | `indawo-service/.../SiteRegulatoryService.java:350` | dead | fix | I |
| G052 | PortalHealthReporting fixtures + fake submit — **orphaned/unmounted** | `ui/.../portal/PortalHealthReporting.tsx:28,110` | component-fixture | reject(delete) or wire | I |
| G053 | PatientLocationBadge "Uses mock data" — **orphaned** | `ui/.../layout/PatientLocationBadge.tsx:7` | mock-comment | reject(delete) or wire | I |
| G054 | Mobile Immunizations/CarePlans/Referrals unwired (`TODO`; honest empty no-op) | `apps/mobile/citizen-app/.../{Immunizations,CarePlans,Referrals}Section.tsx` | todo | fix | I |
| G057 | Capability-token verification is **not yet truly offline** (fetches JWKS online per verify; no caching/distribution) and **omits issuer/audience/scope claim checks** (residual of B2) | `tshepo-offline-service/.../CapabilityTokenJwsVerifier.java` | incomplete | fix (cache/distribute JWKS + add iss/aud/scope checks) | B2-followup |
| G058 | Step-up is **not operationally complete** despite the any-code fix: TOTP enrolment-secret store, SMS-OTP delivery (ties G033) and biometric matcher are unconfigured fail-closed seams; SUPERVISOR_APPROVAL has no controller endpoint (residual of B1) | `tshepo-authz-service/.../stepup/*` | incomplete (seams) | fix (wire enrolment/SMS/biometric/supervisor endpoint) | B1-followup |

## Committed deferred feature blocks (will be built, not dropped)

These are not "deferred indefinitely" — they are scheduled blocks that close a
register row by building a missing surface/feature:

- **H5 — Client demographics-edit flow (closes G055).** Build the UI edit-demographics
  screen + experience-bff update endpoint + `VitoServiceClient` PUT to
  `/v1/clients/{healthId}`, completing the UI→BFF→Vito→read-back thread for the
  already-correct Vito backend (3A). Hard commitment for Wave H.

## ACCEPTABLE / NOT GAPS (verified — do not re-flag)

- `*V11ProbeController` — intentional tech-companion enforcement probes; `/test-command` echo is a documented idempotency proof.
- `rtc-gateway` `InMemoryRtcSessionPersistence` — superseded; `JpaRtcSessionPersistence` is `@Primary`.
- `ndila` `MockProviderAdapter`, `nhume` `Simulated*Adapter` — clearly-labelled config-selectable safe fallbacks.
- `fhir-gateway` `ConsentCacheService` — Kafka-fed TTL revocation cache; enforcement makes a real call, fail-closed DENY.
- BFF Wallet/Queue/Facility fallback controllers — try downstream first, gate fabrication behind `allow-local-fallback=false`/`mode=live`, fail-closed by default. (Contrast G009–G012, which inject **no** client.)
- BFF telemetry history ring-buffers (Telemedicine/AppointmentComms) — bounded ephemeral telemetry by design. NB: the widened detector flags these `in-memory-store`/`in-memory-backing`; treat as low/by-design unless durability is required.
- BFF `HealthIntelligenceService aggregateOnlyPlaceholder` — deliberate visibility withholding.
- `document-service` MINIO storage + ClamAV — real; NOOP variants are `@ConditionalOnProperty`-gated (the *default selection* is the G019/G020 gap).
- `costing-engine` `ZW-PLACEHOLDER-POC` / `DEFAULT_DEMO_TARIFF` — labelled demo seed ("NOT an official national tariff").
- general-ledger, forms-service, developer-portal — in-memory suspicions refuted via full reads.
- Static UI config arrays (nav menus, enum/option pickers, badge/theme maps, structured clinical checklists) — legitimate configuration.
- All honest `501 NOT_IMPLEMENTED` responses — intentional fail-closed contract behaviour.

## Coverage & blind-spot notes

- **Gate vs register:** the gate detects `mockData`/`Placeholder`-marker/in-memory-`*Store`/in-memory-backing/`TODO:wire`/hardcoded-data-collections. It **cannot** detect semantic defects (G001–G008, G013–G017): code that compiles, persists, returns success while defeating auth/tenancy/forwarding. Those live only here.
- **Reachability matters:** G029, G052, G053 are orphaned/unmounted today (no live impact) — flagged so they are not wired up as-is.
- **REAL_PROVEN stays 0:** every "real" service is `REAL_CODE_NOT_PROBED` until a runtime/test probe artifact backs it.
- Not line-re-verified by the sweep: `analytics-pipeline`, `booking` (no markers surfaced).
