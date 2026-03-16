# Fidelity Divergence Analysis

> **Created**: 2026-03-16
> **Purpose**: Detailed analysis of where and why vNext diverged from Lovable prototype

---

## Executive Summary

The Impilo vNext Experience Platform diverged from the Lovable prototype in **three primary dimensions**:

1. **Implementation Depth**: 85 of 97 page files (87.6%) were stubs — containing only empty state placeholders with no real UI, data fetching, or functionality
2. **Layout Component Gaps**: 2 of 6 required layout components (TopBar, EncounterMenu) were never implemented
3. **Telemedicine Absence**: Zero web-based telemedicine functionality despite mobile apps having full implementations

---

## Root Cause Analysis

### Why Did Opus Diverge?

The divergence is traceable to a single root cause: **the original Lovable prototype detailed specifications were never committed to the repository**.

Evidence:
- `docs/prototype/final/02_page_by_page_spec.md` (Conflict #1): "described 'complete UI inventory' but contained no page specs"
- `docs/prototype/final/03_component_inventory.md` (Conflict #6): "stated 'each component entry defines its props interface' but provided no actual specs"
- `docs/prototype/final/01_site_map.md` (Conflict #1): "said '98 routes' but listed none"

Without detailed page-level specs, the implementation:
1. Created the **route structure** correctly (98 routes across 15 zones)
2. Created the **route registry** correctly (routes.ts with all entries)
3. Created the **layout architecture** correctly (4 variants, 11 sidebar contexts)
4. Implemented **only the golden path pages** with real functionality (~12 pages)
5. Left all other pages as **empty stubs** since there were no specs to implement against

### The Stub Pattern

Every stub page followed an identical pattern:
```tsx
import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="[Title]" emptyStateLabel="[Label]" />
    </AppLayout>
  );
}
```

This satisfies the route parity check (`98/98 routes matched`) but provides no actual functionality.

---

## Divergence Categories

### Category A: Structural Compliance (LOW divergence)

The structural elements match the Lovable prototype well:

| Element | Lovable Spec | vNext Implementation | Verdict |
|---------|-------------|---------------------|---------|
| Route count | 98 | 98 | MATCHED |
| Zone count | 15 | 15 | MATCHED |
| Layout variants | 4 | 4 | MATCHED |
| Sidebar contexts | 11 | 11 | MATCHED |
| Auth pathways | 4 | 4 defined, 2 functional | PARTIAL |
| Golden paths | 6 | 6 defined, tests exist | MATCHED |
| Zustand stores | 6 contexts → 4 stores | 4 stores | DIVERGED (documented, acceptable) |
| Session storage keys | 2 → 5 | 5 | DIVERGED (documented, acceptable) |

### Category B: Page Implementation Depth (CRITICAL divergence)

| Zone | Total Routes | Pre-Remediation Implemented | Gap |
|------|-------------|---------------------------|-----|
| Auth | 8 | 0 | 8 |
| Home | 4 | 1 | 3 |
| Facility | 2 | 1 | 1 |
| Workspace | 2 | 1 | 1 |
| Shift | 3 | 1 | 2 |
| Queue | 6 | 1 | 5 |
| EHR | 17 | 1 | 16 |
| Admin | 12 | 3 | 9 |
| Registry | 8 | 2 | 6 |
| Marketplace | 6 | 2 | 4 |
| Finance | 5 | 0 | 5 |
| Pharmacy | 4 | 1 | 3 |
| Inventory | 4 | 0 | 4 |
| Reports | 6 | 0 | 6 |
| Settings | 6 | 0 | 6 |
| Root | 1 | 0 | 1 |
| **TOTAL** | **94** | **14** | **80** |

### Category C: Layout Component Gaps (CRITICAL divergence)

| Component | Lovable Spec | Status | Impact |
|-----------|-------------|--------|--------|
| AppLayout | 4 layout variants, 3-zone sidebar | Implemented | None |
| AppSidebar (ZoneNavigation) | 3-zone nav with context items | Implemented | None |
| AppHeader | Context badges, user info | Implemented | None |
| EHRLayout | Full layout with TopBar + EncounterMenu | **27-line stub** | No contextual actions in EHR |
| TopBar | Operational actions, breadcrumbs | **ABSENT** | No pharmacy/payments/orders access |
| EncounterMenu | 6-group clinical sidebar | **ABSENT** | No persistent clinical navigation |
| AuthLayout | Centered card layout | Implemented | None |
| MinimalLayout | Minimal header | Implemented | None |

### Category D: Clinical Workflow Depth (HIGH divergence)

| Workflow | Lovable Implied | vNext State | Gap |
|----------|----------------|-------------|-----|
| Encounter lifecycle | Full SOAP workflow | Stub pages | No vitals/notes/orders forms |
| Referral workflow | Consultations + Referrals + Teleconsults tabs | No referrals page | No referral creation/tracking |
| Discharge workflow | Full discharge form | Stub page | No discharge form |
| Queue management | Triage + Waiting + Scheduled + Walk-in | Only main queue page | No sub-queue views |
| Clinical notes | SOAP structure | Stub page | No note creation |
| Lab orders | Order creation + result viewing | Stub pages | No order workflow |

### Category E: Telemedicine (COMPLETE divergence)

| Feature | Lovable Implied | Web vNext | Mobile vNext |
|---------|----------------|-----------|-------------|
| Telemedicine Hub | First-class clinical workflow | ABSENT | PRESENT |
| Quick Connect | Ad-hoc teleconsult | ABSENT | PRESENT |
| Schedule Teleconsult | Scheduled sessions | ABSENT | PRESENT |
| Bidirectional flow | Receive/accept/reject | ABSENT | PARTIAL |
| Session management | Status tracking | ABSENT | PRESENT |

**Note**: Telemedicine was not included in the 98-route Experience Platform spec. It exists only in mobile apps. This may be intentional (mobile-first telemedicine) or an oversight.

---

## Divergence Severity Classification

### CRITICAL (Must Fix)
1. **85 stub pages** — 87.6% of routes have no real implementation
2. **EHRLayout** — Missing TopBar and EncounterMenu
3. **Auth pages** — No login/logout UI despite auth being a golden path

### HIGH (Should Fix)
4. **Queue sub-pages** — Triage, waiting, scheduled views all stubs
5. **EHR clinical pages** — Vitals, notes, orders, referrals all stubs
6. **Finance zone** — Completely empty
7. **Reports zone** — Completely empty

### MEDIUM (Nice to Fix)
8. **Settings zone** — All stubs
9. **Inventory zone** — All stubs
10. **Admin detail pages** — User/audit detail views

### LOW (Acceptable)
11. **Zustand vs React Context** — Documented divergence, functionally equivalent
12. **Session storage key count** — Extended for practical needs
13. **Supabase → BFF** — Correct architectural upgrade

---

## Remediation Plan

All CRITICAL and HIGH divergences are being remediated in this wave:
- 85 stub pages → real implementations with API integration
- EHRLayout → upgraded with TopBar + EncounterMenu
- Auth pages → real login/logout forms
- Queue/EHR/Finance/Reports → full implementations

See `fidelity-remediation-log.md` for implementation details.
