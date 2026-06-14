#!/usr/bin/env bash
# audit-page-fidelity.sh — Checks route pages for stub vs real implementation
set -euo pipefail

echo "=== Page Fidelity Audit ==="
echo ""

PAGE_DIR="ui/one-ui-shell/src/app"
TOTAL=0
IMPLEMENTED=0
STUBS=0

if [ ! -d "$PAGE_DIR" ]; then
  echo "ERROR: $PAGE_DIR not found"
  exit 1
fi

is_composition_surface() {
  local page="$1"
  grep -qE "ScopedAdministrationSurface|PlaneWorkspaceShell|CoreTransactionShell|OperatorTelemetryPanel|PageShell|OnboardFlowWizard|BootstrapPage|FundoStudioWorkspace" "$page" 2>/dev/null
}

is_redirect_page() {
  local page="$1"
  grep -qE "router\.(replace|push)|redirect\(" "$page" 2>/dev/null
}

while IFS= read -r page; do
  TOTAL=$((TOTAL + 1))

  if is_composition_surface "$page"; then
    IMPLEMENTED=$((IMPLEMENTED + 1))
    echo "  [COMP] $page"
  elif is_redirect_page "$page"; then
    IMPLEMENTED=$((IMPLEMENTED + 1))
    echo "  [REDIR] $page"
  elif grep -qE "useQuery|useMutation|useState|use[A-Z][a-zA-Z]+|apiClient|useParams" "$page" 2>/dev/null; then
    IMPLEMENTED=$((IMPLEMENTED + 1))
    echo "  [IMPL] $page"
  else
    lines=$(wc -l < "$page" 2>/dev/null || echo "0")
    if [ "$lines" -lt 20 ]; then
      STUBS=$((STUBS + 1))
      echo "  [STUB] $page ($lines lines)"
    else
      IMPLEMENTED=$((IMPLEMENTED + 1))
      echo "  [IMPL] $page ($lines lines)"
    fi
  fi
done < <(find "$PAGE_DIR" -name "page.tsx" -type f | sort)

echo ""
echo "=== Summary ==="
echo "  Total pages:    $TOTAL"
echo "  Implemented:    $IMPLEMENTED"
echo "  Stubs:          $STUBS"
if [ "$TOTAL" -gt 0 ]; then
  echo "  Implementation: $(( IMPLEMENTED * 100 / TOTAL ))%"
fi
echo ""

if [ "$STUBS" -gt 0 ]; then
  echo "  STATUS: INCOMPLETE — $STUBS stub pages remain"
  exit 1
else
  echo "  STATUS: COMPLETE — all pages implemented"
  exit 0
fi
