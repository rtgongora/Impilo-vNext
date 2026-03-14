# ============================================================================
# OPA Policy: Federation Authority Guardrails (Gateway Deployment)
# ============================================================================
#
# Deny updates to national-authoritative resources from non-national pods.
#
# National-authoritative operations:
#   - Patient identity merges (VITO)
#   - Tariff updates (COSTA/MSIKA)
#   - Terminology publishing (ZIBO)
#   - Master data mutations (TUSO facility registry)
#   - Provider credential revocations (VARAPI)
#   - Policy pack management (security-hardening-service)
#
# Rule: If X-Pod-ID != "national" AND operation is national-authoritative,
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

is_national if {
    lower(pod_id) == "national-spine"
}

# ── National-authoritative endpoint patterns ───────────────────────
national_only_operation if {
    path := input.attributes.request.http.path
    input.attributes.request.http.method != "GET"
    input.attributes.request.http.method != "HEAD"
    input.attributes.request.http.method != "OPTIONS"
    national_authoritative_path(path)
}

# Patient identity merge (VITO)
national_authoritative_path(path) if {
    contains(path, "/v1/identity/merge")
}

# Patient identity unlink (VITO)
national_authoritative_path(path) if {
    contains(path, "/v1/identity/unlink")
}

# Tariff management (COSTA/MSIKA)
national_authoritative_path(path) if {
    contains(path, "/v1/tariffs")
}

# Terminology publishing (ZIBO)
national_authoritative_path(path) if {
    contains(path, "/v1/terminology/publish")
}

# Terminology governance (ZIBO)
national_authoritative_path(path) if {
    contains(path, "/v1/governance")
    input.attributes.request.http.method != "GET"
}

# Master facility registry mutations (TUSO)
national_authoritative_path(path) if {
    startswith(path, "/api/v1/facilities")
    input.attributes.request.http.method == "PUT"
}

national_authoritative_path(path) if {
    startswith(path, "/api/v1/facilities")
    input.attributes.request.http.method == "DELETE"
}

# Provider credential revocation (VARAPI)
national_authoritative_path(path) if {
    contains(path, "/v1/credentials/revoke")
}

# Security policy pack management
national_authoritative_path(path) if {
    contains(path, "/v1/policy-packs")
    input.attributes.request.http.method != "GET"
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
    " attempted national-authoritative operation: ",
    input.attributes.request.http.method,
    " ",
    input.attributes.request.http.path,
]) if {
    not allow
}
