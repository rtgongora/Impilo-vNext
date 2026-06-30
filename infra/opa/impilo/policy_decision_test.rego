package impilo.policy_test

import rego.v1

import data.impilo.policy

# A V018-style rule set: a clinical-write ALLOW (path-pinned, facility-scoped, min_loa) + a DENY.
rules := [
	{
		"tenant_id": "t1",
		"name": "allow-clinical-write",
		"effect": "ALLOW",
		"priority": 100,
		"actor_type": "PROVIDER",
		"role": "CLINICIAN",
		"action": "POST",
		"purpose": "TREATMENT",
		"resource_type": "reports",
		"facility_scope": true,
		"conditions": {"path_contains": "patient-safety/reports", "min_loa": 2},
	},
	{
		"tenant_id": "t1",
		"name": "deny-suspended",
		"effect": "DENY",
		"priority": 10,
		"actor_type": "PROVIDER",
		"role": "SUSPENDED",
		"resource_type": "reports",
	},
]

# A clean, fully-qualifying clinical request.
good_input := {
	"tenant_id": "t1",
	"actor_type": "PROVIDER",
	"roles": ["CLINICIAN"],
	"action": "POST:/internal/v1/patient-safety/reports",
	"purpose": "TREATMENT",
	"resource_type": "reports",
	"path": "/internal/v1/patient-safety/reports/123",
	"loa": 2,
	"assurance_level": "LOA2",
	"facility_id": "fac-1",
}

decide(inp) := d if {
	d := policy.decision with data.policy.rules as rules with input as inp
}

# ── allow path ──────────────────────────────────────────────────────────────

test_clinical_write_allowed if {
	d := decide(good_input)
	d.allow == true
	d.reason == "ALLOW"
}

test_action_verb_extracted_before_colon if {
	# action "POST:/..." -> verb POST matches the rule's action POST
	decide(good_input).allow == true
}

# ── allow denied by missing facility scope / failed condition ───────────────

test_facility_scope_requires_facility_id if {
	d := decide(object.remove(good_input, ["facility_id"]))
	d.allow == false
	d.reason == "NO_ALLOW_RULE"
}

test_min_loa_not_met_denies if {
	d := decide(object.union(good_input, {"loa": 1, "assurance_level": "LOA1"}))
	d.allow == false
	d.reason == "NO_ALLOW_RULE"
}

test_wrong_path_denies if {
	d := decide(object.union(good_input, {"path": "/internal/v1/oros/results/9"}))
	d.allow == false
	d.reason == "NO_ALLOW_RULE"
}

test_citizen_actor_has_no_matching_allow if {
	d := decide(object.union(good_input, {"actor_type": "CITIZEN", "roles": ["SELF"]}))
	d.allow == false
	d.reason == "NO_ALLOW_RULE"
}

# ── deny wins over allow (effect DESC) ──────────────────────────────────────

test_matching_deny_wins_over_allow if {
	# actor has both CLINICIAN (allow) and SUSPENDED (deny) -> deny wins regardless of priority
	d := decide(object.union(good_input, {"roles": ["CLINICIAN", "SUSPENDED"]}))
	d.allow == false
	d.reason == "POLICY_DENY"
}

# ── no rules for the resource ───────────────────────────────────────────────

test_no_rules_non_system_denies if {
	d := decide(object.union(good_input, {"resource_type": "unknown-thing"}))
	d.allow == false
	d.reason == "NO_MATCHING_RULES"
}

test_no_rules_system_bypasses if {
	d := decide(object.union(good_input, {"resource_type": "unknown-thing", "purpose": "SYSTEM"}))
	d.allow == true
	d.reason == "SYSTEM_BYPASS_NO_RULES"
}

# ── resource prefix match (rule resource_type is a prefix of the request's) ──

test_resource_prefix_match if {
	prefix_rule := [object.union(rules[0], {"resource_type": "report"})] # "report" is a prefix of "reports"
	d := policy.decision with data.policy.rules as prefix_rule with input as good_input
	d.allow == true
}

# ── tenant isolation: a rule for another tenant is not considered ────────────

test_other_tenant_rule_not_applied if {
	d := decide(object.union(good_input, {"tenant_id": "t2"}))
	d.allow == false
	d.reason == "NO_MATCHING_RULES"
}
