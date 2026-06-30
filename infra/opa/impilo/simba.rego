package impilo.simba

import future.keywords

# Simba — wellness / prevention access policy (MY LIFE wellness companion).
#
# Simba is the wellness layer: profiles, goals, habits, plans/journeys, clubs, coaching, and
# wellness-to-care routing. It never owns clinical records (Butano/PCT), stock (Dura), learning
# content (Fundo), messaging (Khuluma), marketplace (Msika) or payments (MusheX). This policy
# gates Simba's own actions: self-service wellness, dependant wellness (real Mvumo delegation),
# coach access (active assignment + provider scope), programme/admin reporting (aggregate vs
# user-level), wellness-to-care routing, low-trust restriction, and the explicit non-ownership of
# any stock/inventory action.
#
# Input contract (resolved by the BFF / Tshepo gate):
#   input.action                 one of simba_actions below
#   input.identity.health_id     caller Health ID (person anchor)
#   input.identity.identity_type "citizen" | "provider" | "operator" | ...
#   input.assurance_loa          integer 1..4 (LoA)
#   input.subject_ref            person the action targets (self or dependant)
#   input.delegation.active      bool — caller has an active Mvumo delegation for subject
#   input.coach.assigned         bool — caller is the assigned coach for the subject
#   input.coach.in_scope         bool — action is within the coach's cadre/scope of practice
#   input.programme.role         bool — caller holds a lawful programme/admin role
#   input.report.aggregate_only  bool — request is for aggregate (non-identifying) data

default allow := false

simba_actions := {
	"simba.self.view",
	"simba.profile.edit",
	"simba.goal.create",
	"simba.goal.update",
	"simba.habit.checkin",
	"simba.plan.enroll",
	"simba.plan.progress",
	"simba.club.view",
	"simba.club.join",
	"simba.club.leave",
	"simba.content.recommend.view",
	"simba.reminder.register",
	"simba.dependant.view",
	"simba.dependant.act",
	"simba.coach.view",
	"simba.coach.update",
	"simba.programme.manage",
	"simba.report.view",
	"simba.care.route",
	"simba.marketplace.handoff",
}

# Actions Simba is NEVER allowed to expose — stock/inventory belongs to Dura. Any such action
# is denied outright (defence in depth: these should never even be routed here).
forbidden_actions := {
	"simba.stock.view",
	"simba.stock.adjust",
	"simba.inventory.manage",
	"simba.commodity.issue",
}

is_self if {
	input.subject_ref == input.identity.health_id
}

is_self if {
	input.subject_ref == sprintf("Patient/%s", [input.identity.health_id])
}

has_person_anchor if {
	input.identity.health_id != ""
}

effective_loa := loa if {
	is_number(input.assurance_loa)
	loa := input.assurance_loa
}

effective_loa := 1 if {
	not is_number(input.assurance_loa)
}

# ── Self-service wellness: any authenticated person on their own wellness data ────────
self_service_actions := {
	"simba.self.view",
	"simba.profile.edit",
	"simba.goal.create",
	"simba.goal.update",
	"simba.habit.checkin",
	"simba.plan.enroll",
	"simba.plan.progress",
	"simba.club.view",
	"simba.club.join",
	"simba.club.leave",
	"simba.content.recommend.view",
	"simba.care.route",
	"simba.marketplace.handoff",
}

allow if {
	input.action in self_service_actions
	has_person_anchor
	is_self
}

# ── Reminders: self-service, but only when a comms preference/consent permits delivery ─
allow if {
	input.action == "simba.reminder.register"
	has_person_anchor
	is_self
	input.comms.opted_in == true
}

# ── Dependant wellness: requires a real, active Mvumo delegation ──────────────────────
allow if {
	input.action in {"simba.dependant.view", "simba.self.view"}
	has_person_anchor
	not is_self
	input.delegation.active == true
}

allow if {
	input.action in {"simba.dependant.act", "simba.goal.create", "simba.habit.checkin", "simba.plan.enroll"}
	has_person_anchor
	not is_self
	input.delegation.active == true
}

# ── Coaching: a coach may view/update a participant only with an active assignment, and
#    an update must be within the coach's scope of practice. ────────────────────────────
allow if {
	input.action == "simba.coach.view"
	has_person_anchor
	input.coach.assigned == true
}

allow if {
	input.action == "simba.coach.update"
	has_person_anchor
	input.coach.assigned == true
	input.coach.in_scope == true
}

# ── Programme / admin: campaign management and user-level reporting need a lawful role;
#    aggregate (non-identifying) reporting is allowed to programme roles only. ──────────
allow if {
	input.action == "simba.programme.manage"
	input.programme.role == true
}

allow if {
	input.action == "simba.report.view"
	input.report.aggregate_only == true
	input.programme.role == true
}

# User-level (identifying) reporting requires both a programme role AND elevated trust.
allow if {
	input.action == "simba.report.view"
	input.report.aggregate_only == false
	input.programme.role == true
	effective_loa >= 3
}

# ── Machine-readable decision (mirrors Tshepo Decision shape) ─────────────────────────

deny_reasons contains "NO_PERSON_ANCHOR" if {
	input.action in simba_actions
	not has_person_anchor
}

deny_reasons contains "FORBIDDEN_NON_WELLNESS_ACTION" if {
	input.action in forbidden_actions
}

deny_reasons contains "NO_DELEGATION" if {
	input.action in {"simba.dependant.view", "simba.dependant.act"}
	not is_self
	not input.delegation.active == true
}

deny_reasons contains "COACH_NOT_ASSIGNED" if {
	input.action in {"simba.coach.view", "simba.coach.update"}
	not input.coach.assigned == true
}

deny_reasons contains "COACH_OUT_OF_SCOPE" if {
	input.action == "simba.coach.update"
	input.coach.assigned == true
	not input.coach.in_scope == true
}

deny_reasons contains "NO_PROGRAMME_ROLE" if {
	input.action in {"simba.programme.manage", "simba.report.view"}
	not input.programme.role == true
}

deny_reasons contains "USER_LEVEL_TRUST_TOO_LOW" if {
	input.action == "simba.report.view"
	input.report.aggregate_only == false
	input.programme.role == true
	effective_loa < 3
}

deny_reasons contains "COMMS_NOT_PERMITTED" if {
	input.action == "simba.reminder.register"
	not input.comms.opted_in == true
}

decision := {
	"allow": allow,
	"deny_reasons": sort([r | some r in deny_reasons]),
}
