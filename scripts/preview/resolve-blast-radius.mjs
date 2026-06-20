#!/usr/bin/env node
/**
 * Resolve blast radius from git changed files → affected services, change class, gates.
 *
 * Usage:
 *   node scripts/preview/resolve-blast-radius.mjs [--base REF] [--files path1,path2]
 *
 * Writes: reports/audits/blast-radius-<short-sha>.json
 */
import { execSync, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO = process.env.REPO_PATH || path.resolve(__dirname, '../..');

const TRUST_SERVICES = new Set([
  'tshepo-authz-service',
  'tshepo-service',
  'vito-service',
  'mvumo-service',
  'identity-assurance-service',
  'credential-verification-service',
  'security-hardening-service',
]);

const EXPANSION_THRESHOLD = parseInt(process.env.BLAST_RADIUS_EXPANSION_THRESHOLD || '10', 10);

function sh(cmd) {
  try {
    return execSync(cmd, { cwd: REPO, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim();
  } catch {
    return '';
  }
}

function parseArgs(argv) {
  const args = { base: '', files: [] };
  for (let i = 2; i < argv.length; i++) {
    if (argv[i] === '--base' && argv[i + 1]) {
      args.base = argv[++i];
    } else if (argv[i] === '--files' && argv[i + 1]) {
      args.files = argv[++i].split(',').map((f) => f.trim()).filter(Boolean);
    } else if (argv[i] === '--help' || argv[i] === '-h') {
      console.log('Usage: resolve-blast-radius.mjs [--base REF] [--files a,b,c]');
      process.exit(0);
    }
  }
  return args;
}

function resolveBaseRef(explicit) {
  if (explicit) {
    const ok = sh(`git rev-parse --verify '${explicit}^{commit}'`);
    if (!ok) throw new Error(`Invalid base ref: ${explicit}`);
    return explicit;
  }
  const script = `source "${REPO}/scripts/guard/_guard-common.sh"; resolve_base_ref`;
  const r = spawnSync('bash', ['-c', script], { cwd: REPO, encoding: 'utf8' });
  const ref = (r.stdout || '').trim();
  if (ref) return ref;
  return sh('git rev-parse HEAD~1') || sh('git rev-parse origin/main') || 'HEAD';
}

function changedFiles(base, explicitFiles) {
  if (explicitFiles.length) return explicitFiles;
  let files = sh(`git diff --name-only '${base}...HEAD'`);
  if (!files) files = sh(`git diff --name-only '${base}' HEAD`);
  return files ? files.split('\n').filter(Boolean) : [];
}

function loadYamlJson(filePath) {
  const escaped = filePath.replace(/'/g, "'\\''");
  const out = sh(`python3 -c "import yaml,json,sys; print(json.dumps(yaml.safe_load(open('${escaped}'))))"`);
  if (!out) return null;
  try {
    return JSON.parse(out);
  } catch {
    return null;
  }
}

function hasImageStrategy(serviceId, classifications) {
  const entry = classifications.find((e) => e.id === serviceId);
  if (!entry) {
    return serviceId === 'experience-bff' || serviceId === 'one-ui-shell';
  }
  const s = entry.image_strategy || '';
  if (s.startsWith('not-required')) return false;
  if (['shared-dockerfile-template', 'dockerfile', 'jib', 'buildpacks'].includes(s)) return true;
  if (serviceId === 'experience-bff' || serviceId === 'one-ui-shell') return true;
  return Boolean(entry.image_required);
}

function classifyPaths(files) {
  const areas = new Set();
  const classes = new Set();
  const directServices = new Set();
  const refusalReasons = [];
  const rationale = [];

  let docsOnly = files.length > 0;
  const flags = {
    docsOnly: false,
    hasHelm: false,
    hasTrust: false,
    hasContract: false,
    hasMigration: false,
    hasRegistry: false,
    hasMobile: false,
    hasFrontend: false,
    hasBff: false,
    hasBackend: false,
    hasLibs: false,
    hasGenerated: false,
  };

  for (const f of files) {
    if (!f.startsWith('docs/') && !f.endsWith('.md')) docsOnly = false;

    const svcMatch = f.match(/^services\/([^/]+)\//);
    if (svcMatch) {
      directServices.add(svcMatch[1]);
      flags.hasBackend = true;
      if (svcMatch[1] === 'experience-bff') flags.hasBff = true;
      if (TRUST_SERVICES.has(svcMatch[1])) flags.hasTrust = true;
    }
    if (f.startsWith('ui/one-ui-shell/')) {
      flags.hasFrontend = true;
      directServices.add('one-ui-shell');
    }
    if (f.startsWith('apps/mobile/')) flags.hasMobile = true;
    if (f.startsWith('contracts/')) flags.hasContract = true;
    if (f.startsWith('libs/') || f.startsWith('services/shared-core/')) flags.hasLibs = true;
    if (/\/db\/migration\/V[^/]+\.sql$/.test(f)) flags.hasMigration = true;
    if (
      f.startsWith('deploy/') ||
      f.startsWith('config/full-boot') ||
      f.includes('values-full-preview') ||
      f.includes('docker-compose')
    ) {
      flags.hasHelm = true;
    }
    if (f.startsWith('docs/registry/') || f === 'config/full-boot-service-classification.yml') {
      flags.hasRegistry = true;
    }
    if (f.includes('.generated.') || f.endsWith('.generated.yaml')) flags.hasGenerated = true;
    if (f.startsWith('infra/envoy')) flags.hasTrust = true;
  }

  flags.docsOnly = docsOnly && files.length > 0;

  if (files.length === 0) {
    classes.add('I');
    rationale.push('No changed files detected vs base ref');
    return { areas, classes, directServices, refusalReasons, rationale, flags };
  }

  if (flags.docsOnly) {
    classes.add('A');
    areas.add('docs');
    rationale.push('Only documentation files changed');
  }
  if (flags.hasRegistry) {
    classes.add('I');
    rationale.push('Registry or classification metadata changed');
  }
  if (flags.hasHelm) {
    classes.add('G');
    rationale.push('Helm/deploy/infrastructure configuration changed');
  }
  if (flags.hasTrust) {
    classes.add('H');
    rationale.push('Trust/auth plane paths changed');
  }
  if (flags.hasContract || flags.hasLibs) {
    classes.add('E');
    rationale.push('Shared library or contract paths changed');
  }
  if (flags.hasMigration) {
    classes.add('F');
    rationale.push('Database migration files changed');
  }
  if (flags.hasBff) {
    classes.add('D');
    areas.add('bff');
  }
  if (flags.hasFrontend && !flags.hasBff && !flags.hasBackend) {
    classes.add('B');
    areas.add('frontend');
  }
  if (flags.hasBackend && directServices.size === 1 && !flags.hasLibs && !flags.hasContract && !flags.hasHelm) {
    const sid = [...directServices][0];
    if (sid !== 'experience-bff' && sid !== 'one-ui-shell') {
      classes.add('C');
      areas.add('backend');
    }
  }
  if (classes.size === 0) {
    if (flags.hasBackend && flags.hasFrontend) classes.add('D');
    else if (flags.hasBackend) classes.add('C');
    else if (flags.hasFrontend) classes.add('B');
    else classes.add('I');
    rationale.push('Multi-area change — classified conservatively');
  }

  return { areas, classes, directServices, refusalReasons, rationale, flags };
}

function expandServices(directServices, registry, classifications, flags) {
  const expanded = new Set(directServices);
  const { byId } = registry;

  for (const sid of directServices) {
    const entry = byId.get(sid);
    if (entry) {
      for (const up of entry.consumes_from || []) {
        if (hasImageStrategy(up, classifications)) expanded.add(up);
      }
      for (const down of entry.exposes_to || []) {
        if (hasImageStrategy(down, classifications)) expanded.add(down);
      }
    }
    for (const [, svc] of byId) {
      if ((svc.consumes_from || []).includes(sid) && hasImageStrategy(svc.id, classifications)) {
        expanded.add(svc.id);
      }
    }
  }

  const backendChanged = [...directServices].some(
    (s) => s !== 'experience-bff' && s !== 'one-ui-shell',
  );
  if (backendChanged && hasImageStrategy('experience-bff', classifications)) {
    expanded.add('experience-bff');
  }

  if (flags.hasLibs && directServices.has('shared-core')) {
    for (const e of classifications) {
      if (e.deployment_lane === 'runtime_k8s_microservice' && hasImageStrategy(e.id, classifications)) {
        expanded.add(e.id);
      }
    }
  }

  return [...expanded].sort();
}

function buildPipelineOnly(classes, flags) {
  if (classes.has('A') && flags.docsOnly) return 'change-safety';
  const phases = new Set(['security', 'change-safety']);
  if (flags.hasFrontend || classes.has('B') || classes.has('D')) {
    phases.add('static');
    phases.add('frontend');
    phases.add('parity-web');
  }
  if (flags.hasBackend || flags.hasBff || classes.has('C') || classes.has('D') || classes.has('E')) {
    phases.add('backend');
  }
  if (flags.hasContract || classes.has('E')) phases.add('api-contracts');
  if (flags.hasMobile) phases.add('parity-mobile');
  return [...phases].sort().join(',');
}

function decideFullBoot(classes, flags, expandedServices, refusalReasons) {
  if (flags.hasRegistry || flags.hasHelm || flags.hasTrust) return true;
  if (classes.has('I') || classes.has('G') || classes.has('H')) return true;
  if (classes.has('F') && expandedServices.length > 1) return true;
  if (classes.has('E') && expandedServices.length > EXPANSION_THRESHOLD) {
    refusalReasons.push(
      `Contract/lib expansion affects ${expandedServices.length} services (threshold ${EXPANSION_THRESHOLD})`,
    );
    return true;
  }
  for (const sid of expandedServices) {
    if (TRUST_SERVICES.has(sid)) {
      refusalReasons.push(`Trust plane service in expanded set: ${sid}`);
      return true;
    }
  }
  return false;
}

function main() {
  const args = parseArgs(process.argv);
  const base = resolveBaseRef(args.base);
  const files = changedFiles(base, args.files);
  const head = sh('git rev-parse HEAD') || 'unknown';
  const shortSha = sh('git rev-parse --short HEAD') || 'unknown';

  const registryDoc = loadYamlJson(path.join(REPO, 'docs/registry/services-registry.yaml'));
  const services = registryDoc?.services || [];
  const registry = { services, byId: new Map(services.map((s) => [s.id, s])) };
  const classifications = loadYamlJson(path.join(REPO, 'config/full-boot-service-classification.yml'))
    ?.classifications || [];

  const { areas, classes, directServices, refusalReasons, rationale, flags } = classifyPaths(files);
  let expandedServices = expandServices(directServices, registry, classifications, flags);
  let imagesToBuild = expandedServices.filter((sid) => hasImageStrategy(sid, classifications));

  if (flags.docsOnly) {
    expandedServices = [];
    imagesToBuild = [];
  }

  const fullBootRequired = decideFullBoot(classes, flags, expandedServices, refusalReasons);
  let targetedAllowed = !fullBootRequired && (imagesToBuild.length > 0 || flags.docsOnly);

  if (fullBootRequired) {
    targetedAllowed = false;
    if (refusalReasons.length === 0) {
      refusalReasons.push(`Change class ${[...classes].sort().join('+')} requires full boot`);
    }
  }

  const changeClass = [...classes].sort()[0] || 'I';
  const doc = {
    generated_at: new Date().toISOString(),
    base_ref: base,
    head_commit: head,
    short_sha: shortSha,
    changed_file_count: files.length,
    changed_files: files.slice(0, 100),
    areas: [...areas],
    change_class: changeClass,
    change_classes: [...classes].sort(),
    full_boot_required: fullBootRequired,
    targeted_deploy_allowed: targetedAllowed,
    direct_services: [...directServices].sort(),
    expanded_services: expandedServices,
    images_to_build: imagesToBuild,
    pipeline_only: buildPipelineOnly(classes, flags),
    expansion_threshold: EXPANSION_THRESHOLD,
    refusal_reasons: refusalReasons,
    rationale,
  };

  const outDir = path.join(REPO, 'reports/audits');
  fs.mkdirSync(outDir, { recursive: true });
  const outPath = path.join(outDir, `blast-radius-${shortSha}.json`);
  fs.writeFileSync(outPath, JSON.stringify(doc, null, 2) + '\n');

  if (process.env.BLAST_RADIUS_JSON_ONLY === '1') {
    process.stdout.write(JSON.stringify(doc));
  } else {
    console.log(outPath);
  }

  process.exit(fullBootRequired && process.env.BLAST_RADIUS_STRICT === '1' ? 2 : 0);
}

main();
