#!/usr/bin/env bash
# Emulator-only Maestro runtime phase — static gates and Expo export assumed closed.
# Use only after ANDROID_EMULATOR_SANDBOX_PROVISIONING_BLOCKER is remediated.
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

AVD_CONFIG="${HOME}/.android/avd/${AVD_NAME}.avd/config.ini"
if [[ -f "${AVD_CONFIG}" && ! -s "${AVD_CONFIG}" ]]; then
  maestro_orchestration_fail "AVD config corrupted (0 bytes): ${AVD_CONFIG}"
fi

maestro_log_phase "emulator_only_boot begin avd=${AVD_NAME}"
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

maestro_log_phase "screenshot capture"
mkdir -p "${ARTIFACTS}"
adb exec-out screencap -p >"${ARTIFACTS}/emulator-screenshot-${COMMIT}.png" 2>/dev/null \
  || maestro_orchestration_fail "failed to capture emulator screenshot"

kill "${EMU_PID}" 2>/dev/null || true
MAESTRO_RUN_STATUS="completed"
maestro_log_phase "emulator_only_boot finished successfully"
