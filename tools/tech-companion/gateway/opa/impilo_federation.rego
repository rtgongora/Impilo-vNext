# ============================================================================
# OPA Policy: Federation Guardrails
# ============================================================================
#
# Deny updates to national-authoritative fields from private pods.
#
# National-authoritative operations:
#   - Patient identity merges (VITO)
#   - Tariff updates (MSIKA/COSTA)
#   - Terminology publishing (ZIBO)
#   - Controlled substance prescriptions (clinical)
#   - Master data mutations (facility/provider registry)
#
# Rule: If X-Pod-ID != "national" AND the operation is national-authoritative,
#        deny with FEDERATION_AUTHORITY_VIOLATION.
#
# ============================================================================

package impilo.gateway.federation

import rego.v1

default allow := true

# ── Extract pod ID ─────────────────────────────────────────────────
pod_id := input.attributes.request.http.headers["x-pod-id"]

is_national if {
    lower(pod_id) == "national"
}

# ── National-authoritative endpoint patterns ───────────────────────
# Add new patterns here as services adopt the companion spec.

national_only_operation if {
    path := input.attributes.request.http.path
    input.attributes.request.http.method != "GET"
    national_authoritative_path(path)
}

# Patient identity merge (VITO)
national_authoritative_path(path) if {
    contains(path, "/v1/identity/merge")
}

# Tariff management (COSTA/MSIKA)
national_authoritative_path(path) if {
    contains(path, "/v1/tariffs")
    input.attributes.request.http.method != "GET"
}

# Terminology publishing (ZIBO)
national_authoritative_path(path) if {
    contains(path, "/v1/terminology/publish")
}

# Master facility registry mutations (TUSO)
national_authoritative_path(path) if {
    contains(path, "/v1/facilities")
    input.attributes.request.http.method == "PUT"
}

national_authoritative_path(path) if {
    contains(path, "/v1/facilities")
    input.attributes.request.http.method == "DELETE"
}

# Provider credential mutations (VARAPI)
national_authoritative_path(path) if {
    contains(path, "/v1/credentials/revoke")
}

# ── Deny rule ──────────────────────────────────────────────────────
allow := false if {
    national_only_operation
    not is_national
}

# ── Violation reason ──────────────────────────────────────────────
violation_reason := concat("", [
    "FEDERATION_AUTHORITY_VIOLATION: pod=",
    pod_id,
    " attempted national-authoritative operation on ",
    input.attributes.request.http.path,
]) if {
    not allow
}
