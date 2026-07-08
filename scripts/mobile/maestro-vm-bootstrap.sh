#!/usr/bin/env bash
# Maestro VM (218) bootstrap: base toolchain + Android SDK + AVD + repo sync.
# Run ON impilo-mobile-android-sandbox: ssh facility@41.57.127.218 -p 2027
set -euo pipefail

BRANCH="${IMPILO_BRANCH:-claude/staging-ux-orchestration-remediation-Yypyl}"
REPO_DIR="${IMPILO_REPO_DIR:-/opt/impilo/repos/Impilo-vNext}"
ANDROID_SDK="${ANDROID_HOME:-/home/facility/android-sdk}"
REPORTS="${REPO_DIR}/reports/mobile"
PNPM_VERSION="${PNPM_VERSION:-9.15.0}"
API_LEVEL="${ANDROID_API_LEVEL:-35}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=maestro-runtime-orchestration.sh
source "${SCRIPT_DIR}/maestro-runtime-orchestration.sh"

MAESTRO_RUN_COMMIT="bootstrap"
maestro_orchestration_init "${REPORTS}"
trap 'maestro_orchestration_finalize $?' EXIT

log() { maestro_log_phase "$*"; }
die() { maestro_orchestration_fail "$*"; }

log "Phase 0: KVM sanity"
bash "${REPO_DIR}/scripts/mobile/verify-maestro-vm-kvm.sh" 2>/dev/null || {
  egrep -c '(vmx|svm)' /proc/cpuinfo
  test -r /dev/kvm && test -w /dev/kvm || die "KVM not usable"
}

log "Phase 1: base packages"
sudo apt-get update
sudo apt-get install -y git curl ca-certificates gnupg unzip zip build-essential openjdk-17-jdk \
  libpulse0 libnss3 libxcomposite1 libxdamage1 libxrandr2 libasound2t64 libatk-bridge2.0-0 libgtk-3-0

if ! command -v node >/dev/null 2>&1 || [[ "$(node -v)" != v20* ]]; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
  sudo apt-get install -y nodejs
fi
sudo corepack enable || true
corepack prepare "pnpm@${PNPM_VERSION}" --activate

log "Phase 2: Android SDK at ${ANDROID_SDK}"
mkdir -p "${ANDROID_SDK}/cmdline-tools"
if [[ ! -d "${ANDROID_SDK}/cmdline-tools/latest" ]]; then
  TMP=$(mktemp -d)
  curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o "${TMP}/cmdline-tools.zip"
  unzip -q "${TMP}/cmdline-tools.zip" -d "${TMP}"
  mv "${TMP}/cmdline-tools" "${ANDROID_SDK}/cmdline-tools/latest"
  rm -rf "${TMP}"
fi

export ANDROID_HOME="${ANDROID_SDK}"
export ANDROID_SDK_ROOT="${ANDROID_SDK}"
export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"

yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager --install \
  "platform-tools" \
  "emulator" \
  "platforms;android-${API_LEVEL}" \
  "build-tools;${API_LEVEL}.0.0" \
  "system-images;android-${API_LEVEL};google_apis;x86_64"

log "Phase 3: AVD impilo-phone-api35"
if ! avdmanager list avd 2>/dev/null | grep -q impilo-phone-api35; then
  echo no | avdmanager create avd -n impilo-phone-api35 -k "system-images;android-${API_LEVEL};google_apis;x86_64" \
    -d pixel_6 --force
fi

AVD_CONFIG="${HOME}/.android/avd/impilo-phone-api35.avd/config.ini"
if [[ -f "${AVD_CONFIG}" && ! -s "${AVD_CONFIG}" ]]; then
  die "AVD config corrupted (0 bytes): ${AVD_CONFIG}"
fi

log "Phase 4: persist env in ~/.bashrc"
grep -q ANDROID_HOME ~/.bashrc 2>/dev/null || cat >>~/.bashrc <<EOF

# Impilo Maestro Android SDK
export ANDROID_HOME="${ANDROID_SDK}"
export ANDROID_SDK_ROOT="${ANDROID_SDK}"
export JAVA_HOME="${JAVA_HOME}"
export PATH="\${ANDROID_HOME}/cmdline-tools/latest/bin:\${ANDROID_HOME}/platform-tools:\${ANDROID_HOME}/emulator:\${PATH}"
export EXPO_PUBLIC_API_BASE_URL=https://impilo.mohcc.gov.zw
EOF

log "Phase 5: repo sync"
sudo mkdir -p "$(dirname "${REPO_DIR}")" 2>/dev/null || true
if [[ ! -d "${REPO_DIR}/.git" ]]; then
  if [[ -w "$(dirname "${REPO_DIR}")" ]]; then
    git clone https://github.com/rtgongora/Impilo-vNext.git "${REPO_DIR}"
  else
    REPO_DIR="${HOME}/impilo/repos/Impilo-vNext"
    mkdir -p "$(dirname "${REPO_DIR}")"
    git clone https://github.com/rtgongora/Impilo-vNext.git "${REPO_DIR}"
  fi
fi
cd "${REPO_DIR}"
git fetch origin
git checkout "${BRANCH}"
git pull origin "${BRANCH}"

log "Phase 6: mobile deps"
cd "${REPO_DIR}/apps/mobile"
pnpm install --frozen-lockfile

log "Bootstrap complete. Versions:"
git --version; java -version 2>&1 | head -1; node --version; pnpm --version
sdkmanager --version; adb version 2>&1 | head -1; emulator -version 2>&1 | head -1
avdmanager list avd
git -C "${REPO_DIR}" rev-parse --short HEAD

MAESTRO_RUN_STATUS="completed"
log "Next: bash scripts/mobile/maestro-vm-runtime-closure.sh"
