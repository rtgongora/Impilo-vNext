/**
 * Lock-in tests for the product-truth honesty machinery (Wave 2).
 *
 * These assert the scanner cannot quietly slide back to "file-existence == real":
 *  - REAL_PROVEN is never emitted without probe evidence
 *  - fixtures / in-memory stores / security placeholders are detected
 *  - genuine config/taxonomy and the landed golden-threads are NOT flagged
 *
 * Run: node --test   (from scripts/completeness)
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  classifyMaturity,
  classifyServiceGaps,
  classifySurfaceGaps,
  MATURITY,
  capabilityKeyFor,
  classifyCapabilityDisposition,
  CAPABILITY_DISPOSITION,
} from '../product-truth-gaps.mjs';
import {
  scanHardcodedCollections,
  scanInMemoryStore,
  scanMockStubHits,
  scanSecurityPlaceholders,
  scanStubMarkers,
} from '../generate-product-truth.mjs';

const realDims = {
  database: 'real', entitiesRepos: 'real', serviceLayer: 'real', controllers: 'real',
  contract: 'real', bffWiring: 'real', frontendUi: 'real', mobileUi: 'real',
  tests: 'real', authzAudit: 'real',
};

// ---- maturity: the central honesty lever -------------------------------------

test('file-existence "real" is REAL_CODE_NOT_PROBED, never REAL_PROVEN', () => {
  const svc = { id: 'vito-service', dimensions: realDims, productStatus: 'real' };
  assert.equal(classifyMaturity(svc), MATURITY.REAL_CODE_NOT_PROBED);
});

test('REAL_PROVEN only with a passing probe-evidence artifact', () => {
  const base = { id: 'vito-service', dimensions: realDims, productStatus: 'real' };
  assert.equal(classifyMaturity({ ...base, probeEvidence: { passed: true } }), MATURITY.REAL_PROVEN);
  assert.equal(classifyMaturity({ ...base, probeEvidence: { passed: false } }), MATURITY.REAL_CODE_NOT_PROBED);
});

test('a fixture/placeholder hit forces FIXTURE_BACKED, overriding file-existence "real"', () => {
  assert.equal(
    classifyMaturity({ id: 'x', dimensions: realDims, productStatus: 'real', mockStubHits: [{ pattern: 'hardcoded-collection' }] }),
    MATURITY.FIXTURE_BACKED,
  );
  assert.equal(
    classifyMaturity({ id: 'x', dimensions: realDims, productStatus: 'real', securityPlaceholderHits: [{ pattern: 'crypto-key-placeholder' }] }),
    MATURITY.FIXTURE_BACKED,
  );
});

// ---- gap classification ------------------------------------------------------

test('security/crypto placeholder emits a category-S gap; crypto is a blocker', () => {
  const gaps = classifyServiceGaps({
    id: 'mushe-wallet-service', dimensions: realDims,
    securityPlaceholderHits: [{ pattern: 'crypto-key-placeholder' }],
  });
  const s = gaps.find((g) => g.category === 'S');
  assert.ok(s, 'expected a category-S gap');
  assert.equal(s.severity, 'blocker');
});

test('authz placeholder is a high (non-blocker) category-S gap', () => {
  const gaps = classifyServiceGaps({
    id: 'community-service', dimensions: realDims,
    securityPlaceholderHits: [{ pattern: 'authz-placeholder' }],
  });
  const s = gaps.find((g) => g.category === 'S');
  assert.equal(s.severity, 'high');
});

// ---- detectors: precision (no false positives on config) ---------------------

test('hardcoded-collection flags fabricated DATA rows', () => {
  const code = `
    const UNBILLED_CHARGES = [
      { reference: 'INV-1', amount: 1200, status: 'unbilled' },
      { reference: 'INV-2', amount: 50, status: 'unbilled' },
    ];
    export function Panel() { return UNBILLED_CHARGES.map((c) => c.reference); }
  `;
  const hits = scanHardcodedCollections(code, '/x/BillingPanel.tsx');
  assert.equal(hits.length, 1);
  assert.equal(hits[0].detail, 'UNBILLED_CHARGES');
});

test('hardcoded-collection does NOT flag config/taxonomy collections', () => {
  for (const code of [
    `const EHR_ACTIONS = [ { id: 'a', label: 'A', href: '/a', icon: X } ]; EHR_ACTIONS.map(x=>x);`,
    `const SEVERITY_LEVELS = [ { value: 'low', label: 'Low', color: 'green' } ]; SEVERITY_LEVELS.map(x=>x);`,
    `const PROVINCES = [ { code: 'HRE', name: 'Harare' } ]; PROVINCES.map(x=>x);`,
    `const BOOKING_TYPES = [ { value: 'walk-in', label: 'Walk in' } ]; BOOKING_TYPES.map(x=>x);`,
  ]) {
    assert.equal(scanHardcodedCollections(code, '/x/Comp.tsx').length, 0, code);
  }
});

test('hardcoded-collection ignores collections that are never rendered', () => {
  const code = `const INVOICES = [ { reference: 'a', amount: 1 } ]; // not mapped`;
  assert.equal(scanHardcodedCollections(code, '/x/Comp.tsx').length, 0);
});

test('in-memory store detector is narrow: *Store with concurrent field, no JPA', () => {
  const store = `class FooStore { private final Map<String,X> m = new ConcurrentHashMap<>(); }`;
  assert.equal(scanInMemoryStore(store, '/x/FooHistoryStore.java').length, 1);
  // a Store that is repository-backed is fine
  const backed = `class FooStore { @Autowired FooRepository repo; }`;
  assert.equal(scanInMemoryStore(backed, '/x/FooStore.java').length, 0);
  // a plain controller with a Map field must NOT trip it (this was the 56/92 flood)
  const ctrl = `class FooController { Map<String,X> cfg = new ConcurrentHashMap<>(); @GetMapping List<X> list(){return null;} }`;
  assert.equal(scanInMemoryStore(ctrl, '/x/FooController.java').length, 0);
});

test('security placeholder detector catches crypto/authz TODOs', () => {
  assert.equal(scanSecurityPlaceholders('// TODO: fetch real key material from tshepo-keys', '/x/Card.java')[0].pattern, 'crypto-key-placeholder');
  assert.equal(scanSecurityPlaceholders('// TODO(role-check): integrate TSHEPO', '/x/Social.java')[0].pattern, 'authz-placeholder');
  assert.equal(scanSecurityPlaceholders('// not a security issue', '/x/Plain.java').length, 0);
});

// ---- widened detectors (gap-discovery pass) ----------------------------------

test('stub-marker detector flags a future-work Placeholder but not a domain placeholder', () => {
  const promise = scanStubMarkers('"{}",  // Placeholder — actual summary fetched from BUTANO at sync time', '/x/WalletEventConsumer.java');
  assert.ok(promise.some((h) => h.pattern === 'stub-placeholder'), 'future-work Placeholder should flag');
  // domain uses of the word are NOT stubs
  assert.equal(scanStubMarkers('/** Placeholder row created from an OROS order before DICOM UID is known. */', '/x/StudyStatus.java').length, 0);
  assert.equal(scanStubMarkers('/** Placeholder document id when no Landela document is attached yet. */', '/x/ShareSlipEventConsumer.java').length, 0);
  // actionable TODO: wire
  assert.ok(scanStubMarkers('// TODO: wire to PctServiceClient', '/x/VitalsController.java').some((h) => h.pattern === 'todo-wire'));
});

test('stub-marker does NOT flag a guarded-against sentinel/dev value (security control, not a stub)', () => {
  // mushex: a dev pepper the guard must NEVER accept for real webhooks — fail-closed control.
  assert.equal(
    scanStubMarkers('    /** Known dev placeholder that must never authenticate real webhooks. */', '/x/MushexSecurityStartupValidator.java').length,
    0,
  );
  // elmis: the sentinel default URL that means "no real endpoint configured" — fail-closed sync.
  assert.equal(
    scanStubMarkers('    /** The placeholder default that means "no real endpoint has been configured". */', '/x/ElmisSyncConnector.java').length,
    0,
  );
  // a genuine future-work stub with no guard/sentinel framing STILL flags.
  assert.ok(
    scanStubMarkers('"{}",  // Placeholder — actual summary fetched from BUTANO at sync time', '/x/WalletEventConsumer.java')
      .some((h) => h.pattern === 'stub-placeholder'),
  );
});

test('placeholder-copy: backend readiness-gated note is honest; a bare "coming soon" page still flags', () => {
  // WalletController: the note only shows while the CARD_GATEWAY rail is off — a real gate.
  const gated = 'boolean aggregatorLive = isRailLive("CARD_GATEWAY");\n    String extNote = aggregatorLive ? null : "Not yet available — coming soon";';
  assert.equal(scanMockStubHits(gated, '/x/WalletController.java', { backendOnly: true }).length, 0);
  // a bare placeholder-copy with no readiness gate is still a stub.
  assert.ok(
    scanMockStubHits('return "This feature is coming soon";', '/x/FooController.java', { backendOnly: true })
      .some((h) => h.pattern === 'placeholder-copy'),
  );
  // a frontend surface (no backendOnly) with "coming soon" still flags — the exemption is backend-only.
  assert.ok(
    scanMockStubHits('<p>Coming soon</p>', '/x/page.tsx')
      .some((h) => h.pattern === 'placeholder-copy'),
  );
});

test('in-memory detector Rule 2 flags a static seeded collection in a controller (nested generics ok)', () => {
  const ctrl = `class PatientController {
    private static final List<Map<String, Object>> PATIENTS = new CopyOnWriteArrayList<>(buildSeeded());
    @GetMapping List<X> list(){ return null; }
    void add(X x){ PATIENTS.add(x); }
  }`;
  assert.equal(scanInMemoryStore(ctrl, '/x/PatientController.java')[0].pattern, 'in-memory-backing');
  // a repository-backed controller with a local map is fine
  const backed = `class FooController { @Autowired FooRepository repo; Map<String,X> local = new HashMap<>(); }`;
  assert.equal(scanInMemoryStore(backed, '/x/FooController.java').length, 0);
});

// ---- generated-artifact invariants (locks the real output) -------------------

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../../..');
const PT = path.join(REPO_ROOT, 'reports/product/product-truth.json');

test('generated product-truth.json reflects honest maturity + baseline (no regression)', { skip: !fs.existsSync(PT) }, () => {
  const d = JSON.parse(fs.readFileSync(PT, 'utf8'));
  const byMaturity = d.summary.byMaturity || {};
  // REAL_PROVEN is gated entirely on the probe-evidence artifact: a service may be REAL_PROVEN
  // ONLY if it has a passing entry there (the static scan can never self-promote). This preserves
  // the original guarantee while letting real runtime proof move the metric.
  const probePath = path.join(REPO_ROOT, 'reports/product/probe-evidence.json');
  const probe = fs.existsSync(probePath)
    ? (JSON.parse(fs.readFileSync(probePath, 'utf8')).services || {})
    : {};
  const provenIds = new Set(
    Object.entries(probe).filter(([, v]) => v && v.passed).map(([k]) => k),
  );
  const realProven = d.services.filter((s) => s.maturity === 'REAL_PROVEN');
  for (const s of realProven) {
    assert.ok(provenIds.has(s.id), `${s.id} is REAL_PROVEN but has no passing probe-evidence entry`);
  }
  assert.ok((byMaturity.REAL_PROVEN || 0) <= provenIds.size, 'REAL_PROVEN cannot exceed the probe-evidence artifact');
  // userFacingRealProven mirrors the REAL_PROVEN maturity count.
  assert.equal(d.summary.phase6.userFacingRealProven, byMaturity.REAL_PROVEN || 0);

  // Service-level gaps (S/F) are now fully paid down — guard against silent re-hiding by
  // asserting the services that previously carried debt stay fixed once landed.
  const flagged = new Set(
    d.services.filter((s) => (s.gaps || []).some((g) => g.category === 'S' || g.category === 'F')).map((s) => s.id),
  );
  // Landed fixes must STAY fixed: 3B community pin authz, 3C clinical level-of-care;
  // paydown P1 mushe-wallet CardController stub; P3 pct/vashandi clinical authz (V018 rules);
  // P2 experience-bff in-memory *Store history projections (migrated to tshepo-audit + Redis).
  for (const id of ['community-service', 'clinical-knowledge-platform-service', 'mushe-wallet-service',
                    'pct-service', 'vashandi-workforce-service', 'experience-bff']) {
    assert.ok(!flagged.has(id), `${id} was fixed and must not be flagged again`);
  }

  // No regression beyond the recorded baseline.
  const baseline = JSON.parse(fs.readFileSync(path.join(REPO_ROOT, 'reports/product/product-truth-baseline.json'), 'utf8'));
  assert.ok(d.summary.gapCounts.total <= baseline.gapBaseline, `gap total ${d.summary.gapCounts.total} exceeds baseline ${baseline.gapBaseline} — fix the gap, do not raise the baseline`);
});

test('capabilityKeyFor drops transport prefix + path params, keeps two segments', () => {
  assert.equal(capabilityKeyFor('/internal/v1/patient-safety/reports/{id}/submit'), 'patient-safety/reports');
  assert.equal(capabilityKeyFor('/api/v1/patient-safety/reports'), 'patient-safety/reports');
  assert.equal(capabilityKeyFor('/internal/v1/rito/cases/{id}/timeline'), 'rito/cases');
  assert.equal(capabilityKeyFor('/'), '(root)');
});

test('classifyCapabilityDisposition is deterministic across the disposition lattice', () => {
  const D = CAPABILITY_DISPOSITION;
  assert.equal(classifyCapabilityDisposition({ routeCount: 0, frontendSurfaceCount: 0 }), D.EMPTY);
  assert.equal(classifyCapabilityDisposition({ routeCount: 0, frontendSurfaceCount: 2, frontendFixtureCount: 2 }), D.FIXTURE);
  assert.equal(classifyCapabilityDisposition({ routeCount: 3, stubRouteCount: 3 }), D.FIXTURE);
  assert.equal(classifyCapabilityDisposition({ routeCount: 3, stubRouteCount: 1 }), D.PARTIAL);
  assert.equal(classifyCapabilityDisposition({ routeCount: 3, contractUnowned: 2 }), D.PARTIAL);
  assert.equal(classifyCapabilityDisposition({ routeCount: 3, stubRouteCount: 0 }), D.REAL);
  // proven only applies when the capability actually has backend routes (never self-promotes empty)
  assert.equal(classifyCapabilityDisposition({ routeCount: 3, stubRouteCount: 0, proven: true }), D.REAL_PROVEN);
  assert.equal(classifyCapabilityDisposition({ routeCount: 0, frontendSurfaceCount: 0, proven: true }), D.EMPTY);
});

test('generated capability-matrix.json is internally consistent with probe evidence', { skip: !fs.existsSync(path.join(REPO_ROOT, 'reports/product/capability-matrix.json')) }, () => {
  const m = JSON.parse(fs.readFileSync(path.join(REPO_ROOT, 'reports/product/capability-matrix.json'), 'utf8'));
  const probePath = path.join(REPO_ROOT, 'reports/product/probe-evidence.json');
  const probe = fs.existsSync(probePath) ? (JSON.parse(fs.readFileSync(probePath, 'utf8')).services || {}) : {};
  const provenIds = new Set(Object.entries(probe).filter(([, v]) => v && v.passed).map(([k]) => k));

  assert.ok(m.summary.totalCapabilities > 0, 'expected capabilities to be discovered');
  for (const c of m.capabilities) {
    // real-proven capabilities may only belong to a probe-proven service and must have backend routes.
    if (c.disposition === 'real-proven') {
      assert.ok(provenIds.has(c.service), `${c.id} is real-proven but ${c.service} has no probe evidence`);
      assert.ok(c.backendRoutes > 0, `${c.id} is real-proven with no backend routes`);
    }
    // fixture capabilities are either backend-less (frontend-only) or all-stub.
    if (c.disposition === 'fixture') {
      assert.ok(c.backendRoutes === 0 || c.stubRoutes === c.backendRoutes,
        `${c.id} marked fixture but has non-stub backend routes`);
    }
  }
  assert.equal(
    m.summary.byDisposition['real-proven'] || 0,
    m.capabilities.filter((c) => c.disposition === 'real-proven').length,
  );
});
