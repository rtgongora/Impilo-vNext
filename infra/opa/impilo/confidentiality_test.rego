package impilo.confidentiality

import future.keywords

_protected_guardian := {
	"resource_sensitivity": "SPECIALLY_PROTECTED",
	"delegated": true,
	"relationship_type": "GUARDIAN",
	"actor_id": "guardian-1",
	"subject_id": "adolescent-1",
	"purpose": "TREATMENT",
}

_protected_self := {
	"resource_sensitivity": "SPECIALLY_PROTECTED",
	"delegated": false,
	"actor_id": "adolescent-1",
	"subject_id": "adolescent-1",
	"purpose": "TREATMENT",
}

_protected_clinician := {
	"resource_sensitivity": "SPECIALLY_PROTECTED",
	"delegated": false,
	"actor_id": "clinician-1",
	"purpose": "TREATMENT",
}

# ── The delegate exclusion ────────────────────────────────────────────────────
test_guardian_cannot_read_protected if {
	not allow with input as _protected_guardian
}

test_guardian_refusal_reason if {
	refuse_reason == "PROTECTED_RECORD_DELEGATE_DENIED" with input as _protected_guardian
}

test_caregiver_cannot_read_protected if {
	not allow with input as object.union(_protected_guardian, {"relationship_type": "CAREGIVER"})
}

test_rule_grant_does_not_rescue_a_delegate if {
	not allow with input as object.union(_protected_guardian, {"rule_grants_protected_tier": true})
}

test_break_glass_does_not_rescue_a_delegate if {
	not allow with input as object.union(_protected_guardian, {
		"purpose": "BREAK_GLASS",
		"break_glass_active": true,
		"step_up_completed": true,
	})
}

# ── The subject themselves ────────────────────────────────────────────────────
test_subject_reads_own_protected_record if {
	allow with input as _protected_self
}

test_subject_grant_is_audited if {
	audit_event == "CONFIDENTIAL_ACCESS_GRANTED" with input as _protected_self
}

# ── Governed entitlement ──────────────────────────────────────────────────────
test_clinician_without_entitlement_refused if {
	not allow with input as _protected_clinician
}

test_clinician_refusal_reason if {
	refuse_reason == "PROTECTED_RECORD_NOT_ENTITLED" with input as _protected_clinician
}

test_refusal_is_audited if {
	audit_event == "CONFIDENTIAL_ACCESS_REFUSED" with input as _protected_clinician
}

test_clinician_with_governed_entitlement_allowed if {
	allow with input as object.union(_protected_clinician, {"rule_grants_protected_tier": true})
}

# ── Break-glass must be verified, not merely claimed ──────────────────────────
test_verified_break_glass_reaches_protected if {
	allow with input as object.union(_protected_clinician, {
		"purpose": "BREAK_GLASS",
		"break_glass_active": true,
		"step_up_completed": true,
	})
}

test_break_glass_without_step_up_refused if {
	not allow with input as object.union(_protected_clinician, {
		"purpose": "BREAK_GLASS",
		"break_glass_active": true,
	})
}

test_break_glass_without_active_request_refused if {
	not allow with input as object.union(_protected_clinician, {
		"purpose": "BREAK_GLASS",
		"step_up_completed": true,
	})
}

test_bare_emergency_purpose_does_not_reach_protected if {
	not allow with input as object.union(_protected_clinician, {"purpose": "EMERGENCY"})
}

# ── Ordinary care is untouched ────────────────────────────────────────────────
test_ordinary_clinical_is_not_narrowed if {
	allow with input as {
		"resource_sensitivity": "FULL_CLINICAL",
		"delegated": true,
		"relationship_type": "GUARDIAN",
		"actor_id": "guardian-1",
		"subject_id": "child-1",
		"purpose": "TREATMENT",
	}
}

test_ordinary_clinical_emits_no_confidentiality_event if {
	not audit_event with input as {
		"resource_sensitivity": "FULL_CLINICAL",
		"actor_id": "clinician-1",
		"purpose": "TREATMENT",
	}
}

# ── Fail-safe on an absent sensitivity claim ──────────────────────────────────
test_missing_sensitivity_is_not_the_protected_lane if {
	allow with input as {"actor_id": "clinician-1", "purpose": "TREATMENT"}
}
