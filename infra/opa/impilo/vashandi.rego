package impilo.vashandi

import future.keywords.in

default allow := false

# Check-in does not create clinical authority or professional Work access
deny if {
    input.action startswith "clinical."
    input.attendance_event_type in {"check_in", "offline_check_in", "supervisor_confirmed_check_in"}
}

deny if {
    input.action == "work.context.enter"
    input.attendance_event_type in {"check_in", "offline_check_in", "supervisor_confirmed_check_in"}
    not input.active_vashandi_assignment
}

deny if {
    input.action startswith "vashandi.attendance.check_in"
    not input.active_vashandi_assignment
}

deny if {
    input.action startswith "vashandi.attendance.check_in"
    not input.active_rostered_shift
}

# Council/HPA regulatory blocks prevent assignment activation
deny if {
    input.action == "vashandi.assignments.activate"
    input.provider_worker_status in {
        "suspended", "expired", "revoked", "pending_verification",
        "requires_update", "under_investigation"
    }
}

deny if {
    input.action == "vashandi.assignments.activate"
    input.council_registration_status in {"suspended", "expired", "revoked", "restricted"}
}

# HSC employment blocks public-sector workforce activation
deny if {
    input.action == "vashandi.assignments.activate"
    input.public_sector_employment_status in {
        "suspended_from_employment", "dismissed", "not_employed_public_sector",
        "under_disciplinary_review", "contract_expired"
    }
    input.employer_organisation_type != "health_service_commission"
}

deny if {
    input.action startswith "vashandi."
    input.workforce_status in {"suspended", "dismissed", "offboarded", "under_review"}
}

# Vashandi roles do not grant clinical service delivery by default
deny if {
    input.action startswith "clinical."
    input.role_template startswith "vashandi_"
}

# Facility manager scope — assignments only within assigned facility
deny if {
    input.action in {
        "vashandi.assignments.create",
        "vashandi.assignments.activate",
        "vashandi.assignments.suspend",
        "vashandi.assignments.end",
        "vashandi.rosters.manage",
        "vashandi.shifts.manage",
    }
    input.role_template == "vashandi_facility_workforce_manager"
    input.target_facility_id != input.manager_facility_id
}

deny if {
    input.action in {
        "vashandi.assignments.create",
        "vashandi.assignments.activate",
        "vashandi.rosters.manage",
        "vashandi.shifts.manage",
    }
    input.role_template == "vashandi_department_supervisor"
    input.target_department_id != input.manager_department_id
}

# Organisation admin cannot manage public facility workforce outside own organisation
deny if {
    input.action == "vashandi.organisation_workforce.manage"
    input.role_template == "vashandi_organisation_workforce_admin"
    input.target_organisation_id != input.manager_organisation_id
}

deny if {
    input.action == "vashandi.organisation_workforce.manage"
    input.role_template == "vashandi_organisation_workforce_admin"
    input.target_employer_organisation_type == "public_facility"
    input.manager_organisation_type != "public_facility"
}

# Scope-bounded workspace access
allow if {
    input.action startswith "vashandi."
    input.vashandi_workspace in input.visible_vashandi_workspaces
}

allow if {
    input.action == "vashandi.attendance.check_in"
    input.active_vashandi_assignment
    input.active_rostered_shift
    input.vashandi_workspace in input.visible_vashandi_workspaces
}

allow if {
    input.action == "vashandi.attendance.supervisor_confirm"
    input.role_template in {
        "vashandi_attendance_supervisor",
        "vashandi_facility_workforce_manager",
        "vashandi_department_supervisor",
    }
    input.vashandi_workspace in input.visible_vashandi_workspaces
}

allow if {
    input.action == "vashandi.hsc_postings.view"
    input.role_template in {"vashandi_hsc_workforce_admin", "vashandi_national_workforce_admin"}
    input.vashandi_workspace == "vashandi.hsc_postings"
}

allow if {
    input.action == "vashandi.emergency_surge.manage"
    input.role_template in {"vashandi_emergency_surge_coordinator", "vashandi_national_workforce_admin"}
    input.vashandi_workspace == "vashandi.emergency_surge"
}
