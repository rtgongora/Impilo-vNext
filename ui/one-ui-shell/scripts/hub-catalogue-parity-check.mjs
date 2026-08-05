#!/usr/bin/env node

/**
 * The provider hub catalogue exists twice. The two copies must agree, and every destination
 * either copy offers must be a route that exists.
 *
 * `app/api/mobile/provider/hubs/[hub]/route.ts` serves it, and each
 * `Provider*Controller` in experience-bff carries the same list as the `stub` it falls back to
 * when the shell is unreachable. Whichever one answers, a mobile user sees it — so a difference
 * between them is not a style question, it is two different products depending on whether a
 * downstream call succeeded.
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

/** hub name in the shell's switch -> the BFF controller that serves it. */
const HUBS = {
  "admin-registry": "ProviderAdminRegistryController.java",
  "ops-reports": "ProviderOpsReportsController.java",
  developer: "ProviderDeveloperHubController.java",
  "professional-settings": "ProviderProfessionalSettingsHubController.java",
  "professional-channels": "ProviderProfessionalChannelsHubController.java",
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

const problems = [];

for (const [hub, controller] of Object.entries(HUBS)) {
  const shell = shellSections(hub);
  const stub = stubSections(controller);

  if (shell.length === 0 || stub.length === 0) {
    fail([
      `parsed ${shell.length} shell and ${stub.length} stub sections for "${hub}".`,
      "An empty side makes every comparison below vacuous — the parse degraded.",
    ]);
  }

  const key = (s) => `${s.id} -> ${s.webPath}`;
  const shellKeys = shell.map(key);
  const stubKeys = stub.map(key);

  for (const k of stubKeys) {
    if (!shellKeys.includes(k)) {
      problems.push(`${hub}: BFF stub has "${k}", the shell handler does not`);
    }
  }
  for (const k of shellKeys) {
    if (!stubKeys.includes(k)) {
      problems.push(`${hub}: shell handler has "${k}", the BFF stub does not`);
    }
  }
  if (problems.length === 0 && shellKeys.join("|") !== stubKeys.join("|")) {
    problems.push(`${hub}: same sections, different ORDER — the two hubs would read differently`);
  }
}

if (problems.length > 0) {
  fail([
    "The two copies of the provider hub catalogue disagree:",
    "",
    ...problems,
    "",
    "Whichever copy answers is what a mobile user sees, so this is a live difference, not",
    "duplication tidiness. Update both:",
    "  ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts",
    "  services/experience-bff/.../controller/mobile/provider/Provider*Controller.java",
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

const unresolved = [];
for (const hub of Object.keys(HUBS)) {
  for (const { id, webPath } of shellSections(hub)) {
    if (!isRegistered(webPath)) {
      unresolved.push(`${hub}: section "${id}" offers ${webPath}, which is in no route registry`);
    }
  }
}

if (unresolved.length > 0) {
  fail([
    "The provider hubs offer destinations that do not exist:",
    "",
    ...unresolved,
    "",
    "ProfessionalHubBody opens web_path in a browser, so each of these takes a provider OUT of",
    "the app and lands them on a URL the shell cannot serve. Either build the route in",
    "src/lib/routes.ts, point the section at a route that exists, or drop the section — but do",
    "not ship it. If the real destination is a native screen, it does not belong in this",
    "catalogue: web_path is the only field the BFF role filter can evaluate.",
  ]);
}

const total = Object.keys(HUBS).reduce((n, hub) => n + shellSections(hub).length, 0);
console.log(
  `[hub-catalogue-parity] ok — ${Object.keys(HUBS).length} hubs, ${total} sections agree`
    + ` and every destination resolves against ${registryRoutes.length} registered routes`,
);
