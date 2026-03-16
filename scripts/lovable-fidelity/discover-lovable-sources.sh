#!/usr/bin/env bash
# discover-lovable-sources.sh — Identifies and classifies Lovable reference materials
set -euo pipefail

echo "=== Lovable Source Discovery ==="
echo ""

echo "--- Prototype Index Layer (docs/prototype/final/) ---"
if [ -d "docs/prototype/final" ]; then
  for f in docs/prototype/final/*.md; do
    lines=$(wc -l < "$f" 2>/dev/null || echo "0")
    echo "  [INDEX] $f ($lines lines)"
  done
else
  echo "  [MISSING] docs/prototype/final/ directory not found"
fi

echo ""
echo "--- Canonical Plan Specs (docs/plan/) ---"
if [ -d "docs/plan" ]; then
  for f in docs/plan/*.md; do
    lines=$(wc -l < "$f" 2>/dev/null || echo "0")
    echo "  [CANONICAL] $f ($lines lines)"
  done
else
  echo "  [MISSING] docs/plan/ directory not found"
fi

echo ""
echo "--- Architecture Specs (docs/architecture/v1.1/) ---"
if [ -d "docs/architecture/v1.1" ]; then
  for f in docs/architecture/v1.1/*.md; do
    lines=$(wc -l < "$f" 2>/dev/null || echo "0")
    echo "  [CANONICAL] $f ($lines lines)"
  done
else
  echo "  [MISSING] docs/architecture/v1.1/ directory not found"
fi

echo ""
echo "--- Spec Conflict Docs ---"
for f in compose/experience/SPEC_CONFLICTS.md docs/spec-conflicts/*.md; do
  if [ -f "$f" ]; then
    lines=$(wc -l < "$f" 2>/dev/null || echo "0")
    echo "  [SECONDARY] $f ($lines lines)"
  fi
done

echo ""
echo "--- Clinical Fidelity Materials ---"
if [ -d "docs/clinical" ]; then
  for f in docs/clinical/*.md; do
    lines=$(wc -l < "$f" 2>/dev/null || echo "0")
    echo "  [SECONDARY] $f ($lines lines)"
  done
fi

echo ""
echo "--- Lovable Fidelity Outputs ---"
if [ -d "docs/lovable-fidelity" ]; then
  for f in docs/lovable-fidelity/*.md; do
    lines=$(wc -l < "$f" 2>/dev/null || echo "0")
    echo "  [OUTPUT] $f ($lines lines)"
  done
fi

echo ""
echo "--- Route Registry ---"
if [ -f "ui/experience/src/lib/routes.ts" ]; then
  route_count=$(grep -c "path:" ui/experience/src/lib/routes.ts 2>/dev/null || echo "0")
  echo "  [CANONICAL] ui/experience/src/lib/routes.ts ($route_count routes defined)"
fi

echo ""
echo "=== Discovery Complete ==="
