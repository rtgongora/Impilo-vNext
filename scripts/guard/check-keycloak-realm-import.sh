#!/usr/bin/env bash
# =============================================================================
# GUARD — Keycloak realm JSON must actually import. FAIL-CLOSED.
#
# WHY THIS EXISTS (2026-07-20 → 2026-07-26, six days of total auth outage):
# eleven realm fields named `webAuthnPasswordlessPolicy*` instead of
# `webAuthnPolicyPasswordless*` made Keycloak throw UnrecognizedPropertyException
# at startup and DIE ON EVERY RESTART. The names look plausible to a reviewer;
# only Keycloak can tell you they are wrong. So this guard asks Keycloak, by
# performing a real import into a throwaway in-container database.
#
# WHY IT IS FAIL-CLOSED (2026-08-04): the previous version exited 0 when Docker
# was absent, `continue`d past a missing realm file, and validated a hardcoded
# list that had drifted from the workflow's own path filters. Each of those is a
# green result that proves nothing. Meanwhile a `_comment` key and a
# 269-character description sat in the preview realm for eight days —
# unimportable and undetected — because the only place this guard ran was a
# billing-locked GitHub runner.
#
# WHY A SILENT SKIP IS THE WORST OUTCOME: `--import-realm` SKIPS realms that
# already exist. On a running estate a broken realm file therefore changes
# nothing and looks harmless — right up until Keycloak restarts for any reason
# and cannot come back. A guard that also stays quiet turns a latent outage into
# an invisible one.
#
# Inventory: deploy/keycloak/governed-realms.json is the single source of truth.
# This script holds NO realm list of its own. A realm found under a discovery
# root but absent from the inventory is a FAILURE, so a renamed or newly added
# realm cannot escape validation.
#
# Usage:  bash scripts/guard/check-keycloak-realm-import.sh
#         KEYCLOAK_IMAGE=<ref> bash scripts/guard/check-keycloak-realm-import.sh
# Exit:   0 = every governed realm imported cleanly
#         1 = at least one realm rejected, missing, undeclared, or unverifiable
# =============================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT" || { echo "GUARD FAIL  cannot enter repo root" >&2; exit 1; }

INVENTORY="deploy/keycloak/governed-realms.json"
GATE_NAME="keycloak-realm-import"
COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
LOG_DIR="${KEYCLOAK_REALM_LOG_DIR:-reports/keycloak-realm/${COMMIT:0:9}}"
RESULT_JSON="$LOG_DIR/results.json"

fail_hard() { echo ""; echo "GUARD FAIL  $GATE_NAME"; echo "  $*"; exit 1; }

# --- log directory: if we cannot record evidence, we do not pass -------------
if ! mkdir -p "$LOG_DIR" 2>/dev/null || [ ! -w "$LOG_DIR" ]; then
  fail_hard "cannot create or write the log directory: $LOG_DIR
  A run whose evidence cannot be recorded is not a run. Refusing to report PASS."
fi

# --- inventory --------------------------------------------------------------
[ -f "$INVENTORY" ] || fail_hard "governed-realm inventory not found: $INVENTORY
  This guard holds no realm list of its own; without the inventory it cannot
  know what it is meant to prove."

command -v python3 >/dev/null 2>&1 || fail_hard "python3 unavailable — cannot read the inventory."

if ! INV="$(python3 - "$INVENTORY" <<'PY'
import json,sys
d=json.load(open(sys.argv[1]))
realms=[r["path"] for r in d.get("realms",[])]
roots=[f'{r["dir"]}|{r["pattern"]}' for r in d.get("discovery_roots",[])]
print(d.get("keycloak_image",""))
print(" ".join(realms))
print(" ".join(roots))
PY
)"; then
  fail_hard "governed-realm inventory is not valid JSON or lacks required keys: $INVENTORY"
fi

INV_IMAGE="$(sed -n 1p <<<"$INV")"
mapfile -t REALMS < <(sed -n 2p <<<"$INV" | tr ' ' '\n' | sed '/^$/d')
mapfile -t ROOTS   < <(sed -n 3p <<<"$INV" | tr ' ' '\n' | sed '/^$/d')

KEYCLOAK_IMAGE="${KEYCLOAK_IMAGE:-$INV_IMAGE}"
[ -n "$KEYCLOAK_IMAGE" ] || fail_hard "no keycloak_image in the inventory and none supplied."

# --- zero discovered realms is a failure, never a pass -----------------------
[ "${#REALMS[@]}" -gt 0 ] || fail_hard "the inventory declares zero realms.
  Zero realms validated is not success — it is a guard that proves nothing."

# --- drift: a realm on disk that the inventory does not declare --------------
UNDECLARED=()
for root in "${ROOTS[@]}"; do
  dir="${root%%|*}"; pat="${root##*|}"
  [ -d "$dir" ] || continue
  while IFS= read -r found; do
    declared=0
    for r in "${REALMS[@]}"; do [ "$r" = "$found" ] && declared=1 && break; done
    [ "$declared" -eq 0 ] && UNDECLARED+=("$found")
  done < <(find "$dir" -maxdepth 1 -type f -name "$pat" | sort)
done
if [ "${#UNDECLARED[@]}" -gt 0 ]; then
  printf '  undeclared realm: %s\n' "${UNDECLARED[@]}"
  fail_hard "${#UNDECLARED[@]} realm file(s) exist under a discovery root but are NOT in $INVENTORY.
  A realm absent from the inventory is never validated. Add it there — or move it
  out of the discovery roots if it is genuinely not governed."
fi

# --- docker and image availability are prerequisites, not excuses ------------
command -v docker >/dev/null 2>&1 || fail_hard "docker is unavailable.
  This guard is only meaningful if Keycloak actually parses the realm. Refusing
  to report PASS on an unverified realm set."
docker info >/dev/null 2>&1 || fail_hard "docker is installed but not usable (daemon unreachable)."
docker image inspect "$KEYCLOAK_IMAGE" >/dev/null 2>&1 \
  || fail_hard "Keycloak image unavailable locally: $KEYCLOAK_IMAGE
  Pull or build it, or set KEYCLOAK_IMAGE to the image the estate runs."

IMAGE_DIGEST="$(docker image inspect "$KEYCLOAK_IMAGE" --format '{{if .RepoDigests}}{{index .RepoDigests 0}}{{else}}<none>{{end}}' 2>/dev/null || echo '<none>')"

echo "GATE   $GATE_NAME"
echo "commit $COMMIT"
echo "image  $KEYCLOAK_IMAGE"
echo "digest $IMAGE_DIGEST"
echo "realms ${#REALMS[@]} (from $INVENTORY)"
echo "logs   $LOG_DIR"
echo ""

# --- validate every governed realm ------------------------------------------
fail=0
declare -a RESULTS=()
for realm in "${REALMS[@]}"; do
  slug="$(printf '%s' "$realm" | tr '/.' '__')"
  log="$LOG_DIR/${slug}.log"
  started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  if [ ! -f "$realm" ]; then
    finished="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "MISSING: $realm — declared in the inventory but not on disk" >"$log"
    echo "FAIL  $realm (missing — declared but not present)"
    RESULTS+=("$(printf '{"gate":"%s","realm":"%s","commit":"%s","image":"%s","image_digest":"%s","started_at":"%s","finished_at":"%s","exit_code":null,"log":"%s","status":"FAIL","reason":"missing"}' \
      "$GATE_NAME" "$realm" "$COMMIT" "$KEYCLOAK_IMAGE" "$IMAGE_DIGEST" "$started" "$finished" "$log")")
    fail=1
    continue
  fi

  cmd="docker run --rm -v $PWD/$realm:/tmp/realm.json:ro --entrypoint /opt/keycloak/bin/kc.sh $KEYCLOAK_IMAGE import --file /tmp/realm.json"
  {
    echo "gate:    $GATE_NAME"
    echo "realm:   $realm"
    echo "commit:  $COMMIT"
    echo "image:   $KEYCLOAK_IMAGE"
    echo "digest:  $IMAGE_DIGEST"
    echo "command: $cmd"
    echo "started: $started"
    echo "---"
  } >"$log"

  # JSON must parse at all — a precise line/column beats a Java stack trace.
  if ! python3 -c "import json,sys; json.load(open(sys.argv[1]))" "$realm" >>"$log" 2>&1; then
    finished="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "FAIL  $realm (invalid JSON)"
    tail -3 "$log" | sed 's/^/        /'
    RESULTS+=("$(printf '{"gate":"%s","realm":"%s","commit":"%s","image":"%s","image_digest":"%s","started_at":"%s","finished_at":"%s","exit_code":1,"log":"%s","status":"FAIL","reason":"invalid_json"}' \
      "$GATE_NAME" "$realm" "$COMMIT" "$KEYCLOAK_IMAGE" "$IMAGE_DIGEST" "$started" "$finished" "$log")")
    fail=1
    continue
  fi

  # Keycloak must accept every property: same deserialisation as --import-realm.
  docker run --rm -v "$PWD/$realm":/tmp/realm.json:ro \
    --entrypoint /opt/keycloak/bin/kc.sh "$KEYCLOAK_IMAGE" import --file /tmp/realm.json >>"$log" 2>&1
  rc=$?
  finished="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "--- exit: $rc" >>"$log"

  if [ "$rc" -ne 0 ]; then
    echo "FAIL  $realm (exit $rc)"
    grep -iE "UnrecognizedPropertyException|Unrecognized field|not marked as ignorable|InvalidFormatException|MismatchedInputException|Value too long|ERROR:" "$log" \
      | head -4 | sed 's/^/        /'
    echo "        full log: $log"
    RESULTS+=("$(printf '{"gate":"%s","realm":"%s","commit":"%s","image":"%s","image_digest":"%s","started_at":"%s","finished_at":"%s","exit_code":%d,"log":"%s","status":"FAIL","reason":"import_rejected"}' \
      "$GATE_NAME" "$realm" "$COMMIT" "$KEYCLOAK_IMAGE" "$IMAGE_DIGEST" "$started" "$finished" "$rc" "$log")")
    fail=1
  else
    echo "PASS  $realm"
    RESULTS+=("$(printf '{"gate":"%s","realm":"%s","commit":"%s","image":"%s","image_digest":"%s","started_at":"%s","finished_at":"%s","exit_code":0,"log":"%s","status":"PASS","reason":null}' \
      "$GATE_NAME" "$realm" "$COMMIT" "$KEYCLOAK_IMAGE" "$IMAGE_DIGEST" "$started" "$finished" "$log")")
  fi
done

# --- structured result: a missing result is itself a failure ----------------
{ printf '['; (IFS=,; printf '%s' "${RESULTS[*]}"); printf ']\n'; } >"$RESULT_JSON" || \
  fail_hard "could not write $RESULT_JSON — refusing to report a result that was not recorded."

recorded=$(python3 -c "import json,sys;print(len(json.load(open(sys.argv[1]))))" "$RESULT_JSON" 2>/dev/null || echo 0)
if [ "$recorded" -ne "${#REALMS[@]}" ]; then
  fail_hard "recorded $recorded result(s) for ${#REALMS[@]} governed realm(s).
  Every governed realm must produce a result; a silently absent one is a fail."
fi

echo ""
echo "results $RESULT_JSON ($recorded/${#REALMS[@]} realms recorded)"

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "GUARD FAIL  $GATE_NAME"
  echo "  A realm above will crash Keycloak at startup — it will not degrade, it"
  echo "  will fail to boot, taking ALL web and mobile authentication with it."
  echo ""
  echo "  Remember --import-realm SKIPS realms that already exist, so on a running"
  echo "  estate this stays invisible until the next restart. In the 2026-07-20"
  echo "  incident that gap was six days of total auth outage."
  echo ""
  echo "  Runbook: docs/runbooks/keycloak-auth-outage-runbook.md"
  echo "  Realm conventions: deploy/helm/impilo-vnext/files/README.md"
  exit 1
fi

echo "GUARD PASS  ${#REALMS[@]}/${#REALMS[@]} governed realms import cleanly"
exit 0
