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

versioned_rel="docs/architecture/hybrid-federated-target-architecture-v1.3.11.md"
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

# ── Block-scoped content assertions (v1.3.11, L1/L2) ────────────────────────
# A content guard must read the place it protects. Grepping the whole document
# looks equivalent and is not: the phrase survives in a change-log row describing
# the very correction the guard exists to defend, and the guard stays green while
# the rule is deleted. That was proven three times against v1.3.10 — including on
# §29.0's outcome-level landing rule, the most safety-critical control here.
#
# extract_block START END  — prints the block between the anchors. It performs NO
# validation and never calls fail(), because it runs inside a command substitution:
# a fail() there would set failed=1 in a SUBSHELL, print FAIL, and let the run exit 0.
# The first version of this helper did exactly that, and two red proofs reported a
# failure the exit code did not carry. Size validation therefore happens in the caller,
# in the parent shell, via assert_block_size.
extract_block() {
  local start="$1" end="$2"
  awk -v s="$start" -v e="$end" '
      index($0,s){f=1} f{print} f && NR>1 && index($0,e) && !index($0,s){exit}' "$A"
}

# assert_block_size BLOCK MIN MAX NAME — runs in the parent shell, so fail() counts.
assert_block_size() {
  local blk="$1" min="$2" max="$3" name="$4" n
  n="$(printf '%s\n' "$blk" | grep -c . || true)"
  if (( n < min || n > max )); then
    fail "block '$name' is $n lines, expected ${min}-${max} — its anchors have moved, so anything checked inside it was checked nowhere"
    return 1
  fi
  return 0
}

# assert_in_block BLOCK PHRASE MESSAGE — the phrase must appear in THAT block.
assert_in_block() {
  local blk="$1" phrase="$2" msg="$3"
  printf '%s' "$blk" | grep -Fq -- "$phrase" || { fail "$msg"; return 1; }
}

# §38C.4 rule 3 (v1.3.11): the suite asserts its own completeness. v1.3.7 made every
# individual guard prove it examined something, and left the aggregate case open:
# deleting a whole check block exited 0, the only signal being an absent OK line that
# nothing counted. "All checks passed" and "that check no longer exists" produced the
# same output — the defect this file has now produced six times, one level up.
#
# Each check calls record_check with its id exactly once. At the end the recorded set is
# compared against EXPECTED_CHECKS by NAME, so a deleted check is reported as missing
# rather than merely absent from a total. Adding a check means adding its id here — that
# edit is the point: a new guard must be declared to count.
EXPECTED_CHECKS=(
  required-files claude-import legacy-language pointer versioned-complete
  superseded-versions archive-banners active-copy-count
  contract-version outcome-landing-rule withdrawn-citations
  assurance-rows no-false-passing implementation-gate acceptance-ids
  assurance-totals entailment-register register-coverage positive-controls
  frozen-baseline frozen-digest stale-acceptance-range no-blanket-backfill adr55-decisions
  freeze-status-consistent
)
CHECKS_SEEN=""
record_check() {
  case " ${EXPECTED_CHECKS[*]} " in
    *" $1 "*) CHECKS_SEEN="$CHECKS_SEEN $1" ;;
    *) fail "record_check called with an undeclared id: $1" ;;
  esac
}

# Patterns whose expected result is EMPTY. A search meant to find nothing behaves
# identically when its pattern is broken, so each is defined ONCE here and used by both
# the real check and its positive control (§38C.4 rule 2). v1.3.11's first attempt gave
# the probe its own copy of the pattern, which proved only that a hardcoded string
# matches a hardcoded regex — the instrument supplying its own answer. Breaking the real
# pattern left the probe passing, so the control controlled nothing.
LEGACY_PATTERN='Architecture frozen\. Implementation must conform|All business APIs MUST require these headers|X-Tenant-ID: <uuid\|string>.*logical tenant/customer'
LEGACY_PROBE='Architecture frozen. Implementation must conform'
SUPERSEDED_BODY='[^.]{0,40}\b(is|remains)\b[^.]{0,40}\b(current|active|controlling|working)\b'
SUPERSEDED_PROBE_TAIL=' is the current working architecture'

# 1. Required governance files exist and are non-empty.
for path in "${required[@]}"; do
  if [[ ! -s "$root/$path" ]]; then
    fail "missing or empty: $path"
  else
    echo "OK: $path"
  fi
done
record_check required-files

# 2. Root CLAUDE.md imports the governance rules.
if [[ -s "$root/CLAUDE.md" ]] && ! grep -Fq '@docs/architecture/CLAUDE_GOVERNANCE.md' "$root/CLAUDE.md"; then
  fail "root CLAUDE.md does not import the governance rules"
fi
record_check claude-import

# 3. No controlling/frozen legacy language outside the archive (hard failure).
if legacy_hits="$(grep -RIn --exclude-dir=archive --exclude-dir=prompts \
    --exclude='supersession-notice-v1.0.md' \
    -E "$LEGACY_PATTERN" \
    "$root/docs" 2>/dev/null)"; then
  echo "$legacy_hits" >&2
  fail "legacy controlling language remains outside the archive (matches above)"
fi
record_check legacy-language

# 4. The unversioned pointer names v1.3.11 and links the versioned file.
if [[ -s "$root/$pointer_rel" ]]; then
  if ! grep -Fq 'hybrid-federated-target-architecture-v1.3.11.md' "$root/$pointer_rel" \
     || ! grep -Eq 'v1\.3\.3' "$root/$pointer_rel"; then
    fail "unversioned pointer does not point to v1.3.11: $pointer_rel"
  else
    echo "OK: pointer names v1.3.11"
  fi
else
  fail "missing unversioned pointer: $pointer_rel"
fi
record_check pointer

# 4b. No active artefact contradicts the freeze status.
#
# The architecture insists a version is either frozen or it is not. It stopped being able to
# answer that about itself: v1.3.11 was frozen (ADR-0054 froze v1.3.8; ADR-0055 amends the freeze
# forward to v1.3.11, and the digest check above locks it), while FOUR active artefacts still
# said "not architecture-frozen" — the pointer, ARCHITECTURE_PRECEDENCE.md, README.md and
# ADR-0055's own consequences paragraph, which named v1.3.9, a version the same ADR records as
# REFUSED freeze. The pointer is the file a reader opens first, so the estate's most-read
# statement of status was the wrong one.
#
# Editing those four fixed the instance. This fixes the class: the status now has to stay
# consistent across every active artefact, or the pack fails.
#
# Scoped deliberately. `archive/` is excluded because a superseded draft SHOULD record that it was
# never frozen — that is history, not drift. `prompts/` is excluded for the same reason: those
# files quote the status as it stood for the version they were written against.
freeze_scope=()
while IFS= read -r f; do freeze_scope+=("$f"); done < <(
  find "$root/docs/architecture" -maxdepth 2 -name '*.md' \
       -not -path '*/archive/*' -not -path '*/prompts/*' | sort
)
if (( ${#freeze_scope[@]} < 3 )); then
  # A guard that scans nothing reports OK. Assert the scan found the files it exists to check.
  fail "freeze-status scan found only ${#freeze_scope[@]} active architecture docs — expected at least 3"
else
  contradictions=""
  for f in "${freeze_scope[@]}"; do
    if grep -Eqi 'not architecture-frozen|remains not[[:space:]]+architecture-frozen' "$f"; then
      contradictions="$contradictions ${f#"$root/"}"
    fi
  done
  # ADR-0055 must name the version it actually amends to, not the one it records as refused.
  if [[ -s "$root/docs/architecture/adr/ADR-0055-trust-domain-membership-bootstrap.md" ]] \
     && grep -Fq 'v1.3.9 is frozen' "$root/docs/architecture/adr/ADR-0055-trust-domain-membership-bootstrap.md"; then
    contradictions="$contradictions docs/architecture/adr/ADR-0055-trust-domain-membership-bootstrap.md(names-v1.3.9-as-frozen)"
  fi
  if [[ -n "$contradictions" ]]; then
    fail "active artefacts contradict the freeze status of v1.3.11:$contradictions"
  else
    echo "OK: freeze status consistent across ${#freeze_scope[@]} active architecture docs"
  fi
fi
record_check freeze-status-consistent

# 5. The versioned v1.3.11 file is the complete document, not a pointer/stub.
if [[ -s "$root/$versioned_rel" ]]; then
  lines="$(wc -l < "$root/$versioned_rel")"
  if (( lines < min_versioned_lines )); then
    fail "versioned architecture is only a pointer or unexpectedly short ($lines lines < $min_versioned_lines): $versioned_rel"
  else
    echo "OK: versioned architecture is complete ($lines lines)"
  fi
fi
record_check versioned-complete

# 6. No superseded version is referenced as active outside the archive.
#    Historical references ("supersedes v1.3.1", "corrects v1.3.2", archive paths)
#    are legitimate; active-status phrasing and non-archive paths are not.
#    Add each newly superseded version here when the active version moves on —
#    v1.3.2 was added by v1.3.11, which found it cited as the controlling document
#    in four governance files after it had already been archived.
superseded=(1.3.1 1.3.2 1.3.3 1.3.4 1.3.5 1.3.6 1.3.7 1.3.8 1.3.9 1.3.10)
for v in "${superseded[@]}"; do
  ve="${v//./\\.}"
  if active_hits="$(grep -RIn --exclude-dir=archive --exclude-dir=prompts \
      --exclude-dir=adr \
      -E "v${ve}${SUPERSEDED_BODY}|hybrid-federated-target-architecture-v${ve}\.md" \
      "$root/docs" 2>/dev/null | grep -v 'archive/')"; then
    echo "$active_hits" >&2
    fail "v$v is referenced as active (or by non-archive path) outside the archive (matches above)"
  else
    echo "OK: v$v only historical outside archive"
  fi
done
record_check superseded-versions

# 6b. Every archived architecture draft carries a supersession banner in its first
#     20 lines. Without it the file reads as live architecture to anyone who opens
#     it directly — which is how a corrected schema gets implemented from a draft
#     that was corrected precisely because it was wrong.
arch_archive="$root/docs/architecture/archive"
# §38C.4 rule 1 again: this loop's scope is a find result. v1.3.6 wrapped it in a bare
# `if [[ -d ]]` with no else, so relocating or renaming the archive skipped every banner
# check in silence. Count what was examined and require at least one.
banner_checked=0
if [[ ! -d "$arch_archive" ]]; then
  fail "archive directory not found at docs/architecture/archive — banner checks examined nothing"
else
  while IFS= read -r -d '' f; do
    head -3 "$f" | grep -Fq "$arch_h1" || continue
    if ! head -20 "$f" | grep -Fq 'SUPERSEDED'; then
      fail "archived architecture draft carries no supersession banner: ${f#"$root"/}"
    else
      echo "OK: banner present in ${f#"$root"/}"
    fi
    banner_checked=$((banner_checked+1))
  done < <(find "$arch_archive" -name '*.md' -print0 2>/dev/null)
  if (( banner_checked == 0 )); then
    fail "archive exists but no archived architecture draft was examined for a supersession banner"
  fi
fi
record_check archive-banners

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
record_check active-copy-count

# 8. v1.3.11-specific content invariants. The architecture is a governance artefact,
#    so these check the document says what the governed decisions require it to say.
#    They are deliberately content checks, not style checks: each one failed at least
#    once in a real review before it was written here.
A="$root/$versioned_rel"
if [[ ! -s "$A" ]]; then
  fail "versioned architecture missing or empty — the twelve content checks cannot run"
fi
if [[ -s "$A" ]]; then
  content_check() {  # name, grep-mode, pattern
    local name="$1" mode="$2" pat="$3"
    if grep -${mode}q -- "$pat" "$A"; then
      [[ "$mode" == *v* ]] && fail "$name" || echo "OK: $name"
    else
      [[ "$mode" == *v* ]] && echo "OK: $name" || fail "$name"
    fi
  }

  # 8a. FROZEN BASELINE (ADR-0054, 2026-08-05). Before freeze this check asserted the
  #     document still said NOT architecture-frozen; it now asserts the opposite, plus
  #     the things a freeze must not lose: the approval date, the ADR reference, the
  #     freeze-is-not-implementation distinction, and that no earlier version is
  #     described as frozen. An architecture that quietly loses its own freeze record is
  #     indistinguishable from one that was never frozen.
  grep -Fq 'Status: APPROVED — ARCHITECTURE-FROZEN by Product Owner on 2026-08-05 · Version: 1.3.11' "$A" \
    && echo "OK: v1.3.11 carries the approved/frozen status line" \
    || fail "v1.3.11 has lost its APPROVED/ARCHITECTURE-FROZEN status line"
  grep -Fq 'ADR-0054-architecture-freeze-v1.3.8.md' "$A" \
    && echo "OK: freeze ADR referenced" \
    || fail "the freeze ADR reference (ADR-0054) has disappeared from the architecture"
  grep -Fq 'ADR-0055-trust-domain-membership-bootstrap.md' "$A" \
    && echo "OK: amendment ADR referenced" \
    || fail "the amendment ADR reference (ADR-0055) has disappeared from the architecture"
  for adr in ADR-0054-architecture-freeze-v1.3.8 ADR-0055-trust-domain-membership-bootstrap; do
    [[ -s "$root/docs/architecture/adr/${adr}.md" ]] \
      && echo "OK: ${adr} present" \
      || fail "docs/architecture/adr/${adr}.md is missing or empty"
  done
  grep -Fq 'It does not constitute implementation, runtime acceptance, production readiness or deployment authorisation' "$A" \
    && echo "OK: freeze-is-not-implementation distinction present" \
    || fail "the freeze-versus-implementation distinction has been removed"
  # No EARLIER version may be described as frozen. Literal phrasings, deliberately:
  #     the first draft of this check used nested bounded quantifiers over table rows
  #     and backtracked catastrophically — a check that hangs is a check that will be
  #     removed. Three fixed forms cover the assertion; "never frozen" is the correct
  #     phrasing everywhere and is not matched by any of them.
  earlier_frozen=0
  # v1.3.8 is deliberately absent from this list: it WAS genuinely frozen under ADR-0054,
  # and saying so is accurate rather than a defect. v1.3.1-v1.3.7 were each refused, so
  # describing any of them as frozen would be false.
  for v in 1.3.1 1.3.2 1.3.3 1.3.4 1.3.5 1.3.6 1.3.7 1.3.9 1.3.10; do
    for form in "v$v is frozen" "v$v was frozen" "v$v remains frozen" "v$v is architecture-frozen" "v$v was architecture-frozen"; do
      if grep -Fq -- "$form" "$A"; then
        fail "an earlier version is described as frozen (\"$form\"); v1.3.1-v1.3.7 were never frozen"
        earlier_frozen=1
      fi
    done
  done
  (( earlier_frozen )) || echo "OK: no earlier version described as frozen"

  # [L] matters must never be described as settled in the aggregate. Again literal:
  #     individual sentences legitimately say a SPECIFIC question is undetermined, and
  #     §26.3 records questions that WERE settled in earlier versions by ADR — neither
  #     is what this guards against. What it guards against is a blanket claim.
  l_settled=0
  for form in "[L] matters are settled" "[L] matters are resolved" "[L] matters are now settled" \
              "[L] matters have been resolved" "all [L] matters are" "the [L] questions are settled" \
              "legal matters are settled" "legal determinations are complete"; do
    if grep -Fq -- "$form" "$A"; then
      fail "an [L] matter is described as settled (\"$form\"); freeze decides no legal determination"
      l_settled=1
    fi
  done
  (( l_settled )) || echo "OK: [L] matters remain unresolved"
  record_check frozen-baseline


  # 8b. The contract version tracks the document version.
  grep -Fq '"contract_version": "1.3.11"' "$A" \
    && echo "OK: contract_version is 1.3.11" \
    || fail "contract_version is not 1.3.11"
  record_check contract-version

  # 8c. C1 — the personal-domain block must be enforced on the OUTCOME. A guard on
  #     the input is what v1.3.3 had, and step 10 walked past it.
  # Scoped to the precedence function itself. v1.3.10 grepped the whole file, so
  # renaming the assertion away still passed on the strength of a change-log row.
  c1_blk="$(extract_block 'resolve_landing(vector, requested):' 'return the candidate only if')"
  if assert_block_size "$c1_blk" 20 80 'precedence function'; then
    assert_in_block "$c1_blk" 'assert_landing_permitted' \
      "C1 regression: the precedence function no longer calls assert_landing_permitted" \
      && echo "OK: outcome-level personal-domain assertion present in the precedence function"
  fi
  grep -Fq 'stage 2: constrain the outcome' "$A" \
    && echo "OK: precedence function is two-stage" \
    || fail "C1 regression: precedence function is no longer two-stage"
  record_check outcome-landing-rule

  # 8d. C2/C3 — the three withdrawn citations must not return. Anchored to the
  #     journey row so an unrelated mention of the test elsewhere does not trip it.
  for pair in "10:A78" "12:A38" "19:A44"; do
    j="${pair%%:*}"; t="${pair##*:}"
    if grep -Eq "^\| *$j \|.*\| *$t *\|" "$A"; then
      fail "journey $j cites $t again — withdrawn in v1.3.11 as an unrelated citation"
    else
      echo "OK: journey $j does not cite $t"
    fi
  done
  record_check withdrawn-citations

  # 8e. All 24 journeys carry an assurance entry with a phase and a criterion.
  rows="$(awk '/^## 38A\.2/{f=1;next} /^## 38A\.3/{f=0} f && /^\| *[0-9]+ *\|/' "$A" | wc -l)"
  if (( rows == 24 )); then echo "OK: 24 journey assurance entries"; else
    fail "journey assurance matrix has $rows entries, expected 24"; fi
  record_check assurance-rows

  # 8f. Nothing unimplemented may be labelled PASSING. The whole point of §38A is
  #     that a written criterion is not evidence.
  # §38C.4 rule 1: an absence-of-PASSING scan over an empty range also finds no PASSING.
  matrix_scan="$(awk '/^## 38A\.2/{f=1;next} /^## 38A\.3/{f=0} f' "$A" | wc -l)"
  if (( matrix_scan == 0 )); then
    fail "the §38A.2 matrix range is empty — the PASSING scan examined nothing"
  elif awk '/^## 38A\.2/{f=1;next} /^## 38A\.3/{f=0} f' "$A" | grep -q 'PASSING'; then
    fail "a journey is labelled PASSING; no executable evidence exists in this version"
  else
    echo "OK: no journey falsely labelled PASSING ($matrix_scan lines scanned)"
  fi
  record_check no-false-passing
  if grep -E '^\| \*\*A1(0[9]|1[0-7])\*\*' "$A" | grep -q 'Executable test: \*\*yes\*\*\|PASSING'; then
    fail "a specified-only criterion (A109-A117) claims executable evidence"
  else
    echo "OK: A109-A117 declare no executable evidence"
  fi

  # 8g. The implementation-control section is present and substantive.
  #
  #     This check was VACUOUS from v1.3.8 to v1.3.11. It grepped for the literal
  #     'Implementation gate', which v1.3.8 renamed to 'Post-freeze implementation
  #     control'. The phrase survived only in the F5 change-log row, so the check
  #     passed on a historical sentence and asserted nothing about the live section
  #     for four versions — while still counting toward "all N declared checks ran".
  #     A check can outlive its guard by having its SUBJECT renamed, not only by
  #     having its guard removed.
  #
  #     Scoped and size-asserted like every other content guard: the section must
  #     exist, be of the expected size, and still carry the three columns that make
  #     it a control rather than a heading.
  ig_blk="$(extract_block '### Post-freeze implementation control' 'On the acceptance criteria.')"
  if assert_block_size "$ig_blk" 15 45 'post-freeze implementation control'; then
    ig_ok=1
    for required in 'Now eligible for a' 'Still blocked until phase and dependency gates' \
                    'Eligibility is not authorisation' \
                    'It does not constitute implementation, runtime acceptance'; do
      assert_in_block "$ig_blk" "$required" \
        "implementation control has lost its '$required' element" || ig_ok=0
    done
    (( ig_ok )) && echo "OK: post-freeze implementation control present and substantive"
  fi
  record_check implementation-gate

  # 8h. Every acceptance id that is CITED must be DEFINED, and the defined set must be
  #     contiguous. Citing-vs-defining is the distinction that matters: v1.3.11's first
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
  record_check acceptance-ids

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
  record_check assurance-totals

  # 8j. THE ENTAILMENT REGISTER (§38C). A citation is not a number: the register records
  #     the phrase from the cited criterion that entails the claim, and this check proves
  #     the phrase is really in that criterion. Every mis-citation this repository has
  #     produced (A38/A78/A44/A66/A87/A43) passed existence, numbering and cross-reference
  #     checks. None can satisfy this one.
  #
  #     v1.3.5's version of this check read the row with IFS='|', so a '|' inside the
  #     quoted phrase truncated what the machine verified while a human read the whole
  #     cell — and the v1.3.4 mis-citation could be restored with the verifier green.
  #     A checker that reads different text from its reader certifies what it never
  #     examined. Hence rule 1: the row must have exactly four cells, so a pipe in the
  #     phrase is a hard failure rather than a silent truncation.
  ent_ok=1; ent_n=0
  while IFS= read -r line; do
    # rule 1: exactly 4 cells. "| a | b | c |" splits to 5 fields incl. the empty ends.
    nf="$(printf '%s' "$line" | awk -F'|' '{print NF}')"
    if [[ "$nf" != "5" ]]; then
      fail "entailment register row is malformed ($((nf-2)) cells, expected 3 — a '|' inside the phrase?): $(printf '%s' "$line" | cut -c1-60)"
      ent_ok=0; continue
    fi
    claim="$(printf '%s' "$line" | awk -F'|' '{print $2}')"
    crit="$(printf  '%s' "$line" | awk -F'|' '{gsub(/[ *`]/,"",$3); print $3}')"
    cell="$(printf  '%s' "$line" | awk -F'|' '{print $4}')"
    [[ "$crit" =~ ^A[0-9]+$ ]] || { fail "entailment register: '$crit' is not a criterion id"; ent_ok=0; continue; }
    # rule 1 (cont.): the phrase cell is one backticked string and nothing else.
    if ! printf '%s' "$cell" | grep -Eq '^ *`[^`]+` *$'; then
      fail "entailment phrase for $crit is not a single backticked string"; ent_ok=0; continue
    fi
    phrase="$(printf '%s' "$cell" | sed -e 's/^ *`//' -e 's/` *$//')"
    ent_n=$((ent_n+1))
    row="$(grep -F "| **$crit** |" "$A" | head -1)"
    if [[ -z "$row" ]]; then
      fail "entailment register cites $crit, which is not defined"; ent_ok=0; continue
    fi
    if ! printf '%s' "$row" | grep -Fq -- "$phrase"; then
      fail "entailment broken: $crit does not contain \"$phrase\" (claim:$(printf '%s' "$claim" | cut -c1-40))"
      ent_ok=0; continue
    fi
    # rule 2: the phrase must be unique to one criterion, or it proves nothing about
    #     which was cited. "Both refused" occurs in three criteria and is unusable.
    hits="$(grep -E '^\| \*\*A[0-9]+\*\* \|' "$A" | grep -Fc -- "$phrase" || true)"
    if (( hits != 1 )); then
      fail "entailment phrase for $crit matches $hits criteria — it must identify exactly one"
      ent_ok=0
    fi
  done < <(awk '/^# 38C\./{f=1} /^# 39\./{f=0} f && /^\| §38[AB]-/' "$A")
  if (( ent_n == 0 )); then fail "entailment register is empty or missing (§38C)"
  elif (( ent_ok )); then echo "OK: entailment register verified ($ent_n citations, all unique)"; fi
  record_check entailment-register

  # 8k. Every criterion cited by §38B must appear in the register. v1.3.5 left seven
  #     unregistered; all seven happened to be sound, which is not the same as checked.
  #
  #     §38C.4 rule 1: this check derives its scope from an anchored range, so it MUST
  #     assert the scope is non-empty and of the expected size. v1.3.6's version did not:
  #     renaming the table header column from "| # |" to "| No. |" made the range match
  #     nothing, and it printed "OK: every §38B citation is registered" with a
  #     registration deleted. "Found nothing wrong" and "looked at nothing" must never
  #     produce the same output.
  outcome_rows="$(awk '/^\| *#? *N?o?\.? *\| Prohibited outcome/{f=1} /^\*\*/{f=0} f && /^\| *[0-9]+ *\|/' "$A" | wc -l)"
  if (( outcome_rows != 11 )); then
    fail "§38B prohibited-outcome table: found $outcome_rows rows, expected 11 (has the table header or its anchor changed?)"
  else
    cited_38b="$(awk '/^\| *#? *N?o?\.? *\| Prohibited outcome/{f=1} /^\*\*/{f=0} f && /^\| *[0-9]+ *\|/' "$A" \
                 | awk -F'|' '{print $6}' | grep -oE 'A[0-9]+' | sort -u)"
    n_cited="$(printf '%s\n' "$cited_38b" | grep -c . || true)"
    if (( n_cited < 11 )); then
      fail "§38B cites only $n_cited distinct criteria across 11 outcomes — the criterion column is not being read"
    else
      unreg=""
      for c in $cited_38b; do
        grep -E '^\| §38[AB]-' "$A" | awk -F'|' '{gsub(/[ *`]/,"",$3); print $3}' | grep -qx "$c" || unreg="$unreg $c"
      done
      if [[ -n "${unreg// /}" ]]; then fail "§38B cites criteria absent from the entailment register:$unreg"
      else echo "OK: every §38B citation is registered ($n_cited criteria over $outcome_rows outcomes)"; fi
    fi
  fi
  record_check register-coverage

  # 8l. §38C.4 rule 2: the two checks whose expected result is EMPTY cannot assert
  #     non-emptiness, so they carry a positive control. A broken pattern and a clean
  #     repository are indistinguishable to a search that is supposed to find nothing.
  if printf '%s\n' "$LEGACY_PROBE" | grep -Eq "$LEGACY_PATTERN"; then
    echo "OK: positive control — LEGACY_PATTERN still matches known-bad text"
  else
    fail "positive control FAILED: LEGACY_PATTERN no longer matches its own example — check 3 is silently disarmed"
  fi
  if printf '%s\n' "v1.3.1${SUPERSEDED_PROBE_TAIL}" | grep -Eq "v1\.3\.1${SUPERSEDED_BODY}"; then
    echo "OK: positive control — SUPERSEDED_BODY still matches known-bad text"
  else
    fail "positive control FAILED: SUPERSEDED_BODY no longer matches its own example — check 6 is silently disarmed"
  fi
  record_check positive-controls

  # 8m. FROZEN-CONTENT INTEGRITY (ADR-0054). Recompute the digest of the frozen baseline
  #     and compare it to the manifest. A frozen document that can be edited without
  #     anyone noticing is not frozen — it is labelled frozen, which is worse, because
  #     the label is relied upon. The manifest states how to change it legitimately:
  #     a substantive change means a new version; a governed erratum updates the digest
  #     in the same commit with its justification.
  manifest="$root/docs/architecture/FROZEN-BASELINE.sha256"
  if [[ ! -s "$manifest" ]]; then
    fail "frozen-content manifest missing: docs/architecture/FROZEN-BASELINE.sha256"
  else
    recorded="$(grep -v '^#' "$manifest" | grep -oE '^[0-9a-f]{64}' | head -1)"
    actual="$(sha256sum "$A" | cut -d' ' -f1)"
    if [[ -z "$recorded" ]]; then
      fail "frozen-content manifest contains no digest"
    elif [[ "$recorded" != "$actual" ]]; then
      fail "FROZEN CONTENT CHANGED — v1.3.11 no longer matches its manifest digest (recorded ${recorded:0:12}…, actual ${actual:0:12}…). A substantive change requires a new version; a governed erratum must update the manifest in the same commit."
    else
      echo "OK: frozen baseline matches its manifest digest (${actual:0:12}…)"
    fi
  fi
  record_check frozen-digest

  # 8o. ADR-0055: the rejected blanket backfill must not return to the architecture.
  #     "backfill every existing organisation -> the MoHCC trust domain" is the exact
  #     instruction that would fabricate membership for private-provider and
  #     local-authority organisations before anyone determined it.
  if grep -E '^organisation ADD COLUMN trust_domain_id UUID NOT NULL' "$A" | grep -qv '^--'; then
    fail "ADR-0055 regression: organisation.trust_domain_id is NOT NULL again; membership must stay evidenced"
  elif grep -E '^-- Backfill: every existing organisation' "$A" >/dev/null 2>&1; then
    fail "ADR-0055 regression: the blanket MoHCC backfill instruction has returned"
  else
    echo "OK: no blanket trust-domain backfill; membership stays evidenced"
  fi
  record_check no-blanket-backfill

  # 8p. ADR-0055 decisions 1, 3, 5 and 6, made mechanical (v1.3.11, K2).
  #     v1.3.9 stated all four and guarded none: every one of the four mutations below
  #     passed the verifier green. A decision with no guard is a sentence.
  adr55_ok=1

  # (1) Descriptive controller metadata must never be a resolution fallback.
  if grep -Eqi 'controller_type|data_controller_legal_name|data_controller_contact' "$A" \
     && grep -Eqi '(permitted|allowed|acceptable|valid) fallback when controller resolution' "$A"; then
    fail "ADR-0055 d1 regression: descriptive trust-domain metadata is described as a controller-resolution fallback"
    adr55_ok=0
  fi
  # Scoped to trust_domain's own definition. 'never load-bearing' also appears at A116
  # and in the §38C register, so a whole-file grep stayed green with all three markings
  # deleted — proven against v1.3.10.
  td_blk="$(extract_block 'trust_domain (' 'created_by_actor, version INT NOT NULL')"
  if assert_block_size "$td_blk" 15 45 'trust_domain definition'; then
    n_marked="$(printf '%s' "$td_blk" | grep -c 'never load-bearing' || true)"
    if (( n_marked < 3 )); then
      fail "ADR-0055 d1 regression: only $n_marked of 3 descriptive controller fields are marked non-authoritative"
      adr55_ok=0
    fi
  else
    adr55_ok=0
  fi
  if grep -Eq '^  controller_type +VARCHAR\(32\) NOT NULL' "$A"; then
    fail "ADR-0055 d1 regression: controller_type is NOT NULL again; all three descriptive fields must be nullable"
    adr55_ok=0
  fi

  # (2) Membership must never be inferred from an attribute of the subject.
  if grep -Eqi '(organisations?|facilit(y|ies)) of type [A-Z_]+ (map|are mapped|belong)' "$A"; then
    fail "ADR-0055 d3 regression: membership is inferred from organisation or facility type"
    adr55_ok=0
  fi
  grep -Fq 'MEMBERSHIP MAY NEVER BE INFERRED' "$A" \
    || { fail "ADR-0055 d3 regression: the inference prohibition has been removed from the controlling document"; adr55_ok=0; }
  # Scope the check to the prohibition block itself. The first version grepped the whole
  # file, so deleting a source from the list still passed while the phrase survived in
  # prose elsewhere — the guard was reading a different place from the one it protects.
  inference_block="$(extract_block 'MEMBERSHIP MAY NEVER BE INFERRED' 'recorded in provenance.')"
  if ! assert_block_size "$inference_block" 8 20 'inference-prohibition block'; then
    adr55_ok=0
  else
    for src in 'organisation type' 'facility type' 'hosting location' 'platform operator' \
               'infrastructure operator' 'name or code prefix' 'legacy tenant values' \
               'national-spine' 'facility ownership' 'organisation ownership' 'regulatory status'; do
      printf '%s' "$inference_block" | grep -Fq "$src" \
        || { fail "ADR-0055 d3 regression: '$src' is no longer listed among the prohibited inference sources"; adr55_ok=0; }
    done
  fi

  # (3) UNMAPPED is fail-closed and visible — never blank, never read as MoHCC.
  grep -Fq 'UNMAPPED IS FAIL-CLOSED AND VISIBLE' "$A" \
    || { fail "ADR-0055 d6 regression: the fail-closed UNMAPPED rule has been removed"; adr55_ok=0; }
  fc_blk="$(extract_block 'UNMAPPED IS FAIL-CLOSED AND VISIBLE' 'silently read as MoHCC membership')"
  if assert_block_size "$fc_blk" 6 20 'fail-closed block'; then
    for code in TRUST_DOMAIN_UNMAPPED TRUST_DOMAIN_MEMBERSHIP_PENDING TRUST_DOMAIN_MEMBERSHIP_INACTIVE; do
      assert_in_block "$fc_blk" "$code" \
        "ADR-0055 d6 regression: refusal code $code is missing from the fail-closed rule" || adr55_ok=0
    done
  else
    adr55_ok=0
  fi
  if grep -Eqi 'blank means (MOHCC|MoHCC)|UNMAPPED (means|is read as) .*MOHCC' "$A"; then
    fail "ADR-0055 d6 regression: UNMAPPED is treated as blank or as MoHCC membership"
    adr55_ok=0
  fi
  if grep -Eq '^  status +VARCHAR\(16\) NULL' "$A"; then
    fail "ADR-0055 d6 regression: membership status is nullable; UNMAPPED must be an explicit value"
    adr55_ok=0
  fi

  # (4) The denormalised trust_domain_id is a projection, never authority.
  # Scoped to the projection note. 'never the source' also appears in the change log
  # describing this very correction — a whole-file grep read that instead of the rule.
  proj_blk="$(extract_block 'MAY carry a denormalised trust_domain_id as a QUERY' 'never populated by backfill')"
  if assert_block_size "$proj_blk" 2 8 'projection note'; then
    assert_in_block "$proj_blk" 'never the source' \
      "ADR-0055 d5 regression: the projection is no longer marked as not the source of truth" || adr55_ok=0
  else
    adr55_ok=0
  fi
  if grep -Eqi 'trust_domain_id[^.]{0,60}(is|as) the authoritative source|authoritative source of trust-domain membership' "$A"; then
    fail "ADR-0055 d5 regression: the projection trust_domain_id is described as authoritative"
    adr55_ok=0
  fi

  (( adr55_ok )) && echo "OK: ADR-0055 decisions 1, 3, 5 and 6 are enforced"
  record_check adr55-decisions

  # 8n. The superseded acceptance range A87-A108 must not reappear in an ACTIVE
  #     governing statement. §23.7 added A109-A117, and two live statements still used
  #     the old range at freeze review — the pre-freeze erratum of 2026-08-05. A
  #     historical sentence describing what an earlier version said is legitimate and is
  #     excluded by requiring the match to sit outside a change-log row.
  if grep -n 'A87–A108' "$A" | grep -vqE '^\s*[0-9]+:\| \*\*[A-Z][0-9]+\*\* \|'; then
    grep -n 'A87–A108' "$A" | grep -vE '^\s*[0-9]+:\| \*\*[A-Z][0-9]+\*\* \|' | head -3 >&2
    fail "the superseded acceptance range A87–A108 appears in an active statement; §23.7 extended it to A117"
  else
    echo "OK: no active use of the superseded range A87–A108"
  fi
  record_check stale-acceptance-range
fi

missing_checks=""
for c in "${EXPECTED_CHECKS[@]}"; do
  case " $CHECKS_SEEN " in *" $c "*) ;; *) missing_checks="$missing_checks $c" ;; esac
done
n_seen="$(printf '%s\n' $CHECKS_SEEN | grep -c . || true)"
if [[ -n "${missing_checks// /}" ]]; then
  fail "check suite incomplete — these declared checks did not run:$missing_checks"
else
  echo "OK: all ${#EXPECTED_CHECKS[@]} declared checks ran ($n_seen/${#EXPECTED_CHECKS[@]})"
fi

if (( failed )); then
  echo "GOVERNANCE PACK: FAILED" >&2
else
  echo "GOVERNANCE PACK: OK"
fi
exit "$failed"
