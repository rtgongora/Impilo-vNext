#!/usr/bin/env bash
# Confidential-lane routing: a stamped reproductive record may only be served from a confidential path.
#
# THE TRAP THIS EXISTS FOR. tshepo-authz decides confidentiality by a PATH SUBSTRING.
# ResourceSensitivityClassifier.PROTECTED_LANE_MARKERS is {confidential, safeguarding,
# protected-disclosure}, and every rule seeded by tshepo-authz V048 is pinned with
# "path_contains": "/confidential/". A route that does not carry one of those markers is never
# classified SPECIALLY_PROTECTED, so the PDP returns NO confidentialCategories at all.
#
# SpeciallyProtectedVisibilityGuard fails CLOSED — deliberately, because for the highest
# confidentiality class silence must not mean disclosure. Put those two facts together and a
# reproductive controller mounted at, say, /v1/pregnancy/... would, after the governance flip,
# withhold EVERY STAMPED RECORD FROM EVERY REQUESTER — including the midwife who wrote it. The
# service would be healthy, the tests green, and the ward would simply stop seeing its own records.
#
# No unit test surfaces this, because each half is correct on its own. It is only wrong in
# combination, and only after a flip that happens in a different repository layer. Hence a build gate.
#
# WHAT IS CHECKED. A controller that handles one of the stamped RECORD TYPES must be mounted on a path
# containing a lane marker. Detection is by ENTITY IMPORT, not by field name, and that distinction is
# load-bearing: ProgrammeEnrolmentController legitimately exposes a pregnancyEpisodeId POINTER (the
# V111 one-directional seam agreed with the Adult Medicine lane) without ever handling the episode
# record. A name-based check would fire on that and teach people to route around this guard.
#
# THE SAME TRAP ONE LAYER UP (experience-bff). The PDP classifies the path THE CLIENT CALLS, which for
# a web or mobile caller is the BFF path — not the pct path behind it. So a BFF route mounted at, say,
# /internal/v1/maternity/... that proxies pct's /v1/confidential/... is never classified
# SPECIALLY_PROTECTED either: the PDP mints no confidentialCategories for the BFF path, and once
# obligation propagation is enabled the BFF forwards an obligation containing no grant, so pct's
# fail-closed guard withholds exactly as if nothing had been forwarded at all. Until this section
# existed the guard's glob was pct-only, so a BFF route laundering a confidential record onto an
# ordinary path was invisible to it. Detection here is by DOWNSTREAM PATH, resolved one hop through the
# client/service classes a controller uses, because the BFF speaks in maps and DTOs and imports no
# stamped entity.
set -uo pipefail
# Resolve absolutely, BEFORE the cd below: a relative $0 (bash check-...sh from this directory)
# would otherwise resolve against REPO_PATH and fail to find the helper.
_GUARD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_PATH="${REPO_PATH:-$(cd "$_GUARD_DIR/../.." && pwd)}"
cd "$REPO_PATH"
# guard_assert_scanned lives in the shared helper, which sets -e. This guard deliberately does not
# use -e — it reports every offending controller before exiting — so restore its own error mode.
source "$_GUARD_DIR/_guard-common.sh"
set +e

echo "=== Confidential-lane routing guard ==="

# The record types carrying a confidentiality stamp (pct V437).
STAMPED_ENTITIES='PregnancyLossRecordEntity|TopAuthorisationEntity|TopProcedureEntity|PostnatalContactEntity|ContraceptiveEpisodeEntity|PregnancyEpisodeEntity'

# Must match ResourceSensitivityClassifier.PROTECTED_LANE_MARKERS.
LANE_MARKERS='confidential|safeguarding|protected-disclosure'

# Tracked AND untracked-but-not-ignored. `git ls-files` alone lists only TRACKED files, which makes a
# pre-commit guard blind to the newly-added file it exists to catch — a brand-new controller is
# untracked at exactly the moment this check matters. That blindness was demonstrated: the first cut
# of this guard passed cleanly against a deliberately-planted offending controller. Same failure shape
# as check-top-no-record-level-emit.sh, which only saw its target once the migration was committed.
CONTROLLERS=$(git ls-files --cached --others --exclude-standard -- 'services/pct-service/**/*.java' \
  | xargs -r grep -lE '@RestController|@Controller' 2>/dev/null || true)

# Self-reach, part 1: pct-service always has controllers, so zero means the glob stopped reaching
# the tree — and this guard would then report "all are mounted on a confidential lane" about nothing.
guard_assert_scanned "$(printf '%s\n' "$CONTROLLERS" | grep -c .)" \
  "pct-service controllers" || {
  echo "Confidential-lane routing guard: FAILED"
  exit 1
}

FAIL=0
CHECKED=0
for f in $CONTROLLERS; do
    # Does this controller actually HANDLE a stamped record type? Import, not mention.
    if ! grep -qE "^import .*\.($STAMPED_ENTITIES);" "$f" 2>/dev/null; then
        continue
    fi
    CHECKED=$((CHECKED + 1))

    # Every path this controller is mounted on: the class-level @RequestMapping plus any
    # method-level mapping value. If the class mapping carries a marker, the whole controller is fine.
    CLASS_PATH=$(grep -oE '@RequestMapping\("[^"]*"' "$f" | head -1 | sed 's/@RequestMapping("//; s/"$//')
    if echo "$CLASS_PATH" | grep -qiE "$LANE_MARKERS"; then
        continue
    fi

    echo "FAIL: $f"
    echo "      handles a confidentiality-stamped record but is mounted at '${CLASS_PATH:-<no class-level @RequestMapping>}'"
    echo "      which carries no confidential-lane marker ($LANE_MARKERS)."
    grep -nE "^import .*\.($STAMPED_ENTITIES);" "$f" | sed 's/^/        /'
    FAIL=1
done

if [[ "$FAIL" -ne 0 ]]; then
    cat <<'EOF'

      WHY THIS BLOCKS. tshepo-authz classifies confidentiality from the request PATH
      (ResourceSensitivityClassifier), and V048's rules are pinned to "/confidential/". A route
      without a lane marker gets no confidentialCategories from the PDP, and
      SpeciallyProtectedVisibilityGuard fails closed — so after the governance flip this endpoint
      would withhold every stamped record from EVERY requester, including its author. The service
      stays green while the ward stops seeing its own records.

      FIX: mount the controller under a confidential lane, e.g.
          @RequestMapping("/v1/confidential/pregnancy")
      Do NOT instead remove the stamp, and do NOT special-case the guard.
Confidential-lane routing guard: FAILED
EOF
    exit 1
fi

# Self-reach, part 2: detection is by ENTITY IMPORT, so renaming a stamped entity — exactly what a
# refactor does — silently empties the checked set while every message above still says OK. Finding
# controllers but classifying none of them is the failure mode this guard was written to prevent.
guard_assert_scanned "$CHECKED" "controllers handling a confidentiality-stamped record" || {
  echo "      Controllers were found, but none imports any of: $STAMPED_ENTITIES"
  echo "      If a stamped entity was renamed, update STAMPED_ENTITIES to match pct V437."
  echo "Confidential-lane routing guard: FAILED"
  exit 1
}

echo "  $CHECKED controller(s) handle a stamped record; all are mounted on a confidential lane"

# ── experience-bff: a route that proxies the confidential lane must BE a confidential lane ─────────
echo ""
echo "--- experience-bff confidential-lane routing ---"

BFF_FILES=$(git ls-files --cached --others --exclude-standard -- 'services/experience-bff/**/*.java')
BFF_CONTROLLERS=$(printf '%s\n' "$BFF_FILES" | grep -v '/src/test/' \
  | xargs -r grep -lE '@RestController|@Controller' 2>/dev/null || true)

# Self-reach: the BFF always has controllers, so zero means the glob stopped reaching the tree and
# everything below would report "no offenders" about nothing.
guard_assert_scanned "$(printf '%s\n' "$BFF_CONTROLLERS" | grep -c .)" \
  "experience-bff controllers" || {
  echo "Confidential-lane routing guard: FAILED"
  exit 1
}

# Files that name a confidential downstream path. Clients and services as well as controllers, so the
# next step can resolve one hop from a controller to the client that actually builds the URL.
BFF_CONFIDENTIAL_FILES=$(printf '%s\n' "$BFF_FILES" | grep -v '/src/test/' \
  | xargs -r grep -lE '"/?v1/confidential/|/internal/v1/confidential/|/v1/confidential/' 2>/dev/null || true)

# Resolution is per-METHOD, not per-class, and that distinction is the whole correctness of this
# section. PctServiceClient is one class holding both confidential and ordinary routes, so "controller
# injects a class that somewhere names a confidential path" flags every citizen and wellness controller
# that only ever calls the ordinary methods. A guard that fails on eleven innocent files teaches the
# next engineer to disable it. So: extract the methods whose own body builds a confidential URL, and
# only flag a controller that calls one of THOSE.
confidential_methods() {
    # Literal spaces, not [[:space:]]{4}: mawk has no interval expressions and answers a brace
    # quantifier with "REcompile() - panic" on stderr and an empty result on stdout. That combination
    # made this resolver silently return nothing while the guard still printed OK.
    awk '
      # A member declaration at class-body indent. Captures the name immediately before the "(".
      /^    (public|protected|private)[ \t]/ && /\(/ {
          line = $0
          sub(/\(.*$/, "", line)                      # drop the parameter list
          n = split(line, parts, /[[:space:]]+/)
          method = parts[n]                            # last token before "(" is the name
      }
      /"\/?v1\/confidential\/|\/internal\/v1\/confidential\/|\/v1\/confidential\// {
          if (method != "") print method
      }
    ' "$1" | sort -u
}

# Self-reach on the resolver itself. A file landed in BFF_CONFIDENTIAL_FILES because it contains a
# confidential path literal, so at least one of them must yield at least one method name. Zero across
# all of them means the extractor is broken, not that the estate is clean — and a broken extractor here
# fails OPEN: every one-hop offender becomes invisible while the guard still prints OK. That is not
# hypothetical; it is what the mawk brace quantifier above did.
RESOLVER_YIELD=0
for dep in $BFF_CONFIDENTIAL_FILES; do
    [[ -z "$dep" ]] && continue
    RESOLVER_YIELD=$((RESOLVER_YIELD + $(confidential_methods "$dep" | grep -c . || true)))
done
if [[ -n "$(printf '%s\n' "$BFF_CONFIDENTIAL_FILES" | grep -c . | grep -v '^0$')" ]] \
   && [[ "$RESOLVER_YIELD" -eq 0 ]]; then
    echo "FAIL: the confidential-method resolver matched no method in any of these files:"
    printf '%s\n' "$BFF_CONFIDENTIAL_FILES" | sed 's/^/        /'
    echo "      Each contains a confidential path literal, so at least one method must be attributed."
    echo "      The awk method-declaration pattern has stopped matching this codebase's formatting."
    echo "Confidential-lane routing guard: FAILED"
    exit 1
fi

BFF_CHECKED=0
BFF_FAIL=0
for f in $BFF_CONTROLLERS; do
    [[ -z "$f" ]] && continue
    reaches_confidential=0
    reason=""

    if printf '%s\n' "$BFF_CONFIDENTIAL_FILES" | grep -qxF "$f"; then
        reaches_confidential=1
        reason="names a confidential downstream path directly"
    else
        # One hop: does this controller CALL a method that builds a confidential downstream URL?
        for dep in $BFF_CONFIDENTIAL_FILES; do
            [[ -z "$dep" || "$dep" == "$f" ]] && continue
            dep_class="$(basename "$dep" .java)"
            grep -qE "\b$dep_class\b" "$f" 2>/dev/null || continue
            for m in $(confidential_methods "$dep"); do
                if grep -qE "\.$m[[:space:]]*\(" "$f" 2>/dev/null; then
                    reaches_confidential=1
                    reason="calls $dep_class.$m(), which targets the confidential lane"
                    break 2
                fi
            done
        done
    fi

    [[ "$reaches_confidential" -eq 0 ]] && continue
    BFF_CHECKED=$((BFF_CHECKED + 1))

    CLASS_PATH=$(grep -oE '@RequestMapping\("[^"]*"' "$f" | head -1 | sed 's/@RequestMapping("//; s/"$//')
    if echo "$CLASS_PATH" | grep -qiE "$LANE_MARKERS"; then
        continue
    fi

    echo "FAIL: $f"
    echo "      $reason, but is mounted at '${CLASS_PATH:-<no class-level @RequestMapping>}'"
    echo "      which carries no confidential-lane marker ($LANE_MARKERS)."
    BFF_FAIL=1
done

if [[ "$BFF_FAIL" -ne 0 ]]; then
    cat <<'EOF'

      WHY THIS BLOCKS. The PDP classifies the path THE CLIENT CALLS, and for a web or mobile caller
      that is the BFF path, not the pct path behind it. A BFF route without a lane marker therefore
      gets no confidentialCategories minted for it, so once obligation propagation is enabled the BFF
      forwards an obligation carrying no grant and pct's fail-closed guard withholds every stamped
      record — the identical failure the pct half of this guard prevents, one layer up.

      FIX: mount the BFF controller under a confidential lane, e.g.
          @RequestMapping("/internal/v1/confidential/maternity")
      Do NOT instead call an unguarded pct route to make the symptom go away.
Confidential-lane routing guard: FAILED
EOF
    exit 1
fi

# Deliberately NOT guard_assert_scanned: zero is a legitimate answer here, because the BFF need not
# proxy the confidential lane at all. The self-reach assertion above is on the controller glob, which
# is the thing that can silently break.
echo "  $BFF_CHECKED BFF controller(s) reach the confidential lane; all carry a lane marker"

echo "Confidential-lane routing guard: OK"
exit 0
