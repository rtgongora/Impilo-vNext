package impilo.theatre

import future.keywords

# Base trust contexts -----------------------------------------------------------

surgeon(action) := {
	"action": action,
	"identity": {"health_id": "HID-1", "identity_type": "provider"},
	"role_template": "surgeon",
	"assurance_loa": 3,
	"actor_facility_id": "FAC-1",
	"target_facility_id": "FAC-1",
	"surgical_scope_active": true,
}

anaesthetist(action) := {
	"action": action,
	"identity": {"health_id": "HID-2", "identity_type": "provider"},
	"role_template": "anaesthetist",
	"assurance_loa": 3,
	"actor_facility_id": "FAC-1",
	"target_facility_id": "FAC-1",
	"anaesthesia_scope_active": true,
}

coordinator(action) := {
	"action": action,
	"identity": {"health_id": "HID-3", "identity_type": "provider"},
	"role_template": "theatre_coordinator",
	"assurance_loa": 3,
	"actor_facility_id": "FAC-1",
	"target_facility_id": "FAC-1",
}

citizen(action) := {
	"action": action,
	"identity": {"health_id": "HID-9", "identity_type": "citizen"},
	"role_template": "citizen",
	"assurance_loa": 2,
}

# Read / intake -----------------------------------------------------------------

test_surgeon_can_view_queue if {
	allow with input as surgeon("theatre.queue.view")
}

test_citizen_cannot_view_queue if {
	not allow with input as citizen("theatre.queue.view")
}

test_coordinator_can_intake if {
	allow with input as surgeon("theatre.case.intake")
}

# Booking -----------------------------------------------------------------------

test_coordinator_can_book if {
	allow with input as coordinator("theatre.case.book")
}

test_scrub_nurse_cannot_book if {
	p := {
		"action": "theatre.case.book",
		"identity": {"health_id": "HID-4", "identity_type": "provider"},
		"role_template": "scrub_nurse",
		"assurance_loa": 3,
		"actor_facility_id": "FAC-1",
		"target_facility_id": "FAC-1",
	}
	not allow with input as p
}

test_low_loa_cannot_book_without_override if {
	p := json.patch(coordinator("theatre.case.book"), [{"op": "replace", "path": "/assurance_loa", "value": 1}])
	not allow with input as p
}

# Note signing — surgical scope only -------------------------------------------

test_surgeon_with_scope_can_sign if {
	allow with input as surgeon("theatre.note.sign")
}

test_surgeon_without_active_scope_cannot_sign if {
	p := json.patch(surgeon("theatre.note.sign"), [{"op": "replace", "path": "/surgical_scope_active", "value": false}])
	not allow with input as p
}

test_anaesthetist_cannot_sign_operative_note if {
	p := json.patch(anaesthetist("theatre.note.sign"), [{"op": "add", "path": "/surgical_scope_active", "value": true}])
	not allow with input as p
}

# Anaesthesia readiness — anaesthesia scope only -------------------------------

test_anaesthetist_can_complete_anaesthesia if {
	allow with input as anaesthetist("theatre.anaesthesia.complete")
}

test_scrub_nurse_cannot_complete_anaesthesia if {
	p := {
		"action": "theatre.anaesthesia.complete",
		"identity": {"health_id": "HID-5", "identity_type": "provider"},
		"role_template": "scrub_nurse",
		"assurance_loa": 3,
		"actor_facility_id": "FAC-1",
		"target_facility_id": "FAC-1",
	}
	not allow with input as p
}

# Emergency override needs a reason --------------------------------------------

test_cross_facility_denied_without_override if {
	p := json.patch(coordinator("theatre.case.book"), [{"op": "replace", "path": "/target_facility_id", "value": "FAC-2"}])
	not allow with input as p
}

test_emergency_override_without_reason_denied if {
	p := json.patch(coordinator("theatre.case.book"), [
		{"op": "replace", "path": "/target_facility_id", "value": "FAC-2"},
		{"op": "add", "path": "/emergency_override", "value": true},
	])
	not allow with input as p
}

test_emergency_override_with_reason_allows_cross_facility if {
	p := json.patch(coordinator("theatre.case.book"), [
		{"op": "replace", "path": "/target_facility_id", "value": "FAC-2"},
		{"op": "add", "path": "/emergency_override", "value": true},
		{"op": "add", "path": "/override_reason", "value": "Life-threatening haemorrhage, nearest theatre"},
	])
	allow with input as p
}

# Start gate --------------------------------------------------------------------

test_surgeon_can_start if {
	allow with input as surgeon("theatre.case.start")
}

# Safety reporting is never blocked for theatre roles --------------------------

test_scrub_nurse_can_report_safety if {
	p := {
		"action": "theatre.safety.report",
		"identity": {"health_id": "HID-6", "identity_type": "provider"},
		"role_template": "scrub_nurse",
		"assurance_loa": 2,
		"actor_facility_id": "FAC-1",
		"target_facility_id": "FAC-1",
	}
	allow with input as p
}

test_theatre_role_can_route_death if {
	allow with input as surgeon("theatre.death.route")
}

# Foreign owner functions are never theatre functions --------------------------

test_stock_adjust_denied_as_theatre if {
	not allow with input as surgeon("inventory.stock.adjust")
}

test_blood_issue_denied_as_theatre if {
	not allow with input as surgeon("madi.blood.issue")
}
