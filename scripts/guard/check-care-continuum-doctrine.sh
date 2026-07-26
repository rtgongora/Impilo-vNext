#!/usr/bin/env bash
# Care Continuum doctrine guard (docs/doctrine/care-continuum-doctrine.md).
#
# PCT owns the Care Continuum (the person's cradle-to-grave clinical journey); Simba owns
# the peer Wellness Continuum; every other care-path service is a subordinate component.
# This guard keeps the codification from silently regressing: the registry hierarchy
# fields, the prose rulings, the doctrine cross-references, and the generator mirror that
# protects the hand-curated registry from a destructive regeneration.
#
# Anchoring violations V-1..V-3 (CC-5) are WARN-only until the closing code wave lands;
# flip them to FAIL when it does.
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

FAIL=0
REGISTRY=docs/registry/services-registry.yaml
DOCTRINE=docs/doctrine/care-continuum-doctrine.md
SEED=scripts/registry/seed-registry.mjs

echo "=== Care Continuum doctrine guard ==="

# Reads the N lines following a service's id line, so assertions are block-scoped and a
# field on some other service cannot satisfy a check by accident.
service_block() {
  # A service's block runs from its id line to the next one. A fixed -A window silently
  # truncated the block as soon as an entry grew past it, so a prohibition that was still
  # present read as missing — the guard failed on entries getting longer rather than on
  # anything being removed.
  awk -v id="  - id: $1" '
    $0 == id { inblock = 1; print; next }
    inblock && /^  - id: / { exit }
    inblock { print }
  ' "$REGISTRY"
}

# 1. The doctrine document exists and carries its load-bearing rulings.
if [[ ! -f "$DOCTRINE" ]]; then
  echo "FAIL: $DOCTRINE missing"
  FAIL=1
else
  for ruling in "CC-1" "CC-4" "CC-5"; do
    if ! grep -q "$ruling" "$DOCTRINE"; then
      echo "FAIL: doctrine no longer contains ruling $ruling"
      FAIL=1
    fi
  done
fi

# 2. The two continuum owners.
if ! service_block pct-service | grep -q "continuum_role: owner"; then
  echo "FAIL: pct-service is not marked continuum_role: owner"
  FAIL=1
fi
if ! service_block pct-service | grep -q "continuum: care"; then
  echo "FAIL: pct-service is not marked continuum: care"
  FAIL=1
fi
if ! service_block simba-service | grep -q "continuum_role: owner"; then
  echo "FAIL: simba-service is not marked continuum_role: owner"
  FAIL=1
fi
if ! service_block simba-service | grep -q "continuum: wellness"; then
  echo "FAIL: simba-service is not marked continuum: wellness"
  FAIL=1
fi

# 3. Record-authority: BUTANO must never be demoted to a component (CC-3).
for svc in butano-service butano-fhir; do
  if ! service_block "$svc" | grep -q "continuum_role: record-authority"; then
    echo "FAIL: $svc must be continuum_role: record-authority (CC-3)"
    FAIL=1
  fi
done

# 4. Components subordinate to pct-service.
for svc in inpatient-service oros-service booking-service telemonitoring-service \
           referral-service community-service forms-service madi-service; do
  if ! service_block "$svc" | grep -q "continuum_parent: pct-service"; then
    echo "FAIL: $svc missing continuum_parent: pct-service"
    FAIL=1
  fi
done
if ! service_block daidzai-service | grep -q "continuum_role: correlator"; then
  echo "FAIL: daidzai-service must be continuum_role: correlator (CC-4)"
  FAIL=1
fi
if ! service_block wellness-service | grep -q "continuum_parent: simba-service"; then
  echo "FAIL: wellness-service missing continuum_parent: simba-service"
  FAIL=1
fi

# 5. The daidzai spine line must keep the word "delegated" (CC-4 regression tripwire):
#    the original wording put the correlation spine above PCT.
if grep -q "trauma_episode correlation spine" "$REGISTRY"; then
  if ! grep "trauma_episode correlation spine" "$REGISTRY" | grep -q "delegated"; then
    echo "FAIL: daidzai trauma spine line lost the word 'delegated' (CC-4)"
    FAIL=1
  fi
fi

# 6. Ghost/alias prohibitions (CC-6/CC-7).
declare -A PROHIBITIONS=(
  [referral-service]="must-not-claim-referral-canonical-records"
  [community-service]="must-not-fork-community-care-context"
  [wellness-service]="must-not-fork-wellness-continuum"
  [booking-service]="must-not-contain-care-journeys"
  [pct-service]="must-not-own-wellness-continuum"
  [daidzai-service]="must-not-own-care-continuum"
)
for svc in "${!PROHIBITIONS[@]}"; do
  if ! service_block "$svc" | grep -q "${PROHIBITIONS[$svc]}"; then
    echo "FAIL: $svc missing ${PROHIBITIONS[$svc]}"
    FAIL=1
  fi
done

# 7. Cross-document anchors.
if ! grep -q "Care Continuum doctrine line" CLAUDE.md; then
  echo "FAIL: CLAUDE.md lost the Care Continuum doctrine line"
  FAIL=1
fi
if ! grep -q "Care Continuum owner" docs/doctrine/CORE_TRANSACTION_DOCTRINE.md; then
  echo "FAIL: CORE_TRANSACTION_DOCTRINE.md Service Role Anchors lost PCT"
  FAIL=1
fi
if ! grep -q "8a\." docs/templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md; then
  echo "FAIL: feature checklist lost continuum item 8a"
  FAIL=1
fi
if ! grep -q "Care Continuum" docs/architecture/clinical-system-of-record-boundary-map.md; then
  echo "FAIL: clinical SoR boundary map lost the PCT Care Continuum row"
  FAIL=1
fi
if ! grep -q "G-CC1" docs/registry/system-of-record-map.md; then
  echo "FAIL: system-of-record-map lost the G-CC1 ruling"
  FAIL=1
fi

# 8. Generator mirror tripwire: the registry YAML is hand-curated, and these services'
#    entries are only safe against a (manual) regeneration because they are mirrored in
#    DOCTRINE_OVERRIDES. Stripping the mirror makes a regen destructive again.
for svc in pct-service inpatient-service daidzai-service telemonitoring-service; do
  if ! grep -q "\"$svc\"" "$SEED"; then
    echo "FAIL: $svc missing from seed-registry DOCTRINE_OVERRIDES mirror"
    FAIL=1
  fi
done

# 9. WARN-only: the registered CC-5 anchoring violations. These greps detect the closing
#    wave landing (at which point flip them to FAILs on absence, not warnings on presence).
if grep -q 'name = "encounter_id"' services/inpatient-service/src/main/java/zw/gov/mohcc/impilo/inpatient/persistence/entity/ProcedureEpisodeEntity.java 2>/dev/null \
   && ! grep -q "pct_anchor" services/inpatient-service/src/main/resources/db/migration/*procedure*anchor* 2>/dev/null; then
  echo "WARN: V-1 open — elective theatre episodes can still exist without a PCT anchor (CC-5)"
fi
# The column-level constraint, not the partial index ("WHERE encounter_ref IS NOT NULL"),
# is what closes V-2 — the index line must not silence this warning.
if grep -Eq "encounter_ref\s+VARCHAR" services/oros-service/src/main/resources/db/migration/V001__init.sql 2>/dev/null \
   && ! grep -rEq "encounter_ref\s+VARCHAR[^,]*NOT NULL|ALTER COLUMN encounter_ref SET NOT NULL" services/oros-service/src/main/resources/db/migration/ 2>/dev/null; then
  echo "WARN: V-2 open — oros orders still accept a null encounter_ref (CC-5)"
fi
# V-3 detector, repaired 2026-07-26. The previous form globbed
#   services/daidzai-service/src/main/java/*/trauma*
# which cannot match: the only child of .../java is zw/, so the pattern expanded to zw/trauma* and
# matched nothing. grep therefore always found nothing and this WARN fired unconditionally — it
# would have kept firing after V-3 was closed, and would never have fired for the right reason.
# A detector that cannot detect is worse than no detector: it produces a warning everyone learns to
# ignore, and it silently withholds the signal it exists to give.
#
# Detect on the SCHEMA rather than on Java source, because the column is what closes V-3 and a
# migration cannot be refactored out of existence the way a class can.
if ! grep -rq "pct_journey_id" services/daidzai-service/src/main/resources/db/migration/ 2>/dev/null; then
  echo "WARN: V-3 open — trauma episodes still carry no PCT back-link (CC-5)"
else
  # Closed. Now assert it STAYS closed: the back-link must exist and be constrained to be present
  # once the episode has reached a facility phase, or the anchor is optional in practice.
  if ! grep -rq "chk_dai_anchored_once_in_facility" services/daidzai-service/src/main/resources/db/migration/ 2>/dev/null; then
    echo "FAIL: V-3 regressed — pct_journey_id exists but nothing requires it once in a facility (CC-5)"
    FAIL=1
  fi
fi

if [[ "$FAIL" -eq 1 ]]; then
  echo "Care Continuum doctrine guard: FAILED"
  exit 1
fi
echo "Care Continuum doctrine guard: OK"
