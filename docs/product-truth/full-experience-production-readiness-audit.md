# Full Experience Production Readiness Audit

| Field | Value |
|-------|-------|
| Generated | 2026-06-08 (Wave 0 — Full Experience Production Readiness program) |
| Branch | `claude/staging-ux-orchestration-remediation-Yypyl` |
| Orchestration layer | `ui/one-ui-shell` (474 routes in `routes.ts`) |
| Transaction journeys | **47/47 transaction-complete** (post Wave 5) |
| VM gates baseline | 21/21 PASS |

## Executive summary

Impilo vNext has substantial backend and BFF coverage. This program closes **surfacing depth gaps** — static shells, role landing, Nompilo chrome, unregistered routes, and sidecar absorption honesty — so preview validation reflects a sovereign national Health OS, not a route catalogue.

**Skeptical reconciliation:** Prior audits (`CORE_TRANSACTION_HONEST_GAP_AUDIT.md`, `VNEXT_EXPERIENCE_COHERENCE_REPORT.md`) under-counted completion during remediation waves. The authoritative generator now reports **47/47** with runtime evidence entries in `generate-core-transaction-maps.mjs`. Depth gaps addressed in Waves 1–6 are documented per service below.

---

## Global shell / UX (Waves 1–3)

| Requirement | Before | After (program) |
|-------------|--------|-----------------|
| Auth logo prominence | 28–40px | Hero variant 40–64px (`ImpiloBrandLogo variant="hero"`) |
| Nompilo page chrome | Full-width `NompiloGlobalCommandBar` in `AppLayout` | Removed; taskbar + `Ctrl+K` palette + `/ask` |
| Provider post-login | Always `/home` | `resolvePostLoginDestination()` → `/provider-workspace` (facility guard chain) |
| Auth funnel parity | provider-id/MFA/biometric skipped resolver | All paths → `/auth/resolving` with `returnTo` |

---

## 14 critical services — surfacing matrix

| # | Service | Routes (canonical) | BFF / hooks | Wave | Top blocker closed |
|---|---------|-------------------|-------------|------|-------------------|
| 1 | Client intake / VITO | `/registry/clients/*`, `/registry/intake`, `/operations/vito/*`, `/citizen/*` | `useClientRegistry`, `useVitoIssuance`, `useRegistryIntake` | 2 | Status badges; routes registered |
| 2 | Provider ID (VARAPI) | `/registry/providers/*`, `/provider/activate` | `useProviders`, `useProviderLifecycle` | 2 | Onboarding rail apply/verify actions |
| 3 | Provider login | `/auth/*`, `/provider-workspace` | `resolvePostLoginDestination`, `useLinkedIds` | 1 | Work landing, not citizen-first |
| 4 | PCT / telehealth / inpatient | `/queue/*`, `/telemedicine/*`, `/clinical/inpatient/*` | `useQueue`, `useTelemedicine`, `useInpatient` | 3 | RTC token path; inpatient maturity labelled |
| 5 | Simba / Wellness | `/wellness/*` | `useSimba`, `useCitizenWellness` | 4 | Screenings → BFF reminders; dashboard registered |
| 6 | PACS | `/ehr/[patientId]/imaging/*` | `useImagingStudies` | 3 | Viewer boundary honest (no fake DICOM) |
| 7 | MADI | `/madi/*` (30 routes) | `useMadi` | 5 | Logistics → `MadiBloodLogisticsPanel` + dispatch KPIs |
| 8 | OROS / lab | `/lab/*` | `useLabWorklist`, `useLabOrders` | 3 | BFF `LabWorklistController`; worklist accept/reject |
| 9 | MusheX | `/finance/*`, `/wallet/*` | `useMusheWallet`, `useMushexPlatformAdmin` | 4 | Wallet credit mutation on platform hub |
| 10 | Costa | `/finance/costa/*`, `/finance/billing` | `useCostaIntel`, `useFinanceBillingWorkspace` | 4 | Invoice-from-estimate on encounter page |
| 11 | Fundo | `/learning/*` | `useFundoLms`, `useFundoCatalog` | 5 | Provider mobile depth in evidence |
| 12 | Coverage | `/coverage/*` | `useCoverage`, `useMemberCoverage` | 2/4 | Sub-routes in `routes.ts` |
| 13 | Indawo / PH | `/public-health/*` | `usePublicHealth`, `useSiteRegistry` | 5 | `NdilaMap` on site registry |
| 14 | Enterprise | `/enterprise/*` | `useInventory*`, `useDispatchOps`, `useCostaIntel` | 4 | Warehousing + charge-sheet wired |

---

## Mock / stub / placeholder findings (production paths)

| Pattern | Location | Resolution |
|---------|----------|------------|
| Hardcoded `"0"` KPI shells | `/lab/worklist` (was) | Wave 3 — live worklist data |
| Fixture screenings | `/wellness/screenings` (was) | Wave 4 — `useReminders` |
| Decorative warehousing | `/enterprise/warehousing` (was) | Wave 4 — inventory hooks |
| Prose-only charge sheet | `/enterprise/charge-sheet` (was) | Wave 4 — tariff + billing lookup |
| Unregistered routes | coverage sub-routes, wellness dashboard, registry clients | Waves 2/4 — `routes.ts` |

Guards: `check-frontend-mocks-and-stubs.sh`, `no-stub-guard.mjs`, `check-retired-sidecars-full-boot.sh` (Wave 6).

---

## Sidecar absorption matrix

| Sidecar | Ledger status | Task parity | Retirement (Wave 6) |
|---------|---------------|-------------|---------------------|
| `ui/oros-web` | absorbed (partial catalog/reconcile BFF gap) | Worklist accept/reject real | Held — not in RR ledger |
| `ui/mushex-finance-console` | retired sidecar path | Finance routes in shell | **Retired** — CI/build removed |
| `ui/mushex-ops-console` | retired sidecar path | Payer ops in shell | **Retired** |
| `ui/mushex-payer-portal` | retired sidecar path | Wallet/finance in shell | **Retired** |
| `ui/ehr` | retired sidecar path | `/ehr/[patientId]/*` | **Retired** |
| `ui/ops-docs` | partially absorbed | Document issue/print blocked | **Held (Tier D)** |
| `ui/costa-console` | absorbed | Costa finance routes | Held — no DEPRECATED.md |

Detail: [`wave-6-sidecar-retirement-record.md`](wave-6-sidecar-retirement-record.md)

---

## Remaining backend gaps (honest boundaries)

1. **OROS lab catalog / reconciliation** — BFF missing `/internal/v1/lab-catalog` and `/internal/v1/lab-reconciliation`; pages maturity-labelled.
2. **PACS facility worklist** — patient-context imaging only; no technologist facility queue.
3. **Enterprise cross-service KPIs** — revenue/cold-chain/settlement composition endpoints not unified.
4. **MusheX marketplace commerce** — operator 501 paths documented as blocked.
5. **ops-docs document issue/print** — partial absorption; blocker contract open.

---

## Machine truth sources

- `node scripts/product/generate-product-truth-recovery.mjs`
- `node scripts/product/generate-core-transaction-maps.mjs`
- `node scripts/product/generate-experience-orchestration.mjs`
- `node scripts/frontend/generate-parity-docs.mjs`
- Reports: `reports/product/core-transaction-completion-matrix.json`

---

## Stale docs reconciled

| Doc | Action |
|-----|--------|
| `VNEXT_EXPERIENCE_COHERENCE_REPORT.md` | Superseded by this audit for production-readiness scope |
| `CORE_TRANSACTION_HONEST_GAP_AUDIT.md` | Historical; 47/47 achieved with evidence registry |
| `PHASE_4_0_REBASELINE_REPORT.md` | Historical baseline only |
