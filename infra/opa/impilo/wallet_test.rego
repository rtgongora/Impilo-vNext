package impilo.wallet

import future.keywords

# Self-view of own wallet is allowed for any authenticated person.
test_self_view_allowed if {
	allow with input as {
		"action": "wallet.self.view",
		"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
		"subject_ref": "CPID-1",
		"assurance_loa": 1,
	}
}

# Low-trust user may see the clinical summary but NOT the detailed record.
test_low_trust_summary_allowed if {
	allow with input as {
		"action": "wallet.clinical.summary.view",
		"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
		"subject_ref": "CPID-1",
		"assurance_loa": 1,
	}
}

test_low_trust_clinical_detail_denied if {
	not allow with input as {
		"action": "wallet.clinical.detail.view",
		"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
		"subject_ref": "CPID-1",
		"assurance_loa": 2,
	}
}

test_high_trust_clinical_detail_allowed if {
	allow with input as {
		"action": "wallet.clinical.detail.view",
		"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
		"subject_ref": "CPID-1",
		"assurance_loa": 3,
	}
}

# Dependant access requires a real, active delegation.
test_dependant_view_without_delegation_denied if {
	not allow with input as {
		"action": "wallet.dependant.view",
		"identity": {"health_id": "GUARDIAN-1", "identity_type": "citizen"},
		"subject_ref": "Patient/CHILD-1",
		"delegation": {"active": false},
		"assurance_loa": 3,
	}
}

test_dependant_view_with_delegation_allowed if {
	allow with input as {
		"action": "wallet.dependant.view",
		"identity": {"health_id": "GUARDIAN-1", "identity_type": "citizen"},
		"subject_ref": "Patient/CHILD-1",
		"delegation": {"active": true},
		"assurance_loa": 3,
	}
}

# Consent mutation for a dependant needs delegation AND explicit authority.
test_dependant_consent_without_authority_denied if {
	not allow with input as {
		"action": "wallet.consent.revoke",
		"identity": {"health_id": "GUARDIAN-1", "identity_type": "citizen"},
		"subject_ref": "Patient/CHILD-1",
		"delegation": {"active": true},
		"consent": {"has_authority": false},
		"assurance_loa": 3,
	}
}

test_self_consent_revoke_allowed if {
	allow with input as {
		"action": "wallet.consent.revoke",
		"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
		"subject_ref": "CPID-1",
		"assurance_loa": 2,
	}
}

# Provider-as-person: a provider identity does NOT auto-gain access to another person's wallet
# without a delegation — the work role grants nothing here.
test_provider_no_auto_access_to_other_person if {
	not allow with input as {
		"action": "wallet.dependant.view",
		"identity": {"health_id": "PROVIDER-PERSON-1", "identity_type": "provider"},
		"subject_ref": "Patient/SOMEONE-ELSE",
		"delegation": {"active": false},
		"assurance_loa": 4,
	}
}

# No person anchor (no Health ID) => no wallet at all.
test_no_person_anchor_denied if {
	not allow with input as {
		"action": "wallet.self.view",
		"identity": {"health_id": "", "identity_type": "citizen"},
		"subject_ref": "",
		"assurance_loa": 3,
	}
}

# decision object surfaces machine-readable deny reasons.
test_decision_reports_trust_too_low if {
	decision.deny_reasons == ["TRUST_TOO_LOW"] with input as {
		"action": "wallet.clinical.detail.view",
		"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
		"subject_ref": "CPID-1",
		"assurance_loa": 1,
	}
}
