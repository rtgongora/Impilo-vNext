#!/usr/bin/env bash
set -euo pipefail
NAMESPACE="${NAMESPACE:-impilo-preview}"
kubectl get nodes
kubectl get pods,svc,ingress -n "$NAMESPACE"
helm list -n "$NAMESPACE"
