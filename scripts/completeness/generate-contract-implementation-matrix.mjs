#!/usr/bin/env node
/**
 * Contract Implementation Matrix — one row per OpenAPI operation (+ AsyncAPI channel).
 * Detects missing/partial/orphan-handler operations across sovereign services and BFF.
 *
 * Outputs:
 *   reports/product/contract-implementation-matrix.json
 *   docs/product/CONTRACT_IMPLEMENTATION_MATRIX.md
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';
import { OPENAPI_BY_MODULE } from './openapi-contracts.mjs';
import { extractSpringRoutes, normalizePathPattern, STUB_PATTERNS } from './spring-route-extractor.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const OPENAPI_DIR = path.join(REPO_ROOT, 'contracts/openapi');
const ASYNCAPI_DIR = path.join(REPO_ROOT, 'contracts/asyncapi');
const SERVICES_DIR = path.join(REPO_ROOT, 'services');
const BFF_JAVA_ROOT = path.join(
  REPO_ROOT,
  'services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience'
);
const OUT_JSON = path.join(REPO_ROOT, 'reports/product/contract-implementation-matrix.json');
const OUT_MD = path.join(REPO_ROOT, 'docs/product/CONTRACT_IMPLEMENTATION_MATRIX.md');
const CHECK_ONLY = process.argv.includes('--check-only');

const HTTP_METHODS = new Set(['get', 'post', 'put', 'patch', 'delete', 'head', 'options']);

const BFF_ONLY_CONTRACTS = new Set([
  'experience-bff.openapi.yaml',
  'mobile-citizen.openapi.yaml',
  'mobile-provider.openapi.yaml',
  'core-transaction-openapi.yaml',
  'health-os-launcher.openapi.yaml',
  'imaging-viewer-launch.openapi.yaml',
  'monitoring.openapi.yaml',
]);

const CONTRACT_MODULE_OVERRIDES = {
  'butano.custom.openapi.yaml': ['butano-service', 'butano-fhir'],
  'costa.openapi.yaml': ['costa-service', 'costing-engine-service'],
  'costa-billing-extensions.yaml': ['costa-service', 'costing-engine-service'],
  'booking.openapi.yaml': ['booking-service'],
  'madi.openapi.yaml': ['madi-service'],
  'impilo-live.openapi.yaml': ['live-service'],
  'mvumo.openapi.yaml': ['mvumo-service'],
  'social.openapi.yaml': ['community-service'],
  'community.openapi.yaml': ['community-service'],
  'learning.openapi.yaml': ['learning-service'],
  'simba.openapi.yaml': ['simba-service'],
  'rtc-gateway.openapi.yaml': ['rtc-gateway-service'],
  'nhume.openapi.yaml': ['nhume-service'],
  'ndila.openapi.yaml': ['ndila-service'],
  'referral.openapi.yaml': ['referral-service'],
  'integration-registry.openapi.yaml': ['integration-hub'],
  'msika-apps.openapi.yaml': ['msika-apps-service'],
  'scheduling.openapi.yaml': ['scheduling-service', 'booking-service'],
  'inpatient.openapi.yaml': ['inpatient-service'],
  'dispatch.openapi.yaml': ['dispatch-service'],
  'offline-sync.openapi.yaml': ['offline-sync-service'],
  'offline-edge.openapi.yaml': ['offline-edge-service'],
  'pharmacy-elmis.openapi.yaml': ['pharmacy-elmis-adapter'],
  'inventory-elmis.openapi.yaml': ['inventory-elmis-adapter'],
  'connector-fhir.openapi.yaml': ['connector-fhir-adapter'],
  'pacs-adapter.openapi.yaml': ['pacs-adapter-service'],
  'card-print.openapi.yaml': ['card-print-agent'],
};

/** AsyncAPI contract → owning Maven module(s). Clears unowned async channels. */
const ASYNCAPI_MODULE_OVERRIDES = {
  'butano-shr.asyncapi.yaml': ['butano-service', 'butano-fhir'],
  'ndila-events.asyncapi.yaml': ['ndila-service'],
  'pct-clinical-encounter.asyncapi.yaml': ['pct-service'],
  'impilo-live.asyncapi.yaml': ['live-service'],
  'document-store.asyncapi.yaml': ['document-service'],
  'trust-governance.asyncapi.yaml': ['tshepo-service', 'vito-service', 'pct-service'],
  'imaging-pipeline.asyncapi.yaml': ['pacs-adapter-service', 'butano-service'],
  'finance-oros-pharmacy.asyncapi.yaml': [
    'mushex-service',
    'costing-engine-service',
    'oros-service',
    'pharmacy-service',
    'experience-bff',
  ],
  'core-transaction-events.asyncapi.yaml': [
    'pct-service',
    'oros-service',
    'pharmacy-service',
    'mushex-service',
    'msika-flow-service',
    'experience-bff',
  ],
  'data-pipeline-reporting-aggregate.asyncapi.yaml': ['data-pipeline-service', 'reporting-service'],
  'data-ingestion-bronze.asyncapi.yaml': ['data-ingestion-service'],
  'health-os-external-publishable-events.asyncapi.yaml': ['integration-hub', 'notification-service'],
  'campaigns-outbound.asyncapi.yaml': ['campaigns-service'],
};

function readText(p) {
  try {
    return fs.readFileSync(p, 'utf8');
  } catch {
    return '';
  }
}

function walkFiles(dir, filter = () => true, acc = []) {
  if (!fs.existsSync(dir)) return acc;
  for (const name of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, name.name);
    if (name.isDirectory()) {
      if (name.name === 'node_modules' || name.name === 'target' || name.name === '.git') continue;
      walkFiles(p, filter, acc);
    } else if (filter(p)) acc.push(p);
  }
  return acc;
}

function buildModuleByContract() {
  const map = new Map();
  for (const [mod, file] of Object.entries(OPENAPI_BY_MODULE)) {
    if (!map.has(file)) map.set(file, new Set());
    map.get(file).add(mod);
  }
  for (const [file, mods] of Object.entries(CONTRACT_MODULE_OVERRIDES)) {
    if (!map.has(file)) map.set(file, new Set());
    for (const m of mods) map.get(file).add(m);
  }
  return map;
}

const MODULE_BY_CONTRACT = buildModuleByContract();

function resolveModulesForContract(filename) {
  if (MODULE_BY_CONTRACT.has(filename)) {
    return [...MODULE_BY_CONTRACT.get(filename)];
  }
  if (BFF_ONLY_CONTRACTS.has(filename)) return ['experience-bff'];
  const stem = filename.replace(/\.openapi\.(yaml|yml)$/, '').replace(/\.yaml$/, '');
  const candidates = [`${stem}-service`, stem, `${stem}-adapter`, `${stem}-agent`];
  for (const c of candidates) {
    if (fs.existsSync(path.join(SERVICES_DIR, c, 'pom.xml'))) return [c];
  }
  return [];
}

function resolveModulesForAsyncContract(filename) {
  if (ASYNCAPI_MODULE_OVERRIDES[filename]) {
    return ASYNCAPI_MODULE_OVERRIDES[filename];
  }
  const stem = filename.replace(/\.asyncapi\.(yaml|yml)$/, '').replace(/\.yaml$/, '');
  const fromOpenApi = resolveModulesForContract(`${stem}.openapi.yaml`);
  if (fromOpenApi.length > 0) return fromOpenApi;
  return resolveModulesForContract(filename);
}

function serviceJavaRoot(module) {
  if (module === 'experience-bff') return BFF_JAVA_ROOT;
  return path.join(SERVICES_DIR, module, 'src/main/java');
}

function routeMatchesOperation(routes, httpMethod, apiPath) {
  const normOp = normalizePathPattern(apiPath);
  const method = httpMethod.toLowerCase();
  for (const r of routes) {
    if (r.method !== 'all' && r.method !== method) continue;
    if (r.normalized === normOp) return r;
    if (normOp.endsWith(r.normalized) && r.normalized.length > 1) return r;
    if (r.normalized.endsWith(normOp) && normOp.length > 1) return r;
  }
  return null;
}

function parseOpenApiOperations(filename) {
  const full = path.join(OPENAPI_DIR, filename);
  const raw = readText(full);
  if (!raw.trim()) return { ops: [], parseError: 'empty file' };
  try {
    const doc = yaml.load(raw);
    const ops = [];
    const paths = doc?.paths ?? {};
    for (const [apiPath, pathItem] of Object.entries(paths)) {
      if (!pathItem || typeof pathItem !== 'object') continue;
      for (const [method, op] of Object.entries(pathItem)) {
        if (!HTTP_METHODS.has(method)) continue;
        const operationId = op?.operationId ?? `${method.toUpperCase()} ${apiPath}`;
        ops.push({
          contract: filename,
          operationId,
          method: method.toUpperCase(),
          path: apiPath,
          summary: op?.summary ?? '',
        });
      }
    }
    return { ops, parseError: null };
  } catch (err) {
    return { ops: [], parseError: String(err.reason ?? err.message ?? err) };
  }
}

function parseAsyncApiChannels(filename) {
  const full = path.join(ASYNCAPI_DIR, filename);
  const doc = yaml.load(readText(full));
  const channels = [];
  for (const [name, ch] of Object.entries(doc?.channels ?? {})) {
    if (ch?.publish) {
      channels.push({
        contract: filename,
        channel: name,
        direction: 'publish',
        operationId: ch.publish?.operationId ?? `PUBLISH ${name}`,
      });
    }
    if (ch?.subscribe) {
      channels.push({
        contract: filename,
        channel: name,
        direction: 'subscribe',
        operationId: ch.subscribe?.operationId ?? `SUBSCRIBE ${name}`,
      });
    }
  }
  return channels;
}

function classifyOpenApiOp(op, modules) {
  let best = null;
  for (const mod of modules) {
    const routes = extractSpringRoutes(serviceJavaRoot(mod)).map((r) => ({
      ...r,
      file: path.relative(REPO_ROOT, r.file),
    }));
    const match = routeMatchesOperation(routes, op.method, op.path);
    if (match) {
      best = { module: mod, handler: match.file, stubHit: match.stubHit };
      break;
    }
  }
  if (!best) {
    return {
      ...op,
      modules,
      implStatus: modules.length === 0 ? 'unowned-contract' : 'missing',
      serviceHandler: null,
      stubPattern: null,
    };
  }
  if (best.stubHit) {
    return {
      ...op,
      modules,
      implStatus: 'partial',
      serviceHandler: best.handler,
      stubPattern: best.stubHit,
    };
  }
  return {
    ...op,
    modules,
    implStatus: 'implemented',
    serviceHandler: best.handler,
    stubPattern: null,
  };
}

function asyncChannelTopicReferenced(joined, channel, channelKey) {
  return joined.includes(channelKey) || joined.includes(channel);
}

/** Producers (outbox/KafkaTemplate) or consumers (@KafkaListener) — AsyncAPI direction is often observer-centric. */
function asyncChannelImplemented(joined, channel, channelKey) {
  if (!asyncChannelTopicReferenced(joined, channel, channelKey)) {
    return null;
  }
  if (joined.includes('@KafkaListener')) {
    return 'kafka-listener';
  }
  if (
    joined.includes('event_outbox') ||
    joined.includes('Outbox') ||
    joined.includes('KafkaTemplate')
  ) {
    return 'outbox-or-kafka';
  }
  return null;
}

function classifyAsyncChannel(ch, modules) {
  const signals = [];
  for (const mod of modules) {
    const javaRoot = serviceJavaRoot(mod);
    const files = walkFiles(javaRoot, (p) => p.endsWith('.java'));
    const joined = files.map(readText).join('\n');
    const channelKey = ch.channel.replace(/^\//, '').split('/').pop();
    const kind = asyncChannelImplemented(joined, ch.channel, channelKey);
    if (kind) {
      signals.push({ module: mod, kind });
    }
  }
  if (signals.length === 0) {
    return {
      ...ch,
      modules,
      implStatus: modules.length === 0 ? 'unowned-contract' : 'missing',
      serviceHandler: null,
    };
  }
  return {
    ...ch,
    modules,
    implStatus: 'implemented',
    serviceHandler: signals.map((s) => `${s.module}:${s.kind}`).join('; '),
  };
}

const ORPHAN_CHECK_SKIP_MODULES = new Set(['experience-bff']);

function contractPathPrefixes(operations) {
  const prefixes = new Set();
  for (const op of operations) {
    const parts = op.path.split('/').filter(Boolean);
    if (parts.length >= 2) prefixes.add(`/${parts[0]}/${parts[1]}`);
    if (parts.length >= 3) prefixes.add(`/${parts.slice(0, 3).join('/')}`);
  }
  return prefixes;
}

function findOrphanHandlers(modules, operations) {
  const orphans = [];
  const prefixes = contractPathPrefixes(operations);
  for (const mod of modules) {
    if (ORPHAN_CHECK_SKIP_MODULES.has(mod)) continue;
    const modOps = operations.filter((o) => o.modules?.includes(mod));
    const routes = extractSpringRoutes(serviceJavaRoot(mod)).map((r) => ({
      ...r,
      file: path.relative(REPO_ROOT, r.file),
    }));
    for (const r of routes) {
      if (r.method === 'all') continue;
      if (!r.path.startsWith('/internal/') && !r.path.startsWith('/v1/') && !r.path.startsWith('/api/')) {
        continue;
      }
      const prefixOk = [...prefixes].some((p) => r.normalized.startsWith(normalizePathPattern(p)));
      if (!prefixOk) continue;
      const hasOp = modOps.some((o) => routeMatchesOperation([r], o.method, o.path));
      if (!hasOp) {
        orphans.push({
          module: mod,
          method: r.method.toUpperCase(),
          path: r.path,
          handler: r.file,
          implStatus: 'contract-gap',
          remediation: 'add OpenAPI operation — complete contract; do not delete handler',
        });
      }
    }
  }
  return orphans;
}

function main() {
  const openapiFiles = fs
    .readdirSync(OPENAPI_DIR)
    .filter((f) => f.endsWith('.yaml') || f.endsWith('.yml'))
    .sort();

  const asyncFiles = fs.existsSync(ASYNCAPI_DIR)
    ? fs.readdirSync(ASYNCAPI_DIR).filter((f) => f.endsWith('.yaml') || f.endsWith('.yml')).sort()
    : [];

  const openApiRows = [];
  const contractParseErrors = [];
  for (const file of openapiFiles) {
    const { ops, parseError } = parseOpenApiOperations(file);
    if (parseError) {
      contractParseErrors.push({ contract: file, error: parseError, implStatus: 'contract-invalid' });
      continue;
    }
    const modules = resolveModulesForContract(file);
    for (const op of ops) {
      openApiRows.push(classifyOpenApiOp(op, modules));
    }
  }

  const asyncRows = [];
  for (const file of asyncFiles) {
    const modules = resolveModulesForAsyncContract(file);
    for (const ch of parseAsyncApiChannels(file)) {
      asyncRows.push(classifyAsyncChannel(ch, modules));
    }
  }

  const orphanRows = [];
  const seenModules = new Set(openApiRows.flatMap((r) => r.modules ?? []));
  for (const mod of seenModules) {
    const modOps = openApiRows.filter((r) => r.modules?.includes(mod));
    orphanRows.push(...findOrphanHandlers([mod], modOps));
  }

  const allRows = [...openApiRows, ...asyncRows];
  const counts = {
    totalOpenApiOperations: openApiRows.length,
    totalAsyncChannels: asyncRows.length,
    implemented: allRows.filter((r) => r.implStatus === 'implemented').length,
    partial: allRows.filter((r) => r.implStatus === 'partial').length,
    missing: allRows.filter((r) => r.implStatus === 'missing').length,
    unownedContract: allRows.filter((r) => r.implStatus === 'unowned-contract').length,
    orphanHandlers: orphanRows.length,
    violations:
      allRows.filter((r) => r.implStatus === 'partial' || r.implStatus === 'missing').length +
      orphanRows.length +
      contractParseErrors.length,
    contractParseErrors: contractParseErrors.length,
  };

  const payload = {
    generatedAt: new Date().toISOString(),
    counts,
    openApiOperations: openApiRows,
    asyncChannels: asyncRows,
    orphanHandlers: orphanRows,
    contractParseErrors,
  };

  fs.mkdirSync(path.dirname(OUT_JSON), { recursive: true });
  fs.mkdirSync(path.dirname(OUT_MD), { recursive: true });
  fs.writeFileSync(OUT_JSON, JSON.stringify(payload, null, 2));

  const violationSample = [
    ...openApiRows.filter((r) => r.implStatus === 'partial' || r.implStatus === 'missing').slice(0, 30),
    ...orphanRows.slice(0, 10),
  ];

  const md = `# Contract Implementation Matrix

> Generated: ${payload.generatedAt}
> OpenAPI operations: **${counts.totalOpenApiOperations}** | AsyncAPI channels: **${counts.totalAsyncChannels}**

## Summary

| Status | Count |
|--------|------:|
| implemented | ${counts.implemented} |
| partial | ${counts.partial} |
| missing | ${counts.missing} |
| unowned-contract | ${counts.unownedContract} |
| contract-gap (handler exists — extend OpenAPI) | ${counts.orphanHandlers} |
| contract-parse-errors | ${counts.contractParseErrors} |
| **violations (partial + missing + orphan + invalid contract)** | **${counts.violations}** |

## Remediation doctrine: complete — never delete

When the matrix reports a gap:

| Status | Action |
|--------|--------|
| **missing** | Implement the handler, service logic, persistence, and tests — wire BFF + UI |
| **contract-gap** | Add the operation to OpenAPI (sync-handler-routes-to-contract.mjs) — do not remove the controller |
| **partial** | Replace stub/501/TODO with real domain logic |
| **unowned-contract** | Assign contract to owning module in registry + openapi-contracts.mjs |

Forbidden: deleting controllers, removing routes, or trimming contracts to make the gate pass.


## Sample violations (first 40)

${violationSample.length === 0 ? '_No violations detected._' : violationSample.map((r) => `- \`${r.implStatus}\` ${r.operationId ?? `${r.method} ${r.path}`} (${r.contract ?? r.module})`).join('\n')}

_Regenerate: \`npm run contract-matrix --prefix scripts/completeness\`_
`;
  fs.writeFileSync(OUT_MD, md);

  console.log(`Contract matrix: ${OUT_JSON}`);
  console.log(`  OpenAPI ops: ${counts.totalOpenApiOperations}, violations: ${counts.violations}`);

  if (CHECK_ONLY && counts.violations > 0) {
    console.error(`CONTRACT IMPLEMENTATION CHECK FAILED: ${counts.violations} violation(s)`);
    process.exit(1);
  }
}

main();
