#!/usr/bin/env node
/**
 * Phase C: emit docs/architecture/experience-bff-internal-routes.md from @RequestMapping literals.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const CONTROLLER_ROOT = path.join(
  REPO_ROOT,
  'services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller'
);
const OUT = path.join(REPO_ROOT, 'docs/architecture/experience-bff-internal-routes.md');

function walkJavaFiles(dir, acc = []) {
  if (!fs.existsSync(dir)) return acc;
  for (const name of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, name.name);
    if (name.isDirectory()) walkJavaFiles(p, acc);
    else if (name.name.endsWith('.java')) acc.push(p);
  }
  return acc;
}

function extractClassName(source) {
  const m = source.match(/\bpublic\s+class\s+(\w+)/);
  return m ? m[1] : path.basename(source, '.java');
}

function extractInternalBasePaths(source) {
  const paths = new Set();
  const re = /@RequestMapping\(\s*"(\/internal\/v1[^"]*)"\s*\)/g;
  let m;
  while ((m = re.exec(source)) !== null) {
    paths.add(m[1]);
  }
  return [...paths];
}

function main() {
  const files = walkJavaFiles(CONTROLLER_ROOT);
  const rows = [];
  for (const f of files) {
    const text = fs.readFileSync(f, 'utf8');
    const cls = extractClassName(text);
    const bases = extractInternalBasePaths(text);
    const rel = path.relative(REPO_ROOT, f).replace(/\\/g, '/');
    for (const base of bases) {
      rows.push({ cls, base, rel });
    }
  }
  rows.sort((a, b) => a.base.localeCompare(b.base) || a.cls.localeCompare(b.cls));

  const lines = [
    '# Experience BFF — `/internal/v1` controller index',
    '',
    '**Generated** — do not edit by hand. Regenerate:',
    '',
    '```bash',
    'cd scripts/bff-routes && node list-bff-internal-routes.mjs',
    '```',
    '',
    'Phase **C** artifact: maps each Spring `@RequestMapping` on `/internal/v1…` to its controller. For downstream service base URLs see [`experience-bff-downstream-route-map.md`](./experience-bff-downstream-route-map.md).',
    '',
    '| Base path | Controller | Source |',
    '|-----------|------------|--------|',
  ];
  for (const r of rows) {
    lines.push(`| \`${r.base}\` | ${r.cls} | [\`${r.rel}\`](../../${r.rel}) |`);
  }
  lines.push('');
  lines.push(`*Rows: ${rows.length} (some controllers expose multiple base paths).*`);
  lines.push('');

  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, lines.join('\n'), 'utf8');
  console.log('Wrote', path.relative(REPO_ROOT, OUT));
}

main();
