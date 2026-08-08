# Product Truth — Frontend-to-Backend Traceability

> Generated: 2026-08-08T11:41:28.764Z
> Web surfaces: **945** | Mobile screens: **237**

## Web routes (one-ui-shell)

| Route | Title | Zone | BFF backing | Gateway | Reads real | Writes real | Mock/stub | Gaps |
|-------|-------|------|-------------|---------|------------|-------------|-----------|------|
| /bootstrap | Platform Bootstrap | auth | yes | no | yes | no | yes | — |
| /auth/login | Sign In | auth | yes | no | yes | no | yes | — |
| /auth/login/email | Sign In with Email | auth | no | no | yes | no | no | — |
| /auth/login/provider-id | Sign In with Provider ID | auth | yes | no | yes | yes | yes | — |
| /auth/login/biometric | Biometric Verification | auth | yes | no | yes | no | yes | — |
| /auth/login/scan | Scan to sign in | auth | yes | no | yes | no | yes | — |
| /auth/login/passkey/callback | Completing passkey sign-in | auth | yes | no | yes | no | yes | — |
| /auth/forgot-password | Forgot Password | auth | yes | no | yes | no | yes | — |
| /auth/reset-password | Reset Password | auth | yes | no | yes | no | yes | — |
| /auth/mfa | Multi-Factor Authentication | auth | yes | no | yes | no | yes | — |
| /auth/logout | Signing Out | auth | yes | no | yes | yes | yes | — |
| /auth | Authentication | auth | no | no | yes | no | no | — |
| /auth/register | Create Account | auth | no | no | yes | no | yes | — |
| /auth/register/contact | Create account with phone or email | auth | yes | no | yes | yes | yes | — |
| /auth/register/assurance | Identity Assurance | auth | yes | no | yes | yes | yes | — |
| /auth/register/status | Registration Status | auth | yes | no | yes | yes | yes | — |
| /auth/resolving | Resolving Session | auth | yes | no | yes | no | yes | — |
| /auth/context-chooser | Choose Work Context | auth | yes | no | yes | no | yes | — |
| / | Welcome to Impilo | auth | yes | no | yes | no | yes | — |
| /welcome | Welcome to Impilo | auth | yes | no | yes | no | yes | — |
| /welcome/find-care | Find Care | auth | yes | no | yes | no | yes | — |
| /welcome/emergency | Emergency & Public Health | auth | yes | no | yes | no | yes | — |
| /welcome/emergency/track | Track an Emergency Request | auth | yes | no | yes | no | yes | — |
| /welcome/health-info | Health Information | auth | yes | no | yes | no | yes | — |
| /welcome/accessibility | Accessibility & Language | auth | yes | no | yes | no | yes | — |
| /get-involved | Get Involved | auth | yes | no | yes | no | yes | — |
| /get-involved/idea | Share an Idea | auth | yes | no | yes | no | yes | — |
| /get-involved/board | Ideas Board | auth | yes | no | yes | no | yes | — |
| /get-involved/track | Track Your Idea | auth | yes | no | yes | no | yes | — |
| /get-involved/test | Help Test Impilo | auth | yes | no | yes | no | yes | — |
| /status | Service Status | auth | yes | no | yes | no | yes | — |
| /download | Get the Impilo Apps | auth | yes | no | yes | no | yes | — |
| /privacy | Privacy Policy | auth | no | no | yes | no | no | — |
| /terms | Terms of Use | auth | no | no | yes | no | no | — |
| /consent | Review Policies | auth | yes | no | yes | yes | yes | — |
| /account-deletion | Account Deletion | auth | no | no | yes | no | no | — |
| /privacy/app-stores | App Store Privacy | auth | no | no | yes | no | no | — |
| /clinical | Clinical Care | queue | yes | no | yes | no | yes | — |
| /core-transaction | Core Transaction | queue | yes | no | yes | yes | yes | — |
| /core-transaction/[transactionId] | Core Transaction Detail | queue | yes | no | yes | yes | yes | — |
| /client-journey | Client Journey | home | yes | no | yes | yes | yes | — |
| /provider-workspace | Provider Workspace | queue | no | no | yes | no | no | — |
| /provider-workspace/wellness | Wellness Workbench | wellness | yes | no | yes | yes | yes | — |
| /provider-workspace/wellness/social | Wellness-Social Workbench | wellness | yes | no | yes | yes | yes | — |
| /platform-journey | Platform Journey | admin | yes | no | yes | yes | yes | — |
| /clinical-tools | Clinical Tools | queue | yes | no | yes | yes | yes | — |
| /clinical-tools/rules | Rules Engine | queue | yes | no | yes | yes | yes | — |
| /clinical-tools/forms | Form Builder | queue | yes | no | yes | yes | yes | — |
| /clinical/chronic-registers | Chronic disease registers | queue | yes | no | yes | no | yes | — |
| /clinical/control-tower | Control Tower | queue | yes | no | yes | yes | yes | — |
| /clinical/dictation | Voice Dictation | queue | yes | no | yes | no | yes | — |
| /clinical/emergency | ED / Casualty | queue | yes | no | yes | yes | yes | — |
| /clinical/nutrition-tracing | Nutrition Defaulter Tracing | queue | yes | no | yes | yes | yes | — |
| /clinical/emergency/resus/[activationId] | Resuscitation | queue | yes | no | yes | yes | yes | — |
| /clinical/emergency/episode/[episodeId] | Emergency Episode | queue | yes | no | yes | yes | yes | — |
| /clinical/emergency/board | Emergency Board | queue | yes | no | yes | yes | yes | — |
| /clinical/emergency/command | Emergency Command | queue | yes | no | yes | yes | yes | — |
| /clinical/emergency/activation | Open Emergency Episode | queue | yes | no | yes | yes | yes | — |
| /clinical/emergency/pre-arrival | ED Pre-Arrival | queue | yes | no | yes | yes | yes | — |
| /clinical/emergency/analytics | Emergency Analytics | queue | yes | no | yes | yes | yes | — |
| /clinical/emergency/spine/[episodeId]/disposition | Episode Disposition | queue | yes | no | yes | yes | yes | — |
| /clinical/emergency/spine/[episodeId]/observation | Observation Stay | queue | yes | no | yes | yes | yes | — |
| /clinical/emergency/spine/[episodeId] | Emergency Episode Spine | queue | yes | no | yes | yes | yes | — |
| /clinical/emergency/[visitId] | ED Visit | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient | Inpatient Care | queue | yes | no | yes | no | yes | — |
| /clinical/inpatient/admissions | Inpatient Admissions | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/admissions/new | Admit a patient | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/admissions/[admissionId] | Inpatient Episode | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/ward-board | Ward Board | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/nursing | Nursing Workbench | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/rounds | Medical Rounds | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/discharge/[admissionId] | Inpatient Discharge | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/escalations | Deterioration Escalations | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/discharge-board | Discharge Board | queue | yes | no | yes | yes | yes | — |
| /production-command-centre | Production Command Centre | admin | yes | no | yes | no | yes | — |
| /platform/all-features | All Features | admin | yes | no | yes | no | yes | — |
| /health-os/command-centre | Health OS Command Centre | admin | no | no | yes | no | no | — |
| /data-intelligence | Data & Intelligence | reports | yes | no | yes | no | yes | — |
| /data-intelligence/quality | Data Quality | reports | yes | no | yes | no | yes | — |
| /data-intelligence/pipelines | Data Pipelines | reports | yes | no | yes | yes | yes | — |
| /data-intelligence/integration | Integration Monitor | reports | yes | no | yes | no | yes | — |
| /data-intelligence/reports | Reporting Hub | reports | yes | no | yes | no | yes | — |
| /data-intelligence/audit | Audit Intelligence | reports | yes | no | yes | no | yes | — |
| /ndila | Ndila Maps | operations | yes | no | yes | yes | yes | — |
| /public-health | Public Health | admin | yes | no | yes | yes | yes | — |
| /public-health/surveillance | Surveillance | admin | yes | no | yes | yes | yes | — |
| /public-health/campaigns | Campaigns | admin | yes | no | yes | yes | yes | — |
| /public-health/site-registry | Site Registry | admin | yes | no | yes | yes | yes | — |
| /public-health/site-registry/[siteId] | Site Profile | admin | yes | no | yes | yes | yes | — |
| /public-health/oversight | National oversight | admin | yes | no | yes | yes | yes | — |
| /omnichannel | Omnichannel Hub | admin | yes | no | yes | yes | yes | — |
| /coverage | Coverage Operations | admin | yes | no | yes | yes | yes | — |
| /coverage/enroll | Enroll in Coverage | home | yes | no | yes | yes | yes | — |
| /coverage/member | My Coverage | home | no | no | yes | no | no | — |
| /coverage/contracts | Provider Contracts | admin | yes | no | yes | yes | yes | — |
| /coverage/operations | Ruvimbo Operations | admin | yes | no | yes | yes | yes | — |
| /ruvimbo | Ruvimbo | home | yes | no | yes | no | yes | — |
| /ruvimbo/member | My Ruvimbo | home | yes | no | yes | yes | yes | — |
| /ruvimbo/provider | Ruvimbo Provider | admin | yes | no | yes | yes | yes | — |
| /ruvimbo/payer | Ruvimbo Payer | admin | yes | no | yes | yes | yes | — |
| /ruvimbo/administration | Ruvimbo Administration | admin | yes | no | yes | yes | yes | — |
| /ruvimbo/performance | Ruvimbo Performance | admin | yes | no | yes | yes | yes | — |
| /id-services | Identity Services | admin | yes | no | yes | yes | yes | — |
| /ai-governance | AI Governance | admin | yes | no | yes | yes | yes | — |
| /ai-governance/models/[id] | AI Model | admin | yes | no | yes | yes | yes | — |
| /access | Access Channels | admin | yes | no | yes | yes | yes | — |
| /kiosk | Self Check-In | auth | yes | no | yes | yes | yes | — |
| /home | Home | home | yes | no | yes | yes | yes | — |
| /home/notifications | Notifications | home | yes | no | yes | yes | yes | — |
| /home/profile | My Profile | home | yes | no | yes | yes | yes | — |
| /home/preferences | Preferences | home | yes | no | yes | yes | yes | — |
| /home/credentials | Credentials & CPD | home | yes | no | yes | yes | yes | — |
| /home/referrals | My Referrals | home | no | no | yes | no | no | — |
| /home/medications | My Medications | home | yes | no | yes | yes | yes | — |
| /home/conditions | My Conditions | home | yes | no | yes | no | yes | — |
| /home/allergies | My Allergies | home | yes | no | yes | no | yes | — |
| /home/results | My Results | home | yes | no | yes | no | yes | — |
| /home/bookings | My Bookings | home | yes | no | yes | yes | yes | — |
| /home/bookings/new | Book a Service | home | yes | no | yes | yes | yes | — |
| /home/bookings/[bookingId] | Booking Details | home | yes | no | yes | yes | yes | — |
| /home/appointments | My Appointments | home | yes | no | yes | yes | yes | — |
| /home/appointments/[appointmentId] | Appointment Details | home | yes | no | yes | yes | yes | — |
| /citizen | Citizen Services | home | yes | no | yes | yes | yes | — |
| /citizen/my-care | My Care | home | yes | no | yes | no | yes | — |
| /citizen/virtual-care | My Virtual Care Requests | home | yes | no | yes | yes | yes | — |
| /citizen/virtual-care/request | Request a Teleconsult | home | yes | no | yes | yes | yes | — |
| /citizen/health-id/qr | My Health ID QR | home | yes | no | yes | no | yes | — |
| /citizen/health-id/request | Request Health ID | home | yes | no | yes | yes | yes | — |
| /citizen/id-recovery | ID Recovery | home | yes | no | yes | no | yes | — |
| /citizen/delegated-pickup | Delegated Pickup | home | yes | no | yes | yes | yes | — |
| /citizen/record-sharing | Share My Record | home | yes | no | yes | yes | yes | — |
| /citizen/visit/[transactionId] | My Visit | home | yes | no | yes | no | yes | — |
| /citizen/inpatient/[admissionRef] | My Inpatient Stay | home | yes | no | yes | no | yes | — |
| /citizen/wallet | Mushe Personal Health Wallet | home | yes | no | yes | yes | yes | — |
| /citizen/wallet/identity | Identity & Trust | home | yes | no | yes | yes | yes | — |
| /citizen/wallet/profile | Profile & Corrections | home | yes | no | yes | yes | yes | — |
| /citizen/wallet/records | My Health Records | home | yes | no | yes | yes | yes | — |
| /citizen/wallet/timeline | Care Timeline | home | yes | no | yes | yes | yes | — |
| /citizen/wallet/dependants | Dependants & Proxy | home | yes | no | yes | yes | yes | — |
| /citizen/wallet/payments | Payments & Bills | home | yes | no | yes | yes | yes | — |
| /citizen/wallet/smart-card | My Digital SMART Card | home | yes | no | yes | yes | yes | — |
| /citizen/wallet/comms | Communication Preferences | home | yes | no | yes | yes | yes | — |
| /citizen/wallet/trust | Trust Profile | home | yes | no | yes | no | yes | — |
| /verify/credential | Verify Credential | home | no | no | yes | yes | no | — |
| /verify/facility-certificate | Verify Facility Certificate | home | yes | no | yes | yes | yes | — |
| /verify/practitioner | Verify a Health Professional | home | yes | no | yes | yes | yes | — |
| /professional/pic-nominations | PIC Nominations | professional | yes | no | yes | yes | yes | — |
| /professional/regulatory | My Regulatory Affairs | professional | yes | no | yes | no | yes | — |
| /professional/regulatory/applications/[id] | Regulatory Application | professional | yes | no | yes | no | yes | — |
| /professional/regulatory/apply/student/[applicationId] | Student registration | professional | yes | no | yes | yes | yes | — |
| /professional/regulatory/contribute/[inviteId] | Confirm a student's enrolment | professional | yes | no | yes | yes | yes | — |
| /professional/regulatory/complaints | Complaints involving me | professional | yes | no | yes | no | yes | — |
| /professional/practice-regulation | Practice & Facility Regulation | professional | yes | no | yes | yes | yes | — |
| /work/regulators/[regulatorId]/committee | Committee & hearings | operations | yes | no | yes | no | yes | — |
| /work/regulators/[regulatorId]/bulk-import | Bulk import | operations | yes | no | yes | no | yes | — |
| /work/regulatory/[orgId]/dashboard | Regulatory dashboards | operations | yes | no | yes | no | yes | — |
| /work/regulatory/[orgId]/configuration | Regulatory configuration | operations | yes | no | yes | no | yes | — |
| /work/regulatory/[orgId]/registers | Professional registers | operations | yes | no | yes | yes | yes | — |
| /work/regulatory/[orgId]/student-applications | Student applications | operations | yes | no | yes | yes | yes | — |
| /work/regulatory/[orgId]/student-applications/[applicationId] | Student registration review | operations | yes | no | yes | yes | yes | — |
| /work/regulatory/[orgId]/student-reports | Student registration reports | operations | yes | no | yes | yes | yes | — |
| /work/regulatory/[orgId]/cpd-review | CPD review | operations | yes | no | yes | yes | yes | — |
| /work/regulatory/[orgId]/restrictions | Register restrictions | operations | yes | no | yes | no | yes | — |
| /work/regulatory/[orgId]/audit | Regulatory audit | operations | yes | no | yes | no | yes | — |
| /work/regulatory/hpa/oversight | HPA oversight | operations | yes | no | yes | no | yes | — |
| /share/claim | Claim Shared Documents | home | yes | no | yes | yes | no | — |
| /collaboration/access | Provider collaboration access | home | yes | no | yes | yes | yes | — |
| /facility | Select Facility | facility | yes | no | yes | no | yes | — |
| /facility/claim | Claim facility administration | facility | yes | no | yes | yes | yes | — |
| /facility/register | Register a facility | facility | yes | no | yes | yes | yes | — |
| /facility/[id] | Facility Details | facility | yes | no | yes | yes | yes | — |
| /facility/[id]/configuration | Facility Configuration | facility | yes | no | yes | yes | yes | — |
| /facility/[id]/trust | Facility trust & governance | facility | yes | no | yes | yes | yes | — |
| /facility/[id]/cockpit | Facility cockpit | facility | yes | no | yes | yes | yes | — |
| /facility/[id]/control-tower | Facility control tower | facility | yes | no | yes | yes | yes | — |
| /facility/[id]/complete-profile | Complete facility profile | facility | yes | no | yes | yes | yes | — |
| /facility/[id]/departments | Departments & service points | facility | yes | no | yes | yes | yes | — |
| /facility/[id]/regulators | Facility regulators | facility | yes | no | yes | yes | yes | F |
| /facility/[id]/setup | Facility setup | facility | yes | no | yes | yes | yes | — |
| /site/register | Register a site | facility | yes | no | yes | yes | yes | — |
| /site/operator-access | Request site operator access | facility | yes | no | yes | yes | yes | — |
| /workspace | Select Workspace | workspace | yes | no | yes | yes | yes | — |
| /workspace/aggregate | Aggregate oversight | reports | yes | no | yes | no | yes | — |
| /workspace/[id] | Workspace Details | workspace | yes | no | yes | no | yes | — |
| /shift | Start Shift | shift | yes | no | yes | yes | yes | — |
| /shift/active | Active Shift | shift | yes | no | yes | yes | yes | — |
| /shift/handover | Shift Handover | shift | yes | no | yes | yes | yes | — |
| /scheduling | Scheduling | queue | yes | no | yes | yes | yes | — |
| /scheduling/roster | Staff Roster | queue | yes | no | yes | yes | yes | — |
| /scheduling/on-call | On-Call Schedule | queue | yes | no | yes | yes | yes | — |
| /scheduling/noticeboard | Provider Noticeboard | queue | yes | no | yes | yes | yes | — |
| /scheduling/booking-requests | Booking Requests | queue | yes | no | yes | yes | yes | — |
| /scheduling/today | Today's Appointments | queue | yes | no | yes | yes | yes | — |
| /scheduling/bookings/config | Booking Configuration | queue | yes | no | yes | yes | yes | — |
| /scheduling/surgical-waitlist | Surgical Waitlist | queue | yes | no | yes | yes | yes | — |
| /scheduling/theatre-lists | Theatre Lists | queue | yes | no | yes | yes | yes | — |
| /scheduling/theatre-lists/[sessionId] | Theatre List Session | queue | yes | no | yes | yes | yes | — |
| /communication | Communication Ops — Hub | queue | yes | no | yes | yes | yes | — |
| /communication/secure-messaging | Communication Ops — Secure Messaging | queue | yes | no | yes | yes | yes | — |
| /communication/approvals | Communication Ops — Approval Queue | queue | yes | no | yes | yes | yes | — |
| /communication/announcements | Communication Ops — Facility Announcements | queue | yes | no | yes | yes | yes | — |
| /khuluma | Khuluma | home | yes | no | yes | yes | yes | — |
| /khuluma/inbox | Khuluma — Inbox | home | yes | no | yes | yes | yes | — |
| /khuluma/calls | Khuluma — Calls | home | yes | no | yes | yes | yes | — |
| /khuluma/meetings | Khuluma — Meetings | home | yes | no | yes | yes | yes | — |
| /khuluma/updates | Khuluma — Updates & Actions | home | yes | no | yes | yes | yes | — |
| /khuluma/channels | Khuluma — Teams & Channels | home | yes | no | yes | yes | yes | — |
| /khuluma/feedback | Khuluma — Feedback & Support | home | yes | no | yes | yes | yes | — |
| /work/comms | Khuluma — Comms Hub | queue | yes | no | yes | yes | yes | — |
| /my/comms | Khuluma — Messages | home | yes | no | yes | yes | yes | — |
| /my/orders/[requestId]/offers | Compare Offers | home | yes | no | yes | yes | yes | — |
| /my/monitoring | My Monitoring | home | yes | no | yes | yes | yes | — |
| /my/pregnancy | My Pregnancy | home | yes | no | yes | yes | yes | — |
| /queue | Patient Queue | queue | yes | no | yes | yes | yes | — |
| /queue/triage | Triage Queue | queue | yes | no | yes | yes | yes | — |
| /queue/waiting | Waiting Room | queue | yes | no | yes | yes | yes | — |
| /queue/search | Patient Search | queue | yes | no | yes | yes | yes | — |
| /queue/walk-in | Walk-in Registration | queue | yes | no | yes | yes | yes | — |
| /queue/scheduled | Scheduled Visits | queue | yes | no | yes | yes | yes | — |
| /queue/incoming-referrals | Incoming Referrals | queue | yes | no | yes | yes | yes | — |
| /ehr/[patientId] | Patient Chart | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/summary | Patient Summary | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/ips | International Patient Summary | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/vitals | Vitals | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/maternity | Maternity Monitoring | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/history | Medical History | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/conditions | Conditions | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/medications | Medications | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/allergies | Allergies | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/orders | Orders | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/results | Results | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/notes | Clinical Notes | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/documents | Documents | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/encounters | Encounters | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/emergency | Emergency | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/encounter/[encounterId] | Encounter | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/immunizations | Immunizations | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/consults | Consults & Referrals | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/referrals | Referrals | ehr | no | no | yes | no | no | — |
| /ehr/[patientId]/teleconsults | Teleconsults | ehr | no | no | yes | no | no | — |
| /ehr/[patientId]/timeline | Timeline | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/discharge | Discharge | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/care-plans | Care Plans | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/procedures | Procedures | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/procedures/[episodeId] | Procedure Episode | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/paediatrics | Paediatric Workspace | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/growth-chart | Growth Chart | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/imam | Nutrition Treatment | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/family-history | Family History | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/social-history | Social History | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/functional-status | Functional Status | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/advance-directives | Advance Directives | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/care-team | Care Team | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/preferences/communications | Communication Preferences | ehr | yes | no | yes | no | yes | — |
| /ehr/[patientId]/goals | Goals | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/assessments | Assessments | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/imnci | IMNCI Assess & Classify | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/charts | Ward Charts | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/imaging | Imaging | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/investigations | Investigations | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/imaging/viewer | DICOM Viewer | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/programmes | Care programmes | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/consultations | Consultations and MDT | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/examination | Examination | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/medicine | Medicine workspace | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/medicine/cds | Decision support | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/medicine/specialty/[specialty] | Specialty workspace | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/multimorbidity | Multimorbidity | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/ward-round | Ward round | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/workspace/[specialty] | Specialty Workspace | ehr | yes | no | yes | yes | yes | — |
| /admin | Administration | admin | yes | no | yes | no | yes | — |
| /admin/users | Worker & Provider Access | admin | yes | no | yes | yes | yes | — |
| /admin/comms-ops | Comms Operations | admin | yes | no | yes | no | yes | — |
| /admin/users/[id] | User Details | admin | yes | no | yes | yes | yes | — |
| /admin/roles | Role Management | admin | yes | no | yes | yes | yes | — |
| /admin/policies | Policy Management | admin | yes | no | yes | yes | yes | — |
| /admin/audit | Audit Trail | admin | yes | no | yes | no | yes | — |
| /admin/audit/[id] | Audit Entry | admin | yes | no | yes | no | yes | — |
| /admin/workforce-intake | Workforce Intake | admin | yes | no | yes | yes | yes | — |
| /admin/facility-imports | Facility Import Batches | admin | yes | no | yes | yes | yes | — |
| /admin/hpa-enrichment | HPA facility enrichment | admin | yes | no | yes | yes | yes | — |
| /admin/facility-imports/[runId] | Facility Import Batch | admin | yes | no | yes | yes | yes | — |
| /admin/facility-imports/[runId]/review | Facility Import Review | admin | yes | no | yes | yes | yes | — |
| /admin/consent | Consent Management | admin | yes | no | yes | no | yes | — |
| /admin/devices | Device Management | admin | yes | no | yes | no | yes | — |
| /admin/keys | Key Management | admin | yes | no | yes | no | yes | — |
| /admin/federation | Federation | admin | yes | no | yes | no | yes | — |
| /admin/tenants | Tenant Management | admin | yes | no | yes | no | yes | — |
| /admin/break-glass | Break Glass Log | admin | yes | no | yes | yes | yes | — |
| /admin/beds | Bed & Ward Admin | admin | yes | no | yes | yes | yes | — |
| /admin/queues | Queue Configuration | admin | yes | no | yes | yes | yes | — |
| /admin/data-export | Data Export | admin | yes | no | yes | yes | yes | — |
| /admin/data-governance | Data Governance | admin | yes | no | yes | yes | yes | — |
| /admin/clinical-curation | Clinical Knowledge Curation | admin | yes | no | yes | yes | yes | — |
| /admin/system-monitor | System Monitor | admin | yes | no | yes | yes | yes | — |
| /admin/integration-status | Integration Status | admin | yes | no | yes | yes | yes | — |
| /admin/notifications/templates | Notification Templates | admin | yes | no | yes | yes | yes | — |
| /admin/integration-templates | Integration Templates | admin | yes | no | yes | yes | yes | — |
| /admin/sidecar-retirement | Sidecar Retirement | admin | yes | no | yes | no | yes | — |
| /dags | Data Access Governance | admin | yes | no | yes | yes | yes | — |
| /dags/policy | Data Access Policy | admin | yes | no | yes | yes | yes | — |
| /registry-admin | Registry Administration | admin | yes | no | yes | yes | yes | — |
| /registry-admin/trust-console | Trust Console | admin | yes | no | yes | yes | yes | — |
| /registry-admin/activation-letter | Activation Letter | admin | no | no | yes | no | no | — |
| /registry-admin/fee-schedules | Regulatory fee schedule | admin | yes | no | yes | yes | yes | — |
| /organization-admin | Organization Administration | admin | yes | no | yes | no | yes | — |
| /organization-admin/facility | Facility Administration | admin | yes | no | yes | no | yes | — |
| /organization-admin/staffing | Staffing & Scheduling | admin | yes | no | yes | no | yes | — |
| /organization-admin/governance | Organisations & governance | admin | yes | no | yes | no | yes | — |
| /organization-admin/governance/[id] | Organisation detail | admin | yes | no | yes | no | yes | — |
| /registry/clients | Client Registry | registry | yes | no | yes | yes | yes | — |
| /registry/clients/new | New Client Registration | registry | yes | no | yes | yes | yes | — |
| /registry/clients/[id] | Client Identity Workspace | registry | yes | no | yes | yes | yes | — |
| /registry/trust | Trust & Federation | registry | yes | no | yes | no | yes | — |
| /registry/mvumo | Mvumo â€” Digital Consent | registry | yes | no | yes | yes | yes | — |
| /registry | Registry Hub | registry | yes | no | yes | yes | yes | — |
| /registry/intake | Registry Intake | registry | yes | no | yes | yes | yes | — |
| /registry/locality-review | Locality gazetteer review | registry | yes | no | yes | yes | yes | — |
| /registry/facility-verification | Facility verification | registry | yes | no | yes | yes | yes | — |
| /registry/facility-lifecycle | Facility regulatory lifecycle | registry | yes | no | yes | yes | yes | — |
| /registry/facility-lifecycle/[facilityId] | Facility regulatory file | registry | yes | no | yes | yes | yes | — |
| /registry/providers | Provider Registry | registry | yes | no | yes | yes | yes | — |
| /registry/providers/verification | Provider Verification Queue | registry | yes | no | yes | yes | yes | — |
| /registry/providers/new | Create provider | registry | yes | no | yes | yes | yes | — |
| /registry/providers/[id] | Provider Profile | registry | yes | no | yes | yes | yes | — |
| /registry/providers/[id]/edit | Edit provider | registry | yes | no | yes | yes | yes | — |
| /registry/provider-council/self-service | Council self-service | registry | yes | no | yes | yes | yes | — |
| /registry/provider-council/council-workspace | Council operations | registry | yes | no | yes | yes | yes | — |
| /registry/facility-classification | Facility classification reconciliation | registry | yes | no | yes | yes | yes | — |
| /registry/facilities | Facility Registry | registry | yes | no | yes | yes | yes | — |
| /registry/facilities/worklist | Registry worklist | registry | yes | no | yes | yes | yes | — |
| /registry/facilities/new | Register a facility | registry | yes | no | yes | yes | yes | — |
| /registry/facilities/[id] | Facility Profile | registry | yes | no | yes | yes | yes | — |
| /registry/facilities/[id]/edit | Edit facility | registry | yes | no | yes | yes | yes | — |
| /registry/place-governance | Place governance | registry | yes | no | yes | yes | yes | — |
| /registry/terminology | Terminology Browser | registry | yes | no | yes | no | yes | — |
| /registry/terminology/[id] | Concept Details | registry | yes | no | yes | no | yes | — |
| /registry/products | Product Registry | registry | yes | no | yes | no | yes | — |
| /registry/products/[id] | Product Details | registry | yes | no | yes | no | yes | — |
| /ubomi | UBOMI Civil Registry | registry | yes | no | yes | yes | yes | — |
| /marketplace | Health Marketplace | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/catalog | Service Catalog | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/orders | My Orders | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/orders/[id] | Order Details | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/orders/[id]/pay | Pay for Order | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/ops | Marketplace Operations | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/vendor | Vendor Fulfilment | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/vendor/orders | Vendor Orders | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/pickup | Pickup Handoff | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/vendors | Vendors | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/bookings | Bookings | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/store | Marketplace Store | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/store/search | Search Listings | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/store/listing/[id] | Listing | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/store/activity | My Marketplace Activity | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/establishment-guide | Set Up Your Practice | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/seller | Seller Centre | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/seller/listings | My Listings | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/seller/listings/new | New Listing | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/seller/moderation | Listing Moderation | marketplace | yes | no | yes | yes | yes | — |
| /finance | Finance Dashboard | finance | yes | no | yes | yes | yes | — |
| /finance/claims | Claims | finance | yes | no | yes | no | yes | — |
| /finance/claims/[id] | Claim Details | finance | yes | no | yes | no | yes | — |
| /finance/billing | Billing | finance | yes | no | yes | yes | yes | — |
| /finance/billing/[id] | Bill Details | finance | yes | no | yes | yes | yes | — |
| /finance/payments | Payments | finance | yes | no | yes | no | yes | — |
| /finance/msika-governance | MSIKA Governance | finance | yes | no | yes | yes | yes | — |
| /finance/ledger | Ledger | finance | yes | no | yes | no | yes | — |
| /finance/workspace | Finance Workspace | finance | yes | no | yes | no | yes | — |
| /finance/settlements | Settlements | finance | yes | no | yes | yes | yes | — |
| /finance/remittances | Remittances | finance | yes | no | yes | yes | yes | — |
| /finance/reconciliation | Reconciliation | finance | yes | no | yes | yes | yes | — |
| /finance/bank-reconciliation | Bank Reconciliation | finance | yes | no | yes | yes | yes | — |
| /finance/failed-money-events | Failed Money Events | finance | yes | no | yes | yes | yes | — |
| /finance/insurance-plans | Insurance Plan Terms | finance | yes | no | yes | yes | yes | — |
| /finance/refunds | Refunds | finance | yes | no | yes | yes | yes | — |
| /finance/payer-ops | Payer Operations | finance | yes | no | yes | yes | yes | — |
| /finance/payer-claims | Payer Claims Queue | finance | yes | no | yes | yes | yes | — |
| /finance/payer-claims/[claimId] | Payer Claim | finance | yes | no | yes | yes | yes | — |
| /finance/tariffs | Tariff Management | finance | yes | no | yes | no | yes | — |
| /finance/costa | COSTA hub | finance | yes | no | yes | yes | yes | — |
| /finance/costa/encounter/[encounterId] | COSTA encounter timeline | finance | yes | no | yes | yes | yes | — |
| /finance/service-access | Service access decisions | finance | yes | no | yes | yes | yes | — |
| /finance/mushex-platform | MusheX platform admin | finance | yes | no | yes | yes | yes | — |
| /finance/mushex-platform/wallets/[walletId] | Custodial wallet | finance | yes | no | yes | yes | yes | — |
| /finance/mushex-platform/remittance/[transferId] | Remittance transfer | finance | yes | no | yes | yes | yes | — |
| /finance/mushex-platform/cards/[cardId] | Card profile | finance | yes | no | yes | yes | yes | — |
| /finance/mushex-platform/reversals/[reversalId] | Reversal record | finance | yes | no | yes | yes | yes | — |
| /finance/commerce-integrations | Commerce & Payer Stack | finance | yes | no | yes | no | yes | — |
| /finance/reports | Financial reports | finance | yes | no | yes | yes | yes | — |
| /finance/my-account | My Healthcare Account | finance | yes | no | yes | yes | yes | — |
| /wallet | MusheX Wallet | finance | yes | no | yes | yes | yes | — |
| /wallet/deposit | Deposit | finance | yes | no | yes | yes | yes | — |
| /wallet/send | Send Money | finance | yes | no | yes | yes | yes | — |
| /wallet/transactions | Transactions | finance | yes | no | yes | yes | yes | — |
| /wallet/cards | Cards | finance | yes | no | yes | yes | yes | — |
| /wallet/merchant | Merchant | finance | yes | no | yes | yes | yes | — |
| /beds | Bed Management | queue | yes | no | yes | yes | yes | — |
| /pharmacy | Pharmacy Dashboard | pharmacy | yes | no | yes | no | yes | — |
| /pharmacy/dispense | Dispensing | pharmacy | yes | no | yes | no | yes | — |
| /pharmacy/collect | Collect a prescription | pharmacy | yes | no | yes | yes | yes | — |
| /pharmacy/stock | Stock Management | pharmacy | yes | no | yes | no | yes | — |
| /pharmacy/prescriptions | Prescriptions | pharmacy | yes | no | yes | yes | yes | — |
| /pharmacy/transaction-journey | Rx Transaction Journey | pharmacy | yes | no | yes | yes | yes | — |
| /inventory | Inventory Dashboard | inventory | yes | no | yes | yes | yes | — |
| /inventory/movements | Stock Movements | inventory | yes | no | yes | yes | yes | — |
| /inventory/counts | Stock Counts | inventory | yes | no | yes | yes | yes | — |
| /inventory/requisitions | Requisitions | inventory | yes | no | yes | yes | yes | — |
| /inventory/stock-management | Stock Management | inventory | yes | no | yes | yes | yes | — |
| /enterprise | Enterprise Resources | enterprise | yes | no | yes | yes | yes | — |
| /enterprise/warehousing | Warehousing & distribution | enterprise | yes | no | yes | yes | yes | — |
| /enterprise/fleet | Fleet & logistics | enterprise | yes | no | yes | yes | yes | — |
| /enterprise/charge-sheet | Charge sheet | enterprise | yes | no | yes | yes | yes | — |
| /enterprise/oversight | National Enterprise Oversight | enterprise | yes | no | yes | yes | yes | — |
| /erp | Institutional ERP | enterprise | yes | no | yes | no | yes | — |
| /erp/gl | General ledger | enterprise | yes | no | yes | yes | yes | — |
| /erp/hr | HR & payroll | enterprise | yes | no | yes | yes | yes | — |
| /erp/procurement | Procurement | enterprise | yes | no | yes | yes | yes | — |
| /erp/assets | Fixed assets | enterprise | yes | no | yes | yes | yes | — |
| /reports | Reports | reports | yes | no | yes | yes | yes | — |
| /reports/facility | Facility Reports | reports | yes | no | yes | yes | yes | — |
| /reports/clinical | Clinical Reports | reports | yes | no | yes | yes | yes | — |
| /reports/operational | Operational Reports | reports | yes | no | yes | yes | yes | — |
| /reports/custom | Custom Reports | reports | yes | no | yes | yes | yes | — |
| /reports/theatre | Theatre Utilisation | reports | yes | no | yes | no | yes | — |
| /reports/[id] | Report Details | reports | yes | no | yes | yes | yes | — |
| /settings | Settings | settings | yes | no | yes | no | yes | — |
| /settings/account | Account Settings | settings | yes | no | yes | yes | yes | — |
| /settings/security | Security Settings | settings | yes | no | yes | yes | yes | — |
| /settings/notifications | Notification Preferences | settings | yes | no | yes | yes | yes | — |
| /settings/display | Display Settings | settings | yes | no | yes | yes | yes | — |
| /settings/integrations | Integrations | settings | yes | no | yes | yes | yes | — |
| /settings/privacy | Privacy & Data | settings | yes | no | yes | yes | yes | — |
| /telemedicine | Telemedicine Hub | queue | yes | no | yes | yes | yes | — |
| /telemedicine/new | New Teleconsultation | queue | yes | no | yes | yes | yes | F |
| /telemedicine/session/[sessionId] | Teleconsult Session | queue | yes | no | yes | yes | yes | — |
| /telemedicine/analytics | Telemedicine Analytics | queue | yes | no | yes | yes | yes | — |
| /work/telemedicine/worklist | Specialist Worklist | queue | yes | no | yes | yes | yes | — |
| /work/telemedicine/groups | Clinical Groups | queue | yes | no | yes | yes | yes | — |
| /work/telemedicine/virtual-hospitals | Virtual Hospitals | queue | yes | no | yes | yes | yes | — |
| /work/telemedicine/virtual-hospitals/[id] | Virtual Hospital | queue | yes | no | yes | yes | yes | — |
| /provider/activate | Activate Provider Role | auth | yes | no | yes | yes | yes | — |
| /provider/status | Provider Status | auth | yes | no | yes | no | yes | — |
| /provider/workplace | Start a Work Session | auth | yes | no | yes | yes | yes | — |
| /provider/get-access | Get Provider Access | auth | yes | no | yes | no | yes | — |
| /citizen/provider-claim | Claim Provider Profile | home | yes | no | yes | yes | yes | — |
| /citizen/provider-claim/status | Provider Access Requests | home | yes | no | yes | yes | yes | — |
| /facility/[id]/mode | Facility Mode | facility | yes | no | yes | yes | yes | — |
| /wellness | Wellness Hub | wellness | yes | no | yes | yes | yes | — |
| /wellness/dashboard | Wellness Dashboard | wellness | yes | no | yes | yes | yes | — |
| /wellness/goals | Health Goals | wellness | yes | no | yes | yes | yes | — |
| /wellness/programs | Prevention Programs | wellness | yes | no | yes | yes | yes | — |
| /wellness/screenings | Screening Schedule | wellness | yes | no | yes | yes | yes | — |
| /wellness/activity | Activity & Fitness | wellness | yes | no | yes | yes | yes | — |
| /wellness/connect | Health Connect ingest | wellness | yes | no | yes | no | yes | — |
| /wellness/diet | Diet & Nutrition | wellness | yes | no | yes | yes | yes | — |
| /wellness/sleep | Sleep & Recovery | wellness | yes | no | yes | yes | yes | — |
| /wellness/clubs | Clubs & Communities | wellness | no | no | yes | no | no | — |
| /wellness/challenges | Challenges | wellness | no | no | yes | no | no | — |
| /wellness/routes | Routes & Places | wellness | yes | no | yes | yes | yes | — |
| /wellness/coaching | Coaching & Habits | wellness | yes | no | yes | yes | yes | — |
| /wellness/plans | Plans & Journeys | wellness | yes | no | yes | yes | yes | — |
| /wellness/care | Connect to Care | wellness | yes | no | yes | yes | yes | — |
| /wellness/commodities | Programme commodities (Dura) | wellness | yes | no | yes | yes | yes | — |
| /wellness/community | Wellness Community | wellness | yes | no | yes | yes | yes | — |
| /wellness/assessment | Wellness Assessment | wellness | yes | no | yes | yes | yes | — |
| /wellness/timeline | Wellness Timeline | wellness | yes | no | yes | yes | yes | — |
| /wellness/reminders | Preventive Reminders | wellness | yes | no | yes | yes | yes | — |
| /wellness/follow-ups | My Follow-ups | wellness | yes | no | yes | yes | yes | — |
| /wellness/insights | Wellness Insights | wellness | yes | no | yes | yes | yes | — |
| /wellness/settings/consent | Wellness Consent | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/feed | Wellness Community Feed | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/reels | Wellness Reels | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/posts/[id] | Post | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/saved | Saved | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/my-activity | My Activity | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/groups | Wellness Groups | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/groups/new | New Group | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/groups/[id] | Group | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/communities | Wellness Communities | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/communities/[id] | Community | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/challenges | Wellness Challenges | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/challenges/[id] | Challenge | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/notifications | Social Notifications | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/moderation | Social Moderation | wellness | yes | no | yes | yes | yes | — |
| /wellness/social/programme-dashboard | Programme Engagement | wellness | yes | no | yes | yes | yes | — |
| /social | Social Timeline | wellness | yes | no | yes | yes | yes | — |
| /social/drafts | Draft Posts | wellness | yes | no | yes | yes | yes | — |
| /social/saved | Saved Posts | wellness | yes | no | yes | yes | yes | — |
| /social/moderation | Social Moderation | wellness | yes | no | yes | yes | yes | — |
| /communities | Communities | wellness | yes | no | yes | yes | yes | — |
| /communities/[id] | Community | wellness | yes | no | yes | yes | yes | — |
| /pages | Pages | wellness | yes | no | yes | yes | yes | — |
| /pages/[id] | Page | wellness | yes | no | yes | yes | yes | — |
| /caregiving | Caregiving Hub | caregiving | yes | no | yes | yes | yes | — |
| /caregiving/dependants | My Dependants | caregiving | yes | no | yes | no | yes | — |
| /caregiving/delegation | Care Delegation | caregiving | yes | no | yes | no | yes | — |
| /caregiving/tasks | Care Tasks | caregiving | yes | no | yes | no | yes | — |
| /caregiving/notifications | Care Alerts | caregiving | yes | no | yes | no | yes | — |
| /monitoring | Remote Monitoring | monitoring | yes | no | yes | no | yes | — |

## Mobile screens

| App | Screen | BFF paths | Mock/stub |
|-----|--------|-----------|-----------|
| citizen-app | apps/mobile/citizen-app/src/screens/FacilityDetailScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/FacilityDirectoryScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/GlobalErrorBanner.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/HomeScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/LoginScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/NetworkStatusBar.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/NhumeTrackingScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/NompiloAssistantScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/NotificationsScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/auth/AssuranceChoiceScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/auth/SignUpScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/caregiving/DelegationSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/comms/CommsHubScreen.tsx | 0 | yes |
| citizen-app | apps/mobile/citizen-app/src/screens/comms/MeetingScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/crvs/UbomiCrvsScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/discover/ProviderDiscoveryScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/emergency/SosScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/emergency/TrackEmergencyScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/gateway/ContactSignUpScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/gateway/GatewayVerifyScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/gateway/HealthInfoScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/gateway/TrackByReferenceScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/learning/CoursePlayerScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/learning/LearningClassroomScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/live/EventDiscussionSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/live/LiveDiscoverScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/live/LiveEventScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/madi/BecomeDonorScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/madi/DonationDrivesScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/madi/DonorFeedbackScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/madi/DonorHistoryScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/madi/DonorProfileScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/madi/DonorScreeningScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/madi/MadiDonorHubScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/marketplace/FundoLearningScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/marketplace/HealthOsAppsScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/messaging/MessagingInboxScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/messaging/ThreadViewScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/AllergiesSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/AppointmentsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/AssessmentsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/BookingsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/CarePlansSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/CareTeamSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ChallengesScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ClaimSharedDocumentsScreen.tsx | 1 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/CommunicationPreferencesScreen.tsx | 0 | yes |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ConditionsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/CoverageSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/DelegatedPickupScreen.tsx | 1 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/EmergencySOSSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/FinanceSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/GrowthChartsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/HealthIdSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/HealthTimelineScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/IdRecoverySection.tsx | 0 | yes |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ImmunizationsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/MarketplaceStoreSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/MonitoringSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/NompiloGuidanceSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/PatientConsentScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/PersonalScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/PregnancySection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/PrescriptionsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/PrivacyPolicyScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ProductionReadinessJourneyScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ProfileSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ProgramsScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/PublicRegulatoryExploreScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/QueueStatusSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/RecordSharingScreen.tsx | 1 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/RecordsScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ReferralsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/RemindersScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ResultsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/SettingsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/SmartCardSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/TermsOfUseScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/VerifyCredentialScreen.tsx | 1 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/WalletOverviewSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/WalletSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/WellnessJourneysSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/publicHealth/PublicHealthScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/rito/FeedbackScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/rito/RespectfulMaternityCareScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/rito/TrackFeedbackScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/ClubsScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/CommunitiesScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/CrowdfundingScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/PagesScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/ProfessionalPagesScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/SocialFeedScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/SocialHubScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/TimelineComposer.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/WellnessSocialFeedScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/support/SupportScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/telehealth/TelehealthListScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/telehealth/TelehealthSessionScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/GlobalErrorBanner.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/LoginScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/NetworkStatusBar.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/NompiloAssistantScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/NotificationsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/ProviderActivationScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/SelectFacilityScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/SelectWorkspaceScreen.tsx | 1 | no |
| provider-app | apps/mobile/provider-app/src/screens/budgets/BudgetSummaryScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/courier/CourierDashboardScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/courier/CourierProofScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/equipment/EquipmentSearchScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/equipment/EquipmentToolsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/equipment/MaintenanceTasksScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/equipment/ReportEquipmentFaultScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/learning/LearningClassroomScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/live/ProviderLiveHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/madi/MadiCentralBankScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/madi/MadiDriveCaptureScreen.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/madi/MadiOrdersScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/madi/MadiReactionReportScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/madi/MadiTransfusionScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/offline/BreakGlassScreen.tsx | 2 | no |
| provider-app | apps/mobile/provider-app/src/screens/offline/ConflictReviewScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/offline/LocalQueueScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/offline/OfflineDashboardScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/outreach/FollowUpScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/outreach/HouseholdListScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/outreach/OutreachDashboardScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/outreach/PlaceModeDashboardScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/outreach/PostnatalContactScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/outreach/ScreeningScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/APGARScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ActivityFeedScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/AdaptiveEncounterCockpit.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/AdminRegistryHubScreen.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/provider/AssistedCommunicationPreferencesScreen.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/provider/BedManagementScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/BillingScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/BookingRequestsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/CarePlanDetailScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ChronicRegistersScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ClerkingContinuityScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ClinicalJourneyWorkspaces.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ClinicalToolsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ConfirmDeathScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ControlTowerScreen.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/provider/CoreTransactionJourneyShellScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/CriticalEventScreen.tsx | 1 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/DaidzaiFieldMissionScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/DeveloperHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/DiagnosisPanel.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/DiagnosticsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/DischargeClearanceScreen.tsx | 1 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/DischargeScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/EdVisitScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/EmergencyEpisodeBoardScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/EmergencyEpisodeDetailScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/EmergencyHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/EncounterFormsPanel.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/EncounterScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/FacilityAdminScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/FacilityRegulatorsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/FacilitySetupScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/FinanceOverviewScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/FundoLearningShellScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/HealthOsAppsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/InpatientScreen.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/provider/LabHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/LabOrderPanel.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/MarketplaceOpsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/MaternityWorkspaces.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/MedicineCdsEvaluateScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/MedicineWorkspaceScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/MentalHealthQueueScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/MentalHealthReferralScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/MessagingScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/MyRegulatoryAffairsScreen.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/provider/NEWS2ScoringScreen.tsx | 1 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/NotesPanel.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PACSViewerScreen.tsx | 1 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PaediatricWorkspaces.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/provider/PatientLookupScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PatientRegistrationScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PharmacyDispensingScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PharmacyHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PrehospitalEpcrScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PrescriptionPanel.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProceduresCatalogueScreen.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProductionReadinessJourneyScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProfessionalChannelsHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProfessionalProfileScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProfessionalSettingsHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProviderDashboardScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProviderSocialScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PublicHealthFieldTasksScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/QueueDefinitionsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/QueueManagementScreen.tsx | 0 | no |
