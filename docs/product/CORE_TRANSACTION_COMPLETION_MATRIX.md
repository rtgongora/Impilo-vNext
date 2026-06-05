# Core Transaction Completion Matrix

## Phase 4 completion update (2026-06-05)

**Provider Patient Encounter** — orchestration linked on encounter page via `EncounterOrchestrationRail` and BFF `encounter_id` filter. Classification moves from `frontend-route-exists-but-disconnected` toward **orchestration-linked** (journey not yet fully `transaction-complete` until queue→transaction correlation ships).

> Generated: 2026-06-05T07:32:17.772Z
> Journeys: **42** | Transaction-complete: **0**
> Regenerate: `node scripts/product/generate-core-transaction-maps.mjs`

## Classification counts

| Classification | Count |
|----------------|------:|
| backend-ready-but-frontend-incomplete | 26 |
| backend-partial | 11 |
| frontend-route-exists-but-disconnected | 2 |
| mobile-missing | 2 |
| trust-security-incomplete | 1 |

## Coverage

| Metric | Count |
|--------|------:|
| Backend services mapped to journeys | 56 |
| Backend services unmapped | 31 |
| Unmapped user-facing (needs review) | 0 |
| Frontend routes mapped to journeys | 349 |

## Completion matrix

| journey | type | classification | routes | mobile | gap |
| --- | --- | --- | --- | --- | --- |
| Citizen / Client Onboarding | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 4 | 2 | card ops / pickup verification thin |
| Provider Login & Role Activation | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 6 | 2 | device block UX admin-only |
| Workspace / Shift Context Selection | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 8 | 0 | workspace settings coming-soon stub |
| Facility Context Selection | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 2 | 56 | digital readiness dashboards thin |
| Patient Search & Selection | FACILITY_WALK_IN | backend-ready-but-frontend-incomplete | 36 | 0 | — |
| Queue / Walk-in Registration | FACILITY_WALK_IN | backend-ready-but-frontend-incomplete | 7 | 1 | — |
| Provider Patient Encounter | FACILITY_WALK_IN | orchestration-linked (Phase 4) | 3 | 1 | queue→transaction_id correlation pending |
| Outpatient Consultation | FACILITY_WALK_IN | backend-ready-but-frontend-incomplete | 50 | 3 | EHR orders not yet writable via typed BFF command |
| Inpatient Admission Workflow | EMERGENCY | backend-partial | 36 | 2 | inpatient UX partial vs backend |
| Telemedicine Encounter | TELEMEDICINE | backend-ready-but-frontend-incomplete | 3 | 3 | real-time media transport blocked |
| Lab Order & Result | LABORATORY | backend-ready-but-frontend-incomplete | 7 | 3 | orders page read-only; BFF write contract gap |
| Imaging Order & Result | IMAGING | backend-ready-but-frontend-incomplete | 35 | 1 | — |
| Prescription & Dispense | PHARMACY | backend-ready-but-frontend-incomplete | 6 | 1 | mobile prescribing depth |
| Referral Create & Manage | REFERRAL | backend-ready-but-frontend-incomplete | 2 | 1 | — |
| Appointment Scheduling | APPOINTMENT | backend-partial | 5 | 2 | citizen booking UX thin |
| Consent Capture | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 8 | 1 | admin workflow parity |
| Payment / Billing / Exemption / Claim | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 28 | 4 | payer-ops stubs; MusheX raw paths not in browser |
| Document Upload / Scan / Index | ADMINISTRATIVE_HEALTH | backend-partial | 1 | 1 | indexing UX partial |
| Dispatch / Delivery (NHUME) | MARKETPLACE | backend-partial | 18 | 2 | dispatch detail + offline queue UX |
| Notification & Communications | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 3 | 3 | campaign admin thin |
| Fundo / Learning Journey | TRAINING_OR_COMPETENCY | backend-ready-but-frontend-incomplete | 54 | 2 | mobile learning shell shallow |
| Data / Report / Dashboard Journey | ADMINISTRATIVE_HEALTH | backend-partial | 18 | 2 | Ndila map dashboards incomplete |
| Registry Administration | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 34 | 1 | issuance/card ops not fully surfaced |
| Integration / Sync / Replay | ADMINISTRATIVE_HEALTH | backend-partial | 6 | 0 | integration status partial |
| Device / System Event Journey | ADMINISTRATIVE_HEALTH | backend-partial | 48 | 0 | no direct citizen UI (by design) |
| Health ID Issuance & Card Ops | ADMINISTRATIVE_HEALTH | backend-partial | 16 | 1 | card pickup page blocked |
| Marketplace Order | MARKETPLACE | backend-ready-but-frontend-incomplete | 12 | 6 | booking list unavailable |
| Wellness & Lifestyle Journey | WELLNESS | backend-ready-but-frontend-incomplete | 13 | 1 | routes map coming-soon |
| Social / Community / Timeline | WELLNESS | backend-ready-but-frontend-incomplete | 6 | 10 | public health alerts placeholder in rail |
| Public Health / CHW Outreach | COMMUNITY_OUTREACH | mobile-missing | 5 | 6 | field ops mobile thinner than web |
| Civil Registration (UBOMI / CRVS) | ADMINISTRATIVE_HEALTH | mobile-missing | 20 | 0 | mobile CRVS parity missing |
| Coverage Enrollment | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 1 | 1 | intelligence surfaces partial |
| Wallet Payment | MARKETPLACE | backend-ready-but-frontend-incomplete | 6 | 1 | — |
| Offline Clinical Queue | FACILITY_WALK_IN | backend-partial | 25 | 4 | offline conflict UX |
| Emergency / ED Encounter | EMERGENCY | trust-security-incomplete | 36 | 2 | ED flow depth vs backend |
| Core Transaction Orchestration Shell | FACILITY_WALK_IN | frontend-route-exists-but-disconnected | 4 | 0 | UI uses fixtures not live BFF |
| Surveillance / Outbreak Response | COMMUNITY_OUTREACH | backend-ready-but-frontend-incomplete | 6 | 2 | Ndila map dashboards incomplete |
| AI Guidance / Nompilo Assist | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 21 | 2 | route context not always passed |
| Credential Verification | ADMINISTRATIVE_HEALTH | backend-partial | 1 | 0 | verification workflow screens thin |
| Provider Registry Onboarding | ADMINISTRATIVE_HEALTH | backend-ready-but-frontend-incomplete | 19 | 4 | reconciliation queue thin |
| Citizen Remote Monitoring | CHRONIC_CARE | backend-ready-but-frontend-incomplete | 6 | 1 | monitoring depth |
| Chronic Care Management | CHRONIC_CARE | backend-partial | 40 | 1 | care plan UX depth |

## Recommended first completion batch

Prioritize the clinical spine and **complete orchestration on existing surfaces** (wire fixtures to BFF, close write gaps, extend mobile parity). New UI remains in scope when a journey map requires a missing step or screen — this is sequencing, not a moratorium on new surfaces.

1. **Provider Patient Encounter** — frontend-route-exists-but-disconnected: core-transaction pages use fixtures
1. **Core Transaction Orchestration Shell** — frontend-route-exists-but-disconnected: UI uses fixtures not live BFF
1. **Health ID Issuance & Card Ops** — backend-partial: card pickup page blocked
1. **Payment / Billing / Exemption / Claim** — backend-ready-but-frontend-incomplete: payer-ops stubs; MusheX raw paths not in browser
1. **Lab Order & Result** — backend-ready-but-frontend-incomplete: orders page read-only; BFF write contract gap
1. **Public Health / CHW Outreach** — mobile-missing: field ops mobile thinner than web
1. **Civil Registration (UBOMI / CRVS)** — mobile-missing: mobile CRVS parity missing

## Unmapped backend services (supporting/internal)

- ai-model-registry-service
- analytics-pipeline-service
- asset-registry-service
- audit-ledger-service
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
- tshepo-keys-service
- tshepo-offline-service
- tshepo-service


