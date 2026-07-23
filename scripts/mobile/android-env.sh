#!/usr/bin/env bash
# Android build environment for Impilo mobile apps (source this file).
#
#   source scripts/mobile/android-env.sh
#
# Assumes the per-user SDK layout provisioned by the mobile recovery
# (2026-07-23): Android SDK at ~/Android/Sdk, Maestro at ~/.maestro,
# pnpm via corepack shim at ~/.local/bin. JDK 17+ required (Temurin 21
# is what the estate hosts carry).

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

if [ -z "${JAVA_HOME:-}" ] && command -v javac >/dev/null 2>&1; then
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
  export JAVA_HOME
fi

export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$HOME/.maestro/bin:$HOME/.local/bin:$PATH"

# Keep Gradle daemons small enough to coexist with the preview stack on
# shared hosts; individual invocations may override.
export GRADLE_OPTS="${GRADLE_OPTS:--Dorg.gradle.jvmargs='-Xmx4g -XX:MaxMetaspaceSize=1g'}"
export COREPACK_ENABLE_DOWNLOAD_PROMPT=0
