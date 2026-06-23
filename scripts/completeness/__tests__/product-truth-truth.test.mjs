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
} from '../product-truth-gaps.mjs';
import {
  scanHardcodedCollections,
  scanInMemoryStore,
  scanSecurityPlaceholders,
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

// ---- generated-artifact invariants (locks the real output) -------------------

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../../..');
const PT = path.join(REPO_ROOT, 'reports/product/product-truth.json');

test('generated product-truth.json reflects honest maturity + baseline (no regression)', { skip: !fs.existsSync(PT) }, () => {
  const d = JSON.parse(fs.readFileSync(PT, 'utf8'));
  const byMaturity = d.summary.byMaturity || {};
  // The static generator must never claim runtime-proven completeness.
  assert.equal(byMaturity.REAL_PROVEN || 0, 0, 'static scan must not emit REAL_PROVEN');
  assert.equal(d.summary.phase6.userFacingRealProven, 0);

  // Known genuine debt must remain visible (guards against silent re-hiding).
  const flagged = new Set(
    d.services.filter((s) => (s.gaps || []).some((g) => g.category === 'S' || g.category === 'F')).map((s) => s.id),
  );
  for (const id of ['mushe-wallet-service', 'community-service', 'experience-bff']) {
    assert.ok(flagged.has(id), `expected ${id} to be flagged`);
  }

  // No regression beyond the recorded baseline.
  const baseline = JSON.parse(fs.readFileSync(path.join(REPO_ROOT, 'reports/product/product-truth-baseline.json'), 'utf8'));
  assert.ok(d.summary.gapCounts.total <= baseline.gapBaseline, `gap total ${d.summary.gapCounts.total} exceeds baseline ${baseline.gapBaseline} — fix the gap, do not raise the baseline`);
});
