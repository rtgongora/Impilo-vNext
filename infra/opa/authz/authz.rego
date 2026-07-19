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

# ── GAP-6 fine-grained rules (deny-safe strangler; ENFORCE cut-over is CZO-gated) ────────────
#
# These extend the OPA input contract with explicit intent fields that tshepo-authz assembles.
# Every rule is DENY-SAFE: it fires only when its intent field is present, so until tshepo-authz
# populates the field the rule cannot cause a false denial (SHADOW). The WS-OPA/CZO owner decides
# the SHADOW→ENFORCE cut-over. Input contract additions (all optional; absent ⇒ rule inert):
#   input.action           string   fine-grained action verb (e.g. "PROVIDER_CLAIM_APPROVE")
#   input.regulated_action boolean  true when the action requires a regulated professional capacity
#   input.access_mode      string   actor's active zone: "WORK" | "PROFESSIONAL" | "LIFE"
#   input.assignment_active boolean true when the actor has an active work assignment/context

# PROVIDER-SELF-CLAIM: a person must not approve/verify their OWN provider credential claim.
deny_reasons contains "PROVIDER_SELF_CLAIM" if {
	input.action == "PROVIDER_CLAIM_APPROVE"
	is_string(input.subject_id)
	input.subject_id != ""
	input.subject_id == input.actor_id
}

# PROVIDER-ID-DENY: a regulated professional action must carry a Provider ID (regulated capacity).
deny_reasons contains "PROVIDER_ID_REQUIRED" if {
	input.regulated_action == true
	not provider_id_present
}

provider_id_present if {
	is_string(input.provider_id)
	input.provider_id != ""
}

# WORK-REQUIRES-ASSIGNMENT (GAP-7): acting in the WORK zone requires an active work assignment.
# `not assignment_active` fires when the flag is absent OR false (an absent field is undefined, so
# a bare `!= true` would not hold — the helper makes the "missing assignment" case explicit).
deny_reasons contains "WORK_REQUIRES_ASSIGNMENT" if {
	input.access_mode == "WORK"
	not assignment_active
}

assignment_active if input.assignment_active == true

# ── Provider+Place identity program W1 (deny-safe strangler; SHADOW first) ──────────────
#
# Input contract additions (optional; absent ⇒ rule inert):
#   input.identifier_kind  string  the credential/identifier kind presented for the action
#                                  (e.g. "EMAIL", "PHONE", "PASSKEY", "PROVIDER_ID", "COUNCIL_REG")

# LOGIN-PROVIDERID-DENY: a Provider ID / council registration number may IDENTIFY an account
# (prefill / badge-style selection) but must never AUTHENTICATE — professional identifiers are
# often public knowledge and never a possession/knowledge factor (Journey Doctrine PJ1/PJ5).
deny_reasons contains "LOGIN_PROVIDERID_DENY" if {
	input.action == "AUTHENTICATE"
	upper(object.get(input, "identifier_kind", "")) in {"PROVIDER_ID", "COUNCIL_REG", "COUNCIL_NUMBER", "EC_NUMBER"}
}

# LOGIN-PERSON-FIRST: authentication is person-anchored — an account authenticates with a
# person credential (email/phone/username/passkey/device biometric), never a professional or
# place identifier. Fires only when both the action and the kind are supplied (deny-safe).
person_login_kinds := {"EMAIL", "PHONE", "USERNAME", "PASSKEY", "DEVICE_BIOMETRIC", "HEALTH_ID"}

deny_reasons contains "LOGIN_PERSON_FIRST" if {
	input.action == "AUTHENTICATE"
	kind := upper(object.get(input, "identifier_kind", ""))
	kind != ""
	not kind in person_login_kinds
}

# WORK-TOKEN-CONTEXT-MATCH (W3, D-P3): a WORK-zone request must carry a work-context whose
# facility (and workspace, when the request scopes one) matches the request scope — forcing a
# revoke-and-reissue on every facility/workspace switch instead of a silently drifting session.
# Deny-safe: fires only when tshepo-authz supplies BOTH the token context and the request scope.
#   input.work_context   object  claims from the presented WORK_CONTEXT token
#                                ({facility_id, workspace_id, ...})
#   input.scope.facility / input.scope.workspace  the request's evaluated scope
deny_reasons contains "WORK_TOKEN_CONTEXT_MISMATCH" if {
	input.access_mode == "WORK"
	is_object(input.work_context)
	token_facility := object.get(input.work_context, "facility_id", "")
	token_facility != ""
	request_facility := object.get(object.get(input, "scope", {}), "facility", "")
	request_facility != ""
	lower(token_facility) != lower(request_facility)
}

deny_reasons contains "WORK_TOKEN_CONTEXT_MISMATCH" if {
	input.access_mode == "WORK"
	is_object(input.work_context)
	token_workspace := object.get(input.work_context, "workspace_id", "")
	token_workspace != ""
	request_workspace := object.get(object.get(input, "scope", {}), "workspace", "")
	request_workspace != ""
	lower(token_workspace) != lower(request_workspace)
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
