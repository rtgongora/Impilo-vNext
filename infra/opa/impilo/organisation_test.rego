package impilo.organisation_test

import rego.v1

import data.impilo.organisation

active_provider := {
	"identity_type": "provider",
}

active_org := {
	"lifecycle_status": "active",
	"active_organisation_assignment": true,
	"organisation_type": "public_hospital",
	"visible_management_workspaces": [],
}

# ── Allows ──────────────────────────────────────────────────────────────────

test_active_assigned_org_may_enter_work if {
	organisation.allow with input as {
		"action": "work.context.enter",
		"identity": active_provider,
		"organisation": active_org,
	}
}

test_visible_management_workspace_may_enter if {
	organisation.allow with input as {
		"action": "management.workspace.enter",
		"management_workspace": "facility_admin",
		"identity": active_provider,
		"organisation": object.union(active_org, {"visible_management_workspaces": ["facility_admin"]}),
	}
}

# ── Denies (security-critical) ──────────────────────────────────────────────

test_provider_without_assignment_denied if {
	organisation.deny with input as {
		"action": "work.context.enter",
		"identity": active_provider,
		"organisation": object.union(active_org, {"active_organisation_assignment": false}),
	}
}

test_suspended_org_denied if {
	organisation.deny with input as {
		"action": "work.context.enter",
		"identity": active_provider,
		"organisation": object.union(active_org, {"lifecycle_status": "suspended"}),
	}
}

test_sandbox_cannot_call_production_marketplace_api if {
	organisation.deny with input as {
		"action": "marketplace.api.orders",
		"identity": active_provider,
		"organisation": object.union(active_org, {"access_environment": "sandbox"}),
	}
}

test_non_sovereign_cannot_enter_national_governance if {
	organisation.deny with input as {
		"action": "management.workspace.enter",
		"management_workspace": "national_identity_governance",
		"identity": active_provider,
		"organisation": object.union(active_org, {"organisation_type": "private_hospital"}),
	}
}

test_private_provider_cannot_manage_public_facility_staff if {
	organisation.deny with input as {
		"action": "management.workspace.enter",
		"management_workspace": "public_facility_staff_management",
		"identity": active_provider,
		"organisation": object.union(active_org, {"organisation_type": "private_clinic"}),
	}
}

test_vendor_cannot_browse_clinical_records if {
	organisation.deny with input as {
		"action": "clinical.record.view",
		"identity": active_provider,
		"organisation": object.union(active_org, {"organisation_type": "software_vendor"}),
	}
}

test_insurer_cannot_access_clinical if {
	organisation.deny with input as {
		"action": "clinical.record.view",
		"identity": active_provider,
		"organisation": object.union(active_org, {"organisation_type": "insurer"}),
	}
}

test_unapproved_cross_border_clinical_denied if {
	organisation.deny with input as {
		"action": "clinical.record.view",
		"identity": active_provider,
		"organisation": object.union(active_org, {"cross_border_access_flag": true, "cross_border_approved": false}),
	}
}
