# Owner Preview Test Checklist

Expert-user validation for the **Dev Preview Sandbox** (not formal staging).

**Preview URL:** http://41.57.127.235/

**Production-readiness program (2026-06):** Extended manual scripts for all 14 critical services + shell corrections live in [`docs/product-truth/preview-validation-production-readiness.md`](../product-truth/preview-validation-production-readiness.md). Key shell checks: hero auth logo, Nompilo via taskbar/`Ctrl+K` (not full-page strip), provider login → `/provider-workspace`, OROS `/lab/worklist` live data.

## Before Testing

1. Confirm preview is deployed: `bash scripts/deploy/preview-status.sh`
2. Confirm version: `curl -s http://41.57.127.235/health/version`
3. Note environment should show **preview** / **sandbox**

## Checks

| # | Check | Pass? | Notes |
|---|-------|-------|-------|
| 1 | Preview URL loads in browser | | |
| 2 | Environment shows Preview/Sandbox | | |
| 3 | Branch + commit SHA visible (version endpoint or UI) | | |
| 4 | Login/auth page or auth fallback behavior | | Keycloak may be off in MVP |
| 4a | Citizen sign-up `/auth/register` completes or shows explicit error (not silent failure) | | Expect CITIZEN role; `ROLE_ASSIGNMENT_FAILED` must not leave orphan account |
| 4b | SUPER_ADMIN (or SYSTEM_ADMIN+DEVELOPER) sees Work/Admin/Finance nav zones | | Platform override roles expand sidebar visibility |
| 5 | Main navigation / shell loads | | |
| 6 | Registry workflows (if backend up) | | May 503 without registry services |
| 7 | Clinical workflows | | Partial without full stack |
| 8 | Enterprise plane areas | | |
| 9 | Data/intelligence areas | | |
| 10 | Nompilo | | |
| 11 | Fundo | | |
| 12 | Ndila | | |
| 13 | Nhume | | |
| 14 | MusheX | | |
| 15 | MADI — donor hub `/madi/donor` (register, drives, feedback) | | My Life zone |
| 16 | MADI — donation drives `/madi/drives` | | Facility context for ops |
| 17 | MADI — blood bank `/madi/blood-bank` (orders, stock, crossmatch, issue) | | |
| 18 | MADI — clinical order `/madi/orders` from EHR orders page | | |
| 19 | MADI — transfusion `/madi/transfusion` | | |
| 20 | MADI — haemovigilance `/madi/haemovigilance` | | |
| 21 | MADI — central bank `/madi/central-bank` | | National/regional view |
| 22 | Impilo Live hub `/live` loads (discover, replays, saved) | | My Life / Work nav |
| 23 | Impilo Live — professional CPD webinar journey | | `/live/discover` → register → room → attendance → CPD cert |
| 24 | Impilo Live — citizen health talk + Madi donor pathway | | `/live/discover` (CITIZEN) → Madi drive link → replay |
| 25 | Impilo Live — organiser manage + analytics | | `/live/manage` → moderation → `/live/event/{id}/analytics` |
| 26 | Impilo Live — resources tab in live room | | Host adds resource; attendees see list (no 404 on `/resources`) |
| 27 | Impilo Live — replay after event ends | | Status `PUBLISHED_REPLAY`; replay page tracks watch minutes |

### Vashandi workforce preview personas (2026-06)

Preview-only fixtures in `contracts/trust/seeds/preview-vashandi-session-fixtures.json`. Password for all listed personas: `Vashandi@2024!`. Login page shows a **PREVIEW — Vashandi validation sign-in** panel when `NEXT_PUBLIC_IMPILO_ENV=full-preview`.

| Persona | Email | Expected Vashandi surfaces |
|---------|-------|----------------------------|
| National Workforce Admin | vashandi.national@mohcc.gov.zw | Dashboard, workforce registry, analytics |
| Facility Workforce Manager | vashandi.facility@mohcc.gov.zw | Facility staff, rosters, assignments, analytics |
| Ordinary Worker | vashandi.worker@mohcc.gov.zw | My roster, my attendance, leave/availability only |
| HSC Workforce User | vashandi.hsc@mohcc.gov.zw | HSC postings, workforce registry |
| Access Reviewer | vashandi.reviewer@mohcc.gov.zw | Access review, analytics |
| Negative control (citizen) | tatenda.moyo@example.com | No Vashandi nav or routes |

Smoke script: `bash scripts/test/smoke-vashandi-preview-personas.sh` (after BFF/Keycloak redeploy applies fixture wiring).

### Gap-closure journeys (2026-06-08)

| # | Check | Pass? | Notes |
|---|-------|-------|-------|
| 28 | Client intake dedup wizard `/registry/intake` or `/registry/clients/new` | | Search → VITO score → merge when pending case exists |
| 29 | Guardian-assisted + emergency provisional intake panels | | `/registry/clients/new` — honest BFF create/intake session |
| 30 | Provider council workspace `/registry/provider-council/council-workspace` | | Tabs: pending / approved / rejected / needs-info; advance + review actions |
| 31 | Provider login lands Work context | | Provider auth → `/auth/resolving` → `/provider-workspace` (not citizen home) |
| 32 | OROS lab hub `/lab` multi-type orchestration | | LAB / IMAGING / PHARMACY / BLOOD filters; links to worklist, catalog, reconciliation |
| 33 | OROS catalog `/lab/catalog` live data | | No static category shell; BFF `/internal/v1/lab-catalog` |
| 34 | OROS reconciliation `/lab/reconciliation` match/resolve | | BFF `/internal/v1/lab-reconciliation` |
| 35 | Encounter care chain rail on EHR encounter page | | OROS orders + Costa billing + MusheX payment counts visible |
| 36 | Inpatient admissions + nursing `maturity=live` | | `/clinical/inpatient/admissions`, `/clinical/inpatient/nursing` task lists |
| 37 | Imaging worklist + facility dashboard | | `/imaging/worklist` (IMAGING type), `/imaging/facility` |
| 38 | Wellness commodities (inventory-backed) | | `/wellness/commodities` → stock management embed |
| 39 | MusheX finance journeys rail + service access | | `/finance` rail; `/finance/service-access` exemption/deferred register |
| 40 | Enterprise charge-sheet consumables + drill-down | | `ConsumablesCostPanel`; `/enterprise/oversight`; geography KPI tiles on dashboard |
| 41 | Coverage Ndila geography map | | `/coverage` → Intelligence → Geography tab |
| 42 | Public health national oversight | | `/public-health/oversight` district/province drill-down + field task queue |
| 43 | MADI blood logistics panel stock summary | | Blood-bank stock KPIs alongside Nhume dispatch boundary |

### Coverage enrolment-ready (2026-06-08)

| # | Check | Pass? | Notes |
|---|-------|-------|-------|
| C0 | Compose coverage-service healthy | | `./tools/dev/up.sh` — `:8140/actuator/health` |
| C1 | Real-stack enrollment e2e | | `PLAYWRIGHT_COMPOSE_E2E=1` + `coverage-enroll-compose.spec.ts` |
| C2 | Mocked full journey e2e | | `coverage-enroll-flow.spec.ts` — plan→eligibility→enroll→member |
| C3 | Subsidies tab live | | `/coverage` → Subsidies — `SUB-MOHCC-PRIMARY` |
| C4 | Finance remittance hub | | `/finance/remittances` — same BFF feed as settlement tab |
| C5 | Smoke Test 6 | | `compose/experience/smoke-test.sh` — governed `COV-MOHCC-CORE` |

### Simba / Wellness enrolment-ready (2026-06-08)

| # | Check | Pass? | Notes |
|---|-------|-------|-------|
| W0 | Compose simba-service healthy | | `./tools/dev/up.sh` — `:8125/actuator/health` |
| W1 | BFF Simba-only proxy | | All wellness + citizen paths → `simba-service:8125` |
| W2 | Mocked full journey e2e | | `wellness-journey-flow.spec.ts` — goals→activity→diet→club→challenge |
| W3 | Compose journey e2e | | `PLAYWRIGHT_COMPOSE_E2E=1` + `wellness-journey-compose.spec.ts` |
| W4 | Screening programmes UI | | `/wellness/screenings` — Simba `SCR-HIV-ANNUAL` row |
| W5 | Routes distance/elevation | | `/wellness/routes` — Avondale loop from Simba |
| W6 | Smoke Test 7 | | `compose/experience/smoke-test.sh` — `Harare Morning Walkers` |
| W7 | Simba integration tests | | `SimbaWellnessJourneyIT` in `simba-service` |

### Fundo enrolment-ready (2026-06-08)

| # | Check | Pass? | Notes |
|---|-------|-------|-------|
| F0 | Compose learning-service healthy | | `./tools/dev/up.sh` — `:8235/actuator/health` |
| F1 | Real-stack catalog e2e | | `PLAYWRIGHT_COMPOSE_E2E=1` + `fundo-learning-compose.spec.ts` |
| F2 | Governed content + upload formats | | `FUNDO_CONTENT_FORMATS.md`, `/learning/library/uploads` |
| F3 | Certificate verification digest | | Issue cert → detail shows SHA-256 digest |
| F4 | Council CPD bridge UI | | `fundo-cpd-council-flow.spec.ts` + self-service page |
| F5 | Assessment moderation queue | | `/learning/admin/moderation` |

### Fundo LMS depth (2026-06-08)

| # | Check | Pass? | Notes |
|---|-------|-------|-------|
| F1 | Full learner journey e2e | | `e2e/fundo-learning-flow.spec.ts` — catalog→enrol→lessons→assessment→certificate→CPD |
| F2 | Rich lesson delivery | | `FundoLessonContent` — video embed, practical checklist, documents |
| F3 | Survey respond (no placeholder) | | `/learning/surveys/{id}/respond` — interactive activity + submit |
| F4 | Provider mobile assessment + certificate | | `FundoLearningShellScreen` — submit attempt + issue certificate |
| F5 | Preview: `/learning/catalog` live | | Requires `learning-service` pod healthy in preview stack |

### Shell requirements A–D (2026-06-08)

| # | Check | Pass? | Notes |
|---|-------|-------|-------|
| A | Auth hero logo at 375 / 768 / 1280 | | `e2e/auth-hero-logo.spec.ts` — `data-testid="impilo-brand-hero"` on `/auth/login` |
| B | Nompilo via taskbar only (no page chrome) | | No `ProactiveAssistant` / `FloatingClinicalAssist` in AppLayout/EHRLayout; taskbar Ask + Ctrl+K |
| C | Role landing URLs + my_life mode | | `e2e/provider-login-flow.spec.ts` — provider→workspace, citizen→home+my_life, facility guard, activation |
| C2 | Register elevation journey | | `e2e/register-elevation-flow.spec.ts` — Health ID self-reg, deferred professional elevation |
| D | Lab catalog/reconcile live (no static shells) | | `useLabCatalog` / `useLabReconciliation`; `no-stub-guard` blocks static CATEGORIES |

### Preview-runtime depth (2026-06-08)

| # | Check | Pass? | Notes |
|---|-------|-------|-------|
| 44 | Telemedicine care chain rail + RTC health | | `/telemedicine` rail; `/teleconsult/ops/rtc-health` shows LiveKit readiness |
| 45 | Triage → teleconsult escalation | | `/queue/triage` “Escalate to teleconsult” deep-links `/telemedicine/new?patientId=…` |
| 46 | Session auto-media + LiveKit room | | `/telemedicine/session/{id}` provisions token on ACTIVE; video pane connects or shows honest RTC boundary |
| 47 | Council MusheX intent + sync payment | | `/registry/provider-council/self-service?providerId=…` — Create intent → Sync payment → fee-paid advance |
| 48 | National revenue oversight panel | | `/enterprise/oversight` — Costa national revenue + claims + MusheX remittances + debt aging |
| 49 | MADI central-bank mobile tab | | Provider app Clinical Tools → **Central Bank** — metrics + emergency redistribution approve/handoff |
| 50 | **Administration & Governance** — Work → Administration & Governance visible for `superadmin@impilo.gov.zw` | | Requires sovereign WGV assignment or preview Product Owner access mode |
| 51 | **Bootstrap** — `/bootstrap` loads First National Administrator wizard | | Token: `ImpiloPreviewBootstrap2026!` (preview only) |
| 52 | **Onboarding** — `/work/administration-governance/onboard` and org data-uploads routes load | | BFF must reach workforce-governance-service (not localhost:8165) |

### Regulatory / NCZ (2026-07-31)

| # | Check | Pass? | Notes |
|---|-------|-------|-------|
| R1 | Regulatory hub `/work/regulatory/[orgId]` — Registers / Student applications / Student reports / CPD / Restrictions / Audit are real links (not dashed “Not yet available” stubs for those tiles) | | Requires varapi + BFF + org-registry on preview |
| R2 | Registers page shows provenance (`CONFIG_PACK` vs `MIGRATION_SEED`) and reconcile does not invent a studio | | Task #97 materialiser |
| R3 | Student applications queue → detail: return section requires a reason; admit blocked when fee not chargeable; admit shows index number when chargeable + complete | | NCZ-W1C |
| R4 | Student reports panel loads four W1D boards (or honest empty/error) | | NCZ-W1D |
| R5 | CPD review: enter provider numeric id → Fundo candidates; public-id lookup stays read-only | | No council-wide queue claim |
| R6 | Restrictions list is read-only and points impose path to disciplinary | | |
| R7 | Audit page states configuration-pack scope (not a full register-access desk) | | |
| R8 | Public regulatory explorer lists registers per council without sign-in | | `/internal/v1/public/gateway/regulatory/…` |
| R9 | Applicant student page: returned section cannot resubmit empty content | | `/professional/regulatory/apply/student/…` |
| R10 | Legacy `/work/regulators/…/cpd-review|restrictions|audit` redirect into regulatory org tree | | No ScopedAdministrationSurface |

## Product Owner preview access (full-preview only)

- Login: `superadmin@impilo.gov.zw` / `Impilo@2024!`
- Health ID (session actor): `b0000000-0000-4000-8000-000000000010`
- Preview env flag: `IMPILO_PREVIEW_PRODUCT_OWNER_ACCESS=true` (never enable in production)
- Bootstrap token (preview): `ImpiloPreviewBootstrap2026!`
- After deploy: `bash scripts/deploy/seed-full-preview-sovereign-data.sh` if sovereign org missing

## Impilo Live smoke notes

- Seed events: CPD webinar (PROFESSIONAL), citizen health talk, Madi-linked donor drive (see `live-service` `V002__live_seed.sql`).
- Media health: `GET /internal/v1/live/room/{eventId}/media-health` — `productionReady: false` in dev (`LOCAL_DEV` provider).
- Fundo CPD bridge: attendance certificate flow posts to `learning-service` `/internal/v1/learning/v11/sessions/live-completion`.
- Preview deploy required before browser verification; confirm commit via `/health/version`.

## Error Capture

- Browser devtools → Network tab for failed API calls
- Screenshot + URL + timestamp
- `kubectl logs -n impilo-preview -l app=experience-bff --tail=50`

## Report Missing UI for Backend Features

If API works in logs/Postman but UI missing: file issue with route, BFF path, and commit SHA.
