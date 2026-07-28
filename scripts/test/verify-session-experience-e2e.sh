#!/usr/bin/env bash
# verify-session-experience-e2e.sh
#
# Multi-persona Session Experience verification harness.
#
# Deployment truth is the running estate, not the deployment story. This harness logs the
# live Session Experience Contract for each seeded persona and asserts the doctrine-correct
# shape (tabs, management workspaces, default route), then smokes the governance endpoints
# and reconciles running image digests. It concludes with one of the audit A-F outcomes.
#
# READ-ONLY: HTTP GET + read-only kubectl/helm only. No cluster mutation, no deploy.
#
# Usage:
#   scripts/test/verify-session-experience-e2e.sh [--url URL] [--ns NS] [--tenant UUID] [--strict]
#
# Exit codes: 0 pass/advisory | 1 strict failure | 2 usage
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH" 2>/dev/null || true

URL="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
TENANT="${IMPILO_TENANT_ID:-00000000-0000-4000-8000-000000000001}"
STRICT="${SESSION_E2E_STRICT:-0}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url) URL="${2:-}"; shift 2 ;;
    --ns) NS="${2:-}"; shift 2 ;;
    --tenant) TENANT="${2:-}"; shift 2 ;;
    --strict) STRICT=1; shift ;;
    -h|--help) grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

command -v curl >/dev/null 2>&1 || { echo "SESSION_E2E: UNKNOWN (curl unavailable)"; exit 0; }
command -v python3 >/dev/null 2>&1 || { echo "SESSION_E2E: UNKNOWN (python3 unavailable)"; exit 0; }

fail=0
warn=0
echo "== Session Experience E2E: $URL (tenant $TENANT, ns $NS) =="

hdr() {
  local actor="$1" atype="$2"
  echo "-H X-Tenant-ID:$TENANT -H X-Pod-ID:preview -H X-Request-ID:se-e2e -H X-Correlation-ID:se-e2e -H X-Actor-ID:$actor -H X-Actor-Type:$atype"
}

# ── 1. Per-persona contract assertions ────────────────────────────────────────
# row: label|actorId|actorType|expectIdentityRegex|expectWork(true|false|any)|minMgmt
PERSONAS=(
  "ProductOwner|b0000000-0000-4000-8000-000000000010|staff|dual_citizen_provider|provider_worker|true|1"
  "Clinician|c0000000-0000-4000-8000-000000000001|provider|dual_citizen_provider|provider_worker|true|0"
  "Citizen|c0000000-0000-4000-8000-0000000000aa|citizen|citizen|citizen|false|0"
)

echo "-- Persona contract assertions --"
for row in "${PERSONAS[@]}"; do
  IFS='|' read -r label actor atype idA idB expectWork minMgmt <<<"$row"
  body="$(curl -s --max-time 15 \
    -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: preview" \
    -H "X-Request-ID: se-$label" -H "X-Correlation-ID: se-$label" \
    -H "X-Actor-ID: $actor" -H "X-Actor-Type: $atype" \
    "$URL/internal/v1/session/experience" 2>/dev/null || true)"
  if [[ -z "$body" ]]; then
    echo "  FAIL $label: no contract response"; fail=1; continue
  fi
  read -r itype work mgmt ppoa cver <<<"$(printf '%s' "$body" | python3 -c '
import sys,json
try:
    a=json.load(sys.stdin)["data"]["attributes"]
except Exception:
    print("ERR ERR ERR ERR ERR"); sys.exit(0)
print(
    a.get("identity",{}).get("identityType","?"),
    a.get("tabs",{}).get("work",{}).get("visible","?"),
    len(a.get("visibleManagementWorkspaces",[]) or []),
    a.get("policyMetadata",{}).get("previewProductOwnerAccess","absent"),
    a.get("policyMetadata",{}).get("contractVersion","?"),
)' 2>/dev/null)"
  ok=1
  [[ "$itype" == "$idA" || "$itype" == "$idB" ]] || ok=0
  if [[ "$expectWork" != "any" ]]; then
    [[ "${work,,}" == "$expectWork" ]] || ok=0
  fi
  [[ "${mgmt:-0}" =~ ^[0-9]+$ && "${mgmt:-0}" -ge "$minMgmt" ]] || ok=0
  # K-1 doctrine gate: the preview Product Owner override must be gone.
  if [[ "$ppoa" != "absent" && "$ppoa" != "None" ]]; then
    echo "  FAIL $label: previewProductOwnerAccess present ($ppoa) — K-1 violated"; fail=1; ok=0
  fi
  if [[ "$ok" == "1" ]]; then
    echo "  PASS $label: identity=$itype work=$work mgmt=$mgmt cver=$cver"
  else
    echo "  FAIL $label: identity=$itype (want $idA/$idB) work=$work (want $expectWork) mgmt=$mgmt (>= $minMgmt) cver=$cver"
    fail=1
  fi
done

# ── 2. Governance endpoint smoke (never 404-for-implemented, never raw 500) ────
echo "-- Endpoint smoke --"
po_hdr=(-H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: preview" -H "X-Request-ID: se-smoke" -H "X-Correlation-ID: se-smoke" -H "X-Actor-ID: b0000000-0000-4000-8000-000000000010" -H "X-Actor-Type: staff")
declare -A EXPECT=(
  ["/internal/v1/bootstrap/status"]="200"
  ["/internal/v1/admin-governance/organisations"]="200 401 403"
)
for path in "${!EXPECT[@]}"; do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "${po_hdr[@]}" "$URL$path" 2>/dev/null || echo 000)"
  if [[ " ${EXPECT[$path]} " == *" $code "* ]]; then
    echo "  PASS $path -> $code"
  else
    echo "  FAIL $path -> $code (expected ${EXPECT[$path]})"
    [[ "$code" == "404" || "$code" == "500" ]] && fail=1 || warn=1
  fi
done

# WGV in-cluster (read-only) — must not 500 with a valid tenant header.
if command -v kubectl >/dev/null 2>&1; then
  wgv_code="$(kubectl -n "$NS" exec deploy/experience-bff -- sh -c \
    "curl -s -o /dev/null -w '%{http_code}' --max-time 10 -H 'X-Tenant-ID: $TENANT' -H 'X-Pod-ID: preview' -H 'X-Request-ID: se' -H 'X-Correlation-ID: se' http://workforce-governance-service:8165/v1/internal/governance/organisations" 2>/dev/null || echo 000)"
  if [[ "$wgv_code" =~ ^2|^4 ]]; then
    echo "  PASS WGV /governance/organisations -> $wgv_code"
  else
    echo "  FAIL WGV /governance/organisations -> $wgv_code"; fail=1
  fi
fi

# ── 3. Deploy truth — running digests vs committed generated digests ──────────
echo "-- Deploy truth (running vs committed digests) --"
DIGEST_FILE="deploy/helm/impilo-vnext/values-full-preview-digests.generated.yaml"
if command -v kubectl >/dev/null 2>&1 && [[ -f "$DIGEST_FILE" ]]; then
  for svc in one-ui-shell experience-bff workforce-governance-service; do
    running="$(kubectl -n "$NS" get pods -l app="$svc" -o jsonpath='{.items[0].spec.containers[0].image}' 2>/dev/null | sed 's/.*@//')"
    key="$svc"
    case "$svc" in
      one-ui-shell) committed="$(python3 -c 'import sys,yaml;d=yaml.safe_load(open(sys.argv[1]));print(d.get("images",{}).get("oneUiShell",{}).get("digest",""))' "$DIGEST_FILE" 2>/dev/null)";;
      experience-bff) committed="$(python3 -c 'import sys,yaml;d=yaml.safe_load(open(sys.argv[1]));print(d.get("images",{}).get("experienceBff",{}).get("digest",""))' "$DIGEST_FILE" 2>/dev/null)";;
      *) committed="$(python3 -c 'import sys,yaml;d=yaml.safe_load(open(sys.argv[1]));print(d.get("fullBootServices",{}).get("workforce-governance-service",{}).get("image",{}).get("digest",""))' "$DIGEST_FILE" 2>/dev/null)";;
    esac
    if [[ -z "$running" ]]; then
      echo "  WARN $key: no running pod"; warn=1
    elif [[ -z "$committed" ]]; then
      echo "  WARN $key: no committed digest"; warn=1
    elif [[ "$running" == "$committed" ]]; then
      echo "  PASS $key: running matches committed (${running:0:19})"
    else
      echo "  DRIFT $key: running ${running:0:19} != committed ${committed:0:19}"; warn=1
    fi
  done
fi

# ── 4. Outcome (audit A-F taxonomy) ──────────────────────────────────────────
echo "-- Outcome --"
if [[ "$fail" == "1" ]]; then
  echo "SESSION_E2E OUTCOME: C/F — contract or endpoint failures (see FAIL lines above)"
  [[ "$STRICT" == "1" ]] && exit 1 || exit 0
elif [[ "$warn" == "1" ]]; then
  echo "SESSION_E2E OUTCOME: B — running estate drifts from committed digests / advisory warnings"
  exit 0
else
  echo "SESSION_E2E OUTCOME: A — all personas resolve correctly; endpoints healthy; digests aligned"
  exit 0
fi
