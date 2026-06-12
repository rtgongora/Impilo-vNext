# Impilo vNext — Full Preview User Acceptance Testing & Validation Pack

| Field | Value |
|-------|--------|
| **Document version** | 1.0 |
| **Generated** | 2026-06-11 |
| **Preview URL** | http://41.57.127.235 |
| **Namespace** | `impilo-full-preview` (public ingress) |
| **Branch** | `claude/staging-ux-orchestration-remediation-Yypyl` |
| **Baseline commit** | `c5f8ff43` (last successful full preview build, 2026-06-05) |
| **HEAD under test** | `4917def8` (pushed) |
| **Validated change range** | `c5f8ff43..4917def8` (**90 commits**) |
| **Route inventory** | **575** one-ui-shell routes (0 missing) |
| **Runtime closure** | 98/98 deployments ready · 0 problem pods · Postgres healthy · digest alignment PASS |
| **Machine-readable scripts** | [`reports/product/uat-full-preview-test-scripts-4917def8.csv`](../../reports/product/uat-full-preview-test-scripts-4917def8.csv) |
| **Summary JSON** | [`reports/product/uat-full-preview-test-summary-4917def8.json`](../../reports/product/uat-full-preview-test-summary-4917def8.json) |
| **Regenerate CSV** | `python3 scripts/product/generate-uat-full-preview-pack.py` |

---

## 1. Executive testing overview

### Purpose

This pack is a **product-owner-ready user acceptance testing (UAT) design** for the current full-preview build. It translates repository evidence (commits, routes, parity matrices, branding registry, smoke reports, and existing owner checklists) into **practical scenarios** that clinicians, administrators, trainers, citizens, and reviewers can execute in the browser.

It is **not** a build/CI report. Technical gates already passed on the VM; this document defines **what humans should validate** before formal product review.

### Preview under test

- **URL:** http://41.57.127.235
- **Experience layer:** `one-ui-shell` + `experience-bff` (generation `preview-ba7064e2` on ingress)
- **Backend microservices:** 91 Impilo deployments in `impilo-full-preview`; 27 digest-aligned services on `preview-4917def8`
- **Version endpoint:** `/health/version` reports `ba7064e2` (ingress generation; UX/branding changes are in this build)

### Commit range under test

**90 commits** from `c5f8ff43` to `4917def8`, including major product waves:

| Theme | Representative commits | User-visible impact |
|-------|------------------------|---------------------|
| Experience shell A–D | `8629e068`, `5fa3f2e3`, `8510069f` | Auth hero logo, Nompilo taskbar, role landing, provider workspace |
| Sovereign branding | `82778272`, `23f772cf` | Service logos across shell, launcher, PageShell |
| Admin & governance | `b0c6d917`, `fc30788d`, `a94e4fba` | Bootstrap onboarding, invitations, Keycloak activation |
| Trust / session doctrine | `074f08a1` | Three-tab session, MoHCC organogram catalogues |
| Core transactions & PCT | `e0429eac` … `1117ccdb` | Encounter rails, queue walk-in, booking vs appointment |
| Clinical depth | `28335c71`, `63be5f13`, `1877a505` | Inpatient, ED triage, perioperative, bed management |
| Scheduling & telemedicine | `ae2c5fe5`, `31789b46`, `fe1f6c02` | Appointment comms, telehealth analytics, booking service |
| MADI full stack | `1c462ece` … `40479c38` | Blood bank, transfusion, donor, central bank |
| Impilo Live | `ab4b1b2f` … `77544580` | Web + mobile live events, CPD, campaigns bridges |
| Fundo LMS | `8629e068`, `f68365a4`, `61581828` | Enrolment, uploads, certificates, moderation |
| Gap-closure 14 services | `61c767bd`, `4d53e5cd`, `e586d255` | Lab, MusheX, telehealth RTC, PH, enterprise, coverage, Simba |
| Phase 7 consumer | `bc617034` | Nompilo, social, surveillance, Fundo consumer journeys |
| Facility & Ndila | `325fd97c`, `cdd39c0c` | Facility import, maps, spatial proxy |
| Build unblock | `4917def8` | Backend compile fixes (runtime truth; not directly visible in UI) |

### Testing approach

1. **Smoke first** — runtime checks (UAT-RUN-*) and route survival (UAT-REG-001..022).
2. **Critical path** — shell, auth, launcher, Nompilo, provider landing (Section 8 priority list).
3. **Change validation** — branding, admin-governance, new workflows (test type: *New change*).
4. **Regression** — pre-`c5f8ff43` surfaces still reachable; no stub downgrade.
5. **Role-based depth** — execute scenarios per persona (provider, citizen, admin, PH, finance).
6. **Mobile** — device/emulator pass for parity rows marked *screens* in mobile matrix.
7. **Evidence** — screenshot + URL + timestamp; screen recording for multi-step workflows; network tab for API failures.

### Key acceptance principle

> **Latest accumulated changes must be visible in preview, and pre-existing working functionality must not be unnecessarily lost, hidden, broken, downgraded, or replaced with mocks/stubs.**

Automated guards supporting this principle (already PASS on VM):

- `npm run test:no-stubs` (600 pages)
- `npm run test:routes` (575 routes)
- `check-backend-frontend-parity.sh`
- `check-mobile-parity.sh`
- `preview-http-regression.sh`

### Companion documents

| Document | Use |
|----------|-----|
| [`OWNER_PREVIEW_TEST_CHECKLIST.md`](../environment/OWNER_PREVIEW_TEST_CHECKLIST.md) | Operator quick checklist (49+ rows) |
| [`preview-validation-production-readiness.md`](../product-truth/preview-validation-production-readiness.md) | Production-readiness acceptance scripts |
| [`PRODUCT_OWNER_TEST_SCRIPTS.md`](PRODUCT_OWNER_TEST_SCRIPTS.md) | Deep journey scripts (encounter, etc.) |
| [`FRONTEND_BACKEND_PARITY_MATRIX.md`](../architecture/FRONTEND_BACKEND_PARITY_MATRIX.md) | Capability ↔ route ↔ BFF mapping |
| [`MOBILE_PARITY_MATRIX.md`](../architecture/MOBILE_PARITY_MATRIX.md) | Mobile screen expectations |
| [`serviceBranding.ts`](../../ui/one-ui-shell/src/config/serviceBranding.ts) | Sovereign logo/route registry |

---

## 2. Test roles / personas

| Persona | Typical credentials (preview sandbox) | Primary modules to test |
|---------|--------------------------------------|-------------------------|
| **Client / citizen** | Self-registered or seeded citizen account | Auth register, MY LIFE, wellness, Fundo learner, Impilo Live, social, MADI donor |
| **Provider / clinician** | Provider ID login | WORK, MY PROFESSIONAL, queue, EHR, telemedicine, inpatient, MADI clinical, OROS |
| **Facility clerk / intake officer** | Staff at facility context | Vito intake, dedup wizard, provisional intake |
| **Facility manager** | Facility admin role | Tuso registry, workspace context, enterprise warehousing |
| **Public health officer** | PH programme role | Indawo sites, surveillance, campaigns, oversight drill-down |
| **District / provincial / national administrator** | Elevated org admin | Public health oversight, enterprise oversight, data pipelines |
| **Training manager / Fundo admin** | Learning admin | Fundo studio, moderation, assessments, CPD bridge |
| **Finance officer** | Billing/payments role | MusheX, Costa, coverage remittances, claims |
| **Trust / system administrator** | SUPER_ADMIN / trust admin | Tshepo trust, consent, admin-governance, bootstrap onboarding |
| **Product owner / reviewer** | Any + checklist mode | Branding, launcher discoverability, regression route sweep |
| **QA lead** | N/A | Parity gates, no-stub guard, defect triage |
| **Platform operator** | kubectl (non-UI) | Deployment readiness, Postgres, single public stack |

**Credential note:** Preview sandbox may run with Keycloak preview realm or auth fallback. Testers should obtain test accounts from the implementation team. Auth-gated flows **cannot** be fully validated without real credentials.

---

## 3. Test modules and coverage matrix

Use this matrix as an Excel **Module Coverage** sheet. Full scenario detail is in the CSV.

| Module / Service | User role | Workflow area | Priority | Test type | Preview route / surface | Evidence source |
|------------------|-----------|---------------|----------|-----------|-------------------------|-----------------|
| Shell / landing | Reviewer | Auth & first load | Critical | Smoke | `/`, `/auth/login` | `OWNER_PREVIEW_TEST_CHECKLIST` 1–5; commits `8629e068` |
| Shell / launcher | Provider, Admin | App discovery | Critical | New change | ShellStartMenu | `FRONTEND_BACKEND_PARITY_MATRIX` Health OS Launcher |
| Shell / WORK | Provider | Clinical ops nav | Critical | New change | `/work/*`, `/provider-workspace` | `074f08a1` session doctrine |
| Shell / MY PROFESSIONAL | Provider | Professional mode | High | New change | Provider workspace modes | `074f08a1` |
| Shell / MY LIFE | Citizen | Consumer hub | High | Regression | `/home` | `bc617034` Phase 7 |
| Nompilo | All authenticated | Assist & search | Critical | New change | Taskbar, `Ctrl+K`, `/ask` | `OWNER_PREVIEW_TEST_CHECKLIST` B; `serviceBranding` nompilo |
| Vito | Facility clerk | Client registry & intake | Critical | New change | `/registry/clients`, `/registry/intake` | `6ce9894c`, `f902342f` |
| Varapi | Registry officer | Provider registry | Critical | Regression | `/registry/providers` | Parity matrix VARAPI |
| Tuso | Facility manager | Facility registry | High | Regression | `/registry/facilities` | `325fd97c` |
| Tshepo | Trust admin | Trust & consent | High | New change | `/registry/trust`, `/admin/trust` | `074f08a1` |
| Butano | Clinician | SHR / EHR chart | High | Regression | `/ehr/[patientId]` | Parity matrix BUTANO complete |
| Ubomi | Civil registrar | CRVS | Medium | Regression | `/ubomi` | Parity matrix UBOMI partial |
| Zibo | Terminology curator | Code systems | Low | Regression | `/registry/terminology` | `serviceBranding` zibo |
| Msika | Procurement | Marketplace | Medium | Regression | `/marketplace` | Parity matrix Msika |
| Indawo | PH officer | Site registry + geo | High | New change | `/public-health/site-registry` | `cdd39c0c`; parity complete |
| PCT / Queue | Clinician | Walk-in & queue | Critical | Integration | `/queue`, `/clinical/control-tower` | `1117ccdb`, `9c52f4e3` |
| Telemedicine | Clinician | RTC consult | High | Integration | `/telemedicine/*` | `31789b46`; preview-validation |
| Scheduling / Booking | Clinician, Citizen | Appointments | High | New change | Scheduling surfaces | `fe1f6c02`, `ae2c5fe5` |
| Inpatient | Nurse | Ward & admissions | Critical | New change | `/clinical/inpatient/*` | `28335c71`, `1877a505` |
| OROS / Lab | Lab tech | Orders & worklist | Critical | Regression | `/lab`, `/lab/worklist` | `414db3ba`; `OWNER_PREVIEW` 32–34 |
| PACS / Imaging | Radiographer | Worklist & viewer | High | Regression | `/imaging/worklist` | `9fb15722` |
| MADI | Clinician, Donor | Transfusion chain | Critical | New change | `/madi/*` | MADI commit series; `OWNER_PREVIEW` 15–21 |
| Simba / Wellness | Citizen | Prevention journeys | High | Regression | `/wellness` | `b2fcdf06`; wellness e2e |
| MusheX | Finance | Payments & wallet | High | Integration | `/finance/mushex-platform`, `/wallet` | `4d53e5cd` |
| Costa | Finance | Costing & billing | High | Regression | `/finance/costa/*`, `/enterprise/oversight` | Wave-1 finance evidence |
| Coverage | Enrollment officer | Member enrollment | High | Regression | `/coverage/enroll` | `b2fcdf06`; smoke Test 6 |
| Ndila | PH analyst | Maps | Medium | Regression | `/ndila` | `325fd97c` |
| Nhume | Logistics | Dispatch | Medium | Regression | `/nhume`, `/operations/dispatch` | Parity matrix Nhume |
| Fundo | Learner, Trainer | LMS journeys | High | New change | `/learning/*` | `8629e068`, Fundo pilot docs |
| Public Health | PH officer | Surveillance & oversight | High | New change | `/public-health/*` | `e586d255`, `0089211a` |
| Enterprise | Operations mgr | ERP surfaces | High | Regression | `/enterprise/*` | Gap-closure enterprise |
| Impilo Live | Citizen, Trainer | Events & CPD | High | New change | `/live/*` | Live service series `ab4b1b2f`–`77544580` |
| Admin governance | System admin | Bootstrap & invites | High | New change | `/work/administration-governance` | `fc30788d`, `b0c6d917` |
| Branding | Reviewer | Logos & discoverability | High | New change | `/brand/services/*`, launcher | `23f772cf`, `serviceBranding.ts` |
| Mobile parity | QA, Citizens, Providers | App screens | High | Mobile | `apps/mobile/*` | `MOBILE_PARITY_MATRIX` |
| Regression routes | Reviewer | Route survival | Critical | Regression | 22 hub routes | `preview-http-regression.sh` |
| Runtime / smoke | QA, Operator | Health & cluster | Critical | Smoke | `/health/version`, kubectl | Closure report |

---

## 4. Detailed test scenarios

**180 scenarios** are maintained in machine-readable form:

📄 **[`reports/product/uat-full-preview-test-scripts-4917def8.csv`](../../reports/product/uat-full-preview-test-scripts-4917def8.csv)**

### CSV column definitions (Excel **Detailed Test Scripts** sheet)

| Column | Description |
|--------|-------------|
| **Test ID** | Stable identifier, e.g. `UAT-SHL-002`, `UAT-MAD-003`, `UAT-WF-010` |
| **Module/Service** | Functional grouping |
| **User Role/Persona** | Who should execute the test |
| **Test Scenario** | Short title |
| **Preconditions** | Auth, data, environment |
| **Test Steps** | Numbered steps |
| **Expected Result** | What the user should see |
| **Pass Criteria** | Objective pass |
| **Fail Criteria** | Objective fail |
| **Evidence To Capture** | Screenshot, HAR, recording, log |
| **Priority** | Critical / High / Medium / Low |
| **Test Type** | New change / Regression / Smoke / Usability / Mobile / Integration |
| **Related Route/Surface** | Primary URL or component |
| **Related Commit/Change Evidence** | Traceability to git/docs |
| **Status** | *(blank — Pass/Fail/Blocked/Not Tested)* |
| **Tester Comments** | *(blank)* |
| **Defect Reference** | *(blank)* |
| **Retest Result** | *(blank)* |

### Scenario ID prefixes

| Prefix | Module | Count |
|--------|--------|------:|
| `UAT-SHL-*` | Shell / landing / navigation | 15 |
| `UAT-NOM-*` | Nompilo | 8 |
| `UAT-VIT-*` … `UAT-LIV-*` | Sovereign named services | 57 |
| `UAT-WF-*` | Core end-to-end workflows | 25 |
| `UAT-BRD-*` | Branding & discoverability | 20 |
| `UAT-MOB-*` | Mobile parity | 15 |
| `UAT-REG-*` | Regression | 25 |
| `UAT-RUN-*` | Runtime / technical (tester-friendly) | 10 |

### Example scenarios (abbreviated)

#### UAT-SHL-002 — Auth hero branding (Critical, New change)

- **Role:** Citizen / reviewer  
- **Steps:** Open `/auth/login` at 375px, 768px, 1280px  
- **Expected:** Hero Impilo logo (`impilo-brand-hero`); page title *Impilo — Health Operating System*  
- **Pass:** Logo readable at all breakpoints  
- **Fail:** Missing/broken branding  
- **Evidence:** Screenshots at 3 widths  
- **Commit evidence:** `82778272`, `23f772cf`

#### UAT-NOM-001 — Nompilo taskbar only (Critical, New change)

- **Role:** Provider  
- **Steps:** Open `/provider-workspace`; inspect chrome  
- **Expected:** No full-width Nompilo strip; taskbar Ask present  
- **Pass:** Clinical content not displaced by Nompilo chrome  
- **Fail:** Full-page Nompilo assistant returns  

#### UAT-WF-010 — Inpatient admission workflow (Critical, New change)

- **Role:** Nurse  
- **Steps:** `/clinical/inpatient/admissions` → nursing task list  
- **Expected:** Live maturity task lists, not static shell  
- **Pass:** Tasks load from BFF  
- **Fail:** Empty stub or 500  

#### UAT-BRD-001 — Vito logo (High, New change)

- **Role:** Reviewer  
- **Steps:** Load `/brand/services/vito-logo.png`; open Vito route  
- **Expected:** 200 PNG; branded header on registry surfaces  

---

## 5. Repeated validation format

Use this structure in an Excel **Repeated Retest Log** sheet when iterating until a feature passes.

| Test ID | Test round | Date/time | Build/commit | Tester | Result | Issue observed | Fix expected | Retest notes | Final sign-off |
|---------|------------|-----------|--------------|--------|--------|----------------|--------------|--------------|----------------|
| UAT-SHL-004 | 1 | | 4917def8 | | Fail | Provider lands on /home | BFF role resolution | | |
| UAT-SHL-004 | 2 | | 4917def8 | | Pass | | | Fixed session doctrine | PO |

**Result values:** `Pass` · `Fail` · `Blocked` · `Not Tested` · `N/A`

**Sign-off:** Product owner initials + date when test round reaches Pass with no open Sev-1/2 defects.

---

## 6. Suggested Excel workbook design

| Sheet | Purpose |
|-------|---------|
| **README / Instructions** | Preview URL, credentials request process, evidence standards, defect severity definitions |
| **Test Summary Dashboard** | Pivot: pass rate by module, priority, role; import from CSV Status column |
| **Module Coverage Matrix** | Section 3 of this document |
| **Detailed Test Scripts** | Import `uat-full-preview-test-scripts-4917def8.csv` |
| **Repeated Retest Log** | Section 5 format |
| **Defect Log** | ID, Test ID, severity, summary, steps, screenshot link, owner, status |
| **Role-Based Scenarios** | Filter CSV by User Role/Persona for daily test assignments |
| **Route/Surface Checklist** | 22 regression routes + 575 route parity reference |
| **Mobile Parity Checklist** | 15 UAT-MOB-* rows + `MOBILE_PARITY_MATRIX` deferred rows |
| **Regression Checklist** | UAT-REG-* + no-stub/parity gate rows |
| **Sign-off Sheet** | PO / clinical lead / admin lead approval with commit SHA |

### Import tip

1. Open CSV in Excel → **Data → From Text/CSV** → UTF-8.  
2. Freeze header row; enable filters on Priority and Test Type.  
3. Conditional format: Critical = red, High = amber.  
4. Link Defect Log `Test ID` to Detailed Test Scripts via VLOOKUP/XLOOKUP.

---

## 7. Priority test list — first product review walkthrough

Execute these **35 must-run tests** (~2–3 hours with credentials) before broader UAT:

| # | Test ID | Why first |
|---|---------|-----------|
| 1 | UAT-RUN-001 | Confirm preview health |
| 2 | UAT-RUN-002 | Record build commit |
| 3 | UAT-SHL-001 | Shell loads |
| 4 | UAT-SHL-002 | Branding visible (new) |
| 5 | UAT-SHL-004 | Provider landing (new) |
| 6 | UAT-SHL-005 | WORK zone (new) |
| 7 | UAT-SHL-008 | Launcher (new) |
| 8 | UAT-NOM-001 | Nompilo not page chrome (new) |
| 9 | UAT-NOM-002 | Ctrl+K palette (new) |
| 10 | UAT-NOM-003 | Ask route |
| 11 | UAT-BRD-001–006 | Vito, Varapi, Tuso, Tshepo, Madi, Fundo logos (new) |
| 12 | UAT-VIT-001 | Client search |
| 13 | UAT-VIT-002 | Intake dedup (new) |
| 14 | UAT-VAR-003 | Provider council (new) |
| 15 | UAT-PCT-001 | Queue (regression) |
| 16 | UAT-WF-006 | Queue → encounter rail |
| 17 | UAT-WF-010 | Inpatient (new) |
| 18 | UAT-ORO-001 | Lab worklist |
| 19 | UAT-MAD-001 | MADI donor (new) |
| 20 | UAT-MAD-002 | Blood bank (new) |
| 21 | UAT-FND-001 | Fundo catalog (new) |
| 22 | UAT-LIV-001 | Impilo Live discover (new) |
| 23 | UAT-COV-001 | Coverage enroll |
| 24 | UAT-SIM-001 | Wellness hub |
| 25 | UAT-MSX-002 | Finance rail |
| 26 | UAT-PH-001 | PH oversight (new) |
| 27 | UAT-ENT-001 | Enterprise dashboard |
| 28 | UAT-SHL-013 | Admin governance (new) |
| 29 | UAT-REG-001–010 | Top 10 route regression |
| 30 | UAT-REG-023 | No-stub guard (script) |
| 31 | UAT-WF-004 | Provider login workflow |
| 32 | UAT-WF-019 | Fundo certificate journey |
| 33 | UAT-WF-020 | Live CPD webinar |
| 34 | UAT-RUN-006 | No obvious 500s |
| 35 | UAT-RUN-010 | Brand assets 200 |

---

## 8. Remaining caveats

| Caveat | Impact on UAT |
|--------|----------------|
| **Auth-gated flows need credentials** | Provider, admin, and facility scenarios require preview test accounts from the team |
| **Seeded clinical data** | Queue, EHR, MADI transfusion, and billing workflows need known test patients/orders |
| **RTC / LiveKit** | Telemedicine and Live room media may show *honest blocked* boundary if RTC gateway DNS unavailable |
| **External integrations** | PACS viewer, MusheX live rails, SMS/email for invitations may be sandbox-limited |
| **Mobile requires device/emulator** | UAT-MOB-* cannot be completed from web-only testing |
| **4917def8 is backend-only** | `/health/version` shows `ba7064e2`; UX validation targets accumulated `ba7064e2` generation |
| **Partial parity rows** | `FRONTEND_BACKEND_PARITY_MATRIX` marks some capabilities *partial* — expect maturity labels, not full production depth |
| **Intentionally deferred mobile** | UBOMI, Zibo, some MADI admin screens marked *intentionally deferred* on mobile |
| **Browser automation gap** | Interactive auth/session flows should be manually walked; automated browser MCP was unavailable during closure |

---

## 9. Test inventory summary

| Metric | Count |
|--------|------:|
| **Total test scenarios** | **180** |
| **Modules / service areas covered** | **47** |
| **Critical priority** | **52** |
| **High priority** | **96** |
| **Regression test type** | **95** |
| **New change test type** | **45** |
| **Mobile test type** | **15** |
| **Smoke test type** | **9** |

### By module (top groups)

| Module group | Scenarios |
|--------------|----------:|
| Core workflows | 25 |
| Regression / routes | 25 |
| Branding | 20 |
| Mobile parity | 15 |
| Shell | 15 |
| Sovereign services (combined) | 57 |
| Nompilo | 8 |
| Runtime | 10 |

---

## 10. After you upload an example Excel template

When you provide an example Excel validation workbook, we can:

1. **Map columns** — align your template's sheet/column names to this CSV (may require column rename script).
2. **Add organisation fields** — facility name, district, province, tester organisation ID if your template requires them.
3. **Wire formulas** — dashboard pass-rate pivots, defect ageing, sign-off gates.
4. **Merge existing checklists** — import rows from `OWNER_PREVIEW_TEST_CHECKLIST.md` (49 rows) without duplicating Test IDs.
5. **Localise** — language columns for clinician-facing step text if needed.
6. **Severity taxonomy** — match your defect severity scale to Fail criteria in CSV.

**Please include in the template:** preferred Test ID format, whether routes or API endpoints need separate columns, and required sign-off roles.

---

## Appendix A — Change-to-test traceability (high level)

| Product change (c5f8ff43..4917def8) | Primary test IDs |
|---------------------------------------|------------------|
| Sovereign service logos | UAT-BRD-001–017 |
| Shell A–D remediation | UAT-SHL-002,004–007, UAT-NOM-001–002 |
| Admin governance bootstrap | UAT-SHL-013, UAT-WF-021, UAT-VAR-003 |
| Booking vs appointment | UAT-TUS-003, UAT-WF-009, UAT-MOB-012 |
| Inpatient depth | UAT-WF-010, UAT-REG-021 |
| MADI full stack | UAT-MAD-*, UAT-MOB-004–005 |
| Impilo Live | UAT-LIV-*, UAT-WF-020, UAT-MOB-008–009 |
| Fundo LMS depth | UAT-FND-*, UAT-WF-019, UAT-MOB-006–007 |
| Gap-closure 14 services | UAT-ORO-*, UAT-PH-*, UAT-ENT-*, UAT-MSX-* |
| Coverage & Simba parity | UAT-COV-*, UAT-SIM-* |
| Trust organogram | UAT-SHL-014, UAT-TSP-* |

---

## Appendix B — Evidence standards

| Evidence type | When required |
|---------------|---------------|
| Screenshot (PNG) | All UI scenarios; annotate unexpected elements |
| URL + timestamp | Every browser test |
| Screen recording (MP4) | Workflows ≥5 steps (WF-*, MAD-*, FND-*) |
| Network HAR | Any Fail due to API error |
| `kubectl` output | UAT-RUN-003, UAT-RUN-004 only (operators) |
| Script log | UAT-REG-023–025 (QA lead) |

Store artifacts in a shared folder keyed by **Test ID + date + tester initials**.

---

*End of UAT pack. Regenerate CSV after route or checklist changes: `python3 scripts/product/generate-uat-full-preview-pack.py`*
