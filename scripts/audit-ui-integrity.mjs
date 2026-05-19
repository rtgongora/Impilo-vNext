import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(process.cwd());

const TARGETS = ["ui/one-ui-shell/src", "apps/mobile"];
const EXCLUDED = new Set(["node_modules", ".git", ".next", "dist", "build", "target", "coverage"]);

const PATTERNS = [
  { id: "href_hash", regex: /href=["']#["']/g },
  { id: "empty_onclick", regex: /onClick=\{\(\)\s*=>\s*\{\}\}/g },
  { id: "todo_wire", regex: /TODO:\s*Wire|TODO wire|wire to backend|replace with API/gi },
  { id: "fixture_terms", regex: /\bmockData\b|\bfixture\b|\bplaceholder\b|\bdemo\b/gi },
  { id: "not_implemented", regex: /Not implemented|not implemented|coming soon/gi },
];

function walk(dir, files = []) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    if (EXCLUDED.has(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, files);
    } else if (/\.(ts|tsx|js|jsx|md)$/.test(entry.name)) {
      files.push(full);
    }
  }
  return files;
}

function auditFile(filePath) {
  const raw = fs.readFileSync(filePath, "utf8");
  const lines = raw.split(/\r?\n/);
  const findings = [];
  lines.forEach((line, idx) => {
    for (const pattern of PATTERNS) {
      if (pattern.regex.test(line)) {
        findings.push({ pattern: pattern.id, line: idx + 1, content: line.trim() });
      }
      pattern.regex.lastIndex = 0;
    }
  });
  return findings;
}

const report = {};
for (const target of TARGETS) {
  const abs = path.join(ROOT, target);
  if (!fs.existsSync(abs)) continue;
  for (const file of walk(abs)) {
    const hits = auditFile(file);
    if (hits.length > 0) {
      report[path.relative(ROOT, file)] = hits;
    }
  }
}

const outputPath = path.join(ROOT, "docs", "audits", "ui-integrity-scan-report.json");
fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, JSON.stringify(report, null, 2), "utf8");
console.log(`ui-integrity-scan: wrote ${Object.keys(report).length} files with findings to ${outputPath}`);
