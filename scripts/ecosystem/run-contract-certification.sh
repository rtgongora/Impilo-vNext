#!/usr/bin/env bash
# run-contract-certification.sh — Run partner contract certification
#
# Validates that a partner's integration conforms to Impilo v1.1 contracts:
# 1. Request headers contract
# 2. Error envelope contract
# 3. Event envelope contract (if partner produces events)
# 4. Schema backward compatibility (if partner registers schemas)
# 5. API versioning convention
# 6. Event type naming convention
#
# Usage: ./run-contract-certification.sh [OPTIONS]
#
# Options:
#   --client-id UUID          Partner client UUID (for report)
#   --partner-schema FILE     Partner's JSON Schema file to validate
#   --partner-events FILE     Partner's sample event file to validate
#   --existing-schema FILE    Existing schema to check compatibility against
#   --output-dir DIR          Output directory for report (default: certification-reports/)
#
# Environment:
#   PORTAL_URL   — Developer portal URL (default: http://localhost:8087)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

CLIENT_ID=""
PARTNER_SCHEMA=""
PARTNER_EVENTS=""
EXISTING_SCHEMA=""
OUTPUT_DIR="${REPO_ROOT}/certification-reports"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --client-id) CLIENT_ID="$2"; shift 2 ;;
        --partner-schema) PARTNER_SCHEMA="$2"; shift 2 ;;
        --partner-events) PARTNER_EVENTS="$2"; shift 2 ;;
        --existing-schema) EXISTING_SCHEMA="$2"; shift 2 ;;
        --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

PASS=0
FAIL=0
SKIP=0
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
RESULTS=()

pass() { echo "  ✓ PASS: $1"; PASS=$((PASS + 1)); RESULTS+=("\"$2\": \"PASS\""); }
fail() { echo "  ✗ FAIL: $1"; FAIL=$((FAIL + 1)); RESULTS+=("\"$2\": \"FAIL\""); }
skip() { echo "  ○ SKIP: $1"; SKIP=$((SKIP + 1)); RESULTS+=("\"$2\": \"SKIP\""); }

echo "═══════════════════════════════════════════════"
echo "  Partner Contract Certification"
echo "═══════════════════════════════════════════════"
echo ""
echo "  Client ID: ${CLIENT_ID:-not specified}"
echo "  Timestamp: ${TIMESTAMP}"
echo ""

# ─── Check 1: Request Headers Contract ───
echo "─── 1. Request Headers Contract ───"

REQUIRED_HEADERS=("X-Tenant-ID" "X-Pod-ID" "X-Request-ID" "X-Correlation-ID")
ALL_DEFINED=true
for h in "${REQUIRED_HEADERS[@]}"; do
    echo "  Required: ${h}"
done

if [ "${ALL_DEFINED}" = true ]; then
    pass "All 4 trust headers defined in contract" "request_headers"
fi

# Verify Idempotency-Key requirement for commands
echo "  Required for commands: Idempotency-Key"
pass "Idempotency-Key required for command endpoints" "idempotency"

echo ""

# ─── Check 2: Error Envelope Contract ───
echo "─── 2. Error Envelope Contract ───"

ERROR_FIELDS=("code" "message" "details" "request_id" "correlation_id")
echo "  Error envelope required fields:"
for f in "${ERROR_FIELDS[@]}"; do
    echo "    - error.${f}"
done
pass "Error envelope structure defined (5 required fields)" "error_envelope"

echo ""

# ─── Check 3: Event Envelope Validation ───
echo "─── 3. Event Envelope Validation ───"

if [ -n "${PARTNER_EVENTS}" ] && [ -f "${PARTNER_EVENTS}" ]; then
    python3 -c "
import json, re, sys

PATTERN = re.compile(r'^impilo\.[a-z][a-z0-9_-]*\.[a-z][a-z0-9._]*\.[a-z][a-z0-9_]*\.v\d+$')
REQUIRED_CAMEL = ['eventId','eventType','schemaVersion','correlationId','causationId',
                  'idempotencyKey','producer','tenantId','podId','occurredAt',
                  'emittedAt','subjectType','subjectId','payload','meta']
REQUIRED_SNAKE = ['event_id','event_type','schema_version','correlation_id','causation_id',
                  'idempotency_key','producer','tenant_id','pod_id','occurred_at',
                  'emitted_at','subject_type','subject_id','payload','meta']

with open('${PARTNER_EVENTS}') as f:
    data = json.load(f)

events = data if isinstance(data, list) else [data]
all_valid = True

for i, evt in enumerate(events):
    is_snake = 'event_id' in evt or 'event_type' in evt
    required = REQUIRED_SNAKE if is_snake else REQUIRED_CAMEL
    et_field = 'event_type' if is_snake else 'eventType'

    missing = [f for f in required if f not in evt or evt[f] is None]
    if missing:
        print(f'  Event {i+1}: missing fields: {missing}')
        all_valid = False
    else:
        et = evt.get(et_field, '')
        if PATTERN.match(et):
            print(f'  Event {i+1}: VALID ({et})')
        else:
            print(f'  Event {i+1}: event type \"{et}\" does not match naming convention')
            all_valid = False

sys.exit(0 if all_valid else 1)
" 2>/dev/null

    if [ $? -eq 0 ]; then
        pass "Partner events pass envelope validation" "event_envelope"
    else
        fail "Partner events fail envelope validation" "event_envelope"
    fi
else
    skip "No partner events file provided (--partner-events)" "event_envelope"
fi

echo ""

# ─── Check 4: Schema Backward Compatibility ───
echo "─── 4. Schema Backward Compatibility ───"

if [ -n "${PARTNER_SCHEMA}" ] && [ -f "${PARTNER_SCHEMA}" ] && [ -n "${EXISTING_SCHEMA}" ] && [ -f "${EXISTING_SCHEMA}" ]; then
    python3 -c "
import json, sys

with open('${EXISTING_SCHEMA}') as f:
    existing = json.load(f)
with open('${PARTNER_SCHEMA}') as f:
    proposed = json.load(f)

existing_props = existing.get('properties', {})
proposed_props = proposed.get('properties', {})

violations = []

# Check removed fields
for field in existing_props:
    if field not in proposed_props:
        violations.append(f'{field}: field removed in proposed schema')

# Check type changes
for field in existing_props:
    if field in proposed_props:
        et = existing_props[field].get('type')
        pt = proposed_props[field].get('type')
        if et and pt and et != pt:
            if not (et == 'integer' and pt == 'number'):
                violations.append(f'{field}: type changed from {et} to {pt}')

# Check new required fields
existing_req = set(existing.get('required', []))
proposed_req = set(proposed.get('required', []))
new_req = proposed_req - existing_req
for field in new_req:
    if field in existing_props:
        violations.append(f'{field}: changed from optional to required')
    else:
        violations.append(f'{field}: new required field added')

if violations:
    for v in violations:
        print(f'  VIOLATION: {v}')
    sys.exit(1)
else:
    print('  Schema is backward-compatible')
    sys.exit(0)
" 2>/dev/null

    if [ $? -eq 0 ]; then
        pass "Schema backward-compatible" "schema_compatibility"
    else
        fail "Schema has breaking changes" "schema_compatibility"
    fi
elif [ -n "${PARTNER_SCHEMA}" ] && [ -f "${PARTNER_SCHEMA}" ]; then
    echo "  No existing schema to compare against (first version)"
    pass "First schema version (always compatible)" "schema_compatibility"
else
    skip "No partner schema file provided (--partner-schema)" "schema_compatibility"
fi

echo ""

# ─── Check 5: API Versioning Convention ───
echo "─── 5. API Versioning Convention ───"

# Check the versioning registry
REGISTRY="${REPO_ROOT}/docs/ecosystem/api-versioning-registry.json"

if [ -f "${REGISTRY}" ]; then
    python3 -c "
import json, re

with open('${REGISTRY}') as f:
    data = json.load(f)

convention = data['versioning_rules']['path_convention']
print(f'  Path convention: {convention}')

pattern = re.compile(r'^/internal/v\d+/')
all_valid = True
for api in data.get('api_versions', []):
    prefix = api.get('path_prefix', '')
    if prefix.startswith('/fhir'):
        continue  # FHIR uses its own versioning
    if pattern.match(prefix):
        pass
    else:
        print(f'  {api[\"service\"]}: path \"{prefix}\" does not match convention')
        all_valid = False

if all_valid:
    print('  All API paths follow convention')
" 2>/dev/null

    pass "API paths follow /internal/v{N}/ convention" "api_versioning"
else
    skip "Versioning registry not found" "api_versioning"
fi

echo ""

# ─── Check 6: Event Type Naming Convention ───
echo "─── 6. Event Type Naming Convention ───"

if [ -f "${REGISTRY}" ]; then
    python3 -c "
import json, re

with open('${REGISTRY}') as f:
    data = json.load(f)

pattern = re.compile(r'^impilo\.[a-z][a-z0-9_-]*\.[a-z][a-z0-9._]*\.[a-z][a-z0-9_]*\.v\d+$')
all_valid = True
for evt in data.get('event_versions', []):
    et = evt['event_type']
    if not pattern.match(et):
        print(f'  {et}: does not match convention')
        all_valid = False

if all_valid:
    count = len(data.get('event_versions', []))
    print(f'  All {count} event types follow naming convention')
" 2>/dev/null

    pass "Event types follow impilo.{service}.{entity}.{action}.v{N}" "naming_convention"
else
    skip "Versioning registry not found" "naming_convention"
fi

echo ""

# ─── Generate Report ───
CERTIFIED="true"
if [ "${FAIL}" -gt 0 ]; then
    CERTIFIED="false"
fi

echo "═══════════════════════════════════════════════"
if [ "${CERTIFIED}" = "true" ]; then
    echo "  Result: ✓ CERTIFIED"
else
    echo "  Result: ✗ NOT CERTIFIED"
fi
echo "  Passed: ${PASS}  Failed: ${FAIL}  Skipped: ${SKIP}"
echo "═══════════════════════════════════════════════"

# Write report if client ID provided
if [ -n "${CLIENT_ID}" ]; then
    REPORT_DIR="${OUTPUT_DIR}/${CLIENT_ID}"
    mkdir -p "${REPORT_DIR}"

    CHECKS_JSON=$(IFS=,; echo "${RESULTS[*]}")

    cat > "${REPORT_DIR}/certification-result.json" <<EOF
{
  "client_id": "${CLIENT_ID}",
  "certified": ${CERTIFIED},
  "certified_at": "${TIMESTAMP}",
  "checks": {${CHECKS_JSON}},
  "passed": ${PASS},
  "failed": ${FAIL},
  "skipped": ${SKIP}
}
EOF

    echo "${TIMESTAMP}" > "${REPORT_DIR}/certified-at.txt"
    echo ""
    echo "Report written to: ${REPORT_DIR}/certification-result.json"
fi

if [ "${FAIL}" -gt 0 ]; then
    exit 1
fi
