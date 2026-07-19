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
