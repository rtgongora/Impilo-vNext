# Core Transaction Completion Matrix

> Generated: 2026-06-08T02:14:50.545Z
> Journeys: **46** | Transaction-complete: **40**
> Regenerate: `node scripts/product/generate-core-transaction-maps.mjs`

## Classification counts

| Classification | Count |
|----------------|------:|
| transaction-complete | 40 |
| backend-partial | 4 |
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
| Citizen / Client Onboarding | ADMINISTRATIVE_HEALTH | transaction-complete | 4 | 2 | issuance queue ops depth |
| Provider Login & Role Activation | ADMINISTRATIVE_HEALTH | transaction-complete | 6 | 2 | — |
| Workspace / Shift Context Selection | ADMINISTRATIVE_HEALTH | transaction-complete | 8 | 0 | control-tower dashboards thin |
| Facility Context Selection | ADMINISTRATIVE_HEALTH | transaction-complete | 2 | 57 | digital readiness dashboards thin |
| Patient Search & Selection | FACILITY_WALK_IN | transaction-complete | 36 | 0 | — |
| Queue / Walk-in Registration | FACILITY_WALK_IN | transaction-complete | 7 | 1 | — |
| Provider Patient Encounter | FACILITY_WALK_IN | transaction-complete | 3 | 1 | pathway execution orchestration partial |
| Outpatient Consultation | FACILITY_WALK_IN | transaction-complete | 50 | 3 | — |
| Inpatient Admission Workflow | EMERGENCY | transaction-complete | 36 | 2 | — |
| Telemedicine Encounter | TELEMEDICINE | transaction-complete | 3 | 3 | RTC media transport preview-limited (lifecycle BFF wired) |
| Lab Order & Result | LABORATORY | transaction-complete | 7 | 3 | — |
| Imaging Order & Result | IMAGING | transaction-complete | 35 | 1 | — |
| Prescription & Dispense | PHARMACY | transaction-complete | 6 | 1 | — |
| Referral Create & Manage | REFERRAL | transaction-complete | 2 | 1 | — |
| Appointment Scheduling | APPOINTMENT | transaction-complete | 8 | 2 | — |
| Consent Capture | ADMINISTRATIVE_HEALTH | transaction-complete | 10 | 1 | remote-session admin depth |
| Payment / Billing / Exemption / Claim | ADMINISTRATIVE_HEALTH | transaction-complete | 28 | 4 | long-tail finance routes |
| Document Upload / Scan / Index | ADMINISTRATIVE_HEALTH | transaction-complete | 1 | 1 | indexing UX partial vs document-service |
| Dispatch / Delivery (NHUME) | MARKETPLACE | backend-partial | 18 | 2 | dispatch detail + offline queue UX |
| Notification & Communications | ADMINISTRATIVE_HEALTH | transaction-complete | 3 | 3 | template/campaign admin depth |
| Fundo / Learning Journey | TRAINING_OR_COMPETENCY | transaction-complete | 54 | 2 | mobile learning shell shallow (web catalogue/enrolment complete) |
| Data / Report / Dashboard Journey | ADMINISTRATIVE_HEALTH | transaction-complete | 18 | 3 | NDR/warehouse depth |
| Registry Administration | ADMINISTRATIVE_HEALTH | transaction-complete | 34 | 1 | council import queue thin |
| Integration / Sync / Replay | ADMINISTRATIVE_HEALTH | transaction-complete | 6 | 0 | adapter template admin thin |
| Device / System Event Journey | ADMINISTRATIVE_HEALTH | transaction-complete | 48 | 0 | ops admin surface only (by design) |
| Health ID Issuance & Card Ops | ADMINISTRATIVE_HEALTH | transaction-complete | 16 | 1 | pickup verify BFF routes missing |
| Marketplace Order | MARKETPLACE | backend-partial | 12 | 7 | booking list unavailable |
| Wellness & Lifestyle Journey | WELLNESS | transaction-complete | 13 | 1 | — |
| Social / Community / Timeline | WELLNESS | transaction-complete | 6 | 11 | — |
| Public Health / CHW Outreach | COMMUNITY_OUTREACH | mobile-missing | 5 | 6 | field ops mobile thinner than web |
| Civil Registration (UBOMI / CRVS) | ADMINISTRATIVE_HEALTH | mobile-missing | 20 | 0 | mobile CRVS parity missing |
| Coverage Enrollment | ADMINISTRATIVE_HEALTH | transaction-complete | 1 | 1 | intelligence surfaces partial |
| Wallet Payment | MARKETPLACE | transaction-complete | 6 | 1 | — |
| Offline Clinical Queue | FACILITY_WALK_IN | backend-partial | 25 | 4 | offline conflict UX |
| Emergency / ED Encounter | EMERGENCY | transaction-complete | 36 | 2 | — |
| Core Transaction Orchestration Shell | FACILITY_WALK_IN | transaction-complete | 4 | 0 | — |
| Surveillance / Outbreak Response | COMMUNITY_OUTREACH | transaction-complete | 6 | 2 | — |
| AI Guidance / Nompilo Assist | ADMINISTRATIVE_HEALTH | transaction-complete | 21 | 2 | — |
| Credential Verification | ADMINISTRATIVE_HEALTH | transaction-complete | 1 | 0 | — |
| Provider Registry Onboarding | ADMINISTRATIVE_HEALTH | backend-partial | 19 | 4 | reconciliation queue thin |
| Citizen Remote Monitoring | CHRONIC_CARE | transaction-complete | 6 | 1 | — |
| Chronic Care Management | CHRONIC_CARE | transaction-complete | 40 | 1 | — |
| Blood Donation & Donor Engagement | BLOOD_DONATION | transaction-complete | 8 | 11 | — |
| Blood Order & Crossmatch | BLOOD_ORDER | transaction-complete | 2 | 12 | — |
| Transfusion Episode & Bedside Verify | TRANSFUSION | transaction-complete | 2 | 11 | — |

_…and 1 more in JSON/CSV._


## Recommended first completion batch

Prioritize the clinical spine and **complete orchestration on existing surfaces** (wire fixtures to BFF, close write gaps, extend mobile parity). New UI remains in scope when a journey map requires a missing step or screen — this is sequencing, not a moratorium on new surfaces.

1. **Provider Patient Encounter** — transaction-complete: pathway execution orchestration partial
1. **Core Transaction Orchestration Shell** — transaction-complete: 
1. **Health ID Issuance & Card Ops** — transaction-complete: pickup verify BFF routes missing
1. **Payment / Billing / Exemption / Claim** — transaction-complete: long-tail finance routes
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
