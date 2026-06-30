package impilo.khuluma_test

import rego.v1

import data.impilo.khuluma

# A clean provider comms context (active org assignment, clinical role).
provider_ctx := {
	"action": "comms.message.send",
	"role": "CLINICIAN",
	"identity": {"identity_type": "provider"},
	"organisation": {
		"lifecycle_status": "active",
		"active_organisation_assignment": true,
		"organisation_type": "public_hospital",
	},
	"channel": {"transport": "native", "secure": true},
	"message": {"contains_clinical_content": true},
	"conversation": {"linked_to_patient": false},
}

# ── Allows ──────────────────────────────────────────────────────────────────

test_citizen_may_use_core_comms if {
	khuluma.allow with input as {"action": "comms.conversation.create", "identity": {"identity_type": "citizen"}}
}

test_provider_with_assignment_may_use_core_comms if {
	khuluma.allow with input as provider_ctx
	not khuluma.deny with input as provider_ctx
}

test_clinical_provider_may_link_patient if {
	ctx := object.union(provider_ctx, {"action": "comms.conversation.link_patient"})
	khuluma.allow with input as ctx
	not khuluma.deny with input as ctx
}

# ── Denies (security-critical) ──────────────────────────────────────────────

test_suspended_org_is_denied if {
	ctx := object.union(provider_ctx, {"organisation": object.union(provider_ctx.organisation, {"lifecycle_status": "suspended"})})
	khuluma.deny with input as ctx
}

test_provider_without_assignment_cannot_use_comms if {
	ctx := object.union(provider_ctx, {"organisation": object.union(provider_ctx.organisation, {"active_organisation_assignment": false})})
	khuluma.deny with input as ctx
	not khuluma.allow with input as ctx
}

test_non_clinical_cannot_link_patient if {
	ctx := object.union(provider_ctx, {"action": "comms.conversation.link_patient", "role": "RECEPTIONIST"})
	khuluma.deny with input as ctx
	not khuluma.allow with input as ctx
}

test_clinical_content_blocked_over_insecure_external if {
	ctx := object.union(provider_ctx, {"channel": {"transport": "external", "secure": false}})
	khuluma.deny with input as ctx
}

test_clinical_content_allowed_when_external_is_secure if {
	ctx := object.union(provider_ctx, {"channel": {"transport": "external", "secure": true}})
	not khuluma.deny with input as ctx
}

test_vendor_org_cannot_join_patient_linked_comms if {
	ctx := object.union(provider_ctx, {
		"conversation": {"linked_to_patient": true},
		"organisation": object.union(provider_ctx.organisation, {"organisation_type": "software_vendor"}),
	})
	khuluma.deny with input as ctx
}

# default-deny: an unknown action is not allowed.
test_unknown_action_default_deny if {
	not khuluma.allow with input as {"action": "comms.unknown", "identity": {"identity_type": "citizen"}}
}
