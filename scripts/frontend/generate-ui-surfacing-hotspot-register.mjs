#!/usr/bin/env node
/**
 * Generates UI surfacing hotspot register from QueryResultPanel usage and route inventory.
 * Run: node scripts/frontend/generate-ui-surfacing-hotspot-register.mjs
 */
import { readFileSync, writeFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "../..");
const APP = join(ROOT, "ui/one-ui-shell/src/app");
const OUT = join(ROOT, "docs/frontend/UI_SURFACING_HOTSPOT_REGISTER.md");
const DATE = new Date().toISOString().slice(0, 10);

function walkPages(dir, acc = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    const st = statSync(p);
    if (st.isDirectory()) walkPages(p, acc);
    else if (name === "page.tsx") acc.push(p);
  }
  return acc;
}

function countQrp(content) {
  const m = content.match(/QueryResultPanel/g);
  return m ? m.length : 0;
}

function classify(path, qrpCount, content) {
  if (qrpCount >= 5) return "P0-thin-shell";
  if (qrpCount >= 1) return "P1-mixed";
  if (/Mock Data|MOCK_|fixture|coming soon/i.test(content)) return "P1-fixture-risk";
  if (/useQuery|useMutation|apiClient/.test(content) && !qrpCount) return "P2-live-candidate";
  return "P3-review";
}

const pages = walkPages(APP);
const rows = pages
  .map((abs) => {
    const content = readFileSync(abs, "utf8");
    const qrp = countQrp(content);
    const rel = relative(join(ROOT, "ui/one-ui-shell/src/app"), abs).replace(/\/page\.tsx$/, "");
    const route = "/" + rel.replace(/\[([^\]]+)\]/g, ":$1");
    return {
      route: route === "/." ? "/" : route,
      qrp,
      priority: classify(abs, qrp, content),
      file: relative(ROOT, abs),
    };
  })
  .filter((r) => r.priority.startsWith("P0") || r.priority.startsWith("P1") || r.qrp > 0)
  .sort((a, b) => b.qrp - a.qrp || a.route.localeCompare(b.route));

const p0 = rows.filter((r) => r.priority === "P0-thin-shell").length;
const p1 = rows.filter((r) => r.priority.startsWith("P1")).length;

const md = `# UI Surfacing Hotspot Register

> Generated: ${DATE}. Regenerate: \`node scripts/frontend/generate-ui-surfacing-hotspot-register.mjs\`

Surfaces that need meaningful product UI (not route parity alone). Aligned with [GAP_CLOSURE_RULES.md](./GAP_CLOSURE_RULES.md).

## Summary

| Metric | Count |
|--------|-------|
| Pages scanned | ${pages.length} |
| Hotspots (P0/P1 or QRP) | ${rows.length} |
| P0 thin shells (5+ QRP) | ${p0} |
| P1 mixed/fixture | ${p1} |

## Priority legend

| Priority | Meaning |
|----------|---------|
| P0-thin-shell | 5+ QueryResultPanel instances — operator console, not product UI |
| P1-mixed | 1–4 QRP or fixture/mock risk — extend in place |
| P2-live-candidate | Hooks present, no QRP — verify chain + doctrine |
| P3-review | Manual review |

## Hotspot table

| route | qrpCount | priority | file |
|-------|----------|----------|------|
${rows.map((r) => `| ${r.route} | ${r.qrp} | ${r.priority} | \`${r.file}\` |`).join("\n")}

## High-value flows (plan phase 3)

1. Workspace selection — facility context + operations hub
2. Registration — \`/auth/register\` chain
3. Control tower / queue — \`/clinical/control-tower\`, worklist
4. Finance ops — \`/finance/payer-ops\`, \`/finance/workspace\`

## Related

- [PLANE_CAPABILITY_LEDGER.md](../architecture/PLANE_CAPABILITY_LEDGER.md)
- [REMAINING_FRONTEND_GAPS.md](./REMAINING_FRONTEND_GAPS.md)
- [BACKEND_NOT_SURFACED_REGISTER.md](../audits/BACKEND_NOT_SURFACED_REGISTER.md)
`;

writeFileSync(OUT, md);
console.log(`Wrote ${OUT.replace(ROOT + "/", "")} (${rows.length} hotspots)`);
