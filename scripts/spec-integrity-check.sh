#!/usr/bin/env bash
set -euo pipefail

# Spec Integrity Check — Impilo vNext
# Verifies that docs/prototype/final/*.md files are not stubs.
# Exit 0 on pass, non-zero on failure.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CANONICAL_SPEC_ROOT="${REPO_ROOT}/docs/plan"
ARCH_SPEC_ROOT="${REPO_ROOT}/docs/architecture/v1.1"
PROTOTYPE_FINAL="${REPO_ROOT}/docs/prototype/final"

# The 8 required files
REQUIRED_FILES=(
  "00_executive_summary.md"
  "01_site_map.md"
  "02_page_by_page_spec.md"
  "03_component_inventory.md"
  "04_api_surface_map.md"
  "05_state_and_storage.md"
  "06_golden_paths.md"
  "07_opus_execution_contract.md"
)

# Placeholder phrases that indicate a stub
PLACEHOLDER_PATTERNS="stub|summary only|TODO|TBD|placeholder|PLACEHOLDER"

# Minimum line count for a self-contained spec doc
MIN_LINES=200

# Minimum valid relative links for an index doc
MIN_LINKS=10

FAILURES=()
PASS_COUNT=0

echo "=== Spec Integrity Check ==="
echo "Canonical spec root: ${CANONICAL_SPEC_ROOT}"
echo "Architecture spec root: ${ARCH_SPEC_ROOT}"
echo "Prototype final dir: ${PROTOTYPE_FINAL}"
echo ""

# Check 1: Canonical spec root exists and has files
if [[ ! -d "${CANONICAL_SPEC_ROOT}" ]]; then
  FAILURES+=("FAIL: Canonical spec root '${CANONICAL_SPEC_ROOT}' does not exist")
else
  CANON_COUNT=$(find "${CANONICAL_SPEC_ROOT}" -name "*.md" -type f | wc -l)
  if [[ "${CANON_COUNT}" -lt 1 ]]; then
    FAILURES+=("FAIL: Canonical spec root has no .md files")
  else
    echo "PASS: Canonical spec root exists with ${CANON_COUNT} files"
    PASS_COUNT=$((PASS_COUNT + 1))
  fi
fi

# Check 2: Architecture spec root exists
if [[ ! -d "${ARCH_SPEC_ROOT}" ]]; then
  FAILURES+=("FAIL: Architecture spec root '${ARCH_SPEC_ROOT}' does not exist")
else
  ARCH_COUNT=$(find "${ARCH_SPEC_ROOT}" -name "*.md" -type f | wc -l)
  echo "PASS: Architecture spec root exists with ${ARCH_COUNT} files"
  PASS_COUNT=$((PASS_COUNT + 1))
fi

# Check 3: Each required file exists and is not a stub
for FILE in "${REQUIRED_FILES[@]}"; do
  FILEPATH="${PROTOTYPE_FINAL}/${FILE}"

  # 3a: File exists
  if [[ ! -f "${FILEPATH}" ]]; then
    FAILURES+=("FAIL: Missing required file: ${FILE}")
    continue
  fi

  # 3b: Line count check
  LINE_COUNT=$(wc -l < "${FILEPATH}")

  # 3c: Check for placeholder phrases
  PLACEHOLDER_HITS=$(grep -ciE "${PLACEHOLDER_PATTERNS}" "${FILEPATH}" 2>/dev/null || true)

  # 3d: Count valid relative links into canonical spec dirs
  # Links to ../../plan/ or ../../architecture/ or ../../../ paths
  LINK_COUNT=$(grep -coE '\[.*\]\(\.\./\.\.' "${FILEPATH}" 2>/dev/null || true)
  # Also count links to ../../../ (3 levels up, e.g., to compose/ or ui/)
  LINK_COUNT_3=$(grep -coE '\[.*\]\(\.\./\.\./\.\.' "${FILEPATH}" 2>/dev/null || true)
  TOTAL_LINKS=$((LINK_COUNT + LINK_COUNT_3))

  # Decision: file passes if either:
  # (a) line count >= MIN_LINES (self-contained detailed doc), OR
  # (b) it's an index doc with >= MIN_LINKS valid relative links AND no placeholder phrases
  if [[ "${LINE_COUNT}" -ge "${MIN_LINES}" ]]; then
    # Large enough to be a detailed doc — check for placeholders anyway
    if [[ "${PLACEHOLDER_HITS}" -gt 0 ]]; then
      FAILURES+=("FAIL: ${FILE} — has ${LINE_COUNT} lines but contains ${PLACEHOLDER_HITS} placeholder phrase(s)")
    else
      echo "PASS: ${FILE} — ${LINE_COUNT} lines, detailed doc"
      PASS_COUNT=$((PASS_COUNT + 1))
    fi
  elif [[ "${TOTAL_LINKS}" -ge "${MIN_LINKS}" ]] && [[ "${PLACEHOLDER_HITS}" -eq 0 ]]; then
    echo "PASS: ${FILE} — ${LINE_COUNT} lines, index doc with ${TOTAL_LINKS} canonical links"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    # Determine why it failed
    REASONS=""
    if [[ "${LINE_COUNT}" -lt "${MIN_LINES}" ]]; then
      REASONS="${REASONS} line_count=${LINE_COUNT}<${MIN_LINES}"
    fi
    if [[ "${TOTAL_LINKS}" -lt "${MIN_LINKS}" ]]; then
      REASONS="${REASONS} links=${TOTAL_LINKS}<${MIN_LINKS}"
    fi
    if [[ "${PLACEHOLDER_HITS}" -gt 0 ]]; then
      REASONS="${REASONS} placeholders=${PLACEHOLDER_HITS}"
    fi
    FAILURES+=("FAIL: ${FILE} —${REASONS}")
  fi
done

echo ""
echo "=== RESULTS ==="
echo "Passed: ${PASS_COUNT}"
echo "Failed: ${#FAILURES[@]}"

if [[ ${#FAILURES[@]} -gt 0 ]]; then
  echo ""
  echo "--- Failures ---"
  for F in "${FAILURES[@]}"; do
    echo "  ${F}"
  done
  echo ""
  echo "SPEC INTEGRITY CHECK: FAIL"
  exit 1
else
  echo ""
  echo "SPEC INTEGRITY CHECK: PASS"
  exit 0
fi
