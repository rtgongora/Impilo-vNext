#!/usr/bin/env bash
# A jsonb column mapped to a String field must declare @JdbcTypeCode(SqlTypes.JSON).
#
# Postgres does not coerce a varchar bind parameter into jsonb. Without the annotation Hibernate
# binds the field as a varchar and the database refuses the statement outright:
#
#   ERROR: column "entry_context_json" is of type jsonb but expression is of type character varying
#
# This is not a formatting preference. It fails the whole INSERT, so the row is never written — and
# it fails only against a real Postgres. A service whose tests run on H2, or whose tests exercise
# the read path and not the write, stays green while the write path cannot work at all. That is
# exactly how it was found: the emergency episode open, shipped and unit-tested since W10, 500ed on
# the first attempt to open an episode through a browser against a real database.
#
# SCOPE is pct-service. Sixty-odd mappings elsewhere in the estate have the same shape, and their
# owners should decide about their own write paths — a guard that fails on other lanes' code is one
# the fleet learns to push past, which costs a control rather than adding one. Widening this is a
# conversation with those owners, and the scan below is the evidence to have it with.
#
# PROVEN BOTH WAYS: `bash scripts/guard/check-jsonb-column-binding.sh --self-test`.
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
source "$REPO_PATH/scripts/guard/_guard-common.sh"

SCOPED_PATHS=(
  "services/pct-service/src/main/java"
)

usage() {
  echo "usage: $0 [--self-test]"
}

# Prints one "file:line: source" per unbound jsonb mapping. An annotation counts whether it sits on
# the field's own line or on any of the five lines above it, which is where the other jsonb columns
# in this service carry it.
scan() {
  local root="$1"
  python3 - "$root" <<'PY'
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
files = [root] if root.is_file() else sorted(root.rglob("*.java"))
scanned = 0
findings = []

for path in files:
    if "/target/" in str(path):
        continue
    try:
        lines = path.read_text().splitlines()
    except OSError:
        continue
    scanned += 1
    for index, line in enumerate(lines):
        if "columnDefinition" not in line or "jsonb" not in line.lower():
            continue
        window = "\n".join(lines[max(0, index - 5): index + 1])
        # @Convert and @Type are the other two ways to bind a json column; either is a deliberate
        # choice, and neither leaves the varchar bind this guard exists to catch.
        if any(marker in window for marker in ("JdbcTypeCode", "@Convert", "@Type(")):
            continue
        findings.append(f"{path}:{index + 1}: {line.strip()}")

print(scanned)
for finding in findings:
    print(finding)
PY
}

report() {
  local root="$1" output first rest
  output="$(scan "$root")"
  first="$(head -n 1 <<< "$output")"
  rest="$(tail -n +2 <<< "$output")"
  SCANNED_TOTAL=$((SCANNED_TOTAL + first))
  if [[ -n "$rest" ]]; then
    while IFS= read -r line; do
      [[ -z "$line" ]] && continue
      echo "GUARD FAIL  ${line%%:*}: jsonb column bound as a varchar"
      echo "    ${line#*: }"
    done <<< "$rest"
    return 1
  fi
  return 0
}

self_test() {
  echo "=== self-test: the guard must reject an unbound jsonb mapping ==="
  SELF_TEST_TMP="$(mktemp -d)"
  local tmp="$SELF_TEST_TMP"
  trap 'rm -rf "${SELF_TEST_TMP:-}"' EXIT

  cat > "$tmp/Unbound.java" <<'EOF'
@Entity
public class Unbound {
    @Column(name = "entry_context_json", columnDefinition = "jsonb")
    private String entryContextJson;
}
EOF

  SCANNED_TOTAL=0
  if report "$tmp/Unbound.java" > "$tmp/out.txt" 2>&1; then
    echo "SELF-TEST FAIL — the guard passed a jsonb column with no JSON binding."
    echo "  A guard that has never been seen failing proves nothing."
    cat "$tmp/out.txt"
    return 1
  fi
  echo "  rejected as expected:"
  sed 's/^/    /' "$tmp/out.txt"

  echo "=== self-test: an annotated mapping must pass ==="
  cat > "$tmp/Bound.java" <<'EOF'
@Entity
public class Bound {
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entry_context_json", columnDefinition = "jsonb")
    private String entryContextJson;
}
EOF

  SCANNED_TOTAL=0
  if ! report "$tmp/Bound.java" > "$tmp/out2.txt" 2>&1; then
    echo "SELF-TEST FAIL — the guard rejected a correctly annotated mapping."
    cat "$tmp/out2.txt"
    return 1
  fi
  echo "  accepted as expected"

  echo "GUARD PASS  self-test — proven failing and proven passing"
  return 0
}

if [[ "${1:-}" == "--self-test" ]]; then
  self_test
  exit $?
elif [[ -n "${1:-}" ]]; then
  usage
  exit 2
fi

guard_assert_repo_path || exit 1

echo "=== jsonb columns declare a JSON binding (pct-service) ==="

SCANNED_TOTAL=0
overall=0
for path in "${SCOPED_PATHS[@]}"; do
  report "$path" || overall=1
done

guard_assert_scanned "$SCANNED_TOTAL" "Java files in pct-service" || exit 1

if [[ $overall -ne 0 ]]; then
  echo
  echo "Postgres refuses a varchar bind for a jsonb column, so each mapping above fails its INSERT"
  echo "against a real database however green the tests are. Add the binding the rest of this"
  echo "service already uses:"
  echo
  echo "  @JdbcTypeCode(SqlTypes.JSON)"
  echo "  @Column(name = \"…\", columnDefinition = \"jsonb\")"
  echo "  private String …;"
  exit 1
fi

guard_pass "jsonb-column-binding — $SCANNED_TOTAL Java file(s) scanned in pct-service"
