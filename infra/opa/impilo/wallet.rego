package impilo.wallet

import future.keywords

# Unified Person Health Wallet — person-anchor access policy (MY LIFE / MY HEALTH).
#
# The wallet is an experience surface over sovereign owners; it never widens access. This policy
# gates the wallet's own actions: self-view, the graduated reveal of clinical detail by trust
# level, viewing/acting for a dependant (requires a real Mvumo delegation), consent mutation
# (requires authority), and the provider-as-person separation (a work role never auto-grants
# access to another person's wallet).
#
# Input contract (resolved by the BFF / Tshepo gate):
#   input.action            one of the wallet_actions below
#   input.identity.health_id            caller Health ID (person anchor)
#   input.identity.identity_type        "citizen" | "provider" | ...
#   input.assurance_loa                 integer 1..4 (LoA)
#   input.subject_ref                   person whose wallet is targeted (self or dependant)
#   input.delegation.active             bool — caller has an active Mvumo delegation for subject
#   input.delegation.scopes             set/array of granted scopes
#   input.consent.has_authority         bool — caller may mutate the target's consent

default allow := false

wallet_actions := {
	"wallet.self.view",
	"wallet.identity.view",
	"wallet.clinical.summary.view",
	"wallet.clinical.detail.view",
	"wallet.dependant.view",
	"wallet.dependant.act",
	"wallet.consent.grant",
	"wallet.consent.revoke",
	"wallet.consent.history.view",
	"wallet.payments.view",
	"wallet.comms.edit",
	"wallet.profile.edit_low_risk",
	"wallet.profile.correction.submit",
	"wallet.documents.download",
}

# Is the target the caller themselves?
is_self if {
	input.subject_ref == input.identity.health_id
}

is_self if {
	input.subject_ref == sprintf("Patient/%s", [input.identity.health_id])
}

# A wallet requires a person anchor (Health ID) — provider-as-person still signs in as a person.
has_person_anchor if {
	input.identity.health_id != ""
}

effective_loa := loa if {
	is_number(input.assurance_loa)
	loa := input.assurance_loa
}

effective_loa := 1 if {
	not is_number(input.assurance_loa)
}

# ── Self-service, low-risk: any authenticated person on their own wallet ──────────────

allow if {
	input.action in {
		"wallet.self.view",
		"wallet.identity.view",
		"wallet.clinical.summary.view",
		"wallet.payments.view",
		"wallet.comms.edit",
		"wallet.profile.edit_low_risk",
		"wallet.profile.correction.submit",
		"wallet.consent.history.view",
	}
	has_person_anchor
	is_self
}

# ── Graduated friction: detailed clinical record needs a higher trust level ───────────

allow if {
	input.action == "wallet.clinical.detail.view"
	has_person_anchor
	is_self
	effective_loa >= 3
}

allow if {
	input.action == "wallet.documents.download"
	has_person_anchor
	is_self
	effective_loa >= 2
}

# ── Consent mutation on own record (sensitive but self-authority) ─────────────────────

allow if {
	input.action in {"wallet.consent.grant", "wallet.consent.revoke"}
	has_person_anchor
	is_self
}

# Consent mutation for a dependant requires both an active delegation AND explicit authority.
allow if {
	input.action in {"wallet.consent.grant", "wallet.consent.revoke"}
	has_person_anchor
	not is_self
	input.delegation.active == true
	input.consent.has_authority == true
}

# ── Dependant / delegated access: only with a real, active Mvumo delegation ───────────

allow if {
	input.action in {"wallet.dependant.view", "wallet.clinical.summary.view"}
	has_person_anchor
	not is_self
	input.delegation.active == true
}

allow if {
	input.action == "wallet.dependant.act"
	has_person_anchor
	not is_self
	input.delegation.active == true
}

# Detailed clinical view of a dependant: active delegation AND elevated trust.
allow if {
	input.action == "wallet.clinical.detail.view"
	has_person_anchor
	not is_self
	input.delegation.active == true
	effective_loa >= 3
}

# ── Machine-readable decision (mirrors Tshepo Decision shape) ─────────────────────────

deny_reasons contains "NO_PERSON_ANCHOR" if {
	input.action in wallet_actions
	not has_person_anchor
}

deny_reasons contains "NO_DELEGATION" if {
	input.action in {"wallet.dependant.view", "wallet.dependant.act"}
	not is_self
	not input.delegation.active == true
}

deny_reasons contains "TRUST_TOO_LOW" if {
	input.action == "wallet.clinical.detail.view"
	is_self
	effective_loa < 3
}

deny_reasons contains "NO_CONSENT_AUTHORITY" if {
	input.action in {"wallet.consent.grant", "wallet.consent.revoke"}
	not is_self
	not input.consent.has_authority == true
}

decision := {
	"allow": allow,
	"deny_reasons": sort([r | some r in deny_reasons]),
}
