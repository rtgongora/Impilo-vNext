package impilo.hsc

default allow := false

# HSC workforce governance — not clinical service delivery
deny if {
    input.action startswith "clinical."
    input.organisation.organisation_type == "health_service_commission"
}

deny if {
    input.action startswith "clinical."
    input.role_template startswith "hsc_"
}

# HSC does not manage professional council registrations
deny if {
    input.action startswith "registry.provider."
    input.organisation.organisation_type == "health_service_commission"
}

# Facility managers cannot override HSC suspended/dismissed employment
deny if {
    input.action == "facility.staff.assign"
    input.public_sector_employment_status in {"suspended_from_employment", "dismissed", "under_disciplinary_review"}
}

deny if {
    input.action == "work.context.enter"
    input.public_sector_employment_status in {"suspended_from_employment", "dismissed", "not_employed_public_sector"}
    input.employer_organisation_type != "health_service_commission"
}

# HSC employment alone does not grant clinical Work
deny if {
    input.action startswith "clinical."
    input.public_sector_employment_status == "active"
    not input.active_workplace_assignment
}

allow if {
    input.action startswith "hsc."
    input.organisation.organisation_type == "health_service_commission"
    input.management_workspace in input.visible_management_workspaces
}

allow if {
    input.action == "work.context.enter"
    input.context_type == "public_sector_workforce_governance"
    input.organisation.organisation_type == "health_service_commission"
}
