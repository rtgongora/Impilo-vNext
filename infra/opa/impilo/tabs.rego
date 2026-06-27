package impilo.tabs

import future.keywords

default allow := false

# Personal tab requires Health ID / citizen relationship
personal_visible if {
    input.identity.health_id != ""
}

# Professional tab requires verified provider/worker — NOT Keycloak roles
professional_visible if {
    input.identity.provider_worker_id != ""
    input.provider_worker_status in {"verified", "active", "active_restricted"}
}

# Work tab requires active work assignment — NOT provider ID alone
work_visible if {
    count(input.active_work_assignments) > 0
    input.provider_worker_status in {"verified", "active", "active_restricted"}
}

allow if {
    input.action == "tab.personal.view"
    personal_visible
}

allow if {
    input.action == "tab.professional.view"
    professional_visible
}

allow if {
    input.action == "tab.work.view"
    work_visible
}
