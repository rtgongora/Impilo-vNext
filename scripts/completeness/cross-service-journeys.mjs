/**
 * Cross-service cohesion journey definitions (Wave 3).
 * Each journey spans multiple sovereign services via the Experience BFF.
 */

import fs from 'node:fs';
import path from 'node:path';

/** Product statuses considered sufficient for journey cohesion (adapters may be internal-only). */
export const STRONG_PRODUCT_STATUSES = new Set([
  'real',
  'mostly-real',
  'internal-only',
  'deprecated',
]);

export const CROSS_SERVICE_JOURNEYS = [
  {
    id: 'identity-login-context',
    services: ['tshepo-authz-service', 'tshepo-identity-service', 'vito-service', 'experience-bff'],
    goldenThreadTests: ['identity-login-context-golden-thread.test.ts'],
    uiRoutes: ['/auth/login/provider-id', '/registry'],
    bffPaths: ['/internal/v1/auth', '/internal/v1/identity'],
  },
  {
    id: 'provider-workforce-context',
    services: ['varapi-service', 'vashandi-workforce-service', 'workforce-governance-service'],
    goldenThreadTests: ['provider-workforce-context-golden-thread.test.ts'],
    uiRoutes: ['/work/vashandi/workforce', '/registry/providers'],
    bffPaths: ['/internal/v1/vashandi', '/internal/v1/registry'],
  },
  {
    id: 'facility-workplace-context',
    services: ['tuso-service', 'indawo-service', 'experience-bff'],
    goldenThreadTests: ['facility-workplace-context-golden-thread.test.ts'],
    uiRoutes: ['/facility', '/home'],
    bffPaths: ['/internal/v1/facilities'],
  },
  {
    id: 'registry-to-shr',
    services: ['vito-service', 'butano-service', 'pct-service'],
    goldenThreadTests: ['registry-to-shr-golden-thread.test.ts'],
    uiRoutes: ['/registry', '/ehr'],
    bffPaths: ['/internal/v1/identity', '/internal/v1/fhir', '/internal/v1/patients'],
  },
  {
    id: 'orders-inventory-labs-imaging',
    services: ['oros-service', 'inventory-service', 'pacs-adapter-service', 'pharmacy-service'],
    goldenThreadTests: ['orders-inventory-labs-imaging-golden-thread.test.ts', 'imaging-order-result-golden-thread.test.ts'],
    uiRoutes: ['/lab', '/pharmacy', '/imaging'],
    bffPaths: ['/internal/v1/lab', '/internal/v1/pharmacy', '/internal/v1/imaging'],
  },
  {
    id: 'telemedicine-to-pct',
    services: ['rtc-gateway-service', 'pct-service', 'live-service'],
    goldenThreadTests: ['telemedicine-encounter-golden-thread.test.ts'],
    uiRoutes: ['/telemedicine/session'],
    bffPaths: ['/internal/v1/teleconsult', '/internal/v1/encounters'],
  },
  {
    id: 'learning-to-provider-registry',
    services: ['learning-service', 'varapi-service'],
    goldenThreadTests: ['fundo-learning-golden-thread.test.ts', 'provider-registry-onboarding-golden-thread.test.ts'],
    uiRoutes: ['/learning', '/registry/providers'],
    bffPaths: ['/internal/v1/learning', '/internal/v1/registry'],
  },
  {
    id: 'costing-billing-payments',
    services: ['costing-engine-service', 'mushex-service', 'coverage-service'],
    goldenThreadTests: ['payment-billing-claim-golden-thread.test.ts'],
    uiRoutes: ['/finance/payer-ops', '/coverage'],
    bffPaths: ['/internal/v1/finance', '/internal/v1/coverage'],
  },
  {
    id: 'facility-licensing-workspace',
    services: ['tuso-service', 'indawo-service', 'credential-verification-service'],
    goldenThreadTests: ['facility-context-selection-golden-thread.test.ts', 'credential-verification-golden-thread.test.ts'],
    uiRoutes: ['/facility', '/verify/credential'],
    bffPaths: ['/internal/v1/facilities', '/internal/v1/credential'],
  },
  {
    id: 'public-health-surveillance',
    services: ['surveillance-service', 'campaigns-service', 'ndila-service'],
    goldenThreadTests: ['surveillance-outbreak-golden-thread.test.ts'],
    uiRoutes: ['/public-health'],
    bffPaths: ['/internal/v1/public-health'],
  },
  {
    id: 'blood-services-chain',
    services: ['oros-service', 'inventory-service', 'pct-service'],
    goldenThreadTests: ['blood-services-chain-golden-thread.test.ts', 'madi-golden-thread.test.ts'],
    uiRoutes: ['/madi/orders', '/madi/transfusion'],
    bffPaths: ['/internal/v1/madi'],
  },
  {
    id: 'maps-geospatial-logistics',
    services: ['ndila-service', 'nhume-service', 'dispatch-service'],
    goldenThreadTests: ['maps-geospatial-logistics-golden-thread.test.ts', 'dispatch-delivery-golden-thread.test.ts'],
    uiRoutes: ['/nhume/map', '/operations/dispatch'],
    bffPaths: ['/internal/v1/nhume', '/internal/v1/dispatch'],
  },
  {
    id: 'marketplace-procurement-claims',
    services: ['msika-service', 'msika-flow-service', 'procurement-service', 'mushex-service'],
    goldenThreadTests: ['marketplace-procurement-claims-golden-thread.test.ts', 'marketplace-order-golden-thread.test.ts'],
    uiRoutes: ['/marketplace', '/marketplace/cart', '/finance/payer-ops'],
    bffPaths: ['/internal/v1/marketplace', '/internal/v1/commerce', '/internal/v1/finance'],
  },
  {
    id: 'admin-governance-onboarding',
    services: ['tshepo-authz-service', 'data-access-governance-service', 'experience-bff'],
    goldenThreadTests: ['admin-governance-onboarding-golden-thread.test.ts'],
    uiRoutes: ['/work/administration-governance', '/work/administration-governance/access-requests'],
    bffPaths: ['/internal/v1/admin/trust', '/internal/v1/admin-governance'],
  },
];

/**
 * @param {object} data - product-truth dataset
 * @param {string} repoRoot
 */
export function evaluateCrossServiceCohesion(data, repoRoot) {
  const testDir = path.join(repoRoot, 'ui/one-ui-shell/src/lib/__tests__');
  const byId = new Map((data.services || []).map((s) => [s.id, s]));

  return CROSS_SERVICE_JOURNEYS.map((journey) => {
    const serviceStatuses = journey.services.map((id) => byId.get(id)?.productStatus ?? 'missing');
    const weak = journey.services.filter((id) => {
      const status = byId.get(id)?.productStatus ?? 'missing';
      return !STRONG_PRODUCT_STATUSES.has(status);
    });
    const testsFound = journey.goldenThreadTests.filter((file) => fs.existsSync(path.join(testDir, file)));
    const testsMissing = journey.goldenThreadTests.filter((file) => !fs.existsSync(path.join(testDir, file)));

    let status = 'pass';
    const notes = [];

    if (weak.length > 0) {
      status = 'needs-work';
      notes.push(`weak services: ${weak.join(', ')}`);
    }
    if (testsFound.length === 0) {
      status = status === 'pass' ? 'missing-test' : status;
      notes.push(`missing tests: ${testsMissing.join(', ')}`);
    } else if (testsMissing.length > 0) {
      notes.push(`partial tests (${testsFound.length}/${journey.goldenThreadTests.length})`);
    }
    if (notes.length === 0) {
      notes.push(`services: ${serviceStatuses.join(' / ')}; tests: ${testsFound.length}`);
    }

    return {
      ...journey,
      status,
      serviceStatuses,
      testsFound,
      testsMissing,
      notes: notes.join('; '),
    };
  });
}

export function summarizeCohesion(evaluations) {
  const byStatus = {};
  for (const row of evaluations) {
    byStatus[row.status] = (byStatus[row.status] || 0) + 1;
  }
  return {
    total: evaluations.length,
    byStatus,
    pass: byStatus.pass || 0,
    needsWork: byStatus['needs-work'] || 0,
    missingTest: byStatus['missing-test'] || 0,
  };
}
