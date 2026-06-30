# METADATA
# title: Impilo DB-rule PolicyEngine decision mirror (Phase 7 / A4)
# description: |
#   Exact rego mirror of PolicyEngine Step-4 (tshepo-authz). Consumes the policy_rule bundle
#   (data.policy.rules — produced by the A3 PolicyRuleBundleBuilder) + the canonical input, and
#   reproduces the Java verdict:
#     - resource rules = active rules for the tenant whose resource_type is blank, equals, or is a
#       PREFIX of the request resource_type (mirrors PolicyCacheService.getActiveRulesForResource).
#     - order is `effect DESC, priority ASC`, so ALL deny rules precede all allow rules: any matching
#       DENY wins; else the first qualifying ALLOW (matches + facility/workspace scope + conditions).
#     - empty resource rule-set: SYSTEM purpose bypasses (allow), else NO_MATCHING_RULES.
#     - rules present but no qualifying allow: SYSTEM bypasses, else NO_ALLOW_RULE.
#   Java is authoritative; the A5 harness asserts verdict parity.
package impilo.policy

import rego.v1

import data.impilo.policy.conditions

default decision := {"allow": false, "reason": "NO_MATCHING_RULES"}

# A field counts as a wildcard when it is absent, null, or "".
blank_field(r, k) if object.get(r, k, "") == ""

blank_field(r, k) if object.get(r, k, "x") == null

# ── candidate sets ──────────────────────────────────────────────────────────

tenant_rules contains r if {
	some r in data.policy.rules
	object.get(r, "tenant_id", "") == input.tenant_id
}

resource_rules contains r if {
	some r in tenant_rules
	resource_match(r)
}

matching contains r if {
	some r in resource_rules
	matches_rule(r)
}

matching_denies contains r if {
	some r in matching
	upper(object.get(r, "effect", "")) == "DENY"
}

qualifying_allows contains r if {
	some r in matching
	allow_qualifies(r)
}

is_system if input.purpose == "SYSTEM"

# ── decision (mutually-exclusive cases mirroring Step-4) ────────────────────

decision := {"allow": true, "reason": "SYSTEM_BYPASS_NO_RULES"} if {
	count(resource_rules) == 0
	is_system
}

decision := {"allow": false, "reason": "NO_MATCHING_RULES"} if {
	count(resource_rules) == 0
	not is_system
}

decision := {"allow": false, "reason": "POLICY_DENY"} if {
	count(resource_rules) > 0
	count(matching_denies) > 0
}

decision := {"allow": true, "reason": "ALLOW"} if {
	count(resource_rules) > 0
	count(matching_denies) == 0
	count(qualifying_allows) > 0
}

decision := {"allow": true, "reason": "SYSTEM_BYPASS_NO_ALLOW"} if {
	count(resource_rules) > 0
	count(matching_denies) == 0
	count(qualifying_allows) == 0
	is_system
}

decision := {"allow": false, "reason": "NO_ALLOW_RULE"} if {
	count(resource_rules) > 0
	count(matching_denies) == 0
	count(qualifying_allows) == 0
	not is_system
}

# ── resource + rule matching (mirror matchesRule) ───────────────────────────

resource_match(r) if blank_field(r, "resource_type")

resource_match(r) if {
	rt := object.get(r, "resource_type", "")
	rt != ""
	startswith(input.resource_type, rt)
}

matches_rule(r) if {
	actor_type_ok(r)
	role_ok(r)
	action_ok(r)
	purpose_ok(r)
}

actor_type_ok(r) if blank_field(r, "actor_type")
actor_type_ok(r) if lower(object.get(r, "actor_type", "")) == lower(input.actor_type)

# role is an EXACT, case-sensitive membership of the actor's roles (mirrors List.contains).
role_ok(r) if blank_field(r, "role")
role_ok(r) if object.get(r, "role", "") in {x | some x in input.roles}

action_ok(r) if blank_field(r, "action")
action_ok(r) if object.get(r, "action", "") == "*"
action_ok(r) if lower(object.get(r, "action", "")) == lower(action_verb)

# action verb = part before ":" (rule "DELETE" matches "DELETE:/v1/...").
action_verb := v if {
	contains(input.action, ":")
	v := substring(input.action, 0, indexof(input.action, ":"))
} else := input.action

purpose_ok(r) if blank_field(r, "purpose")
purpose_ok(r) if lower(object.get(r, "purpose", "")) == lower(input.purpose)

# ── allow qualification (effect ALLOW + scope + conditions) ─────────────────

allow_qualifies(r) if {
	upper(object.get(r, "effect", "")) == "ALLOW"
	matches_rule(r)
	facility_scope_ok(r)
	workspace_scope_ok(r)
	conditions.conditions_satisfied(object.get(r, "conditions", {}))
}

facility_scope_ok(r) if not object.get(r, "facility_scope", false)
facility_scope_ok(_) if is_string(input.facility_id)

workspace_scope_ok(r) if not object.get(r, "workspace_scope", false)
workspace_scope_ok(_) if is_string(input.workspace_id)
