package impilo.nompilo

import future.keywords

# Self can always see contextual guidance and locked-state explanations on their own context.
test_self_context_view_allowed if {
	allow with input as {
		"action": "nompilo.guidance.context.view",
		"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
		"user_type": "CITIZEN",
		"subject_ref": "CPID-1",
		"assurance_loa": 1,
	}
}

test_self_locked_explain_allowed if {
	allow with input as {
		"action": "nompilo.guidance.locked.explain",
		"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
		"user_type": "CITIZEN",
		"subject_ref": "CPID-1",
		"assurance_loa": 1,
	}
}

# Clinical/care guidance about your own record is allowed even at low trust (it's about you);
# the sovereign owner still governs the underlying detail.
test_self_clinical_guidance_allowed if {
	allow with input as {
		"action": "nompilo.guidance.clinical.view",
		"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
		"user_type": "CITIZEN",
		"subject_ref": "CPID-1",
		"assurance_loa": 1,
	}
}

# Dependant/proxy guidance requires a valid, active delegation.
test_dependant_guidance_without_delegation_denied if {
	not allow with input as {
		"action": "nompilo.guidance.clinical.view",
		"identity": {"health_id": "GUARDIAN-1", "identity_type": "citizen"},
		"user_type": "GUARDIAN",
		"subject_ref": "Patient/CHILD-1",
		"relationship": "PROXY",
		"delegation": {"active": false},
		"assurance_loa": 3,
	}
}

test_dependant_guidance_with_delegation_allowed if {
	allow with input as {
		"action": "nompilo.guidance.clinical.view",
		"identity": {"health_id": "GUARDIAN-1", "identity_type": "citizen"},
		"user_type": "GUARDIAN",
		"subject_ref": "Patient/CHILD-1",
		"relationship": "PROXY",
		"delegation": {"active": true},
		"assurance_loa": 3,
	}
}

# Provider work guidance is denied without an active provider context (provider-as-person
# separation: a provider identity alone does not grant work-context guidance).
test_provider_guidance_without_context_denied if {
	not allow with input as {
		"action": "nompilo.guidance.provider.view",
		"identity": {"health_id": "PROVIDER-PERSON-1", "identity_type": "provider"},
		"user_type": "PROVIDER",
		"subject_ref": "PROVIDER-PERSON-1",
		"provider": {"context_active": false},
		"assurance_loa": 4,
	}
}

test_provider_guidance_with_context_allowed if {
	allow with input as {
		"action": "nompilo.guidance.provider.view",
		"identity": {"health_id": "PROVIDER-PERSON-1", "identity_type": "provider"},
		"user_type": "PROVIDER",
		"subject_ref": "PROVIDER-PERSON-1",
		"provider": {"context_active": true},
		"assurance_loa": 4,
	}
}

# Facility-mode guidance needs an active facility context.
test_facility_guidance_without_context_denied if {
	not allow with input as {
		"action": "nompilo.guidance.facility.view",
		"identity": {"health_id": "ADMIN-1", "identity_type": "operator"},
		"user_type": "FACILITY_ADMIN",
		"subject_ref": "ADMIN-1",
		"facility": {"context_active": false},
		"assurance_loa": 3,
	}
}

test_facility_guidance_with_context_allowed if {
	allow with input as {
		"action": "nompilo.guidance.facility.view",
		"identity": {"health_id": "ADMIN-1", "identity_type": "operator"},
		"user_type": "FACILITY_ADMIN",
		"subject_ref": "ADMIN-1",
		"facility": {"context_active": true},
		"assurance_loa": 3,
	}
}

# Citizen self-education via Fundo is allowed; provider SOP guidance needs a provider context.
test_citizen_fundo_self_education_allowed if {
	allow with input as {
		"action": "nompilo.guidance.fundo.view",
		"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
		"user_type": "CITIZEN",
		"subject_ref": "CPID-1",
		"assurance_loa": 1,
	}
}

test_provider_fundo_protocol_without_context_denied if {
	not allow with input as {
		"action": "nompilo.guidance.fundo.view",
		"identity": {"health_id": "PROVIDER-PERSON-1", "identity_type": "provider"},
		"user_type": "PROVIDER",
		"subject_ref": "PROVIDER-PERSON-1",
		"provider": {"context_active": false},
		"assurance_loa": 4,
	}
}

# No person anchor → no guidance at all.
test_no_person_anchor_denied if {
	not allow with input as {
		"action": "nompilo.guidance.context.view",
		"identity": {"health_id": "", "identity_type": "citizen"},
		"user_type": "CITIZEN",
		"subject_ref": "",
		"assurance_loa": 3,
	}
}

# decision object surfaces machine-readable deny reasons.
test_decision_reports_no_provider_context if {
	decision.deny_reasons == ["NO_PROVIDER_CONTEXT"] with input as {
		"action": "nompilo.guidance.provider.view",
		"identity": {"health_id": "PROVIDER-PERSON-1", "identity_type": "provider"},
		"user_type": "PROVIDER",
		"subject_ref": "PROVIDER-PERSON-1",
		"provider": {"context_active": false},
		"assurance_loa": 4,
	}
}

test_decision_reports_no_delegation_for_proxy if {
	decision.deny_reasons == ["NO_DELEGATION"] with input as {
		"action": "nompilo.guidance.clinical.view",
		"identity": {"health_id": "GUARDIAN-1", "identity_type": "citizen"},
		"user_type": "GUARDIAN",
		"subject_ref": "Patient/CHILD-1",
		"relationship": "PROXY",
		"delegation": {"active": false},
		"assurance_loa": 3,
	}
}
