#!/usr/bin/env node
/**
 * Phase 3 — Coherent Experience Orchestration generator.
 * Inputs: Phase 1 recovery map + Phase 2 journey/completion matrices.
 * Run: node scripts/product/generate-experience-orchestration.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");
const DATE = new Date().toISOString();
const OUT_DOCS = path.join(ROOT, "docs/product");
const OUT_REPORTS = path.join(ROOT, "reports/product");

const ORCH_STATUSES = [
  "coherent", "partial", "isolated-page", "orphan-backend", "orphan-frontend",
  "mock-stub", "unclear-intent", "missing-journey",
];

function readText(p) {
  try { return fs.readFileSync(p, "utf8"); } catch { return ""; }
}
function csvEscape(v) { return `"${String(v ?? "").replace(/"/g, '""')}"`; }
function mdTable(rows, cols, max = 40) {
  const slice = rows.slice(0, max);
  const h = `| ${cols.join(" | ")} |`;
  const s = `| ${cols.map(() => "---").join(" | ")} |`;
  const b = slice.map((r) => `| ${cols.map((c) => String(r[c] ?? "").replace(/\|/g, "\\|").slice(0, 60)).join(" | ")} |`).join("\n");
  const more = rows.length > max ? `\n\n_…and ${rows.length - max} more in JSON/CSV._\n` : "";
  return `${h}\n${s}\n${b}${more}`;
}

const recovery = JSON.parse(readText(path.join(OUT_REPORTS, "product-truth-recovery-map.json")));
const journeysDoc = JSON.parse(readText(path.join(OUT_REPORTS, "core-transaction-journey-maps.json")));
const entries = recovery.entries || [];
const journeys = journeysDoc.journeys || [];

const ZONE_ACTOR = {
  auth: { actor: "citizen", role: "person", context: "authentication" },
  home: { actor: "citizen", role: "person", context: "life-hub" },
  citizen: { actor: "citizen", role: "person", context: "citizen-portal" },
  wellness: { actor: "citizen", role: "person", context: "wellness" },
  queue: { actor: "provider", role: "clinical-staff", context: "facility-clinical" },
  ehr: { actor: "provider", role: "clinical-staff", context: "patient-chart" },
  admin: { actor: "system-administrator", role: "platform-admin", context: "governance" },
  registry: { actor: "registry-administrator", role: "registry-ops", context: "registry" },
  finance: { actor: "finance-billing-user", role: "finance", context: "billing" },
  pharmacy: { actor: "pharmacist", role: "pharmacy", context: "dispensing" },
  lab: { actor: "laboratory-user", role: "lab", context: "laboratory" },
  operations: { actor: "facility-administrator", role: "ops", context: "facility-operations" },
  developer: { actor: "implementer", role: "integration", context: "developer" },
  marketplace: { actor: "citizen", role: "consumer", context: "marketplace" },
  learning: { actor: "provider", role: "learner", context: "competency" },
  support: { actor: "implementer", role: "support", context: "support" },
  monitoring: { actor: "citizen", role: "person", context: "remote-monitoring" },
  intelligent: { actor: "citizen", role: "person", context: "guidance" },
};

function routeToPattern(route) {
  return route.replace(/\[([^\]]+)\]/g, "[^/]+").replace(/\//g, "\\/");
}
function routeMatches(route, patternRoute) {
  if (route === patternRoute) return true;
  return new RegExp(`^${routeToPattern(patternRoute)}`).test(route);
}

const routeJourneyIndex = new Map();
for (const j of journeys) {
  for (const r of j.frontendRoutes || []) {
    if (!routeJourneyIndex.has(r)) routeJourneyIndex.set(r, []);
    routeJourneyIndex.get(r).push(j);
  }
}
function findJourneysForRoute(routePath) {
  const direct = routeJourneyIndex.get(routePath) || [];
  const prefix = journeys.filter((j) =>
    (j.frontendRoutes || []).some((jr) => routeMatches(routePath, jr) || routeMatches(jr, routePath))
  );
  const merged = new Map();
  for (const j of [...direct, ...prefix]) merged.set(j.journeyId, j);
  return [...merged.values()];
}

const STUB_PATTERNS = [
  /coming\s+soon/i,
  /BLOCKED_NOT_LIVE_CAPABLE/i,
  /from\s+["']@\/.*fixtures/i,
  /from\s+["'].*fixtures\/core-transactions/i,
  /mockData\s*=/i,
  /MOCK_[A-Z_]+\s*=/,
  /adapter\s+stub/i,
  /fixture\s+mode/i,
  /JSON\.stringify\s*\(\s*\{/,
];
function detectStub(sourcePath) {
  const text = readText(path.join(ROOT, sourcePath));
  return text ? STUB_PATTERNS.some((re) => re.test(text)) : false;
}

const HOOK_PATTERNS = [
  /useCoreTransaction\w+/,
  /useEncounterCoreTransaction/,
  /useEncounters?\b/,
  /usePatients?\b/,
  /useQueue\w+/,
];
function detectHookFromSource(sourcePath) {
  if (!sourcePath) return false;
  const text = readText(path.join(ROOT, sourcePath));
  return text ? HOOK_PATTERNS.some((re) => re.test(text)) : false;
}

const routesTs = readText(path.join(ROOT, "ui/one-ui-shell/src/lib/routes.ts"));
const routeMeta = new Map();
for (const m of routesTs.matchAll(/\{\s*path:\s*"([^"]+)"[^}]*zone:\s*"([^"]+)"[^}]*guard:\s*"([^"]+)"[^}]*pageTitle:\s*"([^"]+)"/g)) {
  routeMeta.set(m[1], { zone: m[2], guard: m[3], pageTitle: m[4] });
}

const orchestrationEntries = [];

const frontendRouteEntries = entries.filter((e) =>
  e.componentType === "frontend-route" || e.componentType === "frontend-page-unregistered"
);
for (const e of frontendRouteEntries) {
  const routePath = e.canonicalName;
  const meta = routeMeta.get(routePath) || {};
  const zoneInfo = ZONE_ACTOR[meta.zone || e.serviceDomainPlane?.split("/")[1]] || {};
  const matchedJourneys = findJourneysForRoute(routePath);
  const primary = matchedJourneys[0];
  const hasHook = Boolean(e.apiClientHook) || detectHookFromSource(e.sourcePath);
  const isStub = detectStub(e.sourcePath);
  const isUnregistered = e.componentType === "frontend-page-unregistered";

  let status = "partial";
  if (isStub) status = "mock-stub";
  else if (isUnregistered) status = "orphan-frontend";
  else if (matchedJourneys.length === 0 && !hasHook) status = "isolated-page";
  else if (matchedJourneys.length === 0) status = "unclear-intent";
  else if (
    (primary?.currentImplementationStatus === "wired" ||
      primary?.completionClassification === "transaction-complete") &&
    hasHook
  ) {
    status = "coherent";
  } else if (matchedJourneys.length > 0) status = "missing-journey";

  orchestrationEntries.push({
    surfaceType: "frontend-route",
    surfaceId: routePath,
    actorType: primary?.initiatingActor || zoneInfo.actor || "unknown",
    actorRole: zoneInfo.role || meta.guard || "unknown",
    context: primary?.context || zoneInfo.context || meta.zone || "unknown",
    intent: meta.pageTitle || e.aliases || "",
    transaction: primary ? `${primary.coreTransactionType}:${primary.journeyName}` : "",
    journeyId: primary?.journeyId || "",
    servicesInvolved: primary ? (primary.backendServices || []).join("; ") : "experience-bff",
    apisInvolved: primary?.apis || "",
    registriesInvolved: (primary?.backendServices || []).filter((s) =>
      ["vito-service", "varapi-service", "tuso-service", "zibo-service", "product-registry-service"].includes(s)
    ).join("; "),
    trustSecurityChecks: primary?.trustSecurityChecks || meta.guard || "auth",
    dataRecords: primary?.dataReadWritten || "",
    eventTopics: primary?.eventsEmitted || "",
    uiSurface: routePath,
    mobileSurface: (primary?.mobileScreens || []).join("; "),
    nompiloSupport: routePath.includes("/ask") || primary?.journeyId ? "expected" : "optional",
    relatedFeatures: (primary?.relatedJourneys || []).join("; "),
    nextActions: primary?.steps?.split("→").pop()?.trim() || "",
    completionCriteria: primary?.productOwnerAcceptanceTest || "",
    doctrineCompliance: primary ? "mapped-to-core-transaction" : "unmapped",
    orchestrationStatus: status,
    sourcePath: e.sourcePath,
    apiClientHook: e.apiClientHook,
  });
}

const mobileEntries = entries.filter((e) => e.componentType === "mobile-screen");
for (const e of mobileEntries) {
  const matchedJourneys = journeys.filter((j) =>
    (j.mobileScreens || []).some((ms) => e.sourcePath.includes(path.basename(ms)) || ms.includes(e.sourcePath))
  );
  const primary = matchedJourneys[0];
  const app = e.sourcePath.includes("citizen-app") ? "citizen" : "provider";
  let status = primary ? "partial" : "missing-journey";
  if (
    primary?.currentImplementationStatus === "wired" ||
    primary?.completionClassification === "transaction-complete"
  ) {
    status = "coherent";
  }

  orchestrationEntries.push({
    surfaceType: "mobile-screen",
    surfaceId: e.canonicalName,
    actorType: primary?.initiatingActor || (app === "citizen" ? "citizen" : "provider"),
    actorRole: app === "citizen" ? "person" : "clinical-staff",
    context: primary?.context || `${app}-mobile`,
    intent: e.capabilityDescription,
    transaction: primary ? `${primary.coreTransactionType}:${primary.journeyName}` : "",
    journeyId: primary?.journeyId || "",
    servicesInvolved: primary ? (primary.backendServices || []).join("; ") : "experience-bff",
    apisInvolved: "/internal/v1/mobile/*",
    registriesInvolved: "",
    trustSecurityChecks: "mobile-trust-headers",
    dataRecords: primary?.dataReadWritten || "",
    eventTopics: primary?.eventsEmitted || "",
    uiSurface: "",
    mobileSurface: e.sourcePath,
    nompiloSupport: "expected",
    relatedFeatures: (primary?.relatedJourneys || []).join("; "),
    nextActions: "",
    completionCriteria: primary?.productOwnerAcceptanceTest || "",
    doctrineCompliance: primary ? "mapped-to-core-transaction" : "unmapped",
    orchestrationStatus: status,
    sourcePath: e.sourcePath,
    apiClientHook: e.apiClientHook,
  });
}

const bffPrefixes = entries.filter((e) => e.componentType === "bff-route-prefix");
for (const e of bffPrefixes) {
  const prefix = e.aliases || e.canonicalName.replace("bff-route:", "");
  const matchedJourneys = journeys.filter((j) => (j.apis || "").includes(prefix));
  const primary = matchedJourneys[0];
  orchestrationEntries.push({
    surfaceType: "bff-route",
    surfaceId: prefix,
    actorType: "experience-bff",
    actorRole: "composition-facade",
    context: "bff-orchestration",
    intent: e.capabilityDescription,
    transaction: primary ? primary.journeyName : "",
    journeyId: primary?.journeyId || "",
    servicesInvolved: primary ? (primary.backendServices || []).join("; ") : "",
    apisInvolved: prefix,
    registriesInvolved: "",
    trustSecurityChecks: "TSHEPO session + ext_authz",
    dataRecords: "",
    eventTopics: "",
    uiSurface: primary?.frontendRoutes?.slice(0, 3).join("; ") || "",
    mobileSurface: "",
    nompiloSupport: "n/a",
    relatedFeatures: "",
    nextActions: "",
    completionCriteria: "",
    doctrineCompliance: primary ? "mapped" : "bff-unmapped",
    orchestrationStatus: primary ? "partial" : "orphan-backend",
    sourcePath: e.sourcePath,
    apiClientHook: "",
  });
}

const serviceJourneyMap = journeysDoc.serviceToJourneys || {};
const backendServices = entries.filter((e) => e.componentType === "backend-service");
for (const e of backendServices) {
  const svc = e.canonicalName;
  const journeyIds = serviceJourneyMap[svc] || [];
  const primary = journeys.find((j) => j.journeyId === journeyIds[0]);
  orchestrationEntries.push({
    surfaceType: "backend-capability",
    surfaceId: svc,
    actorType: "sovereign-service",
    actorRole: e.serviceDomainPlane,
    context: e.serviceDomainPlane,
    intent: e.capabilityDescription,
    transaction: primary ? primary.journeyName : "",
    journeyId: primary?.journeyId || "",
    servicesInvolved: svc,
    apisInvolved: e.backendApiContractStatus,
    registriesInvolved: /vito|varapi|tuso|zibo|product-registry/.test(svc) ? svc : "",
    trustSecurityChecks: e.trustSecurityDependency,
    dataRecords: e.databaseMigrationDependency,
    eventTopics: e.workflowEventDependency,
    uiSurface: e.currentFrontendRouteComponent,
    mobileSurface: e.currentMobileScreenComponent,
    nompiloSupport: "indirect",
    relatedFeatures: "",
    nextActions: "",
    completionCriteria: "",
    doctrineCompliance: journeyIds.length ? "mapped" : "orphan-backend",
    orchestrationStatus: journeyIds.length ? "partial" : "orphan-backend",
    sourcePath: e.sourcePath,
    apiClientHook: e.apiClientHook,
  });
}

const webOrch = orchestrationEntries.filter((o) => o.surfaceType === "frontend-route");
const totalRoutes = webOrch.length;
const mappedRoutes = webOrch.filter((o) => o.journeyId && o.actorType !== "unknown").length;
const orphanRoutes = webOrch.filter((o) => ["orphan-frontend", "isolated-page", "unclear-intent"].includes(o.orchestrationStatus)).length;
const stubRoutes = webOrch.filter((o) => o.orchestrationStatus === "mock-stub").length;
const isolatedPages = webOrch.filter((o) => o.orchestrationStatus === "isolated-page").length;
const backendWithoutJourney = orchestrationEntries.filter((o) => o.surfaceType === "backend-capability" && o.orchestrationStatus === "orphan-backend").length;
const incompleteJourneys = journeys.filter((j) => j.completionClassification !== "transaction-complete").length;
const mobileDisconnected = orchestrationEntries.filter((o) => o.surfaceType === "mobile-screen" && o.orchestrationStatus === "missing-journey").length;

const gaps = [
  ...webOrch.filter((o) => ["mock-stub", "isolated-page", "orphan-frontend", "missing-journey", "unclear-intent"].includes(o.orchestrationStatus))
    .map((o) => ({ type: "route", id: o.surfaceId, status: o.orchestrationStatus, gap: o.apiClientHook || o.intent })),
  ...journeys.filter((j) => j.completionClassification === "frontend-route-exists-but-disconnected")
    .map((j) => ({ type: "journey", id: j.journeyId, status: j.completionClassification, gap: j.missingFrontendMobileWork })),
  ...journeys.filter((j) => j.completionClassification === "mobile-missing")
    .map((j) => ({ type: "journey", id: j.journeyId, status: j.completionClassification, gap: j.missingFrontendMobileWork })),
].slice(0, 20);

const firstOrchBatch = [
  { title: "Context activation chain", detail: "Login → facility → workspace → shift headers before clinical routes" },
  { title: "Core transaction next-actions", detail: "Every doctrine/journey page exposes next action from BFF timeline" },
  { title: "Patient search → encounter entry", detail: "Queue search opens chart with transaction_id correlation" },
  { title: "Nompilo route context", detail: "Pass pathname + transaction context to /ask and handoff endpoints" },
  { title: "Register orphan pages", detail: "27 unregistered pages into routes.ts with guard/sidebar" },
  { title: "Mobile provider encounter shell", detail: "Align provider-app encounter with web encounter journey" },
  { title: "Finance payer-ops wiring", detail: "Replace stub adapters with typed BFF fail-close envelopes" },
  { title: "Wellness routes map", detail: "Complete wellness map surface (currently coming-soon)" },
];

const orchCols = [
  "surfaceType", "surfaceId", "actorType", "actorRole", "context", "intent", "transaction",
  "journeyId", "servicesInvolved", "apisInvolved", "registriesInvolved", "trustSecurityChecks",
  "dataRecords", "eventTopics", "uiSurface", "mobileSurface", "nompiloSupport", "relatedFeatures",
  "nextActions", "completionCriteria", "doctrineCompliance", "orchestrationStatus", "sourcePath", "apiClientHook",
];
const csv = [orchCols.join(",")];
for (const o of orchestrationEntries) csv.push(orchCols.map((c) => csvEscape(o[c])).join(","));

fs.mkdirSync(OUT_DOCS, { recursive: true });
fs.mkdirSync(OUT_REPORTS, { recursive: true });

fs.writeFileSync(path.join(OUT_REPORTS, "experience-orchestration-map.json"), JSON.stringify({
  generatedAt: DATE, entryCount: orchestrationEntries.length, entries: orchestrationEntries,
}, null, 2));
fs.writeFileSync(path.join(OUT_REPORTS, "experience-orchestration-map.csv"), csv.join("\n") + "\n");

const coherenceReport = {
  generatedAt: DATE,
  totalFrontendRoutes: totalRoutes,
  routesMappedToActorContextIntentTransaction: mappedRoutes,
  orphanRoutes,
  backendCapabilitiesWithoutJourneys: backendWithoutJourney,
  mockStubPlaceholderRouteCount: stubRoutes,
  isolatedPages,
  incompleteTransactionJourneys: incompleteJourneys,
  mobileSurfacesDisconnected: mobileDisconnected,
  orchestrationStatusCounts: Object.fromEntries(ORCH_STATUSES.map((s) => [s, orchestrationEntries.filter((o) => o.orchestrationStatus === s).length])),
  top20CoherenceGaps: gaps,
  recommendedFirstOrchestrationBatch: firstOrchBatch,
};
fs.writeFileSync(path.join(OUT_REPORTS, "experience-coherence-report.json"), JSON.stringify(coherenceReport, null, 2));

const ACTORS = [
  { id: "citizen", label: "Client / Patient / Citizen", identity: "Health ID (VITO)", auth: "Keycloak session + TSHEPO", context: "personal / life-hub", permissions: "self-scope; MINIMAL friction", workspace: "home, wellness, wallet", transactions: "onboarding, wellness, marketplace, consent", services: "vito, experience-bff, wellness, mushe-wallet", ui: "/home, /wellness, citizen-app", audit: "access + consent", safety: "no clinical write without consent" },
  { id: "provider", label: "Provider (generic)", identity: "Health ID + Provider ID (VARAPI)", auth: "provider-id login + MFA", context: "facility + workspace + shift", permissions: "RBAC/ABAC via TSHEPO", workspace: "queue, EHR, clinical", transactions: "encounter, orders, referral", services: "pct, varapi, tuso, experience-bff", ui: "/queue, /ehr, provider-app", audit: "clinical audit chain", safety: "licensure + facility context required" },
  { id: "nurse", label: "Nurse", identity: "Provider ID + Staff role", auth: "same as provider", context: "ward/department", permissions: "nursing order set", workspace: "queue, EHR vitals", transactions: "triage, vitals, inpatient", services: "pct, inpatient", ui: "/ehr/*/vitals", audit: "clinical", safety: "scope of practice" },
  { id: "doctor", label: "Doctor", identity: "Provider ID + licensure", auth: "provider session", context: "consultation room", permissions: "prescribing, orders", workspace: "EHR encounter", transactions: "consultation, prescribe, refer", services: "pct, pharmacy, oros", ui: "/ehr/*/encounter", audit: "prescribing audit", safety: "break-glass for emergency" },
  { id: "pharmacist", label: "Pharmacist", identity: "Provider ID", auth: "provider session", context: "pharmacy workspace", permissions: "dispense authority", workspace: "/pharmacy", transactions: "prescription verify, dispense", services: "pharmacy-service", ui: "/pharmacy", audit: "dispense audit", safety: "formulary checks" },
  { id: "laboratory-user", label: "Laboratory user", identity: "Staff ID", auth: "facility session", context: "lab unit", permissions: "lab result entry", workspace: "/lab", transactions: "lab order, result", services: "oros-service", ui: "/lab", audit: "result audit", safety: "result release rules" },
  { id: "radiology-user", label: "Radiology / imaging user", identity: "Staff ID", auth: "facility session", context: "imaging unit", permissions: "PACS access", workspace: "/pacs, /imaging", transactions: "imaging order, report", services: "pacs-adapter", ui: "/ehr/*/imaging", audit: "PACS access audit", safety: "CPID-only SHR" },
  { id: "facility-administrator", label: "Facility administrator", identity: "Staff ID + admin role", auth: "role guard ADMIN", context: "facility ops", permissions: "facility config", workspace: "/operations, /facility", transactions: "staffing, assets, dispatch", services: "tuso, dispatch", ui: "/operations/*", audit: "ops audit", safety: "separation of duties" },
  { id: "programme-manager", label: "Programme manager", identity: "Staff ID", auth: "programme scope", context: "programme ID", permissions: "programme reports", workspace: "/public-health", transactions: "campaigns, outreach", services: "campaigns, surveillance", ui: "/public-health", audit: "programme audit", safety: "aggregate data only where required" },
  { id: "health-information-officer", label: "Health information officer", identity: "Staff ID", auth: "HIO role", context: "district/national", permissions: "surveillance read", workspace: "/public-health, /ndila", transactions: "outbreak, reporting", services: "surveillance, ndila", ui: "/public-health", audit: "PH audit", safety: "de-identification" },
  { id: "finance-billing-user", label: "Finance / billing user", identity: "Staff ID", auth: "finance role", context: "billing workspace", permissions: "financial ops", workspace: "/finance", transactions: "bill, claim, remittance", services: "costing-engine, mushex, coverage", ui: "/finance/*", audit: "financial audit", safety: "segregation of duties" },
  { id: "registry-administrator", label: "Registry administrator", identity: "Admin ID", auth: "registry admin RBAC", context: "registry ops", permissions: "registry write", workspace: "/registry, /operations/vito", transactions: "identity issuance, reconciliation", services: "vito, varapi", ui: "/registry/*", audit: "registry audit mandatory", safety: "PII handling" },
  { id: "system-administrator", label: "System administrator", identity: "Platform admin", auth: "ADMIN role", context: "platform", permissions: "governance", workspace: "/admin", transactions: "trust, users, audit", services: "tshepo-authz, tshepo-audit", ui: "/admin/*", audit: "critical", safety: "break-glass oversight" },
  { id: "implementer", label: "Implementer / support user", identity: "Support account", auth: "developer role", context: "integration", permissions: "integration hub read", workspace: "/developer", transactions: "adapter config, replay", services: "integration-hub", ui: "/developer", audit: "integration audit", safety: "no production patient data" },
  { id: "data-analyst", label: "Data analyst", identity: "Analyst role", auth: "reporting RBAC", context: "analytics workspace", permissions: "report run", workspace: "/reports, /data-intelligence", transactions: "report, dashboard", services: "reporting, data-warehouse", ui: "/reports", audit: "report access audit", safety: "de-identification" },
  { id: "community-health-worker", label: "Community health worker", identity: "Provider/CHW ID", auth: "outreach mode", context: "field outreach", permissions: "outreach write", workspace: "provider-app outreach mode", transactions: "household visit, screening", services: "community-service", ui: "provider-app/outreach", audit: "field visit audit", safety: "offline sync rules" },
  { id: "device", label: "Device", identity: "Device ID", auth: "mTLS / device registration", context: "facility pod", permissions: "device-scoped", workspace: "n/a", transactions: "telemetry, print", services: "iot-ingestion, card-print-agent", ui: "none", audit: "device event audit", safety: "device block list" },
  { id: "mobile-app", label: "Mobile app (as actor)", identity: "App client credentials", auth: "OAuth + trust headers", context: "citizen or provider mode", permissions: "app-scoped", workspace: "mobile tabs", transactions: "all mobile journeys", services: "experience-bff mobile/*", ui: "apps/mobile/*", audit: "mobile session audit", safety: "app attestation" },
  { id: "ai-assistant", label: "AI assistant (Nompilo)", identity: "Service account", auth: "BFF proxy", context: "route + transaction context", permissions: "read-mostly; no sovereign write", workspace: "/ask", transactions: "guidance, handoff", services: "guidance, llm-orchestration", ui: "/ask, Nompilo launcher", audit: "assist audit", safety: "no clinical override" },
  { id: "scheduled-job", label: "Scheduled job / worker", identity: "Job principal", auth: "service-to-service", context: "batch", permissions: "job-scoped", workspace: "n/a", transactions: "batch, outbox publish", services: "jobs-service", ui: "none", audit: "job audit", safety: "idempotent" },
  { id: "facility-pod", label: "Facility pod", identity: "Pod ID", auth: "pod trust", context: "edge deployment", permissions: "pod-scoped", workspace: "offline-edge", transactions: "federated sync", services: "offline-edge-service", ui: "none", audit: "federation audit", safety: "reconciliation" },
  { id: "external-integration", label: "External integration system", identity: "Integration credentials", auth: "FHIR/gateway auth", context: "external", permissions: "adapter-scoped", workspace: "n/a", transactions: "interop, replay", services: "fhir-gateway, integration-hub", ui: "/developer", audit: "integration audit", safety: "rate limits" },
  { id: "logistics-actor", label: "Logistics actor / courier", identity: "Courier ID", auth: "courier mode", context: "delivery run", permissions: "dispatch scope", workspace: "provider-app courier mode", transactions: "delivery, POD", services: "dispatch, nhume", ui: "/operations/dispatch", audit: "delivery audit", safety: "chain of custody" },
  { id: "offline-sync-actor", label: "Offline sync actor", identity: "Edge node ID", auth: "offline trust envelope", context: "offline queue", permissions: "queued writes", workspace: "offline mode", transactions: "offline clinical queue", services: "offline-sync-service", ui: "provider-app/offline", audit: "sync audit", safety: "conflict resolution" },
];

fs.writeFileSync(path.join(OUT_DOCS, "VNEXT_COHERENT_EXPERIENCE_ORCHESTRATION_DOCTRINE.md"), `# vNext Coherent Experience Orchestration Doctrine

> Generated: ${DATE}
> Branch: \`claude/staging-ux-orchestration-remediation-Yypyl\`
> Phase: **3 — Coherent Experience Orchestration**
> Spine: [CORE_TRANSACTION_ORCHESTRATION_DOCTRINE.md](./CORE_TRANSACTION_ORCHESTRATION_DOCTRINE.md)

## vNext as a coherent Health Operating System

Impilo vNext is one governed Health Operating System — not a folder of apps. The experience layer must behave as a **single orchestration nervous system** connecting person, provider, and platform journeys through shared transaction identity.

## Experience Orchestration Layer

| Layer | Role |
|-------|------|
| **Web** (\`ui/one-ui-shell\`) | Canonical actor-facing orchestration shell |
| **Mobile** (\`citizen-app\`, \`provider-app\`) | Mode-aware journey execution |
| **Experience BFF** | Composition façade — never sovereign truth |
| **Sovereign services** | Source of truth per plane |

## Pages vs transaction journeys

A **page** is a rendering surface. A **transaction journey** is: actor → context → intent → transaction → services → state → next action → completion.

## System self-awareness

Every surface must know: who, where, why, what transaction, which services, what next, when complete.

## Awareness dimensions

Actor, context, intent, transaction, service, journey, trust/security, device/system actors — see [VNEXT_ACTOR_MODEL.md](./VNEXT_ACTOR_MODEL.md).

## Nompilo as orchestration companion

Explains sovereign decisions; receives route + transaction context for handoff.

## Surface purpose rule

**Every surface must know its purpose in the whole.**

## Phase 3 outputs

| Artifact | Path |
|----------|------|
| Actor model | [VNEXT_ACTOR_MODEL.md](./VNEXT_ACTOR_MODEL.md) |
| Orchestration map | [VNEXT_EXPERIENCE_ORCHESTRATION_MAP.md](./VNEXT_EXPERIENCE_ORCHESTRATION_MAP.md) |
| Quality criteria | [VNEXT_EXPERIENCE_QUALITY_CRITERIA.md](./VNEXT_EXPERIENCE_QUALITY_CRITERIA.md) |
| Coherence report | [VNEXT_EXPERIENCE_COHERENCE_REPORT.md](./VNEXT_EXPERIENCE_COHERENCE_REPORT.md) |
`);

fs.writeFileSync(path.join(OUT_DOCS, "VNEXT_ACTOR_MODEL.md"), `# vNext Actor Model

> Generated: ${DATE}
> ${ACTORS.length} actor types (human and non-human)

${mdTable(ACTORS.map((a) => ({ actor: a.label, identity: a.identity, auth: a.auth, context: a.context, transactions: a.transactions, ui: a.ui })), ["actor", "identity", "auth", "context", "transactions", "ui"], 30)}

## Actor detail

${ACTORS.map((a) => `### ${a.label} (\`${a.id}\`)

| Field | Value |
|-------|-------|
| Identity model | ${a.identity} |
| Authentication / trust | ${a.auth} |
| Context model | ${a.context} |
| Permissions | ${a.permissions} |
| Workspace | ${a.workspace} |
| Typical transactions | ${a.transactions} |
| Related services | ${a.services} |
| UI / mobile / API surface | ${a.ui} |
| Audit requirements | ${a.audit} |
| Safety constraints | ${a.safety} |
`).join("\n")}
`);

fs.writeFileSync(path.join(OUT_DOCS, "VNEXT_EXPERIENCE_QUALITY_CRITERIA.md"), `# vNext Experience Quality Criteria

> Generated: ${DATE}

Assess **journeys**, not isolated pages.

### Intelligent
Nompilo and search explain transaction state with route context. **Assess:** handoff uses \`transaction_id\`; \`/ask\` receives pathname context.

### Intuitive
Entry matches actor mental model; steps follow lifecycle stages. **Assess:** goal reachable without unexplained detours.

### Coherent
Surface knows actor, context, intent, transaction, services. **Assess:** orchestration status ≠ isolated/orphan.

### Flowing
Next action visible; correlation preserved across routes. **Assess:** \`transaction_id\` threads entry → completion.

### Relevant
Live BFF data; role hides irrelevant capability. **Assess:** hook → \`/internal/v1/*\`; no production fixture fallback.

### Safe
Trust headers, guards, consent, audit on actions. **Assess:** TSHEPO denial + audit event on meaningful mutations.

### Complete
Journey reaches \`completionState\`; mobile parity where expected. **Assess:** PO acceptance test passable end-to-end.
`);

fs.writeFileSync(path.join(OUT_DOCS, "VNEXT_EXPERIENCE_ORCHESTRATION_MAP.md"), `# vNext Experience Orchestration Map

> Generated: ${DATE}
> Entries: **${orchestrationEntries.length}**

JSON: [experience-orchestration-map.json](../../reports/product/experience-orchestration-map.json)

## Status summary

| Status | Count |
|--------|------:|
${Object.entries(coherenceReport.orchestrationStatusCounts).filter(([, n]) => n > 0).map(([k, n]) => `| ${k} | ${n} |`).join("\n")}

${mdTable(webOrch.slice(0, 30).map((o) => ({ route: o.surfaceId, actor: o.actorType, journey: o.journeyId || "—", status: o.orchestrationStatus })), ["route", "actor", "journey", "status"], 30)}
`);

fs.writeFileSync(path.join(OUT_DOCS, "VNEXT_EXPERIENCE_COHERENCE_REPORT.md"), `# vNext Experience Coherence Report

> Generated: ${DATE}

| # | Metric | Value |
|---|--------|------:|
| 1 | Total frontend routes | ${totalRoutes} |
| 2 | Routes mapped to actor/context/intent/transaction | ${mappedRoutes} |
| 3 | Orphan routes | ${orphanRoutes} |
| 4 | Backend capabilities without journeys | ${backendWithoutJourney} |
| 5 | Mock/stub/placeholder routes | ${stubRoutes} |
| 6 | Isolated pages | ${isolatedPages} |
| 7 | Incomplete transaction journeys | ${incompleteJourneys} |
| 8 | Mobile surfaces disconnected | ${mobileDisconnected} |

## Top 20 coherence gaps

${gaps.map((g, i) => `${i + 1}. **${g.type}:${g.id}** — \`${g.status}\`${g.gap ? `: ${g.gap}` : ""}`).join("\n")}

## Recommended first orchestration-completion batch

${firstOrchBatch.map((b, i) => `${i + 1}. **${b.title}** — ${b.detail}`).join("\n")}
`);

console.log("Experience orchestration generated");
console.log(`  Routes: ${totalRoutes}, mapped: ${mappedRoutes}, orphans: ${orphanRoutes}, stubs: ${stubRoutes}`);
console.log(`  Entries: ${orchestrationEntries.length}, backend without journey: ${backendWithoutJourney}`);
