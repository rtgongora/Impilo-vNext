# Core Transaction Completion Matrix

> Generated: 2026-06-07T13:42:14.136Z
> Journeys: **46** | Transaction-complete: **16**
> Regenerate: `node scripts/product/generate-core-transaction-maps.mjs`

## Classification counts

| Classification | Count |
|----------------|------:|
| transaction-complete | 16 |
| backend-ready-but-frontend-incomplete | 11 |
| backend-partial | 17 |
| mobile-missing | 2 |

## Coverage

| Metric | Count |
|--------|------:|
| Backend services mapped to journeys | 57 |
| Backend services unmapped | 34 |
| Unmapped user-facing (needs review) | 2 |
| Frontend routes mapped to journeys | 366 |

## Completion matrix

| journey | type | classification | routes | mobile | gap |
| --- | --- | --- | --- | --- | --- |
| Citizen / Client Onboarding | ADMINISTRATIVE_HEALTH | backend-partial | 4 | 2 | card ops / pickup verification thin |
| Provider Login & Role Activation | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 6 | 2 | device block UX admin-only |
| Workspace / Shift Context Selection | ADMINISTRATIVE_HEALTH | backend-partial | 8 | 0 | workspace settings coming-soon stub |
| Facility Context Selection | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 2 | 57 | digital readiness dashboards thin |
| Patient Search & Selection | FACILITY_WALK_IN | backend-ready-but-frontend-incomplete | 36 | 0 | — |
| Queue / Walk-in Registration | FACILITY_WALK_IN | transaction-complete | 7 | 1 | — |
| Provider Patient Encounter | FACILITY_WALK_IN | transaction-complete | 3 | 1 | pathway execution orchestration partial |
| Outpatient Consultation | FACILITY_WALK_IN | transaction-complete | 50 | 3 | — |
| Inpatient Admission Workflow | EMERGENCY | transaction-complete | 36 | 2 | — |
| Telemedicine Encounter | TELEMEDICINE | backend-partial | 3 | 3 | real-time media transport blocked |
| Lab Order & Result | LABORATORY | transaction-complete | 7 | 3 | — |
| Imaging Order & Result | IMAGING | transaction-complete | 35 | 1 | — |
| Prescription & Dispense | PHARMACY | transaction-complete | 6 | 1 | — |
| Referral Create & Manage | REFERRAL | transaction-complete | 2 | 1 | — |
| Appointment Scheduling | APPOINTMENT | transaction-complete | 8 | 2 | — |
| Consent Capture | ADMINISTRATIVE_HEALTH | backend-partial | 8 | 1 | admin workflow parity vs mvumo templates |
| Payment / Billing / Exemption / Claim | ADMINISTRATIVE_HEALTH | backend-partial | 28 | 4 | payer-ops stubs; MusheX raw paths not in browser |
| Document Upload / Scan / Index | ADMINISTRATIVE_HEALTH | backend-partial | 1 | 1 | indexing UX partial vs document-service |
| Dispatch / Delivery (NHUME) | MARKETPLACE | backend-partial | 18 | 2 | dispatch detail + offline queue UX |
| Notification & Communications | ADMINISTRATIVE_HEALTH | backend-partial | 3 | 3 | campaign admin thin |
| Fundo / Learning Journey | TRAINING_OR_COMPETENCY | backend-ready-but-frontend-incomplete | 54 | 2 | mobile learning shell shallow |
| Data / Report / Dashboard Journey | ADMINISTRATIVE_HEALTH | backend-partial | 18 | 3 | Ndila map dashboards incomplete |
| Registry Administration | ADMINISTRATIVE_HEALTH | backend-partial | 34 | 1 | issuance/card ops not fully surfaced |
| Integration / Sync / Replay | ADMINISTRATIVE_HEALTH | backend-partial | 6 | 0 | integration status partial |
| Device / System Event Journey | ADMINISTRATIVE_HEALTH | backend-partial | 48 | 0 | no direct citizen UI (by design) |
| Health ID Issuance & Card Ops | ADMINISTRATIVE_HEALTH | backend-partial | 16 | 1 | card pickup page blocked |
| Marketplace Order | MARKETPLACE | backend-partial | 12 | 7 | booking list unavailable |
| Wellness & Lifestyle Journey | WELLNESS | transaction-complete | 13 | 1 | — |
| Social / Community / Timeline | WELLNESS | backend-ready-but-frontend-incomplete | 6 | 11 | public health alerts placeholder in rail |
| Public Health / CHW Outreach | COMMUNITY_OUTREACH | mobile-missing | 5 | 6 | field ops mobile thinner than web |
| Civil Registration (UBOMI / CRVS) | ADMINISTRATIVE_HEALTH | mobile-missing | 20 | 0 | mobile CRVS parity missing |
| Coverage Enrollment | ADMINISTRATIVE_HEALTH | backend-partial | 1 | 1 | intelligence surfaces partial |
| Wallet Payment | MARKETPLACE | backend-ready-but-frontend-incomplete | 6 | 1 | — |
| Offline Clinical Queue | FACILITY_WALK_IN | backend-partial | 25 | 4 | offline conflict UX |
| Emergency / ED Encounter | EMERGENCY | transaction-complete | 36 | 2 | — |
| Core Transaction Orchestration Shell | FACILITY_WALK_IN | transaction-complete | 4 | 0 | — |
| Surveillance / Outbreak Response | COMMUNITY_OUTREACH | backend-ready-but-frontend-incomplete | 6 | 2 | Ndila map dashboards incomplete |
| AI Guidance / Nompilo Assist | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 21 | 2 | route context not always passed to guidance BFF |
| Credential Verification | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 1 | 0 | verification workflow screens thin |
| Provider Registry Onboarding | ADMINISTRATIVE_HEALTH | backend-partial | 19 | 4 | reconciliation queue thin |
| Citizen Remote Monitoring | CHRONIC_CARE | backend-ready-but-frontend-incomplete | 6 | 1 | monitoring depth vs wellness BFF |
| Chronic Care Management | CHRONIC_CARE | backend-ready-but-frontend-incomplete | 40 | 1 | care plan UX depth |
| Blood Donation & Donor Engagement | BLOOD_DONATION | transaction-complete | 8 | 11 | — |
| Blood Order & Crossmatch | BLOOD_ORDER | transaction-complete | 2 | 12 | — |
| Transfusion Episode & Bedside Verify | TRANSFUSION | transaction-complete | 2 | 11 | — |

_…and 1 more in JSON/CSV._


## Recommended first completion batch

Prioritize the clinical spine and **complete orchestration on existing surfaces** (wire fixtures to BFF, close write gaps, extend mobile parity). New UI remains in scope when a journey map requires a missing step or screen — this is sequencing, not a moratorium on new surfaces.

1. **Provider Patient Encounter** — transaction-complete: pathway execution orchestration partial
1. **Core Transaction Orchestration Shell** — transaction-complete: 
1. **Health ID Issuance & Card Ops** — backend-partial: card pickup page blocked
1. **Payment / Billing / Exemption / Claim** — backend-partial: payer-ops stubs; MusheX raw paths not in browser
1. **Lab Order & Result** — transaction-complete: 
1. **Public Health / CHW Outreach** — mobile-missing: field ops mobile thinner than web
1. **Civil Registration (UBOMI / CRVS)** — mobile-missing: mobile CRVS parity missing

## Unmapped backend services (supporting/internal)

- ai-model-registry-service
- analytics-pipeline-service
- asset-registry-service
- audit-ledger-service
- booking-service
- butano-fhir
- clinical-knowledge-platform-service
- data-access-governance-service
- data-governance-service
- data-ingestion-service
- data-pipeline-service
- developer-portal-service
- forms-service
- general-ledger-service
- hr-payroll-service
- inventory-elmis-adapter
- inventory-service
- jobs-service
- live-service
- msika-apps-service
- national-data-repository-service
- ndr-service
- observability-service
- pharmacy-elmis-adapter
- procurement-service
- rules-service
- schema-registry-service
- security-hardening-service
- support-service
- tshepo-audit-service


## Unmapped user-facing services (review required)

- booking-service
- live-service
