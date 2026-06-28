#!/usr/bin/env bash
# Shared orchestration helpers for Maestro mobile runtime scripts.
# Source from bootstrap/runtime closure: source "$(dirname "$0")/maestro-runtime-orchestration.sh"
#
# Guarantees:
#   - RUN_STARTED marker written before any substantive work
#   - Empty expected phase/log/pid files => orchestration failure + formal report
#   - Nonzero or early exit => formal blocker report (transient logs stay gitignored)

maestro_orchestration_init() {
  local reports_dir="${1:?reports dir required}"
  MAESTRO_REPORTS="${reports_dir}"
  MAESTRO_RUN_MARKER="${MAESTRO_REPORTS}/maestro-runtime-run.marker"
  MAESTRO_PHASE_LOG="${MAESTRO_REPORTS}/maestro-runtime-phase.log"
  MAESTRO_FAILURE_REPORT_WRITTEN=0
  MAESTRO_RUN_STATUS="running"
  MAESTRO_RUN_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  MAESTRO_RUN_HOST="$(hostname 2>/dev/null || echo unknown)"
  MAESTRO_RUN_USER="$(whoami 2>/dev/null || echo unknown)"
  MAESTRO_RUN_COMMIT="${MAESTRO_RUN_COMMIT:-unknown}"

  mkdir -p "${MAESTRO_REPORTS}"
  {
    echo "RUN_STARTED"
    echo "started_at=${MAESTRO_RUN_STARTED_AT}"
    echo "host=${MAESTRO_RUN_HOST}"
    echo "user=${MAESTRO_RUN_USER}"
    echo "commit=${MAESTRO_RUN_COMMIT}"
    echo "pid=$$"
  } >"${MAESTRO_RUN_MARKER}"

  echo "[${MAESTRO_RUN_STARTED_AT}] RUN_STARTED host=${MAESTRO_RUN_HOST} user=${MAESTRO_RUN_USER} commit=${MAESTRO_RUN_COMMIT}" \
    >>"${MAESTRO_PHASE_LOG}"
}

maestro_log_phase() {
  local msg="$*"
  local ts
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "[${ts}] ${msg}" >>"${MAESTRO_PHASE_LOG}"
  echo "[maestro-runtime] ${msg}"
}

maestro_require_nonempty_log() {
  local logfile="$1"
  local label="${2:-log}"
  if [[ ! -s "${logfile}" ]]; then
    maestro_orchestration_fail \
      "orchestration_failure: expected non-empty ${label} at ${logfile}"
  fi
}

maestro_require_nonempty_pidfile() {
  local pidfile="$1"
  local label="${2:-pid file}"
  if [[ ! -s "${pidfile}" ]]; then
    maestro_orchestration_fail \
      "orchestration_failure: expected non-empty ${label} at ${pidfile}"
  fi
}

maestro_write_failure_report() {
  local reason="$1"
  local ts
  ts="$(date -u +%Y%m%dT%H%M%SZ)"
  local report="${MAESTRO_REPORTS}/android-emulator-sandbox-provisioning-blocker-${ts}.md"

  if [[ "${MAESTRO_FAILURE_REPORT_WRITTEN}" -eq 1 ]]; then
    return 0
  fi
  MAESTRO_FAILURE_REPORT_WRITTEN=1

  cat >"${report}" <<EOF
# Android Emulator Sandbox Provisioning Blocker

**Classification:** \`ANDROID_EMULATOR_SANDBOX_PROVISIONING_BLOCKER\`  
**Generated:** ${MAESTRO_RUN_STARTED_AT} (UTC)  
**Host:** ${MAESTRO_RUN_HOST}  
**User:** ${MAESTRO_RUN_USER}  
**Commit:** ${MAESTRO_RUN_COMMIT}  
**Run marker:** \`${MAESTRO_RUN_MARKER}\`

## Failure reason

${reason}

## Orchestration evidence

| Artifact | Path | Status |
|----------|------|--------|
| Run marker | \`${MAESTRO_RUN_MARKER}\` | $([[ -s "${MAESTRO_RUN_MARKER}" ]] && echo present || echo missing) |
| Phase log | \`${MAESTRO_PHASE_LOG}\` | $([[ -s "${MAESTRO_PHASE_LOG}" ]] && echo non-empty || echo **empty**) |

## Explicit non-claims

- This is **not** a mobile app code failure.
- Android runtime smoke is **not** marked passed.
- iOS / native / app-store closure is **not** claimed.
- Physical-device validation is **not** claimed.

## Recommended next action

Provision or repair a hardened Android emulator runner with stable nested virtualization/KVM.
Prefer a physical-device/Maestro runner or dedicated emulator host if Xen nested virtualization
remains unstable. Keep this host classified as inconclusive/blocked for emulator runtime closure
until it can boot an AVD, register in adb, capture logs/screenshots, and run runtime smoke reliably.

EOF

  maestro_log_phase "FORMAL_REPORT written: ${report}"
  echo "[maestro-runtime] Formal blocker report: ${report}" >&2
}

maestro_orchestration_fail() {
  local reason="$1"
  MAESTRO_RUN_STATUS="failed"
  maestro_log_phase "FAIL: ${reason}"
  maestro_write_failure_report "${reason}"
  exit 1
}

maestro_orchestration_finalize() {
  local exit_code="${1:-$?}"
  if [[ "${exit_code}" -ne 0 && "${MAESTRO_FAILURE_REPORT_WRITTEN}" -eq 0 ]]; then
    maestro_write_failure_report "script exited with code ${exit_code} before explicit failure report"
  fi
  if [[ "${MAESTRO_RUN_STATUS}" == "running" && "${exit_code}" -eq 0 ]]; then
    maestro_log_phase "RUN_COMPLETED exit=0"
    MAESTRO_RUN_STATUS="completed"
  fi
  return "${exit_code}"
}
