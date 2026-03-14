# ============================================================================
# OPA Policy: Enforce Required v1.1 Headers (Gateway Deployment)
# ============================================================================
#
# Deployed to OPA sidecar alongside Envoy at the gateway.
# Enforces that all requests to /internal/v1/ and /external/v1/
# paths carry the mandatory v1.1 headers:
#   X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID
#
# Missing any => DENY with structured reason.
#
# Usage:
#   opa run --server --addr=:9191 \
#     --set=decision_logs.console=true \
#     ./tools/ops/gateway/opa/
#
# ============================================================================

package impilo.gateway.headers

import rego.v1

default allow := false

# ── Helper: extract header value (case-insensitive) ────────────────
header_value(name) := value if {
    some h in input.attributes.request.http.headers
    lower(h) == lower(name)
    value := input.attributes.request.http.headers[h]
}

# ── Helper: check if path is a v1.1 path ──────────────────────────
is_v11_path if {
    path := input.attributes.request.http.path
    startswith(path, "/internal/v1/")
}

is_v11_path if {
    path := input.attributes.request.http.path
    startswith(path, "/external/v1/")
}

is_v11_path if {
    path := input.attributes.request.http.path
    startswith(path, "/federation/v1/")
}

# ── Non-v1.1 paths: allow without header checks ───────────────────
allow if {
    not is_v11_path
}

# ── v1.1 paths: require all four mandatory headers ────────────────
allow if {
    is_v11_path
    has_tenant_id
    has_pod_id
    has_request_id
    has_correlation_id
}

has_tenant_id if {
    value := header_value("x-tenant-id")
    value != ""
}

has_pod_id if {
    value := header_value("x-pod-id")
    value != ""
}

has_request_id if {
    value := header_value("x-request-id")
    value != ""
}

has_correlation_id if {
    value := header_value("x-correlation-id")
    value != ""
}

# ── Denial reasons ────────────────────────────────────────────────
reasons contains "X-Tenant-ID header is required" if {
    is_v11_path
    not has_tenant_id
}

reasons contains "X-Pod-ID header is required" if {
    is_v11_path
    not has_pod_id
}

reasons contains "X-Request-ID header is required" if {
    is_v11_path
    not has_request_id
}

reasons contains "X-Correlation-ID header is required" if {
    is_v11_path
    not has_correlation_id
}

# ── Response metadata ─────────────────────────────────────────────
response_headers := {
    "x-policy-decision": decision,
    "x-policy-version": "v1.1",
    "x-decision-reason": concat("; ", reasons),
}

decision := "ALLOW" if {
    allow
}

decision := "DENY" if {
    not allow
}
