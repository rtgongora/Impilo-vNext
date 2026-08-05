#!/usr/bin/env node

/**
 * Route-guard export — one source of truth for "does this role open this route?".
 *
 * `AuthGuardProvider` answers a failed `role` guard with `router.replace("/home")`. Any surface
 * that composes a list of destinations must therefore filter it, or it offers doors that do not
 * open. In the web shell `src/lib/auth/route-reachability.ts` does that by reading the requirement
 * off the route registry directly.
 *
 * The mobile provider hubs cannot: they are composed in the experience BFF (Java), which has the
 * caller's roles but no access to `routes.ts`. The obvious fix — hand-copy the role requirements
 * into Java — is exactly the duplication `route-reachability.ts` exists to prevent, and it is how
 * the shell and the BFF drifted apart on /coverage in the first place.
 *
 * So this script exports the registry instead. `routes.ts` stays the only place a guard is
 * declared; the BFF reads a generated projection of it.
 *
 *   generate:  node scripts/generate-route-guard-export.mjs
 *   verify:    node scripts/generate-route-guard-export.mjs --check   (CI; never writes)
 *
 * The check re-runs this same parser and compares. That catches the artefact going stale, but it
 * cannot catch the parser misreading `routes.ts` — the same wrong parse would sit on both sides.
 * INVARIANTS below is the control for that: hand-verified facts about the real registry that a
 * silently-degraded parse (empty array, missed spread, dropped requiredRole) cannot satisfy.
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SHELL = path.resolve(__dirname, "..");
const ROUTES_TS = path.join(SHELL, "src/lib/routes.ts");
const ADMIN_ROUTES_TS = path.join(SHELL, "src/lib/administration-governance/route-registry.ts");
const ROLE_GROUPS_TS = path.join(SHELL, "src/lib/auth/role-groups.ts");
const OUT = path.resolve(
  SHELL,
  "../../services/experience-bff/src/main/resources/route-guards.generated.json",
);

/** The shell handler that serves the provider hub catalogue — the source copy. */
const HUB_ROUTE_TS = path.join(SHELL, "src/app/api/mobile/provider/hubs/[hub]/route.ts");

/**
 * The provider app's offline layout.
 *
 * The app shows a bundled catalogue when it cannot reach the hub API, and that copy used to be
 * hand-written in five screens. It drifted — it was still offering /coverage after the shell copy
 * was corrected, and it never gained ph_field_tasks — and, being offline, nothing filtered it, so
 * it offered every section to every role regardless of what that role can open.
 *
 * Generating it fixes both: the sections come from the one catalogue, and each carries the role
 * requirement its route declares, so the app can withhold what the caller cannot open without
 * having to know anything about the route registry.
 */
const HUB_CATALOGUE_OUT = path.resolve(
  SHELL,
  "../../apps/mobile/provider-app/src/lib/hubCatalogue.generated.ts",
);

/**
 * Hand-verified against the real registry. If the parser degrades, these stop holding.
 * A control that is only ever satisfied by a correct parse — not a restatement of the parse.
 */
const INVARIANTS = [
  ["/admin", "role", "ADMIN"],
  ["/coverage", "role", "ADMIN"],
  ["/ruvimbo/provider", "role", "CLINICAL"],
  ["/ruvimbo/payer", "role", "PAYER_OPS"],
  ["/developer/sandbox", "role", "ADMIN"],
  ["/omnichannel", "role", "ADMIN"],
  ["/public-health/site-registry/[siteId]", "role", "PUBLIC_HEALTH"],
  ["/id-services", "role", "ADMIN_OR_HIE"],
  ["/registry-admin", "role", "REGISTRY_ADMIN"],
  ["/organization-admin", "role", "ORGANIZATION_ADMIN"],
  // Not every hub destination is role-guarded — a parser that invented requirements would fail here.
  ["/registry", "auth", null],
  ["/reports", "auth", null],
  ["/settings/security", "auth", null],
  ["/home/credentials", "auth", null],
  // Only reachable if the `...ADMINISTRATION_GOVERNANCE_ROUTES` spread was inlined in place.
  ["/work/administration-governance", "auth", null],
  // Multi-line entry: caught only by a brace-aware scan, not a line-based one.
  ["/workspace/aggregate", "auth", null],
  ["/intelligence", "auth", null],
];

function fail(message) {
  console.error(`\n[route-guard-export] ${message}\n`);
  process.exit(1);
}

/**
 * Extract the top-level items of an array literal, brace-aware and string-aware.
 *
 * Returns object texts plus any `...SPREAD` identifiers, in source order — order is load-bearing:
 * `matchRouteDefinition` returns the FIRST match, so `/reports/[id]` before `/reports/facility`
 * would swallow it. A parser that sorted or deduped here would silently change resolution.
 */
function arrayItems(source, startIndex) {
  const open = source.indexOf("[", startIndex);
  if (open === -1) fail(`could not find the opening [ of an array at index ${startIndex}`);

  const items = [];
  const spreads = [];
  let depth = 0;
  let objectStart = -1;
  let i = open;

  while (i < source.length) {
    const ch = source[i];
    const next = source[i + 1];

    // Skip strings so a brace or bracket inside a title cannot unbalance the scan.
    if (ch === '"' || ch === "'" || ch === "`") {
      const quote = ch;
      i += 1;
      while (i < source.length && source[i] !== quote) {
        if (source[i] === "\\") i += 1;
        i += 1;
      }
      i += 1;
      continue;
    }
    // Skip comments — the registry is heavily annotated, and a `[` in prose would corrupt depth.
    if (ch === "/" && next === "/") {
      while (i < source.length && source[i] !== "\n") i += 1;
      continue;
    }
    if (ch === "/" && next === "*") {
      i = source.indexOf("*/", i) + 2;
      continue;
    }

    if (ch === "[") {
      depth += 1;
    } else if (ch === "]") {
      depth -= 1;
      if (depth === 0) return { items, spreads, endIndex: i };
    } else if (ch === "{" && depth === 1) {
      if (objectStart === -1) objectStart = i;
      depth += 1;
      // Track nested braces within the object.
      let braces = 1;
      i += 1;
      while (i < source.length && braces > 0) {
        const c = source[i];
        if (c === '"' || c === "'" || c === "`") {
          const q = c;
          i += 1;
          while (i < source.length && source[i] !== q) {
            if (source[i] === "\\") i += 1;
            i += 1;
          }
        } else if (c === "{") braces += 1;
        else if (c === "}") braces -= 1;
        i += 1;
      }
      items.push({ kind: "object", text: source.slice(objectStart, i), index: items.length });
      objectStart = -1;
      depth -= 1;
      continue;
    } else if (ch === "." && source.startsWith("...", i) && depth === 1) {
      const m = /^\.\.\.([A-Za-z0-9_$]+)/.exec(source.slice(i));
      if (m) {
        items.push({ kind: "spread", name: m[1], index: items.length });
        spreads.push(m[1]);
        i += m[0].length;
        continue;
      }
    }
    i += 1;
  }

  fail("array literal never closed — refusing to emit a truncated registry");
  return null;
}

function field(objectText, name) {
  const m = new RegExp(`\\b${name}:\\s*"([^"]*)"`).exec(objectText);
  return m ? m[1] : null;
}

function routeFromObject(objectText, origin) {
  const routePath = field(objectText, "path");
  const guard = field(objectText, "guard");
  if (!routePath) fail(`a route object in ${origin} has no path:\n${objectText.slice(0, 200)}`);
  if (!guard) fail(`route ${routePath} in ${origin} has no guard — refusing to guess`);
  const requiredRole = field(objectText, "requiredRole");
  const route = { path: routePath, guard };
  if (requiredRole) route.requiredRole = requiredRole;
  return route;
}

function parseRouteArray(source, declaration, origin) {
  const at = source.indexOf(declaration);
  if (at === -1) fail(`could not find "${declaration}" in ${origin}`);
  // Start after the `=`, not at the declaration: `ROUTES: RouteDefinition[] = [` puts the
  // type annotation's `[]` before the array literal, and scanning from the first `[` would
  // parse that empty pair and report a registry of zero routes.
  const assign = source.indexOf("=", at);
  if (assign === -1) fail(`"${declaration}" in ${origin} has no assignment`);
  const { items } = arrayItems(source, assign);
  return items;
}

/** Mirrors expandRoleGroup() in src/lib/auth/privileged-roles.ts. */
function expandRoleGroup(roles) {
  const merged = [...roles];
  if (roles.some((r) => r === "SYSTEM_ADMIN" || r === "DEVELOPER")) {
    if (!merged.includes("SUPER_ADMIN")) merged.push("SUPER_ADMIN");
  }
  return merged;
}

function parseRoleGroups() {
  const source = fs.readFileSync(ROLE_GROUPS_TS, "utf8");
  const at = source.indexOf("const RAW_ROLE_GROUPS");
  if (at === -1) fail("could not find RAW_ROLE_GROUPS in role-groups.ts");

  const open = source.indexOf("{", at);
  let depth = 0;
  let i = open;
  let end = -1;
  while (i < source.length) {
    const ch = source[i];
    if (ch === '"' || ch === "'") {
      const q = ch;
      i += 1;
      while (i < source.length && source[i] !== q) i += 1;
    } else if (ch === "/" && source[i + 1] === "/") {
      while (i < source.length && source[i] !== "\n") i += 1;
      continue;
    } else if (ch === "/" && source[i + 1] === "*") {
      i = source.indexOf("*/", i) + 2;
      continue;
    } else if (ch === "{") depth += 1;
    else if (ch === "}") {
      depth -= 1;
      if (depth === 0) {
        end = i;
        break;
      }
    }
    i += 1;
  }
  if (end === -1) fail("RAW_ROLE_GROUPS object never closed");

  const body = source.slice(open + 1, end);
  const groups = {};
  const entry = /([A-Z0-9_]+)\s*:\s*\[([^\]]*)\]/g;
  let m;
  while ((m = entry.exec(body)) !== null) {
    const roles = [...m[2].matchAll(/"([^"]+)"/g)].map((r) => r[1]);
    if (roles.length === 0) fail(`role group ${m[1]} parsed as empty`);
    groups[m[1]] = expandRoleGroup(roles);
  }
  if (Object.keys(groups).length === 0) fail("no role groups parsed");
  return groups;
}

/** Mirrors matchRouteDefinition(): first route whose pattern matches wins. */
function guardFor(routes, webPath) {
  for (const route of routes) {
    const pattern = route.path.replace(/\[(\w+)\]/g, "[^/]+").replace(/\//g, "\\/");
    if (new RegExp(`^${pattern}$`).test(webPath)) return route;
  }
  return null;
}

/** The hub catalogue, read from the shell handler that serves it. */
function parseHubCatalogue(routes) {
  const source = fs.readFileSync(HUB_ROUTE_TS, "utf8");
  const hubs = {};
  const armRe = /case "([a-z-]+)":([\s\S]*?)(?=\n {4}case "|\n {4}default:)/g;
  let arm;
  while ((arm = armRe.exec(source)) !== null) {
    const sectionRe =
      /\{\s*id: "([^"]+)",\s*title: "([^"]+)",\s*web_path: "([^"]+)"(?:,\s*hint: "([^"]*)")?/g;
    const sections = [];
    let s;
    while ((s = sectionRe.exec(arm[2])) !== null) {
      const route = guardFor(routes, s[3]);
      const section = { id: s[1], title: s[2], web_path: s[3] };
      if (s[4] !== undefined) section.hint = s[4];
      // Only the ROLE dimension. auth/facility/none guards do not refuse by role, and an
      // unrecognised path carries no requirement — matching roleGuardAdmits(), which admits
      // both rather than hiding a destination it cannot reason about.
      if (route && route.guard === "role" && route.requiredRole) {
        section.requiredRole = route.requiredRole;
      }
      sections.push(section);
    }
    if (sections.length === 0) fail(`hub "${arm[1]}" parsed as having no sections`);
    hubs[arm[1]] = sections;
  }
  if (Object.keys(hubs).length === 0) fail("no hubs parsed from the shell hub handler");
  return hubs;
}

function serializeHubCatalogue(hubs, roleGroups) {
  const groupsUsed = {};
  for (const sections of Object.values(hubs)) {
    for (const section of sections) {
      if (section.requiredRole && roleGroups[section.requiredRole]) {
        groupsUsed[section.requiredRole] = roleGroups[section.requiredRole];
      }
    }
  }
  return `/**
 * GENERATED FILE — DO NOT EDIT.
 *
 * The provider app's offline hub layout, projected from the one catalogue that serves these hubs
 * (ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts) with each section's role
 * requirement resolved from the route registry (ui/one-ui-shell/src/lib/routes.ts).
 *
 * Online, the experience BFF withholds sections the caller's roles cannot open. Offline there is
 * no BFF to ask, so the app filters this list itself — see src/lib/hubReachability.ts. Without
 * the requiredRole carried here it could not, and the offline layout would go on offering every
 * section to every role, each one a tap that leaves the app and lands on /home.
 *
 * Regenerate with \`npm run generate:route-guards\` in ui/one-ui-shell.
 */

export type GeneratedHubSection = {
  id: string;
  title: string;
  web_path: string;
  hint?: string;
  /** Role group or literal role the route demands; absent when it refuses no role. */
  requiredRole?: string;
};

/** Role group -> the concrete realm roles that satisfy it, already expanded. */
export const HUB_ROLE_GROUPS: Record<string, string[]> = ${JSON.stringify(groupsUsed, null, 2)};

export type HubKey =
${Object.keys(hubs)
  .map((hub) => `  | ${JSON.stringify(hub)}`)
  .join("\n")};

export const HUB_FALLBACK_SECTIONS: Record<HubKey, GeneratedHubSection[]> = ${JSON.stringify(
    hubs,
    null,
    2,
  )};
`;
}

function build() {
  const routesSource = fs.readFileSync(ROUTES_TS, "utf8");
  const adminSource = fs.readFileSync(ADMIN_ROUTES_TS, "utf8");

  const adminItems = parseRouteArray(
    adminSource,
    "export const ADMINISTRATION_GOVERNANCE_ROUTES",
    "administration-governance/route-registry.ts",
  );
  const adminRoutes = adminItems.map((item) => {
    if (item.kind !== "object") fail("unexpected spread inside ADMINISTRATION_GOVERNANCE_ROUTES");
    return routeFromObject(item.text, "administration-governance/route-registry.ts");
  });

  const items = parseRouteArray(routesSource, "export const ROUTES", "routes.ts");
  const routes = [];
  for (const item of items) {
    if (item.kind === "object") {
      routes.push(routeFromObject(item.text, "routes.ts"));
    } else if (item.name === "ADMINISTRATION_GOVERNANCE_ROUTES") {
      // Inlined at its source position so first-match-wins ordering is preserved exactly.
      routes.push(...adminRoutes);
    } else {
      fail(`ROUTES spreads ${item.name}, which this exporter does not know how to resolve`);
    }
  }

  const roleGroups = parseRoleGroups();

  // ── Controls on the parse itself ──────────────────────────────────────────
  if (routes.length < 800) {
    fail(`only ${routes.length} routes parsed; the registry is far larger — the parse degraded`);
  }
  const byPath = new Map(routes.map((r) => [r.path, r]));
  for (const [routePath, guard, requiredRole] of INVARIANTS) {
    const found = byPath.get(routePath);
    if (!found) fail(`invariant route ${routePath} is missing from the parse`);
    if (found.guard !== guard) {
      fail(`invariant ${routePath}: expected guard "${guard}", parsed "${found.guard}"`);
    }
    if ((found.requiredRole ?? null) !== requiredRole) {
      fail(
        `invariant ${routePath}: expected requiredRole ${requiredRole}, ` +
          `parsed ${found.requiredRole ?? null}`,
      );
    }
  }
  if (!roleGroups.ADMIN?.includes("SUPER_ADMIN")) {
    fail("role group ADMIN lacks SUPER_ADMIN — expandRoleGroup() was not applied");
  }
  if (!roleGroups.CLINICAL?.includes("CLINICIAN") || !roleGroups.CLINICAL?.includes("NURSE")) {
    fail("role group CLINICAL lost members — the role-group parse degraded");
  }

  return {
    $comment:
      "GENERATED FILE — DO NOT EDIT. Projection of ui/one-ui-shell/src/lib/routes.ts " +
      "(with administration-governance/route-registry.ts inlined) and src/lib/auth/role-groups.ts. " +
      "Route order is load-bearing: the first matching path wins, as in matchRouteDefinition(). " +
      "Regenerate with `npm run generate:route-guards` in ui/one-ui-shell.",
    routeCount: routes.length,
    roleGroups,
    routes,
  };
}

const built = build();
const serialized = `${JSON.stringify(built, null, 2)}\n`;

const hubs = parseHubCatalogue(built.routes);
const hubSerialized = serializeHubCatalogue(hubs, built.roleGroups);

// Control on the hub projection: the role requirements must have actually been resolved. If
// guardFor() silently stopped matching, every section would come out unguarded and the offline
// filter would admit everything — which is precisely the bug this exists to remove, restored
// invisibly and with a green check on top.
const guarded = Object.values(hubs).flat().filter((s) => s.requiredRole);
if (guarded.length === 0) {
  fail("no hub section resolved a requiredRole; the guard lookup degraded");
}
const channelsCoverage = (hubs["professional-channels"] ?? []).find((s) => s.id === "coverage");
if (channelsCoverage?.requiredRole !== "CLINICAL") {
  fail(
    "professional-channels/coverage should require CLINICAL (it points at /ruvimbo/provider); " +
      `resolved ${channelsCoverage?.requiredRole ?? "nothing"}`,
  );
}

if (process.argv.includes("--check")) {
  if (!fs.existsSync(OUT)) {
    fail(`${path.relative(SHELL, OUT)} does not exist. Run: npm run generate:route-guards`);
  }
  const committed = fs.readFileSync(OUT, "utf8");
  if (committed !== serialized) {
    const a = JSON.parse(committed);
    const b = JSON.parse(serialized);
    console.error("\n[route-guard-export] The generated route-guard export is STALE.\n");
    console.error(
      "  routes.ts (or role-groups.ts) changed without regenerating the projection the\n" +
        "  experience BFF reads. Mobile provider hubs would filter against the OLD guards —\n" +
        "  offering links that bounce, or hiding links that now open.\n",
    );
    const wasByPath = new Map(a.routes.map((r) => [r.path, r]));
    const nowByPath = new Map(b.routes.map((r) => [r.path, r]));
    const describe = (r) => (r.guard === "role" ? `role:${r.requiredRole}` : r.guard);
    for (const [p, now] of nowByPath) {
      const was = wasByPath.get(p);
      if (!was) console.error(`    + ${p} (${describe(now)})`);
      else if (describe(was) !== describe(now)) {
        console.error(`    ~ ${p}: ${describe(was)} -> ${describe(now)}`);
      }
    }
    for (const p of wasByPath.keys()) {
      if (!nowByPath.has(p)) console.error(`    - ${p}`);
    }
    console.error("\n  Fix: npm run generate:route-guards (in ui/one-ui-shell), then commit.\n");
    process.exit(1);
  }

  if (!fs.existsSync(HUB_CATALOGUE_OUT)) {
    fail(`${HUB_CATALOGUE_OUT} does not exist. Run: npm run generate:route-guards`);
  }
  if (fs.readFileSync(HUB_CATALOGUE_OUT, "utf8") !== hubSerialized) {
    console.error("\n[route-guard-export] The generated mobile hub catalogue is STALE.\n");
    console.error(
      "  The hub catalogue or a route guard changed without regenerating the projection the\n" +
        "  provider app bundles. Its OFFLINE layout would show the old sections — and, because\n" +
        "  offline has no BFF to filter, would offer them regardless of what the roles open.\n",
    );
    console.error("  Fix: npm run generate:route-guards (in ui/one-ui-shell), then commit.\n");
    process.exit(1);
  }

  console.log(
    `[route-guard-export] up to date (${built.routeCount} routes, ` +
      `${Object.keys(hubs).length} hubs, ${guarded.length} role-guarded sections)`,
  );
} else {
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, serialized);
  fs.mkdirSync(path.dirname(HUB_CATALOGUE_OUT), { recursive: true });
  fs.writeFileSync(HUB_CATALOGUE_OUT, hubSerialized);
  console.log(
    `[route-guard-export] wrote ${path.relative(process.cwd(), OUT)} — ` +
      `${built.routeCount} routes, ${Object.keys(built.roleGroups).length} role groups`,
  );
  console.log(
    `[route-guard-export] wrote ${path.relative(process.cwd(), HUB_CATALOGUE_OUT)} — ` +
      `${Object.keys(hubs).length} hubs, ${Object.values(hubs).flat().length} sections ` +
      `(${guarded.length} role-guarded)`,
  );
}
