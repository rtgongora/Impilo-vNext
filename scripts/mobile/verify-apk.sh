#!/usr/bin/env bash
# Verify an Impilo mobile APK is a real, standalone, preview-pointed build.
#
#   scripts/mobile/verify-apk.sh <path-to-apk> [expected-base-url]
#
# Checks: aapt2 badging parses (package id + version), the Hermes JS bundle
# is present (standalone — no Metro needed), native libs exist, and the
# expected API base URL is inlined in the bundle. Prints the versionName on
# success; exits non-zero on any failure.
set -euo pipefail

APK="${1:?usage: verify-apk.sh <apk> [expected-base-url]}"
EXPECT_URL="${2:-}"

AAPT2="$(ls "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
[ -n "$AAPT2" ] || { echo "verify-apk: aapt2 not found under ANDROID_HOME" >&2; exit 1; }

BADGING="$("$AAPT2" dump badging "$APK")"
PKG="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$BADGING")"
VERSION="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$BADGING" | head -1)"
[ -n "$PKG" ] && [ -n "$VERSION" ] || { echo "verify-apk: badging missing package/version" >&2; exit 1; }

unzip -l "$APK" | grep -q "assets/index.android.bundle" \
  || { echo "verify-apk: $PKG has no bundled JS (assets/index.android.bundle missing) — not standalone" >&2; exit 1; }
unzip -l "$APK" | grep -q "libhermes" \
  || { echo "verify-apk: $PKG missing Hermes runtime libs" >&2; exit 1; }

if [ -n "$EXPECT_URL" ]; then
  HOST="${EXPECT_URL#*://}"; HOST="${HOST%%/*}"
  unzip -p "$APK" assets/index.android.bundle | strings | grep -qm1 "$HOST" \
    || { echo "verify-apk: expected base URL host '$HOST' not found in JS bundle" >&2; exit 1; }
fi

{
  echo "verify-apk OK: $PKG versionName=$VERSION"
  grep -E "^package:" <<<"$BADGING"
} >&2
echo "$VERSION"
