#!/usr/bin/env bash
# Build installable preview APKs for the Impilo mobile apps.
#
#   scripts/mobile/build-apks.sh citizen|provider|all [--abis <list>]
#
# Builds :app:assembleRelease of the committed android/ project (release is
# signed with the committed debug keystore, so the output is directly
# installable and carries the bundled Hermes JS — assembleDebug does NOT
# bundle JS and needs Metro). EXPO_PUBLIC_* preview endpoints are exported
# before Gradle runs and are inlined into the JS bundle by Metro during
# createBundleReleaseJsAndAssets.
#
# Outputs land in artifacts/mobile/<git-commit>/ as
#   Impilo-preview-<version>-<shortsha>.apk
#   Impilo-Provider-preview-<version>-<shortsha>.apk
# plus SHA256SUMS and per-app build logs. Fails non-zero if any requested
# app fails to build or verify.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=/dev/null
source "$REPO_ROOT/scripts/mobile/android-env.sh"

TARGET="${1:-all}"
ABIS="arm64-v8a,x86_64"
if [ "${2:-}" = "--abis" ] && [ -n "${3:-}" ]; then ABIS="$3"; fi

GIT_SHA="$(git -C "$REPO_ROOT" rev-parse HEAD)"
SHORT_SHA="$(git -C "$REPO_ROOT" rev-parse --short HEAD)"
ART_DIR="$REPO_ROOT/artifacts/mobile/$GIT_SHA"
mkdir -p "$ART_DIR/build-logs"

# Preview environment baked into the bundle. KEYCLOAK_URL deliberately has no
# :8480 port — Keycloak is served under /realms on the public edge (see
# deploy/tls/mohcc-gov/ingressroutes.yaml), the in-config :8480 default is dead.
PREVIEW_API_BASE_URL="${PREVIEW_API_BASE_URL:-https://impilo.mohcc.gov.zw}"
PREVIEW_KEYCLOAK_URL="${PREVIEW_KEYCLOAK_URL:-https://impilo.mohcc.gov.zw}"

build_one() {
  local app_dir="$1" client_id="$2" app_label="$3" out_name="$4"
  local android_dir="$REPO_ROOT/apps/mobile/$app_dir/android"
  local log="$ART_DIR/build-logs/$app_dir-assembleRelease.log"

  echo "==> [$app_label] :app:assembleRelease (ABIs: $ABIS)"
  (
    export EXPO_PUBLIC_ENV=preview \
           EXPO_PUBLIC_APP_VARIANT=preview \
           EXPO_PUBLIC_API_BASE_URL="$PREVIEW_API_BASE_URL" \
           EXPO_PUBLIC_KEYCLOAK_URL="$PREVIEW_KEYCLOAK_URL" \
           EXPO_PUBLIC_AUTH_BASE_URL="$PREVIEW_KEYCLOAK_URL" \
           EXPO_PUBLIC_KEYCLOAK_REALM=impilo \
           EXPO_PUBLIC_KEYCLOAK_CLIENT_ID="$client_id" \
           EXPO_PUBLIC_WEB_BASE_URL="$PREVIEW_API_BASE_URL"
    cd "$android_dir"
    nice -n 10 ionice -c2 -n7 ./gradlew :app:assembleRelease \
      --max-workers="${GRADLE_MAX_WORKERS:-8}" \
      -PreactNativeArchitectures="$ABIS" \
      -Porg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"
  ) 2>&1 | tee "$log" | grep -E "BUILD|FAILURE|error|warning: package" | tail -20 || true

  local apk="$android_dir/app/build/outputs/apk/release/app-release.apk"
  if [ ! -f "$apk" ]; then
    echo "ERROR: [$app_label] build produced no APK (see $log)" >&2
    return 1
  fi

  local version
  version="$("$REPO_ROOT/scripts/mobile/verify-apk.sh" "$apk" "$PREVIEW_API_BASE_URL")" || {
    echo "ERROR: [$app_label] APK failed verification" >&2
    return 1
  }

  local dest="$ART_DIR/$out_name-preview-$version-$SHORT_SHA.apk"
  cp "$apk" "$dest"
  echo "==> [$app_label] APK: $dest"
}

FAILED=0
case "$TARGET" in
  citizen)  build_one citizen-app  impilo-mobile-citizen  "Impilo"          "Impilo" || FAILED=1 ;;
  provider) build_one provider-app impilo-mobile-provider "Impilo Provider" "Impilo-Provider" || FAILED=1 ;;
  all)
    build_one citizen-app  impilo-mobile-citizen  "Impilo"          "Impilo" || FAILED=1
    build_one provider-app impilo-mobile-provider "Impilo Provider" "Impilo-Provider" || FAILED=1
    ;;
  *) echo "usage: $0 citizen|provider|all [--abis <list>]" >&2; exit 2 ;;
esac

( cd "$ART_DIR" && sha256sum ./*.apk > SHA256SUMS 2>/dev/null ) || true
[ -f "$ART_DIR/SHA256SUMS" ] && { echo "==> SHA256SUMS:"; cat "$ART_DIR/SHA256SUMS"; }

exit $FAILED
