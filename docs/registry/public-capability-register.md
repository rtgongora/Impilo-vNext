# Public Capability Register

The enumeration of record for what a person can do on Impilo **without signing in**,
per the Public-First Access Doctrine
([health-services-gateway-doctrine.md §13](../doctrine/health-services-gateway-doctrine.md)).
Every public capability rides a lane registered in the
[public-lane security ADR](../architecture/gateway-public-lane-security-adr.md) and is
guarded by `scripts/guard/check-public-lane.sh`.

Status vocabulary: **LIVE** (lane deployed and proven) · **PLANNED-Wn** (this program's
wave) · **DEFERRED** (doctrine-required, own program).

Last updated: 2026-07-23 (program start).

## Cross-domain foundations

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Public web shell (`/welcome/**`) with Back/Home continuity | one-ui-shell | PublicShell + middleware PUBLIC_PREFIXES | LIVE |
| Anonymous trust-header synthesis + forced public tenant | experience-bff | `PublicGatewayAnonymousDefaultsFilter` | LIVE (tenant unification: PLANNED-W1) |
| Service advisories (public banner) | guidance-service | `/internal/v1/public/gateway/advisory/*` | LIVE |
| Citizen service-status board | observability-service | `/internal/v1/public/gateway/service-status` | LIVE |
| Street map tiles (public MVT) | ndila-service | `/internal/v1/public/gateway/map/tiles/*` | LIVE |
| Anonymous client telemetry | experience-bff | `/internal/v1/public/gateway/client-telemetry` | LIVE |
| "Continue without signing in" + reasoned sign-in prompt components | one-ui-shell | shared public components | PLANNED-W6 |

## Find health services (TUSO / VARAPI / NDILA / NHUME)

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Facility directory search + profile | tuso-service | `/internal/v1/public/gateway/facilities/*` | LIVE |
| Need-first find-care search (service/geo/province) + facility detail | tuso + ndila (BFF orchestration) | `/internal/v1/public/gateway/find-care/*` | LIVE |
| Verified practitioners at a facility | varapi-service | `.../find-care/facilities/{id}/practitioners` | LIVE |
| Practitioner registration verify (anti-enumeration) | varapi-service | `/internal/v1/public/gateway/practitioners/verify/*` | LIVE |
| Provider badge QR scan | varapi-service | `.../practitioners/badge-scan` | LIVE |
| Facility licence/certificate verify + credential scan | tuso-service | `/internal/v1/public/facility-certificates/*` | LIVE |
| Sign-in trigger: save favourites, book, manage a profile | — | authenticated surfaces | LIVE (authenticated) |

## Emergency & disaster (DAIDZAI / NOMPILO / MADI)

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Verified emergency numbers, tap-to-call, danger-sign cards | one-ui-shell (config-driven) | `/welcome/emergency` | LIVE |
| Nompilo guided emergency triage (danger-sign escalation, safe actions) | one-ui-shell deterministic protocol | `/welcome/emergency` | LIVE |
| Urgency taxonomy in triage outcomes (emergency/urgency/routine) | one-ui-shell | triage-protocol.ts | PLANNED-W2 |
| Anonymous SOS / assistance request + honest status tracking | daidzai-service | `/internal/v1/public/gateway/sos*` | LIVE |
| Nearest emergency-capable care (GPS/province) | tuso + ndila | find-care lane (`service=EMERGENCY`) | LIVE |
| Public disaster/incident alerts | daidzai-service | `PublicDisasterAlertsController` (new) | PLANNED-W2 |
| Blood-donation drives & appeals | madi-service | `PublicDonationDrivesController` (new) | PLANNED-W2 |
| Full Disaster Mode surface (maps, shelters, reunification, misinformation control) | daidzai + guidance + ndila | — | DEFERRED |
| Temporary emergency identity (unconscious/unidentified; VITO/TSHEPO reconcile) | vito + tshepo | — | DEFERRED |
| SMS/USSD/voice low-bandwidth channels | channels/khuluma | — | DEFERRED |

## Nompilo public guide (GUIDANCE)

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Health education library (categories, articles, search) | guidance-service | `/internal/v1/public/gateway/guidance/education*` | LIVE |
| Trust-escalation explainers | guidance-service | `.../guidance/explain-steps*` | LIVE |
| Anonymous grounded Q&A (honesty-gated, rate-limited) | guidance-service | `POST .../guidance/ask` | LIVE |
| Sign-in trigger: save assessment, retrieve history, book, transmit to provider | — | authenticated | partially built (gateway W3 owns journey handoff) |

## Quality, safety & feedback (RITO / PATIENT-SAFETY)

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Anonymous complaint/compliment intake + claim-code tracking | rito-quality-safety-service | `/internal/v1/public/gateway/feedback*` | LIVE |
| Public provider/facility reputation reads | rito-quality-safety-service | `/v1/public/rito/reputation/*` | LIVE |
| Anonymous patient-safety (pharmacovigilance) reports | patient-safety-service | `/v1/public/patient-safety/reports` | LIVE |
| Anonymous incident reporting (accidents, hazards) | rito + daidzai | report + SOS lanes | LIVE |
| Sign-in trigger: track with responses, attach records, escalations | — | authenticated | LIVE (authenticated) |

## Participation (GET-INVOLVED)

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Idea/experience submission + claim-code tracking, idea board, test cohorts | participation-service | `/internal/v1/public/gateway/get-involved/*` | LIVE |

## Marketplace (MSIKA / PRODUCT-REGISTRY)

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Browse/search listings, catalogs, items, storefronts (indicative pricing) | msika-service | `PublicListingBrowseController` (new) | PLANNED-W3 |
| Approved vendors + verification badges | msika-flow-service | `PublicVendorController` (new) | PLANNED-W3 |
| Product registry search/detail | product-registry-service | `PublicProductController` (new) | PLANNED-W3 |
| Facility service catalogues | tuso-service | public facility profile | LIVE (extend if needed W3) |
| Temporary guest basket (client-side, claimed at sign-in — PD-7) | one-ui-shell | localStorage | PLANNED-W3 |
| Sign-in trigger: order, pay, submit prescription, track delivery, supplier account, institutional prices | — | authenticated | LIVE (authenticated) |

## Coverage & financing (RUVIMBO / COVERAGE)

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Compare payers, schemes, products, plan versions | coverage-service | `PublicPlanCatalogController` (new) | PLANNED-W4 |
| Public benefits + tariff views (approved fields) | coverage-service | same lane | PLANNED-W4 |
| Provider-network search | coverage-service | same lane | PLANNED-W4 |
| Benefit-terminology explainers + financing education | guidance-service | education category | PLANNED-W4 (content) |
| Anonymous cost estimator | costing/coverage | — | DEFERRED (cost-estimator seam open) |
| Sign-in trigger: membership, personal eligibility, authorisations, claims | — | authenticated | LIVE (authenticated) |

## Wellness (SIMBA)

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Health education by life stage/condition | guidance-service | education lane | LIVE |
| Screening & immunisation schedule definitions | simba-service | `PublicWellnessController` (new) | PLANNED-W5 |
| One-off compute-only calculators (BMI, calorie, hydration, risk) | simba-service | same lane (anonymous compute, nothing retained) | PLANNED-W5 |
| Anonymous self-assessments | simba-service | same lane | PLANNED-W5 |
| Programme previews | simba-service | same lane | PLANNED-W5 |
| Unsaved wellness plan (guest session, save-on-sign-in) | one-ui-shell | sessionStorage | PLANNED-W5 |
| Sign-in trigger: tracking (steps/weight/BP/glucose/sleep), diaries, goals, devices, personalised reminders, provider sharing, longitudinal record | — | authenticated `/wellness/*` | LIVE (authenticated) |

## Learning (FUNDO)

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Public course catalog + course structure | learning-service | `PublicCourseCatalogController` (new) | PLANNED-W5 |
| Certificate verification | credential-verification-service | `/v1/public/verify/{token}` | LIVE |
| Sign-in trigger: enrolment, progress, assessed learning, certificates, CPD credits | — | authenticated `/learning/*` | LIVE (authenticated) |

## Regulatory & professional information (TUSO / VARAPI / ORG-REGISTRY)

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Facility licensing: application types, classifications, fee schedules, rules, stages | tuso-service | `PublicRegulatoryRequirementsController` (new) | PLANNED-W2 |
| Professional registration: categories, councils, requirements, CPD rules | varapi-service | `PublicRegistrationRequirementsController` (new) | PLANNED-W2 |
| Sign-in trigger: start/manage applications, upload documents, pay fees, decisions, compliance tracking | — | authenticated | LIVE (authenticated) |

## Support & communication (KHULUMA)

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Guest web chat / helpdesk with temporary conversation code + authenticated upgrade | khuluma-service | — (service currently rejects guests by design) | DEFERRED (own program) |
| Anonymous enquiries meanwhile | guidance ask + feedback lanes | LIVE lanes | LIVE (interim) |

## Telemedicine

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Explore telemedicine services, clinicians, availability | msika listings + find-care virtual-care fields | W3 marketplace + existing find-care `virtualCare` | PLANNED-W3 (coordinate with telemedicine program) |
| Sign-in trigger: join consultation, consent, clinical exchange, prescriptions | — | authenticated (telemedicine program) | in build (telemedicine program) |

## Patient shares & tokens (existing continuity patterns)

| Capability | Owning service | Lane | Status |
|---|---|---|---|
| Patient-share claim (code → OTP → step-up → workspace) | vito-service | `/internal/v1/public/patient-shares/*` | LIVE |
| Share-slip claim/verify; fundraiser share token | share-slip / mushe | `/v1/public/share/*`, bill-contributions token | LIVE |
