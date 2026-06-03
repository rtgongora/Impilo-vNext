#!/usr/bin/env bash
# Push impilo images to local registry (parallel, missing-only by digest).
set -euo pipefail
REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO"
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"

REGISTRY_HOST="${IMPILO_LOCAL_REGISTRY_HOST:-127.0.0.1}"
REGISTRY_PORT="${IMPILO_LOCAL_REGISTRY_PORT:-5000}"
REGISTRY="${IMPILO_LOCAL_REGISTRY:-${REGISTRY_HOST}:${REGISTRY_PORT}}"
TAG="${FULL_BOOT_IMAGE_TAG:-preview}"
PARALLEL="${IMPILO_PUSH_PARALLEL:-4}"
MODE="${1:-required}"
ONLY_IDS="${IMPILO_PUSH_ONLY:-}"

bash "$REPO/scripts/operator/registry-up.sh"

refs_file="$(mktemp)"
case "$MODE" in
  required)
    python3 - "$REPO" "$TAG" <<'PY' >"$refs_file"
import sys, yaml
repo, tag = sys.argv[1], sys.argv[2]
doc = yaml.safe_load(open(f"{repo}/config/full-boot-service-classification.yml"))
for e in doc["classifications"]:
    if e.get("classification") != "required_full_boot":
        continue
    if e.get("official_image"):
        o = e["official_image"]
        print(o if ":" in o else f"{o}:latest")
    else:
        print(f"impilo/{e['id']}:{tag}")
PY
    ;;
  runtime)
    bash -c "source $REPO/scripts/operator/k3s-image-helper-common.sh 2>/dev/null; impilo_runtime_refs $TAG $REPO" >"$refs_file" 2>/dev/null || \
    python3 - "$REPO" "$TAG" <<'PY' >"$refs_file"
import sys, yaml
repo, tag = sys.argv[1], sys.argv[2]
doc = yaml.safe_load(open(f"{repo}/config/full-boot-service-classification.yml"))
skip = {"not-required-generated-client", "not-required-mobile-artifact", "not-required-internal-package",
        "not-required-doctrine-only-component", "official-helm-chart"}
for e in doc["classifications"]:
    s = e.get("image_strategy", "")
    if s in skip or s.startswith("not-required"):
        continue
    if e.get("official_image"):
        o = e["official_image"]
        print(o if ":" in o else f"{o}:latest")
    elif s in ("shared-dockerfile-template", "dockerfile", "jib", "buildpacks"):
        print(f"impilo/{e['id']}:{tag}")
PY
    ;;
  *)
    echo "Usage: $0 [required|runtime]" >&2
    exit 1
    ;;
esac

if [[ -n "$ONLY_IDS" ]]; then
  filtered="$(mktemp)"
  : >"$filtered"
  IFS=',' read -ra WANT <<<"$ONLY_IDS"
  while read -r ref; do
    sid="${ref#impilo/}"; sid="${sid%%:*}"
    for w in "${WANT[@]}"; do
      w="${w// /}"
      [[ "$sid" == "$w" ]] && echo "$ref" >>"$filtered" && break
    done
  done <"$refs_file"
  mv "$filtered" "$refs_file"
fi

push_one() {
  local ref="$1"
  [[ -z "$ref" ]] && return 0
  [[ "$ref" != impilo/* ]] && return 0
  local src="$ref"
  local dest="${REGISTRY}/${ref}"
  if ! docker image inspect "$src" >/dev/null 2>&1; then
    echo "SKIP  $src  (not in local docker)"
    return 0
  fi
  local sid="${src#impilo/}"; sid="${sid%%:*}"
  if curl -sf "http://${REGISTRY}/v2/impilo/${sid}/manifests/${TAG}" >/dev/null 2>&1; then
    echo "SKIP  $dest  (tag already in registry)"
    return 0
  fi
  docker tag "$src" "$dest"
  if docker push "$dest"; then
    echo "OK  $dest"
  else
    echo "FAIL $dest"
    return 1
  fi
}
export -f push_one
export REGISTRY TAG

FAIL=0
while read -r ref; do
  [[ -z "$ref" ]] && continue
  [[ "$ref" != impilo/* ]] && continue
  echo "$ref"
done <"$refs_file" | xargs -P "$PARALLEL" -I {} bash -c 'push_one "$@"' _ {} || FAIL=1

rm -f "$refs_file"
echo "Push complete (mode=$MODE registry=$REGISTRY)"
exit "$FAIL"
