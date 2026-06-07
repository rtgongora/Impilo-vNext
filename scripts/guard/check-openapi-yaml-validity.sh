#!/usr/bin/env bash
# Validates all OpenAPI YAML files parse cleanly with js-yaml (strict duplicate-key detection).
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

COMPLETENESS_DIR="$REPO_PATH/scripts/completeness"
if [[ ! -d "$COMPLETENESS_DIR/node_modules" ]]; then
  (cd "$COMPLETENESS_DIR" && npm install --silent)
fi

FAIL=0
INVALID=$(cd "$COMPLETENESS_DIR" && node --input-type=module - <<'NODE'
import fs from 'node:fs';
import path from 'node:path';
import yaml from 'js-yaml';

const root = path.resolve('..', '..');
const dir = path.join(root, 'contracts/openapi');
const bad = [];
for (const file of fs.readdirSync(dir).filter((f) => f.endsWith('.yaml') || f.endsWith('.yml')).sort()) {
  const full = path.join(dir, file);
  try {
    yaml.load(fs.readFileSync(full, 'utf8'));
  } catch (e) {
    bad.push(`${file}: ${e.reason ?? e.message} (line ${e.mark?.line ?? '?'})`);
  }
}
process.stdout.write(bad.join('\n'));
NODE
)

if [[ -n "$INVALID" ]]; then
  guard_fail "invalid OpenAPI YAML files:"
  echo "$INVALID"
  FAIL=1
else
  guard_pass "all OpenAPI YAML files parse cleanly"
fi

exit "$FAIL"
