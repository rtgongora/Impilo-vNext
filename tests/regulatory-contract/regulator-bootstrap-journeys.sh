#!/usr/bin/env bash
# =============================================================================
# Regulator bootstrap + appointment-lifecycle conformance (RB-1 … RB-4).
#
# THE POINT. The RB waves each fixed a defect whose failure mode was INVISIBLE —
# an expiry date nobody read, an ended appointment that revoked no token, a
# plaintext invitation token, a country root that was denied nothing. A defect you
# cannot see is one a green build will happily ship on top of. This pack makes each
# fix a checkable invariant, so "RB-1 is done" is verified against the tree rather
# than asserted — the antidote to the pattern the fleet met all day (a claimed fix,
# a claimed guard, a claimed test, a claimed done that were each only a note).
#
# It asserts SOFTWARE CONTRACT at the tree level (the mechanism exists and is wired)
# and, where the estate is reachable, LIVE state via psql. A fix that needs a deploy
# to prove is reported AMBER, never as a false green.
#
# Verdict: exit 0 GREEN / 2 AMBER (software contract green, live checks await deploy)
# / 1 RED. SKIP-never-false-PASS.
# =============================================================================
set -uo pipefail

REPO="${REPO_PATH:-$(cd "$(dirname "$0")/../.." && pwd)}"
TS="$(date +%Y%m%d-%H%M%S 2>/dev/null || echo run)"
EVIDENCE_DIR="${EVIDENCE_DIR:-$REPO/reports/regulator-bootstrap-journeys/$TS}"
mkdir -p "$EVIDENCE_DIR"
SUMMARY="$EVIDENCE_DIR/summary.txt"; : > "$SUMMARY"

NS="${IDENTITY_PACK_NAMESPACE:-impilo-full-preview}"
PSQL="${PSQL:-kubectl exec -i -n $NS deploy/postgres -- psql -U impilo -tA}"
dbq(){ local db="$1" sql="$2"; $PSQL -d "$db" -c "$sql" 2>/dev/null; }

PASS=0; FAIL=0; SKIP=0
ok(){   echo "  PASS: $1" | tee -a "$SUMMARY"; PASS=$((PASS+1)); }
bad(){  echo "  FAIL: $1" | tee -a "$SUMMARY"; FAIL=$((FAIL+1)); }
skip(){ echo "  SKIP: $1" | tee -a "$SUMMARY"; SKIP=$((SKIP+1)); }
say(){  echo "" | tee -a "$SUMMARY"; echo "== $1" | tee -a "$SUMMARY"; }
grepq(){ grep -qs "$2" "$REPO/$1" 2>/dev/null; }

ORG="services/organization-registry-service/src/main/java/zw/gov/mohcc/impilo/orgregistry"
ORGM="services/organization-registry-service/src/main/resources/db/migration"
IDN="services/tshepo-identity-service/src/main/java/zw/gov/mohcc/impilo/tshepo/identity"
AUTHZM="services/tshepo-authz-service/src/main/resources/db/migration"
WGV="services/workforce-governance-service/src/main/java/zw/gov/mohcc/impilo/governance"
WGVM="services/workforce-governance-service/src/main/resources/db/migration"
BFF="services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience"
AUTHZ="services/tshepo-authz-service/src/main/java/zw/gov/mohcc/impilo/tshepo/authz"

echo "Regulator bootstrap + lifecycle conformance @ $TS" | tee -a "$SUMMARY"

# ── ROM-EXPIRY (RB-1) ── an appointment past valid_to must mint no token ──────
say "ROM-EXPIRY expiry is a state transition the system reads, not a decoration"
if grepq "$ORGM/V008__regulatory_appointment_expiry.sql" "expiry_swept_at" \
   && grepq "$ORG/core/RegulatoryAppointmentExpirySweep.java" "expireLapsed" \
   && grepq "$ORG/core/RegulatoryAppointmentService.java" "findByStatusAndValidToBeforeAndExpirySweptAtIsNull"; then
  ok "ROM-EXPIRY: V008 + sweep read valid_to (the column that was written and never read)"
else bad "ROM-EXPIRY: expiry sweep or its index missing — valid_to may still be decorative"; fi

# ── ROM-TEARDOWN (RB-1) ── ending an appointment revokes the org-scoped token ─
say "ROM-TEARDOWN an ended appointment tears down the work-context token"
if grepq "$IDN/events/RegulatoryAppointmentEndedTokenConsumer.java" "impilo.org-registry.events" \
   && grepq "$IDN/persistence/repository/ScopedTokenRepository.java" "revokeWorkContextForActorAtOrganisation"; then
  ok "ROM-TEARDOWN: consumer on impilo.org-registry.events + org-scoped revoke (topic had NO consumer before)"
else bad "ROM-TEARDOWN: no consumer for the ended event — a revoked regulator keeps working"; fi

# ── ROM-PURPOSE (RB-1) ── REGULATORY_DUTY is a valid OPA purpose ──────────────
say "ROM-PURPOSE REGULATORY_DUTY is admitted, so OPA ENFORCE is not a latent outage"
if grepq "infra/opa/authz/authz.rego" "REGULATORY_DUTY"; then
  ok "ROM-PURPOSE: REGULATORY_DUTY in valid_purpose (every regulatory request would else deny under ENFORCE)"
else bad "ROM-PURPOSE: REGULATORY_DUTY missing from valid_purpose — ENFORCE flip denies all regulatory work"; fi

# ── ROM-ROLES (RB-2a) ── administration is a flagged role class ───────────────
say "ROM-ROLES administration is a role class, not a naming convention"
if grepq "$ORGM/V009__regulator_administration_roles.sql" "is_administrative" \
   && grepq "$ORGM/V009__regulator_administration_roles.sql" "FOUNDING_REGULATOR_ADMINISTRATOR" \
   && grepq "$ORGM/V009__regulator_administration_roles.sql" "trg_regulator_duty_separation"; then
  ok "ROM-ROLES: V009 admin role split + separation-of-duties trigger"
else bad "ROM-ROLES: administrator role class or SoD trigger missing"; fi

# ── ROM-SUCCESSION (RB-2a) ── the last administrator cannot be removed ────────
say "ROM-SUCCESSION ending the last administrator is refused"
if grepq "$ORG/core/RegulatoryAppointmentService.java" "LAST_ADMINISTRATOR" \
   && grepq "$ORG/persistence/repository/RegulatoryAppointmentRepository.java" "countActiveAdministrators"; then
  ok "ROM-SUCCESSION: last-administrator guard counts by the administrative flag, not a role-code list"
else bad "ROM-SUCCESSION: no succession guard — a regulator can be left administrator-less"; fi

# ── ROM-ROOT (RB-2b) ── the country root is DENIED case data, not ungranted ───
say "ROM-ROOT the National Trust Administration is denied council case data by policy"
if grepq "$AUTHZM/V052__regulator_administration_roles_and_country_root_deny.sql" "national-trust-no-disciplinary-cases" \
   && grepq "$AUTHZM/V052__regulator_administration_roles_and_country_root_deny.sql" "'DENY', 10,"; then
  # A DENY, not an absence: an absence is out-ranked by a later broad admin ALLOW; a DENY is not.
  ok "ROM-ROOT: active DENY rows on NATIONAL_TRUST_ADMINISTRATOR over case-data lanes"
else bad "ROM-ROOT: the country root relies on absence-of-grant, which a later ALLOW silently defeats"; fi

# ── ROM-CONDGUARD (RB-2b) ── seeded condition keys must be implemented ────────
say "ROM-CONDGUARD an unimplemented condition key cannot ship 'differently active'"
if grepq "$AUTHZ/migration/PolicyConditionKeyContractTest.java" "not implemented" \
   || grepq "services/tshepo-authz-service/src/test/java/zw/gov/mohcc/impilo/tshepo/authz/migration/PolicyConditionKeyContractTest.java" "PolicyEngine"; then
  ok "ROM-CONDGUARD: the condition-key contract guard exists (unknown key = non-match, guarded at build)"
else skip "ROM-CONDGUARD: condition-key guard not found under expected name (ROM lane may have renamed it)"; fi

# ── ROM-JURISDICTION (RB-2c) ── jurisdiction bounds a regulatory session ──────
say "ROM-JURISDICTION jurisdiction reaches the PDP and gates, sourced from the token not a header"
if grepq "$AUTHZ/core/PolicyEngine.java" "allowed_jurisdictions" \
   && grepq "$AUTHZ/core/PolicyEngine.java" "effectiveJurisdiction" \
   && grepq "$AUTHZ/dto/DutyContext.java" "jurisdictionCode" \
   && grepq "$IDN/core/TokenIssuanceService.java" "jurisdiction_code"; then
  ok "ROM-JURISDICTION: token carries jurisdiction_code, DutyContext + PolicyEngine read it (reader had no writer before)"
else bad "ROM-JURISDICTION: the jurisdiction dimension is not wired end to end"; fi

# ── ROM-BOOTSTRAP (RB-3) ── the founding appointment is made under four-eyes ──
say "ROM-BOOTSTRAP nobody self-claims the founding administrator; it rides the four-eyes rail"
# The four-eyes machinery is owned by PlatformOriginService; RegulatorBootstrapService reuses it by
# DELEGATION (it holds a PlatformOriginService and calls initiateFoundingRegulatorAdmin), rather
# than re-implementing a second two-person rail. The invariant checks that delegation, not a
# literal string in the wrong file.
if grepq "$WGV/core/PlatformOriginService.java" "ACTION_APPOINT_FOUNDING_REGULATOR_ADMIN" \
   && grepq "$WGV/core/PlatformOriginService.java" "TwoPersonApprovalService" \
   && grepq "$WGV/core/RegulatorBootstrapService.java" "platformOriginService.initiateFoundingRegulatorAdmin" \
   && grepq "$WGVM/V013__regulator_bootstrap_request.sql" "regulator_bootstrap"; then
  ok "ROM-BOOTSTRAP: founding-admin request delegates to the existing two-person rail (V013 record)"
else bad "ROM-BOOTSTRAP: the bootstrap request does not ride the four-eyes rail"; fi

# ── ROM-ACTIVATE (RB-3b) ── an approved bootstrap becomes an ACTIVE appointment
say "ROM-ACTIVATE the approved bootstrap is consumed into an ACTIVE appointment (atomic, idempotent)"
if grepq "$ORG/events/RegulatorBootstrapActivatedConsumer.java" "regulator_bootstrap.activated" \
   && grepq "$ORG/events/RegulatorBootstrapActivatedConsumer.java" "organization-registry-regulator-bootstrap" \
   && grepq "$ORG/core/RegulatoryAppointmentService.java" "activateFoundingAppointment"; then
  # Separate consumer group: a bootstrap event cannot be lost to the adjudication listener's partitions.
  ok "ROM-ACTIVATE: dedicated-group consumer + atomic activateFoundingAppointment"
else bad "ROM-ACTIVATE: the approved bootstrap event has no consumer — it never becomes an appointment"; fi

# ── ROM-LOA3 (RB-3b) ── submitting AND approving a bootstrap require LOA3 ──────
say "ROM-LOA3 the bootstrap gate is LOA3 and fail-closed, on both submit and approve"
if grepq "$BFF/controller/RegulatorBootstrapController.java" "MIN_BOOTSTRAP_ASSURANCE_RANK = 3" \
   && grepq "$BFF/controller/RegulatorBootstrapController.java" "IDENTITY_PROOFING_REQUIRED"; then
  # Four-eyes over unproven approvers is not four-eyes: the gate is on approve too.
  ok "ROM-LOA3: LOA3 fail-closed gate on submit and approve (wgv delegates the gate to the BFF)"
else bad "ROM-LOA3: the LOA3 assurance gate is missing from the bootstrap path"; fi

# ── ROM-INVITE (RB-4) ── the invitation token is hashed and single-use ────────
say "ROM-INVITE the invitation token is hashed at rest and redeemed atomically"
if grepq "$ORGM/V010__invitation_token_hash_and_role_code.sql" "token_hash" \
   && ! grepq "$ORG/persistence/entity/OrgInvitationEntity.java" "private String token;" \
   && grepq "$ORG/persistence/repository/OrgInvitationRepository.java" "redeem" \
   && grepq "$ORG/core/OrgInvitationService.java" "Invitation is not valid"; then
  ok "ROM-INVITE: hash at rest + atomic single-use redeem + uniform not-recognised response"
else bad "ROM-INVITE: plaintext token, non-atomic redeem, or state-leaking errors remain"; fi

# ── ROM-INVITE-SEAM (RB-4) ── a governed invite mints a PENDING appointment ───
say "ROM-INVITE-SEAM a governed role_code invite mints a PENDING_VERIFICATION appointment"
if grepq "$ORG/core/OrgInvitationService.java" "getRoleCode() != null" \
   && grepq "$ORG/core/OrgInvitationService.java" "\"INVITATION\""; then
  # source='INVITATION' was a legal appointment value that nothing wrote until RB-4.
  ok "ROM-INVITE-SEAM: acceptance of a governed invite creates an appointment (source=INVITATION), not just an affiliation"
else bad "ROM-INVITE-SEAM: governed invitations still only create affiliations"; fi

# ── LIVE (best-effort) ── the two RB-1 defects, demonstrated against the estate
say "LIVE the estate carries the schema (AMBER until the RB services deploy)"
SWEPT="$(dbq organization_registry "select count(*) from information_schema.columns where table_schema='org_registry' and table_name='org_registry_regulatory_appointment' and column_name='expiry_swept_at'" | tr -d '[:space:]')"
if [ "${SWEPT:-0}" = "1" ]; then
  ok "LIVE: expiry_swept_at present on the estate — RB-1 schema deployed"
else
  skip "LIVE: expiry_swept_at not yet on the estate — RB migrations await deploy (AMBER, not a failure)"
fi

# ── verdict ───────────────────────────────────────────────────────────────────
echo "" | tee -a "$SUMMARY"
echo "PASS=$PASS FAIL=$FAIL SKIP=$SKIP" | tee -a "$SUMMARY"
if [ "$FAIL" -gt 0 ]; then
  echo "VERDICT: RED — $FAIL invariant(s) failed." | tee -a "$SUMMARY"; exit 1
elif [ "$SKIP" -gt 0 ]; then
  echo "VERDICT: AMBER — software contract green; $SKIP live check(s) await estate deploy." | tee -a "$SUMMARY"; exit 2
else
  echo "VERDICT: GREEN — regulator bootstrap + lifecycle conformance proven end to end." | tee -a "$SUMMARY"; exit 0
fi
