package impilo.registry

import future.keywords

default allow := false

allow if {
    startswith(input.action, "registry.provider.")
    input.registry_role in {"provider_registry_owner", "provider_registry_steward", "provider_registry_approver"}
}

allow if {
    startswith(input.action, "registry.client.")
    input.registry_role in {"client_registry_owner", "client_registry_steward", "client_registry_resolution_officer"}
}

# Facility licensing regulator cannot approve provider licence unless separately authorised
deny if {
    input.action == "registry.provider.verify"
    input.regulator_role == "facility_licensing_regulator"
}
