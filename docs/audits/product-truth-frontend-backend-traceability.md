# Product Truth — Frontend-to-Backend Traceability

> Generated: 2026-06-20T12:31:06.437Z
> Web surfaces: **614** | Mobile screens: **174**

## Web routes (one-ui-shell)

| Route | Title | Zone | BFF backing | Gateway | Reads real | Writes real | Mock/stub | Gaps |
|-------|-------|------|-------------|---------|------------|-------------|-----------|------|
| /bootstrap | Platform Bootstrap | auth | yes | no | yes | no | no | — |
| /auth/login | Sign In | auth | yes | no | yes | yes | no | — |
| /auth/login/email | Sign In with Email | auth | no | no | yes | no | no | — |
| /auth/login/provider-id | Sign In with Provider ID | auth | yes | no | yes | yes | no | — |
| /auth/login/biometric | Biometric Verification | auth | yes | no | yes | yes | no | — |
| /auth/forgot-password | Forgot Password | auth | yes | no | yes | no | no | — |
| /auth/reset-password | Reset Password | auth | yes | no | yes | no | no | — |
| /auth/mfa | Multi-Factor Authentication | auth | yes | no | yes | yes | no | — |
| /auth/logout | Signing Out | auth | yes | no | yes | yes | no | — |
| /auth | Authentication | auth | no | no | yes | no | no | — |
| /auth/register | Create Account | auth | yes | no | yes | yes | no | — |
| /auth/register/assurance | Identity Assurance | auth | yes | no | yes | yes | no | — |
| /auth/register/status | Registration Status | auth | no | no | yes | no | no | — |
| /auth/resolving | Resolving Session | auth | yes | no | yes | no | no | — |
| /auth/context-chooser | Choose Work Context | auth | yes | no | yes | no | no | — |
| /privacy | Privacy Policy | auth | no | no | yes | no | no | — |
| /terms | Terms of Use | auth | no | no | yes | no | no | — |
| /consent | Review Policies | auth | yes | no | yes | yes | no | — |
| /account-deletion | Account Deletion | auth | no | no | yes | no | no | — |
| /privacy/app-stores | App Store Privacy | auth | no | no | yes | no | no | — |
| /clinical | Clinical Care | queue | yes | no | yes | no | no | — |
| /core-transaction | Core Transaction | queue | yes | no | yes | yes | no | — |
| /core-transaction/[transactionId] | Core Transaction Detail | queue | yes | no | yes | yes | no | — |
| /client-journey | Client Journey | home | yes | no | yes | yes | no | — |
| /provider-workspace | Provider Workspace | queue | yes | no | yes | yes | no | — |
| /platform-journey | Platform Journey | admin | yes | no | yes | yes | no | — |
| /clinical-tools | Clinical Tools | queue | yes | no | yes | yes | no | — |
| /clinical-tools/rules | Rules Engine | queue | yes | no | yes | yes | yes | — |
| /clinical-tools/forms | Form Builder | queue | yes | no | yes | yes | yes | — |
| /clinical/control-tower | Control Tower | queue | yes | no | yes | yes | no | — |
| /clinical/dictation | Voice Dictation | queue | yes | no | yes | no | no | — |
| /clinical/emergency | ED / Casualty | queue | yes | no | yes | yes | no | — |
| /clinical/inpatient | Inpatient Care | queue | yes | no | yes | no | no | — |
| /clinical/inpatient/admissions | Inpatient Admissions | queue | yes | no | yes | yes | no | — |
| /clinical/inpatient/admissions/[admissionId] | Inpatient Episode | queue | yes | no | yes | yes | no | — |
| /clinical/inpatient/ward-board | Ward Board | queue | yes | no | yes | yes | no | — |
| /clinical/inpatient/nursing | Nursing Workbench | queue | yes | no | yes | yes | no | — |
| /clinical/inpatient/rounds | Medical Rounds | queue | yes | no | yes | yes | no | — |
| /clinical/inpatient/discharge/[admissionId] | Inpatient Discharge | queue | yes | no | yes | yes | no | — |
| /production-command-centre | Production Command Centre | admin | yes | no | yes | no | no | — |
| /platform/all-features | All Features | admin | yes | no | yes | no | no | — |
| /health-os/command-centre | Health OS Command Centre | admin | no | no | yes | no | no | — |
| /data-intelligence | Data & Intelligence | reports | yes | no | yes | no | no | — |
| /data-intelligence/quality | Data Quality | reports | yes | no | yes | no | no | — |
| /data-intelligence/pipelines | Data Pipelines | reports | yes | no | yes | yes | no | — |
| /data-intelligence/integration | Integration Monitor | reports | yes | no | yes | no | no | — |
| /data-intelligence/reports | Reporting Hub | reports | yes | no | yes | no | no | — |
| /data-intelligence/audit | Audit Intelligence | reports | yes | no | yes | no | no | — |
| /ndila | Ndila Maps | operations | yes | no | yes | yes | no | — |
| /public-health | Public Health | admin | yes | no | yes | yes | no | — |
| /public-health/surveillance | Surveillance | admin | yes | no | yes | yes | no | — |
| /public-health/campaigns | Campaigns | admin | yes | no | yes | yes | yes | — |
| /public-health/site-registry | Site Registry | admin | yes | no | yes | yes | no | — |
| /public-health/site-registry/[siteId] | Site Profile | admin | yes | no | yes | yes | no | — |
| /public-health/oversight | National oversight | admin | yes | no | yes | yes | no | — |
| /omnichannel | Omnichannel Hub | admin | yes | no | yes | yes | no | — |
| /coverage | Coverage Operations | admin | yes | no | yes | yes | yes | — |
| /coverage/enroll | Enroll in Coverage | home | yes | no | yes | yes | no | — |
| /coverage/member | My Coverage | home | yes | no | yes | yes | no | — |
| /coverage/contracts | Provider Contracts | admin | yes | no | yes | yes | no | — |
| /id-services | Identity Services | admin | yes | no | yes | yes | no | — |
| /ai-governance | AI Governance | admin | yes | no | yes | yes | no | — |
| /ai-governance/models/[id] | AI Model | admin | yes | no | yes | yes | no | — |
| /access | Access Channels | admin | yes | no | yes | yes | no | — |
| /kiosk | Self Check-In | auth | yes | no | yes | yes | no | — |
| / | Home | home | no | no | yes | no | no | — |
| /home | Home | home | yes | no | yes | yes | no | — |
| /home/notifications | Notifications | home | yes | no | yes | yes | no | — |
| /home/profile | My Profile | home | yes | no | yes | yes | no | — |
| /home/preferences | Preferences | home | yes | no | yes | yes | no | — |
| /home/credentials | Credentials & CPD | home | yes | no | yes | yes | no | — |
| /home/referrals | My Referrals | home | no | no | yes | no | no | — |
| /home/medications | My Medications | home | yes | no | yes | yes | no | — |
| /home/conditions | My Conditions | home | yes | no | yes | no | no | — |
| /home/allergies | My Allergies | home | yes | no | yes | no | no | — |
| /home/results | My Results | home | yes | no | yes | no | no | — |
| /home/bookings | My Bookings | home | yes | no | yes | yes | no | — |
| /home/bookings/new | Book a Service | home | yes | no | yes | yes | no | — |
| /home/bookings/[bookingId] | Booking Details | home | yes | no | yes | yes | no | — |
| /home/appointments | My Appointments | home | yes | no | yes | yes | no | — |
| /home/appointments/[appointmentId] | Appointment Details | home | yes | no | yes | yes | no | — |
| /citizen | Citizen Services | home | yes | no | yes | yes | no | — |
| /citizen/health-id/qr | My Health ID QR | home | yes | no | yes | no | no | — |
| /citizen/health-id/request | Request Health ID | home | yes | no | yes | yes | no | — |
| /citizen/id-recovery | ID Recovery | home | yes | no | yes | no | no | — |
| /citizen/delegated-pickup | Delegated Pickup | home | yes | no | yes | yes | no | — |
| /citizen/record-sharing | Share My Record | home | yes | no | yes | yes | no | — |
| /verify/credential | Verify Credential | home | no | no | yes | yes | no | — |
| /share/claim | Claim Shared Documents | home | yes | no | yes | yes | no | — |
| /collaboration/access | Provider collaboration access | home | yes | no | yes | yes | yes | — |
| /facility | Select Facility | facility | yes | no | yes | no | no | — |
| /facility/[id] | Facility Details | facility | yes | no | yes | no | no | — |
| /workspace | Select Workspace | workspace | yes | no | yes | yes | no | — |
| /workspace/[id] | Workspace Details | workspace | yes | no | yes | no | no | — |
| /shift | Start Shift | shift | yes | no | yes | yes | no | — |
| /shift/active | Active Shift | shift | yes | no | yes | yes | no | — |
| /shift/handover | Shift Handover | shift | yes | no | yes | yes | no | — |
| /scheduling | Scheduling | queue | yes | no | yes | yes | no | — |
| /scheduling/roster | Staff Roster | queue | yes | no | yes | yes | no | — |
| /scheduling/on-call | On-Call Schedule | queue | yes | no | yes | yes | no | — |
| /scheduling/noticeboard | Provider Noticeboard | queue | yes | no | yes | yes | no | — |
| /scheduling/booking-requests | Booking Requests | queue | yes | no | yes | yes | no | — |
| /scheduling/today | Today's Appointments | queue | yes | no | yes | yes | no | — |
| /scheduling/bookings/config | Booking Configuration | queue | yes | no | yes | yes | no | — |
| /communication | Communication Hub | queue | yes | no | yes | yes | no | — |
| /communication/secure-messaging | Secure Messaging | queue | yes | no | yes | yes | no | — |
| /queue | Patient Queue | queue | yes | no | yes | yes | no | — |
| /queue/triage | Triage Queue | queue | yes | no | yes | yes | no | — |
| /queue/waiting | Waiting Room | queue | yes | no | yes | yes | no | — |
| /queue/search | Patient Search | queue | yes | no | yes | yes | no | — |
| /queue/walk-in | Walk-in Registration | queue | yes | no | yes | yes | no | — |
| /queue/scheduled | Scheduled Visits | queue | yes | no | yes | yes | no | — |
| /queue/incoming-referrals | Incoming Referrals | queue | yes | no | yes | yes | no | — |
| /ehr/[patientId] | Patient Chart | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/summary | Patient Summary | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/ips | International Patient Summary | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/vitals | Vitals | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/maternity | Maternity Monitoring | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/history | Medical History | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/conditions | Conditions | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/medications | Medications | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/allergies | Allergies | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/orders | Orders | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/results | Results | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/notes | Clinical Notes | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/documents | Documents | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/encounters | Encounters | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/encounter/[encounterId] | Encounter | ehr | yes | no | yes | yes | yes | — |
| /ehr/[patientId]/immunizations | Immunizations | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/consults | Consults & Referrals | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/referrals | Referrals | ehr | no | no | yes | no | no | — |
| /ehr/[patientId]/teleconsults | Teleconsults | ehr | no | no | yes | no | no | — |
| /ehr/[patientId]/timeline | Timeline | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/discharge | Discharge | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/care-plans | Care Plans | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/procedures | Procedures | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/growth-chart | Growth Chart | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/family-history | Family History | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/social-history | Social History | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/functional-status | Functional Status | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/advance-directives | Advance Directives | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/care-team | Care Team | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/preferences/communications | Communication Preferences | ehr | no | no | yes | no | no | — |
| /ehr/[patientId]/goals | Goals | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/assessments | Assessments | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/charts | Ward Charts | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/imaging | Imaging | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/imaging/viewer | DICOM Viewer | ehr | yes | no | yes | yes | no | — |
| /admin | Administration | admin | yes | no | yes | no | no | — |
| /admin/users | Worker & Provider Access | admin | yes | no | yes | yes | no | — |
| /admin/users/[id] | User Details | admin | yes | no | yes | yes | no | — |
| /admin/roles | Role Management | admin | yes | no | yes | yes | no | — |
| /admin/policies | Policy Management | admin | yes | no | yes | yes | no | — |
| /admin/audit | Audit Trail | admin | yes | no | yes | no | no | — |
| /admin/audit/[id] | Audit Entry | admin | yes | no | yes | no | yes | — |
| /admin/consent | Consent Management | admin | yes | no | yes | no | no | — |
| /admin/devices | Device Management | admin | yes | no | yes | no | no | — |
| /admin/keys | Key Management | admin | yes | no | yes | no | no | — |
| /admin/federation | Federation | admin | yes | no | yes | no | no | — |
| /admin/tenants | Tenant Management | admin | yes | no | yes | no | no | — |
| /admin/break-glass | Break Glass Log | admin | yes | no | yes | yes | no | — |
| /admin/beds | Bed & Ward Admin | admin | yes | no | yes | yes | no | — |
| /admin/queues | Queue Configuration | admin | yes | no | yes | no | no | — |
| /admin/data-export | Data Export | admin | yes | no | yes | yes | no | — |
| /admin/data-governance | Data Governance | admin | yes | no | yes | yes | no | — |
| /admin/clinical-curation | Clinical Knowledge Curation | admin | yes | no | yes | yes | no | — |
| /admin/system-monitor | System Monitor | admin | yes | no | yes | yes | no | — |
| /admin/integration-status | Integration Status | admin | yes | no | yes | yes | no | — |
| /admin/notifications/templates | Notification Templates | admin | yes | no | yes | yes | no | — |
| /admin/integration-templates | Integration Templates | admin | yes | no | yes | yes | no | — |
| /admin/sidecar-retirement | Sidecar Retirement | admin | yes | no | yes | no | no | — |
| /dags | Data Access Governance | admin | yes | no | yes | yes | no | — |
| /dags/policy | Data Access Policy | admin | yes | no | yes | yes | no | — |
| /registry-admin | Registry Administration | admin | yes | no | yes | yes | no | — |
| /organization-admin | Organization Administration | admin | yes | no | yes | no | no | — |
| /organization-admin/facility | Facility Administration | admin | yes | no | yes | no | no | — |
| /organization-admin/staffing | Staffing & Scheduling | admin | yes | no | yes | no | no | — |
| /organization-admin/governance | Organisations & governance | admin | yes | no | yes | no | no | — |
| /organization-admin/governance/[id] | Organisation detail | admin | yes | no | yes | no | no | — |
| /registry/clients | Client Registry | registry | yes | no | yes | yes | no | — |
| /registry/clients/new | New Client Registration | registry | yes | no | yes | yes | no | — |
| /registry/clients/[id] | Client Identity Workspace | registry | yes | no | yes | yes | no | — |
| /registry/trust | Trust & Federation | registry | yes | no | yes | no | no | — |
| /registry/mvumo | Mvumo â€” Digital Consent | registry | yes | no | yes | yes | no | — |
| /registry | Registry Hub | registry | yes | no | yes | yes | no | — |
| /registry/intake | Registry Intake | registry | yes | no | yes | yes | no | — |
| /registry/locality-review | Locality gazetteer review | registry | yes | no | yes | yes | no | — |
| /registry/facility-lifecycle | Facility regulatory lifecycle | registry | yes | no | yes | no | no | — |
| /registry/providers | Provider Registry | registry | yes | no | yes | yes | no | — |
| /registry/providers/verification | Provider Verification Queue | registry | yes | no | yes | yes | no | — |
| /registry/providers/[id] | Provider Profile | registry | yes | no | yes | yes | no | — |
| /registry/provider-council/self-service | Council self-service | registry | yes | no | yes | yes | no | — |
| /registry/provider-council/council-workspace | Council operations | registry | yes | no | yes | yes | no | — |
| /registry/facilities | Facility Registry | registry | yes | no | yes | yes | no | — |
| /registry/facilities/[id] | Facility Profile | registry | yes | no | yes | yes | no | — |
| /registry/terminology | Terminology Browser | registry | yes | no | yes | no | no | — |
| /registry/terminology/[id] | Concept Details | registry | yes | no | yes | no | no | — |
| /registry/products | Product Registry | registry | yes | no | yes | no | no | — |
| /registry/products/[id] | Product Details | registry | yes | no | yes | no | no | — |
| /ubomi | UBOMI Civil Registry | registry | yes | no | yes | yes | no | — |
| /marketplace | Health Marketplace | marketplace | yes | no | yes | yes | no | — |
| /marketplace/catalog | Service Catalog | marketplace | yes | no | yes | yes | no | — |
| /marketplace/orders | My Orders | marketplace | yes | no | yes | yes | no | — |
| /marketplace/orders/[id] | Order Details | marketplace | yes | no | yes | yes | no | — |
| /marketplace/ops | Marketplace Operations | marketplace | yes | no | yes | yes | no | — |
| /marketplace/vendor | Vendor Fulfilment | marketplace | yes | no | yes | no | no | — |
| /marketplace/vendor/orders | Vendor Orders | marketplace | yes | no | yes | yes | no | — |
| /marketplace/pickup | Pickup Handoff | marketplace | yes | no | yes | yes | no | — |
| /marketplace/vendors | Vendors | marketplace | yes | no | yes | yes | no | — |
| /marketplace/bookings | Bookings | marketplace | yes | no | yes | yes | no | — |
| /finance | Finance Dashboard | finance | yes | no | yes | yes | no | — |
| /finance/claims | Claims | finance | yes | no | yes | no | no | — |
| /finance/claims/[id] | Claim Details | finance | yes | no | yes | no | no | — |
| /finance/billing | Billing | finance | yes | no | yes | yes | no | — |
| /finance/billing/[id] | Bill Details | finance | yes | no | yes | yes | no | — |
| /finance/payments | Payments | finance | yes | no | yes | no | no | — |
| /finance/msika-governance | MSIKA Governance | finance | yes | no | yes | yes | yes | — |
| /finance/ledger | Ledger | finance | yes | no | yes | no | no | — |
| /finance/workspace | Finance Workspace | finance | yes | no | yes | no | no | — |
| /finance/settlements | Settlements | finance | yes | no | yes | yes | no | — |
| /finance/remittances | Remittances | finance | yes | no | yes | yes | no | — |
| /finance/reconciliation | Reconciliation | finance | yes | no | yes | yes | no | — |
| /finance/refunds | Refunds | finance | yes | no | yes | yes | no | — |
| /finance/payer-ops | Payer Operations | finance | yes | no | yes | yes | no | — |
| /finance/payer-claims | Payer Claims Queue | finance | yes | no | yes | yes | no | — |
| /finance/payer-claims/[claimId] | Payer Claim | finance | yes | no | yes | yes | no | — |
| /finance/tariffs | Tariff Management | finance | yes | no | yes | no | no | — |
| /finance/costa | COSTA hub | finance | yes | no | yes | yes | no | — |
| /finance/costa/encounter/[encounterId] | COSTA encounter timeline | finance | yes | no | yes | yes | yes | — |
| /finance/service-access | Service access decisions | finance | yes | no | yes | yes | no | — |
| /finance/mushex-platform | MusheX platform admin | finance | yes | no | yes | yes | no | — |
| /finance/mushex-platform/wallets/[walletId] | Custodial wallet | finance | yes | no | yes | yes | yes | — |
| /finance/mushex-platform/remittance/[transferId] | Remittance transfer | finance | yes | no | yes | yes | yes | — |
| /finance/mushex-platform/cards/[cardId] | Card profile | finance | yes | no | yes | yes | yes | — |
| /finance/mushex-platform/reversals/[reversalId] | Reversal record | finance | yes | no | yes | yes | yes | — |
| /finance/commerce-integrations | Commerce & Payer Stack | finance | yes | no | yes | no | no | — |
| /finance/reports | Financial reports | finance | yes | no | yes | yes | no | — |
| /finance/my-account | My Healthcare Account | finance | yes | no | yes | yes | no | — |
| /wallet | Wallet | finance | yes | no | yes | yes | no | — |
| /wallet/deposit | Deposit | finance | yes | no | yes | yes | no | — |
| /wallet/send | Send Money | finance | yes | no | yes | yes | no | — |
| /wallet/transactions | Transactions | finance | yes | no | yes | yes | no | — |
| /wallet/cards | Cards | finance | yes | no | yes | yes | no | — |
| /wallet/merchant | Merchant | finance | yes | no | yes | yes | no | — |
| /beds | Bed Management | queue | yes | no | yes | yes | no | — |
| /pharmacy | Pharmacy Dashboard | pharmacy | yes | no | yes | no | no | — |
| /pharmacy/dispense | Dispensing | pharmacy | yes | no | yes | no | no | — |
| /pharmacy/stock | Stock Management | pharmacy | yes | no | yes | no | no | — |
| /pharmacy/prescriptions | Prescriptions | pharmacy | yes | no | yes | yes | no | — |
| /pharmacy/transaction-journey | Rx Transaction Journey | pharmacy | yes | no | yes | yes | no | — |
| /inventory | Inventory Dashboard | inventory | yes | no | yes | yes | no | — |
| /inventory/movements | Stock Movements | inventory | yes | no | yes | yes | no | — |
| /inventory/counts | Stock Counts | inventory | yes | no | yes | yes | no | — |
| /inventory/requisitions | Requisitions | inventory | yes | no | yes | yes | no | — |
| /inventory/stock-management | Stock Management | inventory | yes | no | yes | yes | no | — |
| /enterprise | Enterprise Resources | enterprise | yes | no | yes | yes | no | — |
| /enterprise/warehousing | Warehousing & distribution | enterprise | yes | no | yes | yes | no | — |
| /enterprise/fleet | Fleet & logistics | enterprise | yes | no | yes | yes | no | — |
| /enterprise/charge-sheet | Charge sheet | enterprise | yes | no | yes | yes | no | — |
| /enterprise/oversight | National Enterprise Oversight | enterprise | yes | no | yes | yes | no | — |
| /erp | Institutional ERP | enterprise | yes | no | yes | no | no | — |
| /erp/gl | General ledger | enterprise | yes | no | yes | yes | no | — |
| /erp/hr | HR & payroll | enterprise | yes | no | yes | yes | no | — |
| /erp/procurement | Procurement | enterprise | yes | no | yes | yes | no | — |
| /erp/assets | Fixed assets | enterprise | yes | no | yes | yes | no | — |
| /workspace/aggregate | Aggregate oversight | reports | yes | no | yes | no | no | — |
| /reports | Reports | reports | yes | no | yes | yes | no | — |
| /reports/facility | Facility Reports | reports | yes | no | yes | yes | no | — |
| /reports/clinical | Clinical Reports | reports | yes | no | yes | yes | no | — |
| /reports/operational | Operational Reports | reports | yes | no | yes | yes | no | — |
| /reports/custom | Custom Reports | reports | yes | no | yes | yes | no | — |
| /reports/[id] | Report Details | reports | yes | no | yes | yes | yes | — |
| /settings | Settings | settings | yes | no | yes | no | no | — |
| /settings/account | Account Settings | settings | yes | no | yes | yes | no | — |
| /settings/security | Security Settings | settings | yes | no | yes | yes | no | — |
| /settings/notifications | Notification Preferences | settings | yes | no | yes | yes | no | — |
| /settings/display | Display Settings | settings | yes | no | yes | yes | no | — |
| /settings/integrations | Integrations | settings | yes | no | yes | yes | no | — |
| /settings/privacy | Privacy & Data | settings | yes | no | yes | yes | no | — |
| /telemedicine | Telemedicine Hub | queue | yes | no | yes | yes | no | — |
| /telemedicine/new | New Teleconsultation | queue | yes | no | yes | yes | no | — |
| /telemedicine/session/[sessionId] | Teleconsult Session | queue | yes | no | yes | yes | no | — |
| /telemedicine/analytics | Telemedicine Analytics | queue | yes | no | yes | yes | no | — |
| /provider/activate | Activate Provider Role | auth | yes | no | yes | no | no | — |
| /provider/status | Provider Status | auth | yes | no | yes | no | no | — |
| /wellness | Wellness Hub | wellness | yes | no | yes | yes | no | — |
| /wellness/dashboard | Wellness Dashboard | wellness | yes | no | yes | yes | no | — |
| /wellness/goals | Health Goals | wellness | yes | no | yes | yes | no | — |
| /wellness/programs | Prevention Programs | wellness | yes | no | yes | yes | no | — |
| /wellness/screenings | Screening Schedule | wellness | yes | no | yes | yes | no | — |
| /wellness/activity | Activity & Fitness | wellness | yes | no | yes | yes | no | — |
| /wellness/connect | Health Connect ingest | wellness | yes | no | yes | no | yes | — |
| /wellness/diet | Diet & Nutrition | wellness | yes | no | yes | yes | no | — |
| /wellness/sleep | Sleep & Recovery | wellness | yes | no | yes | yes | no | — |
| /wellness/clubs | Clubs & Communities | wellness | yes | no | yes | yes | no | — |
| /wellness/challenges | Challenges | wellness | yes | no | yes | yes | no | — |
| /wellness/routes | Routes & Places | wellness | yes | no | yes | yes | no | — |
| /wellness/coaching | Coaching & Habits | wellness | yes | no | yes | yes | no | — |
| /wellness/commodities | Wellness Commodities | wellness | yes | no | yes | yes | no | — |
| /wellness/community | Wellness Community | wellness | yes | no | yes | yes | no | — |
| /social | Social Timeline | wellness | yes | no | yes | yes | no | — |
| /social/drafts | Draft Posts | wellness | yes | no | yes | yes | no | — |
| /social/saved | Saved Posts | wellness | yes | no | yes | yes | no | — |
| /social/moderation | Social Moderation | wellness | yes | no | yes | yes | no | — |
| /communities | Communities | wellness | yes | no | yes | yes | no | — |
| /communities/[id] | Community | wellness | yes | no | yes | yes | no | — |
| /pages | Pages | wellness | yes | no | yes | yes | no | — |
| /pages/[id] | Page | wellness | yes | no | yes | yes | no | — |
| /caregiving | Caregiving Hub | caregiving | yes | no | yes | yes | no | — |
| /caregiving/dependants | My Dependants | caregiving | yes | no | yes | no | no | — |
| /caregiving/delegation | Care Delegation | caregiving | yes | no | yes | no | no | — |
| /caregiving/tasks | Care Tasks | caregiving | yes | no | yes | no | no | — |
| /caregiving/notifications | Care Alerts | caregiving | yes | no | yes | no | no | — |
| /monitoring | Remote Monitoring | monitoring | yes | no | yes | no | no | — |
| /monitoring/devices | My Devices | monitoring | yes | no | yes | yes | no | — |
| /monitoring/readings | Readings & Trends | monitoring | yes | no | yes | yes | no | — |
| /monitoring/alerts | Monitoring Alerts | monitoring | yes | no | yes | yes | no | — |
| /monitoring/care-plans | Chronic Care Plans | monitoring | yes | no | yes | no | no | — |
| /monitoring/provider-dashboard | Patient Monitoring Dashboard | monitoring | yes | no | yes | no | no | — |
| /discover | Find Services | discovery | yes | no | yes | no | no | — |
| /discover/providers | Find a Provider | discovery | yes | no | yes | no | no | — |
| /discover/facilities | Find a Facility | discovery | yes | no | yes | yes | no | — |
| /discover/services | Browse Services | discovery | yes | no | yes | no | no | — |
| /lab | Laboratory | lab | yes | no | yes | yes | no | — |
| /lab/worklist | Lab Worklist | lab | yes | no | yes | yes | no | — |
| /imaging/worklist | Imaging Worklist | lab | yes | no | yes | yes | no | — |
| /imaging/facility | Facility Imaging Dashboard | lab | yes | no | yes | yes | no | — |
| /lab/results | Results Review | lab | yes | no | yes | yes | no | — |
| /lab/catalog | Test Catalog | lab | yes | no | yes | no | no | — |
| /lab/reconciliation | Lab Reconciliation | lab | yes | no | yes | yes | no | — |
| /operations | Operations | operations | yes | no | yes | no | no | — |
| /operations/facility-operations | Facility Operations | operations | yes | no | yes | yes | no | — |
| /operations/facility-operations/district-view | District View | operations | yes | no | yes | yes | no | — |
| /operations/facility-operations/patient-flow | Patient Flow | operations | yes | no | yes | yes | no | — |
| /operations/facility-operations/resources | Resource Operations | operations | yes | no | yes | no | no | — |
| /operations/workflows | Workflow Orchestration | operations | yes | no | yes | yes | no | — |
| /operations/workflows/[instanceId] | Workflow Instance | operations | yes | no | yes | yes | yes | — |
| /operations/dispatch | Dispatch Operations | operations | yes | no | yes | yes | no | — |
| /operations/dispatch/[taskId] | Dispatch Task | operations | yes | no | yes | yes | yes | — |
| /operations/vito | Identity Operations | operations | yes | no | yes | yes | no | — |
| /operations/vito/registration | Client Registration | operations | yes | no | yes | yes | no | — |
| /operations/vito/registration/new | New Registration | operations | yes | no | yes | yes | no | — |
| /operations/vito/issuance | Issuance Queue | operations | yes | no | yes | yes | no | — |
| /operations/vito/issuance/[requestId] | Issuance Request | operations | yes | no | yes | yes | no | — |
| /operations/vito/cards | Smart Cards | operations | yes | no | yes | yes | no | — |
| /operations/vito/cards/pickup | Card Pickup | operations | yes | no | yes | yes | no | — |
| /operations/vito/match | Match Review | operations | yes | no | yes | yes | no | — |
| /operations/vito/dedup | Deduplication | operations | yes | no | yes | yes | no | — |
| /operations/vito/print | Print & Slips | operations | yes | no | yes | yes | no | — |
| /operations/vito/patient-shares | Patient Shares | operations | yes | no | yes | yes | no | — |
| /operations/vito/internal-search | Internal Search | operations | yes | no | yes | yes | no | — |
| /operations/vito/biometrics | Biometrics | operations | yes | no | yes | yes | yes | — |
| /operations/vito/recovery | Recovery & SHS | operations | yes | no | yes | yes | yes | — |
| /operations/vito/registry-admin | Registry Admin | operations | yes | no | yes | yes | no | — |
| /operations/butano | SHR Operations | operations | yes | no | yes | no | no | — |
| /operations/assets | Asset Management | operations | yes | no | yes | yes | no | — |
| /operations/equipment | Equipment Management | operations | yes | no | yes | no | no | — |
| /support | Support | support | yes | no | yes | no | no | — |
| /support/tickets | Support Tickets | support | yes | no | yes | no | no | — |
| /support/knowledge-base | Knowledge Base | support | yes | no | yes | no | no | — |
| /developer | Developer Portal | developer | yes | no | yes | no | no | — |
| /developer/api-catalog | API Catalog | developer | yes | no | yes | no | no | — |
| /developer/clients | Client Registration | developer | yes | no | yes | no | no | — |
| /developer/sandbox | Sandbox | developer | yes | yes | yes | no | no | — |
| /home/documents | My Documents | home | yes | no | yes | yes | no | — |
| /marketplace/cart | Shopping Cart | marketplace | yes | no | yes | yes | no | — |
| /marketplace/substitutions | Substitutions | marketplace | yes | no | yes | yes | no | — |
| /shell/file-manager | File manager | shell | yes | no | yes | yes | no | — |
| /shell/task-manager | Task manager | shell | yes | no | yes | no | no | — |
| /ask | Ask | intelligent | yes | no | yes | yes | no | — |
| /intelligence | Health Intelligence | intelligent | yes | no | yes | no | no | — |
| /search | Search | intelligent | yes | no | yes | yes | no | — |
| /guidance | Guidance | intelligent | yes | no | yes | yes | no | — |
| /guidance/reminders | Reminders & Prompts | intelligent | yes | no | yes | no | no | — |
| /guidance/education | Health Education | intelligent | yes | no | yes | no | no | — |
| /learning | Impilo Fundo | professional | yes | no | yes | no | no | — |
| /learning/catalog | Impilo Fundo Catalogue | professional | yes | no | yes | no | no | — |
| /learning/courses/[courseId] | Impilo Fundo Course | professional | yes | no | yes | yes | no | — |
| /learning/my-learning | My Learning | professional | yes | no | yes | no | no | — |
| /learning/enrolments/[enrolmentId] | Enrolment Player | professional | yes | no | yes | no | no | — |
| /learning/enrolments/[enrolmentId]/lessons/[lessonId] | Lesson Player | professional | yes | no | yes | no | no | — |
| /learning/pathways | Learning Pathways | professional | yes | no | yes | no | no | — |
| /learning/pathways/[pathwayId] | Pathway Detail | professional | yes | no | yes | no | no | — |
| /learning/record | Learning Record | professional | yes | no | yes | no | no | — |
| /learning/assessments/[assessmentId] | Assessment | professional | yes | no | yes | no | no | — |
| /learning/assessments/[assessmentId]/attempt | Assessment Attempt | professional | yes | no | yes | yes | no | — |
| /learning/attempts/[attemptId] | Attempt Result | professional | yes | no | yes | no | no | — |
| /learning/certificates | Certificates | professional | yes | no | yes | no | no | — |
| /learning/certificates/[certificateId] | Certificate Detail | professional | yes | no | yes | no | no | — |
| /learning/cpd | CPD Evidence | professional | yes | no | yes | no | no | — |
| /learning/reports | Learning Reports | professional | yes | no | yes | no | no | — |
| /learning/reports/cohorts | Cohort Report | professional | yes | no | yes | no | no | — |
| /learning/reports/courses | Course Report | professional | yes | no | yes | no | no | — |
| /learning/reports/overdue | Overdue Learning | professional | yes | no | yes | no | no | — |
| /learning/reports/assessments | Assessment Report | professional | yes | no | yes | no | no | — |
| /learning/studio | Fundo Studio | professional | yes | no | yes | no | no | — |
| /learning/studio/courses | Studio Courses | professional | no | no | yes | no | no | — |
| /learning/studio/courses/new | Studio New Course | professional | yes | no | yes | yes | no | — |
| /learning/studio/courses/[courseId] | Studio Course Detail | professional | yes | no | yes | no | no | — |
| /learning/studio/courses/[courseId]/builder | Studio Course Builder | professional | yes | no | yes | yes | no | — |
| /learning/studio/library | Studio Library | professional | yes | no | yes | yes | no | — |
| /learning/studio/media | Studio Media | professional | yes | no | yes | yes | no | — |
| /learning/studio/media/recordings | Media Recordings | professional | yes | no | yes | no | no | — |
| /learning/studio/media/scripts | Media Scripts | professional | yes | no | yes | yes | no | — |
| /learning/studio/media/voiceovers | Media Voiceovers | professional | yes | no | yes | no | no | — |
| /learning/studio/media/[mediaId] | Media Asset | professional | yes | no | yes | no | yes | — |
| /learning/studio/assessments | Studio Assessments | professional | yes | no | yes | yes | no | — |
| /learning/studio/surveys | Studio Surveys | professional | yes | no | yes | yes | no | — |
| /learning/studio/ai | Studio AI | professional | yes | no | yes | yes | no | — |
| /learning/studio/publish | Studio Publish | professional | yes | no | yes | yes | no | — |
| /learning/studio/analytics | Studio Analytics | professional | yes | no | yes | no | no | — |
| /learning/library | Fundo Library | professional | yes | no | yes | no | no | — |
| /learning/library/resources | Library Resources | professional | yes | no | yes | no | no | — |
| /learning/library/uploads | Library Uploads | professional | yes | no | yes | yes | no | — |
| /learning/library/[resourceId] | Library Resource Detail | professional | yes | no | yes | no | no | — |
| /learning/notifications | Learning Notifications | professional | yes | no | yes | yes | no | — |
| /learning/surveys/[surveyId] | Learning Survey | professional | yes | no | yes | no | no | — |
| /learning/surveys/[surveyId]/respond | Respond to Survey | professional | yes | no | yes | yes | no | — |
| /learning/feedback/course/[courseId] | Course Feedback | professional | yes | no | yes | yes | no | — |
| /learning/admin | Fundo Admin | professional | yes | no | yes | no | no | — |
| /learning/admin/courses | Admin Courses | professional | no | no | yes | no | no | — |
| /learning/admin/courses/new | New Course | professional | yes | no | yes | no | no | — |
| /learning/admin/courses/[courseId]/edit | Edit Course | professional | no | no | yes | no | no | — |
| /learning/admin/pathways | Admin Pathways | professional | yes | no | yes | no | no | — |
| /learning/admin/pathways/new | New Pathway | professional | yes | no | yes | no | no | — |
| /learning/admin/pathways/[pathwayId]/edit | Edit Pathway | professional | yes | no | yes | yes | no | — |
| /learning/admin/assessments | Admin Assessments | professional | yes | no | yes | no | no | — |
| /learning/admin/assessments/new | New Assessment | professional | yes | no | yes | no | yes | — |
| /learning/admin/assessments/[assessmentId]/edit | Edit Assessment | professional | yes | no | yes | yes | yes | — |
| /nhume | Nhume Logistics | operations | yes | no | yes | no | no | — |
| /nhume/dashboard | Nhume Operations Dashboard | operations | yes | no | yes | yes | no | — |
| /nhume/deliveries | Nhume Deliveries | operations | yes | no | yes | yes | no | — |
| /nhume/deliveries/new | New Delivery Request | operations | yes | no | yes | yes | no | — |
| /nhume/deliveries/[deliveryId] | Delivery Detail | operations | yes | no | yes | yes | no | — |
| /nhume/dispatcher | Nhume Dispatcher Console | operations | yes | no | yes | yes | no | — |
| /nhume/map | Fleet Tracking Map | operations | yes | no | yes | yes | no | — |
| /nhume/courier | Courier / Driver Console | operations | yes | no | yes | yes | no | — |
| /nhume/fleet | Fleet & Asset Management | operations | yes | no | yes | yes | no | — |
| /nhume/fleet/[assetId] | Fleet Asset | operations | yes | no | yes | yes | no | — |
| /nhume/couriers | Drivers & Couriers | operations | yes | no | yes | yes | no | — |
| /nhume/couriers/[courierId] | Courier Profile | operations | yes | no | yes | yes | no | — |
| /nhume/policies | Delivery Policies | operations | yes | no | yes | yes | no | — |
| /nhume/autonomous | Autonomous Delivery | operations | yes | no | yes | yes | no | — |
| /nhume/analytics | Nhume Analytics | operations | yes | no | yes | yes | no | — |
| /nhume/custody/[deliveryId] | Chain of Custody | operations | yes | no | yes | yes | no | — |
| /nhume/track/[deliveryId] | Track Delivery | home | yes | no | yes | yes | no | — |
| /madi | Madi Blood Services | operations | yes | no | yes | no | no | — |
| /madi/donor | My Donor Hub | home | yes | no | yes | yes | no | — |
| /madi/donor/register | Become a Donor | home | yes | no | yes | yes | no | — |
| /madi/donor/profile | Donor Profile | home | yes | no | yes | yes | no | — |
| /madi/donor/screening | Donor Screening | home | yes | no | yes | yes | no | — |
| /madi/donor/drives | Donation Drives Near Me | home | yes | no | yes | yes | no | — |
| /madi/donor/history | Donation History | home | yes | no | yes | yes | no | — |
| /madi/donor/feedback | Donor Feedback | home | yes | no | yes | yes | no | — |
| /madi/donor/preferences | Donor Preferences | home | yes | no | yes | yes | no | — |
| /madi/drives | Donation Drives | operations | yes | no | yes | yes | no | — |
| /madi/drives/new | New Donation Drive | operations | yes | no | yes | yes | no | — |
| /madi/drives/[driveId] | Drive Detail | operations | yes | no | yes | yes | no | — |
| /madi/blood-bank | Local Blood Bank | operations | yes | no | yes | yes | no | — |
| /madi/blood-bank/orders | Blood Bank Orders | operations | yes | no | yes | no | no | — |
| /madi/blood-bank/stock | Blood Stock | operations | yes | no | yes | yes | no | — |
| /madi/blood-bank/crossmatch | Crossmatch | operations | yes | no | yes | no | no | — |
| /madi/blood-bank/issue | Issue Blood | operations | yes | no | yes | no | no | — |
| /madi/blood-bank/fridges | Blood Fridge Monitoring | operations | yes | no | yes | yes | no | — |
| /madi/central-bank | Central Blood Bank | operations | yes | no | yes | yes | no | — |
| /madi/orders | Order Blood | queue | yes | no | yes | yes | no | — |
| /madi/orders/[orderId] | Blood Order Detail | queue | yes | no | yes | yes | no | — |
| /madi/transfusion | Record Transfusion | queue | yes | no | yes | yes | no | — |
| /madi/transfusion/[episodeId] | Transfusion Episode | operations | yes | no | yes | yes | no | — |
| /madi/haemovigilance | Haemovigilance | operations | yes | no | yes | yes | no | — |
| /madi/haemovigilance/national | National Haemovigilance | operations | yes | no | yes | yes | no | — |
| /madi/dashboard | Madi Dashboard | operations | yes | no | yes | yes | no | — |
| /madi/processing | Blood Processing | operations | yes | no | yes | yes | no | — |
| /madi/logistics | Blood Logistics | operations | yes | no | yes | yes | no | — |
| /live | Impilo Live | operations | yes | no | yes | yes | no | — |
| /live/manage | Live Event Management | operations | yes | no | yes | yes | no | — |
| /live/admin | Impilo Live Administration | operations | yes | no | yes | yes | no | — |
| /live/create | Create Live Event | operations | yes | no | yes | yes | no | — |
| /live/discover | Discover Live Events | home | yes | no | yes | yes | no | — |
| /live/saved | Saved Live Events | home | yes | no | yes | yes | no | — |
| /live/my-events | My Live Events | home | yes | no | yes | yes | no | — |
| /live/replays | Live Event Replays | home | yes | no | yes | yes | no | — |
| /live/cpd | Live CPD | professional | yes | no | yes | yes | no | — |
| /live/certificates | Live Certificates | professional | yes | no | yes | yes | no | — |
| /live/event/[eventId] | Live Event Detail | operations | yes | no | yes | yes | no | — |
| /live/event/[eventId]/room | Live Room | operations | yes | no | yes | yes | no | — |
| /live/event/[eventId]/replay | Event Replay | home | yes | no | yes | yes | no | — |
| /live/event/[eventId]/analytics | Live Analytics | operations | yes | no | yes | yes | no | — |
| /work/administration-governance | Administration & Governance | operations | yes | no | yes | no | no | — |
| /work/administration-governance/access-requests | Access Requests | operations | yes | no | yes | yes | no | — |
| /work/administration-governance/access-review | Access Review | operations | yes | no | yes | yes | no | — |
| /work/administration-governance/access-review/[subjectId] | Access Review | operations | yes | no | yes | yes | no | — |
| /work/administration-governance/audit | Audit | operations | yes | no | yes | yes | no | — |
| /work/administration-governance/municipal | Municipal | operations | yes | no | yes | yes | no | — |
| /work/administration-governance/onboard | Onboard New Actor | operations | yes | no | yes | no | no | — |
| /work/administration-governance/onboard/citizen | Onboard Citizen | operations | yes | no | yes | yes | no | — |
| /work/administration-governance/onboard/external-partner-user | Onboard External Partner User | operations | yes | no | yes | yes | no | — |
| /work/administration-governance/onboard/hsc-user | Onboard Hsc User | operations | yes | no | yes | yes | no | — |
| /work/administration-governance/onboard/madi-user | Onboard Madi User | operations | yes | no | yes | yes | no | — |
| /work/administration-governance/onboard/marketplace-user | Onboard Marketplace User | operations | yes | no | yes | yes | no | — |

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
| citizen-app | apps/mobile/citizen-app/src/screens/crvs/UbomiCrvsScreen.tsx | 0 | no |
| citizen-app | apps/mobile/citizen-app/src/screens/discover/ProviderDiscoveryScreen.tsx | 0 | no |
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
| provider-app | apps/mobile/provider-app/src/screens/supervisor/EscalationsScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/supervisor/InventoryScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/supervisor/StockScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/supervisor/SupervisorDashboardScreen.tsx | 0 | no |
| provider-app | apps/mobile/provider-app/src/screens/supervisor/TeamOverviewScreen.tsx | 0 | no |
