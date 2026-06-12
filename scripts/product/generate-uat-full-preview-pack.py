#!/usr/bin/env python3
"""Generate Excel-ready CSV of UAT scenarios for full-preview validation pack."""
from __future__ import annotations

import csv
import json
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OUT_CSV = REPO / "reports/product/uat-full-preview-test-scripts-4917def8.csv"
OUT_SUMMARY = REPO / "reports/product/uat-full-preview-test-summary-4917def8.json"

COLUMNS = [
    "Test ID",
    "Module/Service",
    "User Role/Persona",
    "Test Scenario",
    "Preconditions",
    "Test Steps",
    "Expected Result",
    "Pass Criteria",
    "Fail Criteria",
    "Evidence To Capture",
    "Priority",
    "Test Type",
    "Related Route/Surface",
    "Related Commit/Change Evidence",
    "Status",
    "Tester Comments",
    "Defect Reference",
    "Retest Result",
]


@dataclass
class Scenario:
    test_id: str
    module: str
    role: str
    scenario: str
    preconditions: str
    steps: str
    expected: str
    pass_criteria: str
    fail_criteria: str
    evidence: str
    priority: str
    test_type: str
    route: str
    commit_evidence: str


def s(**kwargs) -> Scenario:
    return Scenario(**kwargs)


def shell_scenarios() -> list[Scenario]:
    ev = "5fa3f2e3,8510069f,8629e068; OWNER_PREVIEW_TEST_CHECKLIST A–D"
    return [
        s(test_id="UAT-SHL-001", module="Shell / Landing", role="Any user", scenario="Preview URL loads Impilo experience shell",
          preconditions="Browser; network access to preview", steps="1. Open http://41.57.127.235\n2. Wait for shell load",
          expected="Landing or auth redirect; Impilo title/branding visible; no 502/503",
          pass_criteria="Page loads within 10s; Impilo branding present", fail_criteria="Blank page, 5xx, or wrong product",
          evidence="Screenshot + URL + timestamp", priority="Critical", test_type="Smoke", route="/",
          commit_evidence="61c767bd feat(shell): shell A-D remediation"),
        s(test_id="UAT-SHL-002", module="Shell / Auth", role="Citizen / reviewer", scenario="Sign-in page shows hero Impilo branding",
          preconditions="None", steps="1. Open /auth/login\n2. View at 375px, 768px, 1280px widths",
          expected="Hero logo visible (data-testid impilo-brand-hero); title 'Impilo — Health Operating System'",
          pass_criteria="Logo readable at all breakpoints; no clipped branding", fail_criteria="Missing logo, tiny mark only, broken image",
          evidence="Screenshots at 3 widths", priority="Critical", test_type="New change", route="/auth/login",
          commit_evidence="82778272,23f772cf feat(branding); e2e auth-hero-logo"),
        s(test_id="UAT-SHL-003", module="Shell / Auth", role="Citizen", scenario="Client self-registration entry",
          preconditions="Registration enabled in preview", steps="1. Open /auth/register\n2. Complete or attempt registration form",
          expected="Form loads; success or explicit governed error (not silent failure)",
          pass_criteria="Clear outcome message; no orphan account on role failure", fail_criteria="Silent failure, 500, mock-only form",
          evidence="Screenshot + network tab on submit", priority="High", test_type="Regression", route="/auth/register",
          commit_evidence="f902342f wire patient search/consent journeys"),
        s(test_id="UAT-SHL-004", module="Shell / Auth", role="Provider", scenario="Provider ID login lands WORK context",
          preconditions="Valid provider test credentials", steps="1. Login via provider ID path\n2. Observe post-auth redirect",
          expected="Redirect via /auth/resolving to /provider-workspace (or facility selection if unassigned)",
          pass_criteria="Provider not dropped on citizen /home by default", fail_criteria="Citizen home default for provider",
          evidence="Screen recording of login funnel", priority="Critical", test_type="New change", route="/provider-workspace",
          commit_evidence="074f08a1 trust session doctrine; e2e provider-login-flow"),
        s(test_id="UAT-SHL-005", module="Shell / Navigation", role="Provider", scenario="WORK zone visible in navigation",
          preconditions="Authenticated provider", steps="1. After login inspect sidebar/top nav\n2. Open WORK-labelled areas",
          expected="WORK zone routes accessible; clinical/operations items visible per role",
          pass_criteria="WORK navigation renders and routes load", fail_criteria="WORK missing or empty for provider",
          evidence="Screenshot of nav + one WORK route", priority="Critical", test_type="New change", route="/work/*",
          commit_evidence="e586d255 UI surfacing hotspots"),
        s(test_id="UAT-SHL-006", module="Shell / Navigation", role="Provider", scenario="MY PROFESSIONAL mode accessible",
          preconditions="Authenticated provider", steps="1. Locate MY PROFESSIONAL entry (mode/tab)\n2. Switch to it",
          expected="Professional context surfaces; does not replace WORK as default for active clinical session",
          pass_criteria="Mode switch works; content loads", fail_criteria="Mode missing or crashes shell",
          evidence="Screenshot before/after switch", priority="High", test_type="New change", route="/provider-workspace",
          commit_evidence="074f08a1 three-tab session doctrine"),
        s(test_id="UAT-SHL-007", module="Shell / Navigation", role="Citizen", scenario="MY LIFE / citizen home surfaces",
          preconditions="Citizen account", steps="1. Login as citizen\n2. Open home / My Life areas",
          expected="Citizen-appropriate tiles; wellness, live, social where entitled",
          pass_criteria="Home loads with citizen modules", fail_criteria="Provider-only shell or blank home",
          evidence="Screenshot of citizen home", priority="High", test_type="Regression", route="/home",
          commit_evidence="bc617034 Phase 7 consumer journeys"),
        s(test_id="UAT-SHL-008", module="Shell / Launcher", role="Provider / Admin", scenario="Start menu / app launcher opens",
          preconditions="Authenticated user with launcher entitlement", steps="1. Open start menu (shell launcher)\n2. Search for a known service",
          expected="Launcher lists apps from BFF; search filters tiles",
          pass_criteria="Launcher opens; apps listed with names", fail_criteria="Empty launcher or static-only list",
          evidence="Screenshot of launcher + search", priority="Critical", test_type="New change", route="ShellStartMenu",
          commit_evidence="FRONTEND_BACKEND_PARITY_MATRIX Health OS Launcher"),
        s(test_id="UAT-SHL-009", module="Shell / Launcher", role="System administrator", scenario="Role/facility-aware launcher filtering",
          preconditions="Admin + provider test accounts", steps="1. Compare launcher tiles admin vs provider\n2. Change facility context if available",
          expected="Tile visibility changes by role/context",
          pass_criteria="Different roles see appropriate subset", fail_criteria="Identical unrestricted list for all roles",
          evidence="Side-by-side screenshots", priority="High", test_type="Integration", route="/internal/v1/launcher/apps",
          commit_evidence="b0c6d917 admin-governance bootstrap"),
        s(test_id="UAT-SHL-010", module="Shell / Context", role="Facility clerk", scenario="Facility context selection persists",
          preconditions="User with multi-facility access", steps="1. Select facility/workspace\n2. Navigate 3 routes\n3. Refresh page",
          expected="Facility context retained; headers sent on API calls",
          pass_criteria="Facility badge visible; APIs not 403 for valid context", fail_criteria="Context lost on navigation",
          evidence="Screenshot + sample API request headers", priority="High", test_type="Regression", route="/facility",
          commit_evidence="325fd97c facility master import"),
        s(test_id="UAT-SHL-011", module="Shell / Navigation", role="Any authenticated", scenario="Back navigation and deep links",
          preconditions="Logged in", steps="1. Deep link to /registry/providers\n2. Use browser back\n3. Forward again",
          expected="Routes load without shell crash; back stack sane",
          pass_criteria="No infinite loading; no 404 on known routes", fail_criteria="Shell white screen or route 404",
          evidence="Short screen recording", priority="Medium", test_type="Regression", route="multiple",
          commit_evidence="575 routes parity check"),
        s(test_id="UAT-SHL-012", module="Shell / UX", role="Reviewer", scenario="Responsive layout on mobile width",
          preconditions="Browser devtools", steps="1. Set 375px width\n2. Visit /auth/login, /home, /provider-workspace",
          expected="Usable layout; nav collapses appropriately",
          pass_criteria="No horizontal scroll on primary pages", fail_criteria="Overlapping UI or unusable controls",
          evidence="375px screenshots", priority="High", test_type="Usability", route="multiple",
          commit_evidence="OWNER_PREVIEW_TEST_CHECKLIST shell A"),
        s(test_id="UAT-SHL-013", module="Shell / Admin Governance", role="System administrator", scenario="Bootstrap onboarding and invitations",
          preconditions="SUPER_ADMIN or bootstrap mode", steps="1. Open /work/administration-governance\n2. Review invitation lifecycle UI\n3. Attempt invite flow if permitted",
          expected="Invitation list, status, activation wiring visible",
          pass_criteria="UI loads with real BFF data or honest empty", fail_criteria="Static mock table or 500",
          evidence="Screenshot + API response sample", priority="High", test_type="New change",
          route="/work/administration-governance", commit_evidence="fc30788d,a94e4fba,b0c6d917 admin-governance"),
        s(test_id="UAT-SHL-014", module="Shell / Trust", role="Trust administrator", scenario="Three-tab session doctrine (organogram)",
          preconditions="Trust admin role", steps="1. Open trust/session admin surfaces\n2. Verify organogram seed catalogues present",
          expected="MoHCC organogram data navigable in trust admin",
          pass_criteria="Catalogues render", fail_criteria="Missing trust admin or empty stubs",
          evidence="Screenshot", priority="Medium", test_type="New change", route="/admin/trust",
          commit_evidence="074f08a1 feat(trust) organogram"),
        s(test_id="UAT-SHL-015", module="Shell / Errors", role="Any user", scenario="Empty and error states are understandable",
          preconditions="Route with no data (new facility)", steps="1. Open list page with no records\n2. Simulate API failure if possible",
          expected="Empty state message; errors not raw stack traces",
          pass_criteria="Human-readable messaging", fail_criteria="JSON error blob or blank panel",
          evidence="Screenshot of empty/error state", priority="Medium", test_type="Usability", route="varies",
          commit_evidence="dab4ada7 wellness honest fields"),
    ]


def nompilo_scenarios() -> list[Scenario]:
    return [
        s(test_id="UAT-NOM-001", module="Nompilo", role="Provider", scenario="Nompilo accessible from taskbar only (not full-page strip)",
          preconditions="Authenticated provider on workspace", steps="1. Open /provider-workspace\n2. Inspect page chrome above content",
          expected="No full-width Nompilo strip; taskbar Ask entry present",
          pass_criteria="Content area not dominated by Nompilo chrome", fail_criteria="ProactiveAssistant full-width strip returns",
          evidence="Screenshot annotated", priority="Critical", test_type="New change", route="/provider-workspace",
          commit_evidence="OWNER_PREVIEW_TEST_CHECKLIST B; 8629e068 shell remediation"),
        s(test_id="UAT-NOM-002", module="Nompilo", role="Any authenticated", scenario="Ctrl+K opens Nompilo command palette",
          preconditions="Logged in", steps="1. Press Ctrl+K (Cmd+K on Mac)\n2. Type a help query",
          expected="Palette/slide-over opens; query accepted",
          pass_criteria="Palette visible; dismissible", fail_criteria="No shortcut or page navigation only",
          evidence="Screenshot of palette", priority="Critical", test_type="New change", route="global command bar",
          commit_evidence="bc617034 Nompilo Phase 7"),
        s(test_id="UAT-NOM-003", module="Nompilo", role="Citizen", scenario="Ask Nompilo full route",
          preconditions="Citizen login optional", steps="1. Open /ask\n2. Submit a health question",
          expected="Guidance/chat UI loads; response or honest offline fallback",
          pass_criteria="Interactive UI; no stub placeholder page", fail_criteria="Static 'coming soon' only",
          evidence="Screenshot + response text", priority="High", test_type="Regression", route="/ask",
          commit_evidence="FRONTEND_BACKEND_PARITY Nompilo"),
        s(test_id="UAT-NOM-004", module="Nompilo", role="Provider", scenario="Contextual help during clinical workflow",
          preconditions="Active encounter open", steps="1. Open encounter page\n2. Invoke Nompilo with patient context if supported",
          expected="Assist respects context; does not override clinical judgement copy",
          pass_criteria="Contextual prompt or honest boundary label", fail_criteria="Generic unrelated answers presented as clinical orders",
          evidence="Screenshot + disclaimer visible", priority="High", test_type="Integration", route="/ehr/[patientId]/encounter",
          commit_evidence="PRODUCT_OWNER_TEST_SCRIPTS encounter journey"),
        s(test_id="UAT-NOM-005", module="Nompilo", role="Reviewer", scenario="Nompilo branding logo renders",
          preconditions="None", steps="1. Open Nompilo entry points\n2. Check /brand/services/nompilo-logo.png",
          expected="Logo loads 200; fallback icon only on failure",
          pass_criteria="PNG logo visible", fail_criteria="Broken image icon on all surfaces",
          evidence="Screenshot + network 200 for logo", priority="Medium", test_type="New change", route="/brand/services/nompilo-logo.png",
          commit_evidence="23f772cf sovereign logo coverage"),
        s(test_id="UAT-NOM-006", module="Nompilo", role="Accessibility reviewer", scenario="Keyboard access to Nompilo",
          preconditions="Keyboard only navigation", steps="1. Tab to taskbar Ask\n2. Activate with Enter\n3. Escape to close",
          expected="Focusable control; ESC closes overlay",
          pass_criteria="Keyboard operable", fail_criteria="Mouse-only access",
          evidence="Short recording or checklist", priority="Medium", test_type="Usability", route="taskbar",
          commit_evidence="shell A-D requirements"),
        s(test_id="UAT-NOM-007", module="Nompilo", role="Provider", scenario="Compact interaction does not block primary workflow",
          preconditions="On queue or EHR page", steps="1. Open Nompilo\n2. Continue clinical task underneath",
          expected="Overlay/panel; underlying page still reachable when closed",
          pass_criteria="Workflow continues after close", fail_criteria="Full page takeover requiring reload",
          evidence="Before/after screenshots", priority="High", test_type="New change", route="overlay",
          commit_evidence="8629e068 shell remediation"),
        s(test_id="UAT-NOM-008", module="Nompilo", role="Citizen", scenario="Social composer assist boundary",
          preconditions="Social route access", steps="1. Open social composer if available\n2. Use assist feature",
          expected="Assist with deterministic fallback when LLM unavailable",
          pass_criteria="Honest fallback label if offline", fail_criteria="Fake live AI label when offline",
          evidence="Screenshot of fallback message", priority="Low", test_type="Integration", route="/social",
          commit_evidence="AGENTS.md social composer assist"),
    ]


def sovereign_service_scenarios() -> list[Scenario]:
    services = [
        ("VIT", "Vito / Client Registry", "Facility clerk", "/registry/clients, /operations/vito, /id-services",
         "6ce9894c vito issuance queue; f902342f patient search", [
            ("Search and open client record", "Search known test client; open profile", "Profile loads with Health ID context"),
            ("Client intake dedup wizard", "Open /registry/intake or /registry/clients/new", "Dedup search → score → merge path visible"),
            ("Guardian/emergency provisional intake", "Use provisional intake panels", "Honest BFF create/intake session"),
         ]),
        ("VAR", "Varapi / Provider Registry", "Registry officer", "/registry/providers",
         "fc30788d admin-governance; FRONTEND_BACKEND_PARITY VARAPI", [
            ("Provider search and profile", "Search provider; open detail", "License/privilege sections visible"),
            ("Provider ID application queue", "Open verification queue", "Pending/approved/rejected tabs"),
            ("Council workspace", "Open /registry/provider-council/council-workspace", "Review actions available"),
         ]),
        ("TUS", "Tuso / Facility Registry", "Facility manager", "/registry/facilities, /facility",
         "325fd97c facility master import; cdd39c0c facility discovery", [
            ("Facility search and detail", "Search facility; open operating model", "Facility metadata loads"),
            ("Workspace context binding", "Select department/ward if shown", "Context reflected in shell badge"),
            ("Booking vs appointment honesty", "Open scheduling surfaces", "Booking-service distinction surfaced"),
         ]),
        ("TSP", "Tshepo / Trust", "Trust administrator", "/registry/trust, /admin/trust, /work/system-admin/tshepo",
         "074f08a1 trust organogram; FRONTEND_BACKEND_PARITY TSHEPO", [
            ("Trust policies view", "Open trust admin", "Policies/audit sections load"),
            ("Consent admin", "Open /admin/consent", "Consent governance UI"),
            ("Break-glass request panel", "From ED or EHR emergency view", "Request form submits or shows boundary"),
         ]),
        ("BUT", "Butano / SHR", "Clinician", "/operations/butano, /ehr/[patientId]",
         "FRONTEND_BACKEND_PARITY BUTANO complete", [
            ("Patient chart summary", "Open EHR for test patient", "Summary/timeline/allergies load"),
            ("Queue search CPID-only", "Search from /queue/search", "No PII leakage in SHR views"),
         ]),
        ("UBO", "Ubomi / CRVS", "Civil registrar", "/ubomi",
         "FRONTEND_BACKEND_PARITY UBOMI partial", [
            ("Births/deaths registry entry", "Open Ubomi hub", "CRVS workflows reachable"),
            ("Verify event", "Run verification action if seeded", "Explicit result or honest blocked"),
         ]),
        ("ZIB", "Zibo / Terminology", "Terminology curator", "/registry/terminology",
         "FRONTEND_BACKEND_PARITY ZIBO", [
            ("Code system browse", "Open terminology hub", "Artifacts/packs list or maturity label"),
         ]),
        ("MSK", "Msika", "Procurement officer", "/marketplace, /registry/products",
         "FRONTEND_BACKEND_PARITY Msika", [
            ("Catalog browse", "Open marketplace", "Products list with honest blocked states if needed"),
            ("Order flow entry", "Start order if entitled", "Real BFF or explicit blocked UX"),
         ]),
        ("IND", "Indawo / Public Health Sites", "PH officer", "/public-health/site-registry",
         "cdd39c0c enrich facility discovery; FRONTEND_BACKEND_PARITY Indawo complete", [
            ("Site registry list", "Open site registry", "Sites load with geo fields"),
            ("Ndila map on site panel", "Open geography/map tab", "Map markers render"),
         ]),
        ("PCT", "PCT / Patient Care Tracker", "Clinician", "/queue, /clinical/control-tower",
         "1117ccdb PCT-backed core transactions; ae2c5fe5 scheduling", [
            ("Queue waiting list", "Open /queue", "Waiting list with call-next"),
            ("Walk-in check-in", "Check in patient", "Booking-first check-in correlation"),
            ("ED triage operations", "Open ED visit ops", "Triage engine UI loads"),
         ]),
        ("CST", "Costa / Costing", "Finance clerk", "/finance/costa, /enterprise/oversight",
         "236b1642 wave-1 finance; preview-validation Costa", [
            ("Encounter billing trigger", "Open Costa encounter billing", "Billing panel loads"),
            ("National revenue oversight", "Open /enterprise/oversight", "Revenue KPI tiles visible"),
         ]),
        ("MSX", "MusheX / Payments", "Finance officer", "/finance/mushex-platform, /wallet, /finance",
         "4d53e5cd MusheX BFF; b2fcdf06 finance parity", [
            ("Wallet / payment intent", "Create payment intent in council self-service", "Intent → sync payment flow"),
            ("Finance journeys rail", "Open /finance", "Journeys rail with MusheX links"),
            ("Remittance hub", "Open /finance/remittances", "Settlement data feed"),
         ]),
        ("ORO", "OROS / Orders & Lab", "Lab technician", "/lab, /lab/worklist, /lab/catalog",
         "414db3ba lab orders; 9fb15722 imaging orders lane", [
            ("Lab worklist live data", "Open /lab/worklist", "Items list not zero-only shell"),
            ("Multi-type orchestration hub", "Open /lab filters LAB/IMAGING/PHARMACY/BLOOD", "Filters work"),
            ("Catalog and reconciliation", "Open /lab/catalog and /lab/reconciliation", "Live BFF data not static categories"),
         ]),
        ("SIM", "Simba / Wellness", "Citizen", "/wellness",
         "b2fcdf06 Simba wellness parity; OWNER_PREVIEW W1-W7", [
            ("Wellness goals journey", "Open wellness hub", "Goals/activity/diet surfaces"),
            ("Screening programmes", "Open /wellness/screenings", "Programme rows from Simba"),
            ("Commodities inventory-backed", "Open /wellness/commodities", "Stock management embed"),
         ]),
        ("NDL", "Ndila / Maps", "PH analyst", "/ndila",
         "325fd97c Ndila maps wave; FRONTEND_BACKEND_PARITY Ndila complete", [
            ("Geocode/map load", "Open Ndila hub", "MapLibre map renders"),
            ("Coverage geography tab", "From /coverage intelligence geography", "Geography map loads"),
         ]),
        ("NHM", "Nhume / Dispatch", "Logistics coordinator", "/nhume, /operations/dispatch",
         "FRONTEND_BACKEND_PARITY Nhume", [
            ("Dispatch task list", "Open dispatch ops", "Tasks load or maturity label"),
            ("MADI blood logistics boundary", "From blood-bank logistics panel", "Nhume dispatch boundary visible"),
         ]),
        ("FND", "Fundo / Learning", "Learner / trainer", "/learning, /learning/catalog, /learning/admin",
         "8629e068 Fundo UX; 61581828 pilot-ready; f68365a4 certificate digest", [
            ("Catalog enrolment journey", "Browse catalog → enrol → lesson", "Progress tracked"),
            ("Assessment and certificate", "Complete assessment; view certificate", "SHA-256 digest on certificate"),
            ("Admin moderation queue", "Open /learning/admin/moderation", "Queue loads"),
            ("CPD council bridge", "Self-service CPD flow", "Council CPD bridge UI"),
         ]),
        ("MAD", "Madi / Blood Transfusion", "Clinician / donor", "/madi, /madi/donor, /madi/blood-bank",
         "1c462ece–c529b59d MADI full stack; 45206ad1 mobile depth", [
            ("Donor hub", "Open /madi/donor", "Register/drives/feedback"),
            ("Blood bank stock", "Open /madi/blood-bank", "Orders/stock/crossmatch/issue"),
            ("Transfusion episode", "Open /madi/transfusion", "Observation capture UI"),
            ("Central bank coordination", "Open /madi/central-bank", "National metrics + redistribution"),
            ("Haemovigilance", "Open /madi/haemovigilance", "Adverse reaction reporting"),
         ]),
        ("PCS", "PACS / Imaging", "Radiographer", "/imaging/worklist, /ehr/[patientId]/imaging",
         "9fb15722 imaging orders; preview-validation PACS", [
            ("Imaging worklist", "Open /imaging/worklist", "Studies list loads"),
            ("EHR imaging viewer", "Open patient imaging tab", "Honest viewer boundary if no PACS link"),
         ]),
        ("COV", "Coverage Service", "Enrollment officer", "/coverage, /coverage/enroll, /coverage/member",
         "b2fcdf06 coverage parity; OWNER_PREVIEW C0-C5", [
            ("Enrollment journey", "Run enroll flow", "Plan → eligibility → member"),
            ("Subsidies tab", "Open subsidies", "SUB-MOHCC-PRIMARY or live row"),
         ]),
        ("PH", "Public Health", "PH officer", "/public-health, /public-health/oversight",
         "0089211a PH parity; e586d255 operator gaps", [
            ("National oversight drill-down", "Open /public-health/oversight", "District/province drill-down"),
            ("Field task queue", "Open field ops queue", "Tasks list loads"),
            ("Surveillance/campaigns", "Open surveillance surfaces", "Investigation/campaign UI"),
         ]),
        ("ENT", "Enterprise Plane", "Operations manager", "/enterprise, /enterprise/warehousing",
         "e586d255 enterprise gaps; preview-validation enterprise", [
            ("Enterprise dashboard drill-down", "Open /enterprise", "KPI tiles clickable"),
            ("Warehousing inventory KPIs", "Open /enterprise/warehousing", "Inventory metrics"),
            ("Charge-sheet consumables", "Open charge-sheet with consumables panel", "Cost panel visible"),
         ]),
        ("LIV", "Impilo Live", "Citizen / trainer", "/live, /live/discover, /live/manage",
         "740e808b–77544580 live modes; d04ffba6 web experience", [
            ("Live hub discover", "Open /live/discover", "Events list with seed data"),
            ("CPD webinar journey", "Register → room → attendance", "CPD cert path"),
            ("Replay after event", "Open replay for ended event", "Watch minutes tracked"),
            ("Organiser analytics", "Open /live/manage → analytics", "Analytics dashboard"),
         ]),
    ]
    out: list[Scenario] = []
    for code, module, role, routes, commit, cases in services:
        for i, (title, steps, expected) in enumerate(cases, 1):
            out.append(s(
                test_id=f"UAT-{code}-{i:03d}",
                module=module,
                role=role,
                scenario=title,
                preconditions=f"Preview full stack up; appropriate {role} credentials; seeded test data where noted",
                steps=f"1. Navigate to related route ({routes})\n2. {steps}",
                expected=expected,
                pass_criteria=f"{expected}; no mock-only shell; understandable errors if backend down",
                fail_criteria="404 on known route, static stub shell, 500 without message, or missing sovereign branding",
                evidence="Screenshot + URL + optional network HAR for write actions",
                priority="Critical" if i == 1 else "High",
                test_type="New change" if "dedup" in title.lower() or "council" in title.lower() else "Regression",
                route=routes.split(",")[0].strip(),
                commit_evidence=commit,
            ))
    return out


def workflow_scenarios() -> list[Scenario]:
    workflows = [
        ("WF-001", "Client self-registration", "Citizen", "/auth/register → /home", "Citizen limited My Life access",
         "f902342f", "Critical", "New change"),
        ("WF-002", "Assisted facility client registration", "Facility clerk", "/registry/intake", "Client created with dedup checks",
         "OWNER_PREVIEW 28-29", "Critical", "New change"),
        ("WF-003", "Provider ID request and approval", "Provider applicant", "/registry/providers apply flow", "Application queued and reviewable",
         "fc30788d", "High", "New change"),
        ("WF-004", "Provider login to WORK", "Provider", "Provider ID login funnel", "Lands /provider-workspace",
         "074f08a1", "Critical", "New change"),
        ("WF-005", "Facility context selection", "Provider", "Facility picker", "Context persists across routes",
         "325fd97c", "High", "Regression"),
        ("WF-006", "PCT queue walk-in to encounter", "Clinician", "/queue → /ehr encounter", "Core transaction rail visible",
         "e0429eac,1117ccdb", "Critical", "Integration"),
        ("WF-007", "ED triage to teleconsult escalation", "Clinician", "/queue/triage → /telemedicine/new", "Escalation deep-link works",
         "63be5f13,31789b46", "High", "New change"),
        ("WF-008", "Telemedicine session RTC", "Clinician", "/telemedicine/session/{id}", "Token provision or honest RTC boundary",
         "preview-validation telemedicine", "High", "Integration"),
        ("WF-009", "Appointment booking vs check-in", "Clinician", "Scheduling surfaces", "Booking-first check-in correlation",
         "fe1f6c02,ae2c5fe5", "High", "New change"),
        ("WF-010", "Inpatient admission and nursing tasks", "Nurse", "/clinical/inpatient/admissions, /nursing", "Task lists maturity=live",
         "28335c71,1877a505", "Critical", "New change"),
        ("WF-011", "OROS lab order to worklist", "Lab tech", "Order → /lab/worklist", "Order appears in worklist",
         "414db3ba", "High", "Integration"),
        ("WF-012", "PACS imaging order lane", "Clinician", "Encounter imaging panel", "IMAGING orders lane active",
         "9fb15722", "High", "New change"),
        ("WF-013", "Madi order to transfusion", "Clinician", "/madi/orders → /madi/transfusion", "Episode capture works",
         "c529b59d", "High", "Integration"),
        ("WF-014", "Simba wellness screening enrolment", "Citizen", "/wellness/screenings", "Programme enrolment",
         "b2fcdf06", "Medium", "Regression"),
        ("WF-015", "MusheX payment on council fee", "Provider", "Council self-service payment", "Intent sync payment completes or honest block",
         "4d53e5cd", "High", "Integration"),
        ("WF-016", "Costa encounter billing", "Finance", "/finance/costa/encounter/{id}", "Billing trigger available",
         "preview-validation Costa", "High", "Regression"),
        ("WF-017", "Coverage enroll to member card", "Officer", "/coverage/enroll → /coverage/member", "Member record visible",
         "b2fcdf06", "High", "Regression"),
        ("WF-018", "Public health site capture with Ndila", "PH officer", "/public-health/site-registry", "Geo capture on map",
         "cdd39c0c", "Medium", "Integration"),
        ("WF-019", "Fundo learn to certificate", "Learner", "/learning/catalog full journey", "Certificate with digest",
         "8629e068", "High", "New change"),
        ("WF-020", "Impilo Live CPD webinar", "Professional", "/live/discover CPD event", "Attendance → CPD cert",
         "77544580", "High", "New change"),
        ("WF-021", "Admin governance invitation", "System admin", "/work/administration-governance", "Invite lifecycle works",
         "fc30788d", "High", "New change"),
        ("WF-022", "Encounter care chain rail", "Clinician", "EHR encounter page", "OROS+Costa+MusheX counts on rail",
         "OWNER_PREVIEW 35", "High", "New change"),
        ("WF-023", "Pharmacy five-rights dispense", "Pharmacist", "Pharmacy dispense path", "Five-rights verification in BFF path",
         "603b22c0", "High", "New change"),
        ("WF-024", "Social timeline post", "Citizen", "/social", "Feed loads real data",
         "bc617034", "Medium", "Regression"),
        ("WF-025", "Nompilo assist during workflow", "Provider", "Any clinical page + Nompilo", "Assist without blocking workflow",
         "8629e068", "Medium", "Usability"),
    ]
    return [
        s(test_id=f"UAT-{wid}", module="Core Workflow", role=role, scenario=name,
          preconditions="Authenticated appropriate persona; seeded test data where required",
          steps=f"1. Start at entry route\n2. Execute workflow steps end-to-end\n3. Verify completion state",
          expected=expected,
          pass_criteria=f"Workflow completes or shows governed honest boundary; {expected}",
          fail_criteria="Silent failure, mock data presented as live, or workflow unreachable",
          evidence="Screen recording recommended; API correlation ID for writes",
          priority=priority, test_type=ttype, route=route, commit_evidence=commit)
        for wid, name, role, route, expected, commit, priority, ttype in workflows
    ]


def branding_scenarios() -> list[Scenario]:
    slugs = [
        "butano", "fundo", "indawo", "madi", "msika", "mushex", "mvumo", "ndila", "nhume",
        "nompilo", "simba", "tshepo", "tuso", "ubomi", "varapi", "vito", "zibo",
    ]
    out = []
    for i, slug in enumerate(slugs, 1):
        out.append(s(
            test_id=f"UAT-BRD-{i:03d}", module="Branding / Discoverability", role="Reviewer",
            scenario=f"Sovereign logo renders for {slug}",
            preconditions="None", steps=f"1. Request /brand/services/{slug}-logo.png\n2. Open primary UI route for {slug}",
            expected=f"PNG returns 200; PageShell/ModuleCard shows branded logo not broken icon",
            pass_criteria="Logo visible on service surface", fail_criteria="Broken image on all branded surfaces",
            evidence="Screenshot + network status for PNG", priority="High" if slug in ("vito","varapi","tuso","tshepo","madi","fundo") else "Medium",
            test_type="New change", route=f"/brand/services/{slug}-logo.png",
            commit_evidence="82778272,23f772cf feat(branding)",
        ))
    out.extend([
        s(test_id="UAT-BRD-018", module="Branding / Discoverability", role="Reviewer",
          scenario="Service cards show correct name and description",
          preconditions="Launcher or registry hub open", steps="1. Open launcher\n2. Inspect 5 known service tiles",
          expected="Names match SERVICE_BRANDING registry; descriptions present",
          pass_criteria="No blank or generic 'Service' labels", fail_criteria="Wrong names or missing descriptions",
          evidence="Screenshot of launcher tiles", priority="High", test_type="New change", route="ShellStartMenu",
          commit_evidence="serviceBranding.ts"),
        s(test_id="UAT-BRD-019", module="Branding / Discoverability", role="Reviewer",
          scenario="No duplicate conflicting launcher entries",
          preconditions="Launcher open", steps="1. Search 'Vito' and 'Client'\n2. Compare duplicate tiles",
          expected="Single canonical entry per sovereign service",
          pass_criteria="No duplicate tiles for same slug", fail_criteria="Two tiles route to conflicting paths",
          evidence="Screenshot annotated", priority="Medium", test_type="Regression", route="ShellStartMenu",
          commit_evidence="COMMAND_CENTRE_TILE_SLUGS"),
        s(test_id="UAT-BRD-020", module="Branding / Discoverability", role="Reviewer",
          scenario="Fallback icons only when logo fails",
          preconditions="DevTools throttle or block one logo", steps="1. Block one logo PNG\n2. Reload surface",
          expected="Lucide fallback icon per serviceBranding.fallbackIcon",
          pass_criteria="Graceful fallback", fail_criteria="Broken image icon without fallback",
          evidence="Screenshot", priority="Low", test_type="Usability", route="varies",
          commit_evidence="ServiceLogo component"),
    ])
    return out


def mobile_scenarios() -> list[Scenario]:
    cases = [
        ("MOB-001", "Citizen app launch", "Citizen", "Build installs; home loads", "69e90d26", "High"),
        ("MOB-002", "Provider app launch", "Provider", "Clinical tools tab loads", "69e90d26", "High"),
        ("MOB-003", "Citizen Health ID / registry", "Citizen", "Health ID surfaces align web Vito", "MOBILE_PARITY_MATRIX VITO", "High"),
        ("MOB-004", "Provider MADI donor pathway", "Provider", "MADI screens reachable", "7c0af6ab", "Critical"),
        ("MOB-005", "Provider MADI central bank tab", "Provider", "Central Bank metrics tab", "45206ad1", "High"),
        ("MOB-006", "Citizen Fundo learning", "Citizen", "Course progress on mobile", "b5dcad84", "High"),
        ("MOB-007", "Provider Fundo assessment submit", "Trainer", "Assessment submit + certificate", "b5dcad84", "High"),
        ("MOB-008", "Citizen Impilo Live discover", "Citizen", "Live events list", "69e90d26", "Medium"),
        ("MOB-009", "Provider Impilo Live host", "Provider", "Manage/moderate controls", "69e90d26", "Medium"),
        ("MOB-010", "Citizen monitoring devices", "Citizen", "Device pair/list", "2884fb58", "Medium"),
        ("MOB-011", "Provider public health field ops", "PH officer", "Field ops screens", "45206ad1", "Medium"),
        ("MOB-012", "My Bookings vs My Appointments split", "Citizen", "Distinct surfaces", "6ceb3ae2", "High"),
        ("MOB-013", "Mobile parity gate inventory", "QA lead", "Review full-mobile-parity-matrix.md gaps", "6d31da93", "Medium"),
        ("MOB-014", "Mobile social feed", "Citizen", "Social timeline loads", "bc617034", "Medium"),
        ("MOB-015", "Mobile telemedicine boundary", "Provider", "Honest RTC blocked label if no runtime", "MOBILE_PARITY telemedicine", "Medium"),
    ]
    return [
        s(test_id=f"UAT-{cid}", module="Mobile Parity", role=role,
          scenario=f"Mobile: {title}",
          preconditions="Android emulator or device with preview BFF endpoint configured",
          steps=f"1. Open {role} mobile app\n2. Navigate to feature\n3. Compare with web route behaviour",
          expected=expected,
          pass_criteria="Feature reachable; honest empty/blocked if backend down",
          fail_criteria="Crash, web-only message without mobile path, or mock fixture data",
          evidence="Device screenshot + app version", priority=priority, test_type="Mobile",
          route="apps/mobile/*", commit_evidence=commit)
        for cid, title, role, expected, commit, priority in cases
    ]


def regression_scenarios() -> list[Scenario]:
    routes = [
        ("/auth/login", "Auth login"),
        ("/home", "Citizen home"),
        ("/registry", "Registry hub"),
        ("/clinical", "Clinical hub"),
        ("/enterprise", "Enterprise hub"),
        ("/data", "Data intelligence"),
        ("/ask", "Ask Nompilo"),
        ("/learning", "Fundo learning"),
        ("/ndila", "Ndila maps"),
        ("/dispatch", "Nhume dispatch"),
        ("/finance/mushex-platform", "MusheX platform"),
        ("/telemedicine", "Telemedicine hub"),
        ("/coverage", "Coverage hub"),
        ("/wellness", "Wellness/Simba"),
        ("/madi", "MADI hub"),
        ("/live", "Impilo Live"),
        ("/public-health", "Public health"),
        ("/marketplace", "Msika marketplace"),
        ("/ubomi", "Ubomi CRVS"),
        ("/lab/worklist", "OROS worklist"),
        ("/clinical/inpatient", "Inpatient hub"),
        ("/provider-workspace", "Provider workspace"),
    ]
    out = []
    for i, (route, name) in enumerate(routes, 1):
        out.append(s(
            test_id=f"UAT-REG-{i:03d}", module="Regression / Route Survival", role="Reviewer",
            scenario=f"Pre-change route still loads: {name}",
            preconditions="Preview up; HTTP or authenticated as needed",
            steps=f"1. Open {route}\n2. Wait for shell\n3. Confirm not 404/500",
            expected="Route resolves (200/307) and shell renders",
            pass_criteria="No regression vs c5f8ff43 baseline route inventory",
            fail_criteria="404, 502, or route removed from nav without documentation",
            evidence="Screenshot + HTTP status", priority="Critical" if i <= 10 else "High",
            test_type="Regression", route=route, commit_evidence="preview-http-regression.sh PASS; 575 routes",
        ))
    out.extend([
        s(test_id="UAT-REG-023", module="Regression / No stubs", role="QA lead",
          scenario="No-stub guard: production routes not fixture shells",
          preconditions="Repo checkout", steps="1. Run npm run test:no-stubs in one-ui-shell",
          expected="600 pages scanned OK",
          pass_criteria="Script exits 0", fail_criteria="Stub guard failures",
          evidence="CI log snippet", priority="Critical", test_type="Regression", route="ui/one-ui-shell",
          commit_evidence="VM gates PASS"),
        s(test_id="UAT-REG-024", module="Regression / Parity gates", role="QA lead",
          scenario="Backend-frontend parity gate passes",
          preconditions="Repo checkout", steps="1. Run check-backend-frontend-parity.sh",
          expected="Gate PASS", pass_criteria="Exit 0", fail_criteria="New orphan backend capabilities",
          evidence="Gate log", priority="Critical", test_type="Regression", route="docs/architecture/FRONTEND_BACKEND_PARITY_MATRIX.md",
          commit_evidence="VM gates PASS"),
        s(test_id="UAT-REG-025", module="Regression / Mobile parity gate", role="QA lead",
          scenario="Mobile parity gate passes",
          preconditions="Repo checkout", steps="1. Run check-mobile-parity.sh",
          expected="Gate PASS", pass_criteria="Exit 0", fail_criteria="New mobile gaps undocumented",
          evidence="Gate log", priority="High", test_type="Regression", route="docs/architecture/MOBILE_PARITY_MATRIX.md",
          commit_evidence="VM gates PASS"),
    ])
    return out


def runtime_scenarios() -> list[Scenario]:
    return [
        s(test_id="UAT-RUN-001", module="Runtime / Preview", role="QA lead", scenario="Preview health endpoint UP",
          preconditions="None", steps="curl http://41.57.127.235/actuator/health", expected='status UP',
          pass_criteria="HTTP 200 UP", fail_criteria="DOWN or unreachable", evidence="curl output",
          priority="Critical", test_type="Smoke", route="/actuator/health", commit_evidence="closure report"),
        s(test_id="UAT-RUN-002", module="Runtime / Preview", role="QA lead", scenario="Version endpoint reports build",
          preconditions="None", steps="curl http://41.57.127.235/health/version", expected="commit + buildDate JSON",
          pass_criteria="HTTP 200 with commit field", fail_criteria="Missing version info",
          evidence="JSON capture", priority="Critical", test_type="Smoke", route="/health/version",
          commit_evidence="ba7064e2 ingress generation"),
        s(test_id="UAT-RUN-003", module="Runtime / Cluster", role="Platform operator", scenario="98/98 deployments ready",
          preconditions="kubectl access", steps="kubectl get deploy -n impilo-full-preview",
          expected="All deployments ready", pass_criteria="0 not-ready", fail_criteria="CrashLoop or pending pods",
          evidence="kubectl output screenshot", priority="Critical", test_type="Smoke", route="impilo-full-preview",
          commit_evidence="closure report 98/98"),
        s(test_id="UAT-RUN-004", module="Runtime / Cluster", role="Platform operator", scenario="Postgres healthy",
          preconditions="kubectl access", steps="kubectl exec deploy/postgres -- pg_isready -U impilo",
          expected="accepting connections", pass_criteria="pg_isready success", fail_criteria="DB unreachable",
          evidence="Command output", priority="Critical", test_type="Smoke", route="postgres",
          commit_evidence="closure report"),
        s(test_id="UAT-RUN-005", module="Runtime / UX", role="Reviewer", scenario="No obvious stale UI generation",
          preconditions="Browser", steps="1. Check /health/version commit\n2. Spot-check 5 routes for old branding",
          expected="Consistent ba7064e2 generation UX; backend digests aligned",
          pass_criteria="No mixed broken assets", fail_criteria="Obviously old Lovable fork UI",
          evidence="Version JSON + screenshots", priority="High", test_type="Smoke", route="/health/version",
          commit_evidence="digest alignment PASS"),
        s(test_id="UAT-RUN-006", module="Runtime / UX", role="Reviewer", scenario="Navigation free of obvious 500 errors",
          preconditions="Authenticated session", steps="1. Open 10 priority routes\n2. Watch network for 500s",
          expected="No unhandled 500 on primary reads", pass_criteria="<=1 transient 500 with recovery",
          fail_criteria="Persistent 500 on hub pages", evidence="Network HAR export",
          priority="High", test_type="Smoke", route="multiple", commit_evidence="smoke regression PASS"),
        s(test_id="UAT-RUN-007", module="Runtime / Performance", role="Reviewer", scenario="Shell loading acceptable",
          preconditions="Normal network", steps="1. Hard refresh /auth/login and /home\n2. Time to interactive",
          expected="Loads <15s on preview VM", pass_criteria="No stuck 'Loading...' >30s",
          fail_criteria="Infinite loading spinner", evidence="Screen recording with timestamp",
          priority="Medium", test_type="Usability", route="/home", commit_evidence="closure smoke"),
        s(test_id="UAT-RUN-008", module="Runtime / Single stack", role="Platform operator", scenario="Single public ingress",
          preconditions="report-preview-generation.sh", steps="Run report-preview-generation.sh",
          expected="SINGLE_PUBLIC_STACK: yes", pass_criteria="One ingress owner", fail_criteria="Duplicate public stacks",
          evidence="Script output", priority="High", test_type="Smoke", route="http://41.57.127.235",
          commit_evidence="closure report"),
        s(test_id="UAT-RUN-009", module="Runtime / Errors", role="Reviewer", scenario="Error messages understandable",
          preconditions="Trigger validation error on form", steps="1. Submit invalid form\n2. Read error copy",
          expected="Field-level or summary human message", pass_criteria="No stack trace in UI",
          fail_criteria="Raw exception text", evidence="Screenshot", priority="Medium", test_type="Usability",
          route="varies", commit_evidence="change-safety gates"),
        s(test_id="UAT-RUN-010", module="Runtime / Assets", role="Reviewer", scenario="Brand assets load",
          preconditions="None", steps="1. Load /brand/mark-rgb.svg and /brand/logo-rgb.svg",
          expected="HTTP 200 SVG assets", pass_criteria="Both assets load", fail_criteria="404 on brand assets",
          evidence="Network log", priority="High", test_type="Smoke", route="/brand/*",
          commit_evidence="82778272 branding"),
    ]


def main() -> None:
    scenarios: list[Scenario] = []
    scenarios.extend(shell_scenarios())
    scenarios.extend(nompilo_scenarios())
    scenarios.extend(sovereign_service_scenarios())
    scenarios.extend(workflow_scenarios())
    scenarios.extend(branding_scenarios())
    scenarios.extend(mobile_scenarios())
    scenarios.extend(regression_scenarios())
    scenarios.extend(runtime_scenarios())

    OUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    with OUT_CSV.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS)
        w.writeheader()
        for sc in scenarios:
            row = asdict(sc)
            w.writerow({
                "Test ID": row["test_id"],
                "Module/Service": row["module"],
                "User Role/Persona": row["role"],
                "Test Scenario": row["scenario"],
                "Preconditions": row["preconditions"],
                "Test Steps": row["steps"],
                "Expected Result": row["expected"],
                "Pass Criteria": row["pass_criteria"],
                "Fail Criteria": row["fail_criteria"],
                "Evidence To Capture": row["evidence"],
                "Priority": row["priority"],
                "Test Type": row["test_type"],
                "Related Route/Surface": row["route"],
                "Related Commit/Change Evidence": row["commit_evidence"],
                "Status": "",
                "Tester Comments": "",
                "Defect Reference": "",
                "Retest Result": "",
            })

    by_module: dict[str, int] = {}
    by_role: dict[str, int] = {}
    by_priority: dict[str, int] = {}
    by_type: dict[str, int] = {}
    for sc in scenarios:
        by_module[sc.module] = by_module.get(sc.module, 0) + 1
        by_role[sc.role] = by_role.get(sc.role, 0) + 1
        by_priority[sc.priority] = by_priority.get(sc.priority, 0) + 1
        by_type[sc.test_type] = by_type.get(sc.test_type, 0) + 1

    summary = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "preview_url": "http://41.57.127.235",
        "branch": "claude/staging-ux-orchestration-remediation-Yypyl",
        "baseline_commit": "c5f8ff43",
        "head_commit": "4917def8",
        "commit_range_count": 90,
        "total_scenarios": len(scenarios),
        "critical_count": by_priority.get("Critical", 0),
        "regression_count": by_type.get("Regression", 0),
        "new_change_count": by_type.get("New change", 0),
        "modules_covered": len(by_module),
        "by_module": by_module,
        "by_role": by_role,
        "by_priority": by_priority,
        "by_test_type": by_type,
        "csv_path": str(OUT_CSV.relative_to(REPO)),
    }
    OUT_SUMMARY.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
