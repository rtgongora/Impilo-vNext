#!/usr/bin/env bash
# Clinical logic must not live in TypeScript.
#
# Every classification, threshold, acuity derivation and dose calculation in this pack belongs to a
# sovereign service — pct-service, the emergency domain library, the clinical knowledge platform —
# where it is versioned, content-hashed, tested against a fixture corpus, and auditable. The moment a
# copy of that reasoning appears in a React component there are two answers to the same clinical
# question, they drift, and the one the clinician sees is the one nobody governs. The browser copy is
# also the one that cannot be corrected in the field: it ships with a bundle.
#
# The emergency lane is where this pressure is highest. "The API is slow, just compute the acuity
# client-side" is a reasonable-sounding sentence that ends with a triage category derived by
# TypeScript nobody reviewed. Hence a guard rather than a convention.
#
# WHAT IS ALLOWED in the scoped paths: presentation, ordering, board arithmetic (counting episodes by
# state), next-action sequencing driven by server-supplied state, and elapsed-time display. What is
# forbidden is deriving a clinical *conclusion* — a category, a score, a threshold verdict, a dose.
#
# SCOPE is deliberately narrow: the emergency feature tree and the three time-critical primitives
# this pack promoted into shared-ui. It is not repo-wide. Widening it is a real conversation with the
# owners of every other feature tree, not a silent expansion of what this guard enforces — and a
# guard that fails on a hundred pre-existing files in lanes nobody here owns is a guard the fleet
# learns to push past, which costs a control rather than adding one.
#
# OPT-OUT: put `// clinical-logic-guard: <reason>` on the line above. Requiring a written reason is
# the point; a bare pragma would be indistinguishable from not having the guard.
#
# PROVEN BOTH WAYS: `bash scripts/guard/check-no-ts-clinical-logic.sh --self-test` writes a
# deliberate violation to a temp file, asserts the guard rejects it, then asserts the same file with
# an opt-out comment passes. A guard that has only ever been seen passing has not been tested.
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
source "$REPO_PATH/scripts/guard/_guard-common.sh"

SCOPED_PATHS=(
  "ui/one-ui-shell/src/features/emergency"
  "ui/one-ui-shell/src/app/clinical/emergency"
  "ui/shared-ui/components/ElapsedTimer.tsx"
  "ui/shared-ui/components/EventTimeline.tsx"
  "ui/shared-ui/components/StalenessBadge.tsx"
)

# pattern|what it catches. Each is anchored on an assignment or comparison so that merely *rendering*
# a server-supplied acuity, or naming one in a type, is not a hit.
RULES=(
  '(acuity|triageCategory|triageLevel|priorityLevel)[A-Za-z]*\s*=\s*[^=].*[<>?]|deriveAcuity|computeTriage|calculateAcuity#derives an acuity or triage category in TypeScript'
  '\b(systolic|diastolic|spo2|SpO2|respiratoryRate|heartRate|gcs|GCS|temperatureC|bloodGlucose)[A-Za-z]*\s*[<>]=?\s*[0-9]#compares a physiological value against a threshold in TypeScript'
  '\b(doseMg|dosePerKg|mgPerKg|weightKg)\b[^;\n]*[*/][^;\n]*[0-9]|\b[a-zA-Z]*[Dd]ose[A-Za-z]*\s*=\s*[^=;\n]*\*#performs dose arithmetic in TypeScript'
  '(SEPSIS|SHOCK|ARREST|CRITICAL|EMERGENT|URGENT)\b\s*:\s*[0-9]+|score\s*\+=\s*[0-9]#embeds a clinical score or scoring table in TypeScript'
)

usage() {
  echo "usage: $0 [--self-test]"
}

scan() {
  local root="$1" fail=0 scanned=0
  local -a files=()
  if [[ -f "$root" ]]; then
    files=("$root")
  elif [[ -d "$root" ]]; then
    mapfile -t files < <(find "$root" -type f \( -name '*.ts' -o -name '*.tsx' \) | sort)
  else
    # A scoped path that does not exist yet is not a violation, but it is worth saying so: a guard
    # pointed at a directory nobody created reports the same clean result as a clean directory.
    echo "  note: scope path not present yet: $root"
    return 0
  fi

  for file in "${files[@]}"; do
    scanned=$((scanned + 1))
    for rule in "${RULES[@]}"; do
      local pattern="${rule%%#*}" description="${rule#*#}"
      while IFS=: read -r lineno _; do
        [[ -z "$lineno" ]] && continue
        local prev=""
        if (( lineno > 1 )); then
          prev="$(sed -n "$((lineno - 1))p" "$file")"
        fi
        if [[ "$prev" == *"clinical-logic-guard:"* ]]; then
          continue
        fi
        echo "GUARD FAIL  $file:$lineno — $description"
        sed -n "${lineno}p" "$file" | sed 's/^/    /'
        fail=1
      done < <(grep -nE "$pattern" "$file" 2>/dev/null || true)
    done
  done

  SCANNED_TOTAL=$((SCANNED_TOTAL + scanned))
  return $fail
}

self_test() {
  echo "=== self-test: the guard must reject a deliberate violation ==="
  # Deliberately not a `local`: the EXIT trap runs after this function's scope is gone.
  SELF_TEST_TMP="$(mktemp -d)"
  local tmp="$SELF_TEST_TMP"
  trap 'rm -rf "${SELF_TEST_TMP:-}"' EXIT

  cat > "$tmp/violation.ts" <<'EOF'
export function triageFromVitals(systolicBp: number): string {
  if (systolicBp < 90) return "RED";
  return "GREEN";
}
EOF

  SCANNED_TOTAL=0
  if scan "$tmp/violation.ts" > "$tmp/out.txt" 2>&1; then
    echo "SELF-TEST FAIL — the guard passed a file that compares a systolic pressure to 90 in TypeScript."
    echo "  A guard that has never been seen failing proves nothing."
    cat "$tmp/out.txt"
    return 1
  fi
  echo "  rejected as expected:"
  sed 's/^/    /' "$tmp/out.txt"

  echo "=== self-test: a written opt-out reason must be honoured ==="
  cat > "$tmp/optout.ts" <<'EOF'
export function renderThreshold(systolicBp: number): string {
  // clinical-logic-guard: presentation only — the 90 is the label the server sent, not a rule
  if (systolicBp < 90) return "below the server-supplied floor";
  return "at or above it";
}
EOF

  SCANNED_TOTAL=0
  if ! scan "$tmp/optout.ts" > "$tmp/out2.txt" 2>&1; then
    echo "SELF-TEST FAIL — the guard rejected a line carrying a written opt-out reason."
    cat "$tmp/out2.txt"
    return 1
  fi
  echo "  honoured as expected"

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

echo "=== No clinical logic in TypeScript (emergency scope) ==="

SCANNED_TOTAL=0
overall=0
for path in "${SCOPED_PATHS[@]}"; do
  scan "$path" || overall=1
done

guard_assert_scanned "$SCANNED_TOTAL" "TypeScript files in the emergency scope" || exit 1

if [[ $overall -ne 0 ]]; then
  echo
  echo "Clinical reasoning belongs in pct-service, libs/emergency-domain or the clinical knowledge"
  echo "platform, where it is versioned, content-hashed and tested against a fixture corpus. A copy"
  echo "in the browser is a second answer to the same clinical question that nobody governs and that"
  echo "cannot be corrected without a redeploy."
  echo
  echo "If a line genuinely is presentation and not a rule, say why on the line above:"
  echo "  // clinical-logic-guard: <reason>"
  exit 1
fi

guard_pass "no-ts-clinical-logic — $SCANNED_TOTAL file(s) scanned in the emergency scope"
