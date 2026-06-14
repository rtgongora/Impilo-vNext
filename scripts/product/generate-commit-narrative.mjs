#!/usr/bin/env node
/**
 * Build a plane-themed product narrative from git history + working-tree delta.
 * Output: docs/product/COMMIT_PRODUCT_NARRATIVE.md
 */
import { execSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(import.meta.dirname, "../..");
const OUT = path.join(ROOT, "docs/product/COMMIT_PRODUCT_NARRATIVE.md");

const PLANE_KEYWORDS = {
  trust: ["tshepo", "trust", "auth", "keycloak", "consent", "break-glass", "identity assurance"],
  registry: ["vito", "varapi", "tuso", "registry", "health id", "provider", "facility"],
  clinical: ["butano", "fhir", "ehr", "clinical", "pharmacy", "inpatient", "oros", "pacs", "prescri"],
  data: ["ndr", "warehouse", "pipeline", "surveillance", "analytics", "governance", "ingestion"],
  integration: ["connector", "interop", "fhir gateway", "offline", "sync", "integration-hub"],
  experience: ["bff", "one-ui", "shell", "nompilo", "route", "lovable", "experience", "mobile", "expo"],
  enterprise: ["msika", "costa", "mushex", "finance", "marketplace", "inventory", "dispatch", "workflow"],
};

function sh(cmd) {
  return execSync(cmd, { cwd: ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 }).trim();
}

function classifySubject(subject) {
  const s = subject.toLowerCase();
  for (const [plane, keywords] of Object.entries(PLANE_KEYWORDS)) {
    if (keywords.some((k) => s.includes(k))) return plane;
  }
  return "cross-cutting";
}

const branch = sh("git branch --show-current");
const head = sh("git rev-parse --short HEAD");
const totalCommits = sh(`git rev-list --count ${branch}`);
const logLines = sh(`git log ${branch} --pretty=format:'%h|%s'`).split("\n");

const byPlane = Object.fromEntries(Object.keys(PLANE_KEYWORDS).map((p) => [p, []]));
byPlane["cross-cutting"] = [];

for (const line of logLines) {
  const [, subject] = line.split("|");
  if (!subject) continue;
  const plane = classifySubject(subject);
  byPlane[plane].push(subject);
}

const status = sh("git status --porcelain");
const uncommitted = status ? status.split("\n").filter(Boolean) : [];

const recent = logLines.slice(0, 40).map((l) => {
  const [hash, ...rest] = l.split("|");
  return `- \`${hash}\` ${rest.join("|")}`;
});

const planeSections = Object.entries(byPlane)
  .sort((a, b) => b[1].length - a[1].length)
  .map(([plane, commits]) => {
    const sample = commits.slice(0, 12).map((s) => `- ${s}`).join("\n");
    return `### ${plane} (${commits.length} commits)\n\n${sample}${commits.length > 12 ? `\n\n_…and ${commits.length - 12} more._` : ""}`;
  })
  .join("\n\n");

const uncommittedBlock =
  uncommitted.length === 0
    ? "_Working tree clean._"
    : uncommitted
        .slice(0, 50)
        .map((l) => `- \`${l.slice(3)}\` (${l.slice(0, 2).trim()})`)
        .join("\n") + (uncommitted.length > 50 ? `\n\n_…and ${uncommitted.length - 50} more paths._` : "");

const md = `# Commit Product Narrative

> Generated: ${new Date().toISOString().slice(0, 10)} · Branch \`${branch}\` · HEAD \`${head}\` · **${totalCommits} commits**

This narrative themes the branch history by canonical plane and highlights the current uncommitted delta (visual/chrome refresh + full-stack unblock work).

## Executive thread

Impilo vNext evolved from infrastructure and trust foundations into a **576-route unified experience shell** backed by **90+ microservices**. Recent work concentrates on:

1. **Experience orchestration** — one-ui-shell convergence, shell taskbar, role-aware home, Nompilo, route parity gates.
2. **Domain verticals** — MADI haemovigilance, social timeline, telemedicine, public-health ops, marketplace/finance.
3. **Full-boot preview** — wave-based k3s deployment so BFF downstream URLs resolve instead of 500-ing.
4. **Visual system refresh (uncommitted)** — official palette, African print canvas, compact chrome, citizen wallet widget, error degradation.

## Recent commits (latest 40)

${recent.join("\n")}

## Themed by plane

${planeSections}

## Uncommitted working-tree delta (${uncommitted.length} paths)

${uncommittedBlock}

## How to regenerate

\`\`\`bash
node scripts/product/generate-commit-narrative.mjs
\`\`\`
`;

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, md);
console.log(`Wrote ${OUT} (${totalCommits} commits, ${uncommitted.length} uncommitted paths)`);
