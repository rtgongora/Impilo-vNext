package impilo.budget

import future.keywords

_anchor := {"health_id": "HID-1"}

test_holder_can_create if {
	allow with input as {"action": "budget.create", "identity": _anchor, "user_type": "BUDGET_HOLDER"}
}

test_reviewer_cannot_create if {
	not allow with input as {"action": "budget.create", "identity": _anchor, "user_type": "FINANCE_REVIEWER"}
}

test_approver_can_approve_when_not_creator if {
	allow with input as {
		"action": "budget.approve", "identity": _anchor, "user_type": "BUDGET_APPROVER",
		"actor_scope": "FACILITY", "budget_scope": "FACILITY", "is_creator": false,
	}
}

test_creator_cannot_self_approve if {
	not allow with input as {
		"action": "budget.approve", "identity": _anchor, "user_type": "BUDGET_APPROVER",
		"actor_scope": "NATIONAL", "budget_scope": "FACILITY", "is_creator": true,
	}
}

test_self_approval_reason_present if {
	d := decision with input as {
		"action": "budget.approve", "identity": _anchor, "user_type": "BUDGET_APPROVER",
		"budget_scope": "FACILITY", "is_creator": true,
	}
	"SELF_APPROVAL" in d.deny_reasons
}

test_facility_actor_cannot_approve_national_budget if {
	not allow with input as {
		"action": "budget.approve", "identity": _anchor, "user_type": "BUDGET_APPROVER",
		"actor_scope": "FACILITY", "budget_scope": "NATIONAL", "is_creator": false,
	}
}

test_national_actor_can_approve_national_budget if {
	allow with input as {
		"action": "budget.approve", "identity": _anchor, "user_type": "FINANCE_ADMIN",
		"actor_scope": "NATIONAL", "budget_scope": "NATIONAL", "is_creator": false,
	}
}

test_out_of_scope_reason_present if {
	d := decision with input as {
		"action": "budget.activate", "identity": _anchor, "user_type": "BUDGET_APPROVER",
		"actor_scope": "FACILITY", "budget_scope": "NATIONAL",
	}
	"OUT_OF_SCOPE" in d.deny_reasons
}

test_emergency_override_requires_elevated_role if {
	allow with input as {"action": "budget.availability.override", "identity": _anchor, "user_type": "EMERGENCY_COMMANDER"}
	not allow with input as {"action": "budget.availability.override", "identity": _anchor, "user_type": "BUDGET_HOLDER"}
}

test_funder_view_restricted_to_matching_grant if {
	allow with input as {"action": "budget.view", "identity": _anchor, "user_type": "FUNDER", "funder_grant_match": true}
	not allow with input as {"action": "budget.view", "identity": _anchor, "user_type": "FUNDER", "funder_grant_match": false}
}

test_threshold_manage_admin_only if {
	allow with input as {"action": "budget.threshold.manage", "identity": _anchor, "user_type": "FINANCE_ADMIN"}
	not allow with input as {"action": "budget.threshold.manage", "identity": _anchor, "user_type": "BUDGET_HOLDER"}
}

test_no_person_anchor_denied if {
	d := decision with input as {"action": "budget.create", "identity": {"health_id": ""}, "user_type": "BUDGET_HOLDER"}
	not d.allow
	"NO_PERSON_ANCHOR" in d.deny_reasons
}
