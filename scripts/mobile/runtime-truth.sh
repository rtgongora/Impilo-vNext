#!/usr/bin/env bash
# runtime-truth.sh — VM runbook for proving the Impilo mobile apps at runtime.
#
# Installs the citizen + provider debug APKs on a connected Android
# device/emulator, launches each app, waits for boot, optionally runs the
# Maestro login/journey flows (when MAESTRO_* credentials are exported;
# otherwise the unauthenticated tier1 smokes), and collects evidence
# (adb logcat excerpts, maestro output, screenshots) into
# reports/mobile/runtime-truth/<timestamp>/.
#
# Usage:
#   scripts/mobile/runtime-truth.sh [citizen-apk] [provider-apk]
#     APK paths default to auto-discovery under
#     apps/mobile/*/android/app/build/outputs/apk/debug/.
#
# Optional env (Maestro forwards MAESTRO_* vars into flows automatically):
#   MAESTRO_CITIZEN_USERNAME / MAESTRO_CITIZEN_PASSWORD
#   MAESTRO_PROVIDER_USERNAME / MAESTRO_PROVIDER_PASSWORD
#   MAESTRO_PROVIDER_ID / MAESTRO_PATIENT_QUERY
#
# Verdict enum (per app, printed on the final lines):
#   PROVEN_RUNTIME | BUILDS_ONLY | INSTALLS_BUT_BLOCKED | AUTH_BLOCKED |
#   PREVIEW_CONNECTIVITY_BLOCKED | NOT_PROVEN
#
# Idempotent: re-running reinstalls (-r) and writes a fresh report dir.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
OUT="${ROOT}/reports/mobile/runtime-truth/${TS}"
mkdir -p "${OUT}"

CITIZEN_PKG="zw.gov.impilo.citizen"
PROVIDER_PKG="zw.gov.impilo.provider"
BOOT_WAIT_SECS="${BOOT_WAIT_SECS:-45}"
PREVIEW_HOST="${PREVIEW_HOST:-impilo.mohcc.gov.zw}"

log() { printf '[runtime-truth] %s\n' "$*" | tee -a "${OUT}/run.log"; }

fail_all() {
  log "FATAL: $1"
  echo "VERDICT citizen-app: NOT_PROVEN ($1)" | tee -a "${OUT}/run.log"
  echo "VERDICT provider-app: NOT_PROVEN ($1)" | tee -a "${OUT}/run.log"
  exit 1
}

# --- Preconditions -----------------------------------------------------------
command -v adb >/dev/null 2>&1 || fail_all "adb not found on PATH (install Android platform-tools)"

DEVICE_COUNT="$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)"
adb devices > "${OUT}/adb-devices.txt" 2>&1
if [ "${DEVICE_COUNT}" -lt 1 ]; then
  fail_all "no Android device/emulator in 'device' state (see adb-devices.txt)"
fi
log "connected devices: ${DEVICE_COUNT}"

HAVE_MAESTRO=0
if command -v maestro >/dev/null 2>&1; then HAVE_MAESTRO=1; fi

HAVE_CREDS=0
if [ -n "${MAESTRO_CITIZEN_USERNAME:-}" ] && [ -n "${MAESTRO_CITIZEN_PASSWORD:-}" ] \
   && [ -n "${MAESTRO_PROVIDER_USERNAME:-}" ] && [ -n "${MAESTRO_PROVIDER_PASSWORD:-}" ]; then
  HAVE_CREDS=1
fi

# --- APK discovery -----------------------------------------------------------
find_apk() { # $1 = app dir name
  find "${ROOT}/apps/mobile/$1/android/app/build/outputs/apk/debug" \
    -name '*.apk' -type f 2>/dev/null | head -n 1
}
CITIZEN_APK="${1:-$(find_apk citizen-app)}"
PROVIDER_APK="${2:-$(find_apk provider-app)}"
[ -n "${CITIZEN_APK}" ] && [ -f "${CITIZEN_APK}" ] || fail_all "citizen APK not found (pass as arg 1 or build assembleDebug)"
[ -n "${PROVIDER_APK}" ] && [ -f "${PROVIDER_APK}" ] || fail_all "provider APK not found (pass as arg 2 or build assembleDebug)"
log "citizen APK:  ${CITIZEN_APK} ($(sha256sum "${CITIZEN_APK}" | cut -d' ' -f1))"
log "provider APK: ${PROVIDER_APK} ($(sha256sum "${PROVIDER_APK}" | cut -d' ' -f1))"

# --- Preview reachability (from the workstation, advisory only) --------------
PREVIEW_REACHABLE=0
if command -v curl >/dev/null 2>&1 && curl -fsS -m 10 -o /dev/null "http://${PREVIEW_HOST}:8480/realms/impilo/.well-known/openid-configuration" 2>>"${OUT}/run.log"; then
  PREVIEW_REACHABLE=1
  log "preview Keycloak reachable at http://${PREVIEW_HOST}:8480"
else
  log "WARNING: preview Keycloak NOT reachable from this host (device may still differ)"
fi

# --- Per-app runtime check ---------------------------------------------------
# check_app <label> <apk> <pkg> <maestro-flow-authed> <maestro-flow-smoke>
check_app() {
  label="$1"; apk="$2"; pkg="$3"; flow_auth="$4"; flow_smoke="$5"
  verdict="NOT_PROVEN"; detail=""

  log "--- ${label}: install ---"
  if adb install -r "${apk}" >> "${OUT}/${label}-install.log" 2>&1; then
    log "${label}: installed"
  else
    log "${label}: INSTALL FAILED (see ${label}-install.log)"
    echo "VERDICT ${label}: BUILDS_ONLY (APK exists but does not install on this device)" | tee -a "${OUT}/run.log"
    return
  fi

  adb logcat -c || true
  log "--- ${label}: launch ---"
  adb shell monkey -p "${pkg}" -c android.intent.category.LAUNCHER 1 >> "${OUT}/${label}-launch.log" 2>&1
  sleep "${BOOT_WAIT_SECS}"

  adb shell screencap -p "/sdcard/${label}-boot.png" 2>>"${OUT}/run.log" \
    && adb pull "/sdcard/${label}-boot.png" "${OUT}/" >/dev/null 2>&1 \
    && adb shell rm -f "/sdcard/${label}-boot.png" || true
  adb logcat -d > "${OUT}/${label}-logcat-full.txt" 2>&1
  grep -Ei "FATAL|AndroidRuntime|${pkg}" "${OUT}/${label}-logcat-full.txt" | tail -n 200 > "${OUT}/${label}-logcat-excerpt.txt" || true

  pid="$(adb shell pidof "${pkg}" 2>/dev/null | tr -d '\r')"
  if [ -z "${pid}" ]; then
    if grep -q "FATAL EXCEPTION" "${OUT}/${label}-logcat-full.txt"; then
      detail="app crashed on boot (FATAL EXCEPTION in logcat excerpt)"
    else
      detail="process not running after ${BOOT_WAIT_SECS}s"
    fi
    echo "VERDICT ${label}: INSTALLS_BUT_BLOCKED (${detail})" | tee -a "${OUT}/run.log"
    return
  fi
  log "${label}: process running (pid ${pid})"
  verdict="INSTALLS_BUT_BLOCKED"; detail="launched; no flow evidence yet"

  if [ "${HAVE_MAESTRO}" -eq 1 ]; then
    if [ "${HAVE_CREDS}" -eq 1 ]; then
      flow="${ROOT}/apps/mobile/maestro/flows/${flow_auth}"
    else
      flow="${ROOT}/apps/mobile/maestro/flows/${flow_smoke}"
    fi
    log "--- ${label}: maestro $(basename "${flow}") ---"
    if maestro test "${flow}" > "${OUT}/${label}-maestro.log" 2>&1; then
      if [ "${HAVE_CREDS}" -eq 1 ]; then
        verdict="PROVEN_RUNTIME"; detail="login+journey flow passed against preview"
      else
        verdict="PROVEN_RUNTIME"; detail="unauthenticated tier1 smoke passed (no credentials exported; login unproven)"
      fi
    else
      if [ "${HAVE_CREDS}" -eq 1 ]; then
        if grep -qi "Username or email" "${OUT}/${label}-maestro.log"; then
          verdict="AUTH_BLOCKED"; detail="reached Keycloak form but flow failed (see ${label}-maestro.log)"
        elif [ "${PREVIEW_REACHABLE}" -eq 0 ]; then
          verdict="PREVIEW_CONNECTIVITY_BLOCKED"; detail="flow failed and preview is unreachable"
        else
          verdict="AUTH_BLOCKED"; detail="authenticated flow failed (see ${label}-maestro.log)"
        fi
      else
        verdict="INSTALLS_BUT_BLOCKED"; detail="tier1 smoke failed (see ${label}-maestro.log)"
      fi
    fi
    adb shell screencap -p "/sdcard/${label}-final.png" 2>>"${OUT}/run.log" \
      && adb pull "/sdcard/${label}-final.png" "${OUT}/" >/dev/null 2>&1 \
      && adb shell rm -f "/sdcard/${label}-final.png" || true
  else
    log "${label}: maestro not installed — launch-only evidence"
    detail="launched OK; install maestro for flow-level proof"
  fi

  echo "VERDICT ${label}: ${verdict} (${detail})" | tee -a "${OUT}/run.log"
}

check_app "citizen-app"  "${CITIZEN_APK}"  "${CITIZEN_PKG}"  "citizen-journey-smoke.yaml"  "citizen-tier1-smoke.yaml"
check_app "provider-app" "${PROVIDER_APK}" "${PROVIDER_PKG}" "provider-journey-smoke.yaml" "provider-tier1-smoke.yaml"

log "evidence collected in ${OUT}"
log "send back: run.log, VERDICT lines, *-maestro.log, *-logcat-excerpt.txt, *.png"
