package impilo.authz_test

import rego.v1

import data.impilo.authz

test_allow_clean_treatment if {
	d := authz.decision with input as {"actor_id": "a", "purpose": "TREATMENT", "loa": 3, "assurance_loa": 3}
	d.allow == true
	count(d.deny_reasons) == 0
}

test_deny_unknown_purpose if {
	d := authz.decision with input as {"actor_id": "a", "purpose": "MARKETING", "loa": 3}
	d.allow == false
	"INVALID_PURPOSE" in d.deny_reasons
}

# RB-1: the org-scoped regulatory work session has minted purpose REGULATORY_DUTY since ROM-W2,
# and the ROM policy rows (tshepo-authz V045/V047) are written against it — but it was absent from
# valid_purpose. Under opaMode=ENFORCE every regulatory request in the estate would have denied
# with INVALID_PURPOSE. This test is the tripwire so it cannot silently fall out again.
test_allow_regulatory_duty_purpose if {
	d := authz.decision with input as {"actor_id": "a", "purpose": "REGULATORY_DUTY", "loa": 3, "assurance_loa": 3}
	d.allow == true
	not "INVALID_PURPOSE" in d.deny_reasons
}

test_deny_below_min_loa if {
	d := authz.decision with input as {"actor_id": "a", "purpose": "TREATMENT", "loa": 1, "min_loa": 3, "assurance_loa": 1}
	d.allow == false
	"MIN_LOA" in d.deny_reasons
}

test_min_loa_satisfied_by_propagated_assurance if {
	# effective_loa = max(acr 1, assurance 3) = 3 satisfies min_loa 3 (mirrors G-CZO-01)
	d := authz.decision with input as {"actor_id": "a", "purpose": "TREATMENT", "loa": 1, "min_loa": 3, "assurance_loa": 3}
	d.allow == true
}

test_deny_account_not_verified if {
	d := authz.decision with input as {"actor_id": "a", "purpose": "TREATMENT", "loa": 2, "account_assurance_required": true, "assurance_loa": 2}
	d.allow == false
	"ACCOUNT_NOT_VERIFIED" in d.deny_reasons
}

test_account_verified_at_loa3 if {
	d := authz.decision with input as {"actor_id": "a", "purpose": "TREATMENT", "loa": 3, "account_assurance_required": true, "assurance_loa": 3}
	d.allow == true
}

test_multiple_deny_reasons_sorted if {
	d := authz.decision with input as {"actor_id": "a", "purpose": "BOGUS", "loa": 1, "min_loa": 3, "assurance_loa": 1}
	d.allow == false
	d.deny_reasons == ["INVALID_PURPOSE", "MIN_LOA"]
}

# ── SELF-TREATMENT-BLOCK (GAP-6) ─────────────────────────────────────────────

test_deny_self_treatment_by_provider if {
	d := authz.decision with input as {
		"actor_id": "person-1", "provider_id": "PRV-1", "subject_id": "person-1",
		"purpose": "TREATMENT", "loa": 3, "assurance_loa": 3,
	}
	d.allow == false
	"SELF_TREATMENT" in d.deny_reasons
}

test_allow_provider_treats_a_different_patient if {
	d := authz.decision with input as {
		"actor_id": "person-1", "provider_id": "PRV-1", "subject_id": "person-2",
		"purpose": "TREATMENT", "loa": 3, "assurance_loa": 3,
	}
	d.allow == true
	not "SELF_TREATMENT" in d.deny_reasons
}

test_allow_self_access_when_not_acting_as_provider if {
	# A person accessing their own record without a regulated provider capacity is not
	# self-treatment — self-access to own data is legitimate.
	d := authz.decision with input as {
		"actor_id": "person-1", "subject_id": "person-1",
		"purpose": "TREATMENT", "loa": 3, "assurance_loa": 3,
	}
	d.allow == true
	not "SELF_TREATMENT" in d.deny_reasons
}

# ── PROVIDER-SELF-CLAIM (GAP-6) ──────────────────────────────────────────────

test_deny_provider_self_claim if {
	d := authz.decision with input as {
		"actor_id": "person-1", "subject_id": "person-1", "action": "PROVIDER_CLAIM_APPROVE",
		"purpose": "OPERATIONS", "loa": 3, "assurance_loa": 3,
	}
	d.allow == false
	"PROVIDER_SELF_CLAIM" in d.deny_reasons
}

test_allow_provider_claim_approved_by_someone_else if {
	d := authz.decision with input as {
		"actor_id": "approver-9", "subject_id": "person-1", "action": "PROVIDER_CLAIM_APPROVE",
		"purpose": "OPERATIONS", "loa": 3, "assurance_loa": 3,
	}
	d.allow == true
	not "PROVIDER_SELF_CLAIM" in d.deny_reasons
}

# ── PROVIDER-ID-DENY (GAP-6) ─────────────────────────────────────────────────

test_deny_regulated_action_without_provider_id if {
	d := authz.decision with input as {
		"actor_id": "person-1", "regulated_action": true,
		"purpose": "TREATMENT", "loa": 3, "assurance_loa": 3,
	}
	d.allow == false
	"PROVIDER_ID_REQUIRED" in d.deny_reasons
}

test_allow_regulated_action_with_provider_id if {
	d := authz.decision with input as {
		"actor_id": "person-1", "regulated_action": true, "provider_id": "PRV-1",
		"purpose": "TREATMENT", "loa": 3, "assurance_loa": 3,
	}
	d.allow == true
	not "PROVIDER_ID_REQUIRED" in d.deny_reasons
}

# ── WORK-REQUIRES-ASSIGNMENT (GAP-7) ─────────────────────────────────────────

test_deny_work_zone_without_assignment if {
	d := authz.decision with input as {
		"actor_id": "person-1", "access_mode": "WORK",
		"purpose": "OPERATIONS", "loa": 3, "assurance_loa": 3,
	}
	d.allow == false
	"WORK_REQUIRES_ASSIGNMENT" in d.deny_reasons
}

test_allow_work_zone_with_active_assignment if {
	d := authz.decision with input as {
		"actor_id": "person-1", "access_mode": "WORK", "assignment_active": true,
		"purpose": "OPERATIONS", "loa": 3, "assurance_loa": 3,
	}
	d.allow == true
	not "WORK_REQUIRES_ASSIGNMENT" in d.deny_reasons
}

test_life_zone_needs_no_assignment if {
	# Personal/LIFE-zone access must never require a work assignment.
	d := authz.decision with input as {
		"actor_id": "person-1", "access_mode": "LIFE",
		"purpose": "TREATMENT", "loa": 3, "assurance_loa": 3,
	}
	d.allow == true
	not "WORK_REQUIRES_ASSIGNMENT" in d.deny_reasons
}

# ── Provider+Place W1: LOGIN-PROVIDERID-DENY / LOGIN-PERSON-FIRST ────────────

test_deny_authenticate_with_provider_id if {
	d := authz.decision with input as {
		"actor_id": "person-1", "action": "AUTHENTICATE", "identifier_kind": "PROVIDER_ID",
		"purpose": "OPERATIONS", "loa": 1, "assurance_loa": 1,
	}
	d.allow == false
	"LOGIN_PROVIDERID_DENY" in d.deny_reasons
	"LOGIN_PERSON_FIRST" in d.deny_reasons
}

test_deny_authenticate_with_council_reg if {
	d := authz.decision with input as {
		"actor_id": "person-1", "action": "AUTHENTICATE", "identifier_kind": "council_reg",
		"purpose": "OPERATIONS", "loa": 1, "assurance_loa": 1,
	}
	d.allow == false
	"LOGIN_PROVIDERID_DENY" in d.deny_reasons
}

test_allow_authenticate_with_person_credential if {
	d := authz.decision with input as {
		"actor_id": "person-1", "action": "AUTHENTICATE", "identifier_kind": "EMAIL",
		"purpose": "OPERATIONS", "loa": 1, "assurance_loa": 1,
	}
	d.allow == true
	not "LOGIN_PROVIDERID_DENY" in d.deny_reasons
	not "LOGIN_PERSON_FIRST" in d.deny_reasons
}

test_deny_authenticate_with_place_identifier if {
	d := authz.decision with input as {
		"actor_id": "person-1", "action": "AUTHENTICATE", "identifier_kind": "FACILITY_CODE",
		"purpose": "OPERATIONS", "loa": 1, "assurance_loa": 1,
	}
	d.allow == false
	"LOGIN_PERSON_FIRST" in d.deny_reasons
}

test_login_rules_inert_without_identifier_kind if {
	# Deny-safe: an AUTHENTICATE decision without the new input field is untouched.
	d := authz.decision with input as {
		"actor_id": "person-1", "action": "AUTHENTICATE",
		"purpose": "OPERATIONS", "loa": 1, "assurance_loa": 1,
	}
	not "LOGIN_PROVIDERID_DENY" in d.deny_reasons
	not "LOGIN_PERSON_FIRST" in d.deny_reasons
}

# ── Provider+Place W3: WORK-TOKEN-CONTEXT-MATCH ──────────────────────────────

test_deny_work_request_outside_token_facility if {
	d := authz.decision with input as {
		"actor_id": "person-1", "access_mode": "WORK", "assignment_active": true,
		"work_context": {"facility_id": "fac-a", "workspace_id": "ws-1"},
		"scope": {"facility": "fac-b"},
		"purpose": "TREATMENT", "loa": 3, "assurance_loa": 3,
	}
	d.allow == false
	"WORK_TOKEN_CONTEXT_MISMATCH" in d.deny_reasons
}

test_deny_work_request_outside_token_workspace if {
	d := authz.decision with input as {
		"actor_id": "person-1", "access_mode": "WORK", "assignment_active": true,
		"work_context": {"facility_id": "fac-a", "workspace_id": "ws-1"},
		"scope": {"facility": "fac-a", "workspace": "ws-2"},
		"purpose": "TREATMENT", "loa": 3, "assurance_loa": 3,
	}
	d.allow == false
	"WORK_TOKEN_CONTEXT_MISMATCH" in d.deny_reasons
}

test_allow_work_request_matching_token_context if {
	d := authz.decision with input as {
		"actor_id": "person-1", "access_mode": "WORK", "assignment_active": true,
		"work_context": {"facility_id": "FAC-A", "workspace_id": "ws-1"},
		"scope": {"facility": "fac-a", "workspace": "WS-1"},
		"purpose": "TREATMENT", "loa": 3, "assurance_loa": 3,
	}
	d.allow == true
	not "WORK_TOKEN_CONTEXT_MISMATCH" in d.deny_reasons
}

test_context_match_inert_without_work_context if {
	# Deny-safe: no token context supplied => the rule never fires.
	d := authz.decision with input as {
		"actor_id": "person-1", "access_mode": "WORK", "assignment_active": true,
		"scope": {"facility": "fac-a"},
		"purpose": "TREATMENT", "loa": 3, "assurance_loa": 3,
	}
	not "WORK_TOKEN_CONTEXT_MISMATCH" in d.deny_reasons
}

test_deny_authenticate_with_badge_serial if {
	d := authz.decision with input as {
		"actor_id": "person-1", "action": "AUTHENTICATE", "identifier_kind": "BADGE_SERIAL",
		"purpose": "OPERATIONS", "loa": 1, "assurance_loa": 1,
	}
	d.allow == false
	"BADGE_NEVER_AUTHORISES" in d.deny_reasons
}

# ── Authentication assurance: MIN_AAL / AUTH_TOO_OLD / PHISHING_RESISTANCE_REQUIRED ──────
# These close the three POLICY_COVERAGE_GAPS registered in OpaShadowInputMapper (dimensions the
# Java PolicyEngine enforces where the corpus had no rule, so OPA allowed while Java denied).

# ── MIN_AAL ──────────────────────────────────────────────────────────────────

test_deny_below_min_aal if {
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3,
		"min_aal": 2, "authentication_aal": 1,
	}
	d.allow == false
	"MIN_AAL" in d.deny_reasons
}

test_min_aal_satisfied_exactly_at_the_minimum if {
	# Boundary: Java is `aal < minAal`, so equality PASSES.
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3,
		"min_aal": 2, "authentication_aal": 2,
	}
	d.allow == true
	not "MIN_AAL" in d.deny_reasons
}

test_deny_one_below_min_aal_boundary if {
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3,
		"min_aal": 3, "authentication_aal": 2,
	}
	d.allow == false
	"MIN_AAL" in d.deny_reasons
}

test_min_aal_satisfied_above_the_minimum if {
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3,
		"min_aal": 1, "authentication_aal": 3,
	}
	d.allow == true
}

test_deny_min_aal_with_no_authentication_at_all if {
	# The mapper emits aal 0 for a request with no AuthenticationAssurance, mirroring none().
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3,
		"min_aal": 1, "authentication_aal": 0,
	}
	d.allow == false
	"MIN_AAL" in d.deny_reasons
}

test_min_aal_inert_without_the_rule_condition if {
	# Deny-safe: no min_aal on the matched rule => the check never fires, however weak the login.
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3,
		"authentication_aal": 0,
	}
	d.allow == true
	not "MIN_AAL" in d.deny_reasons
}

test_min_aal_inert_without_the_assurance_fact if {
	# Deny-safe strangler: until the mapper emits authentication_aal the rule cannot deny.
	d := authz.decision with input as {"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "min_aal": 3}
	d.allow == true
	not "MIN_AAL" in d.deny_reasons
}

test_min_aal_does_not_feed_effective_loa if {
	# AAL is a separate scale from LoA: a strong login must NOT satisfy an identity-proofing
	# minimum. Mirrors PolicyEngineTest "identity LOA never substitutes for authentication min_aal",
	# in the other direction — assurance_loa 1 with min_loa 3 still denies at AAL 3.
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 1, "min_loa": 3,
		"min_aal": 2, "authentication_aal": 3,
	}
	d.allow == false
	"MIN_LOA" in d.deny_reasons
	not "MIN_AAL" in d.deny_reasons
}

# ── AUTH_TOO_OLD ─────────────────────────────────────────────────────────────

test_deny_authentication_too_old if {
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 2,
		"max_auth_age_seconds": 300, "authentication_age_seconds": 900,
	}
	d.allow == false
	"AUTH_TOO_OLD" in d.deny_reasons
}

test_authentication_age_exactly_at_the_maximum_passes if {
	# Boundary: isFresh is `!reference.plusSeconds(max).isBefore(now)` — age == max is still FRESH.
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 2,
		"max_auth_age_seconds": 300, "authentication_age_seconds": 300,
	}
	d.allow == true
	not "AUTH_TOO_OLD" in d.deny_reasons
}

test_deny_authentication_one_second_over_the_maximum if {
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 2,
		"max_auth_age_seconds": 300, "authentication_age_seconds": 301,
	}
	d.allow == false
	"AUTH_TOO_OLD" in d.deny_reasons
}

test_fresh_authentication_allows if {
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 2,
		"max_auth_age_seconds": 300, "authentication_age_seconds": 0,
	}
	d.allow == true
}

test_deny_when_no_authentication_instant_exists if {
	# Java: isFresh returns false when both stepUpTime and authenticationTime are null. The mapper
	# signals it by omitting the age while still sending authentication_aal.
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 0,
		"max_auth_age_seconds": 300,
	}
	d.allow == false
	"AUTH_TOO_OLD" in d.deny_reasons
}

test_auth_age_inert_without_the_rule_condition if {
	# Deny-safe: no max_auth_age_seconds on the matched rule => freshness is not evaluated.
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 2,
		"authentication_age_seconds": 99999,
	}
	d.allow == true
	not "AUTH_TOO_OLD" in d.deny_reasons
}

test_auth_age_inert_when_mapper_sends_neither_fact if {
	# Deny-safe strangler: an un-upgraded mapper sends no authentication_* facts at all, and the
	# "no authentication instant" branch must NOT fire on that — otherwise the rule would deny every
	# request in SHADOW the moment a max_auth_age_seconds rule matched.
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3,
		"max_auth_age_seconds": 300,
	}
	d.allow == true
	not "AUTH_TOO_OLD" in d.deny_reasons
}

test_zero_max_auth_age_disables_the_freshness_check if {
	# Java gates on `maxAgeSeconds > 0`; a zero maximum disables the check rather than denying all.
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 2,
		"max_auth_age_seconds": 0, "authentication_age_seconds": 99999,
	}
	d.allow == true
	not "AUTH_TOO_OLD" in d.deny_reasons
}

test_zero_max_auth_age_does_not_deny_a_missing_instant if {
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 2,
		"max_auth_age_seconds": 0,
	}
	d.allow == true
	not "AUTH_TOO_OLD" in d.deny_reasons
}

# ── PHISHING_RESISTANCE_REQUIRED ─────────────────────────────────────────────

test_deny_when_phishing_resistance_required_and_absent if {
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 2,
		"phishing_resistant_required": true, "authentication_phishing_resistant": false,
	}
	d.allow == false
	"PHISHING_RESISTANCE_REQUIRED" in d.deny_reasons
}

test_allow_when_phishing_resistance_required_and_present if {
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 2,
		"phishing_resistant_required": true, "authentication_phishing_resistant": true,
	}
	d.allow == true
	not "PHISHING_RESISTANCE_REQUIRED" in d.deny_reasons
}

test_phishing_resistance_not_required_permits_a_weak_factor if {
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 2,
		"phishing_resistant_required": false, "authentication_phishing_resistant": false,
	}
	d.allow == true
	not "PHISHING_RESISTANCE_REQUIRED" in d.deny_reasons
}

test_phishing_rule_inert_without_the_rule_condition if {
	# Deny-safe: no phishing_resistant_required on the matched rule => the check never fires.
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 2,
		"authentication_phishing_resistant": false,
	}
	d.allow == true
	not "PHISHING_RESISTANCE_REQUIRED" in d.deny_reasons
}

test_phishing_rule_inert_without_the_assurance_fact if {
	# Deny-safe strangler: until the mapper emits the fact the rule cannot deny.
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "authentication_aal": 2,
		"phishing_resistant_required": true,
	}
	d.allow == true
	not "PHISHING_RESISTANCE_REQUIRED" in d.deny_reasons
}

# ── Composition ──────────────────────────────────────────────────────────────

test_all_three_authentication_reasons_compose_and_sort if {
	# Java evaluates all four sub-checks in one meetsAuthenticationRequirement call; the policy
	# reports each independently so audit shows which dimension failed.
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3,
		"min_aal": 3, "authentication_aal": 1,
		"max_auth_age_seconds": 300, "authentication_age_seconds": 600,
		"phishing_resistant_required": true, "authentication_phishing_resistant": false,
	}
	d.allow == false
	d.deny_reasons == ["AUTH_TOO_OLD", "MIN_AAL", "PHISHING_RESISTANCE_REQUIRED"]
}

test_strong_recent_phishing_resistant_login_allows if {
	d := authz.decision with input as {
		"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "min_loa": 3,
		"min_aal": 2, "authentication_aal": 2,
		"max_auth_age_seconds": 300, "authentication_age_seconds": 12,
		"phishing_resistant_required": true, "authentication_phishing_resistant": true,
	}
	d.allow == true
	count(d.deny_reasons) == 0
}

test_existing_decisions_untouched_by_the_new_rules if {
	# Regression guard: the shape of input the mapper sends TODAY (no authentication_* keys) must
	# produce exactly the verdict it did before these rules existed.
	d := authz.decision with input as {"actor_id": "a", "purpose": "TREATMENT", "assurance_loa": 3, "min_loa": 3}
	d.allow == true
	count(d.deny_reasons) == 0
}
