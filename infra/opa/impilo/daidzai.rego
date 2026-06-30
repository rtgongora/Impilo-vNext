package impilo.daidzai

import future.keywords

# ─────────────────────────────────────────────────────────────────────────────
# WS#7 — Daidzai Emergency & Disaster Suite authorization (daidzai-service command brain).
#
# Daidzai orchestrates the sovereign owners (Nhume dispatch, Ndila maps, PCT care, Khuluma
# comms, Rito after-action) and owns the emergency_request / emergency_incident aggregate.
# Authorization doctrine for emergencies:
#   * Graduated friction is MINIMAL for life-safety: a citizen/bystander may RAISE an SOS
#     (sos.create) — never blocked, payment never gates it.
#   * Operational command (dispatch, mission tracking, disaster command, resource execution)
#     is staff/responder/command-role gated; citizens may only see their OWN request status.
#   * SENSITIVE incident types (violence/GBV/sexual-assault/mental-health/child-protection)
#     are masked from actors without a protected-access role, even staff.
#   * Break-glass widens emergency clinical/record access but is explicit, justified, audited.
#
# Input contract (subset):
#   input.action                  e.g. "daidzai.sos.create", "daidzai.dispatch.request"
#   input.identity.health_id      person anchor (required for any access)
#   input.identity.identity_type  "citizen" | "provider" | "staff" | "system"
#   input.role_template           e.g. "dispatcher", "responder", "response_commander"
#   input.assurance_loa           integer LOA
#   input.actor_facility_id       facility/EOC the actor is operating in
#   input.is_subject_self         truthy when the actor is the subject/requester of the record
#   input.incident_sensitive      truthy when the incident is a protected sensitive type
#   input.break_glass             truthy when an explicit, justified break-glass is in effect
# ─────────────────────────────────────────────────────────────────────────────

default allow := false

has_anchor if {
	input.identity.health_id != ""
	input.identity.health_id != null
}

is_citizen if input.identity.identity_type == "citizen"

# Operational roles permitted to run command actions.
dispatcher_role if input.role_template in {"dispatcher", "response_coordinator", "response_commander", "platform_admin"}

responder_role if {
	input.role_template in {
		"responder", "paramedic", "ambulance_crew", "field_clinician",
		"dispatcher", "response_coordinator", "response_commander", "consultant", "platform_admin",
	}
}

command_role if input.role_template in {"response_commander", "response_coordinator", "platform_admin"}

# Roles permitted to view a sensitive (protected) incident's identifying detail.
protected_access_role if {
	input.role_template in {
		"response_commander", "safeguarding_officer", "mental_health_clinician",
		"protection_officer", "platform_admin",
	}
}

# ── Action classes ──────────────────────────────────────────────────────────

# Life-safety intake — open to anyone with an anchor (citizen/bystander/provider/facility).
sos_actions := {"daidzai.sos.create", "daidzai.request.create"}

# Citizen may track their OWN request/incident status.
self_view_actions := {"daidzai.request.view", "daidzai.incident.track"}

# Triage / classification (staff).
triage_actions := {"daidzai.request.triage", "daidzai.incident.escalate"}

# Operational command — dispatch + mission tracking + resource need.
dispatch_actions := {
	"daidzai.dispatch.request",
	"daidzai.mission.record",
	"daidzai.resource.request",
	"daidzai.handoff.record",
}

# Disaster / MCI command — declare/close/site-management.
command_actions := {
	"daidzai.disaster.declare",
	"daidzai.disaster.close",
	"daidzai.disaster.site.manage",
}

# Reading the operational incident queue / command board (staff only).
ops_read_actions := {"daidzai.incident.list", "daidzai.disaster.list", "daidzai.mission.view"}

# ── DENY rules ──────────────────────────────────────────────────────────────

# Citizens may NOT see the operational queue / command board / others' missions.
deny if {
	input.action in ops_read_actions
	is_citizen
}

# Citizens may NOT run triage / dispatch / command actions.
deny if {
	input.action in (triage_actions | dispatch_actions | command_actions)
	is_citizen
}

# Sensitive incident identifying detail is masked from actors without a protected-access role,
# even operational staff — unless an explicit break-glass is in effect.
deny if {
	input.action in self_view_actions
	input.incident_sensitive
	not is_subject_self
	not protected_access_role
	not input.break_glass
}

# Command actions require a command role.
deny if {
	input.action in command_actions
	not command_role
}

# Dispatch/mission/resource actions require an operational LOA>=2 unless break-glass (emergency).
deny if {
	input.action in (dispatch_actions | command_actions)
	input.assurance_loa < 2
	not input.break_glass
}

is_subject_self if input.is_subject_self == true

# ── ALLOW rules ──────────────────────────────────────────────────────────────

# 1. Life-safety SOS intake — never blocked (any anchored actor; citizen included).
allow if {
	has_anchor
	input.action in sos_actions
}

# 2. Subject self-view of own request/incident status (citizen-friendly).
allow if {
	has_anchor
	input.action in self_view_actions
	is_subject_self
	not denied
}

# 2b. Staff (non-citizen) may view request/incident status within their remit.
allow if {
	has_anchor
	not is_citizen
	input.action in self_view_actions
	not denied
}

# 3. Staff triage / escalation.
allow if {
	has_anchor
	not is_citizen
	input.action in triage_actions
	not denied
}

# 4. Operational read (queue / command board) — staff/responder.
allow if {
	has_anchor
	not is_citizen
	responder_role
	input.action in ops_read_actions
	not denied
}

# 5. Dispatch / mission / resource — dispatcher or responder, LOA>=2 (or break-glass).
allow if {
	has_anchor
	not is_citizen
	responder_role
	input.action in dispatch_actions
	input.assurance_loa >= 2
	not denied
}

allow if {
	has_anchor
	not is_citizen
	responder_role
	input.action in dispatch_actions
	input.break_glass
	not denied
}

# 6. Disaster / MCI command — command role, LOA>=2.
allow if {
	has_anchor
	not is_citizen
	command_role
	input.action in command_actions
	input.assurance_loa >= 2
	not denied
}

# 7. Protected-access role (or break-glass) may view sensitive incident detail.
allow if {
	has_anchor
	not is_citizen
	input.action in self_view_actions
	input.incident_sensitive
	protected_access_role
	not denied
}

denied if deny
