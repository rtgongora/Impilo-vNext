#!/usr/bin/env node

/**
 * The provider hub catalogue exists twice. The two copies must agree.
 *
 * `app/api/mobile/provider/hubs/[hub]/route.ts` serves it, and each
 * `Provider*Controller` in experience-bff carries the same list as the `stub` it falls back to
 * when the shell is unreachable. Whichever one answers, a mobile user sees it — so a difference
 * between them is not a style question, it is two different products depending on whether a
 * downstream call succeeded.
 *
 * This has already cost twice:
 *   - /coverage was corrected in the shell copy and left wrong in the BFF stub, so the fix
 *     reached nobody: the shell handler was shadowed and the stub was what shipped.
 *   - Un-shadowing the handler would have silently DROPPED /tools/ph-field, which only the stub
 *     had — a section disappearing from a hub as a side effect of a routing change.
 *
 * Neither was visible in review. Both are mechanical.
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

const total = Object.keys(HUBS).reduce((n, hub) => n + shellSections(hub).length, 0);
console.log(
  `[hub-catalogue-parity] ok — ${Object.keys(HUBS).length} hubs, ${total} sections agree`,
);
