package impilo.organisation

import future.keywords

default allow := false

# Non-citizen Work requires organisation assignment
deny if {
    input.identity.identity_type != "citizen"
    not input.organisation.active_organisation_assignment
}

# Suspended / offboarded organisations lose Work immediately
deny if {
    input.organisation.lifecycle_status in {"suspended", "offboarded", "blacklisted", "revoked"}
}

# Sandbox environment cannot access production APIs
deny if {
    startswith(input.action, "marketplace.api.")
    input.organisation.access_environment == "sandbox"
    not input.action == "marketplace.api.request_sandbox"
}

# Organisation administrators cannot grant sovereign national tools outside MoHCC
deny if {
    input.action == "management.workspace.enter"
    input.management_workspace in {
        "national_identity_governance",
        "national_organisation_registry",
        "national_system_operator_management",
        "national_platform_user_administration",
    }
    not input.organisation.organisation_type in {"sovereign_public_owner", "ministry_department"}
}

# Municipal users cannot inherit MoHCC national authority
deny if {
    input.action == "management.workspace.enter"
    input.management_workspace == "mohcc_head_office_user_management"
    not input.organisation.organisation_type in {"sovereign_public_owner", "ministry_department"}
}

# Private providers cannot manage public facility staff
deny if {
    input.action == "management.workspace.enter"
    input.management_workspace == "public_facility_staff_management"
    input.organisation.organisation_type in {"private_hospital", "private_clinic", "private_laboratory", "private_pharmacy"}
}

# Marketplace / vendor users cannot browse clinical records
deny if {
    startswith(input.action, "clinical.")
    input.organisation.organisation_type in {
        "software_vendor",
        "api_integration_partner",
        "marketplace_organisation",
        "supplier",
        "manufacturer",
    }
}

# Insurers / payers limited to claims / eligibility / payment scope
deny if {
    startswith(input.action, "clinical.")
    input.organisation.organisation_type in {"insurer", "medical_aid_society", "payment_provider", "claims_processor"}
}

# Research users default to deidentified / aggregate unless explicitly approved
deny if {
    input.action == "clinical.record.view_identified"
    input.organisation.organisation_type == "foreign_research_partner"
    not "identified_data_approved" in input.organisation.data_scopes
}

# Cross-border identified access requires explicit approval
deny if {
    input.organisation.cross_border_access_flag
    not input.organisation.cross_border_approved
    startswith(input.action, "clinical.")
}

allow if {
    input.action == "management.workspace.enter"
    input.management_workspace in input.organisation.visible_management_workspaces
}

allow if {
    input.action == "work.context.enter"
    input.organisation.lifecycle_status in {"active", "active_restricted", "contract_active", "production_active"}
    input.organisation.active_organisation_assignment
}
