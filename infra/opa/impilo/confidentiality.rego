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
#   3. Anyone else needs an explicit governed entitlement — a policy rule whose visibility overlay
#      names confidentialCategories, narrowed by what the ratified pack permits. Grants are
#      CATEGORY-SCOPED, not a single flag: holding safeguarding must not imply holding the
#      adolescent's mental-health notes, which an ordered visibility tier could not express. A
#      clinical role alone is deliberately not enough.
#   4. EMERGENCY and BREAK_GLASS waive the requirement entirely and are loudly audited, mirroring
#      ClinicalAccessGuard in pct-service. Over-restricting kills people too: a teenager arriving
#      unconscious whose HIV status explains the presentation must not be invisible. This is
#      detection rather than prevention, and it is only safe because it is reviewable.
#
# MODE-GATED. mode=OFF evaluates nothing; mode=SHADOW decides and audits but never refuses;
# mode=ENFORCE refuses, and additionally requires a ratified policy pack. The default is SHADOW
# because the content that decides behaviour (confidentiality ages, confidential code lists) is an
# engineering seed pending MoHCC ratification — a protection label that does not protect is worse
# than no label at all.
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
#   input.rule_categories             array — confidentialCategories the matched ALLOW rule grants
#   input.pack_categories             array — categories the RATIFIED policy pack permits (empty
#                                     while the pack is an unratified engineering seed)
#   input.record_category             the category of the content being reached, when known
#   input.purpose                     TREATMENT | EMERGENCY | BREAK_GLASS | ...
#   input.mode                        OFF | SHADOW | ENFORCE (default SHADOW)
#   input.pack_ratified               bool — policy pack is RATIFIED and effective

default allow := false

mode := m if {
	m := input.mode
	m != null
}

mode := "SHADOW" if not input.mode

evaluating if {
	mode != "OFF"
	targets_protected
}

targets_protected if input.resource_sensitivity == "SPECIALLY_PROTECTED"

delegated if input.delegated == true

# ENFORCE requires ratified content. Without it the control stays inert — but LOUDLY, so nobody
# believes a control is live when it is not.
enforcing if {
	mode == "ENFORCE"
	input.pack_ratified == true
}

enforce_unavailable if {
	mode == "ENFORCE"
	not input.pack_ratified
}

# ── The emergency waiver comes FIRST ─────────────────────────────────────────
emergency_waiver if input.purpose == "EMERGENCY"

emergency_waiver if input.purpose == "BREAK_GLASS"

# ── Category-scoped grants ───────────────────────────────────────────────────
# Grants are per-category, never a single flag: holding safeguarding must not imply holding the
# adolescent's mental-health notes. An ordered visibility tier could not express that.
granted_categories := {"*"} if {
	evaluating
	emergency_waiver
}

granted_categories := {"*"} if {
	evaluating
	not emergency_waiver
	not delegated
	input.subject_id == input.actor_id
}

granted_categories := permitted if {
	evaluating
	not emergency_waiver
	not delegated
	input.subject_id != input.actor_id
	permitted := {c | c := input.rule_categories[_]; c == input.pack_categories[_]}
}

granted_categories := set() if {
	evaluating
	not emergency_waiver
	delegated
}

covers_record if granted_categories["*"]

covers_record if granted_categories[input.record_category]

# ── Decision ──────────────────────────────────────────────────────────────────

# Not the confidential lane, or the control is off — nothing to decide here.
allow if not evaluating

# Shadow never refuses: it observes.
allow if {
	evaluating
	not enforcing
}

# The emergency waiver is checked BEFORE the delegate exclusion, matching the Java. An accompanying
# adult handing over a collapsed teenager must not be the reason the clinical picture stays hidden.
allow if {
	evaluating
	enforcing
	emergency_waiver
}

allow if {
	evaluating
	enforcing
	not emergency_waiver
	not delegated
	covers_record
}

# ── Refusal reasons (mirror the Java deny codes) ──────────────────────────────

refuse_reason := "PROTECTED_RECORD_DELEGATE_DENIED" if {
	evaluating
	not emergency_waiver
	delegated
}

refuse_reason := "PROTECTED_RECORD_NOT_ENTITLED" if {
	evaluating
	not emergency_waiver
	not delegated
	count(input.rule_categories) == 0
	not covers_record
}

refuse_reason := "PROTECTED_RECORD_PACK_INERT" if {
	evaluating
	not emergency_waiver
	not delegated
	count(input.rule_categories) > 0
	not covers_record
}

# ── Audit: every decision on this lane is reviewable, grants included ─────────
audit_event := "CONFIDENTIALITY_ENFORCE_UNAVAILABLE" if enforce_unavailable

audit_event := "CONFIDENTIAL_ACCESS_GRANTED" if {
	evaluating
	not enforce_unavailable
	covers_record
}

audit_event := "CONFIDENTIAL_ACCESS_REFUSED" if {
	evaluating
	not enforce_unavailable
	not covers_record
}
