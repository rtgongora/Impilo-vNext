#!/usr/bin/env node
/**
 * Lint + bundle the tshepo trust-decision OpenAPI document using the
 * repository's established OpenAPI tooling (js-yaml strict parse, the same
 * stack as check-openapi-yaml-validity.sh) plus structural OpenAPI rules:
 *   - openapi 3.x declaration, info.title/version present
 *   - version pinned to tshepo.trust.v1
 *   - every local $ref resolves
 *   - every enum is a non-empty unique list
 *   - every object schema declares additionalProperties explicitly (component level)
 * Bundles the resolved document to reports/contracts/tshepo-trust-decision-v1.bundled.json.
 */
import fs from "node:fs";
import path from "node:path";
import yaml from "js-yaml";

const ROOT = path.resolve(import.meta.dirname, "..", "..");
const FILE = path.join(ROOT, "contracts/openapi/tshepo-trust-decision-v1.openapi.yaml");
const OUT_DIR = path.join(ROOT, "reports/contracts");
const OUT = path.join(OUT_DIR, "tshepo-trust-decision-v1.bundled.json");

const errors = [];
const doc = yaml.load(fs.readFileSync(FILE, "utf8"));

if (!/^3\./.test(String(doc.openapi))) errors.push(`openapi version must be 3.x, got ${doc.openapi}`);
if (!doc.info?.title) errors.push("info.title missing");
if (doc.info?.version !== "tshepo.trust.v1") {
  errors.push(`info.version must be tshepo.trust.v1, got ${doc.info?.version}`);
}
if (typeof doc.paths !== "object") errors.push("paths object missing");

const schemas = doc.components?.schemas ?? {};
if (Object.keys(schemas).length === 0) errors.push("components.schemas is empty");

function walk(node, visit, trail = "$") {
  if (Array.isArray(node)) {
    node.forEach((item, i) => walk(item, visit, `${trail}[${i}]`));
  } else if (node && typeof node === "object") {
    visit(node, trail);
    for (const [k, v] of Object.entries(node)) walk(v, visit, `${trail}.${k}`);
  }
}

// $ref resolution
walk(doc, (node, trail) => {
  if (typeof node.$ref === "string") {
    if (!node.$ref.startsWith("#/components/schemas/")) {
      errors.push(`${trail}: non-local $ref ${node.$ref}`);
      return;
    }
    const target = node.$ref.slice("#/components/schemas/".length);
    if (!schemas[target]) errors.push(`${trail}: unresolved $ref ${node.$ref}`);
  }
});

// enum sanity + additionalProperties policy on component objects
for (const [name, schema] of Object.entries(schemas)) {
  if (schema.enum) {
    if (!Array.isArray(schema.enum) || schema.enum.length === 0) {
      errors.push(`${name}: empty enum`);
    } else if (new Set(schema.enum).size !== schema.enum.length) {
      errors.push(`${name}: duplicate enum constants`);
    }
  }
  if (schema.type === "object" && !("additionalProperties" in schema)) {
    errors.push(`${name}: object schema must declare additionalProperties explicitly`);
  }
  if (schema.type === "object" && Array.isArray(schema.required)) {
    for (const req of schema.required) {
      if (!schema.properties?.[req]) {
        errors.push(`${name}: required property '${req}' not declared`);
      }
    }
  }
}

// bundle: inline-resolve all $refs (cycle-safe via ref-name memo depth cap)
function resolveRefs(node, depth = 0) {
  if (depth > 12) return node; // schema graph is shallow; guard against cycles
  if (Array.isArray(node)) return node.map((n) => resolveRefs(n, depth));
  if (node && typeof node === "object") {
    if (typeof node.$ref === "string") {
      const target = node.$ref.slice("#/components/schemas/".length);
      return resolveRefs(schemas[target] ?? {}, depth + 1);
    }
    return Object.fromEntries(Object.entries(node).map(([k, v]) => [k, resolveRefs(v, depth)]));
  }
  return node;
}

if (errors.length > 0) {
  console.error("trust-decision OpenAPI lint FAILED:");
  for (const e of errors) console.error(`  - ${e}`);
  process.exit(1);
}

fs.mkdirSync(OUT_DIR, { recursive: true });
const bundled = {
  openapi: doc.openapi,
  info: doc.info,
  paths: doc.paths,
  components: { schemas: resolveRefs(schemas) },
};
fs.writeFileSync(OUT, JSON.stringify(bundled, null, 2));
console.log(
  `trust-decision OpenAPI lint OK: ${Object.keys(schemas).length} component schemas, 0 errors; bundled -> ${path.relative(ROOT, OUT)}`,
);
