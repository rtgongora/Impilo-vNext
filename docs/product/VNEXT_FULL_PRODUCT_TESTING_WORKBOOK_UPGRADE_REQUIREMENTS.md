# vNext Full Product Testing Workbook Upgrade Requirements

> **Generated:** 2026-06-13  
> **Current pack:** `docs/product/UAT_FULL_PREVIEW_VALIDATION_PACK_4917def8.md` (180 scenarios, 47 modules)  
> **Target:** One workbook row per vNext service/component (146 accountability rows)  
> **Do not generate Excel yet** — requirements only

---

## 1. Purpose

The current UAT pack is strong on shell, branding, regression, and Madi depth — but it covers **47 modules** against **91 registry services** and **146 classified components**. Under the **One Health OS** doctrine, the testing workbook must account for **every vNext service** with explicit user stories, navigation, evidence, and blocker tracking.

**Preview completeness** is not achieved when shell + BFF + core routes work alone. Preview is complete when the **whole product estate** is built, deployed, running, surfaced, testable, and workbook-accounted — or gaps are explicit blockers.

---

## 2. Workbook structure requirements (next version)

### Sheet 1 — Service Accountability Index
One row per component from [`reports/product/vnext-service-accountability-matrix.csv`](../../reports/product/vnext-service-accountability-matrix.csv).

Columns: service slug, plane, deployed, running, shell surfaced, mobile surfaced, BFF configured, UAT module ID, workbook row status, blocker.

### Sheet 2 — User Stories & Definition of Done
Per service:

| Column | Required content |
|--------|------------------|
| User story | As a [role], I need [capability] so that [outcome] |
| Acceptance criteria | Measurable conditions (3–7 bullets) |
| Definition of Done | Build + deploy + run + surface + test + evidence |
| Given/When/Then | BDD format |
| Priority | Critical / High / Medium / Lower walkthrough |
| Regression risk | High/Med/Low + affected routes |

### Sheet 3 — Workflow Navigation (exact paths)
**Madi is the specificity benchmark.** Every service must list exact surfaces — not “blood-specific navigation” generically, but:

#### Madi (benchmark — 11 workbook modules today)

| # | Exact navigation path | Expected result | Evidence |
|---|----------------------|-----------------|----------|
| 1 | `/madi/dashboard` | KPI tiles, 30-day forecast table | Screenshot + API `GET /internal/v1/madi/dashboard` |
| 2 | `/madi/donor/register` → `/madi/donor/profile` | Donor registered, profile visible | Health ID link, donor ID |
| 3 | `/madi/drives` → create drive → field capture | Drive scheduled, mobile sync | Provider mobile if applicable |
| 4 | `/madi/collection` | Collection session recorded | Unit created |
| 5 | `/madi/processing` | Batch status, component labelling | ZIBO SNOMED link works |
| 6 | `/madi/blood-bank/stock` → `/madi/blood-bank/fridges` | Stock balance, fridge IoT readings | Quantity matches seed |
| 7 | `/madi/orders` → crossmatch → reserve → issue | Order lifecycle complete | OROS deep-link if configured |
| 8 | `/madi/transfusion` → observations | Episode documented | VITO verify if configured |
| 9 | `/madi/haemovigilance` → report reaction | Case filed | National roll-up link |
| 10 | `/madi/central-bank` → redistribution | Request/approve flow | Audit event |
| 11 | `/madi/audit` or traceability view | Chain visible | Audit correlation ID |

**Pass rule:** Logged-in click-flow completes with API-backed content — not empty shell.

#### Required specificity for other services (examples)

| Service | Exact navigation entry points |
|---------|------------------------------|
| **Vito** | `/id-services` → search → register → `/registry/clients/[id]` |
| **Varapi** | `/registry/providers` → onboarding → license verify |
| **Tuso** | `/registry/facilities` → import → workspace select |
| **PCT** | `/queue` → walk-in → `/ehr/[cpid]/summary` |
| **OROS** | `/lab/orders` → worklist → result |
| **Mushex** | `/finance/payments` → initiate → status |
| **Indawo** | `/public-health/site-registry` → geo capture map |
| **Fundo** | `/learning` → course → enrol → certificate |
| **Live** | `/live/discover` → join → `/live/admin` host |
| **Simba** | `/monitoring/devices` → pair → sync |
| **workforce-governance** | WORK tab → assignment visible after login |

Every remaining service gets the same level of path specificity in the workbook.

### Sheet 4 — Role & Context Matrix
Per scenario:

| Field | Examples |
|-------|----------|
| Role | Provider, Citizen, Admin, PH officer, Finance |
| Credentials | `superadmin@impilo.gov.zw`, seeded clinicians |
| Context | Facility ID `1`, Provider `PROV-ZW-ADMIN-001` |
| Preconditions | Sovereign seed applied, Keycloak grant |

### Sheet 5 — Test Data & Seed Requirements
Link to:
- `scripts/deploy/seed-full-preview-sovereign-data.sh`
- Per-service seed SQL
- Keycloak realm users/roles

### Sheet 6 — Keycloak & Trust Tests
| Scenario | Given | When | Then |
|----------|-------|------|------|
| Login web | Valid user | OAuth flow | Session in shell |
| Registration | Citizen form | Submit | Health ID created (not 403) |
| WORK tab | Provider + assignment | Open WORK | Active assignment shown |
| Break-glass | Emergency role | Request | Audit event |
| Mobile login | Device | OIDC | Token in app |

### Sheet 7 — BFF Orchestration Tests
| Scenario | Verify |
|----------|--------|
| No localhost in BFF pod | Env inspection |
| VARAPI lookup | 200 + providerPublicId |
| Facilities live mode | TUSO data (not stub) |
| Madi proxy | Dashboard 200 |
| Launcher apps | Role-filtered list |

### Sheet 8 — API-Backed Rendering Tests
For each partial parity row in `FRONTEND_BACKEND_PARITY_MATRIX.md`:
- Network tab: BFF call returns real data
- UI shows data or honest `Blocked` label
- No `JSON.stringify` debug pages

### Sheet 9 — External Integration Fallback Tests
| Integration | Adapter | External down behaviour |
|-------------|---------|-------------------------|
| PACS | pacs-adapter-service | Worklist empty + label |
| LiveKit | rtc-gateway | Telemedicine blocked state |
| eLMIS | inventory-elmis-adapter | Honest adapter error |
| LLM | llm-orchestration | Nompilo offline fallback label |

### Sheet 10 — Mobile Execution Tests
From `MOBILE_PARITY_MATRIX.md` — 15 existing scenarios expanded to per-service mobile paths.

Citizen and provider app screens with BFF mobile routes.

### Sheet 11 — Blocker Tracking
| Blocker type | Example | Owner | Resolution |
|--------------|---------|-------|------------|
| defect: not deployed | — | ops | helm enable |
| gap: not surfaced | audit-ledger | experience | ops route |
| gap: not in workbook | product-registry | QA | add module |
| blocked: seed missing | clinical queue | data | run seed script |
| blocked: external down | RTC | integration | document + UI label |

### Sheet 12 — Evidence & Pass/Fail Log
Extend [`reports/product/uat-full-preview-retest-log-template.csv`](../../reports/product/uat-full-preview-retest-log-template.csv):

- Screenshot path
- Screen recording path
- HAR/network capture
- `/health/version` commit at test time
- Pass/fail/disabled-with-reason
- Tester, date, build SHA

---

## 3. Gap analysis — current pack vs required

| Dimension | Current (`4917def8`) | Required |
|-----------|---------------------|----------|
| Total scenarios | 180 | ≥1 per service (146+) + workflow depth |
| Modules | 47 | 91 service modules + 12 supporting |
| Services without module | ~44 | 0 (blocker tracked) |
| User stories | Implicit in scenarios | Explicit per service |
| DoD | Partial | Formal per service |
| Given/When/Then | Some scenarios | All critical + High |
| Exact navigation | Strong for Madi | Madi-level for all |
| Keycloak tests | 3 shell auth | Full trust matrix |
| BFF tests | 2 runtime | Orchestration sheet |
| Mobile | 15 scenarios | Per partial parity row |
| Blocker column | Limited | Mandatory |

---

## 4. Pass/fail rules (global)

| Result | Rule |
|--------|------|
| **PASS** | Logged-in click-flow completes; API returns expected data; evidence captured |
| **PASS (degraded)** | Honest blocked/fallback label shown; documented external dep down |
| **FAIL** | Dead click, blank page, silent no-op, stub without label |
| **BLOCKED** | Cannot test — seed, credentials, or deploy gap — must cite blocker |
| **N/A** | Supporting library only — link to parent service test |

**“Page opens” alone is FAIL** for functional scenarios.

---

## 5. Regeneration pipeline requirements

1. `python3 scripts/architecture/generate-service-accountability-matrix.py` — accountability CSV
2. `python3 scripts/product/generate-uat-full-preview-pack.py` — extend to read accountability CSV
3. Merge parity matrices (`FRONTEND_BACKEND`, `MOBILE`) into workbook rows
4. Version workbook with commit SHA (`uat-full-preview-test-summary-{sha}.json`)
5. Align with `report-preview-generation.sh` output

---

## 6. Priority order for workbook expansion

### Wave A — Critical path (already partially covered)
Shell auth, WORK, launcher, Nompilo, Vito, Varapi, Tuso, PCT, Madi

### Wave B — Clinical & enterprise
OROS, pharmacy, inpatient, Mushex, Costa, Msika, Simba, coverage

### Wave C — Public health & data
Indawo, surveillance, campaigns, NDR, pipeline, reporting

### Wave D — Platform & integration (currently under-tested)
audit-ledger, integration-hub, workflow, observability, jobs, offline-sync, product-registry, workforce-governance, share-slip, referral

### Wave E — Mobile parity
All `partial` and `missing` rows in `MOBILE_PARITY_MATRIX.md`

---

## 7. Acceptance criteria for workbook upgrade (meta-DoD)

The workbook upgrade is complete when:

1. Every row in accountability CSV has a workbook module ID
2. Every module has user story + DoD + Given/When/Then
3. Every runtime service has exact navigation paths (Madi benchmark)
4. Keycloak/trust sheet covers all browser and service-account clients
5. BFF orchestration sheet covers all `SERVICE_ENV` mappings + gaps
6. Blocker sheet has zero untracked “optional” labels
7. Retest log template includes evidence columns
8. Product owner can execute walkthrough without asking “is this optional?”

---

## References

| Artifact | Path |
|----------|------|
| Current UAT pack | `docs/product/UAT_FULL_PREVIEW_VALIDATION_PACK_4917def8.md` |
| Test scripts CSV | `reports/product/uat-full-preview-test-scripts-4917def8.csv` |
| Summary JSON | `reports/product/uat-full-preview-test-summary-4917def8.json` |
| Retest template | `reports/product/uat-full-preview-retest-log-template.csv` |
| Accountability CSV | `reports/product/vnext-service-accountability-matrix.csv` |
| Madi parity rows | `docs/architecture/FRONTEND_BACKEND_PARITY_MATRIX.md` (rows 32–43) |
