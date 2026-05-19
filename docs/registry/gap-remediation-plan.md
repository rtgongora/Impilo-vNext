# Gap Remediation Plan

## Priority 0 - Production Blockers

| Gap | Impact | Action | Owner |
|---|---|---|---|
| Stub providers active by default (`notification-service`) | silent message delivery failure | switch defaults to explicit real providers; fail closed when provider unset | integration platform |
| Sandbox adapter defaults in `mushex-service` | payment integrity risk | require production profile hard-disable for sandbox adapter and add startup guard | enterprise finance |
| Placeholder BFF responses in production controllers | false-success behavior | replace placeholders with real integrations or typed error responses + audit events | experience platform |
| Mvumo consent evaluate path (previously stubbed) | trust/compliance gap | **completed in Trust audit pass:** delegated `/internal/v1/mvumo/evaluate` to live `tshepo-consent-service` decision endpoint; retain regression tests | trust plane |
| MVUMO remote-session/template trust workflows | trust orchestration false-positive/incomplete behavior | **completed in this pass:** verify/grant/refuse/withdraw + template create/update implemented with persistence, transitions, and audit events | trust plane |
| MVUMO + TSHEPO runtime full-stack cutover evidence | residual integration risk | **partially completed in cutover pass:** runtime harness reliability hardened (preflight daemon/compose checks, deterministic project naming, health-gated startup, retries/timeouts, failure diagnostics artifact capture, clean teardown) and CI job hardened to fail explicitly; blocker remains open until first green `trust-fullstack-runtime` CI execution and subsequent audit-ledger depth expansion | trust plane |
| Legacy TSHEPO retirement execution gates | compatibility drift risk | **advanced in cutover pass:** added machine-readable checklist (`docs/architecture/tshepo-legacy-retirement-checklist.md`), compatibility deprecation metadata in OpenAPI, consumer default-URL guard tests, and removed `TSHEPO_POLICY_BASE_URL` fallback from active runtime policy consumers; complete zero-usage window and compatibility proxy decommission gate before route removal | trust plane |
| Registry core service authz posture drift | unauthorized mutation risk | **remediated in registry pass:** removed production `anyRequest().permitAll()` posture in VITO/VARAPI/TUSO/ZIBO/product-registry and added source-guard tests to prevent regression | registry plane |
| Registry plane runtime E2E proof | residual production readiness risk | **partially remediated in this pass:** added CI-grade multi-service harness stack (`compose/registry/docker-compose.registry-e2e.yml`), runtime scripts (`test/integration/registry-fullstack-runtime.(sh|ps1)`), and CI job (`registry-fullstack-runtime`); blocker remains open until first green CI execution and subsequent audit-depth assertion expansion | registry plane |
| Registry UI/BFF/admin operational evidence | readiness confidence risk | **partially remediated in this pass:** hardened BFF registry fail-close semantics (no synthetic success in live mode for facility/geo dependency errors) and added route-level tests (`FacilityControllerTest`, `RegistryGeoLocalityControllerTest`); extend to full admin mutation paths in follow-up | registry plane |
| Registry contract convergence | integration inconsistency risk | **advanced in this pass:** added service-level convergence matrix (`docs/architecture/registry-api-contract-convergence-matrix.md`) and tightened canonical contract guardrails; complete remaining per-service envelope harmonization gates | registry plane |
| Experience BFF synthetic-success fallback behavior | user-facing false-positive risk | **further remediated in this pass:** active prescription (`PharmacyController`), communication, guidance, search, FHIR, and queue (`QueueController`) routes now fail-close with typed envelopes and request/correlation metadata; queue synthetic status success payloads were removed. Remaining task is long-tail controller parity sweep. | experience plane |
| Experience UI demo/mock leakage into production paths | capability integrity risk | public-health and assessment fixture risks were reduced (prototype full-exam tabs explicitly disabled; hardcoded history defaults removed); remaining work is shell-wide fixture governance completion across additional active routes | experience plane |
| Experience route/contract convergence | orchestration inconsistency risk | continue route-level error/header contract conformance expansion from current hardened clusters; pharmacy prescription write/cancel backend blocker is now remediated, enterprise long-tail parity further advanced (`financial-documents`, `service-access-decisions`, `reconciliation/triple-match`, finance billing lifecycle/detail fail-close normalization), remaining work is residual controller parity and clinical-plane dependency hardening | experience plane |
| Clinical prescription canonical write/cancel API gap (`pharmacy-service`) | clinical prescribing workflows blocked in mobile/web | **completed in this pass:** implemented `/v1/prescriptions` create/list/get/cancel/refill/dispense with persistence + state checks + outbox events; rewired Experience BFF mobile/web prescription write/cancel from `501` to live backend delegation | clinical plane |
| Clinical plane endpoint/authz/audit evidence depth gap | readiness-verdict confidence risk | **completed in this pass:** added service-by-service endpoint inventory evidence, security hardening in `oros-service`/`pct-service`/`fhir-gateway-service`, and cross-service `ClinicalPlaneEvidenceGuardTest` validating authz/audit/boundary markers | clinical plane |
| Clinical SHR/FHIR runtime boundary proof gap | ownership/boundary regression risk | **completed in this pass:** added repeatable `clinical-shr-fhir-runtime.(sh|ps1)` harness plus source-level ownership guards for `butano-service` vs `butano-fhir`/`fhir-gateway-service` | clinical plane |
| PACS + telemedicine closure evidence depth gap | residual confidence risk for imaging/telehealth workflows | **completed in focused closure pass:** added PACS regression guard (`SecurityConfigSourceGuardTest`), included `pacs-adapter-service` in `ClinicalPlaneEvidenceGuardTest`, hardened `MobileTelemedicineController` validation/envelope parity, and added `MobileTelemedicineControllerTest` fail-close coverage | clinical plane |
| Experience validation fragility (test/build) | delivery confidence risk | **remediated in narrow cleanup pass:** fixed finance billing test `@tanstack/react-query` mock parity (`useQueryClient`/`useMutation`), and removed `next/font/google` runtime dependency in `ui/experience` layout so build does not require `fonts.googleapis.com` network fetch | experience plane |
| Encounter/PCT production-path synthetic fallback risk | false clinical success and lifecycle divergence | **further remediated in virtual-encounter pass:** encounter discharge route now resolves encounter->journey linkage and starts canonical PCT discharge; encounter modality metadata added to PCT encounter model | experience + clinical plane |
| Teleconsult canonical backend ownership and wiring gap | referral package/session workflow not backed by sovereign persistence/audit path | **further remediated in focused closure pass:** `/internal/v1/teleconsult/*` now enforces strict attachment ID verification via document-service and bounded routing validation via VARAPI/TUSO (provider/workspace/facility-service); unsupported on-call/team/pool routing returns explicit `501`; remaining blocker is real-time transport backend (chat/audio/video signaling) | experience + clinical plane |
| Encounter lifecycle metadata ambiguity across entry points/contexts | reduced traceability for outpatient/emergency/inpatient/community/virtual orchestration | **remediated in this pass:** added canonical encounter context/entry-point/modality/care-setting/priority/pathway/protocol metadata in PCT + BFF + UI; added duplicate active encounter guard and inventory/mastery maps | clinical + experience plane |
| Deep encounter capability expansion (CDS/inpatient/procedure/PACS) | advanced clinical orchestration depth still partial | **partially remediated in deep pass:** added encounter pathway/protocol reassignment route (PCT + BFF + UI), explicit procedure/OR encounter contexts, and published dedicated maps for pathways/CDS, inpatient, procedure, and PACS/DICOM. Remaining blockers are CDS orchestration unification, ward-round/nursing-plan depth, and procedure aggregate ADR. | clinical + experience plane |
| Enterprise marketplace synthetic create fallback | false financial/order success risk | **further remediated in enterprise parity wave:** order-create remains typed `502 MSIKA_FLOW_UNAVAILABLE`; unsupported order/booking list surfaces now explicit typed `501 MARKETPLACE_ROUTE_UNAVAILABLE`; catalog/vendor/order-detail routes no longer mask upstream non-success status as `200` | enterprise + experience plane |
| Provider financing empty-success fallback | silent coverage dependency failure risk | **further remediated in enterprise parity wave:** provider contract/network list and mutation routes now consistently fail-close with typed `502 COVERAGE_UNAVAILABLE` envelopes plus request/correlation metadata | enterprise + experience plane |
| ERP GL raw-proxy error behavior | enterprise accounting route inconsistency and opaque upstream failure handling | **remediated in enterprise parity wave:** `ErpGlBffController` now emits canonical envelopes with typed `502 GL_UNAVAILABLE` fail-close semantics across accounts/journals/period/report routes | enterprise + experience plane |
| Wallet non-rail synthetic payment success | fake payment completion risk for unsupported rails | **remediated in enterprise parity wave:** non-wallet payment methods now explicit typed `501 PAYMENT_METHOD_UNAVAILABLE`; only wired wallet path can return created payment results | enterprise + experience plane |
| Mobile provider billing synthetic success placeholders | fake billing/charge success in production path | **remediated in enterprise pass:** billing routes now explicit `501 BILLING_ROUTE_UNAVAILABLE` until real enterprise billing wiring is complete | enterprise + experience plane |
| Coverage service production authz posture | unauthorized access risk on enterprise coverage routes | **remediated in enterprise pass:** production security changed from `permitAll` to authenticated catch-all for non-actuator routes with source guard test | enterprise plane |

## Priority 1 - Architecture and Ownership

| Gap | Impact | Action |
|---|---|---|
| Potential SoR overlap (`ndr-service` vs `national-data-repository-service`) | duplicate ownership and drift | **advanced in data-plane pass:** runtime query ownership is now explicitly canonicalized to `ndr-service` (`national-data-repository-service` `/internal/v1/query` returns conflict/deprecation envelope); complete structural service merge ADR as follow-up |
| `mushe-wallet-service` ownership/build drift | enterprise capability fragmentation | **remediated in blocker-fix pass:** added module to `services/pom.xml` reactor, corrected service parent POM lineage to `impilo-parent`, and fixed compile blockers in wallet outbox/hold service so reactor build now succeeds |
| Wellness taxonomy drift (`simba-service` vs `wellness-service`) | ownership confusion across wellness boundaries | **remediated and cascaded repo-wide:** `simba-service` is canonical wellness/personal-health-data SoR and `wellness-service` is compatibility alias only; downstream maps/contracts updated to block parallel ownership |
| Public-health operations module ambiguity | capability ownership uncertainty for cross-service workflows | **remediated in blocker-fix pass:** explicitly ratified public-health-operations as a composite capability over `surveillance-service` + `campaigns-service` (+ `indawo-service` context), avoiding false missing-module classification |
| Parallel experience shells drift | route mismatch and duplicated behavior | consolidate canonical route ownership to `one-ui-shell` and compatibility policy for `ui/experience` |
| TSHEPO decomposition overlap (`tshepo-service` vs decomposed TSHEPO sub-services) | duplicate trust ownership and policy drift | ADR published; enforce migration wave plan and consumer cutover gating to canonical TSHEPO services |
| Enterprise end-to-end runtime proof depth | readiness confidence risk | **partially remediated in second enterprise pass:** added runtime harness scripts (`test/integration/enterprise-fullstack-runtime.(sh|ps1)`) plus runbook (`docs/architecture/enterprise-runtime-proof-harness.md`) with health-gated checks, explicit `501 BILLING_ROUTE_UNAVAILABLE` fail-close assertions, and long-tail enterprise route runtime probes; blocker remains open until first green CI execution and deeper encounter-to-charge/claim-to-remittance/procurement-to-pay state assertions |

## Priority 2 - Backend/Frontend Wiring

| Gap | Action |
|---|---|
| Citizen conditions and provider discovery TODO paths | wire to live APIs via BFF and remove empty local state placeholders |
| SOAP save local-only behavior | add persisted backend endpoint + audit |
| Public-health mixed fixture/live rendering | keep fixture modules disconnected from production route imports and enforce unavailable-state messaging where dedicated dataset APIs are not yet implemented; sovereign lifecycle APIs now available at surveillance `/internal/v1/public-health/*` and wired through BFF |
| Sparse wiring for workflow/dispatch routes | add explicit UI orchestration surfaces or register as non-user-facing APIs |

## Priority 3 - Contracts, Tests, and Operations

| Gap | Action |
|---|---|
| Partial API contract readiness on multiple services | enforce OpenAPI parity with implementation and add contract tests |
| Partial authz/audit and observability status | add mandatory route-level checks and dashboards/alerts runbook references |
| Incomplete readiness signal taxonomy | tighten per-service readiness statuses from inferred to evidenced values |

## Exit Criteria

- No known production-path mock/stub remains unclassified.
- Every service has one primary plane, one domain, explicit SoR, and explicit forbidden responsibilities.
- Critical user-facing capabilities have UI -> BFF -> backend wiring evidence with authz/audit controls.
- Production readiness register transitions from baseline-assessed to evidence-backed status.

## Data Plane Blockers and Next Actions (2026-05-15)

| Gap | Impact | Action | Owner |
|---|---|---|---|
| `ndr-service` + `national-data-repository-service` overlap | duplicate SoR and contract drift risk | **runtime blocker closed:** `ndr-service` now canonical query owner; keep service-merge/deprecation execution plan for structural consolidation | data platform |
| Weekly IDSR dedicated domain API pending (current route is counters-derived projection) | surveillance dashboard lifecycle-depth gap | **closed in bounded pass:** implemented sovereign surveillance endpoint `/internal/v1/public-health/weekly-idsr` and wired BFF route to it | public health data |
| Public health weekly/outbreak/field operations are currently bounded to surveillance primitives | semantics and product-depth gap | **closed in bounded pass:** implemented sovereign lifecycle endpoints (`/public-health/weekly-idsr`, `/outbreaks`, `/field-operations`) and BFF route convergence | public health platform |
| AI registry service security posture and guard depth | unauthorized probe/test-command risk | tighten security config and add source-guard tests | AI/data governance |
| Data plane contract/endpoint parity still partial on multiple services | integration inconsistency | continue contract IT and endpoint inventory hardening across data services | data platform |
