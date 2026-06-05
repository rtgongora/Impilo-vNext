#!/usr/bin/env node
/**
 * Generates per-plane capability ledger from registry, contracts, BFF, and UI hooks.
 * Run: node scripts/architecture/generate-plane-capability-ledger.mjs
 */
import { readFileSync, writeFileSync, readdirSync, existsSync } from "node:fs";
import { dirname, join, basename } from "node:path";
import { fileURLToPath } from "node:url";
import { CAPABILITIES } from "../frontend/generate-parity-docs.mjs";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "../..");
const DATE = new Date().toISOString().slice(0, 10);
const OUT = join(ROOT, "docs/architecture/PLANE_CAPABILITY_LEDGER.md");

const PLANES = ["trust", "registry", "clinical", "data", "integration", "experience", "enterprise"];

function parseRegistryServices() {
  const raw = readFileSync(join(ROOT, "docs/registry/services-registry.yaml"), "utf8");
  const blocks = raw.split(/\n  - id: /).slice(1);
  return blocks.map((block) => {
    const id = block.split("\n")[0].trim();
    const pick = (key) => {
      const m = block.match(new RegExp(`^    ${key}: (.+)$`, "m"));
      return m ? m[1].trim().replace(/^['"]|['"]$/g, "") : "";
    };
    return {
      id,
      primary_plane: pick("primary_plane"),
      domain: pick("domain"),
      frontend_wiring_status: pick("frontend_wiring_status") || "unknown-or-partial",
      api_contract_status: pick("api_contract_status") || "partial",
      default_http_port: pick("default_http_port"),
    };
  });
}

function contractForService(serviceId) {
  const stem = serviceId.replace(/-service$/, "");
  const candidates = [
    join(ROOT, `contracts/openapi/${stem}.openapi.yaml`),
    join(ROOT, `contracts/openapi/${stem}-core.openapi.yaml`),
    join(ROOT, `contracts/openapi/${stem}-flow.openapi.yaml`),
    join(ROOT, `contracts/openapi/${stem}.custom.openapi.yaml`),
  ];
  for (const p of candidates) {
    if (existsSync(p)) return p.replace(`${ROOT}/`, "");
  }
  return "";
}

function scanBffClients() {
  const dir = join(ROOT, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client");
  if (!existsSync(dir)) return new Map();
  const map = new Map();
  for (const file of readdirSync(dir)) {
    if (!file.endsWith("ServiceClient.java")) continue;
    const content = readFileSync(join(dir, file), "utf8");
    const name = file.replace("ServiceClient.java", "");
    const urlMatch = content.match(/@Value\("\$\{[^}]+\.([^}]+)\}"/) || content.match(/baseUrl\s*=\s*"([^"]+)"/);
    map.set(name.toLowerCase(), { file: `services/experience-bff/.../client/${file}`, baseHint: urlMatch?.[1] ?? "" });
  }
  return map;
}

function scanUiHooks() {
  const dir = join(ROOT, "ui/one-ui-shell/src/hooks/queries");
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .filter((f) => f.startsWith("use") && f.endsWith(".ts") && !f.includes(".test."))
    .map((f) => `ui/one-ui-shell/src/hooks/queries/${f}`);
}

function serviceToBffHint(serviceId) {
  const stem = serviceId.replace(/-service$/, "").replace(/-/g, "");
  const clients = scanBffClients();
  for (const [key, val] of clients) {
    if (key.includes(stem.replace(/-/g, "")) || stem.replace(/-/g, "").includes(key)) {
      return val;
    }
  }
  const pascal = serviceId
    .replace(/-service$/, "")
    .split("-")
    .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
    .join("");
  const clientFile = `services/experience-bff/.../client/${pascal}ServiceClient.java`;
  return existsSync(
    join(ROOT, `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/${pascal}ServiceClient.java`),
  )
    ? { file: clientFile, baseHint: "" }
    : null;
}

function maturityFromRegistry(status) {
  if (status === "wired") return "Live";
  if (status === "unknown-or-partial" || status === "partial") return "Partial";
  if (status === "not-wired") return "Not Wired";
  return "Partial";
}

function mdTable(rows, columns) {
  const header = `| ${columns.join(" | ")} |`;
  const sep = `| ${columns.map(() => "---").join(" | ")} |`;
  const body = rows
    .map((r) => `| ${columns.map((c) => String(r[c] ?? "").replace(/\|/g, "\\|")).join(" | ")} |`)
    .join("\n");
  return `${header}\n${sep}\n${body}`;
}

function main() {
  const services = parseRegistryServices();
  const hooks = scanUiHooks();
  const capabilityByDomain = new Map(CAPABILITIES.map((c) => [c.domain.toLowerCase(), c]));

  const sections = PLANES.map((plane) => {
    const planeServices = services.filter((s) => s.primary_plane === plane);
    const rows = planeServices.map((s) => {
      const contract = contractForService(s.id);
      const bff = serviceToBffHint(s.id);
      const cap = capabilityByDomain.get(s.domain) ?? capabilityByDomain.get(s.id.replace(/-service$/, ""));
      return {
        serviceId: s.id,
        domain: s.domain,
        contract: contract || "—",
        bffClient: bff?.file ?? "—",
        uiHook: cap?.webClient ?? "—",
        webRoute: cap?.webRoute ?? "—",
        maturity: cap?.maturity ?? maturityFromRegistry(s.frontend_wiring_status),
        apiContract: s.api_contract_status,
        nextAction: cap?.action ?? "Trace BFF + extend UI surfacing",
      };
    });

    const live = rows.filter((r) => r.maturity === "Live").length;
    const partial = rows.filter((r) => r.maturity === "Partial").length;
    const notWired = rows.filter((r) => r.maturity === "Not Wired").length;

    return `## ${plane.charAt(0).toUpperCase() + plane.slice(1)} plane

| Metric | Count |
|--------|-------|
| Services | ${planeServices.length} |
| Live | ${live} |
| Partial | ${partial} |
| Not Wired | ${notWired} |

${mdTable(rows, ["serviceId", "domain", "contract", "bffClient", "uiHook", "webRoute", "maturity", "apiContract", "nextAction"])}
`;
  });

  const content = `# Plane Capability Ledger

> Generated: ${DATE}. Regenerate: \`node scripts/architecture/generate-plane-capability-ledger.mjs\`

Maps each registry service to contract, BFF client, UI hook/route, and surfacing maturity.
Operator and user-facing workflows are equally in scope per plane surfacing doctrine.

## Summary

| Plane | Services |
|-------|----------|
${PLANES.map((p) => {
  const n = services.filter((s) => s.primary_plane === p).length;
  return `| ${p} | ${n} |`;
}).join("\n")}
| **Total** | **${services.length}** |

## Capability registry cross-reference

${mdTable(
  CAPABILITIES.map((c) => ({
    plane: c.plane,
    domain: c.domain,
    capability: c.capability,
    maturity: c.maturity,
    webRoute: c.webRoute,
    action: c.action,
  })),
  ["plane", "domain", "capability", "maturity", "webRoute", "action"],
)}

## UI hook inventory (${hooks.length} domain hooks)

${hooks.slice(0, 40).map((h) => `- \`${h}\``).join("\n")}
${hooks.length > 40 ? `\n… and ${hooks.length - 40} more under \`ui/one-ui-shell/src/hooks/queries/\`.` : ""}

---

${sections.join("\n\n")}

## Golden path (proof per capability)

\`route/screen → hook → BFF → sovereign service → contract\`

Trust headers: \`ui/one-ui-shell/src/lib/api-client.ts\` → TSHEPO ext_authz → service.

## Regenerate related docs

\`\`\`bash
node scripts/architecture/generate-plane-capability-ledger.mjs
node scripts/frontend/generate-parity-docs.mjs
node scripts/architecture/generate-parity-inventories.mjs
\`\`\`
`;

  writeFileSync(OUT, content);
  console.log(`Wrote ${OUT.replace(`${ROOT}/`, "")}`);
}

main();
