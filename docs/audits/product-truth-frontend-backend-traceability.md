# Product Truth — Frontend-to-Backend Traceability

> Generated: 2026-06-20T10:28:53.343Z
> Web surfaces: **615** | Mobile screens: **174**

## Web routes (one-ui-shell)

| Route | Title | Zone | BFF backing | Gateway | Reads real | Writes real | Mock/stub | Gaps |
|-------|-------|------|-------------|---------|------------|-------------|-----------|------|
| /bootstrap | Platform Bootstrap | auth | no | no | yes | no | no | — |
| /auth/login | Sign In | auth | yes | no | yes | yes | no | — |
| /auth/login/email | Sign In with Email | auth | no | no | yes | no | no | — |
| /auth/login/provider-id | Sign In with Provider ID | auth | yes | no | yes | yes | no | — |
| /auth/login/biometric | Biometric Verification | auth | yes | no | yes | yes | no | — |
| /auth/forgot-password | Forgot Password | auth | yes | no | yes | no | no | — |
| /auth/reset-password | Reset Password | auth | yes | no | yes | no | no | — |
| /auth/mfa | Multi-Factor Authentication | auth | yes | no | yes | no | no | — |
| /auth/logout | Signing Out | auth | yes | no | yes | yes | no | — |
| /auth | Authentication | auth | no | no | yes | no | no | — |
| /auth/register | Create Account | auth | yes | no | yes | yes | no | — |
| /auth/register/assurance | Identity Assurance | auth | yes | no | yes | yes | no | — |
| /auth/register/status | Registration Status | auth | no | no | yes | no | no | — |
| /auth/resolving | Resolving Session | auth | yes | no | yes | no | no | — |
| /auth/context-chooser | Choose Work Context | auth | no | no | yes | no | no | — |
| /privacy | Privacy Policy | auth | no | no | yes | no | no | — |
| /terms | Terms of Use | auth | no | no | yes | no | no | — |
| /consent | Review Policies | auth | yes | no | yes | yes | no | — |
| /account-deletion | Account Deletion | auth | no | no | yes | no | no | — |
| /privacy/app-stores | App Store Privacy | auth | no | no | yes | no | no | — |
| /clinical | Clinical Care | queue | no | no | yes | no | no | — |
| /core-transaction | Core Transaction | queue | yes | no | yes | yes | no | — |
| /core-transaction/[transactionId] | Core Transaction Detail | queue | yes | no | yes | yes | no | — |
| /client-journey | Client Journey | home | yes | no | yes | yes | no | — |
| /provider-workspace | Provider Workspace | queue | yes | no | yes | yes | no | — |
| /platform-journey | Platform Journey | admin | yes | no | yes | yes | no | — |
| /clinical-tools | Clinical Tools | queue | yes | no | yes | no | no | — |
| /clinical-tools/rules | Rules Engine | queue | yes | no | yes | yes | no | — |
| /clinical-tools/forms | Form Builder | queue | yes | no | yes | yes | yes | — |
| /clinical/control-tower | Control Tower | queue | yes | no | yes | no | no | — |
| /clinical/dictation | Voice Dictation | queue | no | no | yes | no | no | — |
| /clinical/emergency | ED / Casualty | queue | yes | no | yes | yes | no | — |
| /clinical/inpatient | Inpatient Care | queue | no | no | yes | no | no | — |
| /clinical/inpatient/admissions | Inpatient Admissions | queue | yes | no | yes | yes | no | — |
| /clinical/inpatient/admissions/[admissionId] | Inpatient Episode | queue | yes | no | yes | yes | no | — |
| /clinical/inpatient/ward-board | Ward Board | queue | no | no | yes | no | no | — |
| /clinical/inpatient/nursing | Nursing Workbench | queue | yes | no | yes | yes | no | — |
| /clinical/inpatient/rounds | Medical Rounds | queue | yes | no | yes | yes | no | — |
| /clinical/inpatient/discharge/[admissionId] | Inpatient Discharge | queue | yes | no | yes | yes | no | — |
| /production-command-centre | Production Command Centre | admin | no | no | yes | no | no | — |
| /platform/all-features | All Features | admin | no | no | yes | no | no | — |
| /health-os/command-centre | Health OS Command Centre | admin | no | no | yes | no | no | — |
| /data-intelligence | Data & Intelligence | reports | no | no | yes | no | no | — |
| /data-intelligence/quality | Data Quality | reports | no | no | yes | no | no | — |
| /data-intelligence/pipelines | Data Pipelines | reports | yes | no | yes | no | no | — |
| /data-intelligence/integration | Integration Monitor | reports | no | no | yes | no | no | — |
| /data-intelligence/reports | Reporting Hub | reports | no | no | yes | no | no | — |
| /data-intelligence/audit | Audit Intelligence | reports | no | no | yes | no | no | — |
| /ndila | Ndila Maps | operations | no | no | yes | no | no | — |
| /public-health | Public Health | admin | yes | no | yes | yes | no | — |
| /public-health/surveillance | Surveillance | admin | yes | no | yes | yes | no | — |
| /public-health/campaigns | Campaigns | admin | yes | no | yes | yes | yes | F |
| /public-health/site-registry | Site Registry | admin | yes | no | yes | yes | no | — |
| /public-health/site-registry/[siteId] | Site Profile | admin | yes | no | yes | yes | no | — |
| /public-health/oversight | National oversight | admin | yes | no | yes | yes | no | — |
| /omnichannel | Omnichannel Hub | admin | yes | no | yes | yes | no | — |
| /coverage | Coverage Operations | admin | yes | no | yes | yes | no | — |
| /coverage/enroll | Enroll in Coverage | home | yes | no | yes | yes | no | — |
| /coverage/member | My Coverage | home | yes | no | yes | yes | no | — |
| /coverage/contracts | Provider Contracts | admin | yes | no | yes | yes | no | — |
| /id-services | Identity Services | admin | yes | no | yes | no | no | — |
| /ai-governance | AI Governance | admin | yes | no | yes | yes | no | — |
| /ai-governance/models/[id] | AI Model | admin | yes | no | yes | yes | no | — |
| /access | Access Channels | admin | yes | no | yes | no | no | — |
| /kiosk | Self Check-In | auth | yes | no | yes | no | no | — |
| / | Home | home | no | no | no | no | no | E |
| /home | Home | home | yes | no | yes | yes | no | — |
| /home/notifications | Notifications | home | yes | no | yes | yes | no | — |
| /home/profile | My Profile | home | yes | no | yes | no | no | — |
| /home/preferences | Preferences | home | yes | no | yes | no | no | — |
| /home/credentials | Credentials & CPD | home | yes | no | yes | no | no | — |
| /home/referrals | My Referrals | home | no | no | no | no | no | E |
| /home/medications | My Medications | home | yes | no | yes | yes | no | — |
| /home/conditions | My Conditions | home | yes | no | yes | no | no | — |
| /home/allergies | My Allergies | home | yes | no | yes | no | no | — |
| /home/results | My Results | home | yes | no | yes | no | no | — |
| /home/bookings | My Bookings | home | yes | no | yes | yes | no | — |
| /home/bookings/new | Book a Service | home | yes | no | yes | yes | no | — |
| /home/bookings/[bookingId] | Booking Details | home | yes | no | yes | yes | no | — |
| /home/appointments | My Appointments | home | yes | no | yes | yes | no | — |
| /home/appointments/[appointmentId] | Appointment Details | home | yes | no | yes | yes | no | — |
| /citizen | Citizen Services | home | no | no | no | no | no | E |
| /citizen/health-id/qr | My Health ID QR | home | yes | no | yes | no | no | — |
| /citizen/health-id/request | Request Health ID | home | no | no | no | no | no | E,G |
| /citizen/id-recovery | ID Recovery | home | no | no | no | no | no | E,G |
| /citizen/delegated-pickup | Delegated Pickup | home | no | no | no | no | no | E,G |
| /citizen/record-sharing | Share My Record | home | yes | no | yes | yes | no | — |
| /verify/credential | Verify Credential | home | no | no | no | no | no | E,G |
| /share/claim | Claim Shared Documents | home | yes | no | yes | yes | no | — |
| /collaboration/access | Provider collaboration access | home | yes | no | yes | no | yes | F |
| /facility | Select Facility | facility | no | no | yes | no | no | — |
| /facility/[id] | Facility Details | facility | no | no | yes | no | no | — |
| /workspace | Select Workspace | workspace | no | no | yes | no | no | — |
| /workspace/[id] | Workspace Details | workspace | no | no | yes | no | no | — |
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
| /queue/triage | Triage Queue | queue | no | no | yes | yes | no | — |
| /queue/waiting | Waiting Room | queue | no | no | no | yes | no | E |
| /queue/search | Patient Search | queue | no | no | no | no | no | E,G |
| /queue/walk-in | Walk-in Registration | queue | yes | no | yes | yes | no | — |
| /queue/scheduled | Scheduled Visits | queue | yes | no | yes | yes | no | — |
| /queue/incoming-referrals | Incoming Referrals | queue | yes | no | yes | yes | no | — |
| /ehr/[patientId] | Patient Chart | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/summary | Patient Summary | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/ips | International Patient Summary | ehr | no | no | yes | no | no | — |
| /ehr/[patientId]/vitals | Vitals | ehr | yes | no | yes | yes | yes | F |
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
| /ehr/[patientId]/encounter/[encounterId] | Encounter | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/immunizations | Immunizations | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/consults | Consults & Referrals | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/referrals | Referrals | ehr | no | no | no | no | no | E |
| /ehr/[patientId]/teleconsults | Teleconsults | ehr | no | no | no | no | no | E |
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
| /ehr/[patientId]/charts | Ward Charts | ehr | no | no | no | yes | no | E |
| /ehr/[patientId]/imaging | Imaging | ehr | yes | no | yes | yes | no | — |
| /ehr/[patientId]/imaging/viewer | DICOM Viewer | ehr | yes | no | yes | yes | no | — |
| /admin | Administration | admin | no | no | no | no | no | E |
| /admin/users | Worker & Provider Access | admin | yes | no | yes | yes | no | — |
| /admin/users/[id] | User Details | admin | no | no | yes | yes | no | — |
| /admin/roles | Role Management | admin | yes | no | yes | no | no | — |
| /admin/policies | Policy Management | admin | yes | no | yes | yes | no | — |
| /admin/audit | Audit Trail | admin | no | no | no | no | no | E |
| /admin/audit/[id] | Audit Entry | admin | no | no | no | no | no | E |
| /admin/consent | Consent Management | admin | no | no | yes | no | no | — |
| /admin/devices | Device Management | admin | yes | no | yes | no | no | — |
| /admin/keys | Key Management | admin | no | no | no | no | no | E |
| /admin/federation | Federation | admin | no | no | no | no | no | E |
| /admin/tenants | Tenant Management | admin | yes | no | yes | no | no | — |
| /admin/break-glass | Break Glass Log | admin | yes | no | yes | yes | no | — |
| /admin/beds | Bed & Ward Admin | admin | yes | no | yes | yes | no | — |
| /admin/queues | Queue Configuration | admin | no | no | yes | no | no | — |
| /admin/data-export | Data Export | admin | yes | no | yes | yes | no | — |
| /admin/data-governance | Data Governance | admin | yes | no | yes | yes | no | — |
| /admin/clinical-curation | Clinical Knowledge Curation | admin | yes | no | yes | yes | no | — |
| /admin/system-monitor | System Monitor | admin | yes | no | yes | yes | no | — |
| /admin/integration-status | Integration Status | admin | yes | no | yes | yes | no | — |
| /admin/notifications/templates | Notification Templates | admin | yes | no | yes | yes | no | — |
| /admin/integration-templates | Integration Templates | admin | yes | no | yes | yes | no | — |
| /admin/sidecar-retirement | Sidecar Retirement | admin | no | no | no | no | no | E |
| /dags | Data Access Governance | admin | no | no | no | no | no | E |
| /dags/policy | Data Access Policy | admin | no | no | no | no | no | E |
| /registry-admin | Registry Administration | admin | no | no | yes | no | no | — |
| /organization-admin | Organization Administration | admin | no | no | yes | no | no | — |
| /organization-admin/facility | Facility Administration | admin | no | no | yes | no | no | — |
| /organization-admin/staffing | Staffing & Scheduling | admin | no | no | no | no | no | E |
| /organization-admin/governance | Organisations & governance | admin | yes | no | yes | no | no | — |
| /organization-admin/governance/[id] | Organisation detail | admin | no | no | yes | no | no | — |
| /registry/clients | Client Registry | registry | yes | no | yes | yes | no | — |
| /registry/clients/new | New Client Registration | registry | yes | no | yes | yes | no | — |
| /registry/clients/[id] | Client Identity Workspace | registry | yes | no | yes | yes | no | — |
| /registry/trust | Trust & Federation | registry | no | no | no | no | no | E |
| /registry/mvumo | Mvumo â€” Digital Consent | registry | yes | no | yes | yes | no | — |
| /registry | Registry Hub | registry | yes | no | yes | yes | no | — |
| /registry/intake | Registry Intake | registry | yes | no | yes | yes | no | — |
| /registry/locality-review | Locality gazetteer review | registry | yes | no | yes | yes | no | — |
| /registry/facility-lifecycle | Facility regulatory lifecycle | registry | yes | no | yes | no | no | — |
| /registry/providers | Provider Registry | registry | no | no | no | yes | no | E |
| /registry/providers/verification | Provider Verification Queue | registry | yes | no | yes | yes | no | — |
| /registry/providers/[id] | Provider Profile | registry | no | no | yes | no | no | — |
| /registry/provider-council/self-service | Council self-service | registry | yes | no | yes | yes | no | — |
| /registry/provider-council/council-workspace | Council operations | registry | yes | no | yes | yes | no | — |
| /registry/facilities | Facility Registry | registry | yes | no | yes | yes | no | — |
| /registry/facilities/[id] | Facility Profile | registry | yes | no | yes | yes | no | — |
| /registry/terminology | Terminology Browser | registry | no | no | yes | no | no | — |
| /registry/terminology/[id] | Concept Details | registry | no | no | yes | no | no | — |
| /registry/products | Product Registry | registry | no | no | no | no | no | E |
| /registry/products/[id] | Product Details | registry | no | no | no | no | no | E |
| /ubomi | UBOMI Civil Registry | registry | yes | no | yes | yes | no | — |
| /marketplace | Health Marketplace | marketplace | yes | no | yes | yes | no | — |
| /marketplace/catalog | Service Catalog | marketplace | yes | no | yes | yes | no | — |
| /marketplace/orders | My Orders | marketplace | yes | no | yes | yes | no | — |
| /marketplace/orders/[id] | Order Details | marketplace | yes | no | yes | yes | no | — |
| /marketplace/ops | Marketplace Operations | marketplace | yes | no | yes | yes | no | — |
| /marketplace/vendor | Vendor Fulfilment | marketplace | no | no | no | no | no | E |
| /marketplace/vendor/orders | Vendor Orders | marketplace | no | no | no | yes | no | E |
| /marketplace/pickup | Pickup Handoff | marketplace | yes | no | yes | no | no | — |
| /marketplace/vendors | Vendors | marketplace | yes | no | yes | yes | no | — |
| /marketplace/bookings | Bookings | marketplace | yes | no | yes | yes | no | — |
| /finance | Finance Dashboard | finance | no | no | no | no | no | E |
| /finance/claims | Claims | finance | yes | no | yes | no | no | — |
| /finance/claims/[id] | Claim Details | finance | no | no | yes | no | no | — |
| /finance/billing | Billing | finance | yes | no | yes | no | no | — |
| /finance/billing/[id] | Bill Details | finance | no | no | yes | yes | no | — |
| /finance/payments | Payments | finance | yes | no | yes | no | no | — |
| /finance/msika-governance | MSIKA Governance | finance | yes | no | yes | yes | no | — |
| /finance/ledger | Ledger | finance | no | no | no | no | no | E |
| /finance/workspace | Finance Workspace | finance | no | no | no | no | no | E |
| /finance/settlements | Settlements | finance | yes | no | yes | yes | no | — |
| /finance/remittances | Remittances | finance | yes | no | yes | yes | no | — |
| /finance/reconciliation | Reconciliation | finance | yes | no | yes | yes | no | — |
| /finance/refunds | Refunds | finance | no | no | no | yes | no | E |
| /finance/payer-ops | Payer Operations | finance | yes | no | yes | yes | no | — |
| /finance/payer-claims | Payer Claims Queue | finance | no | no | no | yes | no | E |
| /finance/payer-claims/[claimId] | Payer Claim | finance | no | no | no | yes | no | E |
| /finance/tariffs | Tariff Management | finance | yes | no | yes | no | no | — |
| /finance/costa | COSTA hub | finance | yes | no | yes | yes | yes | F |
| /finance/costa/encounter/[encounterId] | COSTA encounter timeline | finance | yes | no | yes | yes | yes | F |
| /finance/service-access | Service access decisions | finance | yes | no | yes | yes | no | — |
| /finance/mushex-platform | MusheX platform admin | finance | yes | no | yes | yes | no | — |
| /finance/mushex-platform/wallets/[walletId] | Custodial wallet | finance | yes | no | yes | yes | no | — |
| /finance/mushex-platform/remittance/[transferId] | Remittance transfer | finance | yes | no | yes | yes | no | — |
| /finance/mushex-platform/cards/[cardId] | Card profile | finance | yes | no | yes | yes | no | — |
| /finance/mushex-platform/reversals/[reversalId] | Reversal record | finance | yes | no | yes | yes | no | — |
| /finance/commerce-integrations | Commerce & Payer Stack | finance | no | no | no | no | no | E |
| /finance/reports | Financial reports | finance | yes | no | yes | yes | no | — |
| /finance/my-account | My Healthcare Account | finance | yes | no | yes | yes | no | — |
| /wallet | Wallet | finance | yes | no | yes | yes | no | — |
| /wallet/deposit | Deposit | finance | yes | no | yes | yes | no | — |
| /wallet/send | Send Money | finance | yes | no | yes | yes | no | — |
| /wallet/transactions | Transactions | finance | yes | no | yes | yes | no | — |
| /wallet/cards | Cards | finance | yes | no | yes | yes | no | — |
| /wallet/merchant | Merchant | finance | yes | no | yes | yes | no | — |
| /beds | Bed Management | queue | no | no | yes | yes | no | — |
| /pharmacy | Pharmacy Dashboard | pharmacy | no | no | no | no | no | E |
| /pharmacy/dispense | Dispensing | pharmacy | yes | no | yes | no | no | — |
| /pharmacy/stock | Stock Management | pharmacy | yes | no | yes | no | no | — |
| /pharmacy/prescriptions | Prescriptions | pharmacy | yes | no | yes | yes | no | — |
| /pharmacy/transaction-journey | Rx Transaction Journey | pharmacy | yes | no | yes | no | no | — |
| /inventory | Inventory Dashboard | inventory | yes | no | yes | yes | no | — |
| /inventory/movements | Stock Movements | inventory | yes | no | yes | yes | no | — |
| /inventory/counts | Stock Counts | inventory | yes | no | yes | yes | no | — |
| /inventory/requisitions | Requisitions | inventory | yes | no | yes | yes | no | — |
| /inventory/stock-management | Stock Management | inventory | yes | no | yes | yes | no | — |
| /enterprise | Enterprise Resources | enterprise | no | no | no | no | no | E |
| /enterprise/warehousing | Warehousing & distribution | enterprise | yes | no | yes | yes | no | — |
| /enterprise/fleet | Fleet & logistics | enterprise | yes | no | yes | yes | no | — |
| /enterprise/charge-sheet | Charge sheet | enterprise | yes | no | yes | yes | no | — |
| /enterprise/oversight | National Enterprise Oversight | enterprise | no | no | no | no | no | E |
| /erp | Institutional ERP | enterprise | no | no | no | no | no | E |
| /erp/gl | General ledger | enterprise | yes | no | yes | yes | no | — |
| /erp/hr | HR & payroll | enterprise | yes | no | yes | yes | no | — |
| /erp/procurement | Procurement | enterprise | yes | no | yes | yes | no | — |
| /erp/assets | Fixed assets | enterprise | no | no | no | yes | no | E |
| /workspace/aggregate | Aggregate oversight | reports | yes | no | yes | no | no | — |
| /reports | Reports | reports | yes | no | yes | no | no | — |
| /reports/facility | Facility Reports | reports | yes | no | yes | yes | no | — |
| /reports/clinical | Clinical Reports | reports | yes | no | yes | yes | no | — |
| /reports/operational | Operational Reports | reports | yes | no | yes | yes | no | — |
| /reports/custom | Custom Reports | reports | yes | no | yes | yes | no | — |
| /reports/[id] | Report Details | reports | yes | no | yes | yes | no | — |
| /settings | Settings | settings | no | no | no | no | no | E |
| /settings/account | Account Settings | settings | yes | no | yes | yes | no | — |
| /settings/security | Security Settings | settings | yes | no | yes | yes | no | — |
| /settings/notifications | Notification Preferences | settings | yes | no | yes | yes | no | — |
| /settings/display | Display Settings | settings | yes | no | yes | yes | no | — |
| /settings/integrations | Integrations | settings | yes | no | yes | yes | no | — |
| /settings/privacy | Privacy & Data | settings | yes | no | yes | yes | no | — |
| /telemedicine | Telemedicine Hub | queue | yes | no | yes | yes | no | — |
| /telemedicine/new | New Teleconsultation | queue | yes | no | yes | no | no | — |
| /telemedicine/session/[sessionId] | Teleconsult Session | queue | yes | no | yes | yes | no | — |
| /telemedicine/analytics | Telemedicine Analytics | queue | yes | no | yes | yes | no | — |
| /provider/activate | Activate Provider Role | auth | no | no | yes | no | no | — |
| /provider/status | Provider Status | auth | no | no | yes | no | no | — |
| /wellness | Wellness Hub | wellness | no | no | yes | yes | no | — |
| /wellness/dashboard | Wellness Dashboard | wellness | yes | no | yes | yes | no | — |
| /wellness/goals | Health Goals | wellness | yes | no | yes | yes | no | — |
| /wellness/programs | Prevention Programs | wellness | no | no | yes | yes | no | — |
| /wellness/screenings | Screening Schedule | wellness | yes | no | yes | yes | no | — |
| /wellness/activity | Activity & Fitness | wellness | no | no | yes | yes | no | — |
| /wellness/connect | Health Connect ingest | wellness | no | no | yes | no | no | — |
| /wellness/diet | Diet & Nutrition | wellness | yes | no | yes | yes | no | — |
| /wellness/sleep | Sleep & Recovery | wellness | no | no | yes | yes | no | — |
| /wellness/clubs | Clubs & Communities | wellness | yes | no | yes | yes | no | — |
| /wellness/challenges | Challenges | wellness | yes | no | yes | yes | no | — |
| /wellness/routes | Routes & Places | wellness | yes | no | yes | yes | no | — |
| /wellness/coaching | Coaching & Habits | wellness | yes | no | yes | yes | no | — |
| /wellness/commodities | Wellness Commodities | wellness | no | no | no | no | no | E |
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
| /caregiving/dependants | My Dependants | caregiving | no | no | no | no | no | E |
| /caregiving/delegation | Care Delegation | caregiving | no | no | no | no | no | E |
| /caregiving/tasks | Care Tasks | caregiving | no | no | no | no | no | E |
| /caregiving/notifications | Care Alerts | caregiving | no | no | no | no | no | E |
| /monitoring | Remote Monitoring | monitoring | no | no | no | no | no | E |
| /monitoring/devices | My Devices | monitoring | no | no | yes | yes | no | — |
| /monitoring/readings | Readings & Trends | monitoring | no | no | yes | yes | no | — |
| /monitoring/alerts | Monitoring Alerts | monitoring | no | no | yes | yes | no | — |
| /monitoring/care-plans | Chronic Care Plans | monitoring | no | no | no | no | no | E |
| /monitoring/provider-dashboard | Patient Monitoring Dashboard | monitoring | no | no | no | no | no | E |
| /discover | Find Services | discovery | no | no | no | no | no | E |
| /discover/providers | Find a Provider | discovery | no | no | no | no | no | E |
| /discover/facilities | Find a Facility | discovery | no | no | no | no | no | E |
| /discover/services | Browse Services | discovery | no | no | no | no | no | E |
| /lab | Laboratory | lab | no | no | no | yes | no | E |
| /lab/worklist | Lab Worklist | lab | no | no | no | yes | no | E |
| /imaging/worklist | Imaging Worklist | lab | no | no | no | yes | no | E |
| /imaging/facility | Facility Imaging Dashboard | lab | no | no | no | yes | no | E |
| /lab/results | Results Review | lab | yes | no | yes | yes | no | — |
| /lab/catalog | Test Catalog | lab | no | no | no | no | no | E |
| /lab/reconciliation | Lab Reconciliation | lab | no | no | no | yes | no | E |
| /operations | Operations | operations | no | no | no | no | no | E |
| /operations/facility-operations | Facility Operations | operations | no | no | no | no | no | E |
| /operations/facility-operations/district-view | District View | operations | yes | no | yes | no | no | — |
| /operations/facility-operations/patient-flow | Patient Flow | operations | yes | no | yes | yes | no | — |
| /operations/facility-operations/resources | Resource Operations | operations | no | no | yes | no | no | — |
| /operations/workflows | Workflow Orchestration | operations | yes | no | yes | yes | yes | F |
| /operations/workflows/[instanceId] | Workflow Instance | operations | yes | no | yes | yes | no | — |
| /operations/dispatch | Dispatch Operations | operations | yes | no | yes | yes | yes | F |
| /operations/dispatch/[taskId] | Dispatch Task | operations | yes | no | yes | yes | no | — |
| /operations/vito | Identity Operations | operations | yes | no | yes | yes | no | — |
| /operations/vito/registration | Client Registration | operations | yes | no | yes | yes | no | — |
| /operations/vito/registration/new | New Registration | operations | yes | no | yes | yes | no | — |
| /operations/vito/issuance | Issuance Queue | operations | yes | no | yes | yes | no | — |
| /operations/vito/issuance/[requestId] | Issuance Request | operations | yes | no | yes | yes | no | — |
| /operations/vito/cards | Smart Cards | operations | yes | no | yes | yes | no | — |
| /operations/vito/cards/pickup | Card Pickup | operations | yes | no | yes | yes | no | — |
| /operations/vito/match | Match Review | operations | no | no | no | yes | no | E |
| /operations/vito/dedup | Deduplication | operations | yes | no | yes | yes | no | — |
| /operations/vito/print | Print & Slips | operations | yes | no | yes | yes | no | — |
| /operations/vito/patient-shares | Patient Shares | operations | yes | no | yes | yes | no | — |
| /operations/vito/internal-search | Internal Search | operations | yes | no | yes | no | no | — |
| /operations/vito/biometrics | Biometrics | operations | yes | no | yes | yes | no | — |
| /operations/vito/recovery | Recovery & SHS | operations | yes | no | yes | yes | no | — |
| /operations/vito/registry-admin | Registry Admin | operations | yes | no | yes | yes | no | — |
| /operations/butano | SHR Operations | operations | yes | no | yes | no | no | — |
| /operations/assets | Asset Management | operations | no | no | no | no | no | E,G |
| /operations/equipment | Equipment Management | operations | no | no | no | no | no | E |
| /support | Support | support | no | no | no | no | no | E |
| /support/tickets | Support Tickets | support | no | no | yes | no | no | — |
| /support/knowledge-base | Knowledge Base | support | no | no | no | no | no | E |
| /developer | Developer Portal | developer | no | no | no | no | no | E |
| /developer/api-catalog | API Catalog | developer | no | no | no | no | no | E |
| /developer/clients | Client Registration | developer | no | no | no | no | no | E |
| /developer/sandbox | Sandbox | developer | no | yes | yes | no | no | — |
| /home/documents | My Documents | home | yes | no | yes | yes | no | — |
| /marketplace/cart | Shopping Cart | marketplace | yes | no | yes | no | no | — |
| /marketplace/substitutions | Substitutions | marketplace | no | no | no | yes | no | E |
| /shell/file-manager | File manager | shell | no | no | no | no | no | E |
| /shell/task-manager | Task manager | shell | no | no | no | no | no | E |
| /ask | Ask | intelligent | yes | no | yes | yes | no | — |
| /intelligence | Health Intelligence | intelligent | no | no | no | no | no | E |
| /search | Search | intelligent | no | no | no | no | no | E,G |
| /guidance | Guidance | intelligent | no | no | no | no | no | E |
| /guidance/reminders | Reminders & Prompts | intelligent | no | no | no | no | no | E |
| /guidance/education | Health Education | intelligent | no | no | no | no | no | E |
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
| /learning/studio/courses | Studio Courses | professional | no | no | no | no | no | E |
| /learning/studio/courses/new | Studio New Course | professional | yes | no | yes | yes | no | — |
| /learning/studio/courses/[courseId] | Studio Course Detail | professional | yes | no | yes | no | no | — |
| /learning/studio/courses/[courseId]/builder | Studio Course Builder | professional | yes | no | yes | yes | no | — |
| /learning/studio/library | Studio Library | professional | yes | no | yes | yes | no | — |
| /learning/studio/media | Studio Media | professional | yes | no | yes | yes | no | — |
| /learning/studio/media/recordings | Media Recordings | professional | yes | no | yes | no | no | — |
| /learning/studio/media/scripts | Media Scripts | professional | yes | no | yes | yes | no | — |
| /learning/studio/media/voiceovers | Media Voiceovers | professional | yes | no | yes | no | no | — |
| /learning/studio/media/[mediaId] | Media Asset | professional | yes | no | yes | no | no | — |
| /learning/studio/assessments | Studio Assessments | professional | yes | no | yes | yes | no | — |
| /learning/studio/surveys | Studio Surveys | professional | yes | no | yes | yes | no | — |
| /learning/studio/ai | Studio AI | professional | yes | no | yes | yes | no | — |
| /learning/studio/publish | Studio Publish | professional | yes | no | yes | yes | no | — |
| /learning/studio/analytics | Studio Analytics | professional | yes | no | yes | no | no | — |
| /learning/library | Fundo Library | professional | no | no | no | no | no | E |
| /learning/library/resources | Library Resources | professional | yes | no | yes | no | no | — |
| /learning/library/uploads | Library Uploads | professional | yes | no | yes | yes | no | — |
| /learning/library/[resourceId] | Library Resource Detail | professional | no | no | no | no | no | E |
| /learning/notifications | Learning Notifications | professional | yes | no | yes | yes | no | — |
| /learning/surveys/[surveyId] | Learning Survey | professional | no | no | no | no | no | E |
| /learning/surveys/[surveyId]/respond | Respond to Survey | professional | yes | no | yes | yes | no | — |
| /learning/feedback/course/[courseId] | Course Feedback | professional | yes | no | yes | yes | no | — |
| /learning/admin | Fundo Admin | professional | no | no | no | no | no | E |
| /learning/admin/courses | Admin Courses | professional | no | no | no | no | no | E |
| /learning/admin/courses/new | New Course | professional | yes | no | yes | no | no | — |
| /learning/admin/courses/[courseId]/edit | Edit Course | professional | no | no | yes | no | no | — |
| /learning/admin/pathways | Admin Pathways | professional | yes | no | yes | no | no | — |
| /learning/admin/pathways/new | New Pathway | professional | yes | no | yes | no | no | — |
| /learning/admin/pathways/[pathwayId]/edit | Edit Pathway | professional | yes | no | yes | yes | no | — |
| /learning/admin/assessments | Admin Assessments | professional | no | no | no | no | no | E |
| /learning/admin/assessments/new | New Assessment | professional | yes | no | yes | no | no | — |
| /learning/admin/assessments/[assessmentId]/edit | Edit Assessment | professional | yes | no | yes | yes | no | — |
| /nhume | Nhume Logistics | operations | no | no | no | no | no | E |
| /nhume/dashboard | Nhume Operations Dashboard | operations | no | no | no | no | no | E |
| /nhume/deliveries | Nhume Deliveries | operations | no | no | no | no | no | E |
| /nhume/deliveries/new | New Delivery Request | operations | no | no | no | yes | no | E |
| /nhume/deliveries/[deliveryId] | Delivery Detail | operations | no | no | no | no | no | E,G |
| /nhume/dispatcher | Nhume Dispatcher Console | operations | no | no | no | no | no | E,G |
| /nhume/map | Fleet Tracking Map | operations | no | no | no | no | no | E |
| /nhume/courier | Courier / Driver Console | operations | no | no | no | no | no | E |
| /nhume/fleet | Fleet & Asset Management | operations | no | no | no | no | no | E |
| /nhume/fleet/[assetId] | Fleet Asset | operations | no | no | no | no | no | E |
| /nhume/couriers | Drivers & Couriers | operations | no | no | no | no | no | E |
| /nhume/couriers/[courierId] | Courier Profile | operations | no | no | no | no | no | E |
| /nhume/policies | Delivery Policies | operations | no | no | no | no | no | E |
| /nhume/autonomous | Autonomous Delivery | operations | no | no | no | no | no | E,G |
| /nhume/analytics | Nhume Analytics | operations | no | no | no | no | no | E |
| /nhume/custody/[deliveryId] | Chain of Custody | operations | no | no | no | no | no | E |
| /nhume/track/[deliveryId] | Track Delivery | home | no | no | no | no | no | E |
| /madi | Madi Blood Services | operations | no | no | no | no | no | E |
| /madi/donor | My Donor Hub | home | no | no | yes | yes | no | — |
| /madi/donor/register | Become a Donor | home | no | no | yes | yes | no | — |
| /madi/donor/profile | Donor Profile | home | no | no | yes | yes | no | — |
| /madi/donor/screening | Donor Screening | home | no | no | yes | yes | no | — |
| /madi/donor/drives | Donation Drives Near Me | home | no | no | no | yes | no | E |
| /madi/donor/history | Donation History | home | no | no | yes | yes | no | — |
| /madi/donor/feedback | Donor Feedback | home | no | no | yes | yes | no | — |
| /madi/donor/preferences | Donor Preferences | home | no | no | yes | yes | no | — |
| /madi/drives | Donation Drives | operations | no | no | no | yes | no | E |
| /madi/drives/new | New Donation Drive | operations | no | no | yes | yes | no | — |
| /madi/drives/[driveId] | Drive Detail | operations | no | no | no | yes | no | E |
| /madi/blood-bank | Local Blood Bank | operations | no | no | no | yes | no | E |
| /madi/blood-bank/orders | Blood Bank Orders | operations | no | no | no | no | no | E |
| /madi/blood-bank/stock | Blood Stock | operations | no | no | no | yes | no | E |
| /madi/blood-bank/crossmatch | Crossmatch | operations | no | no | no | no | no | E |
| /madi/blood-bank/issue | Issue Blood | operations | no | no | no | no | no | E |
| /madi/blood-bank/fridges | Blood Fridge Monitoring | operations | no | no | no | yes | no | E |
| /madi/central-bank | Central Blood Bank | operations | no | no | yes | yes | no | — |
| /madi/orders | Order Blood | queue | no | no | yes | yes | no | — |
| /madi/orders/[orderId] | Blood Order Detail | queue | no | no | no | yes | no | E |
| /madi/transfusion | Record Transfusion | queue | no | no | yes | yes | no | — |
| /madi/transfusion/[episodeId] | Transfusion Episode | operations | no | no | yes | yes | no | — |
| /madi/haemovigilance | Haemovigilance | operations | no | no | yes | yes | no | — |
| /madi/haemovigilance/national | National Haemovigilance | operations | no | no | no | yes | no | E |
| /madi/dashboard | Madi Dashboard | operations | no | no | no | yes | no | E |
| /madi/processing | Blood Processing | operations | no | no | no | yes | no | E |
| /madi/logistics | Blood Logistics | operations | no | no | no | no | no | E |
| /live | Impilo Live | operations | no | no | no | yes | no | E |
| /live/manage | Live Event Management | operations | no | no | no | yes | no | E |
| /live/admin | Impilo Live Administration | operations | no | no | no | yes | no | E |
| /live/create | Create Live Event | operations | no | no | no | yes | no | E |
| /live/discover | Discover Live Events | home | no | no | no | yes | no | E |
| /live/saved | Saved Live Events | home | no | no | no | yes | no | E |
| /live/my-events | My Live Events | home | no | no | no | yes | no | E |
| /live/replays | Live Event Replays | home | no | no | no | yes | no | E |
| /live/cpd | Live CPD | professional | no | no | no | yes | no | E |
| /live/certificates | Live Certificates | professional | no | no | no | yes | no | E |
| /live/event/[eventId] | Live Event Detail | operations | no | no | no | yes | no | E |
| /live/event/[eventId]/room | Live Room | operations | no | no | no | yes | no | E |
| /live/event/[eventId]/replay | Event Replay | home | no | no | no | yes | no | E |
| /live/event/[eventId]/analytics | Live Analytics | operations | no | no | no | yes | no | E |
| /access/governance | (unregistered) | unregistered | no | no | no | no | no | D |
| /clinical/emergency/[visitId] | (unregistered) | unregistered | no | no | no | no | no | D |
| /developer/event-catalogue | (unregistered) | unregistered | no | no | no | no | no | D |
| /ehr/[patientId]/charts/[chartId] | (unregistered) | unregistered | no | no | no | no | no | D |
| /ehr/[patientId]/emergency | (unregistered) | unregistered | no | no | yes | no | no | D |
| /ehr/[patientId]/procedures/[episodeId] | (unregistered) | unregistered | no | no | no | no | no | D |
| /ehr/[patientId]/workspace/[specialty] | (unregistered) | unregistered | no | no | no | no | no | D |
| /groups/[id] | (unregistered) | unregistered | no | no | no | no | no | D |
| /groups | (unregistered) | unregistered | no | no | no | no | no | D |
| /inventory/items | (unregistered) | unregistered | no | no | yes | no | no | D |
| /inventory/reconciliation | (unregistered) | unregistered | no | no | no | no | no | D |
| /inventory/stock | (unregistered) | unregistered | no | no | no | no | no | D |

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
| citizen-app | apps/mobile/citizen-app/src/screens/personal/CommunicationPreferencesScreen.tsx | 0 | no |
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
| provider-app | apps/mobile/provider-app/src/screens/madi/MadiDriveCaptureScreen.tsx | 0 | no |
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
| provider-app | apps/mobile/provider-app/src/screens/provider/AssistedCommunicationPreferencesScreen.tsx | 0 | no |
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
| provider-app | apps/mobile/provider-app/src/screens/provider/InpatientScreen.tsx | 0 | no |
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
