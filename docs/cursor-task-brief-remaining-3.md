# Cursor Task Brief: 3 Remaining Items
## Branch: `claude/staging-ux-orchestration-remediation-Yypyl`
## Repo: `rtgongora/Impilo-vNext`

---

## Context

You are Cursor, working on the Impilo vNext national digital health platform. Claude Code is coordinating from the center. Your domain is **non-EHR UI, mobile apps, and BFF controllers** — see `docs/multi-agent-coordination-strategy.md` for full scope.

The codebase has 308 TypeScript files in `ui/experience/`, 166 mobile files, and 2,823 Java backend files. TypeScript compiles with only 2 non-blocking errors. There are 140 ESLint errors and 261 warnings across 125 files that need cleanup.

**Stack**: Next.js 14.2, React 18.3.1, TypeScript 5.5, TailwindCSS 3.4, lucide-react, @tanstack/react-query, Zustand. No shadcn/ui — all components use plain HTML + TailwindCSS.

**Commit conventions**: `feat:`, `fix:`, `refactor:`, `chore:` — one logical change per commit. Push each commit immediately.

---

## Task 1: ESLint Cleanup (140 errors, 261 warnings across 125 files)

### What to do

Run `cd ui/experience && npx next lint` to see all issues. Fix them in batches by category:

#### Category A: Unused imports (highest count — ~80 errors)
Files have imports like `apiClient`, `Clock`, `CheckCircle2`, `useState`, `X`, `Stethoscope`, `AlertCircle`, `ApiResponse`, `User`, `TrendingUp`, `encounterId`, `patient`, `isClinical` that are imported but never used.

**Fix**: Remove the unused import from each file. Do NOT remove imports that are actually used — check before deleting. If a variable like `patient` or `encounterId` is destructured from props/params but unused, prefix with `_` (e.g., `_patient`, `_encounterId`).

Commit: `chore: remove unused imports across 80+ files`

#### Category B: `@typescript-eslint/no-explicit-any` (~18 errors)
Code uses `any` type in places where a proper type should be specified.

**Fix**: Replace `any` with the actual type. Common patterns:
- `.map((item: any) =>` → `.map((item: { id: string; name: string; ... }) =>`
- `Record<string, any>` → `Record<string, unknown>` or a specific interface
- Function params typed as `any` → type from context
- If the type is genuinely dynamic and unknowable, use `unknown` instead of `any`

Commit: `fix: replace explicit any types with proper typings`

#### Category C: `react-hooks/rules-of-hooks` (2 errors)
`useMemo` is called conditionally (after an early return). This is a React rules violation.

**Fix**: Move the hook call before any early return statements, or restructure the component so hooks are always called in the same order.

Commit: `fix: resolve conditional hook call violations`

#### Category D: `react-hooks/exhaustive-deps` warnings (~15)
Missing dependencies in `useEffect`/`useMemo`/`useCallback` dependency arrays.

**Fix**: Add the missing dependencies, or if intentionally excluded, add a `// eslint-disable-next-line react-hooks/exhaustive-deps` comment with a reason.

Commit: `fix: correct React hook dependency arrays`

#### Category E: Remaining warnings
`no-console` warnings, `prefer-const`, etc.

**Fix**: Replace `console.log` with `console.warn` or remove. Change `let` to `const` where variable is never reassigned.

Commit: `chore: clean up console.log and prefer-const warnings`

### Validation
After all fixes, run:
```bash
cd ui/experience && npx next lint 2>&1 | grep -c "Error:"
```
Target: **0 errors**. Warnings below 50 acceptable.

Then run type-check to confirm nothing broke:
```bash
npx tsc --noEmit --skipLibCheck 2>&1 | grep "error TS" | wc -l
```
Target: **2 or fewer** (the 2 known non-blocking errors).

---

## Task 2: 18 Specialty Workspace Shells (Mobile Provider App)

### Background

The provider mobile app has a `ClinicalToolsScreen` with a "Specialty" tab that fetches specialty workspace definitions. Currently it lists workspace names but doesn't render specialty-specific content. The Lovable prototype defined 18 specialty workspaces, each with specific clinical tools and workflows.

### What to implement

**File**: `apps/mobile/provider-app/src/screens/provider/ClinicalToolsScreen.tsx`

The existing "Specialty" tab at the bottom of ClinicalToolsScreen needs to render actual workspace content when a specialty is tapped. Create a `SpecialtyWorkspacePanel` component (new file: `apps/mobile/provider-app/src/screens/provider/SpecialtyWorkspacePanel.tsx`) that renders specialty-specific tools.

#### The 18 Specialties

Each specialty workspace is a screen with:
1. A header showing specialty name and icon
2. A list of tools/protocols specific to that specialty
3. Quick-action buttons for common workflows
4. A "Back to All Specialties" button

```typescript
const SPECIALTY_WORKSPACES = [
  { id: "anaesthesia", name: "Anaesthesia", icon: "Syringe", tools: ["Pre-op Assessment", "ASA Classification", "Airway Assessment (Mallampati)", "Anaesthetic Plan", "Recovery Checklist", "Pain Protocol"] },
  { id: "burns", name: "Burns Unit", icon: "Flame", tools: ["Burns Assessment (Rule of 9s)", "Fluid Resuscitation (Parkland)", "Wound Chart", "Graft Planning", "Pain Ladder", "Nutrition Plan"] },
  { id: "cardiology", name: "Cardiology", icon: "Heart", tools: ["ECG Interpretation", "Troponin Tracker", "ACS Protocol", "Heart Failure Assessment", "Anticoagulation Plan", "Cardiac Rehab"] },
  { id: "chemo", name: "Chemotherapy", icon: "Pill", tools: ["Chemo Protocol Selection", "Dose Calculator (BSA)", "Pre-Chemo Checklist", "Toxicity Grading (CTCAE)", "Antiemetic Protocol", "Blood Count Review"] },
  { id: "dermatology", name: "Dermatology", icon: "Scan", tools: ["Lesion Mapping", "Biopsy Request", "Phototherapy Log", "Dermatology Atlas", "Patch Test Record", "Wound Assessment"] },
  { id: "dialysis", name: "Dialysis", icon: "Activity", tools: ["Dialysis Prescription", "Fluid Balance", "Kt/V Calculator", "Access Assessment", "Electrolyte Tracker", "Dry Weight Trend"] },
  { id: "ent", name: "ENT", icon: "Ear", tools: ["Audiometry Record", "Tympanogram", "Flexible Nasendoscopy", "Voice Assessment", "Thyroid Nodule FNA", "Sleep Study Request"] },
  { id: "gastro", name: "Gastroenterology", icon: "Utensils", tools: ["Endoscopy Report", "Liver Function Trend", "MELD Score", "Child-Pugh Score", "IBD Activity Index", "Nutrition Assessment"] },
  { id: "haematology", name: "Haematology", icon: "Droplet", tools: ["Blood Film Review", "Coagulation Panel", "Transfusion Request", "Sickle Cell Crisis Protocol", "Bone Marrow Report", "Anticoagulation Clinic"] },
  { id: "icu", name: "Intensive Care", icon: "Monitor", tools: ["APACHE II Score", "SOFA Score", "Ventilator Settings", "Sedation (RASS)", "Nutrition (NUTRIC)", "Daily ICU Checklist"] },
  { id: "neonatal", name: "Neonatal", icon: "Baby", tools: ["APGAR Record", "Gestational Age Assessment", "Growth Chart (Fenton)", "Surfactant Protocol", "Bilirubin Chart", "Feeding Plan"] },
  { id: "nephrology", name: "Nephrology", icon: "Filter", tools: ["eGFR Trend", "Urinalysis Review", "Biopsy Report", "Transplant Assessment", "Immunosuppression Protocol", "Dialysis Access"] },
  { id: "neurology", name: "Neurology", icon: "Brain", tools: ["NIHSS Score", "GCS Tracker", "Seizure Log", "Lumbar Puncture Record", "MS Relapse Assessment", "Cognitive Screen (MMSE/MoCA)"] },
  { id: "obstetrics", name: "Obstetrics", icon: "Baby", tools: ["Partograph", "CTG Interpretation", "Bishop Score", "PPH Protocol", "Eclampsia Protocol", "Neonatal Resuscitation"] },
  { id: "oncology", name: "Oncology", icon: "Target", tools: ["Staging (TNM)", "Performance Status (ECOG)", "Treatment Plan", "Symptom Assessment (ESAS)", "Palliative Care Needs", "MDT Summary"] },
  { id: "ophthalmology", name: "Ophthalmology", icon: "Eye", tools: ["Visual Acuity Record", "IOP Measurement", "Fundoscopy Report", "Visual Field Test", "Slit Lamp Findings", "Refraction Record"] },
  { id: "orthopaedics", name: "Orthopaedics", icon: "Bone", tools: ["Fracture Classification", "Neurovascular Check", "Cast/Splint Record", "ROM Assessment", "VTE Prophylaxis", "Rehab Milestones"] },
  { id: "psychiatry", name: "Psychiatry", icon: "Brain", tools: ["Mental State Examination", "PHQ-9", "GAD-7", "Risk Assessment", "Capacity Assessment", "Section/Involuntary Hold"] },
];
```

#### Implementation Pattern

For each specialty, render a scrollable list of tool cards. When a tool is tapped, show a modal/sheet with:
- Tool name and description
- Input fields relevant to the tool (e.g., for "Rule of 9s" — body region checkboxes with percentages)
- Calculate/Save button
- Result display

You do NOT need to implement full clinical calculators for all 108 tools. Instead:
- For the first 3-4 tools in each specialty: create a functional form with inputs and a result
- For the remaining tools: show a "Coming Soon" placeholder with the tool description

Use React Native components (View, Text, ScrollView, TouchableOpacity, TextInput, Modal) — NOT web HTML. This is Expo/React Native.

### Files to create/modify
1. **Create**: `apps/mobile/provider-app/src/screens/provider/SpecialtyWorkspacePanel.tsx` — the specialty panel component
2. **Create**: `apps/mobile/provider-app/src/data/specialtyWorkspaces.ts` — the specialty data definitions
3. **Modify**: `apps/mobile/provider-app/src/screens/provider/ClinicalToolsScreen.tsx` — wire the Specialty tab to render SpecialtyWorkspacePanel

### Commit strategy
- Commit 1: `feat: add specialty workspace data definitions (18 specialties, 108 tools)`
- Commit 2: `feat: implement SpecialtyWorkspacePanel with tool cards and functional forms`
- Commit 3: `feat: wire specialty workspaces into ClinicalToolsScreen Specialty tab`

---

## Task 3: Production Keycloak Realm Configuration

### Background

Impilo vNext uses Keycloak 25.x for identity. The dev environment has a basic realm, but production needs a fully configured realm with all client definitions, roles, identity providers, and authentication flows.

### What to implement

**Create**: `infra/keycloak/realm-impilo-production.json`

This is a Keycloak realm export JSON that defines:

#### Realm Settings
```json
{
  "realm": "impilo",
  "enabled": true,
  "displayName": "Impilo National Health Platform",
  "sslRequired": "external",
  "registrationAllowed": false,
  "loginWithEmailAllowed": true,
  "duplicateEmailsAllowed": false,
  "resetPasswordAllowed": true,
  "editUsernameAllowed": false,
  "bruteForceProtected": true,
  "permanentLockout": false,
  "maxFailureWaitSeconds": 900,
  "minimumQuickLoginWaitSeconds": 60,
  "waitIncrementSeconds": 60,
  "quickLoginCheckMilliSeconds": 1000,
  "maxDeltaTimeSeconds": 43200,
  "failureFactor": 5,
  "passwordPolicy": "length(12) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername and passwordHistory(5)",
  "accessTokenLifespan": 300,
  "accessTokenLifespanForImplicitFlow": 900,
  "ssoSessionIdleTimeout": 1800,
  "ssoSessionMaxLifespan": 36000,
  "offlineSessionIdleTimeout": 2592000
}
```

#### Clients (8)
1. `impilo-ui` — Experience UI (public, PKCE, redirect URIs for impilo.gov.zw)
2. `impilo-ops-console` — Ops Console (confidential)
3. `impilo-ehr` — EHR App (public, PKCE)
4. `impilo-portal` — Citizen Portal (public, PKCE)
5. `impilo-mobile-citizen` — Citizen Mobile App (public, PKCE, custom scheme redirect)
6. `impilo-mobile-provider` — Provider Mobile App (public, PKCE, custom scheme redirect)
7. `impilo-bff` — Experience BFF (confidential, service account)
8. `impilo-admin-cli` — Admin CLI (confidential, service account, direct access grant)

Each client needs: `clientId`, `name`, `enabled`, `publicClient` (true/false), `directAccessGrantsEnabled`, `standardFlowEnabled`, `implicitFlowEnabled: false`, `serviceAccountsEnabled` (for confidential clients), `redirectUris`, `webOrigins`, `defaultClientScopes`, `optionalClientScopes`.

#### Roles (define in realm roles)
```
ADMIN, CLINICAL, FINANCE, PRESCRIBER, DISPENSER, QUEUE_MANAGER, BED_MANAGER,
NURSE, DOCTOR, SPECIALIST, CONSULTANT, REGISTRAR, INTERN_DOCTOR,
MIDWIFE, NURSE_PRACTITIONER, ENROLLED_NURSE,
PHARMACIST, PHARMACY_TECH, LAB_TECH,
PHYSIOTHERAPIST, OCCUPATIONAL_THERAPIST, SOCIAL_WORKER, PSYCHOLOGIST, DIETITIAN,
RADIOGRAPHER, SONOGRAPHER,
PARAMEDIC, EMT,
CHW, ENV_HEALTH, HEALTH_PROMOTER,
HEALTH_INFO_OFFICER, RECEPTIONIST,
SYSTEM_ADMIN, SUPER_ADMIN
```

Each role needs a description. Group them into composite roles:
- `CLINICAL_STAFF` = DOCTOR + NURSE + SPECIALIST + CONSULTANT + REGISTRAR + MIDWIFE + ...
- `ALLIED_HEALTH` = PHYSIOTHERAPIST + OCCUPATIONAL_THERAPIST + SOCIAL_WORKER + ...
- `ADMIN_STAFF` = ADMIN + SYSTEM_ADMIN + HEALTH_INFO_OFFICER + RECEPTIONIST
- `COMMUNITY_HEALTH` = CHW + ENV_HEALTH + HEALTH_PROMOTER

#### Authentication Flows
1. **Browser flow** (default): Username/password → OTP (optional) → Conditional OTP
2. **Provider login flow**: Username/password → mandatory OTP (for clinical staff)
3. **Citizen login flow**: Username/password only (no OTP required)
4. **Direct grant flow**: For service accounts and CLI

#### Identity Provider (optional placeholder)
- MOSIP integration placeholder (for national ID verification)
- LDAP federation placeholder (for facility directory integration)

#### Client Scopes
- `impilo-trust-headers` — custom scope that adds trust headers (X-Actor-Id, X-Tenant-Id, X-Pod-Id) to tokens
- `impilo-clinical` — clinical data access scope
- `impilo-admin` — administrative access scope

Also create: `infra/keycloak/README.md` explaining how to import the realm:
```bash
# Import realm into Keycloak
docker exec -it keycloak /opt/keycloak/bin/kc.sh import --file /opt/keycloak/data/import/realm-impilo-production.json

# Or via Keycloak admin API
curl -X POST http://localhost:8080/admin/realms \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d @infra/keycloak/realm-impilo-production.json
```

### Commit
`feat: add production Keycloak realm configuration with 8 clients, 35 roles, auth flows`

---

## General Rules

1. **One commit per logical change**. Push immediately after each commit.
2. **Do NOT modify** files in `ui/experience/src/components/ehr/`, `ui/experience/src/components/clinical/`, `ui/experience/src/engines/`, `ui/experience/src/data/clerkingTemplates.ts` — those are Codex's domain.
3. **Do NOT modify** files in `helm/`, `infra/k8s/`, `.github/workflows/`, `e2e/` — those are Claude's domain.
4. **Test your changes**: run `npx tsc --noEmit --skipLibCheck` and `npx next lint` after each batch.
5. **Branch**: `claude/staging-ux-orchestration-remediation-Yypyl` — push directly to this branch.
