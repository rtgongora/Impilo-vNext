#!/usr/bin/env node
/**
 * Unified Product Truth scanner — composes registry, contract matrix, spring routes,
 * BFF clients, UI/mobile surfaces, and gap classification into one dataset.
 *
 * Outputs:
 *   reports/product/product-truth.json
 *   docs/audits/product-truth-service-inventory.md
 *   docs/audits/product-truth-backend-ui-traceability.md
 *   docs/audits/product-truth-frontend-backend-traceability.md
 *   docs/audits/product-truth-gap-register.md
 *   docs/product/service-completion-blueprints.md (template + per-service stubs)
 *   docs/audits/full-product-truth-recovery-report.md
 *
 * Run: cd scripts/completeness && npm run product-truth
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import yaml from 'js-yaml';
import { extractSpringRoutes, walkFiles } from './spring-route-extractor.mjs';
import { OPENAPI_BY_MODULE, defaultOpenApiContractFilename } from './openapi-contracts.mjs';
import {
  classifyServiceGaps,
  classifySurfaceGaps,
  sortGapsByPriority,
  aggregateGapCounts,
  isInternalOnly,
  mobileParityRequired,
  triState,
  overallProductStatus,
  classifyMaturity,
  MATURITY,
  GAP_CATEGORIES,
  MOBILE_PARITY_REQUIRED,
  authzDimFromReadiness,
  capabilityKeyFor,
  classifyCapabilityDisposition,
  CAPABILITY_DISPOSITION,
} from './product-truth-gaps.mjs';
import { scanAuthzAuditReadiness } from './authz-audit-readiness.mjs';
import {
  CROSS_SERVICE_JOURNEYS,
  evaluateCrossServiceCohesion,
  summarizeCohesion,
} from './cross-service-journeys.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const REGISTRY_PATH = path.join(REPO_ROOT, 'docs/registry/services-registry.yaml');
const SERVICES_DIR = path.join(REPO_ROOT, 'services');
const BFF_DIR = path.join(REPO_ROOT, 'services/experience-bff');
const UI_SHELL = path.join(REPO_ROOT, 'ui/one-ui-shell');
const MOBILE_ROOT = path.join(REPO_ROOT, 'apps/mobile');
const CONTRACTS_OPENAPI = path.join(REPO_ROOT, 'contracts/openapi');
const OUT_JSON = path.join(REPO_ROOT, 'reports/product/product-truth.json');
const CONTRACT_MATRIX = path.join(REPO_ROOT, 'reports/product/contract-implementation-matrix.json');
const PROBE_EVIDENCE = path.join(REPO_ROOT, 'reports/product/probe-evidence.json');
const CAPABILITY_MATRIX_OUT = path.join(REPO_ROOT, 'reports/product/capability-matrix.json');

const MOCK_STUB_PATTERNS = [
  { re: /mockData|fakeResponse|demoPatient|sampleData|fixtureData|mock[- ]data|mockedData|fakeData|demoData/gi, label: 'mock-data-var' },
  { re: /coming soon|under construction|placeholder page/gi, label: 'placeholder-copy' },
  { re: /<pre[^>]*>\s*\{JSON\.stringify/gi, label: 'json-debug-pre' },
  { re: /\{[^}]*JSON\.stringify\s*\([^)]*\)[^}]*\}/g, label: 'json-stringify-render' },
  { re: /onClick=\{\(\)\s*=>\s*\{\s*\}\}/g, label: 'empty-onClick' },
  { re: /TODO:\s*implement/gi, label: 'todo-implement' },
];

/** Production rail safety tokens — not UX mock/stub indicators. */
const BACKEND_SAFETY_TOKENS = /BLOCKED_NOT_LIVE|NOT_LIVE_CAPABLE|BLOCKED_PRE_LIVE/g;

/**
 * Honest readiness-gated degradation note — the OPPOSITE of a stub. A ternary on a
 * `*Live`/`*Ready`/`*Enabled`/`*Available`/`*Configured` flag whose disabled branch
 * carries a "coming soon"/"not yet available" string (or that literal phrase) is a real
 * capability gate: the citizen only ever sees the note while the rail is genuinely off,
 * and it flips to the live method the moment the adapter reports READY. Backend paths only.
 */
const BACKEND_READINESS_NOTE =
  /\b[A-Za-z]\w*(?:Live|Ready|Enabled|Available|Configured)\b[\s\S]{0,40}\?[\s\S]{0,80}:|not yet available/i;

/** The single source line of `text` containing character offset `index`. */
function lineAt(text, index) {
  const start = text.lastIndexOf('\n', index) + 1;
  let end = text.indexOf('\n', index);
  if (end === -1) end = text.length;
  return text.slice(start, end);
}

/** Services reached via public URL or FHIR layer rather than BFF /internal/v1. */
const DIRECT_PUBLIC_OR_FHIR = new Set([
  'share-slip-service',
  'butano-fhir',
  'fhir-gateway-service',
  'developer-portal-service',
]);

/** UI search terms when module id is not referenced literally in page source. */
const SERVICE_UI_ALIASES = {
  'rtc-gateway-service': ['telemedicine', 'Teleconsult', 'rtc', 'RtcGateway'],
  'credential-verification-service': ['credential-vault', 'CredentialVault', 'credentialVerify'],
  'share-slip-service': ['shareSlip', 'share-slip', 'ShareSlip'],
  'oros-service': ['lab-orders', 'LabOrder', 'oros', '/lab/'],
  'pct-service': ['usePct', 'PatientJourney', 'queue', 'encounter'],
  'simba-service': ['wellness', 'simba', 'useWellness', 'useSimba'],
  'data-access-governance-service': ['dags', 'DataAccessGovernance', 'access-request'],
  'general-ledger-service': ['useGeneralLedger', 'useGlAccounts', '/internal/v1/erp/gl', 'erp/gl'],
  'msika-flow-service': ['useCommerceFlow', '/internal/v1/commerce', 'marketplace/cart', 'marketplace/substitutions'],
  'msika-apps-service': ['useHealthOsLauncher', '/internal/v1/marketplace', 'marketplace/apps'],
  'tshepo-audit-service': ['useAudit', '/internal/v1/admin/audit', 'admin/audit', 'AuditPanel'],
  'tshepo-identity-service': ['useIdentityOperations', 'useIdentity', 'useIdentitySearch', '/internal/v1/identity', 'id-services', 'app/registry'],
  'tshepo-offline-service': ['useOfflineClinicalQueue', '/internal/v1/clinical-tools/offline', 'OfflineClinicalQueue'],
  'butano-fhir': ['useFhirInterop', 'useFhirCapabilityStatement', '/internal/v1/fhir', 'operations/butano'],
  'procurement-service': ['useProcurement', '/internal/v1/erp/procurement'],
  'hr-payroll-service': ['useHrPayroll', '/internal/v1/erp/hr'],
  'product-registry-service': ['useProductRegistry', '/internal/v1/product-registry'],
  'workforce-governance-service': ['workforce-governance', '/internal/v1/workforce-governance'],
  'identity-assurance-service': ['identity-assurance', '/internal/v1/identity-assurance', 'IdentityAssurance'],
  'clinical-knowledge-platform-service': ['clinical-knowledge', '/internal/v1/clinical-knowledge', 'ClinicalKnowledge'],
  'data-governance-service': ['data-governance', '/internal/v1/data-governance'],
  'asset-registry-service': ['asset-registry', '/internal/v1/assets', 'AssetRegistry'],
  'mushex-service': ['useFinanceSettlements', '/internal/v1/wallet', '/internal/v1/finance', 'MusheX'],
  'tshepo-authz-service': ['useTrustAdmin', '/internal/v1/admin/trust', 'PolicyEngine'],
  'tshepo-consent-service': ['useConsent', '/internal/v1/consent', 'ConsentCapture'],
  'tshepo-keys-service': ['admin/keys', '/internal/v1/admin/keys', 'KeyRotation'],
  'butano-service': ['butano-service', 'useShrSummary', '/internal/v1/summary', '/internal/v1/timeline'],
  'document-service': ['useClinicalDocuments', '/internal/v1/clinical-documents', 'ClinicalDocument'],
  'forms-service': ['useExtensions', '/internal/v1/extensions/forms', 'FormDefinition'],
  'scheduling-service': ['useAppointments', '/internal/v1/appointments', 'SchedulingController'],
};

/** Mobile search terms when service id is not referenced literally in app source. */
const MOBILE_UI_ALIASES = {
  'mushex-service': ['mushex', 'MusheX', 'wallet/me', 'financeService'],
  'product-registry-service': ['product-registry', 'registryOperationsService'],
  'butano-service': ['butano-service', 'butano', 'ShrSummary', 'timeline'],
  'simba-service': ['simba', 'wellness', 'WellnessSection'],
  'community-service': ['community', 'social', 'SocialFeed'],
  'learning-service': ['learning', 'fundo', 'FundoCourse'],
  'booking-service': ['booking', 'appointments', 'AppointmentBook'],
};

/** BFF path prefixes that prove mobile wiring for a service. */
const MOBILE_BFF_PATH_PATTERNS = {
  'mushex-service': ['/internal/v1/wallet', '/internal/v1/finance'],
  'product-registry-service': ['/internal/v1/product-registry'],
  'document-service': ['/internal/v1/documents', '/internal/v1/mobile/'],
  'forms-service': ['/internal/v1/forms'],
  'scheduling-service': ['/internal/v1/scheduling', '/internal/v1/mobile/'],
  'share-slip-service': ['/internal/v1/share-slip', '/internal/v1/mobile/'],
};

const MOBILE_SCAN_ROOTS = [
  'citizen-app/src',
  'provider-app/src',
  'packages/mobile-registry/src',
  'packages/mobile-nompilo/src',
  'packages/mobile-api/src',
];

const BFF_CLIENT_MODULE_OVERRIDES = {
  RitoServiceClient: 'rito-quality-safety-service',
  VashandiServiceClient: 'vashandi-workforce-service',
  CostaServiceClient: 'costing-engine-service',
  PctServiceClient: 'pct-service',
  SimbaServiceClient: 'simba-service',
  MsikaServiceClient: 'msika-service',
  ButanoServiceClient: 'butano-service',
  TusoServiceClient: 'tuso-service',
  VitoServiceClient: 'vito-service',
  VarapiServiceClient: 'varapi-service',
  ZiboServiceClient: 'zibo-service',
  OrosServiceClient: 'oros-service',
  ReferralServiceClient: 'referral-service',
  ProductRegistryServiceClient: 'product-registry-service',
  ShareSlipServiceClient: 'share-slip-service',
  CredentialServiceClient: 'credential-verification-service',
  DagsServiceClient: 'data-access-governance-service',
  IdentityAssuranceServiceClient: 'identity-assurance-service',
  DeveloperPortalServiceClient: 'developer-portal-service',
  TshepoServiceClient: 'tshepo-service',
  ButanoFhirServiceClient: 'butano-fhir',
  RtcGatewayServiceClient: 'rtc-gateway-service',
};

function readText(p) {
  try {
    return fs.readFileSync(p, 'utf8');
  } catch {
    return '';
  }
}

function rel(p) {
  return path.relative(REPO_ROOT, p).replace(/\\/g, '/');
}

function ensurePrerequisites() {
  if (!fs.existsSync(CONTRACT_MATRIX)) {
    console.log('Generating contract implementation matrix…');
    spawnSync('npm', ['run', 'contract-matrix', '--silent'], {
      cwd: path.join(REPO_ROOT, 'scripts/completeness'),
      stdio: 'inherit',
    });
  }
  const recoveryMap = path.join(REPO_ROOT, 'reports/product/product-truth-recovery-map.json');
  if (!fs.existsSync(recoveryMap)) {
    console.log('Generating product-truth-recovery-map…');
    spawnSync('node', ['scripts/product/generate-product-truth-recovery.mjs'], {
      cwd: REPO_ROOT,
      stdio: 'inherit',
    });
  }
}

function loadContractMatrix() {
  try {
    return JSON.parse(readText(CONTRACT_MATRIX));
  } catch {
    return { openApiOperations: [], counts: {} };
  }
}

/**
 * Loads the runtime probe-evidence artifact: a map of serviceId -> { passed, ... } recording that a
 * real test/IT run proved the service at runtime. This is the ONLY input that can lift a service off
 * the static REAL_CODE_NOT_PROBED ceiling to REAL_PROVEN (see classifyMaturity). Absent file -> {} so
 * the generator stays honest (no REAL_PROVEN) when no evidence has been supplied.
 */
function loadProbeEvidence() {
  try {
    const parsed = JSON.parse(readText(PROBE_EVIDENCE));
    return parsed && typeof parsed === 'object' ? (parsed.services || parsed) : {};
  } catch {
    return {};
  }
}

function scanMockStubHits(text, filePath, options = {}) {
  const hits = [];
  const fp = rel(filePath);
  const isTest = /\/test\/|\.test\.|\.spec\.|Test\.java$|IT\.java$/i.test(fp);
  for (const { re, label } of MOCK_STUB_PATTERNS) {
    re.lastIndex = 0;
    if (label === 'placeholder-copy') {
      if (options.backendOnly && BACKEND_SAFETY_TOKENS.test(text)) {
        BACKEND_SAFETY_TOKENS.lastIndex = 0;
        continue;
      }
      BACKEND_SAFETY_TOKENS.lastIndex = 0;
      if (isTest) continue;
      // In a backend path, flag only if SOME occurrence is not an honest
      // readiness-gated note (a `<flag>Live ? … : "coming soon"` degradation string
      // is a real capability gate, not placeholder UX). Frontend surfaces are scanned
      // without backendOnly, so a "coming soon" page there still flags.
      let genuine = false;
      for (let m = re.exec(text); m; m = re.exec(text)) {
        if (!(options.backendOnly && BACKEND_READINESS_NOTE.test(lineAt(text, m.index)))) {
          genuine = true;
          break;
        }
      }
      re.lastIndex = 0;
      if (genuine) hits.push({ file: fp, pattern: label });
      continue;
    }
    if (label === 'mock-data-var' && isTest) continue;
    if (re.test(text)) hits.push({ file: fp, pattern: label });
    re.lastIndex = 0;
  }
  return hits;
}

/**
 * Detect a hardcoded data collection that is rendered as product data:
 *   const UPPER_SNAKE = [ { ... }, ... ]   ...later...   UPPER_SNAKE.map(
 * This is the real-world shape of fixture panels (UNBILLED_CHARGES, STAFF_ROSTER,
 * PURCHASE_ORDERS) that the simple mockData|sampleData regex misses. Config-style
 * collections (nav/menu/steps/columns/options) are excluded — those are legitimate
 * static config, not fake product data.
 */
const CONFIG_COLLECTION_NAME =
  /(NAV|MENU|TAB|STEP|ROUTE|LINK|ICON|COLOR|COLOUR|OPTION|FILTER|COLUMN|CONFIG|LABEL|KEY|FIELD|SCHEMA|SORT|VARIANT|SIZE|BREAKPOINT|LOCALE|PERMISSION|ROLE|ZONE|PLANE|SURFACE|LEVEL|TYPE|CATEGOR|SECTION|ACTION|LANE|PROVINCE|CHANNEL|TEMPLATE|DEFINITION|DESCRIPTOR)/;
// Transactional / temporal / clinical fields mark a *data row* (a fake invoice,
// charge, roster entry, report) as opposed to presentational config/taxonomy.
const DATA_ROW_FIELD =
  /\b(amount|total|subtotal|price|cost|balance|due|paid|unpaid|outstanding|invoice|charge|payment|claim|qty|quantity|stock|onHand|reorder|reference|patient|mrn|account|date|dueDate|issuedAt|createdAt|timestamp|requestedAt|submittedAt)\s*:/i;
function scanHardcodedCollections(text, filePath) {
  const fp = rel(filePath);
  if (/\/test\/|\.test\.|\.spec\.|\/fixtures\//i.test(fp)) return [];
  const hits = [];
  const declRe = /const\s+([A-Z][A-Z0-9_]{3,})\s*(?::[^=]+)?=\s*\[\s*(\{[\s\S]*?\})/g;
  let m;
  while ((m = declRe.exec(text)) !== null) {
    const name = m[1];
    const firstRecord = m[2] || '';
    if (CONFIG_COLLECTION_NAME.test(name)) continue;
    // Must look like fabricated DATA (transactional/temporal/clinical fields),
    // and must actually be rendered (.map) — not just a constant lookup table.
    if (!DATA_ROW_FIELD.test(firstRecord)) continue;
    if (new RegExp(`\\b${name}\\b\\.map\\s*\\(`).test(text)) {
      hits.push({ file: fp, pattern: 'hardcoded-collection', detail: name });
    }
  }
  return hits;
}

/**
 * Detect a process-memory store presented as durable persistence.
 *
 * Deliberately narrow to avoid false positives: a class named *Store.java (the
 * codebase convention for a backing store) whose state lives in a concurrent
 * in-memory collection and which has NO JPA/JDBC/repository in the same file —
 * i.e. the in-memory collection IS the sole backing. A broader "any controller
 * with a Map field" rule flagged 56/92 services (noise); this precise rule
 * catches exactly the genuine in-memory history stores. *Cache* stores are
 * excluded — a cache in front of a real store is legitimate.
 */
function scanInMemoryStore(text, filePath) {
  const fp = rel(filePath);
  if (/Cache/.test(path.basename(fp)) || /@Cacheable/.test(text)) return [];
  const hasDurableBacking = /Repository|jpa|jdbcTemplate|@Entity|entityManager/i.test(text);
  if (hasDurableBacking) return [];
  const concurrentField =
    /=\s*new\s+(?:ConcurrentHashMap|ConcurrentLinkedDeque|CopyOnWriteArrayList|ArrayDeque|ConcurrentLinkedQueue|ConcurrentSkipListMap)\s*</.test(text);
  // Rule 1 (original, narrow): a *Store class whose state is a concurrent in-memory collection.
  if (/Store\.java$/.test(fp) && concurrentField) {
    return [{ file: fp, pattern: 'in-memory-store' }];
  }
  // Rule 2 (widened, precise): a Controller/Service holding a STATIC mutable collection
  // as its backing data and serving/mutating it — e.g. PatientController's seeded
  // CopyOnWriteArrayList. Requires a static field initializer + a mutation call, so a
  // local variable or a config map does not trip it.
  if (/(?:Controller|Service)\.java$/.test(fp)) {
    // Tolerate nested generics (e.g. List<Map<String,Object>>) between the type and `=`.
    const staticCollectionField =
      /\bstatic\s+(?:final\s+)?[\w.]+(?:<[^=;{}]*>)?\s+\w+\s*=\s*new\s+(?:ConcurrentHashMap|CopyOnWriteArrayList|ConcurrentLinkedDeque|ArrayDeque|ConcurrentLinkedQueue|LinkedHashMap|HashMap|ArrayList|LinkedList|TreeMap)\s*</.test(text);
    const mutated = /\.\s*(?:add|put|remove|removeIf|set|clear)\s*\(/.test(text);
    const served = /@(?:Get|Post|Put|Patch|Delete)Mapping|@RequestMapping/.test(text);
    if (staticCollectionField && mutated && served) {
      return [{ file: fp, pattern: 'in-memory-backing' }];
    }
  }
  return [];
}

/**
 * General incomplete/stub markers in a product Java path that the security and
 * mock-data detectors miss. High-precision, actionable markers only: a bare
 * `Placeholder` (no colon — colon form is the security detector's), and
 * actionable `TODO: wire/implement/...`. Honest 501 NOT_IMPLEMENTED responses and
 * UnsupportedOperationException are intentionally NOT gate-flagged here (they are
 * honest contract behaviour) — they are tracked in the full register instead.
 */
const STUB_MARKER_PATTERNS = [
  // A genuine stub-placeholder promises future replacement of CODE behaviour
  // ("Placeholder — actual summary fetched later"). Require that promise so domain
  // uses of the word ("placeholder row", "placeholder document id") are excluded.
  { re: /\bPlaceholder\b[\s:—–-]+[^.\n]*\b(?:actual|real|fetch|wire|implement|integrat|will be|to be|for now|TODO|not yet|temporar|stub)/i, label: 'stub-placeholder' },
  { re: /TODO:?\s*(?:wire|implement|hook\s*up|finish|complete|replace with real)\b/i, label: 'todo-wire' },
];
/**
 * A stub-placeholder promises future replacement of BEHAVIOUR ("Placeholder —
 * actual summary fetched later"). When the same phrase instead describes a
 * guarded-against SENTINEL value — a dev secret the code must never accept, or a
 * default that means "no real endpoint configured" — it is a fail-closed security
 * control, the OPPOSITE of a stub. These tokens (guard verbs + sentinel framing +
 * webhook/secret nouns) mark that framing, scoped to the matched phrase itself.
 */
const STUB_PLACEHOLDER_GUARD_CONTEXT =
  /must never|must not|never authenticate|reject|fails? closed|sentinel|default that means|means\s+"?no\b|webhook|secret|pepper|hmac|credential/i;

function scanStubMarkers(text, filePath) {
  const fp = rel(filePath);
  if (/\/test\/java\/|Test\.java$|IT\.java$/.test(fp)) return [];
  const hits = [];
  for (const { re, label } of STUB_MARKER_PATTERNS) {
    const m = re.exec(text);
    if (!m) continue;
    if (label === 'stub-placeholder' && STUB_PLACEHOLDER_GUARD_CONTEXT.test(m[0])) {
      continue;
    }
    hits.push({ file: fp, pattern: label });
  }
  return hits;
}

/**
 * Detect security/crypto/authz placeholders left in product paths.
 */
const SECURITY_PLACEHOLDER_PATTERNS = [
  { re: /TODO[^\n]*(?:real key|key material|tshepo-keys|fetch real)/i, label: 'crypto-key-placeholder' },
  { re: /deriv(?:e|es)\s+a\s+local\s+key/i, label: 'crypto-key-placeholder' },
  { re: /TODO\s*\(\s*role-check\s*\)/i, label: 'authz-placeholder' },
  { re: /TODO[^\n]*(?:authz|authorization|permission|tshepo)/i, label: 'authz-placeholder' },
  { re: /Placeholder:/, label: 'controller-placeholder' },
];
function scanSecurityPlaceholders(text, filePath) {
  const fp = rel(filePath);
  if (/\/test\/java\/|Test\.java$|IT\.java$/.test(fp)) return [];
  const hits = [];
  for (const { re, label } of SECURITY_PLACEHOLDER_PATTERNS) {
    if (re.test(text)) hits.push({ file: fp, pattern: label });
  }
  return hits;
}

function countFiles(dir, filter) {
  return walkFiles(dir, filter).length;
}

function buildServiceSearchTerms(svc, module) {
  const terms = new Set([
    module,
    svc.id,
    ...(svc.product_names || []),
    ...(SERVICE_UI_ALIASES[svc.id] || []),
  ]);
  const stem = svc.id.replace(/-service$/, '').replace(/-adapter$/, '').replace(/-agent$/, '');
  if (stem.length > 3) terms.add(stem);
  return [...terms].filter(Boolean);
}

function buildMobileSearchTerms(svc, module) {
  const terms = new Set([
    ...buildServiceSearchTerms(svc, module),
    ...(MOBILE_UI_ALIASES[svc.id] || []),
    ...(MOBILE_BFF_PATH_PATTERNS[svc.id] || []),
  ]);
  return [...terms].filter(Boolean);
}

function collectMobileHits(svc, module) {
  const mobileHits = [];
  const searchTerms = buildMobileSearchTerms(svc, module);
  for (const relRoot of MOBILE_SCAN_ROOTS) {
    const screensRoot = path.join(MOBILE_ROOT, relRoot);
    if (!fs.existsSync(screensRoot)) continue;
    const appLabel = relRoot.split('/')[0];
    walkFiles(screensRoot, (p) => /\.(tsx|ts)$/.test(p)).forEach((f) => {
      const text = readText(f);
      for (const term of searchTerms) {
        if (term.length > 3 && text.includes(term)) {
          mobileHits.push(`${appLabel}:${rel(f)}`);
          break;
        }
      }
    });
  }
  return mobileHits;
}

function applyCompletionDimensions(svc, dimensions) {
  if (svc.id === 'experience-bff') {
    dimensions.bffWiring = 'n/a';
  }
  if (!mobileParityRequired(svc.id) && !isInternalOnly(svc.id)) {
    dimensions.mobileUi = 'n/a';
  }
  return dimensions;
}

function scanServiceModule(svc, contractMatrix, bffClientMap, probeEvidence = {}) {
  const module = svc.maven_module;
  const modulePath = path.join(SERVICES_DIR, module);
  const exists = fs.existsSync(path.join(modulePath, 'pom.xml'));

  const migrationCount = exists
    ? countFiles(modulePath, (p) => /db\/migration\/V\d+__/.test(p))
    : 0;
  const entityCount = exists
    ? countFiles(modulePath, (p) => p.endsWith('Entity.java') || p.endsWith('.java') && /\/domain\//.test(p))
    : 0;
  const repoCount = exists
    ? countFiles(modulePath, (p) => p.endsWith('Repository.java'))
    : 0;
  const serviceLayerCount = exists
    ? countFiles(modulePath, (p) => /Service\.java$/.test(p) && !p.includes('Application.java'))
    : 0;

  let routes = [];
  if (exists) {
    routes = extractSpringRoutes(path.join(modulePath, 'src/main/java'));
  }
  const stubRouteCount = routes.filter((r) => r.stubHit).length;
  const controllerCount = countFiles(modulePath, (p) => p.endsWith('Controller.java'));

  const contractFile = OPENAPI_BY_MODULE[module] || defaultOpenApiContractFilename(module);
  const contractPath = path.join(CONTRACTS_OPENAPI, contractFile);
  const hasContract = fs.existsSync(contractPath);

  const contractOps = (contractMatrix.openApiOperations || []).filter(
    (op) => op.mavenModule === module || op.modules?.includes?.(module)
  );
  const contractViolations = contractOps.filter(
    (op) => op.status === 'missing' || op.status === 'partial' || op.status === 'orphan-handler'
  ).length;

  const bffClients = bffClientMap.get(module) || [];
  const bffRoutes = bffClientMap.get('__routes__') || [];
  const directPublicOrFhir = DIRECT_PUBLIC_OR_FHIR.has(svc.id);
  if (directPublicOrFhir && bffClients.length === 0) {
    bffClients.push('direct-public-or-fhir');
  }

  // UI references: search one-ui-shell for module/id patterns
  const searchTerms = buildServiceSearchTerms(svc, module);
  const uiHits = [];
  if (fs.existsSync(UI_SHELL)) {
    walkFiles(path.join(UI_SHELL, 'src'), (p) => /\.(tsx|ts)$/.test(p)).forEach((f) => {
      const text = readText(f);
      for (const term of searchTerms) {
        if (term.length > 3 && text.includes(term)) {
          uiHits.push(rel(f));
          break;
        }
      }
    });
  }

  const mobileHits = collectMobileHits(svc, module);

  const testCount =
    (exists ? countFiles(modulePath, (p) => /Test\.java$/.test(p) || /IT\.java$/.test(p)) : 0);

  const mockStubHits = [];
  const securityPlaceholderHits = [];
  if (exists) {
    // Cap is generous headroom over the largest module (~456 files) so the
    // composition layer (experience-bff) is fully scanned — a silent low cap was
    // hiding its in-memory stores ("green by exclusion").
    walkFiles(modulePath, (p) => p.endsWith('.java') && !/\/test\/java\//.test(p)).slice(0, 1200).forEach((f) => {
      const t = readText(f);
      mockStubHits.push(...scanMockStubHits(t, f, { backendOnly: true }));
      mockStubHits.push(...scanInMemoryStore(t, f));
      mockStubHits.push(...scanStubMarkers(t, f));
      securityPlaceholderHits.push(...scanSecurityPlaceholders(t, f));
    });
  }

  const bffDownstream = bffClientMap.get('__downstream__')?.get(module);
  const bffOrphanPaths = bffDownstream
    ? computeBffOrphanRoutes([...bffDownstream], routes)
    : [];

  const authzReadiness = exists ? scanAuthzAuditReadiness(modulePath, svc.id) : { status: 'absent', checks: {}, missing: ['missing-module'], isTrustPlane: false };
  const authzDim = authzDimFromReadiness(authzReadiness);

  // triState(count, thinThreshold) returns 'thin' for `count <= thinThreshold`. A
  // threshold of 0 therefore makes 'thin' UNREACHABLE — `count <= 0` is false for
  // every count that is not already 'absent' — so these five dimensions were binary:
  // one migration file, one test file or one BFF client scored the same 'real' as a
  // hundred. That is why `tests: real` held for 104/104 services and the
  // datasource-less experience-bff scored `database: real` off 45 migration files
  // with 0 entities. Restored to the function's own default of 1, so a single-file
  // dimension reads 'thin' (present but minimal) rather than 'real'.
  const dimensions = applyCompletionDimensions(svc, {
    database: triState(migrationCount, 1),
    entitiesRepos: triState(entityCount + repoCount, 1),
    serviceLayer: triState(serviceLayerCount, 1),
    controllers: triState(controllerCount + routes.length, 1),
    contract: hasContract ? (contractViolations > 0 ? 'thin' : 'real') : 'absent',
    bffWiring: triState(bffClients.length, 1),
    frontendUi: triState(uiHits.length, 1),
    mobileUi: triState(mobileHits.length, 1),
    tests: triState(testCount, 1),
    authzAudit: authzDim,
  });

  const internalOnlyDocPath = path.join(REPO_ROOT, 'docs/audits/internal-only', `${svc.id}.md`);
  const internalOnlyDocumented =
    isInternalOnly(svc.id) &&
    (fs.existsSync(internalOnlyDocPath) ||
      readText(path.join(REPO_ROOT, 'docs/architecture/SERVICE_INVENTORY.md')).includes(svc.id));

  const mobileApplicable = mobileParityRequired(svc.id);
  const bffApplicable = svc.id !== 'experience-bff';

  const record = {
    id: svc.id,
    mavenModule: module,
    repoPath: exists ? rel(modulePath) : `services/${module} (missing)`,
    domain: svc.domain || '',
    plane: svc.primary_plane || svc.plane || '',
    productNames: svc.product_names || [],
    sovereign: svc.sovereign === true,
    sovereignGroup: svc.sovereign_group || null,
    registryStatus: {
      production: svc.production_status,
      implementation: svc.implementation_status,
      frontendWiring: svc.frontend_wiring_status,
      apiContract: svc.api_contract_status,
      authzAudit: svc.authz_audit_status,
      authzAuditCode: authzReadiness.status,
    },
    authzReadiness,
    counts: {
      migrations: migrationCount,
      entities: entityCount,
      repositories: repoCount,
      serviceLayer: serviceLayerCount,
      controllers: controllerCount,
      routes: routes.length,
      stubRoutes: stubRouteCount,
      contractOperations: contractOps.length,
      contractViolations,
      bffClients: bffClients.length,
      uiReferences: uiHits.length,
      mobileReferences: mobileHits.length,
      tests: testCount,
    },
    dimensions,
    productStatus: overallProductStatus(dimensions, svc),
    contractFile: hasContract ? contractFile : null,
    bffClients,
    uiPaths: uiHits.slice(0, 20),
    mobilePaths: mobileHits.slice(0, 10),
    mockStubHits: mockStubHits.slice(0, 10),
    securityPlaceholderHits: securityPlaceholderHits.slice(0, 10),
    stubRouteCount,
    // Was the literal `0`, assigned once and never computed — which made gap L
    // ("BFF exists, downstream service not wired") structurally unreachable, exactly
    // like the four detectors repaired in phase0/i-gate-truth. Now measured.
    bffOrphanRoutes: bffOrphanPaths.length,
    bffOrphanPaths: bffOrphanPaths.slice(0, 10),
    internalOnlyDocumented,
    frontendExpected: !isInternalOnly(svc.id) && svc.frontend_wiring_status !== 'not-applicable',
    expectsPersistence: !module.includes('adapter') && !module.includes('gateway'),
    traceability: {
      q1_realBackendCapabilities: dimensions.controllers !== 'absent' || dimensions.serviceLayer !== 'absent',
      q2_exposedViaApi: dimensions.controllers !== 'absent',
      q3_wiredViaBff: !bffApplicable || dimensions.bffWiring !== 'absent',
      q4_visibleInUi: dimensions.frontendUi !== 'absent' || isInternalOnly(svc.id),
      q5_visibleOnMobile: !mobileApplicable || dimensions.mobileUi === 'n/a' || dimensions.mobileUi !== 'absent',
      q6_fakePartialDisconnected: mockStubHits.length > 0 || stubRouteCount > 0 || dimensions.frontendUi === 'thin',
      q7_backendNoUi: dimensions.controllers !== 'absent' && dimensions.frontendUi === 'absent' && !isInternalOnly(svc.id),
      q8_uiNoBackend: dimensions.frontendUi !== 'absent' && dimensions.controllers === 'absent',
      q9_persistsToDb: dimensions.database !== 'absent' && migrationCount > 0,
      q10_fixtureOnly: mockStubHits.length > 0 && dimensions.database === 'absent',
    },
    phase6Complete:
      isInternalOnly(svc.id)
        ? internalOnlyDocumented && dimensions.controllers !== 'absent'
        : overallProductStatus(dimensions, svc) === 'real',
  };

  // Runtime probe evidence (if supplied) is the only lever that lifts a service to REAL_PROVEN.
  record.probeEvidence = probeEvidence[svc.id] || null;
  record.gaps = classifyServiceGaps(record);
  record.maturity = classifyMaturity(record);
  // Honest phase-6: a fixture-backed or placeholder-carrying service is NOT
  // "complete", even if every file-existence dimension is present. Internal-only
  // services still complete on documented rationale + backend.
  record.phase6Complete = isInternalOnly(svc.id)
    ? internalOnlyDocumented && dimensions.controllers !== 'absent'
    : record.maturity === MATURITY.REAL_PROVEN || record.maturity === MATURITY.REAL_CODE_NOT_PROBED;
  return record;
}

function buildBffClientMap() {
  const map = new Map();
  const bffJavaRoot = path.join(BFF_DIR, 'src/main/java');
  const clientModuleOverrides = BFF_CLIENT_MODULE_OVERRIDES;

  const addClient = (module, clientName) => {
    if (!module) return;
    if (!map.has(module)) map.set(module, []);
    if (!map.get(module).includes(clientName)) map.get(module).push(clientName);
  };

  // Downstream paths each BFF client actually calls, per owning module. This is what
  // makes bffOrphanRoutes computable: "the BFF calls X on this service" can now be
  // checked against "this service serves X". It was only ever possible to ask that
  // once extractClassBase stopped dropping class-level prefixes — before, every
  // service route was a bare fragment with nothing to match against.
  const downstream = new Map();
  const addDownstream = (module, text) => {
    if (!module) return;
    if (!downstream.has(module)) downstream.set(module, new Set());
    const set = downstream.get(module);
    for (const m of text.matchAll(/["'](\/(?:internal|api)\/v\d+\/[^"'\s?]*)["']/g)) {
      const lineStart = text.lastIndexOf('\n', m.index) + 1;
      let lineEnd = text.indexOf('\n', m.index);
      if (lineEnd < 0) lineEnd = text.length;
      const line = text.slice(lineStart, lineEnd);
      // A quoted path is not necessarily a path the BFF CALLS.
      //  - javadoc/comments merely cite routes;
      //  - authorization helpers take a path as the SUBJECT of a policy decision.
      // TshepoAuthzServiceClient passes "/internal/v1/shell/workspace-state" to
      // syntheticAuthorizeVerdict as the `:path` being authorized; tshepo-authz is
      // right not to serve it, and counting it as a missing downstream route is a
      // false positive. Excluding every path the BFF itself serves would also catch
      // this one, but it is too blunt: this BFF proxies many routes at the SAME path
      // as the downstream service, so that rule also discards genuine orphans.
      if (/^\s*(\*|\/\/|\/\*)/.test(line)) continue;
      if (/authoriz|verdict|policy|ext_?authz/i.test(line)) continue;
      set.add(m[1]);
    }
  };

  walkFiles(bffJavaRoot, (p) => p.endsWith('Client.java')).forEach((f) => {
    const name = path.basename(f, '.java');
    const text = readText(f);
    let module = clientModuleOverrides[name];
    if (!module) {
      module = name
        .replace(/ServiceClient$/, '-service')
        .replace(/Client$/, '-service')
        .replace(/([a-z])([A-Z])/g, '$1-$2')
        .toLowerCase();
    }
    addClient(module, name);
    addDownstream(module, text);
    // Parse javadoc @code references
    const codeRef = text.match(/@code\s+([^}]+)/)?.[1] || '';
    for (const [client, mod] of Object.entries(clientModuleOverrides)) {
      if (text.includes(client) || codeRef.includes(mod.replace(/-service$/, ''))) {
        addClient(mod, client);
      }
    }
  });

  // Controllers that delegate without dedicated *ServiceClient filename
  walkFiles(bffJavaRoot, (p) => p.endsWith('Controller.java')).forEach((f) => {
    const text = readText(f);
    const ctrl = path.basename(f, '.java');
    for (const [client, mod] of Object.entries(clientModuleOverrides)) {
      if (text.includes(client)) addClient(mod, `${ctrl}→${client}`);
    }
    // Match service id substrings in controller (ProductRegistryController, ReferralsController, etc.)
    for (const mod of fs.existsSync(SERVICES_DIR) ? fs.readdirSync(SERVICES_DIR) : []) {
      if (!mod.endsWith('-service') && !mod.endsWith('-adapter') && !mod.endsWith('-agent')) continue;
      const stem = mod.replace(/-service$/, '').replace(/-/g, '');
      if (stem.length > 4 && text.toLowerCase().includes(stem.toLowerCase())) {
        addClient(mod, `${ctrl}→inline`);
      }
    }
    if (/llm-orchestration-base-url|llmBaseUrl/.test(text)) {
      addClient('llm-orchestration-service', `${ctrl}→llm-proxy`);
    }
  });

  const bffYml = readText(path.join(BFF_DIR, 'src/main/resources/application.yml'));
  for (const m of bffYml.matchAll(/^\s{4}([\w][\w-]*)-base-url:/gm)) {
    const key = m[1];
    const module = key.endsWith('-service') || key.endsWith('-adapter') || key.endsWith('-agent') ? key : `${key}-service`;
    addClient(module, `${key}→yml-base-url`);
  }

  const bffRoutes = extractSpringRoutes(bffJavaRoot);
  map.set('__routes__', bffRoutes);
  map.set('__downstream__', downstream);
  return map;
}

/**
 * Downstream paths the BFF calls on a service that the service does not serve.
 *
 * Deliberately conservative — this feeds a blocking gap, and a noisy orphan detector
 * would teach people to ignore it:
 *  - only complete-looking paths are considered (>= 3 segments after the version),
 *    because BFF clients frequently build URIs by concatenation and a fragment is
 *    not evidence of a missing route;
 *  - path variables are wildcarded on BOTH sides, so `{id}` matches `{patientId}`;
 *  - a prefix match in EITHER direction counts as served, since a literal may be a
 *    parent of the real route or vice versa;
 * Comment and authorization-subject literals are excluded at collection time, in
 * buildBffClientMap — see the note there.
 * Anything still unmatched is a path the BFF calls and the service does not answer.
 */
export function computeBffOrphanRoutes(downstreamPaths, serviceRoutes) {
  const norm = (p) => p.split('?')[0].replace(/\{[^}]*\}/g, '{}').replace(/\/+$/, '').toLowerCase();
  const served = serviceRoutes.filter((r) => !r.classLevel).map((r) => norm(r.path));
  if (!served.length) return [];
  const matches = (p, list) => list.some((s) => s === p || s.startsWith(`${p}/`) || p.startsWith(`${s}/`));
  return downstreamPaths.filter((raw) => {
    const p = norm(raw);
    if (p.split('/').filter(Boolean).length < 4) return false; // e.g. /internal/v1/x -> too coarse
    return !matches(p, served);
  });
}

function ingestModuleText(moduleText, visited, paths, persist, modulePath, fixtureHits = null) {
  if (modulePath && visited.has(modulePath)) return;
  if (modulePath) visited.add(modulePath);
  // Surface-level fixture detection: scan imported components/libs (not the entry
  // page itself — buildFrontendSurface scans that) so fixtures living inside a
  // mounted component are attributed to the surface that renders them.
  if (fixtureHits && modulePath) {
    fixtureHits.push(...scanMockStubHits(moduleText, modulePath));
    fixtureHits.push(...scanHardcodedCollections(moduleText, modulePath));
  }
  for (const m of moduleText.matchAll(/["'](\/internal\/v1\/[^"']+)["']/g)) paths.add(m[1]);
  for (const m of moduleText.matchAll(/["'](\/api\/v1\/[^"']+)["']/g)) paths.add(m[1]);
  if (/VASHANDI_BASE_PATH\s*=\s*["'](\/internal\/v1\/[^"']+)["']/.test(moduleText)) {
    paths.add(moduleText.match(/VASHANDI_BASE_PATH\s*=\s*["'](\/internal\/v1\/[^"']+)["']/)[1]);
  }
  if (/useMutation|mutateAsync/.test(moduleText)) persist.mutation = true;
  if (/invalidateQueries|refetch|onSuccess/.test(moduleText)) persist.persistHint = true;
  if (/buildSearchPath|apiClient\.(get|post|put|patch|delete)/.test(moduleText)) {
    paths.add('apiClient-dynamic');
  }
  const libImports = [
    ...moduleText.matchAll(/from\s+["']@\/lib\/([^"']+)["']/g),
    ...moduleText.matchAll(/from\s+["']@\/hooks\/(?:queries\/)?([^"']+)["']/g),
  ].map((m) => m[1]);
  for (const libRef of libImports) {
    const candidates = [
      path.join(UI_SHELL, 'src/lib', `${libRef}.ts`),
      path.join(UI_SHELL, 'src/lib', `${libRef}.tsx`),
      path.join(UI_SHELL, 'src/hooks/queries', `${libRef}.ts`),
      path.join(UI_SHELL, 'src/hooks/queries', `${libRef}.tsx`),
      path.join(UI_SHELL, 'src/hooks', `${libRef}.ts`),
      path.join(UI_SHELL, 'src/hooks', `${libRef}.tsx`),
    ];
    for (const candidate of candidates) {
      if (!fs.existsSync(candidate)) continue;
      ingestModuleText(readText(candidate), visited, paths, persist, candidate, fixtureHits);
    }
  }
  const componentImports = [...moduleText.matchAll(/from\s+["']@\/components\/([^"']+)["']/g)].map((m) => m[1]);
  for (const compRef of componentImports) {
    const candidates = [
      path.join(UI_SHELL, 'src/components', `${compRef}.tsx`),
      path.join(UI_SHELL, 'src/components', `${compRef}.ts`),
      path.join(UI_SHELL, 'src/components', `${compRef}/index.tsx`),
      path.join(UI_SHELL, 'src/components', `${compRef}/index.ts`),
    ];
    for (const candidate of candidates) {
      if (!fs.existsSync(candidate)) continue;
      ingestModuleText(readText(candidate), visited, paths, persist, candidate, fixtureHits);
    }
  }
  if (modulePath) {
    const dir = path.dirname(modulePath);
    for (const m of moduleText.matchAll(/from\s+["']\.\/([^"']+)["']/g)) {
      for (const ext of ['.ts', '.tsx']) {
        const candidate = path.join(dir, `${m[1]}${ext}`);
        if (!fs.existsSync(candidate)) continue;
        ingestModuleText(readText(candidate), visited, paths, persist, candidate, fixtureHits);
      }
    }
  }
}

function resolveHookSignals(pageText, visited = new Set(), fixtureHits = null) {
  const paths = new Set();
  const persist = { mutation: false, persistHint: false };
  ingestModuleText(pageText, visited, paths, persist, null, fixtureHits);
  if (/citizenPortalApi|nhumeApi|credentialVerifyUrlForToken|apiClient\.(get|post|put|patch|delete)/.test(pageText)) {
    paths.add('domain-client');
  }
  if (/useVashandi|useNhume|useWorkforceProfiles|useNhumeDelivery/.test(pageText)) {
    paths.add('domain-hook-module');
  }
  if (/fetch\s*\(\s*[`'"]\/internal\/v1\//.test(pageText)) {
    paths.add('inline-bff-fetch');
  }
  if (/fetch\s*\(\s*[`'"]\/api\/v1\//.test(pageText)) {
    paths.add('inline-gateway-fetch');
  }
  if (/\/internal\/v1\/[a-z0-9*/._-]+/i.test(pageText)) {
    paths.add('inline-bff-reference');
  }
  if (/setSubmitted|refreshWorkspace|refetch\s*\(|setRedeemSuccess|setResult\s*\(/.test(pageText)) {
    persist.persistHint = true;
  }
  return { paths: [...paths], persist };
}

function isNavigationHubPage(text) {
  if (!text) return false;
  if (/from\s+["']next\/navigation["']/.test(text) && /redirect\s*\(/.test(text)) return true;
  if (/PlaneWorkspaceShell|HealthOsLauncher|ModuleCard|LauncherCard|OrganizationPlaneContextBar/.test(text)) {
    return true;
  }
  const hubLayout = /PageShell|AppLayout/.test(text);
  const linkGrid =
    /Link\s+from\s+["']next\/link["']/.test(text) &&
    (/(?:SECTIONS|LINKS|_SECTIONS|links)\s*=/.test(text) ||
      /href[=:]\{?["'`]\//.test(text) ||
      /\[\s*["']\/[^"']+["']/.test(text));
  if (hubLayout && linkGrid) return true;
  if (/Life-context hub|Hub with card grid|navigation hub|Admin dashboard with cards/i.test(text)) return true;
  return false;
}

/** Administration & Governance scoped surfaces — link grids with OPA/session contract, not orphan UI. */
function isAdminGovernanceScaffoldPage(text) {
  if (!text) return false;
  if (/ScopedAdministrationSurface|AdministrationGovernanceShell/.test(text)) return true;
  if (/OnboardFlowWizard|OnboardReviewSubmit/.test(text)) return true;
  if (/useSessionExperienceContract/.test(text) && /ADMINISTRATION_SURFACES|surfaceId|ONBOARD_FLOW_STEPS/.test(text)) {
    return true;
  }
  return false;
}

function resolveHookBffPaths(pageText, visited = new Set(), fixtureHits = null) {
  const paths = new Set();
  const { paths: hookPaths, persist } = resolveHookSignals(pageText, visited, fixtureHits);
  hookPaths.forEach((p) => paths.add(p));
  if (/apiClient\.(get|post|put|patch|delete)/.test(pageText)) {
    paths.add('apiClient-dynamic');
  }
  if (/useLogin|useAuthStore|signIn|keycloak/i.test(pageText)) {
    paths.add('auth-keycloak');
  }
  if (/PlaneWorkspaceShell|LauncherCard|HealthOsLauncher|ModuleCard/.test(pageText)) {
    paths.add('shell-composition');
  }
  if (isNavigationHubPage(pageText)) {
    paths.add('navigation-hub');
  }
  if (isAdminGovernanceScaffoldPage(pageText)) {
    paths.add('admin-governance-scaffold');
  }
  if (/VashandiShell/.test(pageText) && /useVashandi|useWorkforce|useAssignments|useRosters|useAttendance/.test(pageText)) {
    paths.add('domain-hook-module');
  }
  if (/from\s+["']next\/navigation["']/.test(pageText) && /redirect\s*\(/.test(pageText)) {
    paths.add('route-delegation');
  }
  if (isClientRedirectShim(pageText)) {
    paths.add('route-delegation');
  }
  return { paths: [...paths], persist };
}

/**
 * A client-side intent-resolution shim: `"use client"` page that computes a target from the
 * search params, calls `router.replace()` and renders nothing. It delegates as completely as a
 * server `redirect()` — the backing belongs to the route it hands off to — so it earns the same
 * `route-delegation` signal rather than a per-route allowlist entry.
 *
 * Deliberately narrow. The `return null` plus no-JSX pair is what makes it a shim: a real page
 * that merely redirects on one branch still renders something on the others and must prove its
 * own backing. Comments are stripped first so prose about JSX in a shim's header cannot satisfy
 * the JSX test. Across the whole app this matches exactly the three shims that exist
 * (/my-life, /provider-workspace, /auth/login/email) and nothing else.
 */
function isClientRedirectShim(pageText) {
  if (!pageText) return false;
  const code = pageText.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
  if (!/from\s+["']next\/navigation["']/.test(code)) return false;
  if (!/router\.(replace|push)\s*\(/.test(code)) return false;
  if (!/return\s+null\s*;/.test(code)) return false;
  return !/<[A-Za-z][\w.]*(\s|\/|>)/.test(code);
}

/** Routes that are shell, auth, legal, or navigation hubs — not direct BFF pages. */
const SURFACE_ALLOWLIST_PREFIXES = [
  '/auth',
  '/privacy',
  '/terms',
  // Public pre-auth orientation pages (/welcome, /welcome/find-care, /welcome/emergency,
  // /welcome/accessibility): static info shells in the same class as /privacy and /terms —
  // no personal data and no sovereign backing by design. (Their only mock/stub hit is a
  // confirmed false positive: the broad json-stringify-render pattern matching a
  // sessionStorage.setItem(JSON.stringify(...)) serialization in the shared
  // useAccessibilityPreferences hook, not a debug render.)
  '/welcome',
  // Public app-discovery / orientation surface (same class as /welcome, /privacy, /terms):
  // honest coming-soon store affordances (no fabricated store deep-links), an email-client
  // "notify me" (no fabricated success state), a static QR encoding the web-app URL, and a
  // static PLATFORMS list. No personal data, no persistence, no sovereign backing by design —
  // a backend "store listing" would be FAKE because the apps are not published yet. If the
  // apps ship and this gains live store metadata it must gain BFF backing and leave this list.
  '/download',
  '/account-deletion',
  '/bootstrap',
  '/platform/all-features',
  '/health-os/command-centre',
  '/production-command-centre',
  '/core-transaction',
  '/client-journey',
  '/provider-workspace',
  '/platform-journey',
  '/clinical',
  '/data-intelligence',
  '/settings',
  '/shell',
  '/admin/keys',
  '/admin/federation',
  '/admin/sidecar-retirement',
  // Client-side print/format view: renders a printable activation letter from query
  // params (Health ID + one-time code) fed by the already-backed activation flow.
  // The code is issued+verified upstream; this page performs no data fetch, no
  // persistence, and carries no passwords or clinical content — same static
  // print-shell class as the /welcome orientation pages. (window.print only.)
  '/registry-admin/activation-letter',
  // Telemedicine operating-model doctrine shells: clinical group taxonomy and
  // virtual-hospital capability orientation rendered from checked-in doctrine
  // data (src/lib/telemedicine/{clinical-groups,session-modes,virtual-hospitals}.ts)
  // — same static-orientation class as /platform/all-features. If these become
  // live registries they must gain sovereign BFF backing and leave this list.
  '/work/telemedicine/groups',
  '/work/telemedicine/virtual-hospitals',
];

function isAllowlistedShellRoute(routePath) {
  return SURFACE_ALLOWLIST_PREFIXES.some(
    (p) => routePath === p || routePath.startsWith(`${p}/`) || routePath.startsWith(p),
  );
}

function parseRouteRegistryEntries(source) {
  return [...source.matchAll(/\{\s*path:\s*"([^"]+)"[^}]*zone:\s*"([^"]+)"[^}]*pageTitle:\s*"([^"]+)"/g)].map(
    (m) => ({ path: m[1], zone: m[2], pageTitle: m[3] }),
  );
}

function buildFrontendSurface(routePath, zone, pageTitle) {
  const pageRel = routePath.replace(/^\//, '');
  const pagePath = path.join(UI_SHELL, 'src/app', pageRel, 'page.tsx');
  const exists = fs.existsSync(pagePath);
  const text = exists ? readText(pagePath) : '';
  const componentFixtureHits = [];
  const resolved = exists
    ? resolveHookBffPaths(text, new Set(), componentFixtureHits)
    : { paths: [], persist: { mutation: false, persistHint: false } };
  const inlineBff = exists ? [...text.matchAll(/["'](\/internal\/v1\/[^"']+)["']/g)].map((m) => m[1]) : [];
  const bffPaths = [...new Set([...resolved.paths, ...inlineBff])];
  const gatewayPaths = exists ? [...text.matchAll(/["'](\/api\/v1\/[^"']+)["']/g)].map((m) => m[1]) : [];
  // Page-level + component-level fixture detection (component fixtures are the
  // ones the old page-only scan missed — e.g. BillingPanel/PortalHealthReporting).
  const mockStubHits = exists
    ? [...scanMockStubHits(text, pagePath), ...scanHardcodedCollections(text, pagePath), ...componentFixtureHits]
    : [];
  const hasMutation =
    /useMutation|mutate\(|onSubmit|handleSubmit|formAction/.test(text) || resolved.persist.mutation;
  const hasPersistHint =
    /invalidateQueries|refetch|router\.refresh|toast\.success|mutateAsync/.test(text) ||
    resolved.persist.persistHint;
  const deadActionHints = exists ? (text.match(/onClick=\{\(\)\s*=>\s*\{\s*\}\}/g) || []) : [];
  const hardcodedDataHints = exists ? (text.match(/mockData|sampleData|hardcoded|demoPatient/gi) || []) : [];
  const allowlisted = isAllowlistedShellRoute(routePath);
  const hasBacking =
    bffPaths.filter((p) => p.startsWith('/')).length > 0 ||
    gatewayPaths.length > 0 ||
    bffPaths.some((p) => !p.startsWith('/')) ||
    allowlisted;

  const surface = {
    path: routePath,
    pageTitle,
    zone,
    pageFile: exists ? rel(pagePath) : null,
    bffPaths: bffPaths.filter((p) => p.startsWith('/')).slice(0, 8),
    backingSignals: bffPaths.filter((p) => !p.startsWith('/')),
    gatewayPaths: [...new Set(gatewayPaths)].slice(0, 5),
    mockStubHits,
    hasMutation,
    hasPersistHint,
    deadActionHints: deadActionHints.length,
    hardcodedDataHints: hardcodedDataHints.length,
    readsRealData: hasBacking,
    writesRealData: hasMutation && hasPersistHint,
    completeVsCosmetic: mockStubHits.length === 0 && (hasBacking || !exists),
    allowlistedShell: allowlisted,
  };
  surface.gaps = allowlisted ? [] : classifySurfaceGaps(surface);
  return surface;
}

function scanFrontendSurfaces() {
  const surfaces = [];
  const routesTs = readText(path.join(UI_SHELL, 'src/lib/routes.ts'));
  const adminRegistryPath = path.join(UI_SHELL, 'src/lib/administration-governance/route-registry.ts');
  const adminRegistry = fs.existsSync(adminRegistryPath) ? readText(adminRegistryPath) : '';

  const routeEntries = [
    ...parseRouteRegistryEntries(routesTs),
    ...parseRouteRegistryEntries(adminRegistry),
  ];
  const seen = new Set();
  for (const entry of routeEntries) {
    if (seen.has(entry.path)) continue;
    seen.add(entry.path);
    surfaces.push(buildFrontendSurface(entry.path, entry.zone, entry.pageTitle));
  }

  // Unregistered pages (app routes with no registry entry)
  const registered = new Set(surfaces.map((s) => s.path));
  walkFiles(path.join(UI_SHELL, 'src/app'), (p) => p.endsWith('page.tsx')).forEach((page) => {
    let routeSuffix = rel(page).replace('ui/one-ui-shell/src/app/', '').replace(/\/page\.tsx$/, '');
    if (routeSuffix === 'page.tsx') routeSuffix = '';
    const routePath = routeSuffix ? `/${routeSuffix}` : '/';
    if (registered.has(routePath)) return;
    const text = readText(page);
    const resolved = resolveHookBffPaths(text);
    const inlineBff = [...text.matchAll(/["'](\/internal\/v1\/[^"']+)["']/g)].map((m) => m[1]);
    const bffPaths = [...new Set([...resolved.paths, ...inlineBff])];
    const hasBacking =
      bffPaths.filter((p) => p.startsWith('/')).length > 0 ||
      bffPaths.some((p) => !p.startsWith('/'));
    const allowlisted = isAllowlistedShellRoute(routePath);
    const unregisteredSurface = {
      path: routePath,
      pageTitle: '(unregistered)',
      zone: 'unregistered',
      pageFile: rel(page),
      bffPaths: bffPaths.filter((p) => p.startsWith('/')).slice(0, 5),
      backingSignals: bffPaths.filter((p) => !p.startsWith('/')),
      gatewayPaths: [],
      mockStubHits: scanMockStubHits(text, page),
      readsRealData: hasBacking,
      writesRealData: false,
      completeVsCosmetic: hasBacking,
      allowlistedShell: allowlisted,
      gaps: [],
    };
    if (!allowlisted && !hasBacking) {
      unregisteredSurface.gaps.push({
        category: 'D',
        severity: 'low',
        description: 'Unregistered route without BFF backing',
        impactScore: 1,
      });
    }
    surfaces.push(unregisteredSurface);
  });

  return surfaces;
}

function scanMobileSurfaces() {
  const surfaces = [];
  for (const app of ['citizen-app', 'provider-app']) {
    const root = path.join(MOBILE_ROOT, app, 'src/screens');
    if (!fs.existsSync(root)) continue;
    walkFiles(root, (p) => p.endsWith('.tsx') && !p.includes('.test.')).forEach((screen) => {
      const text = readText(screen);
      const bffPaths = [...new Set([...text.matchAll(/["'](\/internal\/v1\/[^"']+)["']/g)].map((m) => m[1]))];
      surfaces.push({
        app,
        screen: rel(screen),
        bffPaths: bffPaths.slice(0, 5),
        mockStubHits: scanMockStubHits(text, screen),
        readsRealData: bffPaths.length > 0,
      });
    });
  }
  return surfaces;
}

function detectRegistryDrift(registry) {
  const registered = new Set((registry.services || []).map((s) => s.maven_module));
  const onDisk = fs.existsSync(SERVICES_DIR)
    ? fs.readdirSync(SERVICES_DIR).filter((d) => fs.existsSync(path.join(SERVICES_DIR, d, 'pom.xml')))
    : [];
  const unregistered = onDisk.filter((m) => !registered.has(m) && m !== 'shared-core');
  const missingOnDisk = [...registered].filter((m) => !fs.existsSync(path.join(SERVICES_DIR, m, 'pom.xml')));
  return { unregisteredModules: unregistered, missingOnDiskModules: missingOnDisk };
}

function renderServiceInventory(data) {
  const lines = [
    '# Product Truth — Service Inventory',
    '',
    `> Generated: ${data.generatedAt}`,
    `> Scanner: \`scripts/completeness/generate-product-truth.mjs\``,
    `> Total services: **${data.summary.totalServices}** | Libraries: **${data.summary.totalLibraries}** | UI workspaces: **${data.summary.totalUiWorkspaces}**`,
    '',
  ];

  if (data.registryDrift.unregisteredModules.length || data.registryDrift.missingOnDiskModules.length) {
    lines.push('## Registry drift', '');
    if (data.registryDrift.unregisteredModules.length) {
      lines.push('**Unregistered modules on disk:**', ...data.registryDrift.unregisteredModules.map((m) => `- \`${m}\``), '');
    }
    if (data.registryDrift.missingOnDiskModules.length) {
      lines.push('**Registry entries missing on disk:**', ...data.registryDrift.missingOnDiskModules.map((m) => `- \`${m}\``), '');
    }
  }

  lines.push(
    '## Summary by product status',
    '',
    '| Status | Count |',
    '|--------|------:|',
  );
  for (const [k, v] of Object.entries(data.summary.byProductStatus)) {
    lines.push(`| ${k} | ${v} |`);
  }

  lines.push(
    '',
    '## Service inventory',
    '',
    '| Service | Plane | DB | API | Contract | BFF | Web UI | Mobile | Tests | Product status |',
    '|---------|-------|----|-----|----------|-----|--------|--------|-------|----------------|',
  );

  for (const s of data.services) {
    const d = s.dimensions;
    lines.push(
      `| ${s.id} | ${s.plane} | ${d.database} | ${d.controllers} | ${d.contract} | ${d.bffWiring} | ${d.frontendUi} | ${d.mobileUi} | ${d.tests} | ${s.productStatus} |`,
    );
  }

  lines.push('', '## Libraries', '');
  for (const lib of data.libraries) {
    lines.push(`- **${lib.id}** — \`${lib.path}\``);
  }

  return lines.join('\n') + '\n';
}

function renderBackendUiTraceability(data) {
  const lines = [
    '# Product Truth — Backend-to-UI Traceability',
    '',
    `> Generated: ${data.generatedAt}`,
    '',
    'For each service: backend capabilities → API → BFF → UI → mobile → persistence.',
    '',
  ];

  for (const s of data.services) {
    const t = s.traceability;
    lines.push(`## ${s.id}`, '');
    lines.push(`- **Path:** \`${s.repoPath}\``);
    lines.push(`- **Domain:** ${s.domain} (${s.plane})`);
    lines.push(`- **Product status:** ${s.productStatus}`);
    lines.push('');
    lines.push('| # | Question | Answer |');
    lines.push('|---|----------|--------|');
    lines.push(`| 1 | Real backend capabilities? | ${t.q1_realBackendCapabilities ? 'Yes' : 'No'} (${s.counts.controllers} controllers, ${s.counts.routes} routes) |`);
    lines.push(`| 2 | Exposed via API/contracts? | ${t.q2_exposedViaApi ? 'Yes' : 'No'} (contract: ${s.contractFile || 'none'}) |`);
    lines.push(`| 3 | Wired via BFF? | ${t.q3_wiredViaBff ? 'Yes' : 'No'} (${s.counts.bffClients} clients) |`);
    lines.push(`| 4 | Visible in UI? | ${t.q4_visibleInUi ? 'Yes' : 'No'} (${s.counts.uiReferences} refs) |`);
    lines.push(`| 5 | Visible on mobile? | ${t.q5_visibleOnMobile ? 'Yes' : 'No'} (${s.counts.mobileReferences} refs) |`);
    lines.push(`| 6 | Fake/partial/disconnected? | ${t.q6_fakePartialDisconnected ? 'Yes — review' : 'No'} |`);
    lines.push(`| 7 | Backend without UI? | ${t.q7_backendNoUi ? '**Yes — gap**' : 'No'} |`);
    lines.push(`| 8 | UI without backend? | ${t.q8_uiNoBackend ? '**Yes — gap**' : 'No'} |`);
    lines.push(`| 9 | Persists to DB? | ${t.q9_persistsToDb ? 'Yes' : 'No'} (${s.counts.migrations} migrations) |`);
    lines.push(`| 10 | Fixture-only flows? | ${t.q10_fixtureOnly ? '**Yes — gap**' : 'No'} |`);
    if (s.uiPaths.length) {
      lines.push('', '**UI references (sample):**', ...s.uiPaths.slice(0, 5).map((p) => `- \`${p}\``));
    }
    if (s.gaps.length) {
      lines.push('', '**Gaps:**', ...s.gaps.map((g) => `- [${g.category}] ${g.description} (${g.severity})`));
    }
    lines.push('');
  }

  return lines.join('\n') + '\n';
}

function renderFrontendBackendTraceability(data) {
  const lines = [
    '# Product Truth — Frontend-to-Backend Traceability',
    '',
    `> Generated: ${data.generatedAt}`,
    `> Web surfaces: **${data.frontendSurfaces.length}** | Mobile screens: **${data.mobileSurfaces.length}**`,
    '',
    '## Web routes (one-ui-shell)',
    '',
    '| Route | Title | Zone | BFF backing | Gateway | Reads real | Writes real | Mock/stub | Gaps |',
    '|-------|-------|------|-------------|---------|------------|-------------|-----------|------|',
  ];

  for (const s of data.frontendSurfaces.slice(0, 500)) {
    const gapStr = (s.gaps || []).map((g) => g.category).join(',') || '—';
    lines.push(
      `| ${s.path} | ${s.pageTitle} | ${s.zone} | ${s.bffPaths?.length ? 'yes' : 'no'} | ${s.gatewayPaths?.length ? 'yes' : 'no'} | ${s.readsRealData ? 'yes' : 'no'} | ${s.writesRealData ? 'yes' : 'no'} | ${s.mockStubHits?.length ? 'yes' : 'no'} | ${gapStr} |`,
    );
  }

  lines.push('', '## Mobile screens', '', '| App | Screen | BFF paths | Mock/stub |', '|-----|--------|-----------|-----------|');
  for (const m of data.mobileSurfaces.slice(0, 200)) {
    lines.push(`| ${m.app} | ${m.screen} | ${m.bffPaths?.length || 0} | ${m.mockStubHits?.length ? 'yes' : 'no'} |`);
  }

  return lines.join('\n') + '\n';
}

function renderGapRegister(data) {
  const allGaps = sortGapsByPriority([
    ...data.services.flatMap((s) => s.gaps.map((g) => ({ ...g, entity: s.id, entityType: 'service' }))),
    ...data.frontendSurfaces.flatMap((s) =>
      (s.gaps || []).map((g) => ({ ...g, entity: s.path, entityType: 'web-route' })),
    ),
  ]);
  const agg = aggregateGapCounts(allGaps);

  const lines = [
    '# Product Truth — Gap Register',
    '',
    `> Generated: ${data.generatedAt}`,
    `> Total gaps: **${agg.total}**`,
    '',
    '## Gap categories (A–R)',
    '',
    '| Cat | Description | Count |',
    '|-----|-------------|------:|',
  ];
  for (const [cat, desc] of Object.entries(GAP_CATEGORIES)) {
    lines.push(`| ${cat} | ${desc} | ${agg.byCategory[cat] || 0} |`);
  }

  lines.push('', '## By severity', '', '| Severity | Count |', '|----------|------:|');
  for (const [sev, n] of Object.entries(agg.bySeverity)) {
    lines.push(`| ${sev} | ${n} |`);
  }

  lines.push('', '## Prioritized gaps (top 100)', '', '| Rank | Entity | Category | Severity | Description |', '|------|--------|----------|----------|-------------|');
  allGaps.slice(0, 100).forEach((g, i) => {
    lines.push(`| ${i + 1} | ${g.entity} | ${g.category} | ${g.severity} | ${g.description} |`);
  });

  lines.push('', '## Services requiring product-owner decision', '');
  const poDecisions = data.services.filter(
    (s) => s.gaps.some((g) => g.severity === 'blocker') || (isInternalOnly(s.id) && !s.internalOnlyDocumented),
  );
  if (!poDecisions.length) {
    lines.push('_None flagged as blocker requiring immediate PO decision._');
  } else {
    for (const s of poDecisions.slice(0, 30)) {
      lines.push(`- **${s.id}** — ${s.gaps.filter((g) => g.severity === 'blocker').map((g) => g.description).join('; ') || 'internal-only documentation needed'}`);
    }
  }

  return lines.join('\n') + '\n';
}

function renderBlueprints(data) {
  const lines = [
    '# Service Completion Blueprints',
    '',
    `> Generated: ${data.generatedAt}`,
    '> End-to-end product expectation per service for mature vNext.',
    '',
    'Each blueprint defines personas, workflows, CRUD, UI minimum, production UI, and tests required.',
    '',
  ];

  for (const s of data.services) {
    const isInternal = isInternalOnly(s.id);
    const needsDeep = s.productStatus === 'thin-or-stubbed' || s.productStatus === 'partial' || s.traceability.q7_backendNoUi;
    lines.push(`## ${s.id}`, '');
    lines.push(`**Product names:** ${(s.productNames || []).join(', ') || s.id}`);
    lines.push(`**Plane/domain:** ${s.plane} / ${s.domain}`);
    lines.push(`**Current status:** ${s.productStatus}`);
    lines.push('');

    if (isInternal) {
      lines.push(
        '- **Classification:** Internal-only platform service',
        '- **Primary users:** Platform operators, integration engineers, SRE',
        '- **Minimum viable surface:** Admin/ops API + documented internal-only rationale',
        '- **Production complete:** Contract + implementation + observability + runbook',
        '- **Tests:** Contract IT + smoke for primary endpoints',
        '',
      );
      continue;
    }

    lines.push(
      '### Primary personas',
      `- Operators and domain users for ${s.domain || s.plane} plane capabilities`,
      s.sovereign ? `- Sovereign boundary: **${s.sovereignGroup}**` : '- Standard governed service consumer',
      '',
      '### Main workflows',
      `- List/search ${s.productNames[0] || s.id} records`,
      `- Create and update governed transactions with TSHEPO authz`,
      `- Detail view with audit trail and status transitions where applicable`,
      '',
      '### Minimum viable complete UI',
      needsDeep
        ? `- **Priority gap closure:** Wire BFF + one-ui-shell list/detail for ${s.id}`
        : `- List + detail routes backed by real BFF hooks`,
      `- Empty/loading/error states with honest maturity labels`,
      '',
      '### Production-grade complete UI',
      `- Full CRUD where domain permits; search/filter; role-based visibility`,
      `- Mobile parity ${MOBILE_PARITY_REQUIRED.has(s.id) ? '**required**' : 'where user-facing'}`,
      `- Cross-service handoffs documented in core-transaction journey maps`,
      '',
      '### Tests required',
      `- Backend: \`*IT.java\` or controller tests for primary workflows`,
      `- BFF: proxy/controller test for each exposed route family`,
      `- UI: vitest hook/page test + Playwright e2e for critical path`,
      '',
    );
  }

  return lines.join('\n') + '\n';
}

function renderFinalReport(data, allGaps) {
  const agg = aggregateGapCounts(allGaps);
  const userFacing = data.services.filter((s) => !isInternalOnly(s.id) && s.productStatus !== 'deprecated');
  const phase6Complete = data.services.filter((s) => s.phase6Complete).length;
  const userFacingReal = userFacing.filter((s) => s.productStatus === 'real').length;
  const internalOnly = data.services.filter((s) => s.productStatus === 'internal-only').length;
  const partial = data.services.filter((s) => ['partial', 'thin-or-stubbed', 'mostly-real', 'unknown'].includes(s.productStatus)).length;
  const backendOnly = data.services.filter((s) => s.traceability.q7_backendNoUi).length;
  const uiOnly = data.services.filter((s) => s.traceability.q8_uiNoBackend).length;
  const withDb = data.services.filter((s) => s.dimensions.database !== 'absent').length;
  const mockHits = data.services.filter((s) => s.mockStubHits.length > 0).length;

  return `# Full Product Truth Recovery Report

> Generated: ${data.generatedAt}
> Branch: \`${data.branch}\`

## Executive summary

| Metric | Count |
|--------|------:|
| Total services audited | ${data.summary.totalServices} |
| Backend services | ${data.summary.totalServices} |
| Shared libraries | ${data.summary.totalLibraries} |
| Frontend surfaces (routes) | ${data.frontendSurfaces.length} |
| Mobile screens | ${data.mobileSurfaces.length} |
| BFF route handlers | ${data.summary.bffRouteCount} |
| OpenAPI contracts | ${data.summary.contractCount} |
| Services with DB persistence | ${withDb} |
| **Phase 6 complete (user-facing + documented internal)** | **${phase6Complete}** |
| User-facing services with \`real\` code present (file-existence axis) | ${userFacingReal} / ${userFacing.length} |
| — of those, **runtime-proven** (REAL_PROVEN) | **${data.summary.phase6.userFacingRealProven}** |
| Services internal-only (documented) | ${internalOnly} |
| Services partially complete | ${partial} |
| Services backend-only (no UI) | ${backendOnly} |
| Services UI-only (no backend) | ${uiOnly} |
| Services with mock/stub hits | ${mockHits} |
| Total classified gaps | ${agg.total} |
| Blocker gaps | ${agg.bySeverity.blocker || 0} |
| High severity gaps | ${agg.bySeverity.high || 0} |
| Cross-service cohesion | ${data.summary.crossServiceCohesion?.pass || 0}/${data.summary.crossServiceCohesion?.total || 14} pass |

> **Honesty note:** \`real\` above is the file-existence axis (code present + wired),
> NOT proof the capability runs. The honest maturity axis is below; this static scan
> can never emit \`REAL_PROVEN\` — that requires a runtime/test probe artifact (Wave 5/6).

## Maturity breakdown (honest)

| Maturity | Count |
|----------|------:|
${Object.entries(data.summary.byMaturity || {}).map(([m, n]) => `| ${m} | ${n} |`).join('\n')}

## Quality gates added

- \`scripts/guard/check-product-truth.sh\` — product-truth gap gate (threshold 0)
- \`scripts/guard/check-phase6-service-completion.sh\` — Phase 6 completion gate
- \`scripts/guard/check-cross-service-cohesion.sh\` — cross-service journey cohesion
- Wired into \`scripts/pipeline/run-local-quality-gates.sh\`

## Artifacts produced

| Artifact | Path |
|----------|------|
| Canonical dataset | [product-truth.json](../../reports/product/product-truth.json) |
| Service inventory | [product-truth-service-inventory.md](./product-truth-service-inventory.md) |
| Backend→UI traceability | [product-truth-backend-ui-traceability.md](./product-truth-backend-ui-traceability.md) |
| Frontend→Backend traceability | [product-truth-frontend-backend-traceability.md](./product-truth-frontend-backend-traceability.md) |
| Gap register | [product-truth-gap-register.md](./product-truth-gap-register.md) |
| Completion blueprints | [service-completion-blueprints.md](../product/service-completion-blueprints.md) |

## Remaining gaps by severity

${Object.entries(agg.bySeverity).map(([k, v]) => `- **${k}:** ${v}`).join('\n') || '_None_'}

## Implementation status

**Phase 6 (full-stack service completion)** — user-facing services must reach \`real\` product status with BFF + web wiring (+ mobile where required). Internal-only services require documented rationale under \`docs/audits/internal-only/\`.

**Phase 7 (cross-service cohesion)** — ${data.summary.crossServiceCohesion?.pass || 0}/${data.summary.crossServiceCohesion?.total || 14} journeys pass with golden-thread tests and preview runtime smoke.

## Services requiring product-owner decision

See [product-truth-gap-register.md](./product-truth-gap-register.md#services-requiring-product-owner-decision).

## Regenerate

\`\`\`bash
cd scripts/completeness && npm run product-truth
bash scripts/guard/check-product-truth.sh
bash scripts/guard/check-phase6-service-completion.sh
\`\`\`
`;
}

/**
 * SYS-2 capability-grained view. Joins backend routes (per service, with stub hits) ×
 * frontend surfaces (by their bffPaths) × BFF downstream routes × contract ops, bucketed into
 * capabilities by {@link capabilityKeyFor}. Each capability gets a disposition
 * (real-proven / real / partial / fixture / empty) + the evidence that drove it. This is the
 * finer grain the service-level maturity axis can't express; it never self-promotes to
 * real-proven (that still requires the service's probe-evidence).
 */
function buildCapabilityMatrix(registry, contractMatrix, frontendSurfaces, bffClientMap, probeEvidence) {
  const caps = new Map(); // key: `${serviceId}::${capKey}` -> capability record

  const ensureCap = (serviceId, capKey) => {
    const id = `${serviceId}::${capKey}`;
    if (!caps.has(id)) {
      caps.set(id, {
        id,
        service: serviceId,
        capability: capKey,
        backendRoutes: 0,
        stubRoutes: 0,
        methods: new Set(),
        samplePaths: [],
        frontendSurfaces: 0,
        frontendFixtures: 0,
        frontendPaths: [],
        bffProxied: 0,
        contractOps: 0,
        contractUnowned: 0,
      });
    }
    return caps.get(id);
  };

  // Pass 1 — backend routes per service (the spine: a capability is owned by the service whose
  // routes define it).
  const provenServices = new Set(
    Object.entries(probeEvidence).filter(([, v]) => v && v.passed).map(([k]) => k),
  );
  const routePrefixToService = []; // [{ prefix: 'patient-safety/reports', service }]
  for (const svc of registry.services || []) {
    const module = svc.maven_module || svc.id;
    const javaRoot = path.join(SERVICES_DIR, module, 'src/main/java');
    if (!fs.existsSync(javaRoot)) continue;
    let routes = [];
    try {
      routes = extractSpringRoutes(javaRoot);
    } catch {
      routes = [];
    }
    for (const r of routes) {
      const capKey = capabilityKeyFor(r.path);
      const cap = ensureCap(svc.id, capKey);
      cap.backendRoutes += 1;
      if (r.stubHit) cap.stubRoutes += 1;
      if (r.method) cap.methods.add(r.method.toUpperCase());
      if (cap.samplePaths.length < 6 && !cap.samplePaths.includes(r.path)) cap.samplePaths.push(r.path);
      routePrefixToService.push({ prefix: capKey, service: svc.id });
    }
  }
  // capKey -> owning service (first backend owner wins; used to attribute frontend/bff hits).
  const capOwner = new Map();
  for (const { prefix, service } of routePrefixToService) {
    if (!capOwner.has(prefix)) capOwner.set(prefix, service);
  }

  // Pass 2 — frontend surfaces, attributed by their bffPaths' capability key.
  for (const surface of frontendSurfaces) {
    const isFixture = (surface.mockStubHits || []).length > 0 || (surface.gaps || []).some((g) => g.category === 'F');
    const seen = new Set();
    for (const bp of surface.bffPaths || []) {
      const capKey = capabilityKeyFor(bp);
      if (seen.has(capKey)) continue;
      seen.add(capKey);
      const owner = capOwner.get(capKey) || `frontend:${capKey.split('/')[0] || 'unknown'}`;
      const cap = ensureCap(owner, capKey);
      cap.frontendSurfaces += 1;
      if (isFixture) cap.frontendFixtures += 1;
      if (cap.frontendPaths.length < 6 && !cap.frontendPaths.includes(surface.path)) {
        cap.frontendPaths.push(surface.path);
      }
    }
  }

  // Pass 3 — BFF downstream routes proxied to each capability.
  for (const r of bffClientMap.get('__routes__') || []) {
    const capKey = capabilityKeyFor(r.path);
    const owner = capOwner.get(capKey);
    if (!owner) continue; // only count BFF proxying onto a real backend capability
    ensureCap(owner, capKey).bffProxied += 1;
  }

  // Pass 4 — contract ops by capability key (coarse coverage signal).
  for (const op of contractMatrix.openApiOperations || []) {
    const capKey = capabilityKeyFor(op.path);
    const owner = capOwner.get(capKey);
    if (!owner) continue;
    const cap = ensureCap(owner, capKey);
    cap.contractOps += 1;
    if (op.implStatus && op.implStatus !== 'implemented') cap.contractUnowned += 1;
  }

  // Finalize: classify + shape for output.
  const dispositions = {};
  const records = [...caps.values()]
    .map((c) => {
      const proven = provenServices.has(c.service);
      const disposition = classifyCapabilityDisposition({
        routeCount: c.backendRoutes,
        stubRouteCount: c.stubRoutes,
        frontendSurfaceCount: c.frontendSurfaces,
        frontendFixtureCount: c.frontendFixtures,
        contractUnowned: c.contractUnowned,
        proven,
      });
      dispositions[disposition] = (dispositions[disposition] || 0) + 1;
      return {
        id: c.id,
        service: c.service,
        capability: c.capability,
        disposition,
        proven,
        backendRoutes: c.backendRoutes,
        stubRoutes: c.stubRoutes,
        methods: [...c.methods].sort(),
        frontendSurfaces: c.frontendSurfaces,
        frontendFixtures: c.frontendFixtures,
        bffProxied: c.bffProxied,
        contractOps: c.contractOps,
        contractUnowned: c.contractUnowned,
        samplePaths: c.samplePaths,
        frontendPaths: c.frontendPaths,
      };
    })
    .sort((a, b) => (a.service + a.capability).localeCompare(b.service + b.capability));

  return {
    generatedAt: new Date().toISOString(),
    summary: {
      totalCapabilities: records.length,
      byDisposition: dispositions,
      provenCapabilities: records.filter((r) => r.disposition === CAPABILITY_DISPOSITION.REAL_PROVEN).length,
      fixtureCapabilities: records.filter((r) => r.disposition === CAPABILITY_DISPOSITION.FIXTURE).length,
    },
    capabilities: records,
  };
}

function main() {
  ensurePrerequisites();

  const registry = yaml.load(readText(REGISTRY_PATH));
  const contractMatrix = loadContractMatrix();
  const bffClientMap = buildBffClientMap();
  const probeEvidence = loadProbeEvidence();
  const registryDrift = detectRegistryDrift(registry);

  const services = (registry.services || []).map((svc) =>
    scanServiceModule(svc, contractMatrix, bffClientMap, probeEvidence),
  );

  const libraries = (registry.libraries || []).map((lib) => ({
    id: lib.id,
    mavenModule: lib.maven_module,
    path: lib.path || `libs/${lib.maven_module}`,
  }));

  const uiWorkspaces = fs.existsSync(path.join(REPO_ROOT, 'ui'))
    ? fs.readdirSync(path.join(REPO_ROOT, 'ui')).filter((d) =>
        fs.existsSync(path.join(REPO_ROOT, 'ui', d, 'package.json')),
      )
    : [];

  const frontendSurfaces = scanFrontendSurfaces();
  const mobileSurfaces = scanMobileSurfaces();
  const capabilityMatrix = buildCapabilityMatrix(
    registry, contractMatrix, frontendSurfaces, bffClientMap, probeEvidence,
  );

  const byProductStatus = {};
  const byMaturity = {};
  for (const s of services) {
    byProductStatus[s.productStatus] = (byProductStatus[s.productStatus] || 0) + 1;
    byMaturity[s.maturity] = (byMaturity[s.maturity] || 0) + 1;
  }

  let branch = 'unknown';
  try {
    branch = spawnSync('git', ['branch', '--show-current'], { encoding: 'utf8' }).stdout.trim();
  } catch {
    /* ignore */
  }

  const allGaps = sortGapsByPriority([
    ...services.flatMap((s) => s.gaps),
    ...frontendSurfaces.flatMap((s) => s.gaps || []),
  ]);

  const cohesionEvaluations = evaluateCrossServiceCohesion({ services }, REPO_ROOT);
  const cohesionSummary = summarizeCohesion(cohesionEvaluations);
  const phase6CompleteCount = services.filter((s) => s.phase6Complete).length;
  const userFacingCount = services.filter((s) => !isInternalOnly(s.id) && s.productStatus !== 'deprecated').length;

  const data = {
    generatedAt: new Date().toISOString(),
    branch,
    registryDrift,
    summary: {
      totalServices: services.length,
      totalLibraries: libraries.length,
      totalUiWorkspaces: uiWorkspaces.length,
      bffRouteCount: (bffClientMap.get('__routes__') || []).length,
      contractCount: walkFiles(CONTRACTS_OPENAPI, (p) => p.endsWith('.yaml')).length,
      byProductStatus,
      byMaturity,
      gapCounts: aggregateGapCounts(allGaps),
      crossServiceCohesion: cohesionSummary,
      phase6: {
        complete: phase6CompleteCount,
        userFacing: userFacingCount,
        userFacingReal: services.filter((s) => !isInternalOnly(s.id) && s.productStatus === 'real').length,
        // Honest: file-existence "real" is NOT runtime-proven. REAL_PROVEN is only
        // emitted when a probe-evidence artifact is supplied (see classifyMaturity).
        userFacingRealProven: services.filter((s) => s.maturity === MATURITY.REAL_PROVEN).length,
      },
      capabilities: capabilityMatrix.summary,
    },
    services,
    libraries,
    uiWorkspaces,
    frontendSurfaces,
    mobileSurfaces,
    crossServiceJourneys: cohesionEvaluations,
  };

  fs.mkdirSync(path.dirname(OUT_JSON), { recursive: true });
  fs.writeFileSync(OUT_JSON, JSON.stringify(data, null, 2));
  fs.writeFileSync(CAPABILITY_MATRIX_OUT, JSON.stringify(capabilityMatrix, null, 2));

  const auditDir = path.join(REPO_ROOT, 'docs/audits');
  const productDir = path.join(REPO_ROOT, 'docs/product');
  fs.mkdirSync(auditDir, { recursive: true });
  fs.mkdirSync(productDir, { recursive: true });

  fs.writeFileSync(path.join(auditDir, 'product-truth-service-inventory.md'), renderServiceInventory(data));
  fs.writeFileSync(path.join(auditDir, 'product-truth-backend-ui-traceability.md'), renderBackendUiTraceability(data));
  fs.writeFileSync(
    path.join(auditDir, 'product-truth-frontend-backend-traceability.md'),
    renderFrontendBackendTraceability(data),
  );
  fs.writeFileSync(path.join(auditDir, 'product-truth-gap-register.md'), renderGapRegister(data));
  fs.writeFileSync(path.join(productDir, 'service-completion-blueprints.md'), renderBlueprints(data));
  fs.writeFileSync(
    path.join(auditDir, 'full-product-truth-recovery-report.md'),
    renderFinalReport(data, allGaps),
  );

  // Cohesion validation artifact (Phase 7 scaffold)
  fs.writeFileSync(
    path.join(auditDir, 'product-truth-cross-service-cohesion.md'),
    renderCohesionReport(data),
  );

  console.log('Wrote', rel(OUT_JSON));
  console.log('Services:', services.length, '| Gaps:', allGaps.length);
  console.log('Product status:', JSON.stringify(byProductStatus));
}

function renderCohesionReport(data) {
  const summary = data.summary?.crossServiceCohesion || summarizeCohesion(data.crossServiceJourneys || []);
  const lines = [
    '# Product Truth — Cross-Service Cohesion Validation',
    '',
    `> Generated: ${data.generatedAt}`,
    '',
    'End-to-end journey validation. Each journey must pass: identity context → BFF → domain services → persistence → UI refresh.',
    '',
    `**Summary:** ${summary.pass}/${summary.total} pass | ${summary.needsWork} needs-work | ${summary.missingTest} missing-test`,
    '',
    '| Journey | Services involved | Status | Golden-thread tests | Notes |',
    '|---------|-------------------|--------|---------------------|-------|',
  ];

  for (const row of data.crossServiceJourneys || []) {
    lines.push(
      `| ${row.id} | ${row.services.join(', ')} | **${row.status}** | ${(row.testsFound || []).join(', ') || '—'} | ${row.notes} |`,
    );
  }

  lines.push('', '## Journey definitions', '');
  for (const journey of CROSS_SERVICE_JOURNEYS) {
    lines.push(`- **${journey.id}**: UI ${journey.uiRoutes.join(', ')} → BFF ${journey.bffPaths.join(', ')}`);
  }

  return lines.join('\n') + '\n';
}

// Export pure detectors for unit testing; only run the full scan when invoked
// directly (node generate-product-truth.mjs), not when imported by tests.
export { scanMockStubHits, scanHardcodedCollections, scanInMemoryStore, scanSecurityPlaceholders, scanStubMarkers };

if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}
