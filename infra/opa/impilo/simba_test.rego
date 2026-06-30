package impilo.simba

import future.keywords

citizen(action, subject) := {
	"action": action,
	"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
	"subject_ref": subject,
	"assurance_loa": 2,
}

# ── Self-service ──────────────────────────────────────────────────────────────────────
test_self_goal_create_allowed if {
	allow with input as citizen("simba.goal.create", "CPID-1")
}

test_self_habit_checkin_allowed if {
	allow with input as citizen("simba.habit.checkin", "CPID-1")
}

test_self_plan_enroll_allowed if {
	allow with input as citizen("simba.plan.enroll", "CPID-1")
}

test_self_care_route_allowed if {
	allow with input as citizen("simba.care.route", "CPID-1")
}

test_no_person_anchor_denied if {
	not allow with input as {
		"action": "simba.goal.create",
		"identity": {"health_id": "", "identity_type": "citizen"},
		"subject_ref": "",
	}
}

# ── Reminders gated on comms opt-in ───────────────────────────────────────────────────
test_reminder_allowed_when_opted_in if {
	allow with input as object.union(citizen("simba.reminder.register", "CPID-1"), {"comms": {"opted_in": true}})
}

test_reminder_denied_when_not_opted_in if {
	not allow with input as object.union(citizen("simba.reminder.register", "CPID-1"), {"comms": {"opted_in": false}})
}

# ── Dependant wellness needs a real delegation ────────────────────────────────────────
test_dependant_view_denied_without_delegation if {
	not allow with input as citizen("simba.dependant.view", "CPID-CHILD")
}

test_dependant_view_allowed_with_delegation if {
	allow with input as object.union(citizen("simba.dependant.view", "CPID-CHILD"), {"delegation": {"active": true}})
}

test_dependant_act_allowed_with_delegation if {
	allow with input as object.union(citizen("simba.dependant.act", "CPID-CHILD"), {"delegation": {"active": true}})
}

# ── Coaching scope ────────────────────────────────────────────────────────────────────
coach(action) := {
	"action": action,
	"identity": {"health_id": "PROV-1", "identity_type": "provider"},
	"subject_ref": "CPID-PARTICIPANT",
	"assurance_loa": 3,
}

test_coach_view_denied_without_assignment if {
	not allow with input as object.union(coach("simba.coach.view"), {"coach": {"assigned": false}})
}

test_coach_view_allowed_with_assignment if {
	allow with input as object.union(coach("simba.coach.view"), {"coach": {"assigned": true}})
}

test_coach_update_denied_out_of_scope if {
	not allow with input as object.union(coach("simba.coach.update"), {"coach": {"assigned": true, "in_scope": false}})
}

test_coach_update_allowed_in_scope if {
	allow with input as object.union(coach("simba.coach.update"), {"coach": {"assigned": true, "in_scope": true}})
}

# ── Programme / reporting ─────────────────────────────────────────────────────────────
programme(action, aggregate, loa) := {
	"action": action,
	"identity": {"health_id": "OP-1", "identity_type": "operator"},
	"subject_ref": "OP-1",
	"assurance_loa": loa,
	"programme": {"role": true},
	"report": {"aggregate_only": aggregate},
}

test_programme_manage_allowed if {
	allow with input as programme("simba.programme.manage", true, 3)
}

test_aggregate_report_allowed if {
	allow with input as programme("simba.report.view", true, 2)
}

test_user_level_report_denied_low_trust if {
	not allow with input as programme("simba.report.view", false, 2)
}

test_user_level_report_allowed_high_trust if {
	allow with input as programme("simba.report.view", false, 3)
}

test_report_denied_without_programme_role if {
	not allow with input as {
		"action": "simba.report.view",
		"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
		"subject_ref": "CPID-1",
		"assurance_loa": 4,
		"programme": {"role": false},
		"report": {"aggregate_only": true},
	}
}

# ── Simba is NOT inventory — stock actions are forbidden ──────────────────────────────
test_stock_action_forbidden if {
	not allow with input as citizen("simba.stock.adjust", "CPID-1")
}

test_inventory_action_forbidden if {
	not allow with input as citizen("simba.inventory.manage", "CPID-1")
}

test_decision_surfaces_forbidden_reason if {
	d := decision with input as citizen("simba.stock.adjust", "CPID-1")
	d.allow == false
	"FORBIDDEN_NON_WELLNESS_ACTION" in d.deny_reasons
}

test_decision_surfaces_no_delegation if {
	d := decision with input as citizen("simba.dependant.view", "CPID-CHILD")
	"NO_DELEGATION" in d.deny_reasons
}
