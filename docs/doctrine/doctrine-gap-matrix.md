# Health OS Doctrine — Implementation Gap Matrix

> Living document. Updated 2026-07-12 with the National Health Services Gateway clause
> register (§8, GW-01…GW-08). Previously updated 2026-04-11 after expanded doctrine
> adoption (wellness/lifestyle/diet/sleep/clubs, graduated trust, progressive identity
> assurance, marketplace risk graduation, device/IoT as first-class participants) —
> earlier sections reflect that date; where they conflict with §8, §8 is current.

## Legend
- **DONE** — Implemented and aligned with doctrine
- **PARTIAL** — Infrastructure exists but incomplete
- **STRUCTURAL** — Requires new architectural components
- **PLANNED** — Identified, not yet started

---

## 1. Identity & Identifier Model

| Requirement | Status | Current State | Gap | Files |
|---|---|---|---|---|
| Health ID as person anchor | DONE | VITO issues Health ID, CPID pseudonym | — | `services/vito-service/` |
| Provider ID as regulated role | DONE | VARAPI issues Provider IDs | Linkage to Health ID not enforced at auth layer | `services/varapi-service/` |
| "Sign in as person, practice as provider" | PARTIAL | Two separate login paths exist; no explicit activation flow | Need Provider ID activation step post-login | `ui/experience/src/app/auth/` |
| Multi-class identifiers (6 classes) | PARTIAL | Actor/Context/Transaction IDs exist; Object/Record/Event IDs partial | Asset/Equipment IDs conflated; Care Plan ID missing | `contracts/health-os-identifiers.ts` |
| Caregiver linkage identifiers | STRUCTURAL | Not implemented | Need caregiver relationship model | — |

## 2. Header Contract

| Requirement | Status | Current State | Gap | Files |
|---|---|---|---|---|
| v1.2 headers in CompanionHeaders | DONE | All 18 headers defined | — | `libs/tech-companion/.../CompanionHeaders.java` |
| v1.2 headers in TrustHeaders | PARTIAL | Missing X-Provider-ID, X-Department-ID, X-Ward-ID, X-Programme-ID, X-Assurance-Level, X-Subject-ID | 6 headers need adding | `libs/tshepo-contracts/.../TrustHeaders.java` |
| Frontend injects v1.2 headers | DONE | api-client.ts sends all v1.2 headers | — | `ui/experience/src/lib/api-client.ts` |
| TypeScript contract types | DONE | health-os-identifiers.ts defines all 6 ID classes | — | `contracts/health-os-identifiers.ts` |
| AuthorizationRequest carries doctrine fields | PARTIAL | Missing providerId, departmentId, wardId, programmeId, assuranceLevel, subjectId | 6 fields need adding | `services/tshepo-service/.../AuthorizationRequest.java` |

## 3. Access Control (10 Dimensions)

| Dimension | Status | Current State | Gap |
|---|---|---|---|
| 1. Person identity | DONE | Actor ID in headers/JWT | — |
| 2. Active role | DONE | Roles from Keycloak JWT | — |
| 3. Attached role identifier | PARTIAL | Provider ID in headers but not in PolicyEngine | PolicyEngine needs Provider ID step |
| 4. Organizational affiliation | DONE | Tenant-scoped, facility check | — |
| 5. Facility/workspace context | DONE | Facility + workspace in headers and guards | — |
| 6. Subject relationship | PARTIAL | Consent check exists for Patient* resources | X-Subject-ID not yet in AuthorizationRequest |
| 7. Purpose of use | DONE | Header injected, PolicyEngine Step 2 validates | — |
| 8. Consent/legal basis | DONE | tshepo-consent-service evaluates consent | — |
| 9. Assurance level | STRUCTURAL | Not implemented | Need LOA1–LOA4 gate in PolicyEngine |
| 10. Workflow state | STRUCTURAL | Not implemented | Need workflow state in auth context |

## 4. Unified Experience Shell

| Requirement | Status | Current State | Gap |
|---|---|---|---|
| One coherent experience shell | PARTIAL | `ui/experience/` is primary (219 routes, 26 zones); canonical replacements are tracked in `sidecar-retirement-ledger-v2.ts` and surfaced at `/admin/sidecar-retirement` | Remaining partials are governance, citizen self-service parity, and blocked backend contracts |
| Role-based adaptation | DONE | AuthGuardProvider with 13 role groups | — |
| Citizen experience | PARTIAL | Citizen pages in experience + separate portal app | Consolidate portal into experience |
| Provider experience | PARTIAL | Clinical/EHR zones in experience + separate EHR app | Consolidate EHR into experience |
| Caregiving experience | STRUCTURAL | Not implemented | Need caregiver zone with delegated views |
| Wellness experience | STRUCTURAL | Not implemented | Need wellness zone (prevention, fitness, health tips) |
| Remote monitoring | STRUCTURAL | IoT ingestion service exists; no user-facing surface | Need monitoring dashboard zone |
| Service discovery | STRUCTURAL | Marketplace exists; no discovery-focused zone | Need provider/facility search zone |
| Device/agent interactions | STRUCTURAL | IoT service exists; no user-facing management | Need device management zone |
| In-session role switching | STRUCTURAL | Users must re-login to change roles | Need role context selector |

## 5. Mobile Experience

| Requirement | Status | Current State | Gap |
|---|---|---|---|
| Unified mobile shell | STRUCTURAL | citizen-app and provider-app are separate React Native codebases | Should merge into single role-adaptive app |
| Shared mobile auth | PARTIAL | Both use shared `packages/auth` | Login is separate per app |
| Provider ID activation on mobile | STRUCTURAL | Not implemented | Need provider activation flow in mobile |

## 6. Audit & Traceability (10 Fields)

| Field | Status | Current State |
|---|---|---|
| 1. Person anchor (Health ID) | DONE | Actor ID in audit events |
| 2. Active role | DONE | Actor type in audit events |
| 3. Role-linked identifier | PARTIAL | Provider ID not yet in audit events |
| 4. Organizational context | DONE | Tenant ID in audit events |
| 5. Facility/workspace | DONE | Facility ID in audit events |
| 6. Subject of action | PARTIAL | Resource ID captured, not explicit Subject ID |
| 7. Transaction/record instance | DONE | Resource type + ID in audit events |
| 8. App/module used | PARTIAL | Service-ID header exists but not app-level |
| 9. Purpose of use | DONE | Purpose of use in audit events |
| 10. Time/channel/event context | DONE | Timestamp, correlation ID, request ID |

## 7. Asset & Equipment

| Requirement | Status | Current State | Gap |
|---|---|---|---|
| Asset ID as first-class | DONE | asset-registry-service exists | — |
| Equipment ID as separate class | STRUCTURAL | Equipment conflated with assets | Need separate equipment model or equipment type |
| Device-equipment linkage | STRUCTURAL | IoT devices not linked to equipment assets | Need device→equipment relationship |

---

## 8. National Health Services Gateway (GW-01…GW-08)

Clause register for
[`health-services-gateway-doctrine.md`](health-services-gateway-doctrine.md); grounded
detail (endpoints, anchors, disproving searches) lives in
[`../architecture/gateway-experience-capability-map.md`](../architecture/gateway-experience-capability-map.md);
build sequence in
[`../roadmaps/health-services-gateway-roadmap.md`](../roadmaps/health-services-gateway-roadmap.md).

| Clause | Requirement | Status | Current State | Gap | Files |
|---|---|---|---|---|---|
| GW-01 | One citizen experience, three doors (doctrine §1) | PARTIAL | `/welcome` public landing exists; static brochure owns `/`,`/services`,… at Traefik; seam ungoverned (brochure source outside repo) | Brochure↔gateway handoff, "Get Health Services" CTA, PD-1 ownership decision | `ui/one-ui-shell/src/app/welcome/`, `deploy/tls/mohcc-gov/public-website.yaml` |
| GW-02 | Intent-led gateway, nine pillars (§2) | PARTIAL | W1/W2: `/welcome` intent home + semantic intent primitive; public lanes for facilities, guidance (now with `q` text search), practitioner verify, SOS. **UI catch-up (Jul 2026): mobile is now a coordinated third surface** — the citizen Expo app gained guest gateway screens (R1 onboarding, health-info browse/search, practitioner/facility verify, SOS track) over a new anonymous `publicApiClient` seam, mirroring web | Remaining per-pillar public lanes (cover plan-browse, marketplace browse); the 5 sign-in pillars are W3–W6 backend | `ui/one-ui-shell/src/app/welcome/page.tsx`, `ui/one-ui-shell/src/lib/gateway-intent.ts`, `apps/mobile/citizen-app/src/screens/gateway/`, `services/experience-bff/.../PublicGateway*BffController.java` |
| GW-03 | Public naming doctrine (§3) | PARTIAL | W0 landed: naming dictionary (`config/gateway-public-naming.yml`) + `scripts/guard/check-public-lane.sh` (parity/registry/naming checks, informational) + Envoy public-lane reconciliation (identical `/internal/v1/public/` bypass-with-strip in both configs) | Guard becomes strict (GATEWAY_LANE_STRICT=1) once W1 lands; wire into CI | `config/gateway-public-naming.yml`, `scripts/guard/check-public-lane.sh`, `infra/envoy/envoy.yaml`, `infra/envoy/envoy-runtime.yaml`, `docs/architecture/gateway-public-lane-security-adr.md` |
| GW-04 | Progressive trust ladder R0–R5 (§4) | PARTIAL | R2–R5 enforced end-to-end; W1 backend: R0 API lanes, R1 `CONTACT_VERIFIED` attestation + BFF OTP contact-verify/registration (fail-closed, rate-limited) + `HttpSmsProvider` + V013 templates. **UI catch-up (Jul 2026): the R1 rung now has a front door on BOTH surfaces** — web contact-first OTP account creation (`/auth/register/contact`, shared `OtpCodeInput`, intent-preserving, now the primary "Create account" CTA; assurance=UNVERIFIED) + mobile `ContactSignUpScreen` (Expo) over a new anonymous `publicApiClient` seam (the mobile api-client previously had no pre-auth path — that gap also broke on-device password registration, now fixed). **OTP *login* still not built** (KEYCLOAK-GATE) — OTP proves the channel at registration, password remains the login credential | R1 entitlement enforcement at PDP (PD-2), resumable server-side drafts (PD-5, W3), live-estate `gateway-reachable` journey | `ui/one-ui-shell/src/app/auth/register/contact/page.tsx`, `ui/one-ui-shell/src/components/auth/OtpCodeInput.tsx`, `apps/mobile/citizen-app/src/screens/gateway/ContactSignUpScreen.tsx`, `apps/mobile/packages/mobile-api-client/src/client.ts` |
| GW-05 | Persistent Emergency Help (§7) | PARTIAL | W1: persistent `EmergencyHelpButton`. W2 (rig-proven 7/7): anonymous SOS write lane + daidzai V002 callback-verification dispatch gate (triage 409s until verified). **UI catch-up (Jul 2026): the operator + tracking surfaces now exist** — web dispatcher `/work/daidzai/verify-callbacks` console (AWAITING_CALLBACK worklist + tel: callback + "Mark callback verified" releasing the gate), public SOS status-by-reference tracking (`/welcome/emergency/track` → PII-free `GET /public/gateway/sos/{reference}`, rig-proven 10/10) + "Track this request" receipt link, GPS "share my location" on the assistance form, and mobile SOS GPS + `TrackByReferenceScreen` | Automated callback-OTP (manual dispatcher callback today); mobile offline emergency screen | `ui/one-ui-shell/src/app/work/daidzai/verify-callbacks/page.tsx`, `ui/one-ui-shell/src/app/welcome/emergency/track/page.tsx`, `services/experience-bff/.../PublicGatewaySosBffController.java`, `apps/mobile/citizen-app/src/screens/emergency/` |
| GW-06 | Feedback first-class incl. anonymous (§2 P7) | PARTIAL | Rito case intake/tracking BUILT for authenticated citizens | Anonymous intake + claim-code tracking (patient-shares pattern) — W4 | `services/rito-quality-safety-service/`, `experience-bff/.../RitoPersonaController.java` |
| GW-07 | Nompilo mediates trust escalation (§9) | PARTIAL | W1: guidance `PublicGuidanceController` (V012 explainers) + public BFF lane + `GatewayEscalationExplainer`. **UI catch-up (Jul 2026): explainer coverage broadened beyond login-only** — now mounted on the R1 contact-register page (`verify-contact`) and the welcome intent home (`signin-to-personal`, explaining progressive trust to guests) | Explainer coverage for the remaining W2+ transitions; i18n; per-escalation coverage check | `services/guidance-service/.../PublicGuidanceController.java`, `ui/one-ui-shell/src/components/intelligent/GatewayEscalationExplainer.tsx`, `ui/one-ui-shell/src/app/auth/register/contact/page.tsx`, `ui/one-ui-shell/src/app/welcome/page.tsx` |
| GW-08 | Cover & payments NHI-ready + safeguards (§6) | PARTIAL | coverage-service model (enrolment/eligibility/preauth/claims/subsidy) + COSTA bills/waivers + MusheX BUILT; no public plan-browse; §6.2 configurability and §6.3 safeguards unverified | Public explainers lane; safeguard verification (esp. emergency-never-blocked, no vulnerability flags); benefit-package versioning proof | `services/coverage-service/`, `services/costing-engine-service/`, `services/mushex-service/` |

> Note: §3 row 9 ("Assurance level — STRUCTURAL") predates the G-CZO-01 closure; GW-04
> carries the current truth (assurance enforcement is BUILT in tshepo-authz).

## Priority Roadmap

### Phase 1: Trust Layer Alignment (Immediate) — DONE
1. ~~Extend `TrustHeaders.java` with 6 missing v1.2 headers~~ DONE (886f33a)
2. ~~Extend `AuthorizationRequest.java` with providerId, assuranceLevel, subjectId~~ DONE (886f33a)
3. ~~Add PolicyEngine Steps 7–8 (Provider ID + Assurance Level)~~ DONE (886f33a)

### Phase 2: Experience Shell Enrichment (Near-term) — DONE
4. ~~Add wellness, caregiving, remote monitoring, service discovery zones~~ DONE (4 new zones, 24 routes)
5. ~~Implement Provider ID activation flow (post-login step)~~ DONE (/provider/activate page)
6. ~~Add `provider` guard to AuthGuardProvider~~ DONE
7. ~~useAuthStore: activateProvider/deactivateProvider/hasActiveProvider~~ DONE
8. Accelerate sidecar app retirement — IN PROGRESS (tracked in `ui/experience/src/lib/sidecar-retirement-ledger-v2.ts`)

### Phase 3: Full Doctrine Completion (Mid-term) — DONE
9. ~~Separate Equipment from Assets~~ DONE (EquipmentEntity, V004 migration, repository)
10. ~~Implement caregiver linkage model~~ DONE (VITO V020 migration, useCaregiverLinkage hook, TS contract)
11. ~~Add workflow state to access control~~ DONE (x-workflow-state header end-to-end: CompanionHeaders, TrustHeaders, AuthorizationRequest, Envoy, TS contract)
12. ~~Mobile auth Provider ID support~~ DONE (activateProvider/deactivateProvider/hasActiveProvider)
13. Merge mobile apps into single role-adaptive codebase — DEFERRED (requires React Navigation restructure)
14. In-session role context switching (role selector in sidebar) — DEFERRED (Phase 4)
