#!/usr/bin/env node
/**
 * Complete contracts from existing handlers — never delete handlers.
 * Merges missing Spring controller routes into the module OpenAPI file.
 *
 * Usage: node sync-handler-routes-to-contract.mjs [--module varapi-service] [--dry-run]
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';
import { OPENAPI_BY_MODULE, defaultOpenApiContractFilename } from './openapi-contracts.mjs';
import { extractSpringRoutes } from './spring-route-extractor.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const SERVICES_DIR = path.join(REPO_ROOT, 'services');
const OPENAPI_DIR = path.join(REPO_ROOT, 'contracts/openapi');

const HTTP_METHODS = new Set(['get', 'post', 'put', 'patch', 'delete', 'head', 'options']);

const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run');
const moduleIdx = args.indexOf('--module');
const targetModule = moduleIdx >= 0 ? args[moduleIdx + 1] : null;

const EXTRA_CONTRACTS_BY_MODULE = {
  'integration-hub': ['integration-registry.openapi.yaml'],
};

function readText(p) {
  return fs.readFileSync(p, 'utf8');
}

function isValidHandlerPath(p) {
  return (
    typeof p === 'string'
    && p.startsWith('/')
    && p.length > 1
    && !p.includes('(')
    && !p.includes(')')
    && !/\s/.test(p)
  );
}

function isValidHttpMethod(method) {
  return HTTP_METHODS.has(method);
}

function stubOperation(method, pathStr, module) {
  const opId = `${module.replace(/-service$/, '').replace(/-/g, '')}_${method}_${pathStr.replace(/[^\w]+/g, '_')}`.slice(0, 120);
  return {
    [method.toLowerCase()]: {
      operationId: opId,
      tags: ['Generated-From-Handler'],
      summary: `Documented from ${module} handler (complete-not-delete sync)`,
      responses: {
        '200': { description: 'Success' },
      },
    },
  };
}

/** Replace paths section in OpenAPI file while preserving header + components. */
function writeMergedPaths(contractPath, paths) {
  const original = readText(contractPath);
  const pathsDump = yaml.dump({ paths }, { lineWidth: 120, noRefs: true });
  const pathsBody = pathsDump.replace(/^paths:\n/, '');

  const componentsIdx = original.search(/\ncomponents:/);
  const pathsIdx = original.search(/^paths:\s*$/m);
  if (pathsIdx < 0) throw new Error(`paths: not found in ${path.basename(contractPath)}`);

  const head = original.slice(0, pathsIdx);
  if (componentsIdx >= 0) {
    const tail = original.slice(componentsIdx);
    fs.writeFileSync(contractPath, `${head}paths:\n${pathsBody}${tail.startsWith('\n') ? tail : `\n${tail}`}`);
    return;
  }
  fs.writeFileSync(
    contractPath,
    `${head}paths:\n${pathsBody}\ncomponents:\n  schemas: {}\n`
  );
}

function syncContractFile(module, contractFile, routes) {
  const contractPath = path.join(OPENAPI_DIR, contractFile);
  if (!fs.existsSync(contractPath)) {
    console.warn(`skip ${module}: no contract ${contractFile}`);
    return { added: 0 };
  }

  let doc;
  try {
    doc = yaml.load(readText(contractPath));
  } catch (err) {
    console.warn(`skip ${module}: cannot parse ${contractFile} — ${err.reason ?? err.message}`);
    return { added: 0 };
  }

  const paths = doc.paths ?? (doc.paths = {});
  let added = 0;

  for (const r of routes) {
    if (!isValidHttpMethod(r.method) || !isValidHandlerPath(r.path)) continue;
    if (!paths[r.path]) paths[r.path] = {};
    if (paths[r.path][r.method]) continue;
    Object.assign(paths[r.path], stubOperation(r.method, r.path, module));
    added++;
    console.log(`+ ${r.method.toUpperCase()} ${r.path} -> ${contractFile}`);
  }

  if (added > 0 && !dryRun) {
    writeMergedPaths(contractPath, paths);
  }
  return { added };
}

function syncModule(module) {
  const javaRoot =
    module === 'experience-bff'
      ? path.join(SERVICES_DIR, module, 'src/main/java/zw/gov/mohcc/impilo/experience')
      : path.join(SERVICES_DIR, module, 'src/main/java');
  const routes = extractSpringRoutes(javaRoot);
  const contractFiles = [
    defaultOpenApiContractFilename(module),
    ...(EXTRA_CONTRACTS_BY_MODULE[module] ?? []),
  ];
  let added = 0;
  for (const contractFile of [...new Set(contractFiles)]) {
    added += syncContractFile(module, contractFile, routes).added;
  }
  return { added };
}

function listModules() {
  return fs.readdirSync(SERVICES_DIR).filter((d) => fs.existsSync(path.join(SERVICES_DIR, d, 'pom.xml')));
}

const modules = targetModule
  ? [targetModule]
  : [
      ...listModules().filter((m) => m.endsWith('-service') || m.endsWith('-adapter') || m.endsWith('-agent')),
      ...(listModules().includes('experience-bff') ? ['experience-bff'] : []),
    ];

let total = 0;
for (const mod of modules) {
  total += syncModule(mod).added;
}
console.log(`sync-handler-routes-to-contract: ${dryRun ? 'dry-run ' : ''}added ${total} operation(s)`);
