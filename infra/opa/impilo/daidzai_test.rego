package impilo.daidzai

import future.keywords

# Fixtures -----------------------------------------------------------------

citizen(action) := {
	"action": action,
	"identity": {"health_id": "HID-C", "identity_type": "citizen"},
	"role_template": "citizen",
	"assurance_loa": 1,
}

bystander(action) := {
	"action": action,
	"identity": {"health_id": "HID-B", "identity_type": "citizen"},
	"role_template": "citizen",
	"assurance_loa": 0,
}

dispatcher(action) := {
	"action": action,
	"identity": {"health_id": "HID-DSP", "identity_type": "staff"},
	"role_template": "dispatcher",
	"assurance_loa": 2,
	"actor_facility_id": "EOC-1",
}

responder(action) := {
	"action": action,
	"identity": {"health_id": "HID-R", "identity_type": "provider"},
	"role_template": "responder",
	"assurance_loa": 2,
	"actor_facility_id": "FAC-1",
}

commander(action) := {
	"action": action,
	"identity": {"health_id": "HID-CMD", "identity_type": "staff"},
	"role_template": "response_commander",
	"assurance_loa": 3,
	"actor_facility_id": "EOC-1",
}

# ── 1. Citizen / bystander SOS is always allowed (life-safety, no payment gate) ──
test_citizen_sos_create_allowed if { allow with input as citizen("daidzai.sos.create") }
test_bystander_sos_create_allowed_even_loa0 if { allow with input as bystander("daidzai.sos.create") }
test_request_create_allowed_for_citizen if { allow with input as citizen("daidzai.request.create") }

# ── 2. Citizen can track OWN request but not others / ops queue ──
test_citizen_self_track_allowed if {
	allow with input as object.union(citizen("daidzai.incident.track"), {"is_subject_self": true})
}
test_citizen_cannot_see_ops_queue if { not allow with input as citizen("daidzai.incident.list") }
test_citizen_cannot_see_disaster_board if { not allow with input as citizen("daidzai.disaster.list") }

# ── 3. Citizen cannot run restricted ops (triage/dispatch/command) ──
test_citizen_cannot_triage if { not allow with input as citizen("daidzai.request.triage") }
test_citizen_cannot_dispatch if { not allow with input as citizen("daidzai.dispatch.request") }
test_citizen_cannot_declare_disaster if { not allow with input as citizen("daidzai.disaster.declare") }

# ── 4. Dispatcher / responder role gating ──
test_dispatcher_can_dispatch if { allow with input as dispatcher("daidzai.dispatch.request") }
test_responder_can_record_mission if { allow with input as responder("daidzai.mission.record") }
test_responder_can_view_ops_queue if { allow with input as responder("daidzai.incident.list") }
test_dispatcher_can_triage if { allow with input as dispatcher("daidzai.request.triage") }

# Low-LOA staff without break-glass cannot dispatch.
test_low_loa_dispatch_denied if {
	not allow with input as object.union(dispatcher("daidzai.dispatch.request"), {"assurance_loa": 1})
}

# ── 5. Disaster command role-gated ──
test_commander_can_declare_disaster if { allow with input as commander("daidzai.disaster.declare") }
test_responder_cannot_declare_disaster if { not allow with input as responder("daidzai.disaster.declare") }
test_commander_can_close_disaster if { allow with input as commander("daidzai.disaster.close") }

# ── 6. Sensitive-type masking ──
# A plain responder cannot view a sensitive incident's identifying detail.
test_sensitive_masked_from_responder if {
	not allow with input as object.union(responder("daidzai.incident.track"), {"incident_sensitive": true})
}
# A protected-access role (commander) can.
test_sensitive_visible_to_protected_role if {
	allow with input as object.union(commander("daidzai.incident.track"), {"incident_sensitive": true})
}
# The subject themself can always see their own sensitive record.
test_sensitive_visible_to_self if {
	allow with input as object.union(citizen("daidzai.request.view"), {"incident_sensitive": true, "is_subject_self": true})
}

# ── 7. Break-glass: emergency override is allowed (and audited upstream) ──
test_break_glass_allows_dispatch_low_loa if {
	allow with input as object.union(responder("daidzai.dispatch.request"), {"assurance_loa": 1, "break_glass": true})
}
test_break_glass_allows_sensitive_view if {
	allow with input as object.union(responder("daidzai.incident.track"), {"incident_sensitive": true, "break_glass": true})
}
