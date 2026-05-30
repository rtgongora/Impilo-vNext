#!/usr/bin/env bash
set -euo pipefail
NAMESPACE="${NAMESPACE:-impilo-preview}"
SVC="${1:-experience-bff}"
kubectl logs -n "$NAMESPACE" -l "app=$SVC" --tail=200 -f
