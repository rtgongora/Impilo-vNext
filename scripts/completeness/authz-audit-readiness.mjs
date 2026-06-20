/**
 * Code-based authz / audit / tenant-scoping readiness for backend services.
 * Used by product-truth to classify Category N gaps from implementation signals,
 * not registry metadata alone.
 */
import fs from 'node:fs';
import path from 'node:path';

const TRUST_PLANE_SERVICES = new Set([
  'tshepo-service',
  'tshepo-authz-service',
  'tshepo-audit-service',
  'tshepo-consent-service',
  'tshepo-identity-service',
  'tshepo-keys-service',
  'tshepo-offline-service',
  'mvumo-service',
]);

const TRUST_SIGNALS = [
  'TrustContextFilter',
  'TrustHeaderFilter',
  'TrustLayerGuard',
  'TrustHeaderExtractor',
  'FilterRegistrationBean<TrustContextFilter>',
  'V11HeaderFilter',
  'RequestContextHolder',
  'CompanionHeaders.ACTOR_ID',
  'NdilaTshepoGuard',
  'NdilaTrustHeaders',
];

export function scanAuthzAuditReadiness(modulePath, serviceId) {
  if (!fs.existsSync(modulePath)) {
    return emptyReadiness('missing-module');
  }

  const mainJava = path.join(modulePath, 'src/main/java');
  const migrationDir = path.join(modulePath, 'src/main/resources/db/migration');
  let javaText = '';
  let hasSecurityBaseline = false;
  let hasSecurityConfig = false;
  let configPackage = null;

  if (fs.existsSync(mainJava)) {
    walk(mainJava, (file) => {
      if (!file.endsWith('.java')) return;
      const base = path.basename(file);
      const text = read(file);
      javaText += text + '\n';
      if (base === 'SecurityBaselineConfig.java') hasSecurityBaseline = true;
      if (base === 'SecurityConfig.java') {
        hasSecurityConfig = true;
        const pkg = text.match(/^package\s+([\w.]+);/m);
        if (pkg) configPackage = pkg[1];
      }
    });
  }

  const trustContext = TRUST_SIGNALS.some((s) => javaText.includes(s));
  const tenantScoping =
    javaText.includes('TrustContextHolder') ||
    /\btenantId\b/.test(javaText) ||
    javaText.includes('X-Tenant-ID') ||
    javaText.includes('TENANT_ID');
  const adminAudit =
    javaText.includes('AdminAuditEmitter') || javaText.includes('AdminAuditOutboxSink');
  const rateLimit =
    javaText.includes('RateLimitGuard') || javaText.includes('RateLimitFilter');
  const outbox = detectOutbox(migrationDir, javaText);

  const isTrustPlane = TRUST_PLANE_SERVICES.has(serviceId);
  const checks = {
    trustContext: isTrustPlane ? true : trustContext,
    tenantScoping: tenantScoping || isTrustPlane,
    securityBaseline: hasSecurityBaseline,
    adminAudit: adminAudit || hasSecurityBaseline,
    rateLimit: rateLimit || hasSecurityBaseline,
    outbox: outbox.compatible || outbox.any,
    outboxCompatible: outbox.compatible,
    outboxSchema: outbox.schema,
    securityConfig: hasSecurityConfig,
    configPackage,
  };

  let status = 'absent';
  if (isTrustPlane) {
    status = hasSecurityConfig && (hasSecurityBaseline || serviceId === 'tshepo-service') ? 'wired' : 'partial';
  } else if (
    checks.trustContext &&
    checks.tenantScoping &&
    checks.outbox &&
    checks.securityBaseline
  ) {
    status = 'wired';
  } else if (checks.trustContext || checks.tenantScoping || checks.securityBaseline || checks.outbox) {
    status = 'partial';
  }

  const missing = [];
  if (!isTrustPlane) {
    if (!checks.trustContext) missing.push('trust-context-filter');
    if (!checks.tenantScoping) missing.push('tenant-scoping');
    if (!checks.outbox) missing.push('event-outbox');
    if (!checks.securityBaseline) missing.push('security-baseline-config');
  } else if (!checks.securityBaseline && serviceId.startsWith('tshepo-') && serviceId !== 'tshepo-service') {
    missing.push('security-baseline-config');
  }

  return {
    status,
    checks,
    missing,
    isTrustPlane,
  };
}

function detectOutbox(migrationDir, javaText) {
  let schema = null;
  let compatible = false;
  let any = javaText.includes('event_outbox') || javaText.includes('EventOutbox') || javaText.includes('OutboxEvent');

  if (fs.existsSync(migrationDir)) {
    const sql = walkFiles(migrationDir)
      .filter((f) => f.endsWith('.sql'))
      .map(read)
      .join('\n');
    any = any || /event_outbox/i.test(sql);

    const schemaMatch = sql.match(/CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-zA-Z_][\w]*)\.event_outbox/i);
    if (schemaMatch) {
      schema = schemaMatch[1];
      compatible = true;
    }

    const publicMatch = sql.match(/CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?event_outbox/i);
    if (!schema && publicMatch) {
      schema = 'public';
      compatible = true;
    }
  }

  return { schema, compatible, any };
}

function emptyReadiness(reason) {
  return {
    status: 'absent',
    checks: {},
    missing: [reason],
    isTrustPlane: false,
  };
}

function read(p) {
  try {
    return fs.readFileSync(p, 'utf8');
  } catch {
    return '';
  }
}

function walk(dir, fn) {
  if (!fs.existsSync(dir)) return;
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) walk(p, fn);
    else fn(p);
  }
}

function walkFiles(dir) {
  const out = [];
  walk(dir, (p) => out.push(p));
  return out;
}

/** Human label for SecurityBaselineConfig emission. */
export function baselineServiceKey(serviceId, schema) {
  if (schema && schema !== 'public') return schema.replace(/_/g, '-');
  return serviceId.replace(/-service$/, '').replace(/-/g, '');
}

export function baselineEnvPrefix(serviceId, schema) {
  const key = baselineServiceKey(serviceId, schema).toUpperCase().replace(/-/g, '_');
  return `IMPILO_${key}_`;
}
