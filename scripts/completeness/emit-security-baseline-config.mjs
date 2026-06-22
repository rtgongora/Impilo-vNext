#!/usr/bin/env node
/**
 * Emit SecurityBaselineConfig.java for services missing Wave 14 baseline.
 * Uses full admin-audit wiring when {schema}.event_outbox exists; otherwise rate-limit-only.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { scanAuthzAuditReadiness, baselineEnvPrefix, baselineServiceKey } from './authz-audit-readiness.mjs';
import { loadRegistryServices } from './registry-services.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const SERVICES_DIR = path.join(REPO_ROOT, 'services');

function main() {
  const only = process.argv.slice(2).filter((a) => !a.startsWith('--'));
  const dryRun = process.argv.includes('--dry-run');
  const services = loadRegistryServices();
  let created = 0;
  let skipped = 0;

  for (const svc of services) {
    if (only.length && !only.includes(svc.id)) continue;
    const modulePath = path.join(SERVICES_DIR, svc.maven_module);
    const readiness = scanAuthzAuditReadiness(modulePath, svc.id);
    if (readiness.checks.securityBaseline) {
      skipped++;
      continue;
    }
    const pkg = readiness.checks.configPackage ?? resolveConfigPackage(modulePath);
    if (!pkg || !readiness.checks.outbox) {
      skipped++;
      continue;
    }

    const configDir = path.join(modulePath, 'src/main/java', ...pkg.split('.'));
    const outFile = path.join(configDir, 'SecurityBaselineConfig.java');
    if (fs.existsSync(outFile)) {
      skipped++;
      continue;
    }

    const schema = readiness.checks.outboxSchema;
    const serviceKey = baselineServiceKey(svc.id, schema);
    const envPrefix = baselineEnvPrefix(svc.id, schema);
    // The full template injects a DataSource into AdminAuditEmitter. Only emit it when the
    // module actually has a JDBC datasource on the classpath; otherwise the bean fails to
    // start ("required a bean of type 'javax.sql.DataSource'"). Composition/BFF layers carry
    // an event_outbox migration aspirationally but have no datasource, so they must stay
    // rate-only — admin audit is owned by the domain service of record.
    const content = readiness.checks.outboxCompatible && moduleHasDataSource(modulePath)
      ? fullTemplate(pkg, serviceKey, schema ?? 'public', envPrefix)
      : rateOnlyTemplate(pkg, envPrefix);

    if (dryRun) {
      console.log(`would create ${path.relative(REPO_ROOT, outFile)} (${readiness.checks.outboxCompatible ? 'full' : 'rate-only'})`);
    } else {
      fs.mkdirSync(configDir, { recursive: true });
      fs.writeFileSync(outFile, content, 'utf8');
      console.log(`created ${path.relative(REPO_ROOT, outFile)} (${readiness.checks.outboxCompatible ? 'full' : 'rate-only'})`);
    }
    created++;
  }

  console.log(`emit-security-baseline-config: created=${created} skipped=${skipped}${dryRun ? ' (dry-run)' : ''}`);
}

/**
 * True when the module declares a JDBC datasource on the classpath (JPA/JDBC starter,
 * a JDBC driver, or Flyway). Without one, Spring autoconfigures no DataSource bean and a
 * DataSource-backed AdminAuditEmitter would crash on startup.
 */
function moduleHasDataSource(modulePath) {
  const pom = path.join(modulePath, 'pom.xml');
  if (!fs.existsSync(pom)) return false;
  const text = fs.readFileSync(pom, 'utf8');
  return (
    /spring-boot-starter-data-jpa/.test(text) ||
    /spring-boot-starter-jdbc/.test(text) ||
    /<artifactId>postgresql<\/artifactId>/.test(text) ||
    /<artifactId>flyway-core<\/artifactId>/.test(text)
  );
}

function resolveConfigPackage(modulePath) {
  const mainJava = path.join(modulePath, 'src/main/java');
  if (!fs.existsSync(mainJava)) return null;
  let appPkg = null;
  walk(mainJava, (file) => {
    if (!file.endsWith('Application.java')) return;
    const text = fs.readFileSync(file, 'utf8');
    const m = text.match(/^package\s+([\w.]+);/m);
    if (m) appPkg = `${m[1]}.config`;
  });
  return appPkg;
}

function walk(dir, fn) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) walk(p, fn);
    else fn(p);
  }
}

function fullTemplate(pkg, serviceKey, schema, envPrefix) {
  const schemaArg = schema === 'public' ? 'public' : schema;
  return `package ${pkg};

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import zw.gov.mohcc.impilo.security.audit.AdminAuditEmitter;
import zw.gov.mohcc.impilo.security.ratelimit.RateLimitGuard;
import zw.gov.mohcc.impilo.security.secrets.EnvSecretProvider;
import zw.gov.mohcc.impilo.security.secrets.SecretProvider;
import zw.gov.mohcc.impilo.shared.security.AdminAuditOutboxSink;
import zw.gov.mohcc.impilo.shared.security.RateLimitFilter;
import zw.gov.mohcc.impilo.shared.security.SecurityBaselineExceptionHandler;

import javax.sql.DataSource;

/** Wave 14 security baseline (admin audit + rate limit). Generated by emit-security-baseline-config.mjs */
@Configuration
public class SecurityBaselineConfig {

    @Bean
    public RateLimitGuard rateLimitGuard() {
        return new RateLimitGuard(100, 2);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitGuard guard) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(guard));
        registration.addUrlPatterns("/v1/*", "/internal/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }

    @Bean
    public AdminAuditEmitter adminAuditEmitter(DataSource dataSource) {
        return new AdminAuditEmitter("${serviceKey}", new AdminAuditOutboxSink(dataSource, "${schemaArg}"));
    }

    @Bean
    public SecretProvider secretProvider() {
        return new EnvSecretProvider("${envPrefix}");
    }

    @Bean
    public SecurityBaselineExceptionHandler securityBaselineExceptionHandler() {
        return new SecurityBaselineExceptionHandler();
    }
}
`;
}

function rateOnlyTemplate(pkg, envPrefix) {
  return `package ${pkg};

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import zw.gov.mohcc.impilo.security.ratelimit.RateLimitGuard;
import zw.gov.mohcc.impilo.security.secrets.EnvSecretProvider;
import zw.gov.mohcc.impilo.security.secrets.SecretProvider;
import zw.gov.mohcc.impilo.shared.security.RateLimitFilter;
import zw.gov.mohcc.impilo.shared.security.SecurityBaselineExceptionHandler;

/** Wave 14 security baseline (rate limit; v1.1 domain outbox handles audit events). Generated by emit-security-baseline-config.mjs */
@Configuration
public class SecurityBaselineConfig {

    @Bean
    public RateLimitGuard rateLimitGuard() {
        return new RateLimitGuard(100, 2);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitGuard guard) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(guard));
        registration.addUrlPatterns("/v1/*", "/internal/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }

    @Bean
    public SecretProvider secretProvider() {
        return new EnvSecretProvider("${envPrefix}");
    }

    @Bean
    public SecurityBaselineExceptionHandler securityBaselineExceptionHandler() {
        return new SecurityBaselineExceptionHandler();
    }
}
`;
}

main();
