package impilo.budget

import future.keywords

# Intelligent Budgeting & Budget Tracking — authorization policy (COSTA-owned).
#
# Gates who may create, submit, review, approve, activate, revise, commit against,
# post actuals to, override, reconcile, threshold-manage, close and view budgets.
# Enforces segregation of duties (approver != creator), organisational scope
# (a facility actor cannot approve a national budget) and funder scoping.
#
# Input contract (resolved by the BFF / Tshepo gate):
#   input.action              one of budget_actions below
#   input.identity.health_id  caller Health ID (person anchor)
#   input.user_type           FINANCE_ADMIN | BUDGET_HOLDER | BUDGET_APPROVER |
#                             FINANCE_REVIEWER | FUNDER | EMERGENCY_COMMANDER | ...
#   input.actor_scope         FACILITY | DISTRICT | PROVINCE | NATIONAL (actor's authority)
#   input.budget_scope        FACILITY | DISTRICT | PROVINCE | NATIONAL (budget's scope_level)
#   input.is_creator          bool — caller created the budget being approved
#   input.emergency           bool — availability override is an emergency
#   input.funder_grant_match  bool — FUNDER's grant matches the budget's funding source

default allow := false

budget_actions := {
	"budget.view",
	"budget.create",
	"budget.submit",
	"budget.review",
	"budget.approve",
	"budget.activate",
	"budget.revise",
	"budget.freeze",
	"budget.close",
	"budget.commitment.record",
	"budget.commitment.liquidate",
	"budget.actual.import",
	"budget.availability.override",
	"budget.threshold.manage",
	"budget.recon.resolve",
	"budget.period.close",
}

has_person_anchor if {
	input.identity.health_id != ""
}

scope_rank := {"FACILITY": 1, "DISTRICT": 2, "PROVINCE": 3, "NATIONAL": 4}

# actor's authority must cover the budget's scope (for authoritative actions)
actor_covers_scope if {
	scope_rank[input.actor_scope] >= scope_rank[input.budget_scope]
}

actor_covers_scope if {
	not input.budget_scope
}

is_finance_admin if {
	input.user_type == "FINANCE_ADMIN"
}

# ── Authoring: budget holders and finance admins ──────────────────────────────
allow if {
	input.action in {"budget.create", "budget.submit", "budget.revise"}
	has_person_anchor
	input.user_type in {"BUDGET_HOLDER", "FINANCE_ADMIN"}
}

# ── Review ────────────────────────────────────────────────────────────────────
allow if {
	input.action == "budget.review"
	has_person_anchor
	input.user_type in {"FINANCE_REVIEWER", "FINANCE_ADMIN"}
}

# ── Approve / activate / freeze / close: approver or admin, in-scope, not creator ─
allow if {
	input.action in {"budget.approve", "budget.activate", "budget.freeze", "budget.close"}
	has_person_anchor
	input.user_type in {"BUDGET_APPROVER", "FINANCE_ADMIN"}
	actor_covers_scope
	not self_approval
}

# ── Execution: commitments (holder/admin) ─────────────────────────────────────
allow if {
	input.action in {"budget.commitment.record", "budget.commitment.liquidate"}
	has_person_anchor
	input.user_type in {"BUDGET_HOLDER", "FINANCE_ADMIN"}
}

# ── Actuals import & reconciliation: reviewer/admin ───────────────────────────
allow if {
	input.action in {"budget.actual.import", "budget.recon.resolve"}
	has_person_anchor
	input.user_type in {"FINANCE_REVIEWER", "FINANCE_ADMIN"}
}

# ── Emergency availability override: elevated role only ───────────────────────
allow if {
	input.action == "budget.availability.override"
	has_person_anchor
	input.user_type in {"EMERGENCY_COMMANDER", "FINANCE_ADMIN"}
}

# ── Thresholds & period close: finance admin, in-scope for close ──────────────
allow if {
	input.action == "budget.threshold.manage"
	has_person_anchor
	is_finance_admin
}

allow if {
	input.action == "budget.period.close"
	has_person_anchor
	is_finance_admin
	actor_covers_scope
}

# ── View: any finance role in scope; FUNDER only on matching grant ────────────
allow if {
	input.action == "budget.view"
	has_person_anchor
	input.user_type in {"FINANCE_ADMIN", "BUDGET_HOLDER", "BUDGET_APPROVER", "FINANCE_REVIEWER"}
	actor_covers_scope
}

allow if {
	input.action == "budget.view"
	has_person_anchor
	input.user_type == "FUNDER"
	input.funder_grant_match == true
}

# ── Segregation of duties ─────────────────────────────────────────────────────
self_approval if {
	input.action == "budget.approve"
	input.is_creator == true
}

# ── Machine-readable decision + reasons ───────────────────────────────────────
deny_reasons contains "NO_PERSON_ANCHOR" if {
	input.action in budget_actions
	not has_person_anchor
}

deny_reasons contains "SELF_APPROVAL" if {
	self_approval
}

deny_reasons contains "OUT_OF_SCOPE" if {
	input.action in {"budget.approve", "budget.activate", "budget.freeze", "budget.close", "budget.period.close"}
	not actor_covers_scope
}

deny_reasons contains "FUNDER_GRANT_MISMATCH" if {
	input.action == "budget.view"
	input.user_type == "FUNDER"
	not input.funder_grant_match == true
}

decision := {
	"allow": allow,
	"deny_reasons": sort([r | some r in deny_reasons]),
}
