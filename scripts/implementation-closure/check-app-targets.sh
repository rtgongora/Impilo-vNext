#!/bin/bash
# =============================================================================
# Impilo vNext — Verify App Target Alignment
# Confirms mobile apps target Android+iOS via Expo, web apps use Next.js
# =============================================================================
set -euo pipefail

echo "============================================"
echo " Impilo vNext — App Target Verification"
echo "============================================"
echo ""

EXIT_CODE=0

# Mobile apps — must use Expo/React Native
echo "--- Mobile App Targets ---"
for app in apps/mobile/citizen-app apps/mobile/provider-app; do
    name=$(basename "$app")
    pkg="$app/package.json"
    if [ ! -f "$pkg" ]; then
        echo "FAIL: $name — package.json not found"
        EXIT_CODE=1
        continue
    fi

    HAS_EXPO=$(grep -c '"expo"' "$pkg" 2>/dev/null || true)
    HAS_RN=$(grep -c '"react-native"' "$pkg" 2>/dev/null || true)
    HAS_ANDROID=$(grep -c 'android' "$pkg" 2>/dev/null || true)
    HAS_IOS=$(grep -c 'ios' "$pkg" 2>/dev/null || true)

    if [ "$HAS_EXPO" -gt 0 ] && [ "$HAS_RN" -gt 0 ] && [ "$HAS_ANDROID" -gt 0 ] && [ "$HAS_IOS" -gt 0 ]; then
        echo "PASS: $name — Expo + React Native (Android + iOS)"
    else
        echo "WARN: $name — check Expo/RN dependencies (expo=$HAS_EXPO, rn=$HAS_RN, android=$HAS_ANDROID, ios=$HAS_IOS)"
    fi
done

echo ""

# Mobile packages
echo "--- Mobile Shared Packages ---"
for pkg_dir in apps/mobile/packages/*/; do
    name=$(basename "$pkg_dir")
    ts_count=$(find "$pkg_dir" -name "*.ts" -o -name "*.tsx" 2>/dev/null | wc -l)
    if [ "$ts_count" -gt 0 ]; then
        echo "PASS: $name — $ts_count TypeScript files"
    else
        echo "WARN: $name — no TypeScript files found"
    fi
done

echo ""

# Web apps — must use Next.js
echo "--- Web App Targets ---"
for app in ui/*/; do
    name=$(basename "$app")
    pkg="$app/package.json"
    if [ ! -f "$pkg" ]; then
        echo "SKIP: $name — no package.json"
        continue
    fi

    HAS_NEXT=$(grep -c '"next"' "$pkg" 2>/dev/null || true)
    TS_COUNT=$(find "$app/src" -name "*.ts" -o -name "*.tsx" 2>/dev/null | wc -l)

    if [ "$HAS_NEXT" -gt 0 ] && [ "$TS_COUNT" -gt 0 ]; then
        echo "PASS: $name — Next.js ($TS_COUNT files)"
    elif [ "$HAS_NEXT" -gt 0 ] && [ "$TS_COUNT" -eq 0 ]; then
        echo "WARN: $name — Next.js but no source files"
        EXIT_CODE=1
    else
        echo "INFO: $name — not a Next.js app"
    fi
done

echo ""
echo "============================================"
if [ "$EXIT_CODE" -eq 0 ]; then
    echo "RESULT: All app targets aligned"
else
    echo "RESULT: Some apps have misaligned targets"
fi
exit $EXIT_CODE
