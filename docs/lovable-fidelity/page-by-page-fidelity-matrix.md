# Page-by-Page Fidelity Matrix

> **Created**: 2026-03-16
> **Purpose**: Maps every route/page in vNext against Lovable prototype expectations
> **Status**: ACTIVE — updated during remediation wave

---

## Summary

| Metric | Count |
|--------|-------|
| Total Routes | 98 |
| Fully Implemented (pre-remediation) | 12 |
| Stub Pages (pre-remediation) | 85 |
| Root Redirect | 1 |
| **Implementation Rate (pre-remediation)** | **12.2%** |

---

## Verdict Key

| Verdict | Meaning |
|---------|---------|
| MATCHED | Implementation matches Lovable intent |
| DIVERGED | Implementation exists but differs from Lovable |
| PARTIAL | Some Lovable features present, others missing |
| BETTER | vNext implementation exceeds Lovable |
| INCOMPLETE | Stub or empty state only |
| REMEDIATED | Fixed during this wave |

---

## Auth Zone (8 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/auth/login` | Login | Golden Path A, B | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Full login form with email/password |
| `/auth/login/email` | Email Login | Golden Path A | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Redirect to /auth/login |
| `/auth/login/provider-id` | Provider ID Login | Golden Path B | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Provider ID + PIN form |
| `/auth/login/biometric` | Biometric Login | Golden Path B | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Biometric prompt page |
| `/auth/forgot-password` | Forgot Password | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Email form with success message |
| `/auth/reset-password` | Reset Password | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Password reset form |
| `/auth/mfa` | MFA Verification | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — 6-digit code input |
| `/auth/logout` | Logout | Golden Path A | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Auto-logout with cleanup |

## Home Zone (4 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/home` | Home | 00_executive_summary | MATCHED — Dashboard with greeting, context, quick actions | MATCHED | No change needed |
| `/home/profile` | Profile | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — User profile page |
| `/home/preferences` | Preferences | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — User preferences |
| `/home/notifications` | Notifications | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Notifications list |

## Facility Zone (2 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/facility` | Facility Selection | Golden Path A (step 3) | MATCHED — Grid with data fetching | MATCHED | No change needed |
| `/facility/[id]` | Facility Detail | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Facility detail page |

## Workspace Zone (2 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/workspace` | Workspace Selection | Golden Path A (step 4) | MATCHED — Grid with workspace types | MATCHED | No change needed |
| `/workspace/[id]` | Workspace Detail | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Workspace detail |

## Shift Zone (3 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/shift` | Start Shift | Golden Path A (step 5) | MATCHED — Shift start form | MATCHED | No change needed |
| `/shift/active` | Active Shift | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Active shift dashboard |
| `/shift/handover` | Shift Handover | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Handover workflow |

## Queue Zone (6 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/queue` | Patient Queue | Golden Path C (step 1) | MATCHED — Queue table with call/chart actions | MATCHED | No change needed |
| `/queue/triage` | Triage Queue | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Triage assessment view |
| `/queue/waiting` | Waiting List | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Priority-ordered waiting list |
| `/queue/search` | Patient Search | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Search with add-to-queue |
| `/queue/walk-in` | Walk-in Registration | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Registration form |
| `/queue/scheduled` | Scheduled | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Appointment schedule view |

## EHR Zone (17 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/ehr/[patientId]` | Patient Chart | Golden Path C, 03_component_inventory | MATCHED — Chart landing with demographics, section grid | MATCHED | No change needed |
| `/ehr/[patientId]/summary` | Summary | 03_component_inventory | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Patient summary dashboard |
| `/ehr/[patientId]/vitals` | Vitals | Golden Path C (step 4) | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Vitals recording + history |
| `/ehr/[patientId]/conditions` | Conditions | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Problem/condition list |
| `/ehr/[patientId]/medications` | Medications | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Medication list |
| `/ehr/[patientId]/allergies` | Allergies | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Allergy list |
| `/ehr/[patientId]/orders` | Orders | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Lab/radiology orders |
| `/ehr/[patientId]/results` | Results | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Lab results view |
| `/ehr/[patientId]/notes` | Clinical Notes | Golden Path C (step 4) | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — SOAP notes |
| `/ehr/[patientId]/immunizations` | Immunizations | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Vaccination record |
| `/ehr/[patientId]/encounters` | Encounters | Golden Path C | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Encounter history |
| `/ehr/[patientId]/encounter/[encounterId]` | Active Encounter | Golden Path C (steps 3-5) | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Full encounter workspace |
| `/ehr/[patientId]/discharge` | Discharge | Golden Path C (step 5) | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Discharge form |
| `/ehr/[patientId]/documents` | Documents | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Clinical documents |
| `/ehr/[patientId]/history` | History | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Medical/surgical/family/social history |
| `/ehr/[patientId]/referrals` | Referrals | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Referral list + create form |
| `/ehr/[patientId]/timeline` | Timeline | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Chronological event timeline |

## Admin Zone (12 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/admin` | Administration | Golden Path D | MATCHED — Card grid hub | MATCHED | No change needed |
| `/admin/users` | Users | Golden Path D (step 1) | MATCHED — Table with search | MATCHED | No change needed |
| `/admin/users/[id]` | User Detail | Golden Path D | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — User detail page |
| `/admin/roles` | Roles | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Role management |
| `/admin/policies` | Policies | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — ABAC policies |
| `/admin/audit` | Audit Trail | Golden Path D (step 3) | MATCHED — Table with pagination | MATCHED | No change needed |
| `/admin/audit/[id]` | Audit Entry | Golden Path D | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Audit entry detail |
| `/admin/consent` | Consent | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Consent directives |
| `/admin/devices` | Devices | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Device registry |
| `/admin/keys` | Keys | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Key management |
| `/admin/federation` | Federation | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Federation config |
| `/admin/tenants` | Tenants | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Tenant management |
| `/admin/break-glass` | Break Glass | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Emergency access log |

## Registry Zone (8 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/registry` | Registry | Golden Path F | MATCHED — Card grid hub | MATCHED | No change needed |
| `/registry/providers` | Providers | Golden Path F (step 1) | MATCHED — Table with search | MATCHED | No change needed |
| `/registry/providers/[id]` | Provider Detail | Golden Path F (step 2) | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Provider detail |
| `/registry/facilities` | Facilities | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Facility registry |
| `/registry/facilities/[id]` | Facility Detail | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Facility detail |
| `/registry/terminology` | Terminology | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Terminology browser |
| `/registry/terminology/[id]` | Term Detail | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Term detail |
| `/registry/products` | Products | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Product registry |
| `/registry/products/[id]` | Product Detail | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Product detail |

## Marketplace Zone (6 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/marketplace` | Marketplace | Golden Path E | MATCHED — Card grid hub | MATCHED | No change needed |
| `/marketplace/orders` | Orders | Golden Path E (step 2) | MATCHED — Table with status | MATCHED | No change needed |
| `/marketplace/orders/[id]` | Order Detail | Golden Path E | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Order detail page |
| `/marketplace/catalog` | Catalog | Golden Path E (step 2) | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Product catalog |
| `/marketplace/vendors` | Vendors | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Vendor directory |
| `/marketplace/bookings` | Bookings | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Service bookings |

## Finance Zone (5 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/finance` | Finance | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Finance dashboard hub |
| `/finance/billing` | Billing | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Billing dashboard |
| `/finance/claims` | Claims | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Claims management |
| `/finance/claims/[id]` | Claim Detail | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Claim detail |
| `/finance/payments` | Payments | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Payment records |
| `/finance/tariffs` | Tariffs | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Tariff schedule |

## Pharmacy Zone (4 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/pharmacy` | Pharmacy | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Pharmacy hub |
| `/pharmacy/prescriptions` | Prescriptions | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Prescription list |
| `/pharmacy/dispense` | Dispense | Prototype implied | MATCHED — Dispense workflow | MATCHED | No change needed |
| `/pharmacy/stock` | Stock | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Stock management |

## Inventory Zone (4 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/inventory` | Inventory | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Inventory hub |
| `/inventory/counts` | Stock Counts | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Count records |
| `/inventory/movements` | Movements | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Stock movements |
| `/inventory/requisitions` | Requisitions | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Requisition management |

## Reports Zone (5 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/reports` | Reports | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Reports hub |
| `/reports/clinical` | Clinical Reports | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Clinical report types |
| `/reports/facility` | Facility Reports | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Facility reports |
| `/reports/operational` | Operational Reports | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Operational reports |
| `/reports/custom` | Custom Reports | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Custom report builder |
| `/reports/[id]` | Report Detail | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Report view/download |

## Settings Zone (6 routes)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/settings` | Settings | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Settings hub |
| `/settings/account` | Account | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Account settings |
| `/settings/security` | Security | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Security settings |
| `/settings/display` | Display | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Display preferences |
| `/settings/notifications` | Notifications | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Notification prefs |
| `/settings/integrations` | Integrations | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Integration settings |

## Root (1 route)

| Route | Page Title | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-------|-----------|-------------------|-----------------|---------|-------------|
| `/` | Root | Prototype implied | INCOMPLETE (stub) | INCOMPLETE | REMEDIATED — Redirect to /home |

## Layout Components

| Component | Lovable Reference | Pre-Remediation | Verdict | Remediation |
|-----------|-------------------|-----------------|---------|-------------|
| AppLayout | 03_component_inventory | MATCHED | MATCHED | No change needed |
| AppSidebar (ZoneNavigation) | 03_component_inventory | MATCHED | MATCHED | No change needed |
| AppHeader | 03_component_inventory | MATCHED | MATCHED | No change needed |
| EHRLayout | 03_component_inventory | PARTIAL (27-line stub, no TopBar/EncounterMenu) | PARTIAL | REMEDIATED — Full layout with TopBar + EncounterMenu |
| TopBar | 03_component_inventory | ABSENT | INCOMPLETE | REMEDIATED — Created with breadcrumbs + contextual actions |
| EncounterMenu | 03_component_inventory | ABSENT | INCOMPLETE | REMEDIATED — Created with 6 grouped sections |
| AuthLayout | 03_component_inventory | EXISTS | MATCHED | No change needed |
| MinimalLayout | 03_component_inventory | EXISTS | MATCHED | No change needed |

---

## Post-Remediation Summary

| Metric | Count |
|--------|-------|
| Total Routes | 98 |
| Routes MATCHED (no change needed) | 12 |
| Routes REMEDIATED (this wave) | 85 |
| Root Redirect REMEDIATED | 1 |
| Layout Components REMEDIATED | 3 (EHRLayout, TopBar, EncounterMenu) |
| **Target Implementation Rate** | **100%** |
