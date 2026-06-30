package impilo.vashandi_test

import rego.v1

import data.impilo.vashandi

# ── Allows ──────────────────────────────────────────────────────────────────

test_valid_check_in_allowed if {
	vashandi.allow with input as {
		"action": "vashandi.attendance.check_in",
		"active_vashandi_assignment": true,
		"active_rostered_shift": true,
		"vashandi_workspace": "vashandi.attendance",
		"visible_vashandi_workspaces": ["vashandi.attendance"],
	}
}

test_supervisor_confirm_allowed_for_supervisor if {
	vashandi.allow with input as {
		"action": "vashandi.attendance.supervisor_confirm",
		"role_template": "vashandi_attendance_supervisor",
		"vashandi_workspace": "vashandi.attendance",
		"visible_vashandi_workspaces": ["vashandi.attendance"],
	}
}

# ── Denies (security-critical) ──────────────────────────────────────────────

# Attendance check-in must NOT confer clinical authority.
test_check_in_does_not_grant_clinical_authority if {
	vashandi.deny with input as {
		"action": "clinical.encounter.start",
		"attendance_event_type": "check_in",
	}
}

test_check_in_without_assignment_denied if {
	vashandi.deny with input as {
		"action": "vashandi.attendance.check_in",
		"active_vashandi_assignment": false,
		"active_rostered_shift": true,
	}
}

test_check_in_without_rostered_shift_denied if {
	vashandi.deny with input as {
		"action": "vashandi.attendance.check_in",
		"active_vashandi_assignment": true,
		"active_rostered_shift": false,
	}
}

# A suspended/under-investigation worker cannot activate an assignment.
test_activation_blocked_by_provider_status if {
	vashandi.deny with input as {
		"action": "vashandi.assignments.activate",
		"provider_worker_status": "under_investigation",
	}
}

test_activation_blocked_by_council_suspension if {
	vashandi.deny with input as {
		"action": "vashandi.assignments.activate",
		"council_registration_status": "suspended",
	}
}

# Facility workforce manager may only act within their own facility.
test_facility_manager_cannot_act_outside_own_facility if {
	vashandi.deny with input as {
		"action": "vashandi.assignments.activate",
		"role_template": "vashandi_facility_workforce_manager",
		"target_facility_id": "fac-OTHER",
		"manager_facility_id": "fac-MINE",
	}
}

# Org workforce admin may only manage their own organisation.
test_org_admin_cannot_manage_other_organisation if {
	vashandi.deny with input as {
		"action": "vashandi.organisation_workforce.manage",
		"role_template": "vashandi_organisation_workforce_admin",
		"target_organisation_id": "org-OTHER",
		"manager_organisation_id": "org-MINE",
	}
}

# A vashandi role template does not grant clinical service delivery by default.
test_vashandi_role_does_not_grant_clinical if {
	vashandi.deny with input as {
		"action": "clinical.encounter.start",
		"role_template": "vashandi_attendance_supervisor",
	}
}

# Default-deny: an unknown vashandi action with no matching allow is not allowed.
test_unknown_action_default_deny if {
	not vashandi.allow with input as {"action": "vashandi.unknown", "visible_vashandi_workspaces": []}
}
