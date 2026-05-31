#!/usr/bin/env bash
# Thin wrapper — canonical pipeline is scripts/pipeline/run-local-quality-gates.sh
set -euo pipefail
export PIPELINE_SKIP_TOOLCHECK="${PREVIEW_GATES_SKIP_TOOLCHECK:-1}"
export PIPELINE_SKIP_E2E="${PREVIEW_GATES_SKIP_E2E:-1}"
export PIPELINE_SKIP_BACKEND="${PREVIEW_GATES_SKIP_BACKEND:-0}"
export PIPELINE_SKIP_FRONTEND="${PREVIEW_GATES_SKIP_FRONTEND:-0}"
export PIPELINE_SKIP_INTEGRATION="${PREVIEW_GATES_SKIP_INTEGRATION:-0}"
export PIPELINE_SKIP_REGRESSION="${PREVIEW_GATES_SKIP_REGRESSION:-0}"
export PIPELINE_CI_MODE="${PREVIEW_GATES_CI_MODE:-0}"
exec bash "$(dirname "$0")/../pipeline/run-local-quality-gates.sh"
