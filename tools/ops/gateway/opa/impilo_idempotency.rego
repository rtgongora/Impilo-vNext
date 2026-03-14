# ============================================================================
# OPA Policy: Idempotency-Key Required Endpoint Classification
# ============================================================================
#
# Classifies v1.1 command endpoints (POST, PUT, PATCH, DELETE) and enforces
# that the Idempotency-Key header is present on all command (write) requests.
#
# Read-only requests (GET, HEAD, OPTIONS) are exempt.
#
# This policy provides gateway-level classification; the tech-companion
# IdempotencyFilter provides service-level replay/conflict semantics.
#
# ============================================================================

package impilo.gateway.idempotency

import rego.v1

default allow := true
default idempotency_required := false

# ── Helper: extract header value ───────────────────────────────────
header_value(name) := value if {
    some h in input.attributes.request.http.headers
    lower(h) == lower(name)
    value := input.attributes.request.http.headers[h]
}

# ── Identify v1.1 paths ───────────────────────────────────────────
is_v11_path if {
    path := input.attributes.request.http.path
    startswith(path, "/internal/v1/")
}

is_v11_path if {
    path := input.attributes.request.http.path
    startswith(path, "/external/v1/")
}

# ── Command methods ───────────────────────────────────────────────
is_command_method if {
    input.attributes.request.http.method == "POST"
}

is_command_method if {
    input.attributes.request.http.method == "PUT"
}

is_command_method if {
    input.attributes.request.http.method == "PATCH"
}

is_command_method if {
    input.attributes.request.http.method == "DELETE"
}

# ── Idempotency required for command methods on v1.1 paths ────────
idempotency_required if {
    is_v11_path
    is_command_method
}

# ── Check presence of Idempotency-Key header ─────────────────────
has_idempotency_key if {
    value := header_value("idempotency-key")
    value != ""
}

# ── Deny command requests without Idempotency-Key ────────────────
allow := false if {
    idempotency_required
    not has_idempotency_key
}

# ── Denial reason ────────────────────────────────────────────────
reason := concat("", [
    "IDEMPOTENCY_KEY_REQUIRED: ",
    input.attributes.request.http.method,
    " ",
    input.attributes.request.http.path,
    " requires Idempotency-Key header",
]) if {
    not allow
}

# ── Classification metadata (exposed for logging/metrics) ────────
endpoint_classification := {
    "path": input.attributes.request.http.path,
    "method": input.attributes.request.http.method,
    "idempotency_required": idempotency_required,
    "has_idempotency_key": has_idempotency_key,
}
