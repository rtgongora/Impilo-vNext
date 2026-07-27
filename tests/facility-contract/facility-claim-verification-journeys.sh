#!/usr/bin/env bash
# =============================================================================
# TUSO Facility Claim, Completion and MoHCC Verification — conformance (FCV).
#
# THE POINT. This capability's failure modes are all silent. A facility that
# legitimises itself, an import that quietly revokes a Ministry verdict, a bulk
# assertion that operationalises the estate, an expiry nobody reads — none of
# them throw, and a green build ships happily on top of every one. Each check
# below is an invariant a wave established, so "FCV-W3 is done" is verified
# against the tree rather than asserted.
#
# Verdicts: exit 0 GREEN · exit 2 AMBER (checks skipped, nothing failed) ·
#           exit 1 RED (an invariant is broken).
# A SKIP is never a PASS: an unrunnable check is reported, never assumed green.
# =============================================================================
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TUSO="$ROOT/services/tuso-service/src/main/java/zw/gov/mohcc/impilo/tuso"
MIG="$ROOT/services/tuso-service/src/main/resources/db/migration"
PASS=0; FAIL=0; SKIP=0

ok()   { printf '  \033[32mPASS\033[0m %s\n' "$1"; PASS=$((PASS+1)); }
bad()  { printf '  \033[31mFAIL\033[0m %s\n    → %s\n' "$1" "$2"; FAIL=$((FAIL+1)); }
skip() { printf '  \033[33mSKIP\033[0m %s\n    → %s\n' "$1" "$2"; SKIP=$((SKIP+1)); }
have() { [ -f "$1" ] || { skip "$2" "missing $1"; return 1; }; }

echo "── FCV conformance ────────────────────────────────────────────────────"

# FCV-SELF — a facility cannot confer legitimacy on itself.
F="$TUSO/core/FacilityLegitimacyDecisionService.java"
if have "$F" "FCV-SELF issuing a verdict requires a Ministry verifier appointment"; then
  if grep -q "VERIFIER_ROLES" "$F" && grep -q "authorityOver" "$F"; then
    ok "FCV-SELF issuing a verdict requires a Ministry verifier appointment"
  else
    bad "FCV-SELF issuing a verdict requires a Ministry verifier appointment" \
        "the decision service does not resolve authority via MinistryAuthorityService"
  fi
fi

# FCV-WRITE — the generic legitimacy endpoint cannot grant platform operation.
F="$TUSO/core/FacilitySourceLegitimacyService.java"
if have "$F" "FCV-WRITE only HPA_LEGAL is writable directly"; then
  # Check the guard is INVOKED, not merely defined. Grepping the identifier alone passes happily
  # when someone deletes the call and leaves the method — which is how a check comes to report on
  # a guard that no longer runs.
  if grep -qE "^\s*assertDirectlyWritable\(source\);" "$F"; then
    ok "FCV-WRITE only HPA_LEGAL is writable directly"
  else
    bad "FCV-WRITE only HPA_LEGAL is writable directly" \
        "the source guard is not called on the upsert path — any caller could write PLATFORM_OPERATIONAL/true"
  fi
fi

# FCV-CLOBBER — an import may not silently revoke a Ministry decision.
if have "$F" "FCV-CLOBBER imports skip decision-bound rows"; then
  if grep -q "getDecisionId() != null" "$F"; then
    ok "FCV-CLOBBER imports skip decision-bound rows"
  else
    bad "FCV-CLOBBER imports skip decision-bound rows" \
        "stampFromImport would overwrite a projected Ministry verdict"
  fi
fi

# FCV-DENIER — a Ministry allow may never stand without a platform verdict.
if have "$MIG/V044__legitimacy_decision_binding_and_invariant.sql" "FCV-DENIER denier-present invariant exists"; then
  if grep -q "assert_platform_verdict_present" "$MIG/V044__legitimacy_decision_binding_and_invariant.sql"; then
    ok "FCV-DENIER denier-present invariant exists"
  else
    bad "FCV-DENIER denier-present invariant exists" "trigger missing from V044"
  fi
fi

# FCV-BULK — recognition is not operation.
F="$TUSO/core/MinistryRecognitionBatchService.java"
if have "$F" "FCV-BULK recognition batch never grants platform operation"; then
  if grep -q "PLATFORM_OPERATIONAL" "$F" && grep -qE "PENDING_VERIFICATION,\s*false" "$F"; then
    ok "FCV-BULK recognition batch never grants platform operation"
  else
    bad "FCV-BULK recognition batch never grants platform operation" \
        "the batch does not stamp a denying PLATFORM_OPERATIONAL row"
  fi
  if grep -q "REGISTRY_ADMIN_ROLES" "$F"; then
    ok "FCV-BULKSOD the batch demands registry-admin, not verifier, authority"
  else
    bad "FCV-BULKSOD the batch demands registry-admin, not verifier, authority" \
        "separation of duties not enforced — a verifier could bulk-assert"
  fi
fi

# FCV-EXPIRY — a stored expiry is actually read.
F="$TUSO/core/FacilityLegitimacyExpirySweep.java"
if have "$F" "FCV-EXPIRY an expired decision loses its platform allow"; then
  if grep -q "expires_at < ?" "$F" && grep -q "setAllowedOnPlatform(false)" "$F"; then
    ok "FCV-EXPIRY an expired decision loses its platform allow"
  else
    bad "FCV-EXPIRY an expired decision loses its platform allow" \
        "expiry is stored but not enforced — silent over-granting"
  fi
fi

# FCV-REVERIFY — a verdict does not survive the facility changing underneath it.
F="$TUSO/core/FacilityLegitimacyReverificationService.java"
if have "$F" "FCV-REVERIFY material change demotes to awaiting-verification"; then
  if grep -q "MATERIAL_FIELDS" "$F" && grep -q "PENDING_VERIFICATION" "$F"; then
    ok "FCV-REVERIFY material change demotes to awaiting-verification"
  else
    bad "FCV-REVERIFY material change demotes to awaiting-verification" "demotion not implemented"
  fi
fi

# FCV-JURIS — a verifier cannot act outside their jurisdiction; absence never matches.
F="$TUSO/core/MinistryAuthorityService.java"
if have "$F" "FCV-JURIS jurisdiction is matched against the facility"; then
  if grep -q "canonicalPlace" "$F" && grep -q "!place.isEmpty()" "$F"; then
    ok "FCV-JURIS jurisdiction is matched against the facility, and absence never matches"
  else
    bad "FCV-JURIS jurisdiction is matched against the facility" \
        "an absent district could match a district-scoped verifier"
  fi
fi

# FCV-HEADER — authority never comes from a client-supplied header.
F="$TUSO/api/controller/FacilityVerificationController.java"
if have "$F" "FCV-HEADER verification authority is an appointment, not a header"; then
  if grep -q "ministryAuthority.authorityOver" "$F"; then
    ok "FCV-HEADER verification authority is an appointment, not a header"
  else
    bad "FCV-HEADER verification authority is an appointment, not a header" \
        "requireVerifier still trusts X-Actor-Type"
  fi
fi

# FCV-PROVIDERID — regulated roles need a Provider ID; administrative roles must not.
F="$TUSO/core/FacilityClaimService.java"
if have "$F" "FCV-PROVIDERID Provider ID is required only where regulated"; then
  if grep -q "isRequiresProviderId" "$F" && grep -q "resolveProfessionalIdentifier" "$F"; then
    ok "FCV-PROVIDERID Provider ID is required only where regulated"
  else
    bad "FCV-PROVIDERID Provider ID is required only where regulated" "conditional requirement missing"
  fi
fi
if have "$MIG/V048__facility_relationship_type.sql" "FCV-ADMINCLAIM administrative roles are not gated on a licence"; then
  # Each seed row spans two lines, so read the whole tuple rather than one line — a check that
  # greps a single line here reports on formatting, not on whether a records officer is gated.
  ADMIN_FLAGS=$(tr '\n' ' ' < "$MIG/V048__facility_relationship_type.sql" \
    | grep -oE "\('RECORDS_OFFICER'[^)]*\)" | grep -oE "(TRUE|FALSE)" | head -1)
  REG_FLAGS=$(tr '\n' ' ' < "$MIG/V048__facility_relationship_type.sql" \
    | grep -oE "\('PRACTITIONER_IN_CHARGE'[^)]*\)" | grep -oE "(TRUE|FALSE)" | head -1)
  if [ "$ADMIN_FLAGS" = "FALSE" ] && [ "$REG_FLAGS" = "TRUE" ]; then
    ok "FCV-ADMINCLAIM administrative roles are not gated on a licence, regulated ones are"
  else
    bad "FCV-ADMINCLAIM administrative roles are not gated on a licence" \
        "records officer requires_provider_id=$ADMIN_FLAGS, PIC=$REG_FLAGS — demanding a licence of "\
"administrative staff is exclusion, not rigour"
  fi
fi

# FCV-DRAFT — a draft never describes the facility, and cannot assert its own status.
F="$TUSO/core/FacilityProfileSubmissionService.java"
if have "$F" "FCV-DRAFT proposals are private and not current"; then
  if grep -q "is_current" "$F" && grep -q "FACILITY_CLAIM" "$F"; then
    ok "FCV-DRAFT proposals are private, not current, and carry facility provenance"
  else
    bad "FCV-DRAFT proposals are private and not current" "drafts may be writing the live record"
  fi
  if grep -q "PROPOSABLE_FIELDS" "$F"; then
    ok "FCV-NOSELFSTATUS a facility cannot propose what is decided about it"
  else
    bad "FCV-NOSELFSTATUS a facility cannot propose what is decided about it" \
        "no allowlist — a profile edit could carry status or legitimacy"
  fi
fi

# FCV-CAMPAIGN — campaign progress is derived, not mirrored.
if have "$MIG/V050__facility_onboarding_campaign.sql" "FCV-CAMPAIGN progress is derived from the rails"; then
  if grep -q "CREATE OR REPLACE VIEW tuso.facility_campaign_progress" "$MIG/V050__facility_onboarding_campaign.sql" \
     && ! grep -qE "^\s+status\s+VARCHAR.*NOT NULL DEFAULT 'NOT_CONTACTED'" "$MIG/V050__facility_onboarding_campaign.sql"; then
    ok "FCV-CAMPAIGN progress is derived from the rails, not a mirrored status"
  else
    bad "FCV-CAMPAIGN progress is derived from the rails" "membership stores a status that will drift"
  fi
fi

# FCV-TENANT — data-gap tasks are tenant-scoped.
F="$TUSO/api/controller/FacilityDataGapController.java"
if have "$F" "FCV-TENANT data-gap lookups are tenant-scoped"; then
  if grep -q "AND tenant_id=?" "$F"; then
    ok "FCV-TENANT data-gap lookups are tenant-scoped"
  else
    bad "FCV-TENANT data-gap lookups are tenant-scoped" "cross-tenant task read/mutate is possible"
  fi
fi

# FCV-LIVE — the estate's own answer. Runtime, so it SKIPs when the DB is unreachable.
if command -v kubectl >/dev/null 2>&1 && \
   kubectl get ns impilo-full-preview >/dev/null 2>&1; then
  ROWS=$(kubectl exec -n impilo-full-preview deploy/postgres -- psql -U impilo -d tuso -tAc \
    "SELECT count(*) FROM tuso.facility f
      WHERE f.status='ACTIVE'
        AND NOT EXISTS (SELECT 1 FROM tuso.facility_source_legitimacy l
                         WHERE l.facility_id=f.facility_uuid AND l.allowed_on_platform)
        AND EXISTS (SELECT 1 FROM tuso.facility_source_legitimacy l2
                     WHERE l2.facility_id=f.facility_uuid);" 2>/dev/null | tr -d '[:space:]')
  if [ -n "$ROWS" ]; then
    ok "FCV-LIVE the estate answers ($ROWS stamped-but-not-operational facilities — silence and denial both refuse)"
  else
    skip "FCV-LIVE the estate answers" "preview tuso DB not reachable"
  fi
else
  skip "FCV-LIVE the estate answers" "kubectl or the preview namespace is unavailable"
fi

echo "───────────────────────────────────────────────────────────────────────"
printf 'PASS %d · FAIL %d · SKIP %d\n' "$PASS" "$FAIL" "$SKIP"
if [ "$FAIL" -gt 0 ]; then echo "VERDICT: RED"; exit 1; fi
if [ "$SKIP" -gt 0 ]; then echo "VERDICT: AMBER (checks skipped — a skip is not a pass)"; exit 2; fi
echo "VERDICT: GREEN"; exit 0
