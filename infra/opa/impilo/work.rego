package impilo.work

import future.keywords

default allow := false

active_assignment if {
    some a in input.active_work_assignments
    a.assignment_status in {"active", "active_temporary", "active_restricted"}
}

allow if {
    input.action == "work.context.enter"
    active_assignment
    not provider_blocked
}

provider_blocked if {
    input.provider_worker_status in {
        "suspended", "expired", "revoked", "pending_verification",
        "draft", "submitted", "not_started", "requires_update"
    }
}

# Facility staff management — local scope only; cannot issue Provider IDs
allow if {
    input.action == "facility.staff.assign"
    active_assignment
    input.role_template in {"facility_administrator", "facility_staff_manager", "medical_superintendent", "matron"}
}

deny if {
    input.action == "registry.provider.verify"
    input.role_template in {"facility_administrator", "facility_staff_manager"}
}
