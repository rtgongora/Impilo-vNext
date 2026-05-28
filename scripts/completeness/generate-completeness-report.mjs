#!/usr/bin/env node
/**
 * Phase A2: seven-dimension completeness report per service in docs/registry/services-registry.yaml.
 * Outputs docs/reports/completeness-report.json and completeness-report.md
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';
import { OPENAPI_BY_MODULE, stemMavenModule } from './openapi-contracts.mjs';
import {
  BFF_SERVICE_ENDPOINT_ACCESSOR_BY_MODULE,
  BFF_APP_YML_MARKERS_BY_MODULE,
} from './bff-platform-endpoints.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const REGISTRY_PATH = path.join(REPO_ROOT, 'docs/registry/services-registry.yaml');
const OPENAPI_DIR = path.join(REPO_ROOT, 'contracts/openapi');
const BFF_CLIENT_DIR = path.join(
  REPO_ROOT,
  'services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client'
);
/** All Experience BFF Java (controllers + wellness proxy, etc.) for downstream / proxy detection. */
const BFF_EXPERIENCE_JAVA_ROOT = path.join(
  REPO_ROOT,
  'services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience'
);
const UI_ROOT = path.join(REPO_ROOT, 'ui/one-ui-shell/src');
const SERVICE_CLIENT_CONFIG_PATH = path.join(
  REPO_ROOT,
  'services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/ServiceClientConfig.java'
);
const EXPERIENCE_BFF_APP_YML = path.join(
  REPO_ROOT,
  'services/experience-bff/src/main/resources/application.yml'
);

/** Dedicated BFF Feign client simple class name(s) per maven module. */
const BFF_CLIENT_BY_MODULE = {
  'butano-service': 'ButanoServiceClient',
  'clinical-knowledge-platform-service': 'ClinicalKnowledgePlatformClient',
  'costa-service': 'CostaServiceClient',
  'costing-engine-service': 'CostaServiceClient',
  'coverage-service': 'CoverageServiceClient',
  'document-service': 'DocumentServiceClient',
  'forms-service': 'FormsServiceClient',
  'fhir-gateway-service': 'FhirGatewayServiceClient',
  'guidance-service': 'GuidanceServiceClient',
  'msika-flow-service': 'MsikaFlowServiceClient',
  'msika-service': 'MsikaServiceClient',
  'mushex-service': 'MushexServiceClient',
  'oros-service': 'OrosServiceClient',
  'pct-service': 'PctServiceClient',
  'pharmacy-service': 'PharmacyServiceClient',
  'rules-service': 'RulesServiceClient',
  'search-service': 'SearchServiceClient',
  'tuso-service': 'TusoServiceClient',
  'varapi-service': 'VarapiServiceClient',
  'vito-service': 'VitoServiceClient',
  'integration-hub': 'IntegrationHubServiceClient',
  'credential-verification-service': 'CredentialServiceClient',
};

/** BFF proxy path (no dedicated Feign client) â€” dimension partial credit. */
const BFF_PROXY_BY_MODULE = {
  'surveillance-service': 'PublicHealthController',
  'campaigns-service': 'PublicHealthController',
  'indawo-service': 'PublicHealthController',
  'wellness-service': 'WellnessServiceProxyController',
  /** RestTemplate / shared Feign â€” substring must appear in Experience BFF Java sources. */
  'notification-service': 'notificationBaseUrl',
  'data-governance-service': 'dataGovernanceBaseUrl',
  'landela-adapter-service': 'landelaBaseUrl',
  'product-registry-service': 'ProductRegistryController',
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
  const base = stemMavenModule(module);
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

/** Producer-side signals: dedicated outbox publisher classes and direct KafkaTemplate sends. */
function countKafkaPublishSignals(javaFiles) {
  let n = 0;
  for (const f of javaFiles) {
    const t = readText(f);
    if (/public\s+class\s+\w*OutboxPublisher\b/.test(t)) n += 2;
    if (/\bkafkaTemplate\.send\s*\(/i.test(t)) n += 1;
  }
  return n;
}

function resolveBffProxy(module, controllerBlob) {
  const hint = BFF_PROXY_BY_MODULE[module];
  if (!hint) return null;
  return controllerBlob.includes(hint) ? hint : null;
}

function pomHasSpringdoc(pomPath) {
  const t = readText(pomPath);
  return (
    t.includes('springdoc-openapi') ||
    t.includes('springdoc.openapi') ||
    t.includes('org.springdoc')
  );
}

function pomKafkaDependency(pomPath) {
  const t = readText(pomPath);
  return (
    t.includes('spring-kafka') ||
    t.includes('kafka-clients') ||
    t.includes('spring.kafka')
  );
}

function findApplicationClass(javaMain) {
  const files = walkFiles(javaMain, (p) => p.endsWith('.java'));
  for (const f of files) {
    const t = readText(f);
    // Note: avoid leading \b before @ â€” @ is not a word char, so \b@Springâ€¦ never matches at line start.
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
  const files = walkFiles(BFF_EXPERIENCE_JAVA_ROOT, (p) => p.endsWith('.java'));
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
  terms.add(stemMavenModule(mod));
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
  const libDir = path.join(UI_ROOT, 'lib');
  const appDir = path.join(UI_ROOT, 'app');
  const hookFiles = [
    ...walkFiles(hookDir, (p) => p.endsWith('.ts') || p.endsWith('.tsx')),
    ...walkFiles(libDir, (p) => p.endsWith('.ts') || p.endsWith('.tsx')),
  ];
  const pageFiles = [
    ...walkFiles(appDir, (p) => p.endsWith('.ts') || p.endsWith('.tsx')),
    ...walkFiles(libDir, (p) => p.endsWith('.ts') || p.endsWith('.tsx')),
  ];
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

function levelFromBff({ clientName, clientExists, usedInController, proxy, serviceEndpointDeclared }) {
  if (clientName && clientExists && usedInController) return 'substantial';
  if (clientName && clientExists) return 'partial';
  if (proxy || serviceEndpointDeclared) return 'stub';
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

function levelFromKafka(n, pomKafka) {
  if (n >= 2) return 'substantial';
  if (pomKafka) return 'substantial';
  if (n === 1) return 'partial';
  return 'none';
}

function collectImplementationGaps(levels) {
  const keys = ['backend', 'bff', 'contract', 'api_docs', 'integration_kafka', 'experience_hooks', 'experience_pages'];
  const gaps = [];
  for (const k of keys) {
    if (levels[k] !== 'substantial') gaps.push(k);
  }
  return gaps;
}

function main() {
  const raw = readText(REGISTRY_PATH);
  const doc = yaml.load(raw);
  const services = doc.services || [];
  const clientNames = loadClientNames();
  const controllerBlob = loadBffControllerText();
  const serviceClientConfigText = readText(SERVICE_CLIENT_CONFIG_PATH);
  const experienceBffAppYml = readText(EXPERIENCE_BFF_APP_YML);

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
    const kafkaListeners = countKafkaListeners(javaFiles);
    const kafkaPublish = countKafkaPublishSignals(javaFiles);
    const kafkaN = kafkaListeners + kafkaPublish;
    const openapiFile = guessOpenApiFile(module);
    const hasOpenapiContract = openapiFile && fs.existsSync(path.join(OPENAPI_DIR, openapiFile));
    const hasSpringdoc = fs.existsSync(pomPath) && pomHasSpringdoc(pomPath);
    const pomKafka = fs.existsSync(pomPath) && pomKafkaDependency(pomPath);

    const isBffShell = module === 'experience-bff';
    const dedicatedClient = BFF_CLIENT_BY_MODULE[module];
    const expectedClient = isBffShell
      ? null
      : dedicatedClient ||
          (module !== 'experience-bff' ? 'RegistryDownstreamFacadeClient' : null);
    const clientExists = expectedClient ? clientNames.has(expectedClient) : false;
    const usedInController = expectedClient ? controllerBlob.includes(expectedClient) : false;
    const proxy = resolveBffProxy(module, controllerBlob);
    const bffAccessor = BFF_SERVICE_ENDPOINT_ACCESSOR_BY_MODULE[module];
    const ymlMarker = BFF_APP_YML_MARKERS_BY_MODULE[module];
    const serviceEndpointDeclared =
      (Boolean(bffAccessor) && serviceClientConfigText.includes(`String ${bffAccessor}`)) ||
      (Boolean(ymlMarker) && experienceBffAppYml.includes(ymlMarker));

    const terms = uiSearchTerms(s);
    const ui = countUiHits(terms);

    const backendLevel = levelFromBackend({
      applicationClass,
      flyway,
      javaCount: javaFiles.length,
    });
    const contractLevel = levelFromContract(openapiFile);
    const bffLevel = isBffShell
      ? 'substantial'
      : levelFromBff({
          clientName: expectedClient,
          clientExists,
          usedInController,
          proxy,
          serviceEndpointDeclared,
        });
    const apiDocsLevel = levelFromApiDocs(hasSpringdoc, hasOpenapiContract);
    const kafkaLevel = levelFromKafka(kafkaN, pomKafka);
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

    const dimensionLevels = {
      backend: backendLevel,
      bff: bffLevel,
      contract: contractLevel,
      api_docs: apiDocsLevel,
      integration_kafka: kafkaLevel,
      experience_hooks: uiHooksLevel,
      experience_pages: uiPagesLevel,
    };
    const implementationGaps = collectImplementationGaps(dimensionLevels);

    rows.push({
      maven_module: module,
      product_names: s.product_names || [],
      primary_plane: s.primary_plane ?? s.plane,
      sovereign_group: s.sovereign_group,
      implementation_gaps: implementationGaps,
      implementation_gap_count: implementationGaps.length,
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
          service_endpoint_accessor: bffAccessor || null,
          service_endpoint_declared: Boolean(serviceEndpointDeclared),
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
          kafka_listener_count: kafkaListeners,
          kafka_publish_signals: kafkaPublish,
          kafka_combined_score: kafkaN,
          pom_kafka_dependency: pomKafka,
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
    services: [],
    implementation_backlog: [],
  };

  report.services = [...rows].sort((a, b) => a.composite_score - b.composite_score);
  report.implementation_backlog = [...rows]
    .filter((r) => r.implementation_gap_count > 0)
    .sort((a, b) => {
      const gc = b.implementation_gap_count - a.implementation_gap_count;
      if (gc !== 0) return gc;
      return a.composite_score - b.composite_score;
    })
    .slice(0, 40)
    .map((r) => ({
      maven_module: r.maven_module,
      primary_plane: r.primary_plane,
      implementation_gap_count: r.implementation_gap_count,
      implementation_gaps: r.implementation_gaps,
      composite_score: r.composite_score,
    }));

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
  mdLines.push('', '## Aggregate (0â€“3 per dimension)', '');
  mdLines.push(`- backend: ${report.aggregate.backend_avg}`);
  mdLines.push(`- bff: ${report.aggregate.bff_avg}`);
  mdLines.push(`- contract: ${report.aggregate.contract_avg}`);
  mdLines.push(`- api_docs: ${report.aggregate.api_docs_avg}`);
  mdLines.push(`- kafka: ${report.aggregate.kafka_avg}`);
  mdLines.push(`- experience_hooks: ${report.aggregate.experience_hooks_avg}`);
  mdLines.push(`- experience_pages: ${report.aggregate.experience_pages_avg}`);
  mdLines.push(
    '',
    '## Phase F implementation backlog (literal gaps)',
    '',
    '| Module | gap_count | gaps |',
    '|--------|-----------|------|'
  );
  for (const r of report.implementation_backlog.slice(0, 25)) {
    mdLines.push(
      `| ${r.maven_module} | ${r.implementation_gap_count} | ${r.implementation_gaps.join(', ')} |`
    );
  }
  if (report.implementation_backlog.length === 0) {
    mdLines.push('| *(none)* | | |');
  }
  mdLines.push('', 'Regenerate: `cd scripts/completeness && npm install && npm run report`');

  fs.writeFileSync(path.join(outDir, 'completeness-report.md'), mdLines.join('\n'), 'utf8');
  console.log('Wrote', path.relative(REPO_ROOT, jsonPath));
}

main();
