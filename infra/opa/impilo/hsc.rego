package impilo.hsc

import future.keywords

default allow := false

# HSC workforce governance — not clinical service delivery
deny if {
    startswith(input.action, "clinical.")
    input.organisation.organisation_type == "health_service_commission"
}

deny if {
    startswith(input.action, "clinical.")
    startswith(input.role_template, "hsc_")
}

# HSC does not manage professional council registrations
deny if {
    startswith(input.action, "registry.provider.")
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
    startswith(input.action, "clinical.")
    input.public_sector_employment_status == "active"
    not input.active_workplace_assignment
}

allow if {
    startswith(input.action, "hsc.")
    input.organisation.organisation_type == "health_service_commission"
    input.management_workspace in input.visible_management_workspaces
}

allow if {
    input.action == "work.context.enter"
    input.context_type == "public_sector_workforce_governance"
    input.organisation.organisation_type == "health_service_commission"
}
