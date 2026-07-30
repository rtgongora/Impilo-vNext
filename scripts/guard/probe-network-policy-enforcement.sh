#!/usr/bin/env bash
# Does this cluster actually enforce NetworkPolicy?
#
# An unenforced CNI accepts policy objects and ignores them. `kubectl get networkpolicy`
# lists them, nothing logs a warning, and the estate looks isolated while any pod can still
# reach any other pod. The only way to know is to try it.
#
# Runs a positive control first: if the two pods cannot reach each other BEFORE the policy
# is applied, the probe itself is broken and a later failure would prove nothing.
#
# Creates and deletes its own throwaway namespace. Touches no existing workload.
#
#   bash scripts/guard/probe-network-policy-enforcement.sh
#
# Exit 0 = enforced. Exit 1 = NOT enforced. Exit 2 = probe could not run.
set -uo pipefail

NS="${NETPOL_PROBE_NAMESPACE:-netpol-enforcement-probe}"
IMAGE="${NETPOL_PROBE_IMAGE:-redis:7-alpine}"
SETTLE_SECONDS="${NETPOL_PROBE_SETTLE:-10}"

cleanup() {
  kubectl delete namespace "$NS" --wait=false >/dev/null 2>&1 || true
}

if ! kubectl version >/dev/null 2>&1; then
  echo "PROBE SKIP  no reachable cluster"
  exit 2
fi

if kubectl get namespace "$NS" >/dev/null 2>&1; then
  echo "PROBE FAIL  namespace $NS already exists — refusing to reuse a namespace this probe did not create"
  exit 2
fi

trap cleanup EXIT
kubectl create namespace "$NS" >/dev/null || { echo "PROBE SKIP  cannot create a namespace"; exit 2; }

kubectl apply -f - >/dev/null <<EOF || { echo "PROBE SKIP  cannot create pods"; exit 2; }
apiVersion: v1
kind: Pod
metadata: { name: server, namespace: $NS, labels: { app: server } }
spec:
  containers:
    - { name: redis, image: $IMAGE, imagePullPolicy: IfNotPresent }
---
apiVersion: v1
kind: Pod
metadata: { name: client, namespace: $NS, labels: { app: client } }
spec:
  containers:
    - { name: shell, image: $IMAGE, imagePullPolicy: IfNotPresent, command: ["sleep", "900"] }
EOF

if ! kubectl -n "$NS" wait --for=condition=Ready pod/server pod/client --timeout=180s >/dev/null 2>&1; then
  echo "PROBE SKIP  probe pods did not become ready (image $IMAGE may not be present on the node)"
  exit 2
fi

SERVER_IP=$(kubectl -n "$NS" get pod server -o jsonpath='{.status.podIP}')
reachable() { kubectl -n "$NS" exec client -- sh -c "nc -z -w 5 $SERVER_IP 6379" >/dev/null 2>&1; }

# ── Positive control: prove the instrument before trusting its silence ──────
if ! reachable; then
  echo "PROBE FAIL  pods cannot reach each other with NO policy applied — the probe is broken,"
  echo "            so a block after applying policy would not have meant enforcement."
  exit 2
fi
echo "PROBE OK    positive control: pod-to-pod reachable with no policy"

# ── Negative control: default-deny both directions ──────────────────────────
kubectl apply -f - >/dev/null <<EOF
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata: { name: default-deny-all, namespace: $NS }
spec:
  podSelector: {}
  policyTypes: [Ingress, Egress]
EOF

sleep "$SETTLE_SECONDS"

if reachable; then
  echo
  echo "RESULT: NetworkPolicy is NOT ENFORCED on this cluster."
  echo "        A default-deny ingress+egress policy selecting every pod changed nothing."
  echo "        Any NetworkPolicy shipped against this cluster is decorative."
  echo "        See docs/environment/NETWORK_POLICY_ENFORCEMENT_DEPENDENCY.md"
  exit 1
fi

echo
echo "RESULT: NetworkPolicy IS ENFORCED on this cluster."
echo "        D5 is application work: enable the per-service template, gateway-only ingress,"
echo "        and workload identity for service-to-service calls."
exit 0
