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
| G001 | Step-up MFA accepts **any non-blank code** (no TOTP/SMS/biometric); persists COMPLETED | `tshepo-authz-service/.../StepUpService.java:87` | unimpl/crypto | fix | B1 |
| G002 | Offline capability token **JWS signature never verified** — forgeable | `tshepo-offline-service/.../CapabilityTokenService.java:197` | crypto | fix | B2 |
| G003 | ~~DAGS permit-token HMAC key defaults to literal **"change-me-in-prod"** — forgeable if env unset~~ **✅ FIXED (B3):** fail-closed (no default), full SHA-256 requester binding, full-length HMAC | `data-access-governance-service/.../EnforcementService.java` | hardcoded/crypto | done | B3 ✅ |
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
| G056 | DAGS permit token is **issued/stored/emitted but its signature is never verified** by any consumer — enforcement does not check the permit it signs (discovered during B3) | `data-access-governance-service/.../AccessRequestService.java:70` (issue); no verifier anywhere | dead/unwired | fix (add verification at the enforcement point) | B3-followup |

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

## Citizen Zero-to-One Trust Journey findings (G-CZO-01..16)

> **Register-hygiene fix (2026-06-28):** these 16 rows were produced by the Citizen Zero-to-One
> audit wave and previously lived **only** in `docs/audits/citizen-zero-to-one/06-gap-register.md`
> — they were absent from this canonical register (the original append on `intake/citizen-zero-to-one`
> @ `350c5b38c` did not survive into PT). Ported here with **current disposition** so the master
> register reflects the citizen-journey state (closes the DoD-#15 / output-#6 honesty gap surfaced
> by the adversarial verification wave). Dispositions below distinguish **runtime-proven this
> session** from **present-but-not-probed** from **open/deferred**.

| ID | Sev | Title | Disposition (2026-06-28) | Evidence |
|----|-----|-------|--------------------------|----------|
| G-CZO-01 | 🔴 Blocking | LOA upgrade does not reach policy | **CLOSED — runtime-proven** | `PolicyEngine.effectiveLoa=max(ACR,X-Assurance-Level)` (`PolicyEngine.java:532`); BFF `AssuranceLevelResolutionInterceptor`. PolicyEngineTest **36/36** + interceptor **4/4** green this session |
| G-CZO-02 | 🔴 Blocking | No public L0 entry | **CLOSED — present (not probed)** | `ui/one-ui-shell/src/app/welcome`, `PublicShell.tsx`, middleware public set |
| G-CZO-03 | 🟠 High | L5 delegated/caregiver not built | **CLOSED — runtime-proven** | Mvumo V006 `DelegationService` + PolicyEngine **Step 4.5** (`evaluateDelegation:576`, fail-closed); self-grant IDOR refuted by DelegationServiceTest **11/11** (`consentBasisWithoutBackingGrant_rejected`, `forgedBackingId_denied`, `nonConsentBasis_byCitizen_denied`) |
| G-CZO-04 | 🟠 High | No step-up UI on sensitive actions | **CLOSED — present** | `CitizenStepUpController`, `StepUpPrompt.tsx`, `useStepUp.ts` |
| G-CZO-05 | 🟡 Med | Mobile dashboard no assurance banner | **CLOSED — present** | mobile `TrustBanner` on HomeScreen via assurance status |
| G-CZO-06 | 🟡 Med | Policy consent capture not persisted | **CLOSED — present** | routed to Mvumo `legal_agreement` (V005); BFF `PolicyConsentController`→Mvumo fail-safe |
| G-CZO-07 | 🟡 Med | No citizen consent history/revoke feedback | **CLOSED — present** | `/settings/privacy` real Mvumo history timeline |
| G-CZO-08 | 🟡 Med | High-contrast / a11y not user-exposed | **CLOSED — present** | `PublicAccessibilityMenu` + `ShellAccessibilityMenu`, `useAccessibilityPreferences` |
| G-CZO-09 | 🟡 Med | No resumable form continuation | **CLOSED — present** | Health-ID request localStorage draft + restore banner |
| G-CZO-10 | 🟡 Med | No low-data mode | **DEFERRED (not built)** | no text-first/deferred-image mode |
| G-CZO-11 | 🟡 Med | No SMS/phone-OTP as a login *door* | **DEFERRED (not built)** | SMS-OTP exists only as step-up adapter, not a primary login door — "many doors" login is partial |
| G-CZO-12 | ⚪ Cosmetic | No LOA4 banner state | **DEFERRED (not built)** | banner tops out at FULLY_VERIFIED |
| **G-CZO-13** | 🟠 **High** | Temp tier collects DOB+National-ID with no verification | **OPEN — confirmed this session** | `auth/register/assurance/page.tsx:172-216`: `dateOfBirth`/`idNumber` gate the button (line 216) but `handleContinue` spreads only `{...user, assuranceLevel}` (line 56) → **collected then discarded** (no verification, no step-up, no Vito dedup, not even transmitted). Proper fix = wire temp-ID issuance through Vito dedup behind a gate (medium feature), OR stop collecting until that exists |
| **G-CZO-14** | ⚪ Cosmetic | Biometric web login is simulated | **OPEN — re-graded this session** | `auth/login/biometric/page.tsx:42-81`: 2s `setTimeout` then hardcoded `biometric@impilo.local`/`biometric-token` + client `assuranceLevel:"VERIFIED"`. **Dead fake door** — no backend user exists (creds appear only in this file + the audit doc) so it always 401s against a real backend; client VERIFIED is **cosmetic-only** because server derives assurance authoritatively post-G-CZO-01. Not a live bypass, but dishonest UX. Fix = real WebAuthn or hide the door |
| G-CZO-15 | 🔵 Future | Vito↔identity-assurance level sync | **DEFERRED (future)** | `ClientEntity.identityAssuranceLevel` int vs canonical `AssuranceLevel`; no raise-hook |
| G-CZO-16 | 🟡 Med | Citizen routes / LOA-propagation e2e under-tested | **PARTIAL** | per-slice RTL/MockMvc added; the LOA DENY→ALLOW "Proof-1" is unit/MockMvc, **not** a Postgres-backed live e2e |

**Related cross-cutting findings (citizen/trust plane):**

- **TPL-1 (CRITICAL) — CLOSED.** Client `X-Actor-ID`/type/tenant could override the JWT (impersonation); both ext_authz paths now JWT-authoritative (`ExtAuthzGrpcService`, `AuthorizeController`). Present; refutation-probe owed.
- **OPA-as-PDP — SCAFFOLD (not live).** `shadowCompareOpa` (`PolicyEngine.java:615`) logs divergence; **Java stays authoritative** (`:227`). The 7 doctrine rego modules in `infra/opa/impilo/*.rego` don't compile under OPA 0.68 and were **not** promoted; a minimal `infra/opa/authz/authz.rego` gate was authored (`opa test` 7/7). The doctrine "OPA decides policy" is **not achieved** — strangler is OFF/SHADOW only. Maturity: **FIXTURE/SCAFFOLD**.
- **Maturity note:** the citizen security core (G-CZO-01/03, TPL-1) is **REAL_CODE_NOT_PROBED** per the scanner, but its behavioral tests were **executed green in-session** (36/36 + 11/11 + 4/4, with DENY reasons observed firing). A `probeEvidence` artifact (CLI-Postgres LOA-propagation e2e) is the remaining formality for scanner-level REAL_PROVEN.

## Health Provider Experience findings (G-PX-01..07)

> Added 2026-06-28 by the adversarial verification wave. The Provider-Experience batch (lanes
> L1 clinical / L2 facility / L3 provider / L4 value, branch `integration/provider-clinical-place`)
> **is merged to PT** (ancestor of HEAD). The person-first/anti-enumeration security spine and the
> CRITICAL/HIGH correctness fixes were **runtime-proven green in-session** (44 tests across 5 services:
> SilentIdentifierResolution 8/8, ProviderBootstrap 9/9, inpatient double-admit 6/6, TUSO tenant-guard
> 5/5, COSTA value/dedup 16/16). These rows record what the prompt asked for that is **not** yet real.

| ID | Sev | Finding | file/locus | Decision |
|----|-----|---------|-----------|----------|
| **G-PX-01** | 🟠 High (was Blocking) | **Self-treatment block now SERVER-enforced** in PolicyEngine Step 4.6 (`evaluateSelfTreatment`): a provider in a WORK context (Provider-ID + facility/workspace/shift) opening a clinical record whose subject is their own person → `SELF_TREATMENT_BLOCKED`; emergency/break-glass + My-Life self-access pass through. Tested (PolicyEngineTest 38/38). The client `work-pro-life-boundary.ts` is now defence-in-depth over a real server check. **Remaining:** other §10 per-action policies (workspace-entry, cadre-actions, facility-mode) still on DB-rule RBAC + the OPA-enforce track; path-based self-detection (vs X-Subject-ID) needs identifier-space alignment | `PolicyEngine.evaluateSelfTreatment`; `work-pro-life-boundary.ts` | self-treatment enforced; remaining §10 via DB rules + OPA track |
| **G-PX-02** | 🟡 Med | **Product-truth hollow for the +21k-LOC batch (GAP-22).** Regenerated `product-truth.json` is byte-identical; new capabilities (sorting-desk/facility-mode/self-claim/work-context/cadre) are invisible because the scanner is service-level only (all additions inside already-"real" service dirs). Wave-9 / Acceptance-J "Product Truth updated honestly" not genuinely met. *(from grounded memory; not re-probed)* | `reports/product/product-truth.json`; `docs/registry/services-registry.yaml` | add route/capability granularity + per-service disposition; never report a generated PASS without probing the delta |
| **G-PX-03** | 🟡 Med | **Phone/email/invite silent resolution unwired (GAP-5).** Proven by the service's own test `phoneAndEmail_uniformDenyUntilWired` — VITO has masked search only, no silent contact-resolve. Fails **uniformly** (no enumeration leak), so honest-degrade, but Journey-A "start from any authorised identifier" is incomplete | `tshepo-identity SilentIdentifierResolutionService`; VITO | build contact-resolve seam (no-leak preserved) |
| **G-PX-04** | ✅ **CLOSED** | **Two competing cadre authorities (GAP-4) — RESOLVED.** Verification corrected the finding: there are **no** backend `cadre_scope_rules`/`clinical_cadre_definitions`/`cadre_form_sections` tables or routes; `ui/one-ui-shell/src/engines/cadreEngine.ts` was an **orphaned** client engine calling non-existent endpoints with zero importers. Removed it (`3c3c7e482`); the Java PCT `CadreEngine` (V015, `/v1/cadre/decision`, CadreEngineTest 11/11) is the single SoR | `pct-service` CadreEngine (live) | removed orphan client engine — single authority |
| **G-PX-05** | 🟡 Med | **Mobile/provider parity (Wave 8) deferred.** No mobile surfaces for login/onboarding/context-picker/check-in/Work/My-Professional/Facility-Mode | `apps/mobile/*` | build per Wave 8 |
| **G-PX-06** | 🟡 Med | **Net-new clinical UX depth missing** (Journeys F/G, §7.4): cadre form **content**, front-door **sorting session**, encounter tools/safety-ribbon, ZIBO-governed **order sets**, ICD-11/SNOMED, the 30 seed scenarios | pct/encounter surfaces | multi-wave feature build |
| **G-PX-07** | 🟡 Med | **No live end-to-end journey proof (DoD#4).** Acceptance A–J never runtime-proven as a click-through; per-lane unit/MockMvc only | n/a (proof gap) | persona-level journey IT or live click-through |

## OROS Orders / Investigations / Diagnostics / Results findings (G-OR-01..05)

> Added 2026-06-28 by the adversarial verification wave. The OROS journey + completion wave (O1–O19)
> **is merged to PT** (`fd650cc30` ancestor of HEAD) and is the **most complete** of the three waves
> verified. Runtime-proven green in-session: **oros-service 154/154 + madi-service 29/29** (full
> suites), incl. the safety-critical subset (transition guard, report versioning, critical
> escalation, secure external-result link, specimen/lab-result, blood-bank callback). UI parity
> spot-check (`/diagnostics/lab-worklist`) confirmed genuinely wired to live BFF hooks (not a stub);
> `IntegrationStatusService` honestly reports configured/not-configured (prompt-compliant). These
> rows record honest residuals, not missing functionality.

| ID | Sev | Finding | Locus | Decision |
|----|-----|---------|-------|----------|
| **G-OR-01** | 🟢 **RBAC enforced** | **OROS §21 policy now enforced at ext_authz** (V022 `051fd04e7`, Flyway-proven). The oros `/v1/**` routes were deny-by-default (never live through the gateway — SYS-3); V022 seeds the diagnostic journey (clinical→orders/results, imaging/lab→fulfil/report, CITIZEN→own-results/QR-claim, admin→routing/catalogue, supervisors→reconcile) — strictly additive, no regression. Citizen broad-results grant narrowed (V023 — provider-facing routes; citizen result-view deferred to the patient lane G-CT-01). Remaining: finer per-action gating + purpose tightening | partial → in-service + finer-action follow-up | enforced; refine per-action + in-service subject bind |
| **G-OR-02** | 🟡 Med | **Interop adapters flag-OFF + unsoaked vs live counterparties** (FHIR ServiceRequest/DiagnosticReport/ImagingStudy/Observation, HL7 ORM/ORU, DICOM MWL, LIMS). **Prompt-compliant** honest readiness, but maturity = contract-seam/unit-tested only. Minor doc-vs-code tension: `IntegrationStatusService` calls HL7/DICOM/FHIR-inbound "seams NOT implemented" while mapper/listener classes + tests exist → "built-but-unwired/unsoaked" | `oros-service/integration/*`, `Hl7OruMapper`, `MwlMode` | live counterparty soak at rollout; reconcile the status-doc wording |
| **G-OR-03** | 🔵 Low | **MADI not in compose** → OROS↔MADI blood-bank loop not e2e-runnable locally (unit-proven only: oros BloodOrderCallback 4/4 + MADI 29/29) | `docker-compose*`; madi-service | add MADI to an infra profile for e2e |
| **G-OR-04** | 🟡 Med | **Product-truth hollow for OROS in-service additions** (154 tests + ~14 UI pages invisible to the service-level scanner — all inside already-"real" oros-service). **Instance of systemic SYS-2 (GAP-22).** §23 "Product Truth reflects real state" only partially met | `reports/product/product-truth.json` | route/capability-grained truth (see SYS-2) |
| **G-OR-05** | 🟡 Med | **No live e2e click-through of Journeys A–H.** Acceptance bar is "real backend state, real UI, policy enforcement" end-to-end; proven at unit/component level only. **Instance of systemic SYS-3.** | n/a (proof gap) | journey IT / live click-through |

## TUSO / Indawo / Facility-Mode / Organisation / Regulation findings (G-TI-01..04)

> Added 2026-06-28 by the adversarial verification wave. This is the **L2 lane** of
> `integration/provider-clinical-place` (merged to PT). Broadest end-to-end coverage of the four
> verified waves — backend + BFF + UI present across all 5 modes (Facility/Organisation/Indawo/
> Regulator/Work). Runtime-proven green in-session: **TUSO 58/58, Indawo 25/25, WGV 9/9** (+ the
> earlier TUSO cross-tenant *write*-guard 5/5). Indawo backend confirmed reachable BFF→UI→mobile;
> Organisation tenancy in WGV `OrganisationService` → BFF `AdminGovernanceController` → `/organization-admin`.

| ID | Sev | Finding | Locus | Decision |
|----|-----|---------|-------|----------|
| **G-TI-01** | 🟡 Med | **Cross-tenant *visibility* isolation code-present but not test-proven.** `FacilityService.searchFacilities(tenantId,…)` scopes reads + update rejects mismatched tenant (`FacilityService.java:231`), but the only tenant tests are *writes* (ServicePoint/FacilityUnit guards). §17's literal invariants — "organisation admin cannot see other tenant data", "regulator cannot see outside mandate" — have **no read-denial test** | `tuso-service` FacilityService; WGV | add scoped-read isolation tests (org/regulator/tenant) |
| **G-TI-02** | 🟡 Med | **WGV org-tenancy surface thinly tested.** 9 tests cover the whole Organisation onboarding + AuthorisedRepresentative + OnboardingWorkflow + multi-regulator backend (Journeys A/B/E/F). Code present + green but under-covered relative to breadth | `workforce-governance-service` | expand onboarding/verification/representative tests |
| **G-TI-03** | 🟡 Med | **No unified 5-mode resolution (§6).** `FacilityModeController` is facility-specific; modes resolved per-mode (separate Facility/Indawo controllers) + client-side shell — no single server "list available modes for this user" resolver matching the §6 login decision map. SYS-1-adjacent (mode gating leans on client boundary + edge authz) | `experience-bff` mode controllers; shell | unified mode-resolution endpoint driven by policy |
| **G-TI-04** | 🔵 Low | **Mobile parity partial.** Public-health mobile controllers exist (`ProviderPublicHealthController`/`CitizenPublicHealthController`); **facility-admin mobile screens deferred** (Wave 10 tail) | `apps/mobile/*`; experience-bff mobile | build facility-admin mobile per Wave 10 |

## Khuluma Comms Hub + Nompilo continuity findings (G-KH-01..06)

> Added 2026-06-28 by the adversarial verification wave. `khuluma-service` **is merged to PT**
> (`intake/khuluma-comms-hub` ancestor of HEAD). The native-first core is **runtime-proven green
> in-session**: khuluma-service **23/23** (incl. `RealtimeGatewayTest` = a REAL WebSocket round-trip,
> the repo's first server-side WS) + `KhulumaWave1E2ETest` full journey + BFF Khuluma **11/11**.
> **Best-instrumented wave** (own `khuluma.rego` default-deny + V017 DB-rule seed + BFF policy service;
> new service so VISIBLE to the product-truth scanner; ships an E2E test + smoke + A/V QA checklist).
> BUT the **largest scope gap** of the verified prompts: ~Wave 1 of ~9 built; the rest deferred per an
> **approved phased plan** (honest, not overclaim). Rows record deferred + missing scope.

| ID | Sev | Finding | Status | Decision |
|----|-----|---------|--------|----------|
| **G-KH-01** | 🟡 Med | **Escalation / routing / SLA (prompt §"Escalation and Routing") not built** — no `khuluma_escalations`/`sla_policies`/EscalationService (W4 deferred) | absent on PT | build W4 |
| **G-KH-02** | 🟡 Med | **Facility/programme channels + client communities + broadcasts not built** — conversation types the prompt explicitly requires (W5 deferred) | absent on PT | build W5 |
| **G-KH-03** | 🟡 Med | **External adapter abstraction not built** — the prompt wanted the *model* now (channels/adapters/delivery-attempt tables, native-in-app as first adapter) even with providers deferred (W6) | absent on PT | build adapter model (honest configured/not-configured) |
| **G-KH-04** | 🔵 Low | **Presence depth + Vashandi on-call + mobile realtime push** — delivery is **poll-based** (4–5s) not pushed; browser-direct WS auth (JWT-in-handshake) deferred (W7) | partial | JWT-handshake WS push + Vashandi on-duty |
| **G-KH-05** | 🟠 High | **Nompilo addendum recipient/disclosure model ABSENT (security-relevant).** No guardian/caregiver/proxy/permitted-person recipient model; **no consent-gated disclosure level; no safe notification summaries** ("An update is available" vs "Your HIV result is ready"). The safe-disclosure principle is a **privacy requirement**, not a nicety | absent on PT | build recipient + disclosure-level + safe-summary model with policy gating |
| **G-KH-06** | 🟡 Med | **Nompilo addendum feedback + handoff ABSENT** — no feedback capture/routing/states (received→…→resolved), no Nompilo handoff metadata (action type, flow id, guidance-recommended) on actionable messages | absent on PT | build feedback routing + Nompilo handoff metadata |

## Fundo LMS + Learning-Administration findings (G-FU-01..04)

> Added 2026-06-28 by the adversarial verification wave. `learning-service` **is merged to PT**
> (phase-1 `47bbb4d1f` + phase-2 `0f761a4ab` ancestors); most feature-complete delivery of the
> verified prompts — core LMS + the full 18-wave addendum (pre-service student lifecycle, attendance
> +QR, real AI behind a provider-agnostic interface w/ stub default, provider-tenancy/accreditation,
> dashboards; migrations V016–V025). **G-FU-01 is the headline finding of the whole wave.**

| ID | Sev | Finding | Status | Decision |
|----|-----|---------|--------|----------|
| **G-FU-01** | 🔴 **Blocker** | **Recorded "60 green" is RED on PT — `learning-service` context fails to load.** `learning.security:` YAML stanza is **empty** (missing its `oauth2-enabled` child) in BOTH `application.yml` (prod) + `application-test.yml` → `ConverterNotFound: String→LearningProperties$Security` → context refresh fails → **6/60 `@SpringBootTest` ITs ERROR**. Merge-integration regression (green on `intake/fundo-lms`; merge to PT dropped the child). **Production boot-blocker** under default config (binding is profile-independent; prod startup hits the same path). **FOUND + FIXED this session** (restored `oauth2-enabled: ${LEARNING_OAUTH2_ENABLED:true/false}` in both files) → suite now **60/60 green**. Fix in worktree, **uncommitted** | found+fixed (worktree), unpushed | commit the 1-line fix; add a prod-profile boot smoke to CI |
| **G-FU-02** | 🟡 Med · **DEFERRED_PO_DECISION** | **Tshepo training-gate not enforced.** `FundoTrainingGateService` is a clean signal (is-requirement-satisfied) but no consumer enforces it (vashandi check-in doesn't consult training; no fundo client there). Enforcement = block-vs-warn + requirement-mapping = a clinical-ops/PO decision → parked **PO-20260629-01**. Conservative default applied: kept existing behaviour (no blocking gate shipped — avoids denying licensed providers). | parked (signal exists; consumer deferred) | PO decides activation (see po-decisions/2026-06-29-fundo-training-gate-activation.md); recommend per-requirement ADVISORY default |
| **G-FU-03** | 🟡 Med | **Varapi native CPD egress consumer QUEUED** — `certificate.issued.v1` carries full CPD-candidate shape + egress spec doc, but the varapi-side consumer that lands native completions into the CPD candidate flow is not wired (correctly does NOT duplicate Varapi's ledger) | partial | wire varapi listener (provider/workforce lane) |
| **G-FU-04** | 🔵 Low | **Honest partials** (prompt-compliant): AI provider disabled-by-default (no external egress); offline = mobile read-cache only; document binary upload + campaign/surveillance BFF consumers QUEUED; federated academies deferred | by-design / deferred | build per future waves; keep Product Truth honest |

## Patient Safety & Pharmacovigilance PoC findings (G-PS-01..03)

> Added 2026-06-28 by the adversarial verification wave. `patient-safety-service` **is merged to PT**
> (`intake/patient-safety-pv` ancestor of HEAD). **Cleanest / most honest delivery of the 8 verified
> prompts** — a PoC scoped exactly as the prompt asked (Phase 0/1), with honesty unit-tested.
> Runtime-proven green in-session: patient-safety-service **5/5** (incl. `config_surfaces_honest_adapter_posture`
> — a test asserting dispatch adapters are honestly OFF), BFF `PatientSafetyServiceClientTest` **4/4**,
> surveillance-service **25/25**. Surveillance consumer verified signal-only (does NOT own the case —
> SoR boundary held); BFF public-anon path narrowly scoped (submit-only, no PHI read via anon route).

| ID | Sev | Finding | Status | Decision |
|----|-----|---------|--------|----------|
| **G-PS-01** | ✅ **CLOSED (core)** | **Enforced end-to-end.** Gateway RBAC (V020 `cc5e43841`) + **in-service citizen-own binding** (`requireOwnReportForCitizen`, report get/list — closes the citizen IDOR the gateway rule exposed; tested `citizen_can_only_access_own_report` 403/200/own-list). Remaining (lower-pri): facility-focal facility-scoping + `restricted-phi` field masking + purpose tightening | ✅ core closed | facility-scope + restricted-phi masking later |
| **G-PS-02** | 🔵 Low | **Surveillance `PatientSafetySignalConsumer` has no dedicated test** — signal-feed path unverified (service suite 25/25 green; consumer logic correct on read but uncovered) | gap | add a consumer unit/slice test |
| **G-PS-03** | 🔵 Low | **Documented PoC next-steps (prompt-compliant honest deferrals):** integration-hub live adapters OFF (VigiFlow=MANUAL, E2B=`E2B_R3_ALIGNED` adapters disabled, VigiMobile=external link-out), forms-service runtime form-pack seed migration, Envoy `/v1/public/patient-safety/*` upstream route | by-design / deferred | build per post-PoC waves; keep honest |

## Rito (Quality, Safety & Client Voice) findings (G-RT-01..03)

> Added 2026-06-29 by the adversarial verification wave. `rito-quality-safety-service` **is merged to
> PT** (`intake/rito-quality-safety` ancestor of HEAD). Genuine end-to-end operating service (not a
> dashboard/form): 21 `rit_*` tables, 16-status `CaseLifecycle`, Case/Audit/Survey/Improvement/Signal
> services, BFF persona controllers, 13 web pages, mobile slices, OpenAPI. Runtime-proven green
> in-session: **11/11**, incl. the operating cycle (`fullLifecycleToClose`, `lowCsatSpawnsFollowUpCase`,
> `ingestThenConvertToCase`, `correctiveActionActVerifyClosure`, `qiPlanPdsaLifecycleToCompleted`,
> audit scoring) and the safety invariant **`aiActorCannotCloseCriticalCase`** (`guardHumanDecision`
> blocks SYSTEM/AI/RULES/BOT from resolve/close of CRITICAL-or-sensitive cases — genuinely enforced +
> tested). BFF→service port correct (8391). New service → legitimately visible to product-truth scanner.

| ID | Sev | Finding | Status | Decision |
|----|-----|---------|--------|----------|
| **G-RT-01** | 🟢 **RBAC enforced** | **§7 permission model now enforced at ext_authz** (V021 `f10fa85f1`, Flyway-proven): `rito.*` roles reconciled to realm roles; case-create/quality-ops/reads gated, close-restriction enforced via DENY-wins, REGULATOR realm role added. Citizen case-read narrowed (V024 — was an IDOR; deactivated pending in-service own-subject binding). Remaining: bound citizen case-tracking + §3 sensitive-category identity REDACTION | partial → in-service guard follow-up | add rito sensitive-redaction guard (mirror ClinicalAccessGuard) |
| **G-RT-02** | 🔵 Low | **Honest partials:** survey dynamic-question renderer; M&M review case-type pending PO confirm; mobile is a focused slice (citizen-feedback + provider-safety), not full triage parity; document-upload UX pending | partial | complete per next wave |
| **G-RT-03** | 🔵 Low | **Notifications / Fundo-learning are outbox-intent events only** (`rito.learning.recommended`, `rito.notification.requested`) — delivery owned by consuming services. Honest/by-design, but the cross-service learning loop is not end-to-end-proven | by-design / partial | wire + prove the Fundo learning-loop e2e |

## Core Transaction wave (Patient Access / Flow / Clinical Ops / Inpatient / Encounter / Telemedicine) findings (G-CT-01..04)

> Added 2026-06-29 by the adversarial verification wave. This is the **L1 lane** of
> `integration/provider-clinical-place` (merged to PT). Provider/clinical spine + Core-Transaction
> contract + telemedicine 7-stage loop runtime-proven green in-session: **pct-service 88/88** (incl.
> `CadreEngineTest` 11/11 — Part-16 cadre enforcement, `ReferralPackageServiceTest` 4/4,
> `TelemedicineOrchestrationServiceTest` 2/2 idempotent telemed→value, `PctRtcGatewaySessionProviderTest`).
> Addendum-1 journey doc present (`docs/journeys/core-transaction-patient-access-encounter-orchestration.md`,
> 1002 lines); Core-Transaction contract present (`contracts/core-transaction.ts` + asyncapi + openapi);
> `SortingDeskController/Service` present; telemedicine 7-stage on `TelemedicineController`
> (build→consent→submit→route→accept→telehealth→respond-structured→complete, V020 structured_response).

| ID | Sev | Finding | Status | Decision |
|----|-----|---------|--------|----------|
| **G-CT-01** | 🟠 High | **Patient lane of the three-lane Core Transaction largely unbuilt.** The wave's signature is provider + **patient** + access/compensation lanes simultaneously (Parts 3, 23; DoD #5/#11 "patient has a corresponding experience at each stage" + "patient-facing messages exist"). Reality: patient lane is **documented** in the journey doc and value events exist (L4), but there are **no dedicated patient-facing surfaces** (queue-status / my-visit / inpatient-status / telemedicine-status) and **no patient message-catalog** implementation. Provider ✅ + access/compensation ✅, **patient-facing ✗** | documented, not surfaced | build patient-facing surfaces + message catalog |
| **G-CT-02** | 🟡 Med | **Telemedicine loop-closure lighter than the Addendum-2 mandate.** Stage-6 structured response IS real (V020 `structured_response` diagnosis/actionPlan/redFlags/followUp); Stage-7 `/complete` emits the value event but the **structured referrer Completion Note** (actions-taken / patient-outcome / closure-narrative, "no closure without audit") is thin. The 6 modes route via RTC-gateway session provider; async/chat/MDT-board depth varies | partial | structured completion-note + mode-depth |
| **G-CT-03** | 🟡 Med | **PCT cadre authority duplication + rego not enforced.** Cadre-duplication half **CLOSED** (G-PX-04: orphan client engine removed `3c3c7e482`; Java `CadreEngine` single SoR). Remaining: cadre policy enforced in-service, not as gateway rego — folds into the SYS-1 non-clinical-enforcement track | partial (cadre-dup closed) | **SYS-1** track: governed-resource rules once role taxonomy lands |
| **G-CT-04** | 🟡 Med | **No live end-to-end Core-Transaction journey proof.** Strong per-lane unit/MockMvc (88/88), but no persona click-through identity→sort→triage→queue→encounter→outcome→settle. **Instance of SYS-3** | gap | persona journey IT / live click-through |

## Systemic cross-wave findings (SYS-1..3) — highest leverage

> Surfaced by verifying three independent waves (Citizen Zero-to-One, Provider Experience, OROS).
> The **same three gaps recur in every wave** — they are cross-cutting, not per-wave, and are the
> highest-leverage remaining work. Each wave's per-row instances are noted.

| ID | Sev | Systemic finding | Recurs as | Why it matters |
|----|-----|------------------|-----------|----------------|
| **SYS-1** | 🔴 Blocking | **Fine-grained OPA/Tshepo policy is scaffold/shadow, not enforced.** The doctrine "OPA decides policy" is not live: the `infra/opa/impilo/*.rego` doctrine corpus is unmounted + doesn't compile under OPA 0.68; only a minimal `infra/opa/authz/authz.rego` runs in **SHADOW** (Java authoritative). Per-context/per-action policies rest on generic DB-rule RBAC + client boundaries | CZO OPA-scaffold; Provider **G-PX-01**; OROS **G-OR-01** | every wave's "Trust always / policy-governed access" is enforced by generic edge authz, not the per-action rules the prompts specify |
| **SYS-2** | 🟡 Med | **Product-truth gate is service-level only → hollow for in-service feature additions.** Adding real capabilities/routes/UI inside an already-"real" service moves the gate **0**; the scanner has no route/capability granularity (GAP-22) | Provider **G-PX-02**; OROS **G-OR-04** | "Product Truth reflects real state" is structurally unprovable for most new work; a green gate proves nothing about it |
| **SYS-3** | 🟡 Med | **No wave has a live end-to-end journey proof.** Strong per-service unit/MockMvc coverage (278 tests proven green this session across the 3 waves), but no persona-level click-through with real cross-service state + policy enforcement (every prompt's literal DoD) | CZO **G-CZO-16**; Provider **G-PX-07**; OROS **G-OR-05** | the DoD acceptance journeys are asserted, not demonstrated end-to-end |
