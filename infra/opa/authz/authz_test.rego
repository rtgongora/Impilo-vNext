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
