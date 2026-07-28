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
set -uo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

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

echo "  $CHECKED controller(s) handle a stamped record; all are mounted on a confidential lane"
echo "Confidential-lane routing guard: OK"
exit 0
