package impilo.marketplace

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
    input.action startswith "marketplace.api."
    input.marketplace_pipeline_state == "sandbox_access_granted"
    not input.action == "marketplace.api.request_sandbox"
}

deny if {
    input.action startswith "clinical."
    input.identity.identity_type == "marketplace_actor"
}
