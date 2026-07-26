package impilo.confidentiality

import future.keywords

# The pack as shipped: not ratified, so it permits no category.
_inert_pack := {"pack_ratified": false, "pack_categories": []}

# A ratified pack permitting the two safeguarding-family categories.
_ratified_pack := {
	"pack_ratified": true,
	"pack_categories": ["SAFEGUARDING", "GENDER_BASED_VIOLENCE"],
}

_guardian := {
	"resource_sensitivity": "SPECIALLY_PROTECTED",
	"record_category": "SAFEGUARDING",
	"delegated": true,
	"relationship_type": "GUARDIAN",
	"actor_id": "guardian-1",
	"subject_id": "adolescent-1",
	"purpose": "TREATMENT",
	"rule_categories": [],
}

_self := {
	"resource_sensitivity": "SPECIALLY_PROTECTED",
	"record_category": "HIV",
	"delegated": false,
	"actor_id": "adolescent-1",
	"subject_id": "adolescent-1",
	"purpose": "TREATMENT",
	"rule_categories": [],
}

_clinician := {
	"resource_sensitivity": "SPECIALLY_PROTECTED",
	"record_category": "SAFEGUARDING",
	"delegated": false,
	"actor_id": "clinician-1",
	"subject_id": "adolescent-1",
	"purpose": "TREATMENT",
	"rule_categories": [],
}

_enforce(base, pack, extra) := object.union(object.union(object.union(base, pack), {"mode": "ENFORCE"}), extra)

# ── Inertness: the shipped default changes nothing ────────────────────────────
test_shadow_is_the_default_and_never_refuses if {
	allow with input as object.union(_guardian, _inert_pack)
}

test_shadow_still_audits_the_refusal if {
	audit_event == "CONFIDENTIAL_ACCESS_REFUSED" with input as object.union(_guardian, _inert_pack)
}

test_off_mode_evaluates_nothing if {
	allow with input as object.union(object.union(_guardian, _inert_pack), {"mode": "OFF"})
	not audit_event with input as object.union(object.union(_guardian, _inert_pack), {"mode": "OFF"})
}

test_enforce_without_a_ratified_pack_stays_inert if {
	allow with input as _enforce(_guardian, _inert_pack, {})
}

test_enforce_without_a_ratified_pack_says_so_loudly if {
	audit_event == "CONFIDENTIALITY_ENFORCE_UNAVAILABLE" with input as _enforce(_guardian, _inert_pack, {})
}

# ── The delegate exclusion ────────────────────────────────────────────────────
test_guardian_cannot_read_protected if {
	not allow with input as _enforce(_guardian, _ratified_pack, {})
}

test_guardian_refusal_reason if {
	refuse_reason == "PROTECTED_RECORD_DELEGATE_DENIED" with input as _enforce(_guardian, _ratified_pack, {})
}

test_caregiver_cannot_read_protected if {
	not allow with input as _enforce(_guardian, _ratified_pack, {"relationship_type": "CAREGIVER"})
}

test_rule_grant_does_not_rescue_a_delegate if {
	not allow with input as _enforce(_guardian, _ratified_pack, {"rule_categories": ["SAFEGUARDING"]})
}

# ── The subject themselves ────────────────────────────────────────────────────
test_subject_reads_own_protected_record if {
	allow with input as _enforce(_self, _ratified_pack, {})
}

test_subject_grant_is_audited if {
	audit_event == "CONFIDENTIAL_ACCESS_GRANTED" with input as _enforce(_self, _ratified_pack, {})
}

# ── Category-scoped governed grants ───────────────────────────────────────────
test_clinician_without_a_category_grant_refused if {
	not allow with input as _enforce(_clinician, _ratified_pack, {})
}

test_clinician_refusal_reason if {
	refuse_reason == "PROTECTED_RECORD_NOT_ENTITLED" with input as _enforce(_clinician, _ratified_pack, {})
}

test_clinician_with_the_matching_category_allowed if {
	allow with input as _enforce(_clinician, _ratified_pack, {"rule_categories": ["SAFEGUARDING"]})
}

test_a_grant_for_another_category_does_not_cover_this_record if {
	not allow with input as _enforce(_clinician, _ratified_pack, {"rule_categories": ["GENDER_BASED_VIOLENCE"]})
}

test_holding_safeguarding_does_not_confer_mental_health if {
	not allow with input as _enforce(
		object.union(_clinician, {"record_category": "MENTAL_HEALTH"}),
		_ratified_pack,
		{"rule_categories": ["SAFEGUARDING"]},
	)
}

test_a_category_outside_the_ratified_pack_is_dropped if {
	not allow with input as _enforce(
		object.union(_clinician, {"record_category": "HIV"}),
		_ratified_pack,
		{"rule_categories": ["HIV"]},
	)
}

test_pack_inert_reason_when_a_rule_asked_for_something if {
	refuse_reason == "PROTECTED_RECORD_PACK_INERT" with input as _enforce(
		object.union(_clinician, {"record_category": "HIV"}),
		_ratified_pack,
		{"rule_categories": ["HIV"]},
	)
}

# ── Emergency access is a hard requirement ────────────────────────────────────
test_emergency_purpose_waives if {
	allow with input as _enforce(_clinician, _ratified_pack, {"purpose": "EMERGENCY"})
}

test_break_glass_purpose_waives if {
	allow with input as _enforce(_clinician, _ratified_pack, {"purpose": "BREAK_GLASS"})
}

test_emergency_waiver_precedes_the_delegate_exclusion if {
	allow with input as _enforce(_guardian, _ratified_pack, {"purpose": "EMERGENCY"})
}

test_emergency_waiver_is_audited_as_a_grant if {
	audit_event == "CONFIDENTIAL_ACCESS_GRANTED" with input as _enforce(_clinician, _ratified_pack, {"purpose": "EMERGENCY"})
}

test_emergency_reaches_a_category_outside_the_pack if {
	allow with input as _enforce(
		object.union(_clinician, {"record_category": "HIV"}),
		_ratified_pack,
		{"purpose": "EMERGENCY"},
	)
}

# ── Ordinary care is untouched ────────────────────────────────────────────────
test_ordinary_clinical_is_not_narrowed if {
	allow with input as _enforce(
		object.union(_guardian, {"resource_sensitivity": "FULL_CLINICAL"}),
		_ratified_pack,
		{},
	)
}

test_ordinary_clinical_emits_no_confidentiality_event if {
	not audit_event with input as _enforce(
		object.union(_clinician, {"resource_sensitivity": "FULL_CLINICAL"}),
		_ratified_pack,
		{},
	)
}

test_missing_sensitivity_is_not_the_protected_lane if {
	allow with input as _enforce({"actor_id": "clinician-1", "purpose": "TREATMENT", "rule_categories": []}, _ratified_pack, {})
}
