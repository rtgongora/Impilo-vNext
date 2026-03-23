#!/usr/bin/env bash
# scan-placeholder-indicators.sh — Find stub/placeholder/mock patterns across the codebase
set -euo pipefail

echo "=== Stub Density Audit: Placeholder Indicator Scan ==="
echo "Date: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo ""

echo "--- 1. SAMPLE_/MOCK_/FAKE_/DUMMY_ constants ---"
grep -rn "SAMPLE_\|MOCK_\|FAKE_\|DUMMY_" ui/ apps/ --include="*.tsx" --include="*.ts" 2>/dev/null | grep -v node_modules | grep -v ".next/" | grep -v "__tests__" || echo "(none)"
echo ""

echo "--- 2. 'coming soon' / 'placeholder' / 'TODO.*implement' ---"
grep -rni "coming soon\|placeholder[^:]\|TODO.*implement\|not yet implemented" ui/ apps/ services/ --include="*.tsx" --include="*.ts" --include="*.java" 2>/dev/null | grep -v node_modules | grep -v ".next/" | grep -v "__tests__" || echo "(none)"
echo ""

echo "--- 3. alert() calls (stub error handling) ---"
grep -rn "alert(" ui/ apps/ --include="*.tsx" --include="*.ts" 2>/dev/null | grep -v node_modules | grep -v ".next/" | grep -v "__tests__" || echo "(none)"
echo ""

echo "--- 4. Hardcoded demo data patterns (const X: Type[] = [...]) ---"
grep -rn "const .*: .*\[\] = \[" ui/ apps/ --include="*.tsx" --include="*.ts" 2>/dev/null | grep -v node_modules | grep -v ".next/" | grep -v "__tests__" | grep -v "tailwind" | grep -v "NAV_" | grep -v "layout" | head -50 || echo "(none)"
echo ""

echo "--- 5. 'will be rendered here' / 'Connect to' patterns ---"
grep -rni "will be rendered\|connect to.*api\|will be implemented" ui/ apps/ --include="*.tsx" --include="*.ts" 2>/dev/null | grep -v node_modules | grep -v ".next/" || echo "(none)"
echo ""

echo "=== Scan complete ==="
