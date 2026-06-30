package impilo.policy.conditions_test

import rego.v1

import data.impilo.policy.conditions

# ── effective_loa / parse_assurance_loa ─────────────────────────────────────

test_effective_loa_is_max_of_acr_and_assurance if {
	conditions.effective_loa == 3 with input as {"loa": 1, "assurance_level": "LOA3"}
}

test_effective_loa_uses_acr_when_assurance_absent if {
	conditions.effective_loa == 2 with input as {"loa": 2}
}

test_parse_assurance_variants if {
	conditions.parse_assurance_loa("LOA3") == 3
	conditions.parse_assurance_loa("3") == 3
	conditions.parse_assurance_loa("nonsense") == 0
	conditions.parse_assurance_loa("5") == 0
	conditions.parse_assurance_loa(null) == 0
}

test_account_verified if {
	conditions.account_verified with input as {"assurance_level": "LOA3"}
	conditions.account_verified with input as {"assurance_level": "ACCOUNT_VERIFIED"}
	not conditions.account_verified with input as {"assurance_level": "LOA2"}
}

# ── min_loa ─────────────────────────────────────────────────────────────────

test_min_loa_pass if {
	conditions.conditions_satisfied({"min_loa": 3}) with input as {"loa": 1, "assurance_level": "LOA3"}
}

test_min_loa_fail if {
	not conditions.conditions_satisfied({"min_loa": 3}) with input as {"loa": 1, "assurance_level": "LOA1"}
}

# ── path_contains (segment-bounded + query-smuggle proof) ───────────────────

test_path_contains_segment_match if {
	conditions.conditions_satisfied({"path_contains": "patient-safety/reports"}) with input as {"path": "/internal/v1/patient-safety/reports/123/submit"}
}

test_path_contains_end_of_path if {
	conditions.conditions_satisfied({"path_contains": "patient-safety/reports"}) with input as {"path": "/internal/v1/patient-safety/reports"}
}

test_path_contains_rejects_query_smuggling if {
	# the required substring is only in the query string -> normalised away -> no match
	not conditions.conditions_satisfied({"path_contains": "cadre/decision"}) with input as {"path": "/x/decision?ref=/cadre/decision"}
}

test_path_contains_list_any if {
	conditions.conditions_satisfied({"path_contains": ["a/b", "patient-safety/reports"]}) with input as {"path": "/v1/patient-safety/reports/1"}
}

# ── allowed_facilities (only enforced when a facility_id is present) ─────────

test_allowed_facilities_in_list if {
	conditions.conditions_satisfied({"allowed_facilities": ["fac-1", "fac-2"]}) with input as {"facility_id": "fac-2"}
}

test_allowed_facilities_not_in_list_fails if {
	not conditions.conditions_satisfied({"allowed_facilities": ["fac-1"]}) with input as {"facility_id": "fac-9"}
}

test_allowed_facilities_skipped_when_no_facility if {
	conditions.conditions_satisfied({"allowed_facilities": ["fac-1"]}) with input as {}
}

# ── allowed_actor_types ─────────────────────────────────────────────────────

test_allowed_actor_types if {
	conditions.conditions_satisfied({"allowed_actor_types": ["PROVIDER"]}) with input as {"actor_type": "PROVIDER"}
	not conditions.conditions_satisfied({"allowed_actor_types": ["PROVIDER"]}) with input as {"actor_type": "CITIZEN"}
}

# ── allowed_scope_refs ──────────────────────────────────────────────────────

test_allowed_scope_refs_any if {
	conditions.conditions_satisfied({"allowed_scope_refs": ["ward-7"]}) with input as {"ward_id": "ward-7"}
	not conditions.conditions_satisfied({"allowed_scope_refs": ["ward-7"]}) with input as {"ward_id": "ward-3"}
}

# ── account_assurance_required ──────────────────────────────────────────────

test_account_assurance_required if {
	conditions.conditions_satisfied({"account_assurance_required": true}) with input as {"assurance_level": "LOA3"}
	not conditions.conditions_satisfied({"account_assurance_required": true}) with input as {"assurance_level": "LOA2"}
}

# ── verification grace ──────────────────────────────────────────────────────

test_grace_within_window_passes if {
	conditions.conditions_satisfied({"verification_grace_expiry_epoch_ms": 2000}) with input as {"assurance_level": "LOA1", "now_epoch_ms": 1000}
}

test_grace_expired_fails if {
	not conditions.conditions_satisfied({"verification_grace_expiry_epoch_ms": 2000}) with input as {"assurance_level": "LOA1", "now_epoch_ms": 3000}
}

# ── combined ────────────────────────────────────────────────────────────────

test_all_conditions_combined if {
	c := {"min_loa": 3, "path_contains": "patient-safety/reports", "account_assurance_required": true}
	conditions.conditions_satisfied(c) with input as {"loa": 3, "assurance_level": "LOA3", "path": "/v1/patient-safety/reports/x"}
	not conditions.conditions_satisfied(c) with input as {"loa": 1, "assurance_level": "LOA1", "path": "/v1/patient-safety/reports/x"}
}

# no conditions = unconditional match
test_empty_conditions_pass if {
	conditions.conditions_satisfied({}) with input as {}
}
