#!/usr/bin/env node
/**
 * G046: remove the `<svc>.security.oauth2-enabled=false` full-open off-switch from service
 * SecurityConfigs. The flag, when false, set `anyRequest().permitAll()` — a single property
 * that disabled ALL authentication for a service. We remove it, leaving only the test-only
 * `impilo.security.disable-oauth-for-tests` conditional. Production is then always secure
 * (oauth2 JWT); the open chain is reachable only when the test flag is explicitly true.
 *
 * Per-service request matchers in the secure branch are preserved verbatim.
 *
 * Usage: node g046-remove-oauth-offswitch.mjs [--apply]   (default: dry-run with diffs)
 */
import fs from 'node:fs';
import { execSync } from 'node:child_process';

const APPLY = process.argv.includes('--apply');

const files = execSync(
  "grep -rln 'oauth2-enabled' services --include=SecurityConfig.java",
  { encoding: 'utf8' },
).split('\n').filter((f) => f && !f.includes('/target/'));

const DISABLE_PARAM =
  '@Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests';

let changed = 0;
for (const file of files) {
  const orig = fs.readFileSync(file, 'utf8');
  let text = orig;

  const hasDisable = /disable-oauth-for-tests/.test(text);

  // The oauth2-enabled @Value param; capture the boolean variable name.
  const valRe = /@Value\(\s*"\$\{[\w.]+\.security\.oauth2-enabled:true\}"\s*\)\s*boolean\s+(\w+)\s*/;
  const m = text.match(valRe);
  if (!m) { console.log(`SKIP (no oauth2 @Value): ${file}`); continue; }
  const varName = m[1];

  if (hasDisable) {
    // disableOauthForTests param already present — just drop the oauth2 param.
    let removed = text.replace(new RegExp(valRe.source + ',\\s*'), ''); // trailing comma form
    if (removed === text) {
      removed = text.replace(new RegExp(',\\s*' + valRe.source), ''); // last-param form
    }
    text = removed;
  } else {
    // Replace the oauth2 param with the disable param (same position / commas).
    text = text.replace(valRe, DISABLE_PARAM + ' ');
  }

  // Rewrite the controlling condition (references varName) to gate on the test flag only.
  const ifRe = new RegExp('if\\s*\\([^)]*\\b' + varName + '\\b[^)]*\\)');
  if (!ifRe.test(text)) { console.log(`WARN: no if() referencing ${varName} in ${file}`); }
  text = text.replace(ifRe, 'if (!disableOauthForTests)');

  if (text === orig) { console.log(`NOCHANGE: ${file}`); continue; }
  changed++;
  if (APPLY) {
    fs.writeFileSync(file, text);
    console.log(`rewrote ${file}`);
  } else {
    console.log(`would rewrite ${file}`);
  }
}
console.log(`g046: ${changed} file(s) ${APPLY ? 'rewritten' : '(dry-run)'}`);
