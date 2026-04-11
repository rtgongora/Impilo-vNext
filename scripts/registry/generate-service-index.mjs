/**
 * Regenerate docs/registry/services-index.md from services-registry.yaml
 *
 * Usage (from repo root):
 *   cd scripts/registry && npm install && npm run generate
 */

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import yaml from "js-yaml";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");
const REGISTRY_PATH = path.join(ROOT, "docs/registry/services-registry.yaml");
const OUTPUT_PATH = path.join(ROOT, "docs/registry/services-index.md");

function main() {
  if (!fs.existsSync(REGISTRY_PATH)) {
    console.error("Missing:", REGISTRY_PATH);
    process.exit(1);
  }
  const data = yaml.load(fs.readFileSync(REGISTRY_PATH, "utf8"));
  const version = data.registry_version ?? "?";
  const libs = [...(data.libraries ?? [])].sort((a, b) =>
    (a.maven_module ?? "").localeCompare(b.maven_module ?? "")
  );
  const services = [...(data.services ?? [])].sort((a, b) =>
    (a.maven_module ?? "").localeCompare(b.maven_module ?? "")
  );

  const now = new Date().toISOString().replace(/\.\d{3}Z$/, " UTC");

  const lines = [
    "# Impilo service index (generated)",
    "",
    `**Registry version:** \`${version}\`  `,
    `**Generated:** ${now}  `,
    "**Source:** [`services-registry.yaml`](./services-registry.yaml)  ",
    "",
    "Regenerate:",
    "",
    "```bash",
    "cd scripts/registry && npm install && npm run generate",
    "```",
    "",
    "---",
    "",
    "## Libraries (Maven reactor, not deployable)",
    "",
    "| ID | Maven module | Path |",
    "|----|--------------|------|",
  ];

  for (const e of libs) {
    const mid = e.maven_module ?? "";
    const p =
      e.path ?? (mid.startsWith("libs/") ? mid : `services/${mid}`);
    lines.push(`| \`${e.id ?? ""}\` | \`${mid}\` | \`${p}\` |`);
  }

  lines.push(
    "",
    "---",
    "",
    "## Services & agents",
    "",
    "| Maven module | ID | Plane | Sovereign | Group | Protocol | HTTP port | Product names |",
    "|--------------|-----|-------|-----------|-------|----------|-----------|---------------|"
  );

  for (const e of services) {
    const names = (e.product_names ?? []).length ? e.product_names.join(", ") : "—";
    const sg = e.sovereign_group ?? "—";
    const sov = e.sovereign ? "yes" : "—";
    const port = e.default_http_port != null ? String(e.default_http_port) : "—";
    lines.push(
      `| \`${e.maven_module ?? ""}\` | \`${e.id ?? ""}\` | ${e.plane ?? ""} | ${sov} | ${sg} | ` +
        `${e.primary_protocol ?? ""} | ${port} | ${names} |`
    );
  }

  lines.push(
    "",
    "---",
    "",
    "*Sovereign* marks membership in the nine national dual-mode boundaries (see `TODO.md` / doctrine). " +
      "*Group* is the logical sovereign name when one product spans multiple modules.",
    ""
  );

  fs.mkdirSync(path.dirname(OUTPUT_PATH), { recursive: true });
  fs.writeFileSync(OUTPUT_PATH, lines.join("\n"), "utf8");
  console.log("Wrote", path.relative(ROOT, OUTPUT_PATH));
}

main();
