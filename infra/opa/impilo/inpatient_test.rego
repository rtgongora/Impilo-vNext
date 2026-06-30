package impilo.inpatient

import future.keywords

# Base fixtures -----------------------------------------------------------------

nurse(action) := {
	"action": action,
	"identity": {"health_id": "HID-N", "identity_type": "provider"},
	"role_template": "ward_nurse",
	"assurance_loa": 2,
	"actor_facility_id": "FAC-1",
	"target_facility_id": "FAC-1",
	"ward_context_active": true,
	"care_context_active": true,
}

doctor(action) := {
	"action": action,
	"identity": {"health_id": "HID-D", "identity_type": "provider"},
	"role_template": "ward_doctor",
	"assurance_loa": 2,
	"actor_facility_id": "FAC-1",
	"target_facility_id": "FAC-1",
	"ward_context_active": true,
	"care_context_active": true,
}

manager(action) := {
	"action": action,
	"identity": {"health_id": "HID-M", "identity_type": "provider"},
	"role_template": "ward_manager",
	"assurance_loa": 2,
	"actor_facility_id": "FAC-1",
	"target_facility_id": "FAC-1",
	"care_context_active": true,
}

citizen(action) := {
	"action": action,
	"identity": {"health_id": "HID-C", "identity_type": "citizen"},
	"role_template": "citizen",
	"assurance_loa": 1,
}

# ── Read surfaces ──────────────────────────────────────────────────────────────
test_bedboard_view_allowed if { allow with input as nurse("inpatient.bedboard.view") }
test_ward_view_allowed if { allow with input as nurse("inpatient.ward.view") }
test_chart_view_allowed if { allow with input as doctor("inpatient.chart.view") }
test_admissions_view_allowed if { allow with input as doctor("inpatient.admissions.view") }
test_escalations_view_allowed if { allow with input as nurse("inpatient.escalations.view") }

# ── Placement ──────────────────────────────────────────────────────────────────
test_admission_request_allowed if { allow with input as doctor("inpatient.admission.request") }
test_bed_assign_allowed if { allow with input as nurse("inpatient.bed.assign") }
test_transfer_request_allowed if { allow with input as nurse("inpatient.transfer.request") }

# ── Clinical writes ────────────────────────────────────────────────────────────
test_observation_record_allowed if { allow with input as nurse("inpatient.observation.record") }
test_ews_record_allowed if { allow with input as nurse("inpatient.ews.record") }
test_medication_administer_allowed if { allow with input as nurse("inpatient.medication.administer") }
test_note_write_allowed if { allow with input as doctor("inpatient.note.write") }
test_escalation_respond_allowed if { allow with input as doctor("inpatient.escalation.respond") }

# ── Senior decisions ───────────────────────────────────────────────────────────
test_admission_approve_allowed_for_doctor if { allow with input as doctor("inpatient.admission.approve") }
test_discharge_finalise_allowed_for_manager if { allow with input as manager("inpatient.discharge.finalise") }

# ── DENY: citizen never operates the ward ──────────────────────────────────────
test_citizen_bedboard_denied if { not allow with input as citizen("inpatient.bedboard.view") }
test_citizen_medication_denied if { not allow with input as citizen("inpatient.medication.administer") }
test_citizen_admission_approve_denied if { not allow with input as citizen("inpatient.admission.approve") }

# ── DENY: low assurance ────────────────────────────────────────────────────────
test_low_loa_medication_denied if {
	not allow with input as object.union(nurse("inpatient.medication.administer"), {"assurance_loa": 1})
}
test_low_loa_admission_request_denied if {
	not allow with input as object.union(doctor("inpatient.admission.request"), {"assurance_loa": 1})
}

# ── DENY: no facility context for clinical write ───────────────────────────────
test_no_facility_clinical_write_denied if {
	not allow with input as object.union(nurse("inpatient.observation.record"), {"actor_facility_id": ""})
}

# ── DENY: no care context for clinical write ───────────────────────────────────
test_no_care_context_clinical_write_denied if {
	not allow with input as object.union(nurse("inpatient.ews.record"), {"care_context_active": false})
}

# ── DENY: cross-facility without break-glass ───────────────────────────────────
test_cross_facility_placement_denied if {
	not allow with input as object.union(nurse("inpatient.bed.assign"), {"target_facility_id": "FAC-2"})
}

# ── DENY: discharge finalise requires senior role (nurse cannot) ───────────────
test_nurse_cannot_finalise_discharge if {
	not allow with input as nurse("inpatient.discharge.finalise")
}
test_nurse_cannot_approve_admission if {
	not allow with input as nurse("inpatient.admission.approve")
}

# ── DENY: stock functions never served by inpatient ────────────────────────────
test_stock_adjust_denied if { not allow with input as doctor("inpatient.stock.adjust") }
test_inventory_issue_denied if { not allow with input as doctor("inventory.stock.issue") }

# ── Break-glass: clinical write without prior care context is allowed under explicit BG ──
test_break_glass_clinical_write_allowed if {
	allow with input as object.union(nurse("inpatient.medication.administer"),
		{"care_context_active": false, "break_glass": true})
}
# Break-glass does NOT widen to senior decisions.
test_break_glass_does_not_grant_discharge_finalise if {
	not allow with input as object.union(nurse("inpatient.discharge.finalise"), {"break_glass": true})
}

# ── No anchor → denied ─────────────────────────────────────────────────────────
test_no_anchor_denied if {
	not allow with input as object.union(nurse("inpatient.bedboard.view"),
		{"identity": {"health_id": "", "identity_type": "provider"}})
}
