package impilo.marketplace

import future.keywords

default allow := false

allow if {
    input.action == "marketplace.api.request_sandbox"
    input.marketplace_pipeline_state in {"verified_organisation", "sandbox_access_requested"}
}

allow if {
    input.action == "marketplace.api.approve_production"
    input.marketplace_pipeline_state == "production_access_granted"
}

deny if {
    startswith(input.action, "marketplace.api.")
    input.marketplace_pipeline_state == "sandbox_access_granted"
    not input.action == "marketplace.api.request_sandbox"
}

deny if {
    startswith(input.action, "clinical.")
    input.identity.identity_type == "marketplace_actor"
}
