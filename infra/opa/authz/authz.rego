package impilo.authz

# Strangler seam for promoting policy decisions into rego (Phase 3, Option B). tshepo-authz
# assembles the `input` and (in SHADOW) compares OPA's verdict to the Java PolicyEngine, then
# (later) cuts over rule-classes to ENFORCE. This module covers the SELF-CONTAINED gate
# sub-decision — purpose validity, min_loa, and account-assurance — which the `input` fully
# describes. The DB-rule RBAC/ABAC requires `policy_rules` delivered as bundle `data` and is a
# later increment; it is intentionally NOT decided here.
#
# Authored against OPA 0.68.x (matches docker-compose). `opa test infra/opa/authz/` must be green.

import rego.v1

# Purposes the platform recognises (mirrors PurposeOfUse). Unknown purpose => deny.
valid_purpose := {
	"TREATMENT", "OPERATIONS", "RESEARCH", "PUBLIC_HEALTH",
	"EMERGENCY", "BREAK_GLASS", "SYSTEM", "PAYMENT",
}

# ── Deny reasons (a set; empty => the gate allows) ──────────────────────────

deny_reasons contains "INVALID_PURPOSE" if {
	not input.purpose in valid_purpose
}

# Effective LoA must meet the rule's minimum. `effective_loa` is max(acr, propagated assurance),
# mirroring PolicyEngine.effectiveLoa (G-CZO-01).
deny_reasons contains "MIN_LOA" if {
	is_number(input.min_loa)
	effective_loa < input.min_loa
}

# A "verified account" is identity-assurance LoA3+ (in-person), mirroring accountVerified.
deny_reasons contains "ACCOUNT_NOT_VERIFIED" if {
	input.account_assurance_required == true
	assurance_loa < 3
}

# SELF-TREATMENT-BLOCK (GAP-6): a person acting in a regulated provider capacity must not perform
# clinical TREATMENT on their own person record — an integrity / conflict-of-interest control.
# Fires only when the actor is a provider (provider_id present) AND is the subject of the action.
# Self-access to one's own record for non-treatment purposes (e.g. viewing) is unaffected.
# Deny-safe strangler behaviour: when tshepo-authz has not yet populated subject_id/provider_id the
# rule simply does not fire, so it cannot cause false denials before cut-over to ENFORCE.
deny_reasons contains "SELF_TREATMENT" if {
	input.purpose == "TREATMENT"
	is_string(input.provider_id)
	input.provider_id != ""
	is_string(input.subject_id)
	input.subject_id != ""
	input.subject_id == input.actor_id
}

# ── Derived values ──────────────────────────────────────────────────────────

acr_loa := x if {
	is_number(input.loa)
	x := input.loa
} else := 0

assurance_loa := x if {
	is_number(input.assurance_loa)
	x := input.assurance_loa
} else := 0

effective_loa := max([acr_loa, assurance_loa])

# ── Decision ────────────────────────────────────────────────────────────────

default allow := false

allow if count(deny_reasons) == 0

decision := {
	"allow": allow,
	"deny_reasons": sort([r | some r in deny_reasons]),
}
