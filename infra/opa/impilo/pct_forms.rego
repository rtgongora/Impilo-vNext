package impilo.pct_forms

import future.keywords

# Patient Encounter Structured Forms — authorization policy (PCT-owned).
#
# Gates who may resolve, view, start/draft, edit, submit, countersign, amend, void encounter form
# responses, and who may administer form definitions. Enforces: active licence, facility scope,
# cadre eligibility, trainee → countersign-only (may not countersign), suspended/expired/unverified
# providers blocked, sensitive/restricted form access, and an audited emergency break-glass path.
#
# The Java PolicyEngine (tshepo-authz) remains authoritative; this runs in shadow for parity/testing.
#
# Input contract (resolved by the BFF / Tshepo gate):
#   input.action              one of form_actions below
#   input.identity.health_id  caller Health ID (person anchor)
#   input.cadre_family        PHYSICIAN | CLINICAL_OFFICER | NURSE | MIDWIFE | PHARMACY | COMMUNITY |
#                             LAB | RADIOLOGY | ALLIED | ENVIRONMENTAL | OTHER
#   input.licence_status      ACTIVE | EXPIRED | SUSPENDED | UNVERIFIED
#   input.is_trainee          bool
#   input.is_supervisor       bool  — may countersign
#   input.is_forms_admin      bool  — may administer definitions
#   input.facility_in_scope   bool  — actor's facility covers the encounter facility
#   input.purpose             TREATMENT | EMERGENCY | ...
#   input.break_glass         bool
#   input.form_sensitivity    STANDARD | SENSITIVE | RESTRICTED
#   input.restricted_access_granted bool  — explicit grant for RESTRICTED forms

default allow := false

form_actions := {
	"pct_form.resolve",
	"pct_form.view",
	"pct_form.start",
	"pct_form.edit",
	"pct_form.submit",
	"pct_form.countersign",
	"pct_form.amend",
	"pct_form.void",
	"pct_form.admin",
}

write_actions := {"pct_form.start", "pct_form.edit", "pct_form.submit", "pct_form.amend", "pct_form.void"}
read_actions := {"pct_form.resolve", "pct_form.view"}

clinical_cadres := {
	"PHYSICIAN", "CLINICAL_OFFICER", "NURSE", "MIDWIFE", "PHARMACY",
	"COMMUNITY", "LAB", "RADIOLOGY", "ALLIED", "ENVIRONMENTAL",
}

has_person_anchor if input.identity.health_id != ""

licence_active if input.licence_status == "ACTIVE"

emergency if {
	input.break_glass == true
	upper(object.get(input, "purpose", "")) == "EMERGENCY"
}

# Sensitivity gate: RESTRICTED forms need an explicit grant; SENSITIVE needs an active licence.
sensitivity_ok if {
	sens := object.get(input, "form_sensitivity", "STANDARD")
	sens == "STANDARD"
}
sensitivity_ok if {
	object.get(input, "form_sensitivity", "STANDARD") == "SENSITIVE"
	licence_active
}
sensitivity_ok if {
	object.get(input, "form_sensitivity", "STANDARD") == "RESTRICTED"
	input.restricted_access_granted == true
}

# ── Read: resolve / view — active clinical cadre with a person anchor ──────────
allow if {
	input.action in read_actions
	has_person_anchor
	input.cadre_family in clinical_cadres
	licence_active
	sensitivity_ok
}
# Emergency break-glass may always read (audited).
allow if {
	input.action in read_actions
	emergency
}

# ── Write: start / edit / submit / amend / void ───────────────────────────────
allow if {
	input.action in write_actions
	has_person_anchor
	input.cadre_family in clinical_cadres
	licence_active
	input.facility_in_scope == true
	sensitivity_ok
}
# Emergency break-glass write (audited) — bypasses facility scope + non-active licence.
allow if {
	input.action in write_actions
	emergency
	has_person_anchor
}

# ── Countersign — supervisor only, active licence, never a trainee ────────────
allow if {
	input.action == "pct_form.countersign"
	has_person_anchor
	input.is_supervisor == true
	not input.is_trainee
	licence_active
}

# ── Admin: manage definitions (forms admin only) ──────────────────────────────
allow if {
	input.action == "pct_form.admin"
	has_person_anchor
	input.is_forms_admin == true
}

# ── Deny reasons (surfaced for shadow parity + explainability) ────────────────
deny_reasons contains "NO_PERSON_ANCHOR" if not has_person_anchor

deny_reasons contains "LICENCE_NOT_ACTIVE" if {
	input.action in write_actions
	not licence_active
	not emergency
}

deny_reasons contains "FACILITY_OUT_OF_SCOPE" if {
	input.action in write_actions
	licence_active
	not input.facility_in_scope
	not emergency
}

deny_reasons contains "TRAINEE_CANNOT_COUNTERSIGN" if {
	input.action == "pct_form.countersign"
	input.is_trainee == true
}

deny_reasons contains "COUNTERSIGN_REQUIRES_SUPERVISOR" if {
	input.action == "pct_form.countersign"
	not input.is_supervisor
}

deny_reasons contains "RESTRICTED_FORM_NO_GRANT" if {
	object.get(input, "form_sensitivity", "STANDARD") == "RESTRICTED"
	not input.restricted_access_granted
}

deny_reasons contains "NOT_A_CLINICAL_CADRE" if {
	input.action in (write_actions | read_actions)
	not input.cadre_family in clinical_cadres
	not emergency
}

decision := {"allow": allow, "deny_reasons": deny_reasons}
