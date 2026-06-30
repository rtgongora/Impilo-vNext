package impilo.inpatient

import future.keywords

# ─────────────────────────────────────────────────────────────────────────────
# WS#6 — Full Inpatient Suite authorization (inpatient-service + PCT handshake).
#
# Owns inpatient ward/census + clinical-workflow authorization. Boundary doctrine:
#   * Stock functions belong to Dura/inventory — NEVER served as inpatient actions.
#   * Orders belong to OROS; safety cases to Rito; record to Butano — referenced, not owned.
#   * Provider clinical execution requires an active facility + (for clinical writes) ward context
#     and LOA>=2; break-glass widens access but is explicit, justified, and audited.
#
# Input contract (subset):
#   input.action                  e.g. "inpatient.bed.assign"
#   input.identity.health_id      person anchor (required for any access)
#   input.identity.identity_type  "citizen" | "provider" | "staff" | "system"
#   input.role_template           e.g. "ward_nurse", "ward_doctor", "ward_manager"
#   input.assurance_loa           integer LOA
#   input.actor_facility_id       facility the actor is operating in
#   input.target_facility_id      facility of the target patient/bed
#   input.ward_context_active     truthy when actor has an active ward/workspace context
#   input.care_context_active     truthy when actor holds an active care context for the subject
#   input.break_glass             truthy when an explicit, justified break-glass is in effect
# ─────────────────────────────────────────────────────────────────────────────

default allow := false

has_anchor if {
	input.identity.health_id != ""
	input.identity.health_id != null
}

is_citizen if input.identity.identity_type == "citizen"

# Roles permitted to operate in the ward.
clinical_role if {
	input.role_template in {
		"ward_nurse",
		"ward_doctor",
		"ward_manager",
		"charge_nurse",
		"consultant",
		"intern",
		"clinician",
		"platform_admin",
	}
}

# Roles permitted to APPROVE an admission / FINALISE a discharge (senior).
senior_role if {
	input.role_template in {"ward_doctor", "ward_manager", "consultant", "clinician", "platform_admin"}
}

# Action classes ----------------------------------------------------------------

read_actions := {
	"inpatient.bedboard.view",
	"inpatient.ward.view",
	"inpatient.chart.view",
	"inpatient.admissions.view",
	"inpatient.escalations.view",
	"inpatient.discharge.view",
}

# Census / placement
placement_actions := {
	"inpatient.admission.request",
	"inpatient.bed.assign",
	"inpatient.transfer.request",
}

# Clinical writes (bedside)
clinical_write_actions := {
	"inpatient.observation.record",
	"inpatient.ews.record",
	"inpatient.medication.administer",
	"inpatient.note.write",
	"inpatient.careplan.write",
	"inpatient.escalation.respond",
}

# Senior-gated decisions
senior_actions := {
	"inpatient.admission.approve",
	"inpatient.discharge.finalise",
}

# Stock functions that must NOT be served by the inpatient domain (Dura/inventory own them).
stock_actions := {
	"inventory.stock.adjust",
	"inventory.stock.issue",
	"inpatient.stock.adjust",
}

# ── DENY rules ──────────────────────────────────────────────────────────────

# Dura/stock functions are never inpatient functions.
deny if input.action in stock_actions

# Citizens never operate the ward (clinical-staff domain). Patient self-view is a separate citizen surface.
deny if {
	input.action in (read_actions | placement_actions | clinical_write_actions | senior_actions)
	is_citizen
}

# Clinical writes / placement require LOA>=2 unless break-glass.
deny if {
	input.action in (placement_actions | clinical_write_actions | senior_actions)
	input.assurance_loa < 2
	not input.break_glass
}

# Clinical writes require an active facility context (no context-free bedside writes).
deny if {
	input.action in clinical_write_actions
	not input.actor_facility_id
	not input.break_glass
}

deny if {
	input.action in clinical_write_actions
	input.actor_facility_id == ""
	not input.break_glass
}

# Clinical writes require an active care context for the subject (10-dimension doctrine), unless break-glass.
deny if {
	input.action in clinical_write_actions
	not input.care_context_active
	not input.break_glass
}

# Cross-facility placement/clinical access requires break-glass (an out-of-facility ward action).
deny if {
	input.action in (placement_actions | clinical_write_actions)
	input.target_facility_id != ""
	input.actor_facility_id != ""
	input.target_facility_id != input.actor_facility_id
	not input.break_glass
}

# Senior decisions need a senior role.
deny if {
	input.action in senior_actions
	not senior_role
}

# ── ALLOW rules ────────────────────────────────────────────────────────────────

allow if {
	has_anchor
	not is_citizen
	clinical_role
	input.action in read_actions
	not denied
}

allow if {
	has_anchor
	not is_citizen
	clinical_role
	input.action in placement_actions
	input.assurance_loa >= 2
	not denied
}

allow if {
	has_anchor
	not is_citizen
	clinical_role
	input.action in clinical_write_actions
	input.assurance_loa >= 2
	input.actor_facility_id != ""
	input.care_context_active
	not denied
}

allow if {
	has_anchor
	not is_citizen
	senior_role
	input.action in senior_actions
	input.assurance_loa >= 2
	not denied
}

# Break-glass: a clinical role may perform a clinical write under explicit break-glass even without a
# pre-existing care context (emergency care is never blocked; the override is audited upstream).
allow if {
	has_anchor
	not is_citizen
	clinical_role
	input.action in clinical_write_actions
	input.break_glass
	not stock_or_senior
}

stock_or_senior if input.action in stock_actions
stock_or_senior if input.action in senior_actions

denied if deny
