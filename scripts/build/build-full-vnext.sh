#!/usr/bin/env bash
# Build all required/optional full-boot targets; fail on required failures.
# Optional: --only-modules svc1,svc2 (Maven -pl -am + matching npm workspaces)
set -uo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
full_boot_ensure_artifacts

ONLY_MODULES_CSV=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --only-modules)
      [[ $# -ge 2 ]] || { echo "--only-modules requires comma-separated module ids"; exit 2; }
      ONLY_MODULES_CSV="$2"
      shift 2
      ;;
    -h|--help)
      grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      echo "Usage: bash scripts/build/build-full-vnext.sh [--only-modules id1,id2]" >&2
      exit 2
      ;;
  esac
done

LOG_DIR="$FULL_BOOT_REPORTS/build-logs"
mkdir -p "$LOG_DIR"
SUMMARY_JSON="$FULL_BOOT_REPORTS/full-build-summary.json"
SUMMARY_MD="$FULL_BOOT_REPORTS/full-build-summary.md"

REQUIRED_FAIL=0
OPTIONAL_FAIL=0
PASS=0
SKIP=0
RESULTS=()

mvn_cmd() {
  if [[ -x services/mvnw ]]; then
    echo "services/mvnw"
  elif command -v mvn >/dev/null 2>&1; then
    echo "mvn"
  else
    return 1
  fi
}

run_maven() {
  local mod="$1"
  local log="$LOG_DIR/${mod}.log"
  local mvn
  mvn="$(mvn_cmd)" || { echo "SKIP $mod (no mvn)" | tee "$log"; return 2; }
  if [[ ! -f "services/$mod/pom.xml" ]]; then
    echo "SKIP $mod (no pom.xml)" | tee "$log"
    return 2
  fi
  if (cd services && $mvn -q package -DskipTests -pl "$mod" -am >"$log" 2>&1); then
    echo "PASS $mod" | tee -a "$log"
    return 0
  fi
  echo "FAIL $mod" | tee -a "$log"
  return 1
}

run_npm_ui() {
  local ws="$1"
  local log="$LOG_DIR/${ws}.log"
  if [[ ! -f "ui/$ws/package.json" ]]; then
    echo "SKIP $ws" | tee "$log"
    return 2
  fi
  if (cd "ui/$ws" && npm run build >"$log" 2>&1); then
    echo "PASS $ws" | tee -a "$log"
    return 0
  fi
  echo "FAIL $ws" | tee -a "$log"
  return 1
}

bash scripts/build/discover-build-targets.sh "$FULL_BOOT_REPORTS/build-targets.tsv"

REACTOR_OK=0
if [[ -n "$ONLY_MODULES_CSV" ]]; then
  MVN="$(mvn_cmd 2>/dev/null || true)"
  IFS=',' read -ra ONLY_MODS <<<"$ONLY_MODULES_CSV"
  for mod in "${ONLY_MODS[@]}"; do
    mod="${mod// /}"
    [[ -z "$mod" ]] && continue
    if [[ "$mod" == "one-ui-shell" ]]; then
      run_npm_ui "one-ui-shell" || true
      continue
    fi
    if [[ -n "$MVN" ]]; then
      run_maven "$mod" || true
    fi
  done
else
  # Parent reactor build (all Java modules) — single pass when available
  MVN="$(mvn_cmd 2>/dev/null || true)"
  if [[ -n "$MVN" && -f services/pom.xml ]]; then
    reactor_log="$LOG_DIR/_reactor.log"
    echo "Running Maven reactor (services/pom.xml) via $MVN..."
    if (cd services && $MVN -q install -DskipTests >"$reactor_log" 2>&1); then
      echo "PASS reactor" | tee -a "$reactor_log"
      REACTOR_OK=1
    else
      echo "FAIL reactor — per-module logs below" | tee -a "$reactor_log"
      REACTOR_OK=0
    fi
  fi
fi

while IFS=$'\t' read -r id tool path class; do
  if [[ -n "$ONLY_MODULES_CSV" ]]; then
    want=0
    IFS=',' read -ra ONLY_MODS <<<"$ONLY_MODULES_CSV"
    for mod in "${ONLY_MODS[@]}"; do
      mod="${mod// /}"
      [[ "$id" == "$mod" ]] && want=1
    done
    [[ $want -eq 1 ]] || continue
  fi
  [[ "$id" == "id" ]] && continue
  rc=0
  case "$tool" in
    maven)
      if [[ "${REACTOR_OK:-0}" -eq 1 ]] && ls "services/$id/target/"*.jar >/dev/null 2>&1; then
        echo "PASS $id (reactor)" >>"$LOG_DIR/${id}.log"
        rc=0
      else
        run_maven "$id" || rc=$?
      fi
      ;;
    npm) run_npm_ui "$id" || rc=$? ;;
    pnpm) echo "SKIP $id (pnpm mobile — run mobile checks)" >>"$LOG_DIR/${id}.log"; rc=2 ;;
    *) echo "SKIP $id (tool=$tool)" >>"$LOG_DIR/${id}.log"; rc=2 ;;
  esac
  if [[ $rc -eq 0 ]]; then
    PASS=$((PASS + 1))
    RESULTS+=("{\"id\":\"$id\",\"status\":\"pass\",\"classification\":\"$class\"}")
  elif [[ $rc -eq 2 ]]; then
    SKIP=$((SKIP + 1))
    RESULTS+=("{\"id\":\"$id\",\"status\":\"skip\",\"classification\":\"$class\"}")
  else
    if [[ "$class" == "required_full_boot" ]]; then
      REQUIRED_FAIL=$((REQUIRED_FAIL + 1))
      RESULTS+=("{\"id\":\"$id\",\"status\":\"fail\",\"classification\":\"$class\",\"severity\":\"required\"}")
    else
      OPTIONAL_FAIL=$((OPTIONAL_FAIL + 1))
      RESULTS+=("{\"id\":\"$id\",\"status\":\"fail\",\"classification\":\"$class\",\"severity\":\"optional\"}")
    fi
  fi
done <"$FULL_BOOT_REPORTS/build-targets.tsv"

{
  echo "{"
  echo "  \"generated_at\": \"$(date -Iseconds)\","
  echo "  \"commit\": \"$(git rev-parse HEAD)\","
  echo "  \"pass\": $PASS,"
  echo "  \"skip\": $SKIP,"
  echo "  \"required_fail\": $REQUIRED_FAIL,"
  echo "  \"optional_fail\": $OPTIONAL_FAIL,"
  echo "  \"results\": [$(IFS=,; echo "${RESULTS[*]}")]"
  echo "}"
} >"$SUMMARY_JSON"

cat >"$SUMMARY_MD" <<EOF
# Full Build Summary

- Commit: \`$(git rev-parse --short HEAD)\`
- Pass: **$PASS**
- Skip: **$SKIP**
- Required failures: **$REQUIRED_FAIL**
- Optional failures (advisory): **$OPTIONAL_FAIL**

Logs: \`reports/full-boot/build-logs/\`
EOF

echo "Build summary: pass=$PASS required_fail=$REQUIRED_FAIL optional_fail=$OPTIONAL_FAIL"
[[ $REQUIRED_FAIL -eq 0 ]] || exit 1
exit 0
