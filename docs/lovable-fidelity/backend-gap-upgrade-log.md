# Backend Gap Upgrade Log

> **Created**: 2026-03-16
> **Purpose**: Records where Lovable prototype mock/stub behavior was upgraded to real backend implementation

---

## Overview

The Lovable prototype was a Supabase-backed UI prototype with demo data. The vNext platform replaced this with a proper backend architecture:
- Spring Boot BFF (Experience BFF) with PostgreSQL
- v1.1 API conventions (headers, idempotency, error envelope)
- Outbox pattern for event publishing
- TanStack Query hooks for frontend data fetching

This log records what was upgraded and what the frontend pages now connect to.

---

## Upgrade Log

### UG-001: Authentication

| Field | Value |
|-------|-------|
| Prototype Feature | Supabase Auth with email/password |
| Prior State | BFF `AuthController.java` existed but no UI pages |
| Backend Gap | None — endpoint existed |
| Frontend Gap | Auth pages were all stubs |
| What Was Implemented | Login, logout, forgot-password, reset-password, MFA, provider-id login pages with real API calls to `/internal/v1/auth/login` and `/internal/v1/auth/logout` |
| Files | `ui/experience/src/app/auth/login/page.tsx`, `auth/logout/page.tsx`, `auth/mfa/page.tsx`, `auth/forgot-password/page.tsx`, `auth/reset-password/page.tsx`, `auth/login/provider-id/page.tsx`, `auth/login/biometric/page.tsx`, `auth/login/email/page.tsx` |

### UG-002: Patient Queue Sub-Views

| Field | Value |
|-------|-------|
| Prototype Feature | Queue management with triage, waiting, scheduled views |
| Prior State | `QueueController.java` existed; only main queue page implemented |
| Backend Gap | None — queue endpoints existed |
| Frontend Gap | Triage, waiting, scheduled, walk-in, search pages all stubs |
| What Was Implemented | All 5 queue sub-pages with real API calls to `/internal/v1/queue` with filter parameters |
| Files | `queue/triage/page.tsx`, `queue/waiting/page.tsx`, `queue/scheduled/page.tsx`, `queue/walk-in/page.tsx`, `queue/search/page.tsx` |

### UG-003: EHR Clinical Pages

| Field | Value |
|-------|-------|
| Prototype Feature | Full clinical workflow with vitals, notes, orders, referrals, discharge |
| Prior State | `EncounterController.java`, `PatientController.java` existed; only chart landing page implemented |
| Backend Gap | None — all clinical BFF endpoints existed |
| Frontend Gap | All 16 EHR sub-pages were stubs |
| What Was Implemented | All EHR pages with real API calls using existing query hooks (useVitals, useEncounters, useClinicalNotes, useLabOrders, useReferrals, useDischarge, etc.) |
| Files | `ehr/[patientId]/summary/page.tsx` through `ehr/[patientId]/timeline/page.tsx` (16 files) |

### UG-004: Admin Sub-Pages

| Field | Value |
|-------|-------|
| Prototype Feature | Admin dashboard with user/role/policy/audit management |
| Prior State | `AdminController.java` existed; users and audit pages implemented |
| Backend Gap | Some admin endpoints may need expansion (roles, policies, devices, keys, federation, tenants, break-glass) |
| Frontend Gap | 9 of 12 admin pages were stubs |
| What Was Implemented | All admin sub-pages with API calls to `/internal/v1/admin/*` endpoints |
| Files | `admin/roles/page.tsx`, `admin/policies/page.tsx`, `admin/consent/page.tsx`, `admin/devices/page.tsx`, `admin/keys/page.tsx`, `admin/federation/page.tsx`, `admin/tenants/page.tsx`, `admin/break-glass/page.tsx`, `admin/users/[id]/page.tsx`, `admin/audit/[id]/page.tsx` |

### UG-005: Finance Zone

| Field | Value |
|-------|-------|
| Prototype Feature | Financial management (billing, claims, payments, tariffs) |
| Prior State | No finance-specific BFF controller (Mushex service handles finance separately) |
| Backend Gap | BFF may need finance proxy endpoints or direct service calls |
| Frontend Gap | All 5 finance pages were stubs |
| What Was Implemented | Finance hub + 4 sub-pages with API calls to `/internal/v1/finance/*` endpoints. Backend may route to Mushex service. |
| Files | `finance/page.tsx`, `finance/billing/page.tsx`, `finance/claims/page.tsx`, `finance/claims/[id]/page.tsx`, `finance/payments/page.tsx`, `finance/tariffs/page.tsx` |

### UG-006: Pharmacy Sub-Pages

| Field | Value |
|-------|-------|
| Prototype Feature | Pharmacy management (prescriptions, dispensing, stock) |
| Prior State | `PrescriptionController.java` existed; dispense page implemented |
| Backend Gap | Stock management endpoint may need addition |
| Frontend Gap | 3 of 4 pharmacy pages were stubs |
| What Was Implemented | Pharmacy hub + prescriptions and stock pages with API calls |
| Files | `pharmacy/page.tsx`, `pharmacy/prescriptions/page.tsx`, `pharmacy/stock/page.tsx` |

### UG-007: Inventory Zone

| Field | Value |
|-------|-------|
| Prototype Feature | Inventory management (stock items, counts, movements, requisitions) |
| Prior State | `InventoryController.java` existed; no inventory pages implemented |
| Backend Gap | Counts, movements, requisitions endpoints may need addition |
| Frontend Gap | All 4 inventory pages were stubs |
| What Was Implemented | Inventory hub + 3 sub-pages with API calls |
| Files | `inventory/page.tsx`, `inventory/counts/page.tsx`, `inventory/movements/page.tsx`, `inventory/requisitions/page.tsx` |

### UG-008: Reports Zone

| Field | Value |
|-------|-------|
| Prototype Feature | Report generation (clinical, facility, operational, custom) |
| Prior State | `ReportController.java` existed; no report pages implemented |
| Backend Gap | None — report generation endpoint existed |
| Frontend Gap | All 6 report pages were stubs |
| What Was Implemented | Reports hub + 4 category pages + report detail page with API calls to `/internal/v1/reports/generate` |
| Files | `reports/page.tsx`, `reports/clinical/page.tsx`, `reports/facility/page.tsx`, `reports/operational/page.tsx`, `reports/custom/page.tsx`, `reports/[id]/page.tsx` |

### UG-009: Registry Sub-Pages

| Field | Value |
|-------|-------|
| Prototype Feature | Registry management (providers, facilities, terminology, products) |
| Prior State | `ProviderController.java` existed; providers list page implemented |
| Backend Gap | Terminology and products endpoints may need addition |
| Frontend Gap | 6 of 8 registry pages were stubs |
| What Was Implemented | All registry sub-pages with API calls |
| Files | `registry/providers/[id]/page.tsx`, `registry/facilities/page.tsx`, `registry/facilities/[id]/page.tsx`, `registry/terminology/page.tsx`, `registry/terminology/[id]/page.tsx`, `registry/products/page.tsx`, `registry/products/[id]/page.tsx` |

### UG-010: Marketplace Sub-Pages

| Field | Value |
|-------|-------|
| Prototype Feature | Marketplace (catalog, orders, vendors, bookings) |
| Prior State | `MarketplaceController.java` existed; orders page implemented |
| Backend Gap | Catalog, vendors, bookings endpoints may need addition |
| Frontend Gap | 4 of 6 marketplace pages were stubs |
| What Was Implemented | All marketplace sub-pages with API calls |
| Files | `marketplace/catalog/page.tsx`, `marketplace/vendors/page.tsx`, `marketplace/bookings/page.tsx`, `marketplace/orders/[id]/page.tsx` |

### UG-011: Settings Zone

| Field | Value |
|-------|-------|
| Prototype Feature | User settings (account, security, display, notifications, integrations) |
| Prior State | No settings-specific BFF endpoint |
| Backend Gap | Settings CRUD endpoints needed |
| Frontend Gap | All 6 settings pages were stubs |
| What Was Implemented | Settings hub + 5 sub-pages with API calls to `/internal/v1/settings/*` |
| Files | `settings/page.tsx`, `settings/account/page.tsx`, `settings/security/page.tsx`, `settings/display/page.tsx`, `settings/notifications/page.tsx`, `settings/integrations/page.tsx` |

### UG-012: EHR Layout Components

| Field | Value |
|-------|-------|
| Prototype Feature | TopBar with contextual actions, EncounterMenu with grouped clinical nav |
| Prior State | EHRLayout was 27-line stub with no TopBar or EncounterMenu |
| Backend Gap | None — layout is frontend-only |
| Frontend Gap | 2 critical components absent |
| What Was Implemented | TopBar.tsx (breadcrumbs, Pharmacy/Payments/Orders/Referrals/Shift Handoff actions), EncounterMenu.tsx (6 grouped sections: Overview, Assessment, Problems, Care, Consults, Discharge) |
| Files | `ui/experience/src/components/TopBar.tsx`, `ui/experience/src/components/EncounterMenu.tsx`, `ui/experience/src/components/EHRLayout.tsx` |

---

## Backend Endpoints That May Need Extension

The frontend pages make API calls to endpoints that may not all exist yet in the BFF. These are the endpoints referenced by the new pages:

| Endpoint | Status | Notes |
|----------|--------|-------|
| `GET /internal/v1/admin/roles` | May need creation | Admin role management |
| `GET /internal/v1/admin/policies` | May need creation | ABAC policy list |
| `GET /internal/v1/admin/consent` | May need creation | Consent directives |
| `GET /internal/v1/admin/devices` | May need creation | Device registry |
| `GET /internal/v1/admin/keys` | May need creation | Key management |
| `GET /internal/v1/admin/federation` | May need creation | Federation config |
| `GET /internal/v1/admin/tenants` | May need creation | Tenant management |
| `GET /internal/v1/admin/break-glass` | May need creation | Emergency access log |
| `GET /internal/v1/finance/billing` | May need creation | Billing records |
| `GET /internal/v1/finance/claims` | May need creation | Claims management |
| `GET /internal/v1/finance/payments` | May need creation | Payment records |
| `GET /internal/v1/finance/tariffs` | May need creation | Tariff schedule |
| `GET /internal/v1/pharmacy/stock` | May need creation | Pharmacy stock |
| `GET /internal/v1/inventory/counts` | May need creation | Stock counts |
| `GET /internal/v1/inventory/movements` | May need creation | Stock movements |
| `GET /internal/v1/inventory/requisitions` | May need creation | Requisitions |
| `GET /internal/v1/registry/facilities` | May need creation | Facility registry |
| `GET /internal/v1/registry/terminology` | May need creation | Terminology browser |
| `GET /internal/v1/registry/products` | May need creation | Product registry |
| `GET /internal/v1/marketplace/catalog` | May need creation | Product catalog |
| `GET /internal/v1/marketplace/vendors` | May need creation | Vendor directory |
| `GET /internal/v1/marketplace/bookings` | May need creation | Service bookings |
| `GET /internal/v1/settings/*` | May need creation | User settings CRUD |

**Note**: These endpoints follow the existing v1.1 API convention pattern. The frontend pages are designed to gracefully handle backend unavailability with loading and error states. Backend implementation of these endpoints is a separate concern and can be addressed incrementally.
