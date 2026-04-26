import fs from "node:fs";
import path from "node:path";

/**
 * Generates:
 * - Tier-1 mobile parity matrix (journey-based)
 * - Tier-2 parity matrix (zone-based: ehr/queue/pharmacy/lab/marketplace)
 *
 * Source of truth: `ui/one-ui-shell/src/lib/routes.ts`
 *
 * Output:
 * - docs/mobile/full-mobile-parity-matrix.json
 * - docs/mobile/full-mobile-parity-matrix.md
 * - docs/mobile/tier2-parity-matrix.json
 * - docs/mobile/tier2-parity-matrix.md
 * - docs/mobile/tier3-parity-matrix.json
 * - docs/mobile/tier3-parity-matrix.md
 * - docs/mobile/tier3-wave2-parity-matrix.json
 * - docs/mobile/tier3-wave2-parity-matrix.md
 * - docs/mobile/tier3-wave3-parity-matrix.json
 * - docs/mobile/tier3-wave3-parity-matrix.md
 * - docs/mobile/tier3-wave4-parity-matrix.json
 * - docs/mobile/tier3-wave4-parity-matrix.md
 * - docs/mobile/tier3-wave5-parity-matrix.json
 * - docs/mobile/tier3-wave5-parity-matrix.md
 * - docs/mobile/tier3-wave6-parity-matrix.json
 * - docs/mobile/tier3-wave6-parity-matrix.md
 * - docs/mobile/tier3-wave7-parity-matrix.json
 * - docs/mobile/tier3-wave7-parity-matrix.md
 *
 * Tier-3 contract columns: `contract_ok` when OpenAPI defines the strict schema
 * components checked in this script (hub envelopes, facilities/workspaces, citizen long-tail).
 */

const repoRoot = path.resolve(process.cwd());
const routesFile = path.join(repoRoot, "ui", "one-ui-shell", "src", "lib", "routes.ts");
const citizenScreensRoot = path.join(repoRoot, "apps", "mobile", "citizen-app", "src", "screens");
const providerScreensRoot = path.join(repoRoot, "apps", "mobile", "provider-app", "src", "screens");
const outJson = path.join(repoRoot, "docs", "mobile", "full-mobile-parity-matrix.json");
const outMd = path.join(repoRoot, "docs", "mobile", "full-mobile-parity-matrix.md");
const outTier2Json = path.join(repoRoot, "docs", "mobile", "tier2-parity-matrix.json");
const outTier2Md = path.join(repoRoot, "docs", "mobile", "tier2-parity-matrix.md");
const outTier3Json = path.join(repoRoot, "docs", "mobile", "tier3-parity-matrix.json");
const outTier3Md = path.join(repoRoot, "docs", "mobile", "tier3-parity-matrix.md");
const outTier3Wave2Json = path.join(repoRoot, "docs", "mobile", "tier3-wave2-parity-matrix.json");
const outTier3Wave2Md = path.join(repoRoot, "docs", "mobile", "tier3-wave2-parity-matrix.md");
const outTier3Wave3Json = path.join(repoRoot, "docs", "mobile", "tier3-wave3-parity-matrix.json");
const outTier3Wave3Md = path.join(repoRoot, "docs", "mobile", "tier3-wave3-parity-matrix.md");
const outTier3Wave4Json = path.join(repoRoot, "docs", "mobile", "tier3-wave4-parity-matrix.json");
const outTier3Wave4Md = path.join(repoRoot, "docs", "mobile", "tier3-wave4-parity-matrix.md");
const outTier3Wave5Json = path.join(repoRoot, "docs", "mobile", "tier3-wave5-parity-matrix.json");
const outTier3Wave5Md = path.join(repoRoot, "docs", "mobile", "tier3-wave5-parity-matrix.md");
const outTier3Wave6Json = path.join(repoRoot, "docs", "mobile", "tier3-wave6-parity-matrix.json");
const outTier3Wave6Md = path.join(repoRoot, "docs", "mobile", "tier3-wave6-parity-matrix.md");
const outTier3Wave7Json = path.join(repoRoot, "docs", "mobile", "tier3-wave7-parity-matrix.json");
const outTier3Wave7Md = path.join(repoRoot, "docs", "mobile", "tier3-wave7-parity-matrix.md");

// CI enforces `git diff --exit-code` after generation. A wall-clock timestamp would
// cause perpetual drift; keep this deterministic.
const GENERATED_AT = "1970-01-01T00:00:00.000Z";

function readText(p) {
  return fs.readFileSync(p, "utf8");
}

function fileExists(p) {
  try {
    fs.accessSync(p, fs.constants.F_OK);
    return true;
  } catch {
    return false;
  }
}

function normalizeSlashes(p) {
  return p.replaceAll("\\", "/");
}

function listFilesRecursive(root) {
  /** @type {string[]} */
  const out = [];
  /** @type {string[]} */
  const stack = [root];
  while (stack.length) {
    const cur = stack.pop();
    const entries = fs.readdirSync(cur, { withFileTypes: true });
    for (const e of entries) {
      const full = path.join(cur, e.name);
      if (e.isDirectory()) stack.push(full);
      else out.push(full);
    }
  }
  return out;
}

function screenIndex(root) {
  const files = listFilesRecursive(root).filter((p) => p.endsWith("Screen.tsx") || p.endsWith("Section.tsx"));
  const rel = files.map((p) => path.relative(repoRoot, p).replaceAll("\\", "/"));
  return new Set(rel);
}

const citizenIndex = screenIndex(citizenScreensRoot);
const providerIndex = screenIndex(providerScreensRoot);

const tier2Zones = new Set(["ehr", "queue", "pharmacy", "lab", "marketplace"]);
const tier3ProviderZones = new Set(["facility", "workspace", "shift"]);

function isTier3ProviderOpsRoute(r) {
  if (tier3ProviderZones.has(r.zone)) return true;
  if (r.path.startsWith("/scheduling")) return true;
  return false;
}

function isTier3CitizenFacilityRoute(r) {
  return r.zone === "facility" && r.path.startsWith("/facility");
}

const tier3Wave2CitizenPaths = new Set([
  "/citizen/health-id/request",
  "/citizen/id-recovery",
  "/citizen/delegated-pickup",
  "/citizen/record-sharing",
  "/verify/credential",
  "/share/claim",
  "/privacy",
  "/terms",
  "/consent",
  "/account-deletion",
]);

function isTier3Wave2CitizenLongtailRoute(r) {
  return tier3Wave2CitizenPaths.has(r.path);
}

/** Tier-3 wave 3: provider professional-plane admin + registry landings (single mobile hub). */
const tier3Wave3ProviderPaths = new Set([
  "/admin",
  "/registry",
  "/registry-admin",
  "/organization-admin",
  "/public-health",
  "/id-services",
  "/access",
  "/ai-governance",
]);

function isTier3Wave3ProviderAdminRegistryRoute(r) {
  return tier3Wave3ProviderPaths.has(r.path);
}

function inferTier3Wave3ProviderFiles() {
  return ["apps/mobile/provider-app/src/screens/provider/AdminRegistryHubScreen.tsx"];
}

/** Tier-3 wave 4: operations + reports professional-plane (single mobile hub). */
const tier3Wave4ProviderPaths = new Set([
  "/operations",
  "/operations/vito",
  "/operations/butano",
  "/operations/assets",
  "/operations/equipment",
  "/reports",
  "/reports/facility",
  "/reports/clinical",
  "/reports/operational",
  "/reports/custom",
  "/reports/[id]",
]);

function isTier3Wave4ProviderOpsReportsRoute(r) {
  return tier3Wave4ProviderPaths.has(r.path);
}

function inferTier3Wave4ProviderFiles() {
  return ["apps/mobile/provider-app/src/screens/provider/OpsReportsHubScreen.tsx"];
}

/** Tier-3 wave 5: developer portal professional-plane (single mobile hub). */
const tier3Wave5ProviderPaths = new Set([
  "/developer",
  "/developer/api-catalog",
  "/developer/clients",
  "/developer/sandbox",
]);

function isTier3Wave5ProviderDeveloperRoute(r) {
  return tier3Wave5ProviderPaths.has(r.path);
}

function inferTier3Wave5ProviderFiles() {
  return ["apps/mobile/provider-app/src/screens/provider/DeveloperHubScreen.tsx"];
}

/** Tier-3 wave 6: web professional settings zone (`/settings*`). */
const tier3Wave6ProviderPaths = new Set([
  "/settings",
  "/settings/account",
  "/settings/security",
  "/settings/notifications",
  "/settings/display",
  "/settings/integrations",
  "/settings/privacy",
]);

function isTier3Wave6ProviderProfessionalSettingsRoute(r) {
  return tier3Wave6ProviderPaths.has(r.path);
}

function inferTier3Wave6ProviderFiles() {
  return ["apps/mobile/provider-app/src/screens/provider/ProfessionalSettingsHubScreen.tsx"];
}

/** Tier-3 wave 7: omnichannel, coverage, credentials, public-health drill-down (single hub). */
const tier3Wave7ProviderPaths = new Set([
  "/omnichannel",
  "/coverage",
  "/home/credentials",
  "/public-health/surveillance",
  "/public-health/campaigns",
  "/public-health/site-registry",
  "/public-health/site-registry/[siteId]",
]);

function isTier3Wave7ProviderProfessionalChannelsRoute(r) {
  return tier3Wave7ProviderPaths.has(r.path);
}

function inferTier3Wave7ProviderFiles() {
  return ["apps/mobile/provider-app/src/screens/provider/ProfessionalChannelsHubScreen.tsx"];
}

// Very small, explicit Tier-1 mapping for this wave.
// These are canonical "journeys" we expect to exist in mobile, derived from the plan.
const tier1RouteMatchers = [
  // Citizen life
  { id: "citizen_home", match: (r) => r.navZone === "life" && (r.path === "/" || r.path === "/home"), mobile: { app: "citizen", files: ["apps/mobile/citizen-app/src/screens/HomeScreen.tsx"] } },
  { id: "citizen_profile", match: (r) => r.path === "/home/profile", mobile: { app: "citizen", files: ["apps/mobile/citizen-app/src/screens/personal/ProfileSection.tsx"] } },
  { id: "citizen_health_id_qr", match: (r) => r.path === "/citizen/health-id/qr", mobile: { app: "citizen", files: ["apps/mobile/citizen-app/src/screens/personal/HealthIdSection.tsx"] } },
  { id: "citizen_wellness", match: (r) => r.path.startsWith("/wellness"), mobile: { app: "citizen", files: ["apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx"] } },
  { id: "citizen_monitoring", match: (r) => r.path.startsWith("/monitoring"), mobile: { app: "citizen", files: ["apps/mobile/citizen-app/src/screens/personal/MonitoringSection.tsx"] } },
  { id: "citizen_marketplace", match: (r) => r.path.startsWith("/marketplace") || r.path.startsWith("/services"), mobile: { app: "citizen", files: ["apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx", "apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx"] } },
  { id: "citizen_messaging", match: (r) => r.path.startsWith("/communication") || r.path.includes("messaging"), mobile: { app: "citizen", files: ["apps/mobile/citizen-app/src/screens/messaging/MessagingInboxScreen.tsx", "apps/mobile/citizen-app/src/screens/messaging/ThreadViewScreen.tsx"] } },
  { id: "citizen_telehealth", match: (r) => r.path.startsWith("/telemedicine") || r.path.startsWith("/telehealth"), mobile: { app: "citizen", files: ["apps/mobile/citizen-app/src/screens/telehealth/TelehealthListScreen.tsx", "apps/mobile/citizen-app/src/screens/telehealth/TelehealthSessionScreen.tsx"] } },

  // Provider work
  { id: "provider_dashboard", match: (r) => r.path === "/clinical" || r.path === "/clinical-tools" || (r.navZone === "work" && r.path === "/queue"), mobile: { app: "provider", files: ["apps/mobile/provider-app/src/screens/provider/ProviderDashboardScreen.tsx", "apps/mobile/provider-app/src/screens/provider/ClinicalToolsScreen.tsx", "apps/mobile/provider-app/src/screens/provider/QueueManagementScreen.tsx"] } },
  { id: "provider_patient_lookup", match: (r) => r.path === "/queue/search" || r.path === "/queue/walk-in", mobile: { app: "provider", files: ["apps/mobile/provider-app/src/screens/provider/PatientLookupScreen.tsx", "apps/mobile/provider-app/src/screens/provider/PatientRegistrationScreen.tsx"] } },
  { id: "provider_results", match: (r) => r.path.includes("/results") && r.navZone === "work", mobile: { app: "provider", files: ["apps/mobile/provider-app/src/screens/provider/ResultsViewScreen.tsx"] } },
  { id: "provider_messaging", match: (r) => r.path.includes("messaging") && r.navZone === "work", mobile: { app: "provider", files: ["apps/mobile/provider-app/src/screens/provider/MessagingScreen.tsx"] } },
  { id: "provider_shift_handoff", match: (r) => r.path === "/shift/handover", mobile: { app: "provider", files: ["apps/mobile/provider-app/src/screens/provider/ShiftHandoffScreen.tsx"] } },
];

function parseRoutes(ts) {
  // Best-effort parse: extract object literals that look like route definitions.
  // Assumption: fields are in a single line object literal, as in this file.
  const lines = ts.split(/\r?\n/);
  /** @type {Array<any>} */
  const routes = [];
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed.startsWith("{ path:")) continue;
    const pathMatch = trimmed.match(/path:\s*"([^"]+)"/);
    const zoneMatch = trimmed.match(/zone:\s*"([^"]+)"/);
    const navZoneMatch = trimmed.match(/navZone:\s*"([^"]+)"/);
    const guardMatch = trimmed.match(/guard:\s*"([^"]+)"/);
    const titleMatch = trimmed.match(/pageTitle:\s*"([^"]+)"/);
    if (!pathMatch || !zoneMatch || !guardMatch || !titleMatch) continue;
    routes.push({
      path: pathMatch[1],
      zone: zoneMatch[1],
      guard: guardMatch[1],
      pageTitle: titleMatch[1],
      navZone: navZoneMatch ? navZoneMatch[1] : undefined,
      raw: trimmed,
    });
  }
  return routes;
}

const allRoutes = parseRoutes(readText(routesFile));

/** Provider OpenAPI: strict Tier-3 professional hub envelope (`contracts/openapi/mobile-provider.openapi.yaml`). */
function providerOpenApiHubContractOk(text) {
  return (
    text.includes("ProviderMobileHubResponse") &&
    text.includes("ProviderHubPayload") &&
    text.includes("ProviderHubSection")
  );
}

/** Provider OpenAPI: strict Tier-3 ops foundation (facilities + workspaces list). */
function providerOpenApiOpsFoundationContractOk(text) {
  return (
    text.includes("ProviderFacilityListResponse") &&
    text.includes("ProviderFacilityDetailResponse") &&
    text.includes("ProviderWorkspaceListResponse")
  );
}

/** Citizen OpenAPI: strict facility list/detail envelopes. */
function citizenOpenApiFacilitiesStrictOk(text) {
  return text.includes("CitizenFacilityListResponse") && text.includes("CitizenFacilityDetailResponse");
}

/** Citizen OpenAPI: strict long-tail POST response schemas (wave 2). */
function citizenOpenApiLongtailStrictOk(text) {
  return (
    text.includes("CitizenShareIssueResponse") &&
    text.includes("CitizenShareClaimResponse") &&
    text.includes("CitizenCredentialVerifyResponse") &&
    text.includes("CitizenDelegatedPickupIssueResponse")
  );
}

function contractAudit(rows) {
  return {
    contract_ok: rows.filter((r) => r.contractStatus === "contract_ok").length,
    contract_partial: rows.filter((r) => r.contractStatus === "contract_partial").length,
    contract_missing: rows.filter((r) => r.contractStatus === "contract_missing").length,
  };
}

function evaluateTier1Mapping() {
  /** @type {Array<any>} */
  const rows = [];
  for (const r of allRoutes) {
    for (const m of tier1RouteMatchers) {
      if (!m.match(r)) continue;
      const files = m.mobile.files;
      const index = m.mobile.app === "citizen" ? citizenIndex : providerIndex;
      const missingFiles = files.filter((f) => !index.has(normalizeSlashes(f)));
      rows.push({
        tier: "tier1",
        routePath: r.path,
        routeTitle: r.pageTitle,
        routeZone: r.zone,
        routeGuard: r.guard,
        routeNavZone: r.navZone ?? null,
        mappingId: m.id,
        mobileApp: m.mobile.app,
        mobileFiles: files,
        status: missingFiles.length === 0 ? "done" : "missing",
        missingFiles,
      });
    }
  }

  // Deduplicate (same route can match multiple times across repeated parsing lines)
  const key = (x) => `${x.routePath}::${x.mappingId}`;
  const seen = new Set();
  const deduped = [];
  for (const row of rows) {
    const k = key(row);
    if (seen.has(k)) continue;
    seen.add(k);
    deduped.push(row);
  }
  return deduped.sort((a, b) => a.mobileApp.localeCompare(b.mobileApp) || a.routePath.localeCompare(b.routePath));
}

function inferTier2ProviderFiles(route) {
  // Tier-2 is provider-heavy for the selected zones.
  /** @type {string[]} */
  const files = [];

  if (route.zone === "queue") {
    files.push(
      "apps/mobile/provider-app/src/screens/provider/QueueManagementScreen.tsx",
      "apps/mobile/provider-app/src/screens/provider/PatientLookupScreen.tsx",
      "apps/mobile/provider-app/src/screens/provider/PatientRegistrationScreen.tsx",
      "apps/mobile/provider-app/src/screens/provider/TriageScreen.tsx"
    );
  }
  if (route.zone === "ehr") {
    files.push(
      "apps/mobile/provider-app/src/screens/provider/EncounterScreen.tsx",
      "apps/mobile/provider-app/src/screens/provider/ResultsViewScreen.tsx",
      "apps/mobile/provider-app/src/screens/provider/DischargeScreen.tsx",
      "apps/mobile/provider-app/src/screens/provider/CarePlanDetailScreen.tsx"
    );
  }
  if (route.zone === "pharmacy") {
    files.push("apps/mobile/provider-app/src/screens/provider/PharmacyHubScreen.tsx");
  }
  if (route.zone === "lab") {
    files.push("apps/mobile/provider-app/src/screens/provider/LabHubScreen.tsx");
  }
  if (route.zone === "marketplace") {
    files.push("apps/mobile/provider-app/src/screens/provider/MarketplaceOpsScreen.tsx");
  }

  // Deduplicate
  return Array.from(new Set(files));
}

function evaluateTier2Mapping() {
  /** @type {Array<any>} */
  const rows = [];

  const providerContractText = (() => {
    const p = path.join(repoRoot, "contracts", "openapi", "mobile-provider.openapi.yaml");
    return fileExists(p) ? readText(p) : "";
  })();

  for (const r of allRoutes) {
    if (!tier2Zones.has(r.zone)) continue;

    const providerFiles = inferTier2ProviderFiles(r);
    const missingFiles = providerFiles.filter((f) => !providerIndex.has(normalizeSlashes(f)));

    rows.push({
      tier: "tier2",
      routePath: r.path,
      routeTitle: r.pageTitle,
      routeZone: r.zone,
      routeGuard: r.guard,
      routeNavZone: r.navZone ?? null,
      mobileApp: "provider",
      mobileFiles: providerFiles,
      uiStatus: missingFiles.length === 0 ? "done_ui" : "missing_ui",
      missingFiles,
      contractStatus: providerContractText.includes(`/internal/v1/mobile/provider/`) ? "contract_ok" : "contract_missing",
    });
  }

  // Deduplicate per route path
  const seen = new Set();
  const deduped = [];
  for (const row of rows) {
    const k = `${row.routePath}::${row.routeZone}`;
    if (seen.has(k)) continue;
    seen.add(k);
    deduped.push(row);
  }

  return deduped.sort((a, b) => a.routeZone.localeCompare(b.routeZone) || a.routePath.localeCompare(b.routePath));
}

function inferTier3ProviderFiles(route) {
  /** @type {string[]} */
  const files = [];

  // Facility selection
  if (route.zone === "facility") {
    files.push("apps/mobile/provider-app/src/screens/SelectFacilityScreen.tsx");
  }

  // Workspace selection (expected to be added in Tier-3 wave)
  if (route.zone === "workspace") {
    files.push("apps/mobile/provider-app/src/screens/SelectWorkspaceScreen.tsx");
  }

  // Shift ops (start/active/handover)
  if (route.zone === "shift") {
    files.push(
      "apps/mobile/provider-app/src/screens/provider/ScheduleScreen.tsx",
      "apps/mobile/provider-app/src/screens/provider/ShiftHandoffScreen.tsx"
    );
  }

  // Scheduling routes live in zone=queue in the canonical web registry
  if (route.path.startsWith("/scheduling")) {
    files.push("apps/mobile/provider-app/src/screens/provider/ScheduleScreen.tsx");
  }

  return Array.from(new Set(files));
}

function inferTier3CitizenFiles(route) {
  /** @type {string[]} */
  const files = [];
  if (isTier3CitizenFacilityRoute(route)) {
    files.push(
      "apps/mobile/citizen-app/src/screens/FacilityDirectoryScreen.tsx",
      "apps/mobile/citizen-app/src/screens/FacilityDetailScreen.tsx"
    );
  }
  return Array.from(new Set(files));
}

function inferTier3Wave2CitizenFiles(route) {
  /** @type {string[]} */
  const files = [];

  // Existing sections
  if (route.path === "/citizen/id-recovery") {
    files.push("apps/mobile/citizen-app/src/screens/personal/IdRecoverySection.tsx");
  }

  // Consent exists (screen)
  if (route.path === "/consent") {
    files.push("apps/mobile/citizen-app/src/screens/personal/ConsentScreen.tsx");
  }

  // Settings contains account deletion affordance (deleteAccount)
  if (route.path === "/account-deletion") {
    files.push("apps/mobile/citizen-app/src/screens/personal/SettingsSection.tsx");
  }

  // Health ID request is represented by Health ID section for now
  if (route.path === "/citizen/health-id/request") {
    files.push("apps/mobile/citizen-app/src/screens/personal/HealthIdSection.tsx");
  }

  // Long-tail placeholders to be implemented in wave 2
  if (route.path === "/citizen/record-sharing") {
    files.push("apps/mobile/citizen-app/src/screens/personal/RecordSharingScreen.tsx");
  }
  if (route.path === "/share/claim") {
    files.push("apps/mobile/citizen-app/src/screens/personal/ClaimSharedDocumentsScreen.tsx");
  }
  if (route.path === "/verify/credential") {
    files.push("apps/mobile/citizen-app/src/screens/personal/VerifyCredentialScreen.tsx");
  }
  if (route.path === "/citizen/delegated-pickup") {
    files.push("apps/mobile/citizen-app/src/screens/personal/DelegatedPickupScreen.tsx");
  }
  if (route.path === "/privacy") {
    files.push("apps/mobile/citizen-app/src/screens/personal/PrivacyPolicyScreen.tsx");
  }
  if (route.path === "/terms") {
    files.push("apps/mobile/citizen-app/src/screens/personal/TermsOfUseScreen.tsx");
  }

  return Array.from(new Set(files));
}

function evaluateTier3Mapping() {
  /** @type {Array<any>} */
  const rows = [];

  const providerContractText = (() => {
    const p = path.join(repoRoot, "contracts", "openapi", "mobile-provider.openapi.yaml");
    return fileExists(p) ? readText(p) : "";
  })();
  const citizenContractText = (() => {
    const p = path.join(repoRoot, "contracts", "openapi", "mobile-citizen.openapi.yaml");
    return fileExists(p) ? readText(p) : "";
  })();

  for (const r of allRoutes) {
    // Provider ops foundation
    if (isTier3ProviderOpsRoute(r)) {
      const providerFiles = inferTier3ProviderFiles(r);
      const missingFiles = providerFiles.filter((f) => !providerIndex.has(normalizeSlashes(f)));
      let contractStatus = "contract_missing";
      if (providerContractText.includes("/internal/v1/facilities")) {
        contractStatus = providerOpenApiOpsFoundationContractOk(providerContractText) ? "contract_ok" : "contract_partial";
      }
      rows.push({
        tier: "tier3",
        routePath: r.path,
        routeTitle: r.pageTitle,
        routeZone: r.zone,
        routeGuard: r.guard,
        routeNavZone: r.navZone ?? null,
        mobileApp: "provider",
        mobileFiles: providerFiles,
        uiStatus: missingFiles.length === 0 ? "done_ui" : "missing_ui",
        missingFiles,
        contractStatus,
      });
    }

    // Citizen facility baseline
    if (isTier3CitizenFacilityRoute(r)) {
      const citizenFiles = inferTier3CitizenFiles(r);
      const missingFiles = citizenFiles.filter((f) => !citizenIndex.has(normalizeSlashes(f)));
      let contractStatus = "contract_missing";
      if (citizenContractText.includes("/internal/v1/facilities")) {
        contractStatus = citizenOpenApiFacilitiesStrictOk(citizenContractText) ? "contract_ok" : "contract_partial";
      }
      rows.push({
        tier: "tier3",
        routePath: r.path,
        routeTitle: r.pageTitle,
        routeZone: r.zone,
        routeGuard: r.guard,
        routeNavZone: r.navZone ?? null,
        mobileApp: "citizen",
        mobileFiles: citizenFiles,
        uiStatus: missingFiles.length === 0 ? "done_ui" : "missing_ui",
        missingFiles,
        contractStatus,
      });
    }
  }

  // Deduplicate per app+route path
  const seen = new Set();
  const deduped = [];
  for (const row of rows) {
    const k = `${row.mobileApp}::${row.routePath}`;
    if (seen.has(k)) continue;
    seen.add(k);
    deduped.push(row);
  }

  return deduped.sort((a, b) =>
    a.mobileApp.localeCompare(b.mobileApp) ||
    a.routeZone.localeCompare(b.routeZone) ||
    a.routePath.localeCompare(b.routePath)
  );
}

function evaluateTier3Wave2Mapping() {
  /** @type {Array<any>} */
  const rows = [];

  const citizenContractText = (() => {
    const p = path.join(repoRoot, "contracts", "openapi", "mobile-citizen.openapi.yaml");
    return fileExists(p) ? readText(p) : "";
  })();

  for (const r of allRoutes) {
    if (!isTier3Wave2CitizenLongtailRoute(r)) continue;
    const citizenFiles = inferTier3Wave2CitizenFiles(r);
    const missingFiles = citizenFiles.filter((f) => !citizenIndex.has(normalizeSlashes(f)));
    let contractStatus = "contract_missing";
    if (citizenContractText.includes("/internal/v1/mobile/citizen/share/issue")) {
      contractStatus = citizenOpenApiLongtailStrictOk(citizenContractText) ? "contract_ok" : "contract_partial";
    }
    rows.push({
      tier: "tier3-wave2",
      routePath: r.path,
      routeTitle: r.pageTitle,
      routeZone: r.zone,
      routeGuard: r.guard,
      routeNavZone: r.navZone ?? null,
      mobileApp: "citizen",
      mobileFiles: citizenFiles,
      uiStatus: missingFiles.length === 0 ? "done_ui" : "missing_ui",
      missingFiles,
      contractStatus,
    });
  }

  // Deduplicate per route
  const seen = new Set();
  const deduped = [];
  for (const row of rows) {
    const k = row.routePath;
    if (seen.has(k)) continue;
    seen.add(k);
    deduped.push(row);
  }

  return deduped.sort((a, b) => a.routePath.localeCompare(b.routePath));
}

function evaluateTier3Wave3Mapping() {
  /** @type {Array<any>} */
  const rows = [];

  const providerContractText = (() => {
    const p = path.join(repoRoot, "contracts", "openapi", "mobile-provider.openapi.yaml");
    return fileExists(p) ? readText(p) : "";
  })();

  for (const r of allRoutes) {
    if (!isTier3Wave3ProviderAdminRegistryRoute(r)) continue;
    const providerFiles = inferTier3Wave3ProviderFiles();
    const missingFiles = providerFiles.filter((f) => !providerIndex.has(normalizeSlashes(f)));
    rows.push({
      tier: "tier3-wave3",
      routePath: r.path,
      routeTitle: r.pageTitle,
      routeZone: r.zone,
      routeGuard: r.guard,
      routeNavZone: r.navZone ?? null,
      mobileApp: "provider",
      mobileFiles: providerFiles,
      uiStatus: missingFiles.length === 0 ? "done_ui" : "missing_ui",
      missingFiles,
      contractStatus:
        providerContractText.includes("/internal/v1/mobile/provider/admin-registry/hub") &&
        providerOpenApiHubContractOk(providerContractText)
          ? "contract_ok"
          : providerContractText.includes("/internal/v1/mobile/provider/admin-registry/hub")
            ? "contract_partial"
            : "contract_missing",
    });
  }

  const seen = new Set();
  const deduped = [];
  for (const row of rows) {
    if (seen.has(row.routePath)) continue;
    seen.add(row.routePath);
    deduped.push(row);
  }

  return deduped.sort((a, b) => a.routePath.localeCompare(b.routePath));
}

function evaluateTier3Wave4Mapping() {
  /** @type {Array<any>} */
  const rows = [];

  const providerContractText = (() => {
    const p = path.join(repoRoot, "contracts", "openapi", "mobile-provider.openapi.yaml");
    return fileExists(p) ? readText(p) : "";
  })();

  for (const r of allRoutes) {
    if (!isTier3Wave4ProviderOpsReportsRoute(r)) continue;
    const providerFiles = inferTier3Wave4ProviderFiles();
    const missingFiles = providerFiles.filter((f) => !providerIndex.has(normalizeSlashes(f)));
    rows.push({
      tier: "tier3-wave4",
      routePath: r.path,
      routeTitle: r.pageTitle,
      routeZone: r.zone,
      routeGuard: r.guard,
      routeNavZone: r.navZone ?? null,
      mobileApp: "provider",
      mobileFiles: providerFiles,
      uiStatus: missingFiles.length === 0 ? "done_ui" : "missing_ui",
      missingFiles,
      contractStatus:
        providerContractText.includes("/internal/v1/mobile/provider/ops-reports/hub") &&
        providerOpenApiHubContractOk(providerContractText)
          ? "contract_ok"
          : providerContractText.includes("/internal/v1/mobile/provider/ops-reports/hub")
            ? "contract_partial"
            : "contract_missing",
    });
  }

  const seen = new Set();
  const deduped = [];
  for (const row of rows) {
    if (seen.has(row.routePath)) continue;
    seen.add(row.routePath);
    deduped.push(row);
  }

  return deduped.sort((a, b) => a.routePath.localeCompare(b.routePath));
}

function evaluateTier3Wave5Mapping() {
  /** @type {Array<any>} */
  const rows = [];

  const providerContractText = (() => {
    const p = path.join(repoRoot, "contracts", "openapi", "mobile-provider.openapi.yaml");
    return fileExists(p) ? readText(p) : "";
  })();

  for (const r of allRoutes) {
    if (!isTier3Wave5ProviderDeveloperRoute(r)) continue;
    const providerFiles = inferTier3Wave5ProviderFiles();
    const missingFiles = providerFiles.filter((f) => !providerIndex.has(normalizeSlashes(f)));
    rows.push({
      tier: "tier3-wave5",
      routePath: r.path,
      routeTitle: r.pageTitle,
      routeZone: r.zone,
      routeGuard: r.guard,
      routeNavZone: r.navZone ?? null,
      mobileApp: "provider",
      mobileFiles: providerFiles,
      uiStatus: missingFiles.length === 0 ? "done_ui" : "missing_ui",
      missingFiles,
      contractStatus:
        providerContractText.includes("/internal/v1/mobile/provider/developer/hub") &&
        providerOpenApiHubContractOk(providerContractText)
          ? "contract_ok"
          : providerContractText.includes("/internal/v1/mobile/provider/developer/hub")
            ? "contract_partial"
            : "contract_missing",
    });
  }

  const seen = new Set();
  const deduped = [];
  for (const row of rows) {
    if (seen.has(row.routePath)) continue;
    seen.add(row.routePath);
    deduped.push(row);
  }

  return deduped.sort((a, b) => a.routePath.localeCompare(b.routePath));
}

function evaluateTier3Wave6Mapping() {
  /** @type {Array<any>} */
  const rows = [];

  const providerContractText = (() => {
    const p = path.join(repoRoot, "contracts", "openapi", "mobile-provider.openapi.yaml");
    return fileExists(p) ? readText(p) : "";
  })();

  for (const r of allRoutes) {
    if (!isTier3Wave6ProviderProfessionalSettingsRoute(r)) continue;
    const providerFiles = inferTier3Wave6ProviderFiles();
    const missingFiles = providerFiles.filter((f) => !providerIndex.has(normalizeSlashes(f)));
    rows.push({
      tier: "tier3-wave6",
      routePath: r.path,
      routeTitle: r.pageTitle,
      routeZone: r.zone,
      routeGuard: r.guard,
      routeNavZone: r.navZone ?? null,
      mobileApp: "provider",
      mobileFiles: providerFiles,
      uiStatus: missingFiles.length === 0 ? "done_ui" : "missing_ui",
      missingFiles,
      contractStatus:
        providerContractText.includes("/internal/v1/mobile/provider/professional-settings/hub") &&
        providerOpenApiHubContractOk(providerContractText)
          ? "contract_ok"
          : providerContractText.includes("/internal/v1/mobile/provider/professional-settings/hub")
            ? "contract_partial"
            : "contract_missing",
    });
  }

  const seen = new Set();
  const deduped = [];
  for (const row of rows) {
    if (seen.has(row.routePath)) continue;
    seen.add(row.routePath);
    deduped.push(row);
  }

  return deduped.sort((a, b) => a.routePath.localeCompare(b.routePath));
}

function evaluateTier3Wave7Mapping() {
  /** @type {Array<any>} */
  const rows = [];

  const providerContractText = (() => {
    const p = path.join(repoRoot, "contracts", "openapi", "mobile-provider.openapi.yaml");
    return fileExists(p) ? readText(p) : "";
  })();

  for (const r of allRoutes) {
    if (!isTier3Wave7ProviderProfessionalChannelsRoute(r)) continue;
    const providerFiles = inferTier3Wave7ProviderFiles();
    const missingFiles = providerFiles.filter((f) => !providerIndex.has(normalizeSlashes(f)));
    rows.push({
      tier: "tier3-wave7",
      routePath: r.path,
      routeTitle: r.pageTitle,
      routeZone: r.zone,
      routeGuard: r.guard,
      routeNavZone: r.navZone ?? null,
      mobileApp: "provider",
      mobileFiles: providerFiles,
      uiStatus: missingFiles.length === 0 ? "done_ui" : "missing_ui",
      missingFiles,
      contractStatus:
        providerContractText.includes("/internal/v1/mobile/provider/professional-channels/hub") &&
        providerOpenApiHubContractOk(providerContractText)
          ? "contract_ok"
          : providerContractText.includes("/internal/v1/mobile/provider/professional-channels/hub")
            ? "contract_partial"
            : "contract_missing",
    });
  }

  const seen = new Set();
  const deduped = [];
  for (const row of rows) {
    if (seen.has(row.routePath)) continue;
    seen.add(row.routePath);
    deduped.push(row);
  }

  return deduped.sort((a, b) => a.routePath.localeCompare(b.routePath));
}

const tier1Rows = evaluateTier1Mapping();
const tier1Summary = {
  generatedAt: GENERATED_AT,
  source: "ui/one-ui-shell/src/lib/routes.ts",
  tier: "tier1",
  total: tier1Rows.length,
  done: tier1Rows.filter((r) => r.status === "done").length,
  missing: tier1Rows.filter((r) => r.status !== "done").length,
};

fs.mkdirSync(path.dirname(outJson), { recursive: true });
fs.writeFileSync(outJson, JSON.stringify({ summary: tier1Summary, rows: tier1Rows }, null, 2) + "\n");

function tier1ToMd() {
  const lines = [];
  lines.push("# Full Mobile Parity Matrix (Tier-1)\n");
  lines.push(`**Generated**: ${tier1Summary.generatedAt}`);
  lines.push(`**Source**: \`${tier1Summary.source}\``);
  lines.push(`**Tier**: ${tier1Summary.tier}`);
  lines.push(`**Status**: done=${tier1Summary.done} missing=${tier1Summary.missing} total=${tier1Summary.total}\n`);

  const byApp = Object.groupBy(tier1Rows, (r) => r.mobileApp);
  for (const app of Object.keys(byApp).sort()) {
    lines.push(`## ${app === "citizen" ? "Citizen app" : "Provider app"}\n`);
    lines.push("| Web route | Title | Guard | Mobile files | Status |");
    lines.push("|---|---|---|---|---|");
    for (const r of byApp[app]) {
      const files = r.mobileFiles.map((f) => `\`${f}\``).join("<br/>");
      const status = r.status === "done" ? "DONE" : `MISSING: ${r.missingFiles.map((f) => `\`${f}\``).join(", ")}`;
      lines.push(`| \`${r.routePath}\` | ${r.routeTitle} | \`${r.routeGuard}\` | ${files} | ${status} |`);
    }
    lines.push("");
  }

  lines.push("## Notes\n");
  lines.push("- Tier-1 is intentionally a **journey set**, not all 252 web routes.\n");
  lines.push("- Update `tools/parity/generate-mobile-parity-matrix.mjs` mapping rules when Tier-1 definition changes.\n");
  return lines.join("\n");
}

fs.writeFileSync(outMd, tier1ToMd());

const tier2Rows = evaluateTier2Mapping();
const tier2Summary = {
  generatedAt: GENERATED_AT,
  source: "ui/one-ui-shell/src/lib/routes.ts",
  tier: "tier2",
  zones: Array.from(tier2Zones).sort(),
  total: tier2Rows.length,
  done_ui: tier2Rows.filter((r) => r.uiStatus === "done_ui").length,
  missing_ui: tier2Rows.filter((r) => r.uiStatus !== "done_ui").length,
};

fs.writeFileSync(outTier2Json, JSON.stringify({ summary: tier2Summary, rows: tier2Rows }, null, 2) + "\n");

function tier2ToMd() {
  const lines = [];
  lines.push("# Mobile Parity Matrix (Tier-2)\n");
  lines.push(`**Generated**: ${tier2Summary.generatedAt}`);
  lines.push(`**Source**: \`${tier2Summary.source}\``);
  lines.push(`**Tier**: ${tier2Summary.tier}`);
  lines.push(`**Zones**: ${tier2Summary.zones.join(", ")}`);
  lines.push(`**UI status**: done_ui=${tier2Summary.done_ui} missing_ui=${tier2Summary.missing_ui} total=${tier2Summary.total}\n`);

  const byZone = Object.groupBy(tier2Rows, (r) => r.routeZone);
  for (const zone of Object.keys(byZone).sort()) {
    lines.push(`## Zone: ${zone}\n`);
    lines.push("| Web route | Title | Guard | Provider screens | UI | Contract |");
    lines.push("|---|---|---|---|---|---|");
    for (const r of byZone[zone]) {
      const files = r.mobileFiles.map((f) => `\`${f}\``).join("<br/>");
      const ui = r.uiStatus === "done_ui" ? "DONE" : `MISSING: ${r.missingFiles.map((f) => `\`${f}\``).join(", ")}`;
      lines.push(`| \`${r.routePath}\` | ${r.routeTitle} | \`${r.routeGuard}\` | ${files} | ${ui} | ${r.contractStatus} |`);
    }
    lines.push("");
  }

  lines.push("## Notes\n");
  lines.push("- Tier-2 is zone-based and intentionally provider-heavy for EHR/Queue/Lab/Pharmacy/Marketplace ops.\n");
  lines.push("- Contract status is filled in during the Tier-2 contracts/BFF task.\n");
  return lines.join("\n");
}

fs.writeFileSync(outTier2Md, tier2ToMd());

const tier3Rows = evaluateTier3Mapping();
const tier3Summary = {
  generatedAt: GENERATED_AT,
  source: "ui/one-ui-shell/src/lib/routes.ts",
  tier: "tier3",
  scope: {
    provider: "facility/workspace/shift + /scheduling*",
    citizen: "facility",
  },
  total: tier3Rows.length,
  done_ui: tier3Rows.filter((r) => r.uiStatus === "done_ui").length,
  missing_ui: tier3Rows.filter((r) => r.uiStatus !== "done_ui").length,
  contract_ok: tier3Rows.filter((r) => r.contractStatus === "contract_ok").length,
  contract_partial: tier3Rows.filter((r) => r.contractStatus === "contract_partial").length,
  contract_missing: tier3Rows.filter((r) => r.contractStatus === "contract_missing").length,
};

fs.writeFileSync(outTier3Json, JSON.stringify({ summary: tier3Summary, rows: tier3Rows }, null, 2) + "\n");

function tier3ToMd() {
  const lines = [];
  lines.push("# Mobile Parity Matrix (Tier-3 wave 1)\n");
  lines.push(`**Generated**: ${tier3Summary.generatedAt}`);
  lines.push(`**Source**: \`${tier3Summary.source}\``);
  lines.push(`**Tier**: ${tier3Summary.tier}`);
  lines.push(`**Scope**: provider=${tier3Summary.scope.provider}; citizen=${tier3Summary.scope.citizen}`);
  lines.push(
    `**UI status**: done_ui=${tier3Summary.done_ui} missing_ui=${tier3Summary.missing_ui} total=${tier3Summary.total}`
  );
  lines.push(
    `**Contract**: contract_ok=${tier3Summary.contract_ok} contract_partial=${tier3Summary.contract_partial} contract_missing=${tier3Summary.contract_missing}\n`
  );

  const byApp = Object.groupBy(tier3Rows, (r) => r.mobileApp);
  for (const app of Object.keys(byApp).sort()) {
    lines.push(`## ${app === "citizen" ? "Citizen app" : "Provider app"}\n`);
    lines.push("| Web route | Title | Zone | Guard | Mobile files | UI | Contract |");
    lines.push("|---|---|---|---|---|---|---|");
    for (const r of byApp[app]) {
      const files = r.mobileFiles.map((f) => `\`${f}\``).join("<br/>");
      const ui = r.uiStatus === "done_ui" ? "DONE" : `MISSING: ${r.missingFiles.map((f) => `\`${f}\``).join(", ")}`;
      lines.push(`| \`${r.routePath}\` | ${r.routeTitle} | \`${r.routeZone}\` | \`${r.routeGuard}\` | ${files} | ${ui} | ${r.contractStatus} |`);
    }
    lines.push("");
  }

  lines.push("## Notes\n");
  lines.push("- Tier-3 wave 1 is intentionally scoped to ops foundation (provider) + facility baseline (citizen).\n");
  lines.push(
    "- **Contract** uses typed components in `contracts/openapi/mobile-*.openapi.yaml` (facilities/workspaces + envelopes); `contract_partial` / `contract_missing` indicate drift from those schemas.\n"
  );
  return lines.join("\n");
}

fs.writeFileSync(outTier3Md, tier3ToMd());

const tier3Wave2Rows = evaluateTier3Wave2Mapping();
const tier3Wave2Summary = {
  generatedAt: GENERATED_AT,
  source: "ui/one-ui-shell/src/lib/routes.ts",
  tier: "tier3-wave2",
  scope: "citizen-longtail",
  total: tier3Wave2Rows.length,
  done_ui: tier3Wave2Rows.filter((r) => r.uiStatus === "done_ui").length,
  missing_ui: tier3Wave2Rows.filter((r) => r.uiStatus !== "done_ui").length,
  contract_ok: tier3Wave2Rows.filter((r) => r.contractStatus === "contract_ok").length,
  contract_partial: tier3Wave2Rows.filter((r) => r.contractStatus === "contract_partial").length,
  contract_missing: tier3Wave2Rows.filter((r) => r.contractStatus === "contract_missing").length,
};

fs.writeFileSync(outTier3Wave2Json, JSON.stringify({ summary: tier3Wave2Summary, rows: tier3Wave2Rows }, null, 2) + "\n");

function tier3Wave2ToMd() {
  const lines = [];
  lines.push("# Mobile Parity Matrix (Tier-3 wave 2)\n");
  lines.push(`**Generated**: ${tier3Wave2Summary.generatedAt}`);
  lines.push(`**Source**: \`${tier3Wave2Summary.source}\``);
  lines.push(`**Tier**: ${tier3Wave2Summary.tier}`);
  lines.push(`**Scope**: ${tier3Wave2Summary.scope}`);
  lines.push(
    `**UI status**: done_ui=${tier3Wave2Summary.done_ui} missing_ui=${tier3Wave2Summary.missing_ui} total=${tier3Wave2Summary.total}`
  );
  lines.push(
    `**Contract**: contract_ok=${tier3Wave2Summary.contract_ok} contract_partial=${tier3Wave2Summary.contract_partial} contract_missing=${tier3Wave2Summary.contract_missing}\n`
  );

  lines.push("| Web route | Title | Guard | Mobile files | UI | Contract |");
  lines.push("|---|---|---|---|---|---|");
  for (const r of tier3Wave2Rows) {
    const files = r.mobileFiles.map((f) => `\`${f}\``).join("<br/>");
    const ui = r.uiStatus === "done_ui" ? "DONE" : `MISSING: ${r.missingFiles.map((f) => `\`${f}\``).join(", ")}`;
    lines.push(`| \`${r.routePath}\` | ${r.routeTitle} | \`${r.routeGuard}\` | ${files} | ${ui} | ${r.contractStatus} |`);
  }

  lines.push("\n## Notes\n");
  lines.push("- Tier-3 wave 2 focuses on citizen long-tail life flows (record sharing, claim/verify, delegated pickup, legal).\n");
  return lines.join("\n");
}

fs.writeFileSync(outTier3Wave2Md, tier3Wave2ToMd());

const tier3Wave3Rows = evaluateTier3Wave3Mapping();
const tier3Wave3Summary = {
  generatedAt: GENERATED_AT,
  source: "ui/one-ui-shell/src/lib/routes.ts",
  tier: "tier3-wave3",
  scope: "provider-admin-registry-hub",
  total: tier3Wave3Rows.length,
  done_ui: tier3Wave3Rows.filter((r) => r.uiStatus === "done_ui").length,
  missing_ui: tier3Wave3Rows.filter((r) => r.uiStatus !== "done_ui").length,
  ...contractAudit(tier3Wave3Rows),
};

fs.writeFileSync(outTier3Wave3Json, JSON.stringify({ summary: tier3Wave3Summary, rows: tier3Wave3Rows }, null, 2) + "\n");

function tier3Wave3ToMd() {
  const lines = [];
  lines.push("# Mobile Parity Matrix (Tier-3 wave 3)\n");
  lines.push(`**Generated**: ${tier3Wave3Summary.generatedAt}`);
  lines.push(`**Source**: \`${tier3Wave3Summary.source}\``);
  lines.push(`**Tier**: ${tier3Wave3Summary.tier}`);
  lines.push(`**Scope**: ${tier3Wave3Summary.scope}`);
  lines.push(
    `**UI status**: done_ui=${tier3Wave3Summary.done_ui} missing_ui=${tier3Wave3Summary.missing_ui} total=${tier3Wave3Summary.total}`
  );
  lines.push(
    `**Contract**: contract_ok=${tier3Wave3Summary.contract_ok} contract_partial=${tier3Wave3Summary.contract_partial} contract_missing=${tier3Wave3Summary.contract_missing}\n`
  );

  lines.push("| Web route | Title | Guard | Mobile files | UI | Contract |");
  lines.push("|---|---|---|---|---|---|");
  for (const r of tier3Wave3Rows) {
    const files = r.mobileFiles.map((f) => `\`${f}\``).join("<br/>");
    const ui = r.uiStatus === "done_ui" ? "DONE" : `MISSING: ${r.missingFiles.map((f) => `\`${f}\``).join(", ")}`;
    lines.push(`| \`${r.routePath}\` | ${r.routeTitle} | \`${r.routeGuard}\` | ${files} | ${ui} | ${r.contractStatus} |`);
  }

  lines.push("\n## Notes\n");
  lines.push(
    "- Tier-3 wave 3 maps multiple web admin/registry landings to one provider **Tools → Admin** hub plus a thin BFF hub endpoint.\n"
  );
  return lines.join("\n");
}

fs.writeFileSync(outTier3Wave3Md, tier3Wave3ToMd());

const tier3Wave4Rows = evaluateTier3Wave4Mapping();
const tier3Wave4Summary = {
  generatedAt: GENERATED_AT,
  source: "ui/one-ui-shell/src/lib/routes.ts",
  tier: "tier3-wave4",
  scope: "provider-operations-reports-hub",
  total: tier3Wave4Rows.length,
  done_ui: tier3Wave4Rows.filter((r) => r.uiStatus === "done_ui").length,
  missing_ui: tier3Wave4Rows.filter((r) => r.uiStatus !== "done_ui").length,
  ...contractAudit(tier3Wave4Rows),
};

fs.writeFileSync(outTier3Wave4Json, JSON.stringify({ summary: tier3Wave4Summary, rows: tier3Wave4Rows }, null, 2) + "\n");

function tier3Wave4ToMd() {
  const lines = [];
  lines.push("# Mobile Parity Matrix (Tier-3 wave 4)\n");
  lines.push(`**Generated**: ${tier3Wave4Summary.generatedAt}`);
  lines.push(`**Source**: \`${tier3Wave4Summary.source}\``);
  lines.push(`**Tier**: ${tier3Wave4Summary.tier}`);
  lines.push(`**Scope**: ${tier3Wave4Summary.scope}`);
  lines.push(
    `**UI status**: done_ui=${tier3Wave4Summary.done_ui} missing_ui=${tier3Wave4Summary.missing_ui} total=${tier3Wave4Summary.total}`
  );
  lines.push(
    `**Contract**: contract_ok=${tier3Wave4Summary.contract_ok} contract_partial=${tier3Wave4Summary.contract_partial} contract_missing=${tier3Wave4Summary.contract_missing}\n`
  );

  lines.push("| Web route | Title | Guard | Mobile files | UI | Contract |");
  lines.push("|---|---|---|---|---|---|");
  for (const r of tier3Wave4Rows) {
    const files = r.mobileFiles.map((f) => `\`${f}\``).join("<br/>");
    const ui = r.uiStatus === "done_ui" ? "DONE" : `MISSING: ${r.missingFiles.map((f) => `\`${f}\``).join(", ")}`;
    lines.push(`| \`${r.routePath}\` | ${r.routeTitle} | \`${r.routeGuard}\` | ${files} | ${ui} | ${r.contractStatus} |`);
  }

  lines.push("\n## Notes\n");
  lines.push(
    "- Tier-3 wave 4 maps web **operations** and **reports** landings to provider **Tools → Ops+** plus a thin BFF hub endpoint.\n"
  );
  return lines.join("\n");
}

fs.writeFileSync(outTier3Wave4Md, tier3Wave4ToMd());

const tier3Wave5Rows = evaluateTier3Wave5Mapping();
const tier3Wave5Summary = {
  generatedAt: GENERATED_AT,
  source: "ui/one-ui-shell/src/lib/routes.ts",
  tier: "tier3-wave5",
  scope: "provider-developer-portal-hub",
  total: tier3Wave5Rows.length,
  done_ui: tier3Wave5Rows.filter((r) => r.uiStatus === "done_ui").length,
  missing_ui: tier3Wave5Rows.filter((r) => r.uiStatus !== "done_ui").length,
  ...contractAudit(tier3Wave5Rows),
};

fs.writeFileSync(outTier3Wave5Json, JSON.stringify({ summary: tier3Wave5Summary, rows: tier3Wave5Rows }, null, 2) + "\n");

function tier3Wave5ToMd() {
  const lines = [];
  lines.push("# Mobile Parity Matrix (Tier-3 wave 5)\n");
  lines.push(`**Generated**: ${tier3Wave5Summary.generatedAt}`);
  lines.push(`**Source**: \`${tier3Wave5Summary.source}\``);
  lines.push(`**Tier**: ${tier3Wave5Summary.tier}`);
  lines.push(`**Scope**: ${tier3Wave5Summary.scope}`);
  lines.push(
    `**UI status**: done_ui=${tier3Wave5Summary.done_ui} missing_ui=${tier3Wave5Summary.missing_ui} total=${tier3Wave5Summary.total}`
  );
  lines.push(
    `**Contract**: contract_ok=${tier3Wave5Summary.contract_ok} contract_partial=${tier3Wave5Summary.contract_partial} contract_missing=${tier3Wave5Summary.contract_missing}\n`
  );

  lines.push("| Web route | Title | Guard | Mobile files | UI | Contract |");
  lines.push("|---|---|---|---|---|---|");
  for (const r of tier3Wave5Rows) {
    const files = r.mobileFiles.map((f) => `\`${f}\``).join("<br/>");
    const ui = r.uiStatus === "done_ui" ? "DONE" : `MISSING: ${r.missingFiles.map((f) => `\`${f}\``).join(", ")}`;
    lines.push(`| \`${r.routePath}\` | ${r.routeTitle} | \`${r.routeGuard}\` | ${files} | ${ui} | ${r.contractStatus} |`);
  }

  lines.push("\n## Notes\n");
  lines.push(
    "- Tier-3 wave 5 maps web **developer** portal landings to provider **Tools → Dev** plus a thin BFF hub endpoint.\n"
  );
  return lines.join("\n");
}

fs.writeFileSync(outTier3Wave5Md, tier3Wave5ToMd());

const tier3Wave6Rows = evaluateTier3Wave6Mapping();
const tier3Wave6Summary = {
  generatedAt: GENERATED_AT,
  source: "ui/one-ui-shell/src/lib/routes.ts",
  tier: "tier3-wave6",
  scope: "provider-professional-settings-hub",
  total: tier3Wave6Rows.length,
  done_ui: tier3Wave6Rows.filter((r) => r.uiStatus === "done_ui").length,
  missing_ui: tier3Wave6Rows.filter((r) => r.uiStatus !== "done_ui").length,
  ...contractAudit(tier3Wave6Rows),
};

fs.writeFileSync(outTier3Wave6Json, JSON.stringify({ summary: tier3Wave6Summary, rows: tier3Wave6Rows }, null, 2) + "\n");

function tier3Wave6ToMd() {
  const lines = [];
  lines.push("# Mobile Parity Matrix (Tier-3 wave 6)\n");
  lines.push(`**Generated**: ${tier3Wave6Summary.generatedAt}`);
  lines.push(`**Source**: \`${tier3Wave6Summary.source}\``);
  lines.push(`**Tier**: ${tier3Wave6Summary.tier}`);
  lines.push(`**Scope**: ${tier3Wave6Summary.scope}`);
  lines.push(
    `**UI status**: done_ui=${tier3Wave6Summary.done_ui} missing_ui=${tier3Wave6Summary.missing_ui} total=${tier3Wave6Summary.total}`
  );
  lines.push(
    `**Contract**: contract_ok=${tier3Wave6Summary.contract_ok} contract_partial=${tier3Wave6Summary.contract_partial} contract_missing=${tier3Wave6Summary.contract_missing}\n`
  );

  lines.push("| Web route | Title | Guard | Mobile files | UI | Contract |");
  lines.push("|---|---|---|---|---|---|");
  for (const r of tier3Wave6Rows) {
    const files = r.mobileFiles.map((f) => `\`${f}\``).join("<br/>");
    const ui = r.uiStatus === "done_ui" ? "DONE" : `MISSING: ${r.missingFiles.map((f) => `\`${f}\``).join(", ")}`;
    lines.push(`| \`${r.routePath}\` | ${r.routeTitle} | \`${r.routeGuard}\` | ${files} | ${ui} | ${r.contractStatus} |`);
  }

  lines.push("\n## Notes\n");
  lines.push(
    "- Tier-3 wave 6 maps web **`/settings*`** professional landings to provider **Tools → Prefs** plus a thin BFF hub endpoint.\n"
  );
  return lines.join("\n");
}

fs.writeFileSync(outTier3Wave6Md, tier3Wave6ToMd());

const tier3Wave7Rows = evaluateTier3Wave7Mapping();
const tier3Wave7Summary = {
  generatedAt: GENERATED_AT,
  source: "ui/one-ui-shell/src/lib/routes.ts",
  tier: "tier3-wave7",
  scope: "provider-professional-channels-hub",
  total: tier3Wave7Rows.length,
  done_ui: tier3Wave7Rows.filter((r) => r.uiStatus === "done_ui").length,
  missing_ui: tier3Wave7Rows.filter((r) => r.uiStatus !== "done_ui").length,
  ...contractAudit(tier3Wave7Rows),
};

fs.writeFileSync(outTier3Wave7Json, JSON.stringify({ summary: tier3Wave7Summary, rows: tier3Wave7Rows }, null, 2) + "\n");

function tier3Wave7ToMd() {
  const lines = [];
  lines.push("# Mobile Parity Matrix (Tier-3 wave 7)\n");
  lines.push(`**Generated**: ${tier3Wave7Summary.generatedAt}`);
  lines.push(`**Source**: \`${tier3Wave7Summary.source}\``);
  lines.push(`**Tier**: ${tier3Wave7Summary.tier}`);
  lines.push(`**Scope**: ${tier3Wave7Summary.scope}`);
  lines.push(
    `**UI status**: done_ui=${tier3Wave7Summary.done_ui} missing_ui=${tier3Wave7Summary.missing_ui} total=${tier3Wave7Summary.total}`
  );
  lines.push(
    `**Contract**: contract_ok=${tier3Wave7Summary.contract_ok} contract_partial=${tier3Wave7Summary.contract_partial} contract_missing=${tier3Wave7Summary.contract_missing}\n`
  );

  lines.push("| Web route | Title | Guard | Mobile files | UI | Contract |");
  lines.push("|---|---|---|---|---|---|");
  for (const r of tier3Wave7Rows) {
    const files = r.mobileFiles.map((f) => `\`${f}\``).join("<br/>");
    const ui = r.uiStatus === "done_ui" ? "DONE" : `MISSING: ${r.missingFiles.map((f) => `\`${f}\``).join(", ")}`;
    lines.push(`| \`${r.routePath}\` | ${r.routeTitle} | \`${r.routeGuard}\` | ${files} | ${ui} | ${r.contractStatus} |`);
  }

  lines.push("\n## Notes\n");
  lines.push(
    "- Tier-3 wave 7 maps **omnichannel**, **coverage**, **credentials**, and **public-health** drill-down web routes to provider **Tools → CX+** plus a thin BFF hub endpoint.\n"
  );
  return lines.join("\n");
}

fs.writeFileSync(outTier3Wave7Md, tier3Wave7ToMd());

console.log(
  `Wrote:\n- ${path.relative(repoRoot, outJson)}\n- ${path.relative(repoRoot, outMd)}\n- ${path.relative(repoRoot, outTier2Json)}\n- ${path.relative(repoRoot, outTier2Md)}\n- ${path.relative(repoRoot, outTier3Json)}\n- ${path.relative(repoRoot, outTier3Md)}\n- ${path.relative(repoRoot, outTier3Wave2Json)}\n- ${path.relative(repoRoot, outTier3Wave2Md)}\n- ${path.relative(repoRoot, outTier3Wave3Json)}\n- ${path.relative(repoRoot, outTier3Wave3Md)}\n- ${path.relative(repoRoot, outTier3Wave4Json)}\n- ${path.relative(repoRoot, outTier3Wave4Md)}\n- ${path.relative(repoRoot, outTier3Wave5Json)}\n- ${path.relative(repoRoot, outTier3Wave5Md)}\n- ${path.relative(repoRoot, outTier3Wave6Json)}\n- ${path.relative(repoRoot, outTier3Wave6Md)}\n- ${path.relative(repoRoot, outTier3Wave7Json)}\n- ${path.relative(repoRoot, outTier3Wave7Md)}`
);
