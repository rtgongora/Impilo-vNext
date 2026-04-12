#!/usr/bin/env node
/**
 * Writes minimal OpenAPI 3.0.3 stubs for registry services that do not yet have
 * contracts/openapi/<stem>.openapi.yaml (same stem rules as generate-completeness-report.mjs).
 * Idempotent: skips existing files.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';
import { defaultOpenApiContractFilename } from './openapi-contracts.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const REGISTRY_PATH = path.join(REPO_ROOT, 'docs/registry/services-registry.yaml');
const OPENAPI_DIR = path.join(REPO_ROOT, 'contracts/openapi');

function minimalSpec({ title, port, module }) {
  const safeTitle = JSON.stringify(title);
  return `openapi: 3.0.3
info:
  title: ${safeTitle}
  description: |
    Phase F baseline contract for \`${module}\`. Extend with real paths and schemas as the HTTP surface is stabilised.
  version: 0.1.0
servers:
  - url: http://localhost:${port}
    description: Local default (\`default_http_port\` in docs/registry/services-registry.yaml)

paths:
  /internal/v1/health:
    get:
      operationId: internalHealth
      summary: Internal health probe
      tags: [Health]
      responses:
        '200':
          description: Service reachable
          content:
            application/json:
              schema:
                type: object
                additionalProperties: true

components:
  schemas: {}
`;
}

function main() {
  const doc = yaml.load(fs.readFileSync(REGISTRY_PATH, 'utf8'));
  const services = doc.services || [];
  fs.mkdirSync(OPENAPI_DIR, { recursive: true });
  let created = 0;
  let skipped = 0;
  for (const s of services) {
    const module = s.maven_module;
    if (!module) continue;
    const name = defaultOpenApiContractFilename(module);
    const dest = path.join(OPENAPI_DIR, name);
    if (fs.existsSync(dest)) {
      skipped++;
      continue;
    }
    const title = (s.product_names && s.product_names[0]) || module;
    const port = s.default_http_port ?? 8080;
    fs.writeFileSync(dest, minimalSpec({ title, port, module }), 'utf8');
    console.log('created', path.relative(REPO_ROOT, dest).replace(/\\/g, '/'));
    created++;
  }
  console.log(`emit-missing-openapi-stubs: created=${created} skipped_existing=${skipped}`);
}

main();
