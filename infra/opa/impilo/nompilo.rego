package impilo.nompilo

import future.keywords

# Nompilo Intelligent Layer — guidance access policy.
#
# Nompilo is the on-platform guide: it explains where you are, what's next, why something is
# locked, and routes to the sovereign owner. It owns no transaction and must NEVER widen access.
# This policy gates Nompilo's own guidance actions — it does not authorise the underlying owner
# action (that is the owner's PDP). It governs: who may see contextual guidance, who may fetch a
# locked/denied explanation, when clinical/care or Fundo/protocol guidance is shown, and the
# provider-as-person + proxy separations.
#
# Input contract (resolved by the BFF / Tshepo gate, mirrors the wallet + policy_decision shape):
#   input.action            one of nompilo_actions below
#   input.identity.health_id        caller Health ID (person anchor)
#   input.identity.identity_type    "citizen" | "provider" | "operator" | ...
#   input.user_type                 CITIZEN | PROVIDER | GUARDIAN | FACILITY_ADMIN | SUPPORT
#   input.assurance_loa             integer 1..4 (LoA)
#   input.subject_ref               person the guidance is about (self or dependant)
#   input.relationship              SELF | GUARDIAN | CAREGIVER | PROXY | FACILITY_ASSISTED
#   input.delegation.active         bool — caller has an active Mvumo delegation for the subject
#   input.provider.context_active   bool — caller has a valid active provider/work context
#   input.facility.context_active   bool — caller is checked into a facility/workspace

default allow := false

nompilo_actions := {
	"nompilo.guidance.context.view",
	"nompilo.guidance.locked.explain",
	"nompilo.guidance.clinical.view",
	"nompilo.guidance.provider.view",
	"nompilo.guidance.facility.view",
	"nompilo.guidance.fundo.view",
	"nompilo.guidance.followup.request",
	"nompilo.guidance.escalation.request",
	"nompilo.guidance.dismiss",
}

# A person anchor (Health ID) is required for any Nompilo guidance — provider-as-person still
# signs in as a person.
has_person_anchor if {
	input.identity.health_id != ""
}

is_self if {
	input.subject_ref == input.identity.health_id
}

is_self if {
	input.subject_ref == sprintf("Patient/%s", [input.identity.health_id])
}

is_self if {
	input.subject_ref == ""
}

is_self if {
	not input.subject_ref
}

effective_loa := loa if {
	is_number(input.assurance_loa)
	loa := input.assurance_loa
}

effective_loa := 1 if {
	not is_number(input.assurance_loa)
}

# ── Self-service guidance: any authenticated person on their own context ──────────────

allow if {
	input.action in {
		"nompilo.guidance.context.view",
		"nompilo.guidance.locked.explain",
		"nompilo.guidance.dismiss",
		"nompilo.guidance.followup.request",
		"nompilo.guidance.escalation.request",
	}
	has_person_anchor
	is_self
}

# ── Clinical/care guidance: self at any trust (it's about you), or a dependant with delegation ─

allow if {
	input.action == "nompilo.guidance.clinical.view"
	has_person_anchor
	is_self
}

allow if {
	input.action in {
		"nompilo.guidance.context.view",
		"nompilo.guidance.locked.explain",
		"nompilo.guidance.clinical.view",
		"nompilo.guidance.followup.request",
	}
	has_person_anchor
	not is_self
	input.delegation.active == true
}

# ── Provider work guidance: only with a valid, active provider context ────────────────

allow if {
	input.action == "nompilo.guidance.provider.view"
	has_person_anchor
	input.user_type == "PROVIDER"
	input.provider.context_active == true
}

# ── Facility-mode guidance: only with an active facility context ──────────────────────

allow if {
	input.action == "nompilo.guidance.facility.view"
	has_person_anchor
	input.user_type == "FACILITY_ADMIN"
	input.facility.context_active == true
}

# ── Fundo / protocol guidance: citizen self-education always; provider SOP needs provider ctx ─

allow if {
	input.action == "nompilo.guidance.fundo.view"
	has_person_anchor
	input.user_type == "CITIZEN"
	is_self
}

allow if {
	input.action == "nompilo.guidance.fundo.view"
	has_person_anchor
	input.user_type == "PROVIDER"
	input.provider.context_active == true
}

# ── Machine-readable decision (mirrors the Tshepo / wallet Decision shape) ─────────────

deny_reasons contains "NO_PERSON_ANCHOR" if {
	input.action in nompilo_actions
	not has_person_anchor
}

deny_reasons contains "NO_DELEGATION" if {
	input.action in {
		"nompilo.guidance.clinical.view",
		"nompilo.guidance.context.view",
		"nompilo.guidance.locked.explain",
	}
	not is_self
	not input.delegation.active == true
}

deny_reasons contains "NO_PROVIDER_CONTEXT" if {
	input.action == "nompilo.guidance.provider.view"
	not input.provider.context_active == true
}

deny_reasons contains "NO_FACILITY_CONTEXT" if {
	input.action == "nompilo.guidance.facility.view"
	not input.facility.context_active == true
}

decision := {
	"allow": allow,
	"deny_reasons": sort([r | some r in deny_reasons]),
}
