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
  triState,
  overallProductStatus,
  GAP_CATEGORIES,
  MOBILE_PARITY_REQUIRED,
  authzDimFromReadiness,
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

const MOCK_STUB_PATTERNS = [
  { re: /mockData|fakeResponse|demoPatient|sampleData|fixtureData/gi, label: 'mock-data-var' },
  { re: /coming soon|under construction|placeholder page/gi, label: 'placeholder-copy' },
  { re: /<pre[^>]*>\s*\{JSON\.stringify/gi, label: 'json-debug-pre' },
  { re: /\{[^}]*JSON\.stringify\s*\([^)]*\)[^}]*\}/g, label: 'json-stringify-render' },
  { re: /onClick=\{\(\)\s*=>\s*\{\s*\}\}/g, label: 'empty-onClick' },
  { re: /TODO:\s*implement/gi, label: 'todo-implement' },
];

/** Production rail safety tokens — not UX mock/stub indicators. */
const BACKEND_SAFETY_TOKENS = /BLOCKED_NOT_LIVE|NOT_LIVE_CAPABLE|BLOCKED_PRE_LIVE/g;

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
};

const BFF_CLIENT_MODULE_OVERRIDES = {
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

function scanMockStubHits(text, filePath, options = {}) {
  const hits = [];
  const fp = rel(filePath);
  const isTest = /\/test\/|\.test\.|\.spec\.|Test\.java$|IT\.java$/i.test(fp);
  for (const { re, label } of MOCK_STUB_PATTERNS) {
    if (re.test(text)) {
      if (options.backendOnly && label === 'placeholder-copy' && BACKEND_SAFETY_TOKENS.test(text)) {
        re.lastIndex = 0;
        continue;
      }
      if (isTest && (label === 'placeholder-copy' || label === 'mock-data-var')) {
        re.lastIndex = 0;
        continue;
      }
      hits.push({ file: fp, pattern: label });
    }
    re.lastIndex = 0;
  }
  return hits;
}

function countFiles(dir, filter) {
  return walkFiles(dir, filter).length;
}

function scanServiceModule(svc, contractMatrix, bffClientMap) {
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
  const searchTerms = [module, svc.id, ...(svc.product_names || []), ...(SERVICE_UI_ALIASES[svc.id] || [])].filter(Boolean);
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

  // Mobile references
  const mobileHits = [];
  for (const app of ['citizen-app', 'provider-app']) {
    const screensRoot = path.join(MOBILE_ROOT, app, 'src');
    if (!fs.existsSync(screensRoot)) continue;
    walkFiles(screensRoot, (p) => /\.(tsx|ts)$/.test(p)).forEach((f) => {
      const text = readText(f);
      for (const term of searchTerms) {
        if (term.length > 3 && text.includes(term)) {
          mobileHits.push(`${app}:${rel(f)}`);
          break;
        }
      }
    });
  }

  const testCount =
    (exists ? countFiles(modulePath, (p) => /Test\.java$/.test(p) || /IT\.java$/.test(p)) : 0);

  const mockStubHits = [];
  if (exists) {
    walkFiles(modulePath, (p) => p.endsWith('.java') && !/\/test\/java\//.test(p)).slice(0, 200).forEach((f) => {
      mockStubHits.push(...scanMockStubHits(readText(f), f, { backendOnly: true }));
    });
  }

  const authzReadiness = exists ? scanAuthzAuditReadiness(modulePath, svc.id) : { status: 'absent', checks: {}, missing: ['missing-module'], isTrustPlane: false };
  const authzDim = authzDimFromReadiness(authzReadiness);

  const dimensions = {
    database: triState(migrationCount, 0),
    entitiesRepos: triState(entityCount + repoCount, 1),
    serviceLayer: triState(serviceLayerCount, 0),
    controllers: triState(controllerCount + routes.length, 1),
    contract: hasContract ? (contractViolations > 0 ? 'thin' : 'real') : 'absent',
    bffWiring: triState(bffClients.length, 0),
    frontendUi: triState(uiHits.length, 1),
    mobileUi: triState(mobileHits.length, 0),
    tests: triState(testCount, 0),
    authzAudit: authzDim,
  };

  const internalOnlyDocPath = path.join(REPO_ROOT, 'docs/audits/internal-only', `${svc.id}.md`);
  const internalOnlyDocumented =
    isInternalOnly(svc.id) &&
    (fs.existsSync(internalOnlyDocPath) ||
      readText(path.join(REPO_ROOT, 'docs/architecture/SERVICE_INVENTORY.md')).includes(svc.id));

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
    stubRouteCount,
    bffOrphanRoutes: 0,
    internalOnlyDocumented,
    frontendExpected: !isInternalOnly(svc.id) && svc.frontend_wiring_status !== 'not-applicable',
    expectsPersistence: !module.includes('adapter') && !module.includes('gateway'),
    traceability: {
      q1_realBackendCapabilities: dimensions.controllers !== 'absent' || dimensions.serviceLayer !== 'absent',
      q2_exposedViaApi: dimensions.controllers !== 'absent',
      q3_wiredViaBff: dimensions.bffWiring !== 'absent',
      q4_visibleInUi: dimensions.frontendUi !== 'absent',
      q5_visibleOnMobile: dimensions.mobileUi !== 'absent',
      q6_fakePartialDisconnected: mockStubHits.length > 0 || stubRouteCount > 0 || dimensions.frontendUi === 'thin',
      q7_backendNoUi: dimensions.controllers !== 'absent' && dimensions.frontendUi === 'absent' && !isInternalOnly(svc.id),
      q8_uiNoBackend: dimensions.frontendUi !== 'absent' && dimensions.controllers === 'absent',
      q9_persistsToDb: dimensions.database !== 'absent' && migrationCount > 0,
      q10_fixtureOnly: mockStubHits.length > 0 && dimensions.database === 'absent',
    },
  };

  record.gaps = classifyServiceGaps(record);
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
  });

  const bffRoutes = extractSpringRoutes(bffJavaRoot);
  map.set('__routes__', bffRoutes);
  return map;
}

function ingestModuleText(moduleText, visited, paths, persist, modulePath) {
  if (modulePath && visited.has(modulePath)) return;
  if (modulePath) visited.add(modulePath);
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
      ingestModuleText(readText(candidate), visited, paths, persist, candidate);
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
      ingestModuleText(readText(candidate), visited, paths, persist, candidate);
    }
  }
  if (modulePath) {
    const dir = path.dirname(modulePath);
    for (const m of moduleText.matchAll(/from\s+["']\.\/([^"']+)["']/g)) {
      for (const ext of ['.ts', '.tsx']) {
        const candidate = path.join(dir, `${m[1]}${ext}`);
        if (!fs.existsSync(candidate)) continue;
        ingestModuleText(readText(candidate), visited, paths, persist, candidate);
      }
    }
  }
}

function resolveHookSignals(pageText, visited = new Set()) {
  const paths = new Set();
  const persist = { mutation: false, persistHint: false };
  ingestModuleText(pageText, visited, paths, persist, null);
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

function resolveHookBffPaths(pageText, visited = new Set()) {
  const paths = new Set();
  const { paths: hookPaths, persist } = resolveHookSignals(pageText, visited);
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
  return { paths: [...paths], persist };
}

/** Routes that are shell, auth, legal, or navigation hubs — not direct BFF pages. */
const SURFACE_ALLOWLIST_PREFIXES = [
  '/auth',
  '/privacy',
  '/terms',
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
  const resolved = exists ? resolveHookBffPaths(text) : { paths: [], persist: { mutation: false, persistHint: false } };
  const inlineBff = exists ? [...text.matchAll(/["'](\/internal\/v1\/[^"']+)["']/g)].map((m) => m[1]) : [];
  const bffPaths = [...new Set([...resolved.paths, ...inlineBff])];
  const gatewayPaths = exists ? [...text.matchAll(/["'](\/api\/v1\/[^"']+)["']/g)].map((m) => m[1]) : [];
  const mockStubHits = exists ? scanMockStubHits(text, pagePath) : [];
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
  const complete = data.services.filter((s) => s.productStatus === 'real' || s.productStatus === 'mostly-real').length;
  const partial = data.services.filter((s) => ['partial', 'thin-or-stubbed', 'unknown'].includes(s.productStatus)).length;
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
| Services fully/mostly complete | ${complete} |
| Services partially complete | ${partial} |
| Services backend-only (no UI) | ${backendOnly} |
| Services UI-only (no backend) | ${uiOnly} |
| Services with mock/stub hits | ${mockHits} |
| Total classified gaps | ${agg.total} |
| Blocker gaps | ${agg.bySeverity.blocker || 0} |
| High severity gaps | ${agg.bySeverity.high || 0} |

## Quality gates added

- \`scripts/guard/check-product-truth.sh\` — advisory gate driven by \`reports/product/product-truth.json\`
- Wired into \`scripts/pipeline/run-local-quality-gates.sh\` (advisory phase)

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

${Object.entries(agg.bySeverity).map(([k, v]) => `- **${k}:** ${v}`).join('\n')}

## Implementation status

Phase 6 (service-by-service fixes) and Phase 7 (cross-service cohesion) are documented in the gap register priority list. Wave 1 should target blocker/high gaps in user-facing clinical, registry, and finance domains.

## Services requiring product-owner decision

See [product-truth-gap-register.md](./product-truth-gap-register.md#services-requiring-product-owner-decision).

## Regenerate

\`\`\`bash
cd scripts/completeness && npm run product-truth
bash scripts/guard/check-product-truth.sh
\`\`\`
`;
}

function main() {
  ensurePrerequisites();

  const registry = yaml.load(readText(REGISTRY_PATH));
  const contractMatrix = loadContractMatrix();
  const bffClientMap = buildBffClientMap();
  const registryDrift = detectRegistryDrift(registry);

  const services = (registry.services || []).map((svc) =>
    scanServiceModule(svc, contractMatrix, bffClientMap),
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

  const byProductStatus = {};
  for (const s of services) {
    byProductStatus[s.productStatus] = (byProductStatus[s.productStatus] || 0) + 1;
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
      gapCounts: aggregateGapCounts(allGaps),
      crossServiceCohesion: cohesionSummary,
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

main();
