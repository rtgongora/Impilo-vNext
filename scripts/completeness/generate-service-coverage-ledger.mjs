#!/usr/bin/env node
/**
 * Service Coverage Ledger — one row per platform component (~140).
 * Tracks surface_status (no floating/stubbed pages) and journey mapping.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const REGISTRY_PATH = path.join(REPO_ROOT, 'docs/registry/services-registry.yaml');
const RECOVERY_PATH = path.join(REPO_ROOT, 'reports/product/product-truth-recovery-map.json');
const JOURNEY_JSON = path.join(REPO_ROOT, 'reports/product/core-transaction-journey-maps.json');
const OUT_JSON = path.join(REPO_ROOT, 'reports/product/service-coverage-ledger.json');
const OUT_MD = path.join(REPO_ROOT, 'docs/product/SERVICE_COVERAGE_LEDGER.md');

const BFF_ONLY = new Set(['experience-bff']);
const ADAPTER_SUFFIX = ['-adapter', '-agent', 'integration-hub', 'fhir-gateway-service'];
const UI_WORKSPACES = fs.existsSync(path.join(REPO_ROOT, 'ui'))
  ? fs.readdirSync(path.join(REPO_ROOT, 'ui')).filter((d) =>
      fs.existsSync(path.join(REPO_ROOT, 'ui', d, 'package.json'))
    )
  : [];

function readJson(p, fallback = {}) {
  try {
    return JSON.parse(fs.readFileSync(p, 'utf8'));
  } catch {
    return fallback;
  }
}

function classifyService(id, sovereign) {
  if (BFF_ONLY.has(id)) return 'C';
  if (ADAPTER_SUFFIX.some((s) => id.endsWith(s) || id === s)) return 'D';
  if (sovereign) return 'A';
  return 'B';
}

function loadJourneyServiceMap() {
  const j = readJson(JOURNEY_JSON, { journeys: [], serviceToJourneys: {} });
  if (j.serviceToJourneys) return j.serviceToJourneys;
  const map = {};
  for (const row of j.journeys ?? []) {
    for (const svc of row.backendServices ?? []) {
      if (!map[svc]) map[svc] = [];
      map[svc].push(row.id);
    }
  }
  return map;
}

function main() {
  const registry = yaml.load(fs.readFileSync(REGISTRY_PATH, 'utf8'));
  const recovery = readJson(RECOVERY_PATH);
  const journeyMap = loadJourneyServiceMap();
  const unregisteredPages = (recovery.entries ?? []).filter(
    (e) => e.componentType === 'frontend-page-unregistered'
  ).length;

  const rows = [];

  for (const svc of registry.services ?? []) {
    const journeyIds = journeyMap[svc.maven_module] ?? journeyMap[svc.id] ?? [];
    let surfaceStatus = 'unsurfaced';
    if (svc.frontend_wiring_status === 'wired') surfaceStatus = 'surfaced-live';
    else if (svc.frontend_wiring_status === 'unknown-or-partial') surfaceStatus = 'unsurfaced';
    if (journeyIds.length === 0 && svc.implementation_status !== 'not-applicable') {
      surfaceStatus = surfaceStatus === 'surfaced-live' ? 'floating' : 'unsurfaced';
    }
    rows.push({
      entity: svc.id,
      mavenModule: svc.maven_module,
      serviceClass: classifyService(svc.id, svc.sovereign === true),
      surfaceStatus,
      journeyIds,
      webRoutes: [],
      mobileScreens: [],
      bffEndpoints: [],
      internalOnlyReason: journeyIds.length === 0 ? null : null,
      layerStatus: { L0: 'pending', L1: 'pending', L2: 'pending', L3: 'pending', L4: 'pending', L5: 'pending', L6: 'pending' },
    });
  }

  for (const lib of registry.libraries ?? []) {
    rows.push({
      entity: lib.id,
      mavenModule: lib.maven_module,
      serviceClass: 'G',
      surfaceStatus: 'internal-only',
      journeyIds: [],
      internalOnlyReason: 'shared library — no runtime surface',
      layerStatus: { L0: 'pending' },
    });
  }

  for (const ws of UI_WORKSPACES) {
    rows.push({
      entity: ws,
      mavenModule: null,
      serviceClass: ws === 'one-ui-shell' ? 'E' : 'E',
      surfaceStatus: ws === 'one-ui-shell' ? 'surfaced-live' : 'unsurfaced',
      journeyIds: [],
      internalOnlyReason: ws === 'one-ui-shell' ? null : 'secondary UI workspace — consolidate into one-ui-shell or document',
      layerStatus: { L0: 'pending', L3: 'pending' },
    });
  }

  rows.push({
    entity: 'citizen-app',
    serviceClass: 'F',
    surfaceStatus: 'surfaced-live',
    journeyIds: [],
    layerStatus: { L0: 'pending', L4: 'pending' },
  });
  rows.push({
    entity: 'provider-app',
    serviceClass: 'F',
    surfaceStatus: 'surfaced-live',
    journeyIds: [],
    layerStatus: { L0: 'pending', L4: 'pending' },
  });

  const counts = {
    total: rows.length,
    surfacedLive: rows.filter((r) => r.surfaceStatus === 'surfaced-live').length,
    floating: rows.filter((r) => r.surfaceStatus === 'floating').length,
    stubbed: rows.filter((r) => r.surfaceStatus === 'stubbed').length,
    unsurfaced: rows.filter((r) => r.surfaceStatus === 'unsurfaced').length,
    internalOnly: rows.filter((r) => r.surfaceStatus === 'internal-only').length,
    unmappedServices: rows.filter((r) => r.serviceClass === 'B' && (r.journeyIds?.length ?? 0) === 0).length,
    unregisteredPages,
  };

  const payload = { generatedAt: new Date().toISOString(), counts, rows };
  fs.mkdirSync(path.dirname(OUT_JSON), { recursive: true });
  fs.mkdirSync(path.dirname(OUT_MD), { recursive: true });
  fs.writeFileSync(OUT_JSON, JSON.stringify(payload, null, 2));

  const md = `# Service Coverage Ledger

> Generated: ${payload.generatedAt}
> Components: **${counts.total}**

| Metric | Count |
|--------|------:|
| surfaced-live | ${counts.surfacedLive} |
| floating | ${counts.floating} |
| stubbed | ${counts.stubbed} |
| unsurfaced | ${counts.unsurfaced} |
| internal-only | ${counts.internalOnly} |
| domain services without journey | ${counts.unmappedServices} |
| unregistered frontend pages | ${counts.unregisteredPages} |

**Target:** floating=0, stubbed=0, unmappedUserFacing=0.

Machine-readable: [\`reports/product/service-coverage-ledger.json\`](../../reports/product/service-coverage-ledger.json)

_Regenerate: \`npm run coverage-ledger --prefix scripts/completeness\`_
`;
  fs.writeFileSync(OUT_MD, md);
  console.log(`Service coverage ledger: ${OUT_JSON} (${counts.total} rows)`);
}

main();
