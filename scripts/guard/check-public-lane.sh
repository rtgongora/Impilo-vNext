#!/usr/bin/env bash
# Gateway public-lane guard — docs/architecture/gateway-public-lane-security-adr.md
#
# 1. ENVOY PARITY (hard fail): the /internal/v1/public/ bypass route block must be
#    byte-identical in infra/envoy/envoy.yaml and infra/envoy/envoy-runtime.yaml.
# 2. REGISTRY COVERAGE (warn; strict with GATEWAY_LANE_STRICT=1): every permitAll
#    route family in the experience-bff SecurityConfig must appear in the ADR's
#    public contract registry.
# 3. PUBLIC NAMING (warn; strict with GATEWAY_LANE_STRICT=1): public surfaces named
#    in config/gateway-public-naming.yml must not contain internal service names.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

STRICT="${GATEWAY_LANE_STRICT:-0}"
ADR="docs/architecture/gateway-public-lane-security-adr.md"
DICT="config/gateway-public-naming.yml"
FAILED=0

soft_fail() {
  if [[ "$STRICT" == "1" ]]; then guard_fail "$1" || FAILED=1; else guard_warn "$1"; fi
}

# ── 1. Envoy parity ─────────────────────────────────────────────────────────
extract_public_block() {
  awk '/prefix: "\/internal\/v1\/public\/"/{on=1} on{print} on && /disabled: true/{exit}' "$1" \
    | sed 's/^[[:space:]]*//'
}
A=$(extract_public_block infra/envoy/envoy.yaml)
B=$(extract_public_block infra/envoy/envoy-runtime.yaml)
if [[ -z "$A" || -z "$B" ]]; then
  guard_fail "public-lane route missing from an Envoy config (envoy.yaml present: $([[ -n $A ]] && echo yes || echo no); envoy-runtime.yaml present: $([[ -n $B ]] && echo yes || echo no))" || FAILED=1
elif [[ "$A" != "$B" ]]; then
  guard_fail "public-lane route blocks differ between envoy.yaml and envoy-runtime.yaml (ENVOY-GATE: both files change together)" || FAILED=1
else
  guard_pass "envoy public-lane parity (identical bypass + header-strip block in both configs)"
fi

# ── 2. permitAll registry coverage ──────────────────────────────────────────
SECCONF=$(ls services/experience-bff/src/main/java/*/gov/mohcc/impilo/experience/config/SecurityConfig.java 2>/dev/null | head -1 || true)
[[ -z "$SECCONF" ]] && SECCONF=$(find services/experience-bff -name SecurityConfig.java | head -1)
if [[ -z "$SECCONF" ]]; then
  guard_fail "experience-bff SecurityConfig.java not found" || FAILED=1
else
  MISSING=$(python3 - "$SECCONF" "$ADR" <<'PY'
import re, sys
sec, adr = sys.argv[1], sys.argv[2]
src = open(sec).read()
paths = set()
for m in re.finditer(r'requestMatchers\(([^)]*)\)\s*\.permitAll', src, re.S):
    paths.update(re.findall(r'"([^"]+)"', m.group(1)))
registry = open(adr).read()
families = re.findall(r'^\|\s*`([^`]+)`', registry, re.M)
def covered(p):
    base = p.replace('/**','').replace('/*','').rstrip('/')
    for fam in families:
        f = fam.replace('/**','').replace('/*','').rstrip('/')
        if base.startswith(f) or f.startswith(base):
            return True
    return False
missing = sorted(p for p in paths if not covered(p))
# actuator/error/static noise is registered as one actuator row; ignore pure infra
noise = re.compile(r'^(/actuator|/error|/health)')
print('\n'.join(p for p in missing if not noise.match(p)))
PY
)
  if [[ -n "$MISSING" ]]; then
    soft_fail "permitAll routes not in the ADR public contract registry: $(echo "$MISSING" | tr '\n' ' ')"
  else
    guard_pass "all BFF permitAll route families covered by the ADR registry"
  fi
fi

# ── 3. Public naming scan ───────────────────────────────────────────────────
if [[ ! -f "$DICT" ]]; then
  guard_fail "naming dictionary $DICT missing" || FAILED=1
else
  SURFACES=$(python3 -c "
import yaml, glob
d = yaml.safe_load(open('$DICT'))
files = []
for g in d.get('scan_surfaces', []):
    files.extend(glob.glob(g, recursive=True))
print('\n'.join(f for f in files if not f.endswith(('.png','.svg','.ico'))))")
  HITS=""
  if [[ -n "$SURFACES" ]]; then
    # Scan source that can render or become a public URL. Implementation comments,
    # identifiers and module import paths are not citizen-visible and must not create
    # false failures; executable strings and JSX text remain strict.
    SCAN_RC=0
    HITS=$(python3 scripts/guard/check-public-naming.py "$DICT") || SCAN_RC=$?
    if [[ "$SCAN_RC" -gt 1 ]]; then
      guard_fail "public naming scanner failed (exit=$SCAN_RC)" || FAILED=1
    fi
  fi
  if [[ -n "$HITS" ]]; then
    soft_fail "internal service names on public surfaces:
$HITS"
  else
    guard_pass "public surfaces clean of internal service names ($(echo "$SURFACES" | grep -c . || true) files scanned)"
  fi
fi

# ── 4. PF-W6 access-choice affordances ─────────────────────────────────────
AFFORDANCE_PAGES=(
  ui/one-ui-shell/src/app/welcome/regulatory/page.tsx
  ui/one-ui-shell/src/app/welcome/marketplace/page.tsx
  ui/one-ui-shell/src/app/welcome/coverage/page.tsx
  ui/one-ui-shell/src/app/welcome/wellness/page.tsx
  ui/one-ui-shell/src/app/welcome/learning/page.tsx
)
AFFORDANCE_MISSING=()
for page in "${AFFORDANCE_PAGES[@]}"; do
  if [[ ! -f "$page" ]] || ! grep -q "ReasonedSignInPrompt" "$page"; then
    AFFORDANCE_MISSING+=("$page")
  fi
done
if [[ ! -f ui/one-ui-shell/src/components/public/ContinueWithoutSignIn.tsx ]] \
  || ! grep -q "Continue without signing in" \
    ui/one-ui-shell/src/components/public/ContinueWithoutSignIn.tsx; then
  AFFORDANCE_MISSING+=("shared ContinueWithoutSignIn component")
fi
if [[ ! -f ui/one-ui-shell/src/components/public/ReasonedSignInPrompt.tsx ]] \
  || ! grep -q "IntentLink" ui/one-ui-shell/src/components/public/ReasonedSignInPrompt.tsx; then
  AFFORDANCE_MISSING+=("shared reasoned sign-in + intent component")
fi
if [[ "${#AFFORDANCE_MISSING[@]}" -gt 0 ]]; then
  soft_fail "PF-W6 access choices missing: ${AFFORDANCE_MISSING[*]}"
else
  guard_pass "PF-W6 reasoned sign-in + continue-without affordances present on all new public lanes"
fi

if [[ "$FAILED" == "1" ]]; then
  exit 1
fi
guard_pass "check-public-lane complete (strict=$STRICT)"
