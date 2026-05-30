#!/usr/bin/env bash
set -euo pipefail
NAMESPACE="${NAMESPACE:-impilo-preview}"
RELEASE="${RELEASE:-impilo-preview}"
read -r -p "Delete Helm release $RELEASE in $NAMESPACE? [y/N] " ans
[[ "$ans" == "y" || "$ans" == "Y" ]] || exit 1
helm uninstall "$RELEASE" -n "$NAMESPACE" || true
