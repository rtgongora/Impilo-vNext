#!/usr/bin/env bash
# W12 identity proof: for every table on the trauma/emergency lane carrying a resolvable
# subject_cpid, prove a repository repoint method AND a wired IdentityRepointHook both exist.
#
# Why this is a static repo check, not a live-estate audit like audit-no-healthid-in-clinical.sh:
# that script proves no vito.client.health_id LEAKED into a clinical table (a runtime data
# property). This proves something upstream of that — that the MACHINERY exists at all to move a
# table's subject_cpid off an identity that has since merged. Without it, W8 (or any later wave)
# adds a new subject_cpid-bearing table, nobody adds the hook, and the first real VITO merge
# silently leaves that table's rows attached to an identity that no longer refers to anyone. Nothing
# fails; the rows simply stop being findable under the patient's current identity. That failure mode
# is exactly what daidzai.dai_mci_casualty, dai_emergency_request and dai_assistance_request hit
# before this guard was written (see DaidzaiIntakeRepointHook).
#
# Scope: the trauma/emergency lane services already wired into the VITO merge fan-out (pct,
# inpatient, daidzai, madi). Deliberately NOT repo-wide — costa, rito-quality-safety and several
# other services carry subject_cpid/patient_cpid columns with zero IdentityRepointHook wiring, which
# is pre-existing debt outside this pack's remit, not a new violation to demand plumbing for on
# ownership this pack does not have. Widening the scope is a real conversation for whoever owns those
# services, not a silent expansion of what this guard enforces.
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
source "$REPO_PATH/scripts/guard/_guard-common.sh"

guard_assert_repo_path || exit 1

echo "=== Identity repoint coverage guard (W12) ==="

# repoFile:repointMethod:hookFile — one row per identity-bearing table wired into the fan-out.
CHECKS=(
  "services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/persistence/repository/EmergencyEpisodeRepository.java:repointSubjectCpid:services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/core/PctEmergencyRepointHook.java"
  "services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/persistence/repository/EdVisitRepository.java:repointPatientCpid:services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/core/PctTraumaRepointHook.java"
  "services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/persistence/repository/EmergencyCaseRepository.java:repointKnownSubjectCpid:services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/core/PctTraumaRepointHook.java"
  "services/inpatient-service/src/main/java/zw/gov/mohcc/impilo/inpatient/persistence/repository/EmergencyActivationRepository.java:repointSubjectCpid:services/inpatient-service/src/main/java/zw/gov/mohcc/impilo/inpatient/core/InpatientResuscitationRepointHook.java"
  "services/inpatient-service/src/main/java/zw/gov/mohcc/impilo/inpatient/persistence/repository/ProcedureEpisodeRepository.java:repointSubjectCpid:services/inpatient-service/src/main/java/zw/gov/mohcc/impilo/inpatient/core/InpatientProcedureEpisodeRepointHook.java"
  "services/daidzai-service/src/main/java/zw/gov/mohcc/impilo/daidzai/persistence/repository/TraumaEpisodeRepository.java:repointSubjectCpid:services/daidzai-service/src/main/java/zw/gov/mohcc/impilo/daidzai/core/DaidzaiTraumaRepointHook.java"
  "services/daidzai-service/src/main/java/zw/gov/mohcc/impilo/daidzai/persistence/repository/EmergencyIncidentRepository.java:repointSubjectCpid:services/daidzai-service/src/main/java/zw/gov/mohcc/impilo/daidzai/core/DaidzaiTraumaRepointHook.java"
  "services/daidzai-service/src/main/java/zw/gov/mohcc/impilo/daidzai/persistence/repository/MciCasualtyRepository.java:repointSubjectCpid:services/daidzai-service/src/main/java/zw/gov/mohcc/impilo/daidzai/core/DaidzaiIntakeRepointHook.java"
  "services/daidzai-service/src/main/java/zw/gov/mohcc/impilo/daidzai/persistence/repository/EmergencyRequestRepository.java:repointSubjectCpid:services/daidzai-service/src/main/java/zw/gov/mohcc/impilo/daidzai/core/DaidzaiIntakeRepointHook.java"
  "services/daidzai-service/src/main/java/zw/gov/mohcc/impilo/daidzai/persistence/repository/AssistanceRequestRepository.java:repointSubjectCpid:services/daidzai-service/src/main/java/zw/gov/mohcc/impilo/daidzai/core/DaidzaiIntakeRepointHook.java"
  "services/madi-service/src/main/java/zw/gov/mohcc/impilo/madi/persistence/repository/BloodOrderRepository.java:repointPatientCpid:services/madi-service/src/main/java/zw/gov/mohcc/impilo/madi/core/MadiBloodRepointHook.java"
)

fail=0
checked=0

for entry in "${CHECKS[@]}"; do
  IFS=':' read -r repo_file method hook_file <<< "$entry"
  checked=$((checked + 1))
  label="$(basename "$repo_file" .java).${method}"

  if [[ ! -f "$repo_file" ]]; then
    echo "GUARD FAIL  $label — repository file does not exist: $repo_file"
    fail=1
    continue
  fi
  if ! grep -qE "\b${method}\s*\(" "$repo_file"; then
    echo "GUARD FAIL  $label — no ${method}(...) method declared in $repo_file"
    echo "  A subject_cpid-bearing table with no repoint method is unreachable by any VITO merge."
    fail=1
    continue
  fi

  if [[ ! -f "$hook_file" ]]; then
    echo "GUARD FAIL  $label — expected hook file does not exist: $hook_file"
    fail=1
    continue
  fi
  if ! grep -q "implements IdentityRepointHook" "$hook_file"; then
    echo "GUARD FAIL  $label — $hook_file does not implement IdentityRepointHook"
    fail=1
    continue
  fi
  repo_interface="$(basename "$repo_file" .java)"
  if ! grep -q "$repo_interface" "$hook_file"; then
    echo "GUARD FAIL  $label — $hook_file never references $repo_interface"
    echo "  The repoint method exists but nothing wired into the merge fan-out calls it — a"
    echo "  VITO merge would still leave this table's rows on a dead identity."
    fail=1
    continue
  fi
  if ! grep -q "@Component" "$hook_file"; then
    echo "GUARD FAIL  $label — $hook_file is not a @Component, so Spring never auto-collects it"
    echo "  into the service's List<IdentityRepointHook> fan-out."
    fail=1
    continue
  fi

  echo "GUARD PASS  $label"
done

guard_assert_scanned "$checked" "identity-repoint coverage entries"

if [[ "$fail" -ne 0 ]]; then
  echo ""
  echo "FAIL: one or more identity-bearing tables have no working repoint path."
  echo "Add the repository method and a @Component IdentityRepointHook in the SAME wave as the"
  echo "migration that adds the column — never a later one (see V210's own rationale)."
  exit 1
fi

echo ""
echo "PASS: all ${checked} identity-repoint coverage entries wired."
