# vNext Parity Closure Plan

> **Wave:** Product Completion Wave 1  
> **Generated:** 2026-06-13  
> **Sources:** `FRONTEND_BACKEND_PARITY_MATRIX.md`, `MOBILE_PARITY_MATRIX.md`

---

## Summary

| Matrix | Complete | Partial | Deferred/Missing |
|--------|----------|---------|------------------|
| Frontend ↔ Backend (37 rows) | 17 | **20** | 0 |
| Mobile (37 rows) | 8 | **29** | 8 intentionally deferred |

---

## Ranked closure order

### Tier 1 — Login / shell / workspace / context (P0)

| Row | Capability | PCW-2 status | Evidence |
|-----|------------|--------------|----------|
| Health OS Launcher | Role/facility launcher | **Advanced** — 31-tile catalogue + readiness metadata | `HealthOsLauncherControllerTest`, `launcher-vnext-catalog.json` |
| VITO | Client registry | **Complete (web Tier 1)** | `/registry/clients`, degraded `meta.guidance`, shell test |
| VARAPI | Provider registry | **Complete (web Tier 1)** | `/registry/providers`, degraded `meta.guidance`, `RegistryControllerTest` |
| TUSO | Facilities | **Complete (web Tier 1)** | `IMPILO_BFF_FACILITIES_MODE: live`, TUSO degraded meta |
| TSHEPO Trust admin | Policies, break-glass | Partial — Tier 2 | unchanged |

**PCW-2 report:** `reports/product/pcw-2-tier1-parity-closure-report.md`

---

### Tier 2 — Registries / trust (P1)

| Row | Capability | Priority | Owner area | Required fix |
|-----|------------|----------|------------|--------------|
| UBOMI | CRVS | HIGH | `useUbomiRegistry.ts`, `/ubomi` | Live births/deaths when service up |
| Break-glass | Emergency access | HIGH | `BreakGlassRequestPanel.tsx` | ED + EHR emergency views |
| Admin/Governance | Users, roles | MEDIUM | `useAdminUsers.ts`, `/admin/*` | Document Blocked surfaces |
| Integration Hub | Routes, DLQ | MEDIUM | `useIntegrationHub.ts` | Admin depth |
| ZIBO | Terminology | LOW | `ziboApi.ts`, `/registry/terminology` | Maturity labels |

---

### Tier 3 — Clinical / PCT / telemedicine (P1)

| Row | Capability | Priority | Owner area | Required fix |
|-----|------------|----------|------------|--------------|
| Core Transaction | Journey steppers | HIGH | `useCoreTransactionExperience.ts` | Command/handoff wiring |
| Telemedicine | Teleconsult | HIGH | `useTelemedicine.ts`, `/telemedicine/*` | Honest Blocked for RTC |
| Nhume | Dispatch | HIGH | `useDispatchOps.ts`, `/nhume/*` | Unified operator UX |
| Workflow/Dispatch | Instances | HIGH | `useDispatchOps.ts` | Instance table + guided detail |
| Wellness/Monitoring | Devices | MEDIUM | `useCitizenMonitoring.ts` | Simba proxy depth (wellness→simba) |

**MADI rows (11):** All **complete** on web — benchmark; mobile gaps remain (see Tier 5).

---

### Tier 4 — Enterprise / resource (P2)

| Row | Capability | Priority | Owner area | Required fix |
|-----|------------|----------|------------|--------------|
| MusheX/COSTA | Payments, billing | HIGH | `useMusheWallet.ts`, `/finance/*` | Finance mobile parity |
| Msika/Msika Flow | Marketplace | MEDIUM | `useMarketplace.ts` | Honest blocked on lists |
| Fundo | LMS | MEDIUM | `useFundoLms.ts`, `/learning/*` | Mobile module depth |
| Comms Hub | Notifications | MEDIUM | `useOmnichannel.ts` | Actionable tasks |

---

### Tier 5 — Citizen / mobile (P2)

**29 partial mobile rows** — prioritize:

| Capability | Mobile gap | Fix area |
|------------|------------|----------|
| VITO/VARAPI/TUSO | partial screens | `apps/mobile/provider-app` registry modules |
| Telemedicine | partial | RTC blocked label on mobile |
| UBOMI | intentionally deferred | Add citizen birth/death screens |
| MADI processing/stock/central-bank/dashboard | intentionally deferred | Provider mobile blood bank modules |
| Data Pipeline/NDR | missing | Provider governance strip |
| ZIBO | not supported | Web-only maturity label |
| Health OS Launcher | partial | Mobile app launcher parity |

**Files:** `apps/mobile/packages/mobile-registry`, `docs/implementation/mobile-parity-wave.md`

---

### Tier 6 — Reporting / analytics (P3)

| Row | Status | Fix |
|-----|--------|-----|
| Data Pipeline & NDR | **complete** web | Provider mobile strip only |
| Telemedicine analytics | **complete** web | Provider SLA strip mobile |
| Nompilo | partial | Context query params + fallback label |

---

## Full partial frontend/backend list (20)

1. TSHEPO Trust admin — HIGH  
2. VITO — HIGH  
3. VARAPI — HIGH  
4. TUSO — HIGH  
5. Core Transaction — HIGH  
6. Nhume — HIGH  
7. Comms Hub — MEDIUM  
8. Telemedicine — HIGH  
9. Break-glass — HIGH  
10. Msika/Msika Flow — MEDIUM  
11. MusheX/COSTA — HIGH  
12. Fundo — MEDIUM  
13. UBOMI — HIGH  
14. ZIBO — LOW  
15. Nompilo — HIGH  
16. Integration Hub — MEDIUM  
17. Workflow/Dispatch — HIGH  
18. Admin/Governance — MEDIUM  
19. Health OS Launcher — HIGH  
20. Wellness/Monitoring — MEDIUM  

---

## Full partial mobile list (29)

All 37 rows except: Social, MADI (donor/drives/orders/transfusion/haemovigilance), Impilo Live, Wellness/Monitoring (complete on mobile).

**Intentionally deferred (8):** Telemedicine analytics, Data Pipeline/NDR, UBOMI, ZIBO, MADI processing/stock/central-bank/dashboard.

---

## Implementation waves (recommended)

| Wave | Scope | Est. effort |
|------|-------|-------------|
| **PCW-2** | Tier 1 (launcher, WORK, Vito/Varapi/Tuso) | 1 sprint |
| **PCW-3** | Tier 3 clinical + telemedicine honest blocked | 1 sprint |
| **PCW-4** | Tier 4 enterprise/finance | 1 sprint |
| **PCW-5** | Tier 5 mobile partial rows | 2 sprints |
| **PCW-6** | Tier 2 trust/admin depth | 1 sprint |

---

## Test evidence per closure

Each closed row requires:
1. Parity matrix regenerated → `complete`
2. UAT scenario PASS in `vnext-91-service-testing-dataset.csv`
3. `npm run test:no-stubs` PASS for affected routes
4. Mobile row updated if applicable

---

## Acceptance

- [x] 20 partial web rows listed with priority
- [x] 29 partial mobile rows categorized
- [x] Ranked by product importance
- [x] Owner files/areas identified
- [ ] Implementation (deferred to PCW-2+)
