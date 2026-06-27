#!/usr/bin/env bash
# Maestro VM runtime closure: static gates, emulator boot, runtime smoke, APK attempt.
# Prerequisites: maestro-vm-bootstrap.sh completed on 218.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REPORTS="${ROOT}/reports/mobile"
ARTIFACTS="${REPORTS}/artifacts/android"
AVD_NAME="${IMPILO_AVD:-impilo-phone-api35}"
COMMIT="$(git -C "${ROOT}" rev-parse --short HEAD)"
API_URL="${EXPO_PUBLIC_API_BASE_URL:-http://41.57.127.235}"
export EXPO_PUBLIC_API_BASE_URL="${API_URL}"
export EXPO_PUBLIC_APP_VARIANT="${EXPO_PUBLIC_APP_VARIANT:-preview}"

mkdir -p "${REPORTS}" "${ARTIFACTS}"
log() { echo "[maestro-runtime] $*"; }

source ~/.bashrc 2>/dev/null || true
export ANDROID_HOME="${ANDROID_HOME:-/home/facility/android-sdk}"
export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}"

log "Static closure rerun"
cd "${ROOT}/apps/mobile"
STATIC_LOG="${REPORTS}/maestro-mobile-closure-rerun.log"
{
  echo "commit=${COMMIT}"
  echo "=== typecheck ==="
  pnpm mobile:typecheck
  echo "=== tests ==="
  pnpm mobile:test
  echo "=== guard:mobile-parity ==="
  pnpm guard:mobile-parity
} 2>&1 | tee "${STATIC_LOG}"

log "Expo export citizen"
cd "${ROOT}/apps/mobile/citizen-app"
EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL="${API_URL}" \
  npx expo export --platform android 2>&1 | tee "${REPORTS}/expo-export-citizen.log" || true

log "Expo export provider"
cd "${ROOT}/apps/mobile/provider-app"
EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL="${API_URL}" \
  npx expo export --platform android 2>&1 | tee "${REPORTS}/expo-export-provider.log" || true

log "Boot emulator headless"
adb devices
pkill -f "emulator.*${AVD_NAME}" 2>/dev/null || true
nohup emulator -avd "${AVD_NAME}" -no-window -no-audio -gpu swiftshader_indirect -no-boot-anim \
  > "${REPORTS}/emulator-boot.log" 2>&1 &
EMU_PID=$!
for i in $(seq 1 120); do
  boot=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  [[ "${boot}" == "1" ]] && break
  sleep 2
done
adb devices | tee "${REPORTS}/adb-devices.txt"
adb shell getprop ro.build.version.release 2>/dev/null | tee "${REPORTS}/android-release.txt" || true

log "Citizen runtime (expo run:android)"
cd "${ROOT}/apps/mobile/citizen-app"
timeout 300 env EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL="${API_URL}" \
  pnpm exec expo run:android --no-bundler 2>&1 | tee "${REPORTS}/citizen-runtime-run.log" || true

log "Provider runtime (expo run:android)"
cd "${ROOT}/apps/mobile/provider-app"
timeout 300 env EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL="${API_URL}" \
  pnpm exec expo run:android --no-bundler 2>&1 | tee "${REPORTS}/provider-runtime-run.log" || true

log "APK debug build attempt"
for app in citizen-app provider-app; do
  APP_DIR="${ROOT}/apps/mobile/${app}"
  if [[ -d "${APP_DIR}/android" ]]; then
    (cd "${APP_DIR}/android" && ./gradlew assembleDebug 2>&1 | tee "${REPORTS}/${app}-assembleDebug.log") || true
    APK=$(find "${APP_DIR}/android/app/build/outputs/apk" -name '*debug*.apk' 2>/dev/null | head -1)
    if [[ -n "${APK}" ]]; then
      cp "${APK}" "${ARTIFACTS}/impilo-${app%-app}-preview-${COMMIT}.apk"
    fi
  fi
done

log "Screenshots"
adb exec-out screencap -p > "${REPORTS}/artifacts/emulator-screenshot-${COMMIT}.png" 2>/dev/null || true

log "Maestro (if installed)"
if command -v maestro >/dev/null 2>&1; then
  bash "${ROOT}/scripts/mobile/verify-maestro-flows.sh" 2>&1 | tee "${REPORTS}/maestro-flows.log" || true
fi

kill "${EMU_PID}" 2>/dev/null || true
log "Runtime closure script finished. Update reports/mobile/*.md from logs."
