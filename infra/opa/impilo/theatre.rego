package impilo.theatre

import future.keywords

# ─────────────────────────────────────────────────────────────────────────────
# Theatre & Perioperative Depth authorization (inpatient-service procedure-episode
# theatre module). Closes the WS#6 theatre-depth deferred seam at the policy layer.
#
# Boundary doctrine (owner-routed, never duplicated):
#   * Orders → OROS; blood → Madi (via OROS BLOOD_BANK); stock → Dura; equipment →
#     asset-registry; provider scope → Varapi; team → Vashandi; record → Butano;
#     death-in-theatre → PCT DeathWorkflow; safety → Rito. This policy authorises the
#     THEATRE action; the owner enforces its own resource policy.
#
# Input contract (subset):
#   input.action                  e.g. "theatre.case.book"
#   input.identity.health_id      person anchor (required)
#   input.identity.identity_type  "citizen" | "provider" | "staff" | "system"
#   input.role_template           e.g. "surgeon", "anaesthetist", "scrub_nurse", "theatre_coordinator"
#   input.assurance_loa           integer LOA
#   input.actor_facility_id       facility the actor operates in
#   input.target_facility_id      facility of the theatre case
#   input.surgical_scope_active   truthy when Varapi confirms an active SURGEON scope (signing)
#   input.anaesthesia_scope_active truthy when Varapi confirms an active ANAESTHETIST scope
#   input.emergency_override      truthy when an explicit emergency override is requested
#   input.override_reason         non-empty justification (required with emergency_override)
# ─────────────────────────────────────────────────────────────────────────────

default allow := false

has_anchor if {
	input.identity.health_id != ""
	input.identity.health_id != null
}

is_citizen if input.identity.identity_type == "citizen"

# Theatre staff roles.
theatre_role if {
	input.role_template in {
		"surgeon",
		"anaesthetist",
		"scrub_nurse",
		"theatre_nurse",
		"theatre_coordinator",
		"consultant",
		"clinician",
		"platform_admin",
	}
}

# Roles permitted to book/triage/manage a theatre case (coordination + senior clinical).
coordinator_role if {
	input.role_template in {"surgeon", "theatre_coordinator", "consultant", "clinician", "ward_manager", "platform_admin"}
}

# Roles permitted to sign an operative note — surgical scope only.
surgical_signer_role if {
	input.role_template in {"surgeon", "consultant", "platform_admin"}
}

# Roles permitted to complete anaesthesia readiness — anaesthesia scope only.
anaesthesia_role if {
	input.role_template in {"anaesthetist", "consultant", "platform_admin"}
}

# Action classes ----------------------------------------------------------------

read_actions := {
	"theatre.queue.view",
	"theatre.case.view",
	"theatre.readiness.view",
	"theatre.note.view",
}

intake_actions := {
	"theatre.case.intake",
	"theatre.case.triage",
}

book_actions := {
	"theatre.case.book",
	"theatre.readiness.evaluate",
}

anaesthesia_actions := {
	"theatre.anaesthesia.complete",
}

note_sign_actions := {
	"theatre.note.sign",
}

safety_actions := {
	"theatre.safety.report",
	"theatre.death.route",
}

start_actions := {
	"theatre.case.start",
}

# Stock / order / blood functions that must NOT be served as theatre actions (owners own them).
foreign_actions := {
	"inventory.stock.adjust",
	"oros.order.place",
	"madi.blood.issue",
	"asset.equipment.decommission",
}

# ── DENY rules ──────────────────────────────────────────────────────────────

# Foreign owner functions are never theatre functions.
deny if input.action in foreign_actions

# Citizens never operate theatre.
deny if {
	input.action in (read_actions | intake_actions | book_actions | anaesthesia_actions | note_sign_actions | safety_actions | start_actions)
	is_citizen
}

# Clinical theatre actions require LOA>=2 unless an audited emergency override.
deny if {
	input.action in (book_actions | anaesthesia_actions | note_sign_actions | start_actions)
	input.assurance_loa < 2
	not valid_override
}

# Out-of-scope signer cannot sign an operative note (Varapi surgical scope required).
deny if {
	input.action in note_sign_actions
	not surgical_signer_role
}

deny if {
	input.action in note_sign_actions
	not input.surgical_scope_active
	not valid_override
}

# Non-anaesthesia role cannot complete anaesthesia readiness.
deny if {
	input.action in anaesthesia_actions
	not anaesthesia_role
}

# Booking/triage requires a coordinator role.
deny if {
	input.action in book_actions
	not coordinator_role
}

# Emergency override requested but no reason → deny (override must be justified + audited).
deny if {
	input.emergency_override == true
	not has_reason
}

# Cross-facility theatre action requires emergency override.
deny if {
	input.action in (book_actions | anaesthesia_actions | note_sign_actions | start_actions)
	input.target_facility_id != ""
	input.actor_facility_id != ""
	input.target_facility_id != input.actor_facility_id
	not valid_override
}

# ── helpers ──────────────────────────────────────────────────────────────────

has_reason if {
	input.override_reason != ""
	input.override_reason != null
}

# A valid emergency override is explicit AND carries a justification (audited upstream).
valid_override if {
	input.emergency_override == true
	has_reason
}

denied if deny

# ── ALLOW rules ────────────────────────────────────────────────────────────────

allow if {
	has_anchor
	not is_citizen
	theatre_role
	input.action in read_actions
	not denied
}

allow if {
	has_anchor
	not is_citizen
	theatre_role
	input.action in intake_actions
	input.assurance_loa >= 2
	not denied
}

allow if {
	has_anchor
	not is_citizen
	coordinator_role
	input.action in book_actions
	input.assurance_loa >= 2
	not denied
}

allow if {
	has_anchor
	not is_citizen
	anaesthesia_role
	input.action in anaesthesia_actions
	input.assurance_loa >= 2
	not denied
}

allow if {
	has_anchor
	not is_citizen
	surgical_signer_role
	input.action in note_sign_actions
	input.surgical_scope_active
	input.assurance_loa >= 2
	not denied
}

# Safety reporting + death routing: any theatre role may report (never block a safety event).
allow if {
	has_anchor
	not is_citizen
	theatre_role
	input.action in safety_actions
	not denied
}

# Start theatre: coordinator/clinical role, LOA gate, not denied (WHO-checklist gate is enforced in-service).
allow if {
	has_anchor
	not is_citizen
	theatre_role
	input.action in start_actions
	input.assurance_loa >= 2
	not denied
}

# Emergency override path: a theatre role may book/start under an explicit, justified override even
# cross-facility or below the usual LOA gate (emergency care is never blocked; override is audited).
allow if {
	has_anchor
	not is_citizen
	theatre_role
	input.action in (book_actions | start_actions)
	valid_override
	not foreign_or_citizen
}

foreign_or_citizen if input.action in foreign_actions
