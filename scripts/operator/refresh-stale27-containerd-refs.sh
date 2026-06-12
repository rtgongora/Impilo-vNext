#!/usr/bin/env bash
# Targeted containerd ref refresh for 27 stale full-preview services.
# Removes scoped 127.0.0.1:5000/impilo/* refs only, then re-imports preview-4917def8.
# Run as root: sudo bash scripts/operator/refresh-stale27-containerd-refs.sh
set -euo pipefail

REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
TAG="${FULL_BOOT_IMAGE_TAG:-preview-4917def8}"
ONLY_IDS="ai-model-registry-service,audit-ledger-service,butano-service,community-service,credential-verification-service,document-service,guidance-service,identity-assurance-service,inventory-elmis-adapter,inventory-service,llm-orchestration-service,msika-service,mushe-wallet-service,pharmacy-service,product-registry-service,scheduling-service,search-service,share-slip-service,tshepo-audit-service,tshepo-authz-service,tshepo-consent-service,tshepo-identity-service,tshepo-keys-service,tshepo-offline-service,ubomi-service,varapi-service,zibo-service"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "ERROR: run as root: sudo bash $0" >&2
  exit 1
fi

echo "=== stale-27 containerd ref refresh ==="
echo "tag=$TAG repo=$REPO"
echo "scope: 127.0.0.1:5000/impilo/<stale27> tags preview|preview-* and @sha256 digests only"
echo "no global prune; no PVC/DB/Helm changes"

# Install v5+ helper first, then source once (avoid _IMPILO_K3S_HELPER_COMMON_LOADED short-circuit).
bash "$REPO/scripts/operator/install-k3s-image-helper.sh" >/dev/null

LIB="/usr/local/libexec/impilo/k3s-image-helper-common.sh"
if [[ ! -f "$LIB" ]]; then
  echo "ERROR: missing $LIB after install" >&2
  exit 1
fi
unset _IMPILO_K3S_HELPER_COMMON_LOADED
# shellcheck source=/usr/local/libexec/impilo/k3s-image-helper-common.sh
source "$LIB"

if ! declare -F impilo_stale27_service_ids >/dev/null 2>&1; then
  echo "ERROR: helper v${IMPILO_HELPER_VERSION:-?} missing impilo_stale27_service_ids — reinstall helper" >&2
  exit 1
fi

impilo_validate_tag "$TAG"
impilo_validate_repo "$REPO"

mapfile -t SERVICES < <(impilo_stale27_service_ids)
echo "services_targeted=${#SERVICES[@]}"

CTR_CACHE="$(mktemp)"
impilo_ctr_list >"$CTR_CACHE" || true

declare -A PLANNED_SEEN=()
declare -a PLANNED=()
for sid in "${SERVICES[@]}"; do
  [[ -n "$sid" ]] || continue
  prefix="127.0.0.1:5000/impilo/${sid}"
  while IFS= read -r ref; do
    [[ -n "$ref" ]] || continue
    if impilo_validate_stale27_ctr_ref "$ref" "$sid" && [[ -z "${PLANNED_SEEN[$ref]:-}" ]]; then
      PLANNED+=("$ref")
      PLANNED_SEEN[$ref]=1
    fi
  done <"$CTR_CACHE"
  while IFS= read -r tag_ref; do
    [[ -n "$tag_ref" ]] || continue
    if impilo_ref_in_containerd "$tag_ref" "$CTR_CACHE" && [[ -z "${PLANNED_SEEN[$tag_ref]:-}" ]]; then
      PLANNED+=("$tag_ref")
      PLANNED_SEEN[$tag_ref]=1
    fi
  done < <(impilo_stale27_tag_refs "$sid" "$TAG")
done

if [[ ${#PLANNED[@]} -eq 0 ]]; then
  echo "PLANNED_REMOVAL: none (no matching stale-27 containerd refs found)"
else
  echo "PLANNED_REMOVAL count=${#PLANNED[@]}"
  printf '  REMOVE  %s\n' "${PLANNED[@]}"
fi

REMOVED=0
FAILED=0
SKIPPED=0
impilo_helper_log "stale27-refresh START removals=${#PLANNED[@]}"

for ref in "${PLANNED[@]}"; do
  sid="${ref#127.0.0.1:5000/impilo/}"
  sid="${sid%%[:@]*}"
  if ! impilo_validate_stale27_ctr_ref "$ref" "$sid"; then
    echo "SKIP  $ref  (out of scope)"
    SKIPPED=$((SKIPPED + 1))
    continue
  fi
  echo "RM    $ref"
  if impilo_ctr_rm "$ref"; then
    REMOVED=$((REMOVED + 1))
    impilo_helper_log "RM OK $ref"
  else
    echo "FAIL  $ref  (ctr rm)"
    FAILED=$((FAILED + 1))
    impilo_helper_log "RM FAIL $ref"
  fi
done

rm -f "$CTR_CACHE"

echo ""
echo "removed_count=$REMOVED failed_rm_count=$FAILED skipped_count=$SKIPPED"

if [[ "$FAILED" -gt 0 ]]; then
  echo "ERROR: containerd ref removal failed for $FAILED ref(s)" >&2
  exit 1
fi

# Clear dead import locks only; child impilo-k3s-import-images acquires its own lock.
impilo_clear_stale_import_lock || true
pkill -9 -f '/usr/local/sbin/impilo-k3s-import-images' 2>/dev/null || true
rm -f /tmp/impilo-k3s-import.lock /tmp/impilo-k3s-import.lock.pid 2>/dev/null || true
sleep 1

echo ""
echo "=== re-import preview-4917def8 for stale-27 (approved docker save + ctr import) ==="
if ! /usr/local/sbin/impilo-k3s-import-images "$TAG" "$REPO" --runtime-only --only "$ONLY_IDS" --force; then
  echo "ERROR: re-import failed after containerd ref refresh" >&2
  exit 1
fi

echo "STALE27_CONTAINERD_REF_REFRESH: complete tag=$TAG removed=$REMOVED reimport=27"
impilo_helper_log "stale27-refresh END removed=$REMOVED tag=$TAG"
