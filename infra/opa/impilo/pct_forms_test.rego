package impilo.pct_forms

import future.keywords

_anchor := {"health_id": "HID-provider-1"}

# ── Reads ─────────────────────────────────────────────────────────────────────
test_active_nurse_can_resolve if {
	allow with input as {
		"action": "pct_form.resolve", "identity": _anchor,
		"cadre_family": "NURSE", "licence_status": "ACTIVE",
	}
}

test_unverified_cannot_view if {
	not allow with input as {
		"action": "pct_form.view", "identity": _anchor,
		"cadre_family": "NURSE", "licence_status": "UNVERIFIED",
	}
}

# ── Writes ────────────────────────────────────────────────────────────────────
test_active_doctor_in_scope_can_submit if {
	allow with input as {
		"action": "pct_form.submit", "identity": _anchor,
		"cadre_family": "PHYSICIAN", "licence_status": "ACTIVE", "facility_in_scope": true,
	}
}

test_out_of_facility_scope_cannot_submit if {
	not allow with input as {
		"action": "pct_form.submit", "identity": _anchor,
		"cadre_family": "PHYSICIAN", "licence_status": "ACTIVE", "facility_in_scope": false,
	}
}

test_suspended_provider_blocked_from_write if {
	not allow with input as {
		"action": "pct_form.submit", "identity": _anchor,
		"cadre_family": "PHYSICIAN", "licence_status": "SUSPENDED", "facility_in_scope": true,
	}
}

test_suspended_write_deny_reason if {
	d := decision with input as {
		"action": "pct_form.submit", "identity": _anchor,
		"cadre_family": "PHYSICIAN", "licence_status": "SUSPENDED", "facility_in_scope": true,
	}
	"LICENCE_NOT_ACTIVE" in d.deny_reasons
}

# ── Break-glass ───────────────────────────────────────────────────────────────
test_break_glass_emergency_write_allowed_despite_expired_licence if {
	allow with input as {
		"action": "pct_form.submit", "identity": _anchor,
		"cadre_family": "NURSE", "licence_status": "EXPIRED",
		"facility_in_scope": false, "break_glass": true, "purpose": "EMERGENCY",
	}
}

# ── Countersign ───────────────────────────────────────────────────────────────
test_supervisor_can_countersign if {
	allow with input as {
		"action": "pct_form.countersign", "identity": _anchor,
		"cadre_family": "PHYSICIAN", "licence_status": "ACTIVE",
		"is_supervisor": true, "is_trainee": false,
	}
}

test_trainee_cannot_countersign if {
	not allow with input as {
		"action": "pct_form.countersign", "identity": _anchor,
		"cadre_family": "PHYSICIAN", "licence_status": "ACTIVE",
		"is_supervisor": true, "is_trainee": true,
	}
}

test_trainee_countersign_deny_reason if {
	d := decision with input as {
		"action": "pct_form.countersign", "identity": _anchor,
		"cadre_family": "PHYSICIAN", "licence_status": "ACTIVE",
		"is_supervisor": true, "is_trainee": true,
	}
	"TRAINEE_CANNOT_COUNTERSIGN" in d.deny_reasons
}

test_non_supervisor_cannot_countersign if {
	not allow with input as {
		"action": "pct_form.countersign", "identity": _anchor,
		"cadre_family": "PHYSICIAN", "licence_status": "ACTIVE", "is_supervisor": false,
	}
}

# ── Sensitive / restricted forms ──────────────────────────────────────────────
test_restricted_form_requires_grant if {
	not allow with input as {
		"action": "pct_form.view", "identity": _anchor,
		"cadre_family": "PHYSICIAN", "licence_status": "ACTIVE",
		"form_sensitivity": "RESTRICTED", "restricted_access_granted": false,
	}
}

test_restricted_form_with_grant_ok if {
	allow with input as {
		"action": "pct_form.view", "identity": _anchor,
		"cadre_family": "PHYSICIAN", "licence_status": "ACTIVE",
		"form_sensitivity": "RESTRICTED", "restricted_access_granted": true,
	}
}

test_sensitive_form_needs_active_licence if {
	allow with input as {
		"action": "pct_form.view", "identity": _anchor,
		"cadre_family": "NURSE", "licence_status": "ACTIVE", "form_sensitivity": "SENSITIVE",
	}
}

# ── Admin ─────────────────────────────────────────────────────────────────────
test_forms_admin_can_administer if {
	allow with input as {
		"action": "pct_form.admin", "identity": _anchor, "is_forms_admin": true,
	}
}

test_non_admin_cannot_administer if {
	not allow with input as {
		"action": "pct_form.admin", "identity": _anchor,
		"cadre_family": "PHYSICIAN", "licence_status": "ACTIVE", "is_forms_admin": false,
	}
}

# ── Cadre eligibility ─────────────────────────────────────────────────────────
test_non_clinical_cadre_cannot_write if {
	not allow with input as {
		"action": "pct_form.submit", "identity": _anchor,
		"cadre_family": "OTHER", "licence_status": "ACTIVE", "facility_in_scope": true,
	}
}
