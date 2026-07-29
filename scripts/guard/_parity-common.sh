#!/usr/bin/env bash
# Shared parity guard helpers (allowlist, path classification, summaries).
set -euo pipefail

PARITY_ALLOWLIST="${PARITY_ALLOWLIST:-config/parity-allowlist.yml}"
FULL_BOOT_CATALOG="${FULL_BOOT_CATALOG:-docs/architecture/FULL_VNEXT_SERVICE_CATALOG.md}"
FULL_BOOT_CLASSIFICATION="${FULL_BOOT_CLASSIFICATION:-config/full-boot-service-classification.yml}"

parity_ensure_full_catalog() {
  if [[ ! -f "$FULL_BOOT_CLASSIFICATION" ]]; then
    bash scripts/full-boot/generate-artifacts.sh 2>/dev/null || true
  fi
}
PARITY_BLOCKING=0
PARITY_ADVISORY=0
PARITY_SUMMARY_FILE="${PARITY_SUMMARY_FILE:-/tmp/impilo-parity-summary.txt}"

parity_is_test_path() {
  local f="$1"
  [[ "$f" == *".test."* || "$f" == *".spec."* || "$f" == *__tests__* || "$f" == *"/test/"* \
    || "$f" == *".stories."* || "$f" == *"/storybook/"* || "$f" == *"/fixtures/"* \
    || "$f" == *".mock."* || "$f" == *"/mocks/"* ]] && return 0
  return 1
}

parity_is_allowlisted() {
  local f="$1"
  [[ -f "$PARITY_ALLOWLIST" ]] || return 1
  grep -qF "$f" "$PARITY_ALLOWLIST" 2>/dev/null
}

parity_scan_grep() {
  local label="$1"
  local root="$2"
  local pattern="$3"
  local hits
  hits="$(find "$root" -type f \( -name '*.tsx' -o -name '*.ts' -o -name '*.jsx' -o -name '*.js' \) \
    ! -path '*/node_modules/*' ! -path '*/.next/*' ! -path '*/dist/*' 2>/dev/null \
    | while read -r f; do
        parity_is_test_path "$f" && continue
        parity_is_allowlisted "$f" && continue
        if grep -qiE "$pattern" "$f" 2>/dev/null; then
          echo "$f"
        fi
      done | head -50)"
  if [[ -n "$hits" ]]; then
    echo "PARITY $label findings:"
    echo "$hits"
    return 0
  fi
  return 1
}

parity_record_blocking() { PARITY_BLOCKING=1; }
parity_record_advisory() { PARITY_ADVISORY=1; }

parity_print_verdict() {
  local name="$1"
  {
    echo "=== $name verdict ==="
    echo "blocking: $PARITY_BLOCKING"
    echo "advisory: $PARITY_ADVISORY"
  } | tee -a "$PARITY_SUMMARY_FILE"
  if [[ "$PARITY_BLOCKING" -ne 0 ]]; then
    echo "VERDICT: FAIL"
    return 1
  fi
  if [[ "$PARITY_ADVISORY" -ne 0 ]]; then
    echo "VERDICT: PASS WITH ADVISORY WARNINGS"
    return 0
  fi
  echo "VERDICT: PASS"
  return 0
}
