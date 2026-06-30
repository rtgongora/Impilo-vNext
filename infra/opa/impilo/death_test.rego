package impilo.death

import future.keywords

# Fixtures -----------------------------------------------------------------

doctor(action) := {
	"action": action,
	"identity": {"health_id": "HID-DR", "identity_type": "provider"},
	"role_template": "doctor",
}

nurse(action) := {
	"action": action,
	"identity": {"health_id": "HID-NU", "identity_type": "provider"},
	"role_template": "nurse",
}

mortuary(action) := {
	"action": action,
	"identity": {"health_id": "HID-MO", "identity_type": "staff"},
	"role_template": "mortuary_officer",
}

clerk(action) := {
	"action": action,
	"identity": {"health_id": "HID-CL", "identity_type": "staff"},
	"role_template": "registry_clerk",
}

examiner(action) := {
	"action": action,
	"identity": {"health_id": "HID-EX", "identity_type": "staff"},
	"role_template": "medical_examiner",
}

deceased_citizen(action) := {
	"action": action,
	"identity": {"health_id": "HID-DEC", "identity_type": "citizen", "deceased": true},
	"role_template": "citizen",
}

# ── 1. Confirm a death is role-gated ──
test_doctor_can_confirm if { allow with input as doctor("death.confirm") }
test_nurse_cannot_confirm if { not allow with input as nurse("death.confirm") }

# ── 2. Certify requires eligible role + licence + no open medico-legal referral ──
test_doctor_with_licence_can_certify if {
	allow with input as object.union(doctor("death.certify"), {"has_licence": true})
}
test_certify_without_licence_denied if {
	not allow with input as doctor("death.certify")
}
test_nurse_cannot_certify if {
	not allow with input as object.union(nurse("death.certify"), {"has_licence": true})
}
test_certify_blocked_under_open_medico_legal if {
	not allow with input as object.union(doctor("death.certify"), {"has_licence": true, "medico_legal_open": true})
}

# ── 3. Body release requires mortuary role + no active hold ──
test_mortuary_can_release_when_clear if { allow with input as mortuary("death.body.release") }
test_release_blocked_by_active_hold if {
	not allow with input as object.union(mortuary("death.body.release"), {"hold_active": true})
}
test_non_mortuary_cannot_manage_body if { not allow with input as doctor("death.body.receive") }

# ── 4. Medico-legal detail restricted to medico-legal roles (or break-glass) ──
test_examiner_can_view_medico_legal if { allow with input as examiner("death.medicolegal.view") }
test_doctor_cannot_view_medico_legal if { not allow with input as doctor("death.medicolegal.view") }
test_break_glass_allows_medico_legal if {
	allow with input as object.union(doctor("death.medicolegal.view"), {"break_glass": true})
}

# ── 5. Deceased self-service is DENIED ──
test_deceased_self_service_denied if {
	not allow with input as deceased_citizen("citizen.selfservice.access")
}
test_deceased_record_view_denied if {
	not allow with input as deceased_citizen("citizen.record.view")
}

# ── 6. Family portal never sees forensic content ──
test_family_portal_no_forensic if {
	not allow with input as object.union(examiner("death.case.view"), {"portal": "family", "content_forensic": true})
}

# ── 7. CRVS package role-gated ──
test_clerk_can_package_crvs if { allow with input as clerk("death.crvs.package") }
test_nurse_cannot_package_crvs if { not allow with input as nurse("death.crvs.package") }

# ── 8. Staff may read death case / audit; citizen may not ──
test_staff_can_read_case if { allow with input as mortuary("death.case.view") }
test_citizen_cannot_read_case if {
	not allow with input as {"action": "death.case.list", "identity": {"health_id": "HID-C", "identity_type": "citizen"}, "role_template": "citizen"}
}
