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

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=maestro-runtime-orchestration.sh
source "${SCRIPT_DIR}/maestro-runtime-orchestration.sh"

MAESTRO_RUN_COMMIT="${COMMIT}"
maestro_orchestration_init "${REPORTS}"
trap 'maestro_orchestration_finalize $?' EXIT

EMULATOR_BOOT_LOG="${REPORTS}/emulator-boot.log"
EMULATOR_PID_FILE="${REPORTS}/emulator-boot.pid"
ADB_DEVICES_FILE="${REPORTS}/adb-devices.txt"

source ~/.bashrc 2>/dev/null || true
export ANDROID_HOME="${ANDROID_HOME:-/home/facility/android-sdk}"
export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}"

maestro_log_phase "static_closure_rerun begin"
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
maestro_require_nonempty_log "${STATIC_LOG}" "static closure log"
maestro_log_phase "static_closure_rerun complete"

maestro_log_phase "expo_export_citizen begin"
cd "${ROOT}/apps/mobile/citizen-app"
EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL="${API_URL}" \
  npx expo export --platform android 2>&1 | tee "${REPORTS}/expo-export-citizen.log"
maestro_require_nonempty_log "${REPORTS}/expo-export-citizen.log" "citizen expo export log"
grep -q 'Exported: dist' "${REPORTS}/expo-export-citizen.log" \
  || maestro_orchestration_fail "citizen expo export log missing 'Exported: dist'"
[[ -d dist ]] || maestro_orchestration_fail "citizen-app/dist missing after export"
maestro_log_phase "expo_export_citizen complete"

maestro_log_phase "expo_export_provider begin"
cd "${ROOT}/apps/mobile/provider-app"
EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL="${API_URL}" \
  npx expo export --platform android 2>&1 | tee "${REPORTS}/expo-export-provider.log"
maestro_require_nonempty_log "${REPORTS}/expo-export-provider.log" "provider expo export log"
grep -q 'Exported: dist' "${REPORTS}/expo-export-provider.log" \
  || maestro_orchestration_fail "provider expo export log missing 'Exported: dist'"
[[ -d dist ]] || maestro_orchestration_fail "provider-app/dist missing after export"
maestro_log_phase "expo_export_provider complete"

maestro_log_phase "emulator_boot begin avd=${AVD_NAME}"
adb devices >"${ADB_DEVICES_FILE}" 2>&1 || true
pkill -f "emulator.*${AVD_NAME}" 2>/dev/null || true
: >"${EMULATOR_BOOT_LOG}"
nohup emulator -avd "${AVD_NAME}" -no-window -no-audio -gpu swiftshader_indirect -no-boot-anim \
  >>"${EMULATOR_BOOT_LOG}" 2>&1 &
EMU_PID=$!
echo "${EMU_PID}" >"${EMULATOR_PID_FILE}"
maestro_require_nonempty_pidfile "${EMULATOR_PID_FILE}" "emulator pid file"

booted=0
for _ in $(seq 1 120); do
  if ! kill -0 "${EMU_PID}" 2>/dev/null; then
    maestro_require_nonempty_log "${EMULATOR_BOOT_LOG}" "emulator boot log"
    maestro_orchestration_fail "emulator process ${EMU_PID} exited before boot_completed"
  fi
  boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "${boot}" == "1" ]]; then
    booted=1
    break
  fi
  sleep 2
done

maestro_require_nonempty_log "${EMULATOR_BOOT_LOG}" "emulator boot log"
adb devices | tee "${ADB_DEVICES_FILE}"
if [[ "${booted}" -ne 1 ]]; then
  maestro_orchestration_fail "emulator did not reach sys.boot_completed=1 within timeout"
fi
if ! grep -E 'device$' "${ADB_DEVICES_FILE}" | grep -qv 'List of devices'; then
  maestro_orchestration_fail "no adb device registered after emulator boot"
fi
maestro_log_phase "emulator_boot complete"

maestro_log_phase "citizen_runtime begin"
cd "${ROOT}/apps/mobile/citizen-app"
CITIZEN_RUN_LOG="${REPORTS}/citizen-runtime-run.log"
timeout 300 env EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL="${API_URL}" \
  pnpm exec expo run:android --no-bundler 2>&1 | tee "${CITIZEN_RUN_LOG}"
maestro_require_nonempty_log "${CITIZEN_RUN_LOG}" "citizen runtime log"
maestro_log_phase "citizen_runtime complete"

maestro_log_phase "provider_runtime begin"
cd "${ROOT}/apps/mobile/provider-app"
PROVIDER_RUN_LOG="${REPORTS}/provider-runtime-run.log"
timeout 300 env EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL="${API_URL}" \
  pnpm exec expo run:android --no-bundler 2>&1 | tee "${PROVIDER_RUN_LOG}"
maestro_require_nonempty_log "${PROVIDER_RUN_LOG}" "provider runtime log"
maestro_log_phase "provider_runtime complete"

maestro_log_phase "apk_debug_build begin"
for app in citizen-app provider-app; do
  APP_DIR="${ROOT}/apps/mobile/${app}"
  if [[ -d "${APP_DIR}/android" ]]; then
    (cd "${APP_DIR}/android" && ./gradlew assembleDebug 2>&1 | tee "${REPORTS}/${app}-assembleDebug.log")
    maestro_require_nonempty_log "${REPORTS}/${app}-assembleDebug.log" "${app} assembleDebug log"
    APK=$(find "${APP_DIR}/android/app/build/outputs/apk" -name '*debug*.apk' 2>/dev/null | head -1)
    if [[ -n "${APK}" ]]; then
      cp "${APK}" "${ARTIFACTS}/impilo-${app%-app}-preview-${COMMIT}.apk"
    fi
  fi
done
maestro_log_phase "apk_debug_build complete"

maestro_log_phase "screenshot capture"
adb exec-out screencap -p >"${REPORTS}/artifacts/emulator-screenshot-${COMMIT}.png" 2>/dev/null \
  || maestro_orchestration_fail "failed to capture emulator screenshot"

if command -v maestro >/dev/null 2>&1; then
  maestro_log_phase "maestro_flows begin"
  bash "${ROOT}/scripts/mobile/verify-maestro-flows.sh" 2>&1 | tee "${REPORTS}/maestro-flows.log"
  maestro_require_nonempty_log "${REPORTS}/maestro-flows.log" "maestro flows log"
  maestro_log_phase "maestro_flows complete"
fi

kill "${EMU_PID}" 2>/dev/null || true
MAESTRO_RUN_STATUS="completed"
maestro_log_phase "runtime_closure finished successfully"
