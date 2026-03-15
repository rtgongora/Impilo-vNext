#!/usr/bin/env bash
# verify-deprecation-window.sh — Enforce versioning and deprecation policy
#
# Reads docs/ecosystem/api-versioning-registry.json and validates:
# 1. All deprecated APIs have at least 90 days before sunset
# 2. No more than 2 concurrent versions per service
# 3. Sunset dates have not passed without removal
# 4. All event types follow naming convention
#
# Exit code: 0 = all checks pass, 1 = violations found
#
# Usage: ./scripts/ecosystem/verify-deprecation-window.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
REGISTRY="${REPO_ROOT}/docs/ecosystem/api-versioning-registry.json"

PASS=0
FAIL=0
WARN=0

pass() { echo "  ✓ PASS: $1"; PASS=$((PASS + 1)); }
fail() { echo "  ✗ FAIL: $1"; FAIL=$((FAIL + 1)); }
warn() { echo "  ⚠ WARN: $1"; WARN=$((WARN + 1)); }

echo "============================================="
echo "  API Versioning & Deprecation Policy Check"
echo "============================================="
echo ""

if [ ! -f "${REGISTRY}" ]; then
    fail "Registry not found: ${REGISTRY}"
    exit 1
fi

pass "Registry file exists"

# ─── Check 1: Deprecation windows ───
echo ""
echo "─── Check 1: Deprecation Window Enforcement ───"

MIN_WINDOW_DAYS=$(python3 -c "
import json, sys
with open('${REGISTRY}') as f:
    data = json.load(f)
print(data['versioning_rules']['min_deprecation_window_days'])
" 2>/dev/null || echo "90")

echo "  Minimum window: ${MIN_WINDOW_DAYS} days"

python3 -c "
import json, sys
from datetime import datetime, timedelta

with open('${REGISTRY}') as f:
    data = json.load(f)

min_days = ${MIN_WINDOW_DAYS}
today = datetime.now().date()
violations = []

for api in data.get('api_versions', []):
    svc = api['service']
    status = api['status']
    dep = api.get('deprecated_at')
    sun = api.get('sunset_at')

    if status == 'DEPRECATED' and dep and sun:
        dep_date = datetime.strptime(dep, '%Y-%m-%d').date()
        sun_date = datetime.strptime(sun, '%Y-%m-%d').date()
        window = (sun_date - dep_date).days

        if window < min_days:
            violations.append(f'{svc} v{api[\"version\"]}: deprecation window is {window} days (min {min_days})')
            print(f'  ✗ FAIL: {svc} v{api[\"version\"]}: window={window}d < min {min_days}d')
        else:
            print(f'  ✓ PASS: {svc} v{api[\"version\"]}: window={window}d >= {min_days}d')

if not any(a['status'] == 'DEPRECATED' for a in data.get('api_versions', [])):
    print('  ✓ PASS: No deprecated APIs (nothing to check)')

sys.exit(1 if violations else 0)
" 2>/dev/null

if [ $? -eq 0 ]; then
    PASS=$((PASS + 1))
else
    FAIL=$((FAIL + 1))
fi

# ─── Check 2: Max concurrent versions ───
echo ""
echo "─── Check 2: Max Concurrent Versions ───"

MAX_VERSIONS=$(python3 -c "
import json
with open('${REGISTRY}') as f:
    data = json.load(f)
print(data['versioning_rules']['max_concurrent_versions'])
" 2>/dev/null || echo "2")

echo "  Max concurrent: ${MAX_VERSIONS}"

python3 -c "
import json, sys
from collections import defaultdict

with open('${REGISTRY}') as f:
    data = json.load(f)

max_v = ${MAX_VERSIONS}
service_versions = defaultdict(list)

for api in data.get('api_versions', []):
    if api['status'] in ('CURRENT', 'DEPRECATED'):
        service_versions[api['service']].append(api['version'])

violations = []
for svc, versions in service_versions.items():
    if len(versions) > max_v:
        violations.append(f'{svc}: {len(versions)} active versions (max {max_v})')
        print(f'  ✗ FAIL: {svc} has {len(versions)} active versions > max {max_v}')
    else:
        print(f'  ✓ PASS: {svc} has {len(versions)} version(s)')

sys.exit(1 if violations else 0)
" 2>/dev/null

if [ $? -eq 0 ]; then
    PASS=$((PASS + 1))
else
    FAIL=$((FAIL + 1))
fi

# ─── Check 3: Sunset dates not expired ───
echo ""
echo "─── Check 3: Expired Sunset Dates ───"

python3 -c "
import json, sys
from datetime import datetime

with open('${REGISTRY}') as f:
    data = json.load(f)

today = datetime.now().date()
grace_days = data['versioning_rules'].get('sunset_grace_period_days', 30)
violations = []

for api in data.get('api_versions', []):
    sun = api.get('sunset_at')
    if sun and api['status'] != 'SUNSET':
        sun_date = datetime.strptime(sun, '%Y-%m-%d').date()
        grace_end = sun_date + __import__('datetime').timedelta(days=grace_days)
        if today > grace_end:
            violations.append(f'{api[\"service\"]} v{api[\"version\"]}: sunset was {sun}, grace ended {grace_end}')
            print(f'  ✗ FAIL: {api[\"service\"]} v{api[\"version\"]}: sunset {sun} + {grace_days}d grace expired')

if not violations:
    print('  ✓ PASS: No expired sunset dates')

sys.exit(1 if violations else 0)
" 2>/dev/null

if [ $? -eq 0 ]; then
    PASS=$((PASS + 1))
else
    FAIL=$((FAIL + 1))
fi

# ─── Check 4: Event type naming ───
echo ""
echo "─── Check 4: Event Type Naming Convention ───"

python3 -c "
import json, re, sys

with open('${REGISTRY}') as f:
    data = json.load(f)

pattern = re.compile(r'^impilo\.[a-z][a-z0-9_-]*\.[a-z][a-z0-9._]*\.[a-z][a-z0-9_]*\.v\d+$')
violations = []

for evt in data.get('event_versions', []):
    et = evt['event_type']
    if pattern.match(et):
        print(f'  ✓ PASS: {et}')
    else:
        violations.append(et)
        print(f'  ✗ FAIL: {et} does not match naming convention')

sys.exit(1 if violations else 0)
" 2>/dev/null

if [ $? -eq 0 ]; then
    PASS=$((PASS + 1))
else
    FAIL=$((FAIL + 1))
fi

# ─── Check 5: All CURRENT versions have introduction dates ───
echo ""
echo "─── Check 5: Version Metadata Completeness ───"

python3 -c "
import json, sys

with open('${REGISTRY}') as f:
    data = json.load(f)

violations = []

for api in data.get('api_versions', []):
    svc = api['service']
    if not api.get('introduced'):
        violations.append(f'{svc} v{api[\"version\"]}: missing introduction date')
        print(f'  ✗ FAIL: {svc} v{api[\"version\"]}: no introduced date')
    elif not api.get('endpoints'):
        violations.append(f'{svc} v{api[\"version\"]}: no endpoints listed')
        print(f'  ✗ FAIL: {svc} v{api[\"version\"]}: no endpoints')
    else:
        print(f'  ✓ PASS: {svc} v{api[\"version\"]}: introduced={api[\"introduced\"]}, {len(api[\"endpoints\"])} endpoints')

sys.exit(1 if violations else 0)
" 2>/dev/null

if [ $? -eq 0 ]; then
    PASS=$((PASS + 1))
else
    FAIL=$((FAIL + 1))
fi

# ─── Summary ───
echo ""
echo "============================================="
echo "  Results: ${PASS} passed, ${FAIL} failed, ${WARN} warnings"
echo "============================================="

if [ "${FAIL}" -gt 0 ]; then
    exit 1
fi
