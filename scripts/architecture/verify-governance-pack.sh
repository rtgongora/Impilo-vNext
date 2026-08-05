#!/usr/bin/env bash
# Governance-pack integrity verifier.
#
# Fails (exit 1) when any invariant below is broken; prints OK lines otherwise.
# Root resolution: explicit $1 (fixtures), else the repository containing this
# script (git rev-parse), else the script's grandparent directory. All checks
# run against absolute paths beneath that root, so the caller's cwd is irrelevant.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -n "${1:-}" ]]; then
  root="$(cd "$1" && pwd)"
elif root="$(git -C "$script_dir" rev-parse --show-toplevel 2>/dev/null)"; then
  :
else
  root="$(cd "$script_dir/../.." && pwd)"
fi

versioned_rel="docs/architecture/hybrid-federated-target-architecture-v1.3.5.md"
pointer_rel="docs/architecture/vnext-hybrid-federation-target-architecture.md"
# The versioned file is the complete document (~4,600 lines). Anything shorter
# than this floor is a pointer or a truncation masquerading as the architecture.
min_versioned_lines=1000
# Any file outside archive/ bearing the architecture H1 and at least this many
# lines counts as a "complete active copy"; there must be exactly one.
copy_floor_lines=200
arch_h1='# Impilo vNext — Hybrid / Federated Target Architecture'

required=(
  "CLAUDE.md"
  "docs/architecture/CLAUDE_GOVERNANCE.md"
  "docs/architecture/ARCHITECTURE_PRECEDENCE.md"
  "$versioned_rel"
  "docs/architecture/product-capability-architecture-v2.0.md"
  "docs/architecture/supersession-notice-v1.0.md"
  "docs/standards/technical-standards-catalogue-v1.0.md"
)

failed=0
fail() { echo "FAIL: $*" >&2; failed=1; }

# 1. Required governance files exist and are non-empty.
for path in "${required[@]}"; do
  if [[ ! -s "$root/$path" ]]; then
    fail "missing or empty: $path"
  else
    echo "OK: $path"
  fi
done

# 2. Root CLAUDE.md imports the governance rules.
if [[ -s "$root/CLAUDE.md" ]] && ! grep -Fq '@docs/architecture/CLAUDE_GOVERNANCE.md' "$root/CLAUDE.md"; then
  fail "root CLAUDE.md does not import the governance rules"
fi

# 3. No controlling/frozen legacy language outside the archive (hard failure).
if legacy_hits="$(grep -RIn --exclude-dir=archive --exclude-dir=prompts \
    --exclude='supersession-notice-v1.0.md' \
    -E 'Architecture frozen\. Implementation must conform|All business APIs MUST require these headers|X-Tenant-ID: <uuid\|string>.*logical tenant/customer' \
    "$root/docs" 2>/dev/null)"; then
  echo "$legacy_hits" >&2
  fail "legacy controlling language remains outside the archive (matches above)"
fi

# 4. The unversioned pointer names v1.3.5 and links the versioned file.
if [[ -s "$root/$pointer_rel" ]]; then
  if ! grep -Fq 'hybrid-federated-target-architecture-v1.3.5.md' "$root/$pointer_rel" \
     || ! grep -Eq 'v1\.3\.3' "$root/$pointer_rel"; then
    fail "unversioned pointer does not point to v1.3.5: $pointer_rel"
  else
    echo "OK: pointer names v1.3.5"
  fi
else
  fail "missing unversioned pointer: $pointer_rel"
fi

# 5. The versioned v1.3.5 file is the complete document, not a pointer/stub.
if [[ -s "$root/$versioned_rel" ]]; then
  lines="$(wc -l < "$root/$versioned_rel")"
  if (( lines < min_versioned_lines )); then
    fail "versioned architecture is only a pointer or unexpectedly short ($lines lines < $min_versioned_lines): $versioned_rel"
  else
    echo "OK: versioned architecture is complete ($lines lines)"
  fi
fi

# 6. No superseded version is referenced as active outside the archive.
#    Historical references ("supersedes v1.3.1", "corrects v1.3.2", archive paths)
#    are legitimate; active-status phrasing and non-archive paths are not.
#    Add each newly superseded version here when the active version moves on —
#    v1.3.2 was added by v1.3.5, which found it cited as the controlling document
#    in four governance files after it had already been archived.
superseded=(1.3.1 1.3.2 1.3.3 1.3.4)
for v in "${superseded[@]}"; do
  ve="${v//./\\.}"
  if active_hits="$(grep -RIn --exclude-dir=archive --exclude-dir=prompts \
      -E "v${ve}[^.]{0,40}\b(is|remains)\b[^.]{0,40}\b(current|active|controlling|working)\b|hybrid-federated-target-architecture-v${ve}\.md" \
      "$root/docs" 2>/dev/null | grep -v 'archive/')"; then
    echo "$active_hits" >&2
    fail "v$v is referenced as active (or by non-archive path) outside the archive (matches above)"
  else
    echo "OK: v$v only historical outside archive"
  fi
done

# 6b. Every archived architecture draft carries a supersession banner in its first
#     20 lines. Without it the file reads as live architecture to anyone who opens
#     it directly — which is how a corrected schema gets implemented from a draft
#     that was corrected precisely because it was wrong.
arch_archive="$root/docs/architecture/archive"
if [[ -d "$arch_archive" ]]; then
  while IFS= read -r -d '' f; do
    head -3 "$f" | grep -Fq "$arch_h1" || continue
    if ! head -20 "$f" | grep -Fq 'SUPERSEDED'; then
      fail "archived architecture draft carries no supersession banner: ${f#"$root"/}"
    else
      echo "OK: banner present in ${f#"$root"/}"
    fi
  done < <(find "$arch_archive" -name '*.md' -print0 2>/dev/null)
fi

# 7. Exactly one complete active architecture copy exists outside the archive.
copies=()
while IFS= read -r -d '' f; do
  head -3 "$f" | grep -Fq "$arch_h1" || continue
  (( $(wc -l < "$f") >= copy_floor_lines )) || continue
  copies+=("$f")
done < <(find "$root/docs" -name '*.md' -not -path '*/archive/*' -print0 2>/dev/null)
if (( ${#copies[@]} != 1 )); then
  printf '%s\n' "${copies[@]:-<none>}" >&2
  fail "expected exactly 1 complete active architecture copy outside archive, found ${#copies[@]}"
else
  echo "OK: exactly one complete active architecture copy (${copies[0]#"$root"/})"
fi

# 8. v1.3.5-specific content invariants. The architecture is a governance artefact,
#    so these check the document says what the governed decisions require it to say.
#    They are deliberately content checks, not style checks: each one failed at least
#    once in a real review before it was written here.
A="$root/$versioned_rel"
if [[ -s "$A" ]]; then
  content_check() {  # name, grep-mode, pattern
    local name="$1" mode="$2" pat="$3"
    if grep -${mode}q -- "$pat" "$A"; then
      [[ "$mode" == *v* ]] && fail "$name" || echo "OK: $name"
    else
      [[ "$mode" == *v* ]] && echo "OK: $name" || fail "$name"
    fi
  }

  # 8a. Still a working draft. v1.3.5 is NOT frozen; freeze is a separate PO act.
  grep -Fq 'NOT architecture-frozen' "$A" \
    && echo "OK: v1.3.5 still marked NOT architecture-frozen" \
    || fail "v1.3.5 no longer states NOT architecture-frozen"
  grep -Eq 'ARCHITECTURE-FROZEN by Product Owner' "$A" \
    && fail "v1.3.5 claims frozen status; freeze is not authorised in this version" \
    || echo "OK: no premature freeze claim"

  # 8b. The contract version tracks the document version.
  grep -Fq '"contract_version": "1.3.5"' "$A" \
    && echo "OK: contract_version is 1.3.5" \
    || fail "contract_version is not 1.3.5"

  # 8c. C1 — the personal-domain block must be enforced on the OUTCOME. A guard on
  #     the input is what v1.3.3 had, and step 10 walked past it.
  grep -Fq 'assert_landing_permitted' "$A" \
    && echo "OK: outcome-level personal-domain assertion present" \
    || fail "C1 regression: no outcome-level landing assertion (assert_landing_permitted)"
  grep -Fq 'stage 2: constrain the outcome' "$A" \
    && echo "OK: precedence function is two-stage" \
    || fail "C1 regression: precedence function is no longer two-stage"

  # 8d. C2/C3 — the three withdrawn citations must not return. Anchored to the
  #     journey row so an unrelated mention of the test elsewhere does not trip it.
  for pair in "10:A78" "12:A38" "19:A44"; do
    j="${pair%%:*}"; t="${pair##*:}"
    if grep -Eq "^\| *$j \|.*\| *$t *\|" "$A"; then
      fail "journey $j cites $t again — withdrawn in v1.3.5 as an unrelated citation"
    else
      echo "OK: journey $j does not cite $t"
    fi
  done

  # 8e. All 24 journeys carry an assurance entry with a phase and a criterion.
  rows="$(awk '/^## 38A\.2/{f=1;next} /^## 38A\.3/{f=0} f && /^\| *[0-9]+ *\|/' "$A" | wc -l)"
  if (( rows == 24 )); then echo "OK: 24 journey assurance entries"; else
    fail "journey assurance matrix has $rows entries, expected 24"; fi

  # 8f. Nothing unimplemented may be labelled PASSING. The whole point of §38A is
  #     that a written criterion is not evidence.
  if awk '/^## 38A\.2/{f=1;next} /^## 38A\.3/{f=0} f' "$A" | grep -q 'PASSING'; then
    fail "a journey is labelled PASSING; no executable evidence exists in this version"
  else
    echo "OK: no journey falsely labelled PASSING"
  fi
  if grep -E '^\| \*\*A1(0[9]|1[0-7])\*\*' "$A" | grep -q 'Executable test: \*\*yes\*\*\|PASSING'; then
    fail "a specified-only criterion (A109-A117) claims executable evidence"
  else
    echo "OK: A109-A117 declare no executable evidence"
  fi

  # 8g. The pre-freeze implementation gate is still the active control.
  grep -Fq 'Implementation gate' "$A" \
    && echo "OK: pre-freeze implementation gate present" \
    || fail "the pre-freeze implementation gate has been removed"

  # 8h. Every acceptance id that is CITED must be DEFINED, and the defined set must be
  #     contiguous. Citing-vs-defining is the distinction that matters: v1.3.5's first
  #     draft of this check only asked whether an id appeared somewhere, so deleting a
  #     criterion's definition still passed because the journey table still cited it.
  #     A criterion cited by a journey but defined nowhere is precisely the defect
  #     §38A exists to prevent.
  defined="$(grep -oE '^\| \*\*A[0-9]{1,3}\*\* \|' "$A" | grep -oE '[0-9]+' | sort -n -u)"
  cited="$(grep -oE '\*\*A[0-9]{1,3}\*\*|\bA[0-9]{1,3}\b' "$A" | grep -oE '[0-9]+' | sort -n -u)"
  undefined="$(comm -13 <(echo "$defined") <(echo "$cited") | tr '\n' ' ')"
  if [[ -n "${undefined// /}" ]]; then
    fail "acceptance ids cited but never defined: $undefined"
  else
    echo "OK: every cited acceptance id is defined"
  fi
  gaps="$(echo "$defined" | awk 'NR>1{while(++p<$1) printf "%d ",p} {p=$1}')"
  if [[ -n "${gaps// /}" ]]; then fail "acceptance numbering has gaps: $gaps"; else
    echo "OK: defined acceptance criteria contiguous"; fi
fi

  # 8i. The §38A totals must be DERIVED from the matrix rows, not asserted beside them.
  #     v1.3.4 published 16/5/3 against rows holding 17/4/3. Both sum to 24, so every
  #     check that asked "does it total 24?" passed. A count that matches the expected
  #     total is not a count that matches the data.
  totals_ok=1
  rows_tmp="$(awk '/^## 38A\.2/{f=1;next} /^## 38A\.3/{f=0} f && /^\| *[0-9]+ \|/' "$A")"
  for st in SPECIFIED_NOT_IMPLEMENTED BLOCKED_BY_PHASE PARTIALLY_PROVEN \
            IMPLEMENTED_NOT_YET_PROVEN PASSING NOT_APPLICABLE_TO_CURRENT_RELEASE; do
    actual="$(printf '%s\n' "$rows_tmp" | grep -c "\`$st\`" || true)"
    stated="$(awk -v s="$st" '/^## 38A\.3/{f=1} f && $0 ~ "\\| `"s"` \\|" {gsub(/[^0-9]/,"",$0); print; exit}' "$A")"
    if [[ -z "$stated" ]]; then fail "§38A totals omit $st"; totals_ok=0
    elif (( stated != actual )); then
      fail "§38A totals: $st stated $stated, rows hold $actual"; totals_ok=0
    fi
  done
  (( totals_ok )) && echo "OK: §38A totals derived from the rows agree"

  # 8j. THE ENTAILMENT REGISTER (§38C). A citation is not a number: the register records
  #     the phrase from the cited criterion that entails the claim, and this check proves
  #     the phrase is really in that criterion. Every mis-citation this repository has
  #     produced (A38/A78/A44/A66/A87) passed existence, numbering and cross-reference
  #     checks. None of them could satisfy this one.
  ent_ok=1; ent_n=0
  while IFS='|' read -r _ claim crit phrase _; do
    crit="$(echo "$crit" | tr -d ' *`')"; [[ "$crit" =~ ^A[0-9]+$ ]] || continue
    phrase="$(echo "$phrase" | sed -e 's/^ *`//' -e 's/` *$//')"
    [[ -n "$phrase" ]] || continue
    ent_n=$((ent_n+1))
    row="$(grep -F "| **$crit** |" "$A" | head -1)"
    if [[ -z "$row" ]]; then
      fail "entailment register cites $crit, which is not defined"; ent_ok=0
    elif ! printf '%s' "$row" | grep -Fq -- "$phrase"; then
      fail "entailment broken: $crit does not contain \"$phrase\" (claim:$(echo "$claim" | cut -c1-40))"
      ent_ok=0
    fi
  done < <(awk '/^# 38C\./{f=1} /^# 39\./{f=0} f && /^\| §38[AB]-/' "$A")
  if (( ent_n == 0 )); then fail "entailment register is empty or missing (§38C)"
  elif (( ent_ok )); then echo "OK: entailment register verified ($ent_n citations)"; fi

if (( failed )); then
  echo "GOVERNANCE PACK: FAILED" >&2
else
  echo "GOVERNANCE PACK: OK"
fi
exit "$failed"
