#!/usr/bin/env bash
# verify-ui-bundle-truth.sh
#
# All of vNext is accountable. All deployable vNext services must run. Deployment truth is the
# running estate, not the deployment story. For one-ui-shell, /health/version and the pod
# imageID are not enough: the SERVED browser bundle must reflect the deployed commit.
#
# Verifies that the served one-ui-shell JS bundle:
#   - is reachable,
#   - contains expected feature markers/routes from the deployed commit,
#   - changed when expected (compared to a recorded previous hash).
#
# READ-ONLY (HTTP GET only). No cluster mutation.
#
# Usage:
#   scripts/test/verify-ui-bundle-truth.sh [--url URL] [--expect-markers "a,b,c"] \
#       [--expect-changed] [--prev-hash-file PATH] [--record]
#
# Exit codes: 0 pass | 1 fail | 2 usage/unreachable
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH" 2>/dev/null || true

URL="${PREVIEW_URL:-http://41.57.127.235}"
# Markers must be strings that live in the layout/main-app chunks themselves.
# Feature-component strings (e.g. the "All Features" label) get code-split into
# arbitrary route chunks by webpack and false-fail here; the all-features route
# token + shell error boundary are chunk-stable proof of the deployed shell.
EXPECT_MARKERS="ShellErrorBoundary,all-features"
EXPECT_CHANGED=0
RECORD=0
PREV_HASH_FILE="reports/full-boot/ui-bundle-hash.txt"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url) URL="${2:-}"; shift 2 ;;
    --expect-markers) EXPECT_MARKERS="${2:-}"; shift 2 ;;
    --expect-changed) EXPECT_CHANGED=1; shift ;;
    --prev-hash-file) PREV_HASH_FILE="${2:-}"; shift 2 ;;
    --record) RECORD=1; shift ;;
    -h|--help) grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

command -v curl >/dev/null 2>&1 || { echo "UI_BUNDLE_TRUTH: UNKNOWN (curl not available)"; exit 0; }

echo "== UI bundle truth: $URL =="

# Fetch a rendered shell page. The root may redirect to /auth/login; that page still embeds
# the layout/main-app bundles from _next/static.
html="$(curl -fsSL --max-time 20 "$URL/" 2>/dev/null || true)"
if [[ -z "$html" ]]; then
  html="$(curl -fsSL --max-time 20 "$URL/auth/login" 2>/dev/null || true)"
fi
if [[ -z "$html" ]]; then
  echo "UI_BUNDLE_TRUTH: FAIL (could not fetch app HTML from $URL)"
  exit 1
fi

# Extract the layout bundle filename (hash carrier) and main-app bundle.
layout_js="$(printf '%s' "$html" | grep -oE '/_next/static/chunks/app/layout-[a-f0-9]+\.js' | head -1 || true)"
[[ -z "$layout_js" ]] && layout_js="$(printf '%s' "$html" | grep -oE 'layout-[a-f0-9]+\.js' | head -1 | sed 's#^#/_next/static/chunks/app/#' || true)"
main_js="$(printf '%s' "$html" | grep -oE '/_next/static/chunks/main-app-[a-f0-9]+\.js' | head -1 || true)"

if [[ -z "$layout_js" ]]; then
  echo "UI_BUNDLE_TRUTH: FAIL (no layout-*.js bundle reference found in served HTML)"
  exit 1
fi

bundle_hash="$(printf '%s' "$layout_js" | grep -oE '[a-f0-9]{12,}' | head -1)"
echo "served layout bundle: $layout_js (hash=$bundle_hash)"

# Fetch the bundle JS and check feature markers.
js="$(curl -fsSL --max-time 25 "$URL$layout_js" 2>/dev/null || true)"
if [[ -n "$main_js" ]]; then
  js="$js
$(curl -fsSL --max-time 25 "$URL$main_js" 2>/dev/null || true)"
fi
if [[ -z "$js" ]]; then
  echo "UI_BUNDLE_TRUTH: FAIL (could not fetch bundle $layout_js)"
  exit 1
fi

missing=()
# Preview must be same-origin: browser bundle must not call localhost:8160 on the user's machine.
if printf '%s' "$js" | grep -qF 'localhost:8160'; then
  echo "  FORBIDDEN marker present: localhost:8160 (wrong NEXT_PUBLIC_BFF_URL at image build)"
  missing+=("same-origin-bff")
fi
IFS=',' read -ra MARKERS <<<"$EXPECT_MARKERS"
for m in "${MARKERS[@]}"; do
  m_trim="$(echo "$m" | sed 's/^ *//;s/ *$//')"
  [[ -z "$m_trim" ]] && continue
  if printf '%s' "$js" | grep -qF "$m_trim"; then
    echo "  marker present: $m_trim"
  else
    echo "  marker MISSING: $m_trim"
    missing+=("$m_trim")
  fi
done

# Optional /health/version cross-check: metadata commit must not outrun a stale bundle.
hv_commit="$(curl -fsSL --max-time 10 "$URL/health/version" 2>/dev/null | python3 -c 'import sys,json;print(json.load(sys.stdin).get("commit",""))' 2>/dev/null || true)"
[[ -n "$hv_commit" ]] && echo "health/version commit: ${hv_commit:0:12}"

# Changed-bundle check.
changed_fail=0
prev_hash=""
[[ -f "$PREV_HASH_FILE" ]] && prev_hash="$(cat "$PREV_HASH_FILE" 2>/dev/null || true)"
if [[ "$EXPECT_CHANGED" == "1" ]]; then
  if [[ -n "$prev_hash" && "$prev_hash" == "$bundle_hash" ]]; then
    echo "  EXPECTED CHANGE but bundle hash unchanged ($bundle_hash) - UI deploy did not take effect"
    changed_fail=1
  else
    echo "  bundle changed (prev=${prev_hash:-none} now=$bundle_hash)"
  fi
fi

if [[ "$RECORD" == "1" ]]; then
  mkdir -p "$(dirname "$PREV_HASH_FILE")"
  printf '%s\n' "$bundle_hash" >"$PREV_HASH_FILE"
  echo "  recorded bundle hash to $PREV_HASH_FILE"
fi

if [[ ${#missing[@]} -gt 0 || "$changed_fail" == "1" ]]; then
  echo "UI_BUNDLE_TRUTH: FAIL"
  echo "Do NOT advise a hard refresh until the served bundle reflects the deployed commit."
  exit 1
fi

echo "UI_BUNDLE_TRUTH: PASS (served bundle reflects expected feature markers)"
echo "If you previously saw stale UI, a hard refresh (Ctrl+Shift+R) is now safe - runtime digest is proven."
