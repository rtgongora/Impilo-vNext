#!/usr/bin/env bash
set -euo pipefail
NAMESPACE="${NAMESPACE:-impilo-preview}"
RELEASE="${RELEASE:-impilo-preview}"
helm rollback "$RELEASE" -n "$NAMESPACE" "${1:-0}"
