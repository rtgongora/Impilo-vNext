#!/usr/bin/env bash
# redroid-runtime.sh — drive the redroid Android-in-container fixture.
#
# The redroid pod (deploy/helm/impilo-vnext/templates/redroid.yaml) is a
# standing adb device: this script connects to it, installs the packaged
# preview APKs, runs the runtime-verified Maestro flows, and collects
# screenshot evidence. Requires: adb (scripts/mobile/android-env.sh),
# kubectl access to the preview cluster, maestro (~/.maestro/bin) for smoke.
#
# usage: redroid-runtime.sh <connect|install|smoke|evidence|all> [artifact-dir]
set -euo pipefail

NS="${REDROID_NAMESPACE:-impilo-full-preview}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ART="${2:-$(ls -d "$REPO_ROOT"/artifacts/mobile/*/ 2>/dev/null | sort | tail -1)}"
BOOT_TIMEOUT="${REDROID_BOOT_TIMEOUT:-600}"

die() { echo "redroid-runtime: $*" >&2; exit 1; }

adb_bin() { command -v adb >/dev/null || die "adb not on PATH — source scripts/mobile/android-env.sh"; }

redroid_addr() {
  # Resolution order: explicit override → docker fixture (primary) → k8s svc.
  if [ -n "${REDROID_ADDR:-}" ]; then echo "$REDROID_ADDR"; return; fi
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "${REDROID_NAME:-impilo-redroid}"; then
    echo "127.0.0.1:${REDROID_PORT:-15555}"; return
  fi
  local ip
  ip=$(kubectl -n "$NS" get svc redroid -o jsonpath='{.spec.clusterIP}' 2>/dev/null) \
    || die "no redroid found: start the docker fixture (scripts/mobile/redroid-docker-fixture.sh start) or deploy the chart"
  echo "${ip}:5555"
}

cmd_connect() {
  adb_bin
  local addr; addr=$(redroid_addr)
  echo "==> adb connect $addr"
  adb connect "$addr" | grep -v "^already" || true
  export ANDROID_SERIAL="$addr"
  echo "==> waiting for sys.boot_completed (timeout ${BOOT_TIMEOUT}s)"
  local start=$SECONDS
  until [ "$(adb -s "$addr" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    [ $((SECONDS - start)) -gt "$BOOT_TIMEOUT" ] && die "boot timeout after ${BOOT_TIMEOUT}s"
    sleep 10
  done
  echo "==> redroid booted ($addr)"
}

cmd_install() {
  adb_bin
  local addr; addr=$(redroid_addr)
  [ -d "$ART" ] || die "artifact dir not found: $ART (build APKs first: pnpm mobile:build)"
  local c p
  c=$(ls "$ART"/Impilo-preview-*.apk 2>/dev/null | head -1) || true
  p=$(ls "$ART"/Impilo-Provider-preview-*.apk 2>/dev/null | head -1) || true
  [ -n "$c" ] || die "citizen APK missing in $ART"
  [ -n "$p" ] || die "provider APK missing in $ART"
  for apk in "$c" "$p"; do
    local base; base=$(basename "$apk")
    echo "==> installing $base"
    adb -s "$addr" push "$apk" "/data/local/tmp/$base" >/dev/null
    adb -s "$addr" shell pm install -r -t "/data/local/tmp/$base" | tail -1
  done
  adb -s "$addr" shell pm list packages zw.gov.impilo
}

cmd_smoke() {
  local addr; addr=$(redroid_addr)
  command -v maestro >/dev/null 2>&1 || export PATH="$HOME/.maestro/bin:$PATH"
  command -v maestro >/dev/null 2>&1 || die "maestro not installed (curl -Ls https://get.maestro.mobile.dev | bash)"
  local out="$ART/redroid/maestro"
  mkdir -p "$out"
  local rc=0
  for flow in "$REPO_ROOT"/apps/mobile/maestro/flows-runtime/*.yaml; do
    echo "==> maestro flow: $(basename "$flow")"
    MAESTRO_HEALTH_QUERY="${MAESTRO_HEALTH_QUERY:-antenatal}" \
    MAESTRO_DRIVER_STARTUP_TIMEOUT="${MAESTRO_DRIVER_STARTUP_TIMEOUT:-300000}" \
      maestro --device "$addr" test --debug-output "$out" "$flow" || rc=1
  done
  return $rc
}

cmd_evidence() {
  adb_bin
  local addr; addr=$(redroid_addr)
  local out="$ART/redroid/screenshots"
  mkdir -p "$out"
  for pkg in zw.gov.impilo.citizen.dev zw.gov.impilo.provider.dev; do
    echo "==> launching $pkg"
    adb -s "$addr" shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
    sleep 45
    adb -s "$addr" exec-out screencap -p > "$out/${pkg##*.dev}-launch-$(basename "$pkg" | cut -d. -f4)-$(date -u +%H%M%S 2>/dev/null || echo now).png" || true
  done
  ls -la "$out"
}

case "${1:-}" in
  connect)  cmd_connect ;;
  install)  cmd_connect; cmd_install ;;
  smoke)    cmd_connect; cmd_smoke ;;
  evidence) cmd_connect; cmd_evidence ;;
  all)      cmd_connect; cmd_install; cmd_smoke; cmd_evidence ;;
  *) echo "usage: $0 <connect|install|smoke|evidence|all> [artifact-dir]" >&2; exit 2 ;;
esac
