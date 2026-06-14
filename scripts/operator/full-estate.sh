#!/usr/bin/env bash
# full-estate.sh — canonical operator entrypoint for the vNext unified runtime estate.
#
# All of vNext is accountable. All deployable vNext services must run. One estate means all
# deployable vNext services. Supporting artifacts do not run as pods, but they remain part of
# vNext truth. Waves are sequencing, not optionality. Deployment truth is the running estate,
# not the deployment story. A deployment is not complete until the full estate is running,
# aligned, healthy, current, and testable.
#
# Subcommands:
#   status   (read-only)  estate readiness + runtime image truth + UI/BFF behaviour + label
#   update   (mutating)   build -> push -> import -> helm full estate -> rollout -> verify
#   stop     (mutating)   scale the estate to zero; PRESERVE PVC/Postgres/secrets/namespace
#   start    (mutating)   scale the estate up to target and validate
#   restart  (mutating)   rollout restart in safe sequence and validate
#
# Mutating subcommands print intended actions and require --yes (or interactive confirm).
# This script NEVER deletes PVCs/PVs/secrets/namespace, never `helm uninstall`, never prunes.
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"
source scripts/full-boot/_estate-guard.sh

NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
PREVIEW_URL="${PREVIEW_URL:-http://41.57.127.235}"
ASSUME_YES=0
SUBCMD="${1:-status}"
shift || true
for a in "$@"; do
  [[ "$a" == "--yes" || "$a" == "-y" ]] && ASSUME_YES=1
done

DOCTRINE="All of vNext is accountable. One estate means all deployable vNext services. Deployment truth is the running estate, not the deployment story."

confirm() {
  [[ "$ASSUME_YES" == "1" ]] && return 0
  read -r -p "$1 [type 'yes' to proceed]: " ans
  [[ "$ans" == "yes" ]]
}

estate_service_ids() {
  python3 - "$REPO_PATH" <<'PY'
import sys, yaml
doc = yaml.safe_load(open(f"{sys.argv[1]}/config/full-boot-service-classification.yml"))
ids = {e["id"] for e in doc["classifications"] if e.get("deployment_lane") == "runtime_k8s_microservice"}
ids |= {"experience-bff", "one-ui-shell"}
print("\n".join(sorted(ids)))
PY
}

cmd_status() {
  echo "== vNext full estate status =="
  echo "$DOCTRINE"
  echo "Namespace: $NS | URL: $PREVIEW_URL"
  echo ""
  local expected ready
  expected="$(estate_service_ids | wc -l | tr -d ' ')"
  echo "--- deployments ---"
  if command -v kubectl >/dev/null 2>&1; then
    ready="$(kubectl get deploy -n "$NS" --no-headers 2>/dev/null | awk '{print $1}' | wc -l | tr -d ' ')"
    echo "Deployments in $NS: $ready (expected runtime estate services: $expected)"
    echo ""
    echo "--- pods (non-ready) ---"
    kubectl get pods -n "$NS" --no-headers 2>/dev/null | awk '$3!="Running" && $1 !~ /^v-/' | head -20 || true
  else
    echo "kubectl unavailable - cannot read live estate"
  fi
  echo ""
  echo "--- runtime completeness (estate label) ---"
  bash scripts/guard/check-full-boot-runtime-completeness.sh 2>&1 | tail -6 || true
  echo ""
  echo "--- runtime image truth ---"
  bash scripts/guard/check-runtime-image-truth.sh 2>&1 | tail -4 || true
  echo ""
  echo "--- public ingress / single stack ---"
  bash scripts/operator/report-preview-generation.sh 2>&1 | tail -4 || true
  echo ""
  echo "--- served UI bundle truth ---"
  bash scripts/test/verify-ui-bundle-truth.sh --url "$PREVIEW_URL" 2>&1 | tail -3 || true
  echo ""
  echo "--- BFF behaviour truth ---"
  bash scripts/test/verify-bff-behaviour-truth.sh --url "$PREVIEW_URL" --ns "$NS" 2>&1 | tail -3 || true
}

cmd_update() {
  echo "== vNext full estate UPDATE (full build -> push -> import -> helm -> verify) =="
  echo "$DOCTRINE"
  echo "This deploys the FULL estate to $NS and requires the deploy authorization phrase."
  confirm "Proceed with full estate update?" || { echo "Aborted."; exit 1; }
  # Delegates to the canonical full-estate deploy path (default = full estate, with push + truth).
  exec bash scripts/deploy/full-boot-preview-deploy.sh
}

cmd_stop() {
  echo "== vNext full estate STOP (scale to zero; data PRESERVED) =="
  echo "This scales all estate deployments to 0 replicas in $NS."
  echo "PVCs, Postgres data, secrets, namespace, and Helm release are PRESERVED. No deletes."
  confirm "Proceed to scale the estate down?" || { echo "Aborted."; exit 1; }
  command -v kubectl >/dev/null 2>&1 || { echo "kubectl required"; exit 1; }
  kubectl scale deploy --all -n "$NS" --replicas=0
  echo "Estate scaled to zero. Use 'full-estate.sh start' to bring it back."
}

cmd_start() {
  echo "== vNext full estate START (scale up) =="
  confirm "Proceed to scale the estate up?" || { echo "Aborted."; exit 1; }
  command -v kubectl >/dev/null 2>&1 || { echo "kubectl required"; exit 1; }
  kubectl scale deploy --all -n "$NS" --replicas=1
  kubectl rollout status deployment -n "$NS" --timeout=600s || true
  cmd_status
}

cmd_restart() {
  echo "== vNext full estate RESTART (safe sequence) =="
  confirm "Proceed to restart the estate?" || { echo "Aborted."; exit 1; }
  command -v kubectl >/dev/null 2>&1 || { echo "kubectl required"; exit 1; }
  # Safe sequence: infra-adjacent trust spine first, then the rest.
  local spine="tshepo-authz-service tshepo-identity-service tshepo-consent-service tshepo-audit-service tshepo-keys-service"
  for s in $spine; do
    kubectl rollout restart deploy "$s" -n "$NS" 2>/dev/null || true
  done
  # Remaining estate (exclude the spine already restarted).
  while read -r svc; do
    case " $spine " in *" $svc "*) continue ;; esac
    kubectl rollout restart deploy "$svc" -n "$NS" 2>/dev/null || true
  done < <(estate_service_ids)
  kubectl rollout status deployment -n "$NS" --timeout=600s || true
  cmd_status
}

case "$SUBCMD" in
  status)  cmd_status ;;
  update)  cmd_update ;;
  stop)    cmd_stop ;;
  start)   cmd_start ;;
  restart) cmd_restart ;;
  -h|--help|help)
    grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//' ;;
  *) echo "Unknown subcommand: $SUBCMD (use status|update|start|stop|restart)"; exit 2 ;;
esac
