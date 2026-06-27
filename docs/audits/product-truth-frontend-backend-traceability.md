# Product Truth — Frontend-to-Backend Traceability

> Generated: 2026-06-27T16:24:42.300Z
> Web surfaces: **672** | Mobile screens: **181**

## Web routes (one-ui-shell)

| Route | Title | Zone | BFF backing | Gateway | Reads real | Writes real | Mock/stub | Gaps |
|-------|-------|------|-------------|---------|------------|-------------|-----------|------|
| /bootstrap | Platform Bootstrap | auth | yes | no | yes | no | yes | — |
| /auth/login | Sign In | auth | yes | no | yes | yes | yes | — |
| /auth/login/email | Sign In with Email | auth | no | no | yes | no | no | — |
| /auth/login/provider-id | Sign In with Provider ID | auth | yes | no | yes | yes | yes | — |
| /auth/login/biometric | Biometric Verification | auth | yes | no | yes | yes | yes | — |
| /auth/forgot-password | Forgot Password | auth | yes | no | yes | no | yes | — |
| /auth/reset-password | Reset Password | auth | yes | no | yes | no | yes | — |
| /auth/mfa | Multi-Factor Authentication | auth | yes | no | yes | yes | yes | — |
| /auth/logout | Signing Out | auth | yes | no | yes | yes | yes | — |
| /auth | Authentication | auth | no | no | yes | no | no | — |
| /auth/register | Create Account | auth | yes | no | yes | yes | yes | — |
| /auth/register/assurance | Identity Assurance | auth | yes | no | yes | yes | yes | — |
| /auth/register/status | Registration Status | auth | no | no | yes | no | yes | — |
| /auth/resolving | Resolving Session | auth | yes | no | yes | no | yes | — |
| /auth/context-chooser | Choose Work Context | auth | yes | no | yes | no | yes | — |
| /welcome | Welcome to Impilo | auth | no | no | yes | no | yes | — |
| /welcome/find-care | Find Care | auth | no | no | yes | no | yes | — |
| /welcome/emergency | Emergency & Public Health | auth | no | no | yes | no | yes | — |
| /welcome/accessibility | Accessibility & Language | auth | no | no | yes | no | yes | — |
| /privacy | Privacy Policy | auth | no | no | yes | no | no | — |
| /terms | Terms of Use | auth | no | no | yes | no | no | — |
| /consent | Review Policies | auth | yes | no | yes | yes | yes | — |
| /account-deletion | Account Deletion | auth | no | no | yes | no | no | — |
| /privacy/app-stores | App Store Privacy | auth | no | no | yes | no | no | — |
| /clinical | Clinical Care | queue | yes | no | yes | no | yes | — |
| /core-transaction | Core Transaction | queue | yes | no | yes | yes | yes | — |
| /core-transaction/[transactionId] | Core Transaction Detail | queue | yes | no | yes | yes | yes | — |
| /client-journey | Client Journey | home | yes | no | yes | yes | yes | — |
| /provider-workspace | Provider Workspace | queue | yes | no | yes | yes | yes | — |
| /platform-journey | Platform Journey | admin | yes | no | yes | yes | yes | — |
| /clinical-tools | Clinical Tools | queue | yes | no | yes | yes | yes | — |
| /clinical-tools/rules | Rules Engine | queue | yes | no | yes | yes | yes | — |
| /clinical-tools/forms | Form Builder | queue | yes | no | yes | yes | yes | — |
| /clinical/control-tower | Control Tower | queue | yes | no | yes | yes | yes | — |
| /clinical/dictation | Voice Dictation | queue | yes | no | yes | no | yes | — |
| /clinical/emergency | ED / Casualty | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient | Inpatient Care | queue | yes | no | yes | no | yes | — |
| /clinical/inpatient/admissions | Inpatient Admissions | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/admissions/[admissionId] | Inpatient Episode | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/ward-board | Ward Board | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/nursing | Nursing Workbench | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/rounds | Medical Rounds | queue | yes | no | yes | yes | yes | — |
| /clinical/inpatient/discharge/[admissionId] | Inpatient Discharge | queue | yes | no | yes | yes | yes | — |
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
| /coverage/member | My Coverage | home | yes | no | yes | yes | yes | — |
| /coverage/contracts | Provider Contracts | admin | yes | no | yes | yes | yes | — |
| /id-services | Identity Services | admin | yes | no | yes | yes | yes | — |
| /ai-governance | AI Governance | admin | yes | no | yes | yes | yes | — |
| /ai-governance/models/[id] | AI Model | admin | yes | no | yes | yes | yes | — |
| /access | Access Channels | admin | yes | no | yes | yes | yes | — |
| /kiosk | Self Check-In | auth | yes | no | yes | yes | yes | — |
| / | Home | home | no | no | yes | no | no | — |
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
| /citizen/health-id/qr | My Health ID QR | home | yes | no | yes | no | yes | — |
| /citizen/health-id/request | Request Health ID | home | yes | no | yes | yes | yes | — |
| /citizen/id-recovery | ID Recovery | home | yes | no | yes | no | yes | — |
| /citizen/delegated-pickup | Delegated Pickup | home | yes | no | yes | yes | yes | — |
| /citizen/record-sharing | Share My Record | home | yes | no | yes | yes | yes | — |
| /verify/credential | Verify Credential | home | no | no | yes | yes | no | — |
| /share/claim | Claim Shared Documents | home | yes | no | yes | yes | no | — |
| /collaboration/access | Provider collaboration access | home | yes | no | yes | yes | yes | — |
| /facility | Select Facility | facility | yes | no | yes | no | yes | — |
| /facility/[id] | Facility Details | facility | yes | no | yes | no | yes | — |
| /workspace | Select Workspace | workspace | yes | no | yes | yes | yes | — |
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
| /communication | Khuluma — Communication Hub | queue | yes | no | yes | yes | yes | — |
| /communication/secure-messaging | Khuluma — Secure Messaging | queue | yes | no | yes | yes | yes | — |
| /work/comms | Khuluma — Comms Hub | queue | yes | no | yes | yes | yes | — |
| /my/comms | Khuluma — Messages | home | yes | no | yes | yes | yes | — |
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
| /ehr/[patientId]/encounter/[encounterId] | Encounter | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/immunizations | Immunizations | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/consults | Consults & Referrals | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/referrals | Referrals | ehr | no | no | yes | no | no | — |
| /ehr/[patientId]/teleconsults | Teleconsults | ehr | no | no | yes | no | no | — |
| /ehr/[patientId]/timeline | Timeline | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/discharge | Discharge | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/care-plans | Care Plans | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/procedures | Procedures | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/growth-chart | Growth Chart | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/family-history | Family History | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/social-history | Social History | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/functional-status | Functional Status | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/advance-directives | Advance Directives | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/care-team | Care Team | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/preferences/communications | Communication Preferences | ehr | no | no | yes | no | yes | — |
| /ehr/[patientId]/goals | Goals | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/assessments | Assessments | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/charts | Ward Charts | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/imaging | Imaging | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/investigations | Investigations | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/imaging/viewer | DICOM Viewer | ehr | yes | no | yes | yes | yes | — |
| /admin | Administration | admin | yes | no | yes | no | yes | — |
| /admin/users | Worker & Provider Access | admin | yes | no | yes | yes | yes | — |
| /admin/users/[id] | User Details | admin | yes | no | yes | yes | yes | — |
| /admin/roles | Role Management | admin | yes | no | yes | yes | yes | — |
| /admin/policies | Policy Management | admin | yes | no | yes | yes | yes | — |
| /admin/audit | Audit Trail | admin | yes | no | yes | no | yes | — |
| /admin/audit/[id] | Audit Entry | admin | yes | no | yes | no | yes | — |
| /admin/consent | Consent Management | admin | yes | no | yes | no | yes | — |
| /admin/devices | Device Management | admin | yes | no | yes | no | yes | — |
| /admin/keys | Key Management | admin | yes | no | yes | no | yes | — |
| /admin/federation | Federation | admin | yes | no | yes | no | yes | — |
| /admin/tenants | Tenant Management | admin | yes | no | yes | no | yes | — |
| /admin/break-glass | Break Glass Log | admin | yes | no | yes | yes | yes | — |
| /admin/beds | Bed & Ward Admin | admin | yes | no | yes | yes | yes | — |
| /admin/queues | Queue Configuration | admin | yes | no | yes | no | yes | — |
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
| /registry/facility-lifecycle | Facility regulatory lifecycle | registry | yes | no | yes | no | yes | — |
| /registry/providers | Provider Registry | registry | yes | no | yes | yes | yes | — |
| /registry/providers/verification | Provider Verification Queue | registry | yes | no | yes | yes | yes | — |
| /registry/providers/[id] | Provider Profile | registry | yes | no | yes | yes | yes | — |
| /registry/provider-council/self-service | Council self-service | registry | yes | no | yes | yes | yes | — |
| /registry/provider-council/council-workspace | Council operations | registry | yes | no | yes | yes | yes | — |
| /registry/facilities | Facility Registry | registry | yes | no | yes | yes | yes | — |
| /registry/facilities/[id] | Facility Profile | registry | yes | no | yes | yes | yes | — |
| /registry/terminology | Terminology Browser | registry | yes | no | yes | no | yes | — |
| /registry/terminology/[id] | Concept Details | registry | yes | no | yes | no | yes | — |
| /registry/products | Product Registry | registry | yes | no | yes | no | yes | — |
| /registry/products/[id] | Product Details | registry | yes | no | yes | no | yes | — |
| /ubomi | UBOMI Civil Registry | registry | yes | no | yes | yes | yes | — |
| /marketplace | Health Marketplace | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/catalog | Service Catalog | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/orders | My Orders | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/orders/[id] | Order Details | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/ops | Marketplace Operations | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/vendor | Vendor Fulfilment | marketplace | yes | no | yes | no | yes | — |
| /marketplace/vendor/orders | Vendor Orders | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/pickup | Pickup Handoff | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/vendors | Vendors | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/bookings | Bookings | marketplace | yes | no | yes | yes | yes | — |
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
| /wallet | Wallet | finance | yes | no | yes | yes | yes | — |
| /wallet/deposit | Deposit | finance | yes | no | yes | yes | yes | — |
| /wallet/send | Send Money | finance | yes | no | yes | yes | yes | — |
| /wallet/transactions | Transactions | finance | yes | no | yes | yes | yes | — |
| /wallet/cards | Cards | finance | yes | no | yes | yes | yes | — |
| /wallet/merchant | Merchant | finance | yes | no | yes | yes | yes | — |
| /beds | Bed Management | queue | yes | no | yes | yes | yes | — |
| /pharmacy | Pharmacy Dashboard | pharmacy | yes | no | yes | no | yes | — |
| /pharmacy/dispense | Dispensing | pharmacy | yes | no | yes | no | yes | — |
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
| /workspace/aggregate | Aggregate oversight | reports | yes | no | yes | no | yes | — |
| /reports | Reports | reports | yes | no | yes | yes | yes | — |
| /reports/facility | Facility Reports | reports | yes | no | yes | yes | yes | — |
| /reports/clinical | Clinical Reports | reports | yes | no | yes | yes | yes | — |
| /reports/operational | Operational Reports | reports | yes | no | yes | yes | yes | — |
| /reports/custom | Custom Reports | reports | yes | no | yes | yes | yes | — |
| /reports/[id] | Report Details | reports | yes | no | yes | yes | yes | — |
| /settings | Settings | settings | yes | no | yes | no | yes | — |
| /settings/account | Account Settings | settings | yes | no | yes | yes | yes | — |
| /settings/security | Security Settings | settings | yes | no | yes | yes | yes | — |
| /settings/notifications | Notification Preferences | settings | yes | no | yes | yes | yes | — |
| /settings/display | Display Settings | settings | yes | no | yes | yes | yes | — |
| /settings/integrations | Integrations | settings | yes | no | yes | yes | yes | — |
| /settings/privacy | Privacy & Data | settings | yes | no | yes | yes | yes | — |
| /telemedicine | Telemedicine Hub | queue | yes | no | yes | yes | yes | — |
| /telemedicine/new | New Teleconsultation | queue | yes | no | yes | yes | yes | — |
| /telemedicine/session/[sessionId] | Teleconsult Session | queue | yes | no | yes | yes | yes | — |
| /telemedicine/analytics | Telemedicine Analytics | queue | yes | no | yes | yes | yes | — |
| /provider/activate | Activate Provider Role | auth | yes | no | yes | no | yes | — |
| /provider/status | Provider Status | auth | yes | no | yes | no | yes | — |
| /wellness | Wellness Hub | wellness | yes | no | yes | yes | yes | — |
| /wellness/dashboard | Wellness Dashboard | wellness | yes | no | yes | yes | yes | — |
| /wellness/goals | Health Goals | wellness | yes | no | yes | yes | yes | — |
| /wellness/programs | Prevention Programs | wellness | yes | no | yes | yes | yes | — |
| /wellness/screenings | Screening Schedule | wellness | yes | no | yes | yes | yes | — |
| /wellness/activity | Activity & Fitness | wellness | yes | no | yes | yes | yes | — |
| /wellness/connect | Health Connect ingest | wellness | yes | no | yes | no | yes | — |
| /wellness/diet | Diet & Nutrition | wellness | yes | no | yes | yes | yes | — |
| /wellness/sleep | Sleep & Recovery | wellness | yes | no | yes | yes | yes | — |
| /wellness/clubs | Clubs & Communities | wellness | yes | no | yes | yes | yes | — |
| /wellness/challenges | Challenges | wellness | yes | no | yes | yes | yes | — |
| /wellness/routes | Routes & Places | wellness | yes | no | yes | yes | yes | — |
| /wellness/coaching | Coaching & Habits | wellness | yes | no | yes | yes | yes | — |
| /wellness/commodities | Wellness Commodities | wellness | yes | no | yes | yes | yes | F |
| /wellness/community | Wellness Community | wellness | yes | no | yes | yes | yes | — |
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
| /monitoring/devices | My Devices | monitoring | yes | no | yes | yes | yes | — |
| /monitoring/readings | Readings & Trends | monitoring | yes | no | yes | yes | yes | — |
| /monitoring/alerts | Monitoring Alerts | monitoring | yes | no | yes | yes | yes | — |
| /monitoring/care-plans | Chronic Care Plans | monitoring | yes | no | yes | no | yes | — |
| /monitoring/provider-dashboard | Patient Monitoring Dashboard | monitoring | yes | no | yes | no | yes | — |
| /discover | Find Services | discovery | yes | no | yes | no | yes | — |
| /discover/providers | Find a Provider | discovery | yes | no | yes | no | yes | — |
| /discover/facilities | Find a Facility | discovery | yes | no | yes | yes | yes | — |
| /discover/services | Browse Services | discovery | yes | no | yes | no | yes | — |
| /lab | Laboratory | lab | yes | no | yes | yes | yes | — |
| /lab/worklist | Lab Worklist | lab | yes | no | yes | yes | yes | — |
| /imaging/worklist | Imaging Worklist | lab | yes | no | yes | yes | yes | — |
| /diagnostics/orders | Diagnostics Orders | lab | yes | no | yes | yes | yes | — |
| /diagnostics/orders/new | Create Diagnostic Order | lab | yes | no | yes | yes | yes | — |
| /diagnostics/orders/route | Route Order | lab | yes | no | yes | yes | yes | — |
| /diagnostics/results-inbox | Results Inbox | lab | yes | no | yes | yes | yes | — |
| /diagnostics/critical-queue | Critical Results | lab | yes | no | yes | yes | yes | — |
| /diagnostics/worklist | Imaging Worklist | lab | yes | no | yes | yes | yes | — |
| /diagnostics/lab-worklist | Lab Worklist | lab | yes | no | yes | yes | yes | — |
| /diagnostics/procedure-worklist | Procedure Worklist | lab | yes | no | yes | yes | yes | — |
| /diagnostics/reporting | Report Authoring | lab | yes | no | yes | yes | yes | — |
| /diagnostics/intake/qr | Claim Order QR | lab | yes | no | yes | yes | yes | — |
| /operations/diagnostics-reconciliation | Diagnostics Reconciliation | lab | yes | no | yes | yes | yes | — |
| /admin/integrations | Integration Status | admin | yes | no | yes | yes | yes | — |
| /admin/diagnostics-catalogue | Diagnostics Catalogue | admin | yes | no | yes | yes | yes | — |
| /imaging/facility | Facility Imaging Dashboard | lab | yes | no | yes | yes | yes | — |
| /lab/results | Results Review | lab | yes | no | yes | yes | yes | — |
| /lab/catalog | Test Catalog | lab | yes | no | yes | no | yes | — |
| /lab/reconciliation | Lab Reconciliation | lab | yes | no | yes | yes | yes | — |
| /operations | Operations | operations | yes | no | yes | no | yes | — |
| /operations/facility-operations | Facility Operations | operations | yes | no | yes | yes | yes | F |
| /operations/facility-operations/district-view | District View | operations | yes | no | yes | yes | yes | — |
| /operations/facility-operations/patient-flow | Patient Flow | operations | yes | no | yes | yes | yes | — |
| /operations/facility-operations/resources | Resource Operations | operations | yes | no | yes | no | yes | — |
| /operations/workflows | Workflow Orchestration | operations | yes | no | yes | yes | yes | — |
| /operations/workflows/[instanceId] | Workflow Instance | operations | yes | no | yes | yes | yes | — |
| /operations/dispatch | Dispatch Operations | operations | yes | no | yes | yes | yes | — |
| /operations/dispatch/[taskId] | Dispatch Task | operations | yes | no | yes | yes | yes | — |
| /operations/vito | Identity Operations | operations | yes | no | yes | yes | yes | — |
| /operations/vito/registration | Client Registration | operations | yes | no | yes | yes | yes | — |
| /operations/vito/registration/new | New Registration | operations | yes | no | yes | yes | yes | — |
| /operations/vito/issuance | Issuance Queue | operations | yes | no | yes | yes | yes | — |
| /operations/vito/issuance/[requestId] | Issuance Request | operations | yes | no | yes | yes | yes | — |
| /operations/vito/cards | Smart Cards | operations | yes | no | yes | yes | yes | — |
| /operations/vito/cards/pickup | Card Pickup | operations | yes | no | yes | yes | yes | — |
| /operations/vito/match | Match Review | operations | yes | no | yes | yes | yes | — |
| /operations/vito/dedup | Deduplication | operations | yes | no | yes | yes | yes | — |
| /operations/vito/print | Print & Slips | operations | yes | no | yes | yes | yes | — |
| /operations/vito/patient-shares | Patient Shares | operations | yes | no | yes | yes | yes | — |
| /operations/vito/internal-search | Internal Search | operations | yes | no | yes | yes | yes | — |
| /operations/vito/biometrics | Biometrics | operations | yes | no | yes | yes | yes | — |
| /operations/vito/recovery | Recovery & SHS | operations | yes | no | yes | yes | yes | — |
| /operations/vito/registry-admin | Registry Admin | operations | yes | no | yes | yes | yes | — |
| /operations/butano | SHR Operations | operations | yes | no | yes | no | yes | — |
| /operations/assets | Asset Management | operations | yes | no | yes | yes | yes | — |
| /operations/equipment | Equipment Management | operations | yes | no | yes | no | yes | — |
| /support | Support | support | yes | no | yes | no | yes | — |
| /support/tickets | Support Tickets | support | yes | no | yes | no | yes | — |
| /support/knowledge-base | Knowledge Base | support | yes | no | yes | no | yes | — |
| /developer | Developer Portal | developer | yes | no | yes | no | yes | — |
| /developer/api-catalog | API Catalog | developer | yes | no | yes | no | yes | — |
| /developer/clients | Client Registration | developer | yes | no | yes | no | yes | — |
| /developer/sandbox | Sandbox | developer | yes | yes | yes | no | yes | — |
| /home/documents | My Documents | home | yes | no | yes | yes | yes | — |
| /marketplace/cart | Shopping Cart | marketplace | yes | no | yes | yes | yes | — |
| /marketplace/substitutions | Substitutions | marketplace | yes | no | yes | yes | yes | — |
| /shell/file-manager | File manager | shell | yes | no | yes | yes | yes | — |
| /shell/task-manager | Task manager | shell | yes | no | yes | no | yes | — |
| /ask | Ask | intelligent | yes | no | yes | yes | yes | — |
| /intelligence | Health Intelligence | intelligent | yes | no | yes | no | yes | — |
| /search | Search | intelligent | yes | no | yes | yes | yes | — |
| /guidance | Guidance | intelligent | yes | no | yes | yes | yes | — |
| /guidance/reminders | Reminders & Prompts | intelligent | yes | no | yes | no | yes | — |
| /guidance/education | Health Education | intelligent | yes | no | yes | no | yes | — |
| /learning | Impilo Fundo | professional | yes | no | yes | no | yes | — |
| /learning/catalog | Impilo Fundo Catalogue | professional | yes | no | yes | no | yes | — |
| /learning/courses/[courseId] | Impilo Fundo Course | professional | yes | no | yes | yes | yes | — |
| /learning/my-learning | My Learning | professional | yes | no | yes | no | yes | — |
| /learning/enrolments/[enrolmentId] | Enrolment Player | professional | yes | no | yes | no | yes | — |
| /learning/enrolments/[enrolmentId]/lessons/[lessonId] | Lesson Player | professional | yes | no | yes | no | yes | — |
| /learning/pathways | Learning Pathways | professional | yes | no | yes | no | yes | — |
| /learning/pathways/[pathwayId] | Pathway Detail | professional | yes | no | yes | no | yes | — |
| /learning/record | Learning Record | professional | yes | no | yes | no | yes | — |
| /learning/assessments/[assessmentId] | Assessment | professional | yes | no | yes | no | yes | — |
| /learning/assessments/[assessmentId]/attempt | Assessment Attempt | professional | yes | no | yes | yes | yes | — |
| /learning/attempts/[attemptId] | Attempt Result | professional | yes | no | yes | no | yes | — |
| /learning/certificates | Certificates | professional | yes | no | yes | no | yes | — |
| /learning/certificates/[certificateId] | Certificate Detail | professional | yes | no | yes | no | yes | — |
| /learning/cpd | CPD Evidence | professional | yes | no | yes | no | yes | — |
| /learning/reports | Learning Reports | professional | yes | no | yes | no | yes | — |
| /learning/reports/cohorts | Cohort Report | professional | yes | no | yes | no | yes | — |
| /learning/reports/courses | Course Report | professional | yes | no | yes | no | yes | — |
| /learning/reports/overdue | Overdue Learning | professional | yes | no | yes | no | yes | — |
| /learning/reports/assessments | Assessment Report | professional | yes | no | yes | no | yes | — |
| /learning/studio | Fundo Studio | professional | yes | no | yes | no | yes | — |
| /learning/studio/courses | Studio Courses | professional | no | no | yes | no | yes | — |
| /learning/studio/courses/new | Studio New Course | professional | yes | no | yes | yes | yes | — |
| /learning/studio/courses/[courseId] | Studio Course Detail | professional | yes | no | yes | no | yes | — |
| /learning/studio/courses/[courseId]/builder | Studio Course Builder | professional | yes | no | yes | yes | yes | — |
| /learning/studio/library | Studio Library | professional | yes | no | yes | yes | yes | — |
| /learning/studio/media | Studio Media | professional | yes | no | yes | yes | yes | — |
| /learning/studio/media/recordings | Media Recordings | professional | yes | no | yes | no | yes | — |
| /learning/studio/media/scripts | Media Scripts | professional | yes | no | yes | yes | yes | — |
| /learning/studio/media/voiceovers | Media Voiceovers | professional | yes | no | yes | no | yes | — |
| /learning/studio/media/[mediaId] | Media Asset | professional | yes | no | yes | no | yes | — |
| /learning/studio/assessments | Studio Assessments | professional | yes | no | yes | yes | yes | — |
| /learning/studio/surveys | Studio Surveys | professional | yes | no | yes | yes | yes | — |
| /learning/studio/ai | Studio AI | professional | yes | no | yes | yes | yes | — |
| /learning/studio/publish | Studio Publish | professional | yes | no | yes | yes | yes | — |
| /learning/studio/analytics | Studio Analytics | professional | yes | no | yes | no | yes | — |
| /learning/library | Fundo Library | professional | yes | no | yes | no | yes | — |
| /learning/library/resources | Library Resources | professional | yes | no | yes | no | yes | — |
| /learning/library/uploads | Library Uploads | professional | yes | no | yes | yes | yes | — |
| /learning/library/[resourceId] | Library Resource Detail | professional | yes | no | yes | no | yes | — |
| /learning/notifications | Learning Notifications | professional | yes | no | yes | yes | yes | — |
| /learning/surveys/[surveyId] | Learning Survey | professional | yes | no | yes | no | yes | — |
| /learning/surveys/[surveyId]/respond | Respond to Survey | professional | yes | no | yes | yes | yes | — |
| /learning/feedback/course/[courseId] | Course Feedback | professional | yes | no | yes | yes | yes | — |
| /learning/admin | Fundo Admin | professional | yes | no | yes | no | yes | — |
| /learning/admin/courses | Admin Courses | professional | no | no | yes | no | yes | — |
| /learning/admin/courses/new | New Course | professional | yes | no | yes | no | yes | — |
| /learning/admin/courses/[courseId]/edit | Edit Course | professional | no | no | yes | no | yes | — |
| /learning/admin/pathways | Admin Pathways | professional | yes | no | yes | no | yes | — |
| /learning/admin/pathways/new | New Pathway | professional | yes | no | yes | no | yes | — |
| /learning/admin/pathways/[pathwayId]/edit | Edit Pathway | professional | yes | no | yes | yes | yes | — |
| /learning/admin/assessments | Admin Assessments | professional | yes | no | yes | no | yes | — |
| /learning/admin/assessments/new | New Assessment | professional | yes | no | yes | no | yes | — |
| /learning/admin/assessments/[assessmentId]/edit | Edit Assessment | professional | yes | no | yes | yes | yes | — |
| /nhume | Nhume Logistics | operations | yes | no | yes | no | yes | — |
| /nhume/dashboard | Nhume Operations Dashboard | operations | yes | no | yes | yes | yes | — |
| /nhume/deliveries | Nhume Deliveries | operations | yes | no | yes | yes | yes | — |
| /nhume/deliveries/new | New Delivery Request | operations | yes | no | yes | yes | yes | — |
| /nhume/deliveries/[deliveryId] | Delivery Detail | operations | yes | no | yes | yes | yes | — |
| /nhume/dispatcher | Nhume Dispatcher Console | operations | yes | no | yes | yes | yes | — |
| /nhume/map | Fleet Tracking Map | operations | yes | no | yes | yes | yes | — |
| /nhume/courier | Courier / Driver Console | operations | yes | no | yes | yes | yes | — |
| /nhume/fleet | Fleet & Asset Management | operations | yes | no | yes | yes | yes | — |
| /nhume/fleet/[assetId] | Fleet Asset | operations | yes | no | yes | yes | yes | — |
| /nhume/couriers | Drivers & Couriers | operations | yes | no | yes | yes | yes | — |
| /nhume/couriers/[courierId] | Courier Profile | operations | yes | no | yes | yes | yes | — |
| /nhume/policies | Delivery Policies | operations | yes | no | yes | yes | yes | — |
| /nhume/autonomous | Autonomous Delivery | operations | yes | no | yes | yes | yes | — |
| /nhume/analytics | Nhume Analytics | operations | yes | no | yes | yes | yes | — |
| /nhume/custody/[deliveryId] | Chain of Custody | operations | yes | no | yes | yes | yes | — |
| /nhume/track/[deliveryId] | Track Delivery | home | yes | no | yes | yes | yes | — |
| /madi | Madi Blood Services | operations | yes | no | yes | no | yes | — |
| /madi/donor | My Donor Hub | home | yes | no | yes | yes | yes | — |
| /madi/donor/register | Become a Donor | home | yes | no | yes | yes | yes | — |
| /madi/donor/profile | Donor Profile | home | yes | no | yes | yes | yes | — |
| /madi/donor/screening | Donor Screening | home | yes | no | yes | yes | yes | — |
| /madi/donor/drives | Donation Drives Near Me | home | yes | no | yes | yes | yes | — |
| /madi/donor/history | Donation History | home | yes | no | yes | yes | yes | — |
| /madi/donor/feedback | Donor Feedback | home | yes | no | yes | yes | yes | — |
| /madi/donor/preferences | Donor Preferences | home | yes | no | yes | yes | yes | — |
| /madi/drives | Donation Drives | operations | yes | no | yes | yes | yes | — |
| /madi/drives/new | New Donation Drive | operations | yes | no | yes | yes | yes | — |
| /madi/drives/[driveId] | Drive Detail | operations | yes | no | yes | yes | yes | — |
| /madi/blood-bank | Local Blood Bank | operations | yes | no | yes | yes | yes | — |
| /madi/blood-bank/orders | Blood Bank Orders | operations | yes | no | yes | no | yes | — |
| /madi/blood-bank/stock | Blood Stock | operations | yes | no | yes | yes | yes | — |
| /madi/blood-bank/crossmatch | Crossmatch | operations | yes | no | yes | no | yes | — |
| /madi/blood-bank/issue | Issue Blood | operations | yes | no | yes | no | yes | — |
| /madi/blood-bank/fridges | Blood Fridge Monitoring | operations | yes | no | yes | yes | yes | — |
| /madi/central-bank | Central Blood Bank | operations | yes | no | yes | yes | yes | — |
| /madi/orders | Order Blood | queue | yes | no | yes | yes | yes | — |
| /madi/orders/[orderId] | Blood Order Detail | queue | yes | no | yes | yes | yes | — |
| /madi/transfusion | Record Transfusion | queue | yes | no | yes | yes | yes | — |
| /madi/transfusion/[episodeId] | Transfusion Episode | operations | yes | no | yes | yes | yes | — |
| /madi/haemovigilance | Haemovigilance | operations | yes | no | yes | yes | yes | — |
| /madi/haemovigilance/national | National Haemovigilance | operations | yes | no | yes | yes | yes | — |
| /madi/dashboard | Madi Dashboard | operations | yes | no | yes | yes | yes | — |
| /madi/processing | Blood Processing | operations | yes | no | yes | yes | yes | — |
| /madi/logistics | Blood Logistics | operations | yes | no | yes | yes | yes | — |
| /live | Impilo Live | operations | yes | no | yes | yes | yes | — |
| /live/manage | Live Event Management | operations | yes | no | yes | yes | yes | — |
| /live/admin | Impilo Live Administration | operations | yes | no | yes | yes | yes | — |
| /live/create | Create Live Event | operations | yes | no | yes | yes | yes | — |
| /live/discover | Discover Live Events | home | yes | no | yes | yes | yes | — |
| /live/saved | Saved Live Events | home | yes | no | yes | yes | yes | — |

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
| citizen-app | apps/mobile/citizen-app/src/screens/crvs/UbomiCrvsScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/discover/ProviderDiscoveryScreen.tsx | 0 | no |
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
| citizen-app | apps/mobile/citizen-app/src/screens/personal/MonitoringSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/PatientConsentScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/PersonalScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/PrescriptionsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/PrivacyPolicyScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ProductionReadinessJourneyScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ProfileSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ProgramsScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/QueueStatusSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/RecordSharingScreen.tsx | 1 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/RecordsScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ReferralsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/RemindersScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/ResultsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/SettingsSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/TermsOfUseScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/VerifyCredentialScreen.tsx | 1 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/WalletSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/publicHealth/PublicHealthScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/rito/FeedbackScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/rito/TrackFeedbackScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/ClubsScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/CommunitiesScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/CrowdfundingScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/PagesScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/ProfessionalPagesScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/SocialFeedScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/SocialHubScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/social/TimelineComposer.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/support/SupportScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/telehealth/LiveKitMobileConsultRoom.tsx | 0 | no |
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
| provider-app | apps/mobile/provider-app/src/screens/courier/CourierDashboardScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/courier/CourierProofScreen.tsx | 0 | no |
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
| provider-app | apps/mobile/provider-app/src/screens/outreach/ScreeningScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/APGARScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ActivityFeedScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/AdminRegistryHubScreen.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/provider/AssistedCommunicationPreferencesScreen.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/provider/BedManagementScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/BillingScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/BookingRequestsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/CarePlanDetailScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ClinicalToolsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/CoreTransactionJourneyShellScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/CriticalEventScreen.tsx | 1 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/DeveloperHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/DiagnosisPanel.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/DiagnosticsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/DischargeClearanceScreen.tsx | 1 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/DischargeScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/EdVisitScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/EncounterScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/FacilityAdminScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/FinanceOverviewScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/FundoLearningShellScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/HealthOsAppsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/InpatientScreen.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/provider/LabHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/LabOrderPanel.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/LiveKitMobileConsultRoom.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/MarketplaceOpsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/MessagingScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/NEWS2ScoringScreen.tsx | 1 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/NotesPanel.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PACSViewerScreen.tsx | 1 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PatientLookupScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PatientRegistrationScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PharmacyDispensingScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PharmacyHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PrescriptionPanel.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProductionReadinessJourneyScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProfessionalChannelsHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProfessionalProfileScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProfessionalSettingsHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProviderDashboardScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ProviderSocialScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/PublicHealthFieldTasksScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/QueueManagementScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ReferralPanel.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ReportsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ResultsViewScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ResuscitationScreen.tsx | 1 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ScheduleScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/ShiftHandoffScreen.tsx | 1 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/SpecialtyWorkspacePanel.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/provider/SystemStatusScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/TelemedicineScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/TheatreProcedureScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/TraumaScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/TriageScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/VashandiAttendanceScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/VashandiAvailabilityScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/VashandiFacilityStaffScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/VashandiRosterScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/VashandiWorkforceHubScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/VitalsMonitorScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/VitalsPanel.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/WardAlertsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/provider/WorkflowDispatchOpsScreen.tsx | 0 | yes |
| provider-app | apps/mobile/provider-app/src/screens/rito/MySafetyCasesScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/rito/ReportSafetyScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/supervisor/EscalationsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/supervisor/InventoryScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/supervisor/StockScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/supervisor/SupervisorDashboardScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/supervisor/TeamOverviewScreen.tsx | 0 | no |
