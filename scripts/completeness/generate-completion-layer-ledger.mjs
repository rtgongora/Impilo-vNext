#!/usr/bin/env node
/**
 * Completion Layer Ledger — reset all journeys to uncertified; track L0-L6 per entity.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const JOURNEY_JSON = path.join(REPO_ROOT, 'reports/product/core-transaction-journey-maps.json');
const OUT_JSON = path.join(REPO_ROOT, 'reports/product/completion-layer-ledger.json');
const OUT_MD = path.join(REPO_ROOT, 'docs/product/COMPLETION_LAYER_LEDGER.md');

const LAYERS = ['L0', 'L1', 'L2', 'L3', 'L4', 'L5', 'L6'];

function readJson(p) {
  try {
    return JSON.parse(fs.readFileSync(p, 'utf8'));
  } catch {
    return { journeys: [] };
  }
}

function main() {
  const journeyData = readJson(JOURNEY_JSON);
  const journeys = journeyData.journeys ?? [];

  const journeyRows = journeys.map((j) => ({
    entity: j.id,
    entityType: 'journey',
    name: j.journeyName ?? j.id,
    priorClassification: j.completionClassification ?? 'unknown',
    certification: 'uncertified',
    layers: Object.fromEntries(LAYERS.map((l) => [l, 'pending'])),
    blockers: ['Zero-based reset — no layer credit until adversarial verdict packet passes'],
    evidenceLinks: [],
  }));

  const payload = {
    generatedAt: new Date().toISOString(),
    policy: 'Zero-based layered completion — transaction-complete count reset to 0 until L6 certified',
    transactionCompleteCount: 0,
    journeyCount: journeyRows.length,
    certifiedJourneyCount: 0,
    journeys: journeyRows,
  };

  fs.mkdirSync(path.dirname(OUT_JSON), { recursive: true });
  fs.mkdirSync(path.dirname(OUT_MD), { recursive: true });
  fs.writeFileSync(OUT_JSON, JSON.stringify(payload, null, 2));

  const md = `# Completion Layer Ledger

> Generated: ${payload.generatedAt}
> Journeys: **${payload.journeyCount}** | Certified: **${payload.certifiedJourneyCount}** | transaction-complete: **${payload.transactionCompleteCount}**

## Policy

All journeys start **uncertified**. Prior \`transaction-complete\` labels carry **zero credit** until each layer passes adversarial review (L0→L6).

## Journey status

| Journey | Prior label | L0 | L1 | L2 | L3 | L4 | L5 | L6 |
|---------|-------------|----|----|----|----|----|----|-----|
${journeyRows
  .slice(0, 20)
  .map(
    (j) =>
      `| ${j.entity} | ${j.priorClassification} | pending | pending | pending | pending | pending | pending | pending |`
  )
  .join('\n')}

_Full list in [\`reports/product/completion-layer-ledger.json\`](../../reports/product/completion-layer-ledger.json)._

_Regenerate: \`npm run completion-ledger --prefix scripts/completeness\`_
`;
  fs.writeFileSync(OUT_MD, md);
  console.log(`Completion layer ledger: ${OUT_JSON} (${journeyRows.length} journeys, 0 certified)`);
}

main();
