# Shared logic for root-installed Impilo k3s image helpers.
# Installed copy: /usr/local/libexec/impilo/k3s-image-helper-common.sh (root:root, 0644)
# shellcheck shell=bash
[[ -n "${_IMPILO_K3S_HELPER_COMMON_LOADED:-}" ]] && return 0
_IMPILO_K3S_HELPER_COMMON_LOADED=1

IMPILO_HELPER_VERSION="3"
IMPILO_DEFAULT_REPO="/opt/impilo/repos/Impilo-vNext"
IMPILO_HELPER_LOG="/var/log/impilo-k3s-image-helper.log"
IMPILO_IMPORT_LOCK="/tmp/impilo-k3s-import.lock"
IMPILO_TAG_REGEX='^preview(-[a-f0-9]{7,40})?$'

impilo_helper_log() {
  local msg="$1"
  local line
  line="$(date -Is) [$$] $msg"
  echo "$line" >>"$IMPILO_HELPER_LOG" 2>/dev/null || true
  if [[ -n "${IMPILO_REPO_PATH:-}" && -d "${IMPILO_REPO_PATH}/reports/full-boot" ]]; then
    echo "$line" >>"${IMPILO_REPO_PATH}/reports/full-boot/k3s-image-import-helper.log" 2>/dev/null || true
  fi
}

impilo_validate_tag() {
  local tag="$1"
  if [[ -z "$tag" ]]; then
    echo "ERROR: image tag required (preview or preview-<git-sha>)" >&2
    return 1
  fi
  if [[ ! "$tag" =~ $IMPILO_TAG_REGEX ]]; then
    echo "ERROR: tag '$tag' not allowed (must match preview or preview-<hex-sha>)" >&2
    return 1
  fi
  return 0
}

impilo_validate_repo() {
  local repo="${1:-$IMPILO_DEFAULT_REPO}"
  if [[ ! "$repo" =~ ^/opt/impilo/repos/Impilo-vNext$ ]]; then
    echo "ERROR: repo path must be /opt/impilo/repos/Impilo-vNext" >&2
    return 1
  fi
  if [[ ! -d "$repo" || ! -f "$repo/config/full-boot-service-classification.yml" ]]; then
    echo "ERROR: invalid repo or missing classification at $repo" >&2
    return 1
  fi
  IMPILO_REPO_PATH="$repo"
  return 0
}

impilo_validate_tar_path() {
  local tar="$1"
  case "$tar" in
    /tmp/impilo-image-*) ;;
    "${IMPILO_REPO_PATH}/reports/full-boot/"*.tar) ;;
    *)
      echo "ERROR: tar path not approved: $tar" >&2
      return 1
      ;;
  esac
  return 0
}

impilo_ctr_import() {
  local tar="$1"
  impilo_validate_tar_path "$tar" || return 1
  if command -v k3s >/dev/null 2>&1; then
    k3s ctr images import "$tar"
    return $?
  fi
  if command -v ctr >/dev/null 2>&1; then
    ctr -n k8s.io images import "$tar"
    return $?
  fi
  echo "ERROR: k3s/ctr not found" >&2
  return 1
}

impilo_ctr_list() {
  if command -v k3s >/dev/null 2>&1; then
    k3s ctr images list -q 2>/dev/null
    return 0
  fi
  if command -v ctr >/dev/null 2>&1; then
    ctr -n k8s.io images list -q 2>/dev/null
    return 0
  fi
  return 1
}

# Returns 0 if ref appears present in containerd list file (missing-only skip).
impilo_ref_in_containerd() {
  local ref="$1"
  local ctr_file="$2"
  [[ -f "$ctr_file" ]] || return 1
  local repo_name="${ref%%:*}"
  local want_tag="${ref#*:}"
  [[ "$want_tag" == "$ref" ]] && want_tag="latest"
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    [[ "$line" != *"$repo_name"* ]] && continue
    if [[ "$want_tag" == "preview" ]]; then
      if [[ "$line" == *":preview" ]] || [[ "$line" == *":preview-"* ]]; then
        return 0
      fi
    elif [[ "$line" == *":${want_tag}"* ]]; then
      return 0
    fi
  done <"$ctr_file"
  return 1
}

impilo_acquire_import_lock() {
  exec {IMPILO_LOCK_FD}>"$IMPILO_IMPORT_LOCK"
  if ! flock -n "$IMPILO_LOCK_FD"; then
    local holder=""
    holder="$(cat "${IMPILO_IMPORT_LOCK}.pid" 2>/dev/null || echo unknown)"
    echo "ERROR: k3s image import already running (lock holder PID $holder)" >&2
    echo "Wait for it to finish or run cleanup_duplicate_k3s_import_processes checkpoint." >&2
    return 1
  fi
  echo $$ >"${IMPILO_IMPORT_LOCK}.pid"
  return 0
}

impilo_infra_refs() {
  cat <<'EOF'
postgres:16-alpine
redis:7-alpine
apache/kafka:3.7.1
quay.io/keycloak/keycloak:25.0
minio/minio:latest
hapiproject/hapi:v7.4.0
envoyproxy/envoy:v1.31-latest
EOF
}

impilo_required_refs() {
  local tag="$1"
  local repo="$2"
  python3 - "$repo" "$tag" <<'PY'
import sys
import yaml

repo, tag = sys.argv[1], sys.argv[2]
path = f"{repo}/config/full-boot-service-classification.yml"
doc = yaml.safe_load(open(path))
req = [e for e in doc["classifications"] if e.get("classification") == "required_full_boot"]
refs = []
for e in req:
    if e.get("official_image"):
        off = e["official_image"]
        refs.append(off if ":" in off else f"{off}:latest")
    else:
        refs.append(f"impilo/{e['id']}:{tag}")
for r in refs:
    print(r)
PY
}

impilo_wave_refs() {
  local tag="$1"
  local repo="$2"
  local max_wave="${3:-0}"
  python3 - "$repo" "$tag" "$max_wave" <<'PY'
import sys
import yaml

repo, tag, max_wave = sys.argv[1], sys.argv[2], int(sys.argv[3])
cls = yaml.safe_load(open(f"{repo}/config/full-boot-service-classification.yml"))
waves = yaml.safe_load(open(f"{repo}/config/full-boot-waves.yml"))
enabled = set()
for wave in waves.get("waves", []):
    if wave.get("id", 0) > max_wave:
        break
    for sid in wave.get("services", []):
        enabled.add(sid)
by_id = {e["id"]: e for e in cls["classifications"]}
refs = []
for sid in sorted(enabled):
    e = by_id.get(sid)
    if not e:
        continue
    if e.get("official_image"):
        off = e["official_image"]
        refs.append(off if ":" in off else f"{off}:latest")
    elif e.get("image_strategy", "").startswith("not-required"):
        continue
    else:
        refs.append(f"impilo/{sid}:{tag}")
for r in refs:
    print(r)
PY
}

impilo_runtime_refs() {
  local tag="$1"
  local repo="$2"
  python3 - "$repo" "$tag" <<'PY'
import sys
import yaml

repo, tag = sys.argv[1], sys.argv[2]
doc = yaml.safe_load(open(f"{repo}/config/full-boot-service-classification.yml"))
skip = {"not-required-generated-client", "not-required-mobile-artifact", "not-required-internal-package",
        "not-required-doctrine-only-component", "official-helm-chart", "unknown-needs-review"}
for e in doc["classifications"]:
    strat = e.get("image_strategy", "")
    if strat in skip or strat.startswith("not-required"):
        continue
    if e.get("official_image"):
        off = e["official_image"]
        print(off if ":" in off else f"{off}:latest")
    elif strat in ("shared-dockerfile-template", "dockerfile", "jib", "buildpacks"):
        print(f"impilo/{e['id']}:{tag}")
PY
}

impilo_check_presence() {
  local tag="$1"
  local repo="$2"
  local ctr_file="${3:-}"
  IMPILO_REPO_PATH="$repo"
  export IMPILO_REPO_PATH tag
  if [[ -n "$ctr_file" && -f "$ctr_file" ]]; then
    export CTR_IMAGES_FILE="$ctr_file"
  else
    unset CTR_IMAGES_FILE
  fi
  python3 - <<'PY'
import os
import sys

import yaml

repo = os.environ["IMPILO_REPO_PATH"]
tag = os.environ["tag"]
if os.environ.get("CTR_IMAGES_FILE"):
    with open(os.environ["CTR_IMAGES_FILE"]) as f:
        ctr_lines = [ln.strip() for ln in f if ln.strip()]
else:
    ctr_lines = []

doc = yaml.safe_load(open(os.path.join(repo, "config/full-boot-service-classification.yml")))
max_wave = os.environ.get("FULL_BOOT_MAX_WAVE")
if max_wave is not None and str(max_wave).strip() != "":
    waves = yaml.safe_load(open(os.path.join(repo, "config/full-boot-waves.yml")))
    enabled = set()
    for wave in waves.get("waves", []):
        if wave.get("id", 0) > int(max_wave):
            break
        for sid in wave.get("services", []):
            enabled.add(sid)
    req = [e for e in doc["classifications"] if e.get("id") in enabled]
else:
    req = [e for e in doc["classifications"] if e.get("classification") == "required_full_boot"]


def expected_ref(e):
    if e.get("official_image"):
        off = e["official_image"]
        return off if ":" in off else f"{off}:latest"
    return f"impilo/{e['id']}:{tag}"


def image_present(ref: str) -> tuple[bool, str]:
    repo_name = ref.split(":")[0] if ":" in ref else ref
    want_tag = ref.split(":", 1)[1] if ":" in ref else "latest"
    matches = []
    for line in ctr_lines:
        if repo_name not in line:
            continue
        if want_tag == "preview":
            if ":preview" in line or ":preview-" in line:
                matches.append(line)
        elif f":{want_tag}" in line:
            matches.append(line)
    if matches:
        return True, matches[0]
    return False, ""


present = 0
missing = []
for e in req:
    ref = expected_ref(e)
    ok, matched = image_present(ref)
    sid = e["id"]
    if ok:
        present += 1
        print(f"PASS  {sid}  {ref}")
        if matched and matched != ref:
            print(f"      matched: {matched}")
    else:
        missing.append((sid, ref))
        print(f"FAIL  {sid}  {ref}  (not in containerd)")

total = len(req)
print("")
if missing:
    print(f"IMAGE_PRESENCE: FAIL  ({present}/{total} present)")
    for sid, ref in missing:
        print(f"  missing: {ref}")
    print(f"SUMMARY ok=0 fail={len(missing)}")
    sys.exit(1)
print(f"IMAGE_PRESENCE: PASS  ({present}/{total} present)")
print(f"SUMMARY ok={total} fail=0")
sys.exit(0)
PY
}
