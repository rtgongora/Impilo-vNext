/**
 * Product Truth gap classification (categories A–R) and severity scoring.
 */

export const GAP_CATEGORIES = {
  A: 'Backend exists, UI missing',
  B: 'Backend exists, BFF missing',
  C: 'Backend exists, contract missing/stale',
  D: 'Backend exists, frontend only partially wired',
  E: 'UI exists, backend missing',
  F: 'UI exists, uses mock/stub/fixture data',
  G: 'UI exists, form submits but does not persist',
  H: 'UI exists, button/card is dead or decorative',
  I: 'API exists, database persistence missing',
  J: 'Database exists, API missing',
  K: 'Contract exists, implementation missing',
  L: 'BFF exists, downstream service not wired',
  M: 'Mobile parity missing',
  N: 'Auth/policy/tenant scoping missing',
  O: 'Tests missing',
  P: 'Workflow incomplete across services',
  Q: 'Service is internal-only and needs documentation instead of UI',
  R: 'Duplicate/overlapping capability requiring consolidation',
};

/** Services expected to be internal-only (no actor-facing UI). */
export const INTERNAL_ONLY_PATTERNS = [
  /^schema-registry-service$/,
  /^observability-service$/,
  /^security-hardening-service$/,
  /^audit-ledger-service$/,
  /^iot-ingestion-service$/,
  /^analytics-pipeline-service$/,
  /^data-pipeline-service$/,
  /^data-warehouse-service$/,
  /^data-ingestion-service$/,
  /^national-data-repository-service$/,
  /^ndr-service$/,
  /^llm-orchestration-service$/,
  /^ai-model-registry-service$/,
  /^card-print-agent$/,
  /^connector-fhir-adapter$/,
  /^fhir-gateway-service$/,
  /^integration-hub$/,
  /^jobs-service$/,
  /^offline-edge-service$/,
  /^offline-sync-service$/,
  /^landela-adapter-service$/,
  /^inventory-elmis-adapter$/,
  /^pharmacy-elmis-adapter$/,
  /^pacs-adapter-service$/,
  /^wellness-service$/, // deprecated, migrated to simba
];

/** User-facing services that require mobile parity. */
export const MOBILE_PARITY_REQUIRED = new Set([
  'vito-service',
  'pct-service',
  'oros-service',
  'pharmacy-service',
  'community-service',
  'learning-service',
  'simba-service',
  'booking-service',
  'coverage-service',
  'live-service',
  'notification-service',
  'surveillance-service',
  'campaigns-service',
]);

const SEVERITY_WEIGHT = { blocker: 4, high: 3, medium: 2, low: 1 };

export function isInternalOnly(serviceId) {
  if (INTERNAL_ONLY_PATTERNS.some((re) => re.test(serviceId))) return true;
  return false;
}

/** Actor-facing flows that require a mobile surface (see MOBILE_PARITY_MATRIX). */
export function mobileParityRequired(serviceId) {
  return MOBILE_PARITY_REQUIRED.has(serviceId);
}

export function triState(count, thinThreshold = 1) {
  if (!count || count === 0) return 'absent';
  if (count <= thinThreshold) return 'thin';
  return 'real';
}

export function overallProductStatus(dimensions, svc) {
  const vals = Object.values(dimensions).filter((v) => v !== 'n/a');
  if (svc?.production_status === 'deprecated-module-retired-runtime') return 'deprecated';
  if (isInternalOnly(svc?.id)) {
    const hasBackend = dimensions.controllers !== 'absent' || dimensions.contract !== 'absent';
    return hasBackend ? 'internal-only' : 'incomplete-internal';
  }
  const absent = vals.filter((v) => v === 'absent').length;
  const thin = vals.filter((v) => v === 'thin').length;
  const real = vals.filter((v) => v === 'real').length;
  if (real >= 6 && absent === 0) return 'real';
  if (real >= 4 && thin <= 2) return 'mostly-real';
  if (absent >= 4) return 'thin-or-stubbed';
  if (absent >= 2) return 'partial';
  return 'unknown';
}

export function authzDimFromReadiness(readiness) {
  if (!readiness) return 'absent';
  if (readiness.status === 'wired') return 'real';
  if (readiness.status === 'partial') return 'thin';
  return 'absent';
}

/**
 * @param {object} svc - service scan record
 * @returns {Array<{category: string, severity: string, description: string, impactScore: number}>}
 */
export function classifyServiceGaps(svc) {
  const gaps = [];
  const d = svc.dimensions;
  const id = svc.id;

  if (isInternalOnly(id)) {
    if (!svc.internalOnlyDocumented) {
      gaps.push({
        category: 'Q',
        severity: 'medium',
        description: `${id}: internal-only service lacks documented rationale`,
        impactScore: 2,
      });
    }
    if (d.controllers === 'absent' && d.contract === 'absent') {
      gaps.push({
        category: 'K',
        severity: 'high',
        description: `${id}: internal service has neither controllers nor contract`,
        impactScore: 3,
      });
    }
    return gaps;
  }

  const hasBackend =
    d.controllers !== 'absent' || d.serviceLayer !== 'absent' || d.database !== 'absent';
  const hasUi = d.frontendUi !== 'absent';
  const hasBff = d.bffWiring !== 'absent';
  const hasContract = d.contract !== 'absent';

  if (hasBackend && !hasUi && svc.frontendExpected !== false) {
    gaps.push({
      category: 'A',
      severity: 'high',
      description: `${id}: backend capability with no frontend surface`,
      impactScore: 3,
    });
  }
  if (hasBackend && !hasBff && id !== 'experience-bff') {
    gaps.push({
      category: 'B',
      severity: d.controllers === 'real' ? 'high' : 'medium',
      description: `${id}: backend not wired through Experience BFF`,
      impactScore: d.controllers === 'real' ? 3 : 2,
    });
  }
  if (hasBackend && !hasContract) {
    gaps.push({
      category: 'C',
      severity: 'medium',
      description: `${id}: no matched OpenAPI contract`,
      impactScore: 2,
    });
  }
  if (hasBackend && hasUi && (d.frontendUi === 'thin' || d.bffWiring === 'thin')) {
    gaps.push({
      category: 'D',
      severity: 'medium',
      description: `${id}: partial frontend/BFF wiring`,
      impactScore: 2,
    });
  }
  if (hasUi && !hasBackend && id !== 'experience-bff') {
    gaps.push({
      category: 'E',
      severity: 'blocker',
      description: `${id}: UI surface without detected backend`,
      impactScore: 4,
    });
  }
  if (svc.mockStubHits?.length > 0) {
    gaps.push({
      category: 'F',
      severity: 'high',
      description: `${id}: mock/stub/fixture patterns detected (${svc.mockStubHits.length} hits)`,
      impactScore: 3,
    });
  }
  if (d.controllers !== 'absent' && d.database === 'absent' && svc.expectsPersistence !== false) {
    gaps.push({
      category: 'I',
      severity: 'medium',
      description: `${id}: API routes without database migrations`,
      impactScore: 2,
    });
  }
  if (d.database !== 'absent' && d.controllers === 'absent') {
    gaps.push({
      category: 'J',
      severity: 'high',
      description: `${id}: database schema without REST controllers`,
      impactScore: 3,
    });
  }
  if (hasContract && d.controllers === 'absent') {
    gaps.push({
      category: 'K',
      severity: 'high',
      description: `${id}: contract exists but no controller implementation`,
      impactScore: 3,
    });
  }
  if (d.bffWiring === 'thin' && svc.bffOrphanRoutes > 0) {
    gaps.push({
      category: 'L',
      severity: 'medium',
      description: `${id}: BFF routes may not reach downstream service`,
      impactScore: 2,
    });
  }
  if (MOBILE_PARITY_REQUIRED.has(id) && d.mobileUi === 'absent') {
    gaps.push({
      category: 'M',
      severity: 'medium',
      description: `${id}: user-facing service missing mobile surface`,
      impactScore: 2,
    });
  }
  if (hasBackend && (d.authzAudit === 'absent' || d.authzAudit === 'thin')) {
    const missing = svc.authzReadiness?.missing?.filter((m) => m !== 'missing-module') ?? [];
    const detail = missing.length ? missing.join(', ') : d.authzAudit;
    gaps.push({
      category: 'N',
      severity: d.controllers === 'real' ? 'medium' : 'low',
      description: `${id}: auth/policy/audit gaps (${detail})`,
      impactScore: d.controllers === 'real' ? 2 : 1,
    });
  }
  if (d.tests === 'absent' && hasBackend) {
    gaps.push({
      category: 'O',
      severity: 'medium',
      description: `${id}: no automated tests detected`,
      impactScore: 2,
    });
  }
  if (svc.stubRouteCount > 0) {
    gaps.push({
      category: 'P',
      severity: 'high',
      description: `${id}: ${svc.stubRouteCount} stub/TODO controller routes`,
      impactScore: 3,
    });
  }

  return gaps;
}

export function classifySurfaceGaps(surface) {
  const gaps = [];
  if (surface.allowlistedShell) return gaps;
  const cosmeticJsonOnly =
    surface.mockStubHits?.length > 0 &&
    surface.mockStubHits.every((h) => h.pattern === 'json-stringify-render' || h.pattern === 'json-debug-pre') &&
    ((surface.bffPaths?.length ?? 0) > 0 ||
      (surface.gatewayPaths?.length ?? 0) > 0 ||
      (surface.backingSignals?.length ?? 0) > 0) &&
    surface.readsRealData;
  if (surface.mockStubHits?.length > 0 && !cosmeticJsonOnly) {
    gaps.push({ category: 'F', severity: 'high', description: `${surface.path}: mock/stub data`, impactScore: 3 });
  }
  const hasBacking =
    (surface.bffPaths?.length ?? 0) > 0 ||
    (surface.gatewayPaths?.length ?? 0) > 0 ||
    (surface.backingSignals?.length ?? 0) > 0;
  if (!hasBacking && surface.pageFile) {
    gaps.push({ category: 'E', severity: 'high', description: `${surface.path}: no BFF/API backing detected`, impactScore: 4 });
  }
  if (surface.hasMutation && !surface.hasPersistHint && !surface.readsRealData) {
    gaps.push({ category: 'G', severity: 'medium', description: `${surface.path}: form/mutation without persist hint`, impactScore: 2 });
  }
  if (surface.deadActionHints?.length > 0) {
    gaps.push({ category: 'H', severity: 'medium', description: `${surface.path}: possible dead actions`, impactScore: 2 });
  }
  if (surface.hardcodedDataHints?.length > 0) {
    gaps.push({ category: 'F', severity: 'medium', description: `${surface.path}: hardcoded dashboard/data`, impactScore: 2 });
  }
  return gaps;
}

export function sortGapsByPriority(gaps) {
  return [...gaps].sort((a, b) => {
    const sw = (SEVERITY_WEIGHT[b.severity] || 0) - (SEVERITY_WEIGHT[a.severity] || 0);
    if (sw !== 0) return sw;
    return (b.impactScore || 0) - (a.impactScore || 0);
  });
}

export function aggregateGapCounts(gaps) {
  const byCategory = {};
  const bySeverity = {};
  for (const g of gaps) {
    byCategory[g.category] = (byCategory[g.category] || 0) + 1;
    bySeverity[g.severity] = (bySeverity[g.severity] || 0) + 1;
  }
  return { byCategory, bySeverity, total: gaps.length };
}
