#!/usr/bin/env node
/**
 * Phase A2: seven-dimension completeness report per service in docs/registry/services-registry.yaml.
 * Outputs docs/reports/completeness-report.json and completeness-report.md
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const REGISTRY_PATH = path.join(REPO_ROOT, 'docs/registry/services-registry.yaml');
const OPENAPI_DIR = path.join(REPO_ROOT, 'contracts/openapi');
const BFF_CLIENT_DIR = path.join(
  REPO_ROOT,
  'services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client'
);
const BFF_CONTROLLER_DIR = path.join(
  REPO_ROOT,
  'services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller'
);
const UI_ROOT = path.join(REPO_ROOT, 'ui/experience/src');

/** Explicit OpenAPI contract filename (under contracts/openapi) per maven module. */
const OPENAPI_BY_MODULE = {
  'butano-fhir-service': 'butano.custom.openapi.yaml',
  'butano-fhir-agent': 'butano.custom.openapi.yaml',
  'card-print-agent': 'card-print.openapi.yaml',
  'clinical-knowledge-platform-service': 'clinical-knowledge-platform.openapi.yaml',
  'costa-service': 'costa.openapi.yaml',
  'coverage-service': 'coverage.openapi.yaml',
  'credential-verification-service': 'credential-verification.openapi.yaml',
  'document-service': 'document-store.openapi.yaml',
  'guidance-service': 'guidance.openapi.yaml',
  'indawo-service': 'indawo.openapi.yaml',
  'inventory-service': 'inventory.openapi.yaml',
  'landela-adapter-service': 'landela-adapter.openapi.yaml',
  'msika-flow-service': 'msika-flow.openapi.yaml',
  'msika-service': 'msika-core.openapi.yaml',
  'mushex-service': 'mushex.openapi.yaml',
  'oros-service': 'oros.openapi.yaml',
  'pct-service': 'pct.openapi.yaml',
  'product-registry-service': 'product-registry.openapi.yaml',
  'pharmacy-service': 'pharmacy.openapi.yaml',
  'share-slip-service': 'share-slip.openapi.yaml',
  'vito-service': 'vito.openapi.yaml',
  'varapi-service': 'varapi.openapi.yaml',
  'tuso-service': 'tuso.openapi.yaml',
  'ubomi-service': 'ubomi.openapi.yaml',
  'zibo-service': 'zibo.openapi.yaml',
};

/** Dedicated BFF Feign client simple class name(s) per maven module. */
const BFF_CLIENT_BY_MODULE = {
  'butano-fhir-service': 'ButanoServiceClient',
  'clinical-knowledge-platform-service': 'ClinicalKnowledgePlatformClient',
  'costa-service': 'CostaServiceClient',
  'coverage-service': 'CoverageServiceClient',
  'document-service': 'DocumentServiceClient',
  'extension-service': 'ExtensionServiceClient',
  'fhir-gateway-service': 'FhirGatewayServiceClient',
  'guidance-service': 'GuidanceServiceClient',
  'msika-flow-service': 'MsikaFlowServiceClient',
  'msika-service': 'MsikaServiceClient',
  'mushex-service': 'MushexServiceClient',
  'oros-service': 'OrosServiceClient',
  'pct-service': 'PctServiceClient',
  'pharmacy-service': 'PharmacyServiceClient',
  'search-service': 'SearchServiceClient',
  'tuso-service': 'TusoServiceClient',
  'varapi-service': 'VarapiServiceClient',
  'vito-service': 'VitoServiceClient',
};

/** BFF proxy path (no dedicated Feign client) — dimension partial credit. */
const BFF_PROXY_BY_MODULE = {
  'surveillance-service': 'PublicHealthController',
  'campaigns-service': 'PublicHealthController',
  'indawo-service': 'PublicHealthController',
};

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

function readText(p) {
  try {
    return fs.readFileSync(p, 'utf8');
  } catch {
    return '';
  }
}

function guessOpenApiFile(module) {
  if (OPENAPI_BY_MODULE[module]) return OPENAPI_BY_MODULE[module];
  let base = module.replace(/-service$/, '').replace(/-adapter$/, '').replace(/-agent$/, '');
  const candidates = [`${base}.openapi.yaml`, `${base}.openapi.yml`];
  for (const c of candidates) {
    if (fs.existsSync(path.join(OPENAPI_DIR, c))) return c;
  }
  return null;
}

function countKafkaListeners(javaFiles) {
  let n = 0;
  for (const f of javaFiles) {
    const t = readText(f);
    if (t.includes('@KafkaListener')) n += (t.match(/@KafkaListener/g) || []).length;
  }
  return n;
}

function pomHasSpringdoc(pomPath) {
  const t = readText(pomPath);
  return (
    t.includes('springdoc-openapi') ||
    t.includes('springdoc.openapi') ||
    t.includes('org.springdoc')
  );
}

function findApplicationClass(javaMain) {
  const files = walkFiles(javaMain, (p) => p.endsWith('.java'));
  for (const f of files) {
    const t = readText(f);
    // Note: avoid leading \b before @ — @ is not a word char, so \b@Spring… never matches at line start.
    if (/@SpringBootApplication\b/.test(t) || /@SpringCloudApplication\b/.test(t)) {
      return path.basename(f, '.java');
    }
  }
  return null;
}

function countFlywayMigrations(serviceRoot) {
  const dirs = [
    path.join(serviceRoot, 'src/main/resources/db/migration'),
    path.join(serviceRoot, 'src/main/resources/db/migration/common'),
  ];
  let n = 0;
  for (const d of dirs) {
    if (!fs.existsSync(d)) continue;
    n += fs.readdirSync(d).filter((f) => f.endsWith('.sql')).length;
  }
  return n;
}

function loadBffControllerText() {
  const files = walkFiles(BFF_CONTROLLER_DIR, (p) => p.endsWith('.java'));
  return files.map((f) => readText(f)).join('\n');
}

function loadClientNames() {
  if (!fs.existsSync(BFF_CLIENT_DIR)) return new Set();
  return new Set(
    fs
      .readdirSync(BFF_CLIENT_DIR)
      .filter((f) => f.endsWith('.java'))
      .map((f) => path.basename(f, '.java'))
  );
}

function uiSearchTerms(service) {
  const terms = new Set();
  const mod = service.maven_module || '';
  terms.add(mod.replace(/-service$/, '').replace(/-adapter$/, '').replace(/-agent$/, ''));
  for (const pn of service.product_names || []) {
    terms.add(String(pn).toLowerCase().replace(/\s+/g, ''));
    for (const w of String(pn).toLowerCase().split(/\s+/)) {
      if (w.length > 2) terms.add(w);
    }
  }
  terms.delete('');
  return [...terms];
}

function countUiHits(terms) {
  if (!fs.existsSync(UI_ROOT) || terms.length === 0) return { hooks: 0, pages: 0 };
  const hookDir = path.join(UI_ROOT, 'hooks');
  const appDir = path.join(UI_ROOT, 'app');
  const hookFiles = walkFiles(hookDir, (p) => p.endsWith('.ts') || p.endsWith('.tsx'));
  const pageFiles = walkFiles(appDir, (p) => p.endsWith('.ts') || p.endsWith('.tsx'));
  const lowerTerms = terms.map((t) => t.toLowerCase());

  function hitsInFiles(files) {
    let h = 0;
    for (const f of files) {
      const text = readText(f).toLowerCase();
      for (const term of lowerTerms) {
        if (term.length < 3) continue;
        if (text.includes(term)) {
          h++;
          break;
        }
      }
    }
    return h;
  }
  return { hooks: hitsInFiles(hookFiles), pages: hitsInFiles(pageFiles) };
}

function levelFromBackend({ applicationClass, flyway, javaCount }) {
  if (applicationClass && flyway > 0 && javaCount >= 5) return 'substantial';
  if (applicationClass && javaCount >= 3) return 'partial';
  if (applicationClass) return 'stub';
  return 'none';
}

function levelFromBff({ clientName, clientExists, usedInController, proxy }) {
  if (clientName && clientExists && usedInController) return 'substantial';
  if (clientName && clientExists) return 'partial';
  if (proxy) return 'stub';
  return 'none';
}

function levelFromContract(openapiFile) {
  if (!openapiFile) return 'none';
  return fs.existsSync(path.join(OPENAPI_DIR, openapiFile)) ? 'substantial' : 'none';
}

function levelFromApiDocs(hasSpringdoc, hasOpenapi) {
  if (hasSpringdoc && hasOpenapi) return 'substantial';
  if (hasSpringdoc || hasOpenapi) return 'partial';
  return 'stub';
}

function levelFromKafka(n) {
  if (n >= 2) return 'substantial';
  if (n === 1) return 'partial';
  return 'none';
}

function levelFromUi({ hooks, pages }) {
  if (hooks > 0 && pages > 0) return 'substantial';
  if (hooks > 0 || pages > 0) return 'partial';
  return 'none';
}

function main() {
  const raw = readText(REGISTRY_PATH);
  const doc = yaml.load(raw);
  const services = doc.services || [];
  const clientNames = loadClientNames();
  const controllerBlob = loadBffControllerText();

  const rows = [];
  let sum = { backend: 0, bff: 0, contract: 0, api_docs: 0, kafka: 0, ui_hooks: 0, ui_pages: 0 };

  for (const s of services) {
    const module = s.maven_module;
    const serviceRoot = path.join(REPO_ROOT, 'services', module);
    const javaMain = path.join(serviceRoot, 'src/main/java');
    const pomPath = path.join(serviceRoot, 'pom.xml');
    const javaFiles = walkFiles(javaMain, (p) => p.endsWith('.java'));
    const applicationClass = findApplicationClass(javaMain);
    const flyway = countFlywayMigrations(serviceRoot);
    const kafkaN = countKafkaListeners(javaFiles);
    const openapiFile = guessOpenApiFile(module);
    const hasOpenapiContract = openapiFile && fs.existsSync(path.join(OPENAPI_DIR, openapiFile));
    const hasSpringdoc = fs.existsSync(pomPath) && pomHasSpringdoc(pomPath);

    const isBffShell = module === 'experience-bff';
    const expectedClient = BFF_CLIENT_BY_MODULE[module];
    const clientExists = expectedClient ? clientNames.has(expectedClient) : false;
    const usedInController = expectedClient ? controllerBlob.includes(expectedClient) : false;
    const proxy = BFF_PROXY_BY_MODULE[module] || null;

    const terms = uiSearchTerms(s);
    const ui = countUiHits(terms);

    const backendLevel = levelFromBackend({
      applicationClass,
      flyway,
      javaCount: javaFiles.length,
    });
    const bffLevel = isBffShell
      ? 'substantial'
      : levelFromBff({
          clientName: expectedClient,
          clientExists,
          usedInController,
          proxy,
        });
    const contractLevel = levelFromContract(openapiFile);
    const apiDocsLevel = levelFromApiDocs(hasSpringdoc, hasOpenapiContract);
    const kafkaLevel = levelFromKafka(kafkaN);
    const uiHooksLevel = ui.hooks > 0 ? (ui.hooks >= 2 ? 'substantial' : 'partial') : 'none';
    const uiPagesLevel = ui.pages > 0 ? (ui.pages >= 2 ? 'substantial' : 'partial') : 'none';

    const scoreMap = { none: 0, stub: 1, partial: 2, substantial: 3 };
    const dims = [
      scoreMap[backendLevel],
      scoreMap[bffLevel],
      scoreMap[contractLevel],
      scoreMap[apiDocsLevel],
      scoreMap[kafkaLevel],
      scoreMap[uiHooksLevel],
      scoreMap[uiPagesLevel],
    ];
    const composite = dims.reduce((a, b) => a + b, 0) / (dims.length * 3);

    rows.push({
      maven_module: module,
      product_names: s.product_names || [],
      plane: s.plane,
      sovereign_group: s.sovereign_group,
      dimensions: {
        backend: {
          level: backendLevel,
          application_class: applicationClass,
          flyway_migrations: flyway,
          java_files: javaFiles.length,
        },
        bff: {
          level: bffLevel,
          is_bff_shell: isBffShell,
          expected_client: expectedClient || null,
          client_file_present: clientExists,
          referenced_in_controller: usedInController,
          proxy: proxy,
        },
        contract: {
          level: contractLevel,
          openapi_file: openapiFile,
          present: Boolean(hasOpenapiContract),
        },
        api_docs: {
          level: apiDocsLevel,
          springdoc_in_pom: hasSpringdoc,
        },
        integration_kafka: {
          level: kafkaLevel,
          kafka_listener_count: kafkaN,
        },
        experience_hooks: {
          level: uiHooksLevel,
          files_with_hits: ui.hooks,
          search_terms_sample: terms.slice(0, 5),
        },
        experience_pages: {
          level: uiPagesLevel,
          files_with_hits: ui.pages,
        },
      },
      composite_score: Number(composite.toFixed(3)),
    });

    sum.backend += scoreMap[backendLevel];
    sum.bff += scoreMap[bffLevel];
    sum.contract += scoreMap[contractLevel];
    sum.api_docs += scoreMap[apiDocsLevel];
    sum.kafka += scoreMap[kafkaLevel];
    sum.ui_hooks += scoreMap[uiHooksLevel];
    sum.ui_pages += scoreMap[uiPagesLevel];
  }

  const n = rows.length || 1;
  const report = {
    generated_at: new Date().toISOString(),
    registry: path.relative(REPO_ROOT, REGISTRY_PATH).replace(/\\/g, '/'),
    service_count: rows.length,
    aggregate: {
      backend_avg: Number((sum.backend / n).toFixed(2)),
      bff_avg: Number((sum.bff / n).toFixed(2)),
      contract_avg: Number((sum.contract / n).toFixed(2)),
      api_docs_avg: Number((sum.api_docs / n).toFixed(2)),
      kafka_avg: Number((sum.kafka / n).toFixed(2)),
      experience_hooks_avg: Number((sum.ui_hooks / n).toFixed(2)),
      experience_pages_avg: Number((sum.ui_pages / n).toFixed(2)),
    },
    services: rows.sort((a, b) => a.composite_score - b.composite_score),
  };

  const outDir = path.join(REPO_ROOT, 'docs/reports');
  fs.mkdirSync(outDir, { recursive: true });
  const jsonPath = path.join(outDir, 'completeness-report.json');
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2), 'utf8');

  const mdLines = [
    '# Service completeness report (Phase A2)',
    '',
    `Generated: ${report.generated_at}`,
    '',
    '| Module | Composite | backend | bff | contract | api_docs | kafka | ui_hooks | ui_pages |',
    '|--------|-----------|---------|-----|----------|----------|-------|----------|----------|',
  ];
  for (const r of report.services) {
    const d = r.dimensions;
    mdLines.push(
      `| ${r.maven_module} | ${r.composite_score} | ${d.backend.level} | ${d.bff.level} | ${d.contract.level} | ${d.api_docs.level} | ${d.integration_kafka.level} | ${d.experience_hooks.level} | ${d.experience_pages.level} |`
    );
  }
  mdLines.push('', '## Aggregate (0–3 per dimension)', '');
  mdLines.push(`- backend: ${report.aggregate.backend_avg}`);
  mdLines.push(`- bff: ${report.aggregate.bff_avg}`);
  mdLines.push(`- contract: ${report.aggregate.contract_avg}`);
  mdLines.push(`- api_docs: ${report.aggregate.api_docs_avg}`);
  mdLines.push(`- kafka: ${report.aggregate.kafka_avg}`);
  mdLines.push(`- experience_hooks: ${report.aggregate.experience_hooks_avg}`);
  mdLines.push(`- experience_pages: ${report.aggregate.experience_pages_avg}`);
  mdLines.push('', 'Regenerate: `cd scripts/completeness && npm install && npm run report`');

  fs.writeFileSync(path.join(outDir, 'completeness-report.md'), mdLines.join('\n'), 'utf8');
  console.log('Wrote', path.relative(REPO_ROOT, jsonPath));
}

main();
