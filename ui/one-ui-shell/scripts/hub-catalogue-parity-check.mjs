#!/usr/bin/env node

/**
 * The provider hub catalogue exists three times. All three copies must agree, and every
 * destination any of them offers must be a route that exists.
 *
 * Each copy is what a provider sees under different conditions, so a difference between them is
 * not duplication tidiness — it is three different products depending on how far a request got:
 *
 *   1. `app/api/mobile/provider/hubs/[hub]/route.ts` — the shell handler, the live answer.
 *   2. `Provider*Controller` in experience-bff — the `stub` each controller falls back to when
 *      the shell is unreachable. Serves when the request reaches the BFF but no further.
 *   3. `FALLBACK_SECTIONS` in each `*HubScreen.tsx` — the layout bundled into the provider app,
 *      shown when the BFF itself cannot be reached. Serves offline, which is the condition under
 *      which a field worker is least able to work around a wrong link.
 *
 * This has already cost three times:
 *   - /coverage was corrected in the shell copy and left wrong in the BFF stub, so the fix
 *     reached nobody: the shell handler was shadowed and the stub was what shipped.
 *   - Un-shadowing the handler would have silently DROPPED /tools/ph-field, which only the stub
 *     had — a section disappearing from a hub as a side effect of a routing change.
 *   - /tools/ph-field itself was in no route registry for as long as it shipped. Nothing caught
 *     it, and nothing could: RouteGuardRegistry.admits() returns true for a path it does not
 *     recognise, so the BFF role filter passed it through, and agreeing with the other copy was
 *     all this check used to ask. Two copies of a dead link agree perfectly.
 *
 * The bundled app copy was unguarded through all three, and drifts the most quietly of the
 * three: it is the only one that cannot be corrected by a deploy.
 *
 * None was visible in review. All are mechanical.
 *
 *   node scripts/hub-catalogue-parity-check.mjs
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SHELL = path.resolve(__dirname, "..");
const ROUTE_TS = path.join(SHELL, "src/app/api/mobile/provider/hubs/[hub]/route.ts");
const BFF_CONTROLLERS = path.resolve(
  SHELL,
  "../../services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/mobile/provider",
);

const MOBILE_SCREENS = path.resolve(
  SHELL,
  "../../apps/mobile/provider-app/src/screens/provider",
);

/** hub name in the shell's switch -> the BFF controller and the app screen that carry its copy. */
const HUBS = {
  "admin-registry": ["ProviderAdminRegistryController.java", "AdminRegistryHubScreen.tsx"],
  "ops-reports": ["ProviderOpsReportsController.java", "OpsReportsHubScreen.tsx"],
  developer: ["ProviderDeveloperHubController.java", "DeveloperHubScreen.tsx"],
  "professional-settings": [
    "ProviderProfessionalSettingsHubController.java",
    "ProfessionalSettingsHubScreen.tsx",
  ],
  "professional-channels": [
    "ProviderProfessionalChannelsHubController.java",
    "ProfessionalChannelsHubScreen.tsx",
  ],
};

function fail(lines) {
  console.error(`\n[hub-catalogue-parity] ${lines.join("\n  ")}\n`);
  process.exit(1);
}

const routeSource = fs.readFileSync(ROUTE_TS, "utf8");

/** Sections of one `case "hub":` arm, as {id, web_path}. */
function shellSections(hub) {
  const arm = new RegExp(
    `case "${hub}":([\\s\\S]*?)(?=\\n {4}case "|\\n {4}default:)`,
  ).exec(routeSource);
  if (!arm) fail([`the shell handler has no \`case "${hub}"\` arm`]);
  return [...arm[1].matchAll(/\{\s*id: "([^"]+)"[^}]*?web_path: "([^"]+)"/g)].map((m) => ({
    id: m[1],
    webPath: m[2],
  }));
}

/** Sections of one controller's `stub`, as {id, web_path}. */
function stubSections(file) {
  const full = path.join(BFF_CONTROLLERS, file);
  if (!fs.existsSync(full)) fail([`missing BFF controller ${file}`]);
  const source = fs.readFileSync(full, "utf8");
  return [...source.matchAll(/section\("([^"]+)",\s*"[^"]*",\s*"([^"]+)"/g)].map((m) => ({
    id: m[1],
    webPath: m[2],
  }));
}

/** Sections of one hub screen's bundled `FALLBACK_SECTIONS`, as {id, web_path}. */
function bundledSections(file) {
  const full = path.join(MOBILE_SCREENS, file);
  if (!fs.existsSync(full)) fail([`missing provider-app hub screen ${file}`]);
  const source = fs.readFileSync(full, "utf8");
  const block = /const FALLBACK_SECTIONS[^=]*=\s*\[([\s\S]*?)\n\];/.exec(source);
  if (!block) {
    // Not "assume none": a screen whose array this cannot find is one whose copy is unchecked,
    // and reporting zero sections would read as agreement with an empty hub.
    fail([
      `${file} has no parsable \`const FALLBACK_SECTIONS = [ ... ];\`.`,
      "The bundled copy cannot be compared, so this check would silently stop covering it.",
    ]);
  }
  return [...block[1].matchAll(/\{\s*id: "([^"]+)"[^}]*?web_path: "([^"]+)"/g)].map((m) => ({
    id: m[1],
    webPath: m[2],
  }));
}

const key = (s) => `${s.id} -> ${s.webPath}`;
const problems = [];

/** Every copy is compared against the shell handler, which is the one that answers when all is well. */
function compareToShell(hub, shellKeys, otherKeys, label) {
  for (const k of otherKeys) {
    if (!shellKeys.includes(k)) {
      problems.push(`${hub}: ${label} has "${k}", the shell handler does not`);
    }
  }
  for (const k of shellKeys) {
    if (!otherKeys.includes(k)) {
      problems.push(`${hub}: shell handler has "${k}", ${label} does not`);
    }
  }
  if (shellKeys.join("|") !== otherKeys.join("|")
      && shellKeys.slice().sort().join("|") === otherKeys.slice().sort().join("|")) {
    problems.push(`${hub}: shell and ${label} hold the same sections in a different ORDER`);
  }
}

for (const [hub, [controller, screen]] of Object.entries(HUBS)) {
  const shell = shellSections(hub);
  const stub = stubSections(controller);
  const bundled = bundledSections(screen);

  if (shell.length === 0 || stub.length === 0 || bundled.length === 0) {
    fail([
      `parsed ${shell.length} shell, ${stub.length} stub and ${bundled.length} bundled`
        + ` sections for "${hub}".`,
      "An empty side makes every comparison below vacuous — the parse degraded.",
    ]);
  }

  const shellKeys = shell.map(key);
  compareToShell(hub, shellKeys, stub.map(key), "the BFF stub");
  compareToShell(hub, shellKeys, bundled.map(key), "the app's bundled layout");
}

if (problems.length > 0) {
  fail([
    "The copies of the provider hub catalogue disagree:",
    "",
    ...problems,
    "",
    "Which copy answers depends only on how far the request got — live, BFF-stubbed, or",
    "offline — so this is a live difference, not duplication tidiness. Update all three:",
    "  ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts",
    "  services/experience-bff/.../controller/mobile/provider/Provider*Controller.java",
    "  apps/mobile/provider-app/src/screens/provider/*HubScreen.tsx  (FALLBACK_SECTIONS)",
  ]);
}

// ---------------------------------------------------------------------------
// Every destination the hubs ship must be a route that exists.
//
// Agreement alone cannot catch a dead link, because both copies can be equally dead — that is
// exactly what /tools/ph-field was. The BFF role filter cannot catch it either: an unrecognised
// path is *admitted* on purpose, so an unbuilt destination is the one thing that filter waves
// through. Checked here against the same projection of routes.ts that the filter itself loads,
// kept in step with the registry by `npm run test:route-guard-export`.
// ---------------------------------------------------------------------------

const ROUTE_GUARDS_JSON = path.resolve(
  SHELL,
  "../../services/experience-bff/src/main/resources/route-guards.generated.json",
);

/** Mirrors RouteGuardRegistry.compile(): `[param]` is one non-slash segment, the rest literal. */
function compileRoute(routePath) {
  const escaped = routePath.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  // The escape above also escaped the brackets of `[param]`, so match them in escaped form —
  // \w is what the Java DYNAMIC_SEGMENT accepts, so `[...slug]` stays literal in both.
  return new RegExp(`^${escaped.replace(/\\\[(\w+)\\\]/g, "[^/]+")}$`);
}

if (!fs.existsSync(ROUTE_GUARDS_JSON)) {
  fail([
    `missing ${path.relative(SHELL, ROUTE_GUARDS_JSON)}.`,
    "Regenerate it with `npm run generate:route-guards`. Skipping this check instead would",
    "restore the defect it exists to catch, and would look like a pass.",
  ]);
}

const registryRoutes = JSON.parse(fs.readFileSync(ROUTE_GUARDS_JSON, "utf8")).routes ?? [];
if (registryRoutes.length < 100) {
  fail([
    `the route projection parsed to ${registryRoutes.length} routes.`,
    "Too few to be the real registry — a degraded parse would mark every hub path unknown,",
    "or, if it degraded the other way, mark every one fine.",
  ]);
}
const compiled = registryRoutes.map((r) => compileRoute(r.path));
const isRegistered = (p) => compiled.some((re) => re.test(p));

// Control on the instrument: a matcher that says yes to everything would report a clean run
// while catching nothing, and that is indistinguishable from there being nothing to catch.
const CONTROL = "/__no-such-route__/hub-catalogue-parity-control";
if (isRegistered(CONTROL)) {
  fail([
    `the route matcher admits ${CONTROL}, which is in no registry.`,
    "It matches everything, so the check below proves nothing.",
  ]);
}

// Scanned across all three copies, not just the shell's. Parity above already requires them to
// agree, so this is normally the same set — but it must not depend on that, or relaxing parity
// would quietly narrow what gets resolved.
const unresolved = new Map();
for (const [hub, [controller, screen]] of Object.entries(HUBS)) {
  const seen = [
    ["the shell handler", shellSections(hub)],
    ["the BFF stub", stubSections(controller)],
    ["the app's bundled layout", bundledSections(screen)],
  ];
  for (const [label, sections] of seen) {
    for (const { id, webPath } of sections) {
      if (!isRegistered(webPath)) {
        const at = `${hub}: section "${id}" offers ${webPath}, which is in no route registry`;
        unresolved.set(at, [...(unresolved.get(at) ?? []), label]);
      }
    }
  }
}

if (unresolved.size > 0) {
  fail([
    "The provider hubs offer destinations that do not exist:",
    "",
    ...[...unresolved].map(([at, copies]) => `${at}  [in: ${copies.join(", ")}]`),
    "",
    "ProfessionalHubBody opens web_path in a browser, so each of these takes a provider OUT of",
    "the app and lands them on a URL the shell cannot serve. Either build the route in",
    "src/lib/routes.ts, point the section at a route that exists, or drop the section — but do",
    "not ship it. If the real destination is a native screen, it does not belong in this",
    "catalogue: web_path is the only field the BFF role filter can evaluate.",
  ]);
}

const hubCount = Object.keys(HUBS).length;
const total = Object.keys(HUBS).reduce((n, hub) => n + shellSections(hub).length, 0);
console.log(
  `[hub-catalogue-parity] ok — ${hubCount} hubs, ${total} sections agree across 3 copies`
    + ` (shell, BFF stub, bundled app layout)`
    + ` and every destination resolves against ${registryRoutes.length} registered routes`,
);
