#!/usr/bin/env node
/**
 * Emit handler-synced OpenAPI 3.0.3 from Spring MVC routes for a service module.
 * Usage: node emit-handler-synced-openapi.mjs <maven-module> [path-prefix-filter]
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractSpringRoutes } from './spring-route-extractor.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const OPENAPI_DIR = path.join(REPO_ROOT, 'contracts/openapi');

const module = process.argv[2];
const prefixFilter = process.argv[3] || '';
if (!module) {
  console.error('Usage: node emit-handler-synced-openapi.mjs <maven-module> [path-prefix-filter]');
  process.exit(1);
}

const stem = module.endsWith('-service') ? module.slice(0, -'-service'.length) : module;
const outFile = `${stem}.openapi.yaml`;
const javaRoot = path.join(REPO_ROOT, 'services', module, 'src/main/java');
const routes = extractSpringRoutes(javaRoot).filter((r) => !prefixFilter || r.path.startsWith(prefixFilter));

const byPath = new Map();
for (const r of routes) {
  const methods = r.method === 'all' ? ['get', 'post', 'put', 'patch', 'delete'] : [r.method];
  for (const method of methods) {
    if (!byPath.has(r.path)) byPath.set(r.path, new Set());
    byPath.get(r.path).add(method);
  }
}

function opId(method, p) {
  const slug = p.replace(/^\//, '').replace(/[{}\/]/g, '_').replace(/_+/g, '_').replace(/_$/, '');
  return `${stem}_${method}_${slug || 'root'}`;
}

const pathLines = [];
for (const p of [...byPath.keys()].sort()) {
  pathLines.push(`  ${p}:`);
  for (const method of [...byPath.get(p)].sort()) {
    pathLines.push(`    ${method}:`);
    pathLines.push(`      operationId: ${opId(method, p)}`);
    pathLines.push(`      tags: [Generated-From-Handler]`);
    pathLines.push(`      summary: Documented from ${module} handler (Wave 2 contract sync)`);
    pathLines.push(`      responses:`);
    pathLines.push(`        '200':`);
    pathLines.push(`          description: Success`);
    pathLines.push(`          content:`);
    pathLines.push(`            application/json:`);
    pathLines.push(`              schema:`);
    pathLines.push(`                type: object`);
    pathLines.push(`                additionalProperties: true`);
  }
}

const yaml = `# =============================================================================
# ${module} — handler-synced OpenAPI (Wave 2)
# =============================================================================
openapi: "3.0.3"

info:
  title: ${JSON.stringify(module.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()))}
  version: "1.0.0"
  description: |
    Sovereign HTTP surface for \`${module}\`, generated from Spring handler paths.
    Extend schemas as DTOs stabilise.

servers:
  - url: /
    description: Service-relative paths (Envoy/BFF may prefix)

security:
  - bearerAuth: []

paths:
${pathLines.join('\n')}

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
  schemas: {}
`;

const dest = path.join(OPENAPI_DIR, outFile);
fs.mkdirSync(OPENAPI_DIR, { recursive: true });
fs.writeFileSync(dest, yaml, 'utf8');
console.log(`Wrote ${path.relative(REPO_ROOT, dest)} (${byPath.size} paths, ${routes.length} handler mappings)`);
