# Core Transaction Journey Maps

> Generated: 2026-06-05T08:46:44.674Z
> Journeys discovered: **42**
> Regenerate: `node scripts/product/generate-core-transaction-maps.mjs`

See [CORE_TRANSACTION_ORCHESTRATION_DOCTRINE.md](./CORE_TRANSACTION_ORCHESTRATION_DOCTRINE.md) for spine doctrine.

## Summary

| journey | type | initiator | entry | status | classification |
| --- | --- | --- | --- | --- | --- |
| Citizen / Client Onboarding | ADMINISTRATIVE_HEALTH | citizen | /auth/register | partial | backend-ready-but-frontend-incomplete |
| Provider Login & Role Activation | ADMINISTRATIVE_HEALTH | provider | /auth/login/provider-id | wired | backend-ready-but-frontend-incomplete |
| Workspace / Shift Context Selection | ADMINISTRATIVE_HEALTH | provider | /workspace | partial | backend-ready-but-frontend-incomplete |
| Facility Context Selection | ADMINISTRATIVE_HEALTH | provider | /facility | wired | backend-ready-but-frontend-incomplete |
| Patient Search & Selection | FACILITY_WALK_IN | provider | /queue/search | wired | backend-ready-but-frontend-incomplete |
| Queue / Walk-in Registration | FACILITY_WALK_IN | provider | /queue/walk-in | wired | backend-ready-but-frontend-incomplete |
| Provider Patient Encounter | FACILITY_WALK_IN | provider | /ehr/[patientId]/encounter/[encounterId] | wired | transaction-complete |
| Outpatient Consultation | FACILITY_WALK_IN | provider | /clinical | partial | backend-ready-but-frontend-incomplete |
| Inpatient Admission Workflow | EMERGENCY | provider | /ehr/[patientId]/inpatient | partial | backend-partial |
| Telemedicine Encounter | TELEMEDICINE | provider | /telemedicine | partial | backend-ready-but-frontend-incomplete |
| Lab Order & Result | LABORATORY | provider | /ehr/[patientId]/orders | partial | backend-ready-but-frontend-incomplete |
| Imaging Order & Result | IMAGING | provider | /ehr/[patientId]/imaging | wired | backend-ready-but-frontend-incomplete |
| Prescription & Dispense | PHARMACY | provider | /ehr/[patientId]/medications | wired | backend-ready-but-frontend-incomplete |
| Referral Create & Manage | REFERRAL | provider | /ehr/[patientId]/referrals | wired | backend-ready-but-frontend-incomplete |
| Appointment Scheduling | APPOINTMENT | citizen | /queue/scheduled | partial | backend-partial |
| Consent Capture | ADMINISTRATIVE_HEALTH | citizen | /consent | wired | backend-ready-but-frontend-incomplete |
| Payment / Billing / Exemption / Claim | ADMINISTRATIVE_HEALTH | citizen | /finance | partial | backend-ready-but-frontend-incomplete |
| Document Upload / Scan / Index | ADMINISTRATIVE_HEALTH | provider | /ehr/[patientId]/documents | partial | backend-partial |
| Dispatch / Delivery (NHUME) | MARKETPLACE | courier | /operations/dispatch | partial | backend-partial |
| Notification & Communications | ADMINISTRATIVE_HEALTH | platform | /communication | wired | backend-ready-but-frontend-incomplete |
| Fundo / Learning Journey | TRAINING_OR_COMPETENCY | provider | /learning | partial | backend-ready-but-frontend-incomplete |
| Data / Report / Dashboard Journey | ADMINISTRATIVE_HEALTH | data-analyst | /reports | partial | backend-partial |
| Registry Administration | ADMINISTRATIVE_HEALTH | registry-administrator | /registry | partial | backend-ready-but-frontend-incomplete |
| Integration / Sync / Replay | ADMINISTRATIVE_HEALTH | integration-system | /developer | partial | backend-partial |
| Device / System Event Journey | ADMINISTRATIVE_HEALTH | device | system ingress (no UI) | partial | backend-partial |
| Health ID Issuance & Card Ops | ADMINISTRATIVE_HEALTH | registry-administrator | /operations/vito | partial | backend-partial |
| Marketplace Order | MARKETPLACE | citizen | /marketplace | partial | backend-ready-but-frontend-incomplete |
| Wellness & Lifestyle Journey | WELLNESS | citizen | /wellness | partial | backend-ready-but-frontend-incomplete |
| Social / Community / Timeline | WELLNESS | citizen | /social | wired | backend-ready-but-frontend-incomplete |
| Public Health / CHW Outreach | COMMUNITY_OUTREACH | community-health-worker | /public-health | partial | mobile-missing |
| Civil Registration (UBOMI / CRVS) | ADMINISTRATIVE_HEALTH | registry-administrator | /registry | partial | mobile-missing |
| Coverage Enrollment | ADMINISTRATIVE_HEALTH | citizen | /coverage | partial | backend-ready-but-frontend-incomplete |
| Wallet Payment | MARKETPLACE | citizen | /wallet | wired | backend-ready-but-frontend-incomplete |
| Offline Clinical Queue | FACILITY_WALK_IN | provider | mobile offline mode | partial | backend-partial |
| Emergency / ED Encounter | EMERGENCY | provider | /clinical/emergency | partial | trust-security-incomplete |
| Core Transaction Orchestration Shell | FACILITY_WALK_IN | platform | /core-transaction | wired | transaction-complete |
| Surveillance / Outbreak Response | COMMUNITY_OUTREACH | health-information-officer | /public-health | partial | backend-ready-but-frontend-incomplete |
| AI Guidance / Nompilo Assist | ADMINISTRATIVE_HEALTH | citizen | /ask | partial | backend-ready-but-frontend-incomplete |
| Credential Verification | ADMINISTRATIVE_HEALTH | facility-administrator | /verify | partial | backend-partial |
| Provider Registry Onboarding | ADMINISTRATIVE_HEALTH | registry-administrator | /registry/providers | partial | backend-ready-but-frontend-incomplete |
| Citizen Remote Monitoring | CHRONIC_CARE | citizen | /monitoring | partial | backend-ready-but-frontend-incomplete |
| Chronic Care Management | CHRONIC_CARE | provider | /ehr/[patientId] | partial | backend-partial |

## Journey detail (sample — full data in JSON/CSV)

### Citizen / Client Onboarding

- **Initiating actor:** citizen
- **Responding actor:** platform-registry
- **Transaction object:** Health ID + person anchor
- **Context:** self-service registration; assurance level pending
- **Entry point:** /auth/register
- **Steps:** register → assurance → identity resolution → Health ID issued → consent review
- **Backend services:** vito-service, tshepo-identity-service, identity-assurance-service, tshepo-consent-service, experience-bff
- **APIs:** /internal/v1/auth/*, /internal/v1/identity/*, /internal/v1/vito/*
- **Web routes:** /auth/register, /auth/register/assurance, /auth/register/status, /id-services
- **Mobile screens:** apps/mobile/citizen-app/src/screens/personal/HealthIdSection.tsx, apps/mobile/citizen-app/src/screens/personal/ProfileSection.tsx
- **Completion state:** IDENTITY_RESOLVED
- **Status:** partial — backend-ready-but-frontend-incomplete
- **PO acceptance test:** Citizen completes registration, receives Health ID, sees next-step guidance

### Provider Login & Role Activation

- **Initiating actor:** provider
- **Responding actor:** platform-trust
- **Transaction object:** authenticated session + Provider ID activation
- **Context:** facility-bound professional capacity
- **Entry point:** /auth/login/provider-id
- **Steps:** login → MFA → provider lookup → role activation → session established
- **Backend services:** tshepo-authz-service, tshepo-identity-service, varapi-service, experience-bff
- **APIs:** /internal/v1/auth/*, /internal/v1/identity/providers
- **Web routes:** /auth/login, /auth/login/biometric, /auth/login/email, /auth/login/provider-id, /auth/mfa…
- **Mobile screens:** apps/mobile/citizen-app/src/screens/LoginScreen.tsx, apps/mobile/provider-app/src/screens/LoginScreen.tsx
- **Completion state:** TRUST_CONTEXT_ESTABLISHED
- **Status:** wired — backend-ready-but-frontend-incomplete
- **PO acceptance test:** Provider signs in with Provider ID, activates role, lands in workspace

### Workspace / Shift Context Selection

- **Initiating actor:** provider
- **Responding actor:** platform-registry
- **Transaction object:** active workspace + shift context
- **Context:** department/ward/workspace under facility
- **Entry point:** /workspace
- **Steps:** select facility → select workspace → select shift → context headers injected
- **Backend services:** tuso-service, experience-bff
- **APIs:** /internal/v1/workspaces/*, /internal/v1/shifts/*, /internal/v1/facilities
- **Web routes:** /facility, /facility/[id], /shift, /shift/active, /shift/handover…
- **Mobile screens:** none mapped
- **Completion state:** TRUST_CONTEXT_ESTABLISHED
- **Status:** partial — backend-ready-but-frontend-incomplete
- **PO acceptance test:** Provider selects workspace/shift; subsequent requests carry trust context

### Facility Context Selection

- **Initiating actor:** provider
- **Responding actor:** tuso-service
- **Transaction object:** Facility ID context
- **Context:** regulated facility operating model
- **Entry point:** /facility
- **Steps:** facility search → select → X-Facility-ID set → queue/EHR unlocked
- **Backend services:** tuso-service, experience-bff
- **APIs:** /internal/v1/facilities, /internal/v1/registry/*
- **Web routes:** /facility, /facility/[id]
- **Mobile screens:** apps/mobile/provider-app/src/screens/provider/APGARScreen.tsx, apps/mobile/provider-app/src/screens/provider/ActivityFeedScreen.tsx, apps/mobile/provider-app/src/screens/provider/AdminRegistryHubScreen.tsx
- **Completion state:** ACCESS_GRANTED
- **Status:** wired — backend-ready-but-frontend-incomplete
- **PO acceptance test:** Provider selects facility; queue and EHR routes become available

### Patient Search & Selection

- **Initiating actor:** provider
- **Responding actor:** vito-service
- **Transaction object:** subject Health ID / CPID selection
- **Context:** facility queue or EHR entry
- **Entry point:** /queue/search
- **Steps:** search → match → select patient → open chart or queue item
- **Backend services:** vito-service, experience-bff, pct-service
- **APIs:** /internal/v1/vito/*, /internal/v1/identity/*, /internal/v1/queue
- **Web routes:** /ehr/[patientId], /ehr/[patientId]/advance-directives, /ehr/[patientId]/allergies, /ehr/[patientId]/assessments, /ehr/[patientId]/care-plans…
- **Mobile screens:** none mapped
- **Completion state:** IDENTITY_RESOLVED
- **Status:** wired — backend-ready-but-frontend-incomplete
- **PO acceptance test:** Provider searches patient, selects, opens chart with correct CPID


_Full 42 journeys in [core-transaction-journey-maps.json](../../reports/product/core-transaction-journey-maps.json)._
