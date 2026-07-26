package impilo.confidentiality

import future.keywords

# Specially-protected confidentiality control — shadow mirror of PolicyEngine Step 4.7.
#
# The Java PolicyEngine (tshepo-authz) remains authoritative; this module exists so the rule can be
# read, tested and diffed as policy rather than only as code, and so the OPA strangler has a target
# when the DB-rule PDP moves across.
#
# THE RULE IT MIRRORS. Within the confidential clinical lane (restricted adolescent and safeguarding
# data, sexual and reproductive health, HIV, mental health):
#   1. A delegated act — a guardian or caregiver acting on another person's behalf — is refused
#      absolutely. Separating guardian access from the confidential adolescent record is the point
#      of the control, and no policy grant may widen it, or the hole reopens through governance.
#   2. The subject themselves is entitled to their own protected record.
#   3. Anyone else needs an explicit governed entitlement (a policy rule whose visibility overlay
#      grants SPECIALLY_PROTECTED_CLINICAL). A clinical role alone is deliberately not enough.
#   4. Break-glass reaches protected content, but only when actually verified — an active
#      break-glass request AND a completed step-up. A bare purpose header is an unverified claim.
#
# Outside the confidential lane this module has nothing to say and allows: a confidentiality control
# that narrows ordinary care gets routed around, which protects nobody.
#
# Input contract:
#   input.resource_sensitivity        DataSensitivityClass name of the target resource
#   input.delegated                   bool — acting on behalf of a different subject (X-Subject-ID
#                                     != actor with an ACTIVE Mvumo delegation)
#   input.relationship_type           GUARDIAN | CAREGIVER | CHW | FACILITY_STAFF | ... (audit only)
#   input.actor_id                    the requester
#   input.subject_id                  whose record is being opened
#   input.rule_grants_protected_tier  bool — matched ALLOW rule carries the visibility overlay
#   input.purpose                     TREATMENT | BREAK_GLASS | ...
#   input.break_glass_active          bool — an active break_glass_request exists
#   input.step_up_completed           bool — step-up challenge completed in window

default allow := false

targets_protected if input.resource_sensitivity == "SPECIALLY_PROTECTED"

delegated if input.delegated == true

# ── Entitlement sources ───────────────────────────────────────────────────────
entitled if input.subject_id == input.actor_id

entitled if input.rule_grants_protected_tier == true

entitled if verified_break_glass

verified_break_glass if {
	input.purpose == "BREAK_GLASS"
	input.break_glass_active == true
	input.step_up_completed == true
}

# ── Decision ──────────────────────────────────────────────────────────────────

# Not the confidential lane — nothing to decide here.
allow if not targets_protected

allow if {
	targets_protected
	not delegated
	entitled
}

# ── Refusal reasons (mirror the Java deny codes) ──────────────────────────────

refuse_reason := "PROTECTED_RECORD_DELEGATE_DENIED" if {
	targets_protected
	delegated
}

refuse_reason := "PROTECTED_RECORD_NOT_ENTITLED" if {
	targets_protected
	not delegated
	not entitled
}

# Every decision on this lane is reviewable — grants included, not only refusals.
audit_event := "CONFIDENTIAL_ACCESS_GRANTED" if {
	targets_protected
	allow
}

audit_event := "CONFIDENTIAL_ACCESS_REFUSED" if {
	targets_protected
	not allow
}
