# ============================================================================
# OPA Policy: Class A vs Class B Caching Guidance
# ============================================================================
#
# From Manifest v1.1:
#   Class A — Strongly consistent, authoritative writes
#     Examples: patient registration, order placement, payment intents
#     Rule: NEVER cache. Always hit the authoritative source.
#     Cache-Control: no-store, no-cache, must-revalidate
#
#   Class B — Eventually consistent reads, cacheable
#     Examples: terminology lookups, facility profiles, catalog reads
#     Rule: Cache with appropriate TTL (5min–1hr typical).
#     Cache-Control: public, max-age=300
#
#   Class C — Fire-and-forget, async
#     Examples: audit log appends, event publishing
#     Rule: No caching relevance (fire-and-forget).
#
# This policy provides classification guidance per service/endpoint.
# OPA evaluates this at the gateway to set Cache-Control headers.
# ============================================================================

package impilo.gateway.caching

import rego.v1

default data_class := "A"

# ── Class B: read-only terminology/catalog endpoints ───────────────
# These endpoints return eventually consistent data suitable for caching.

data_class := "B" if {
    path := input.attributes.request.http.path
    input.attributes.request.http.method == "GET"
    class_b_path(path)
}

class_b_path(path) if {
    startswith(path, "/internal/v1/terminology/")
}

class_b_path(path) if {
    startswith(path, "/internal/v1/facilities/")
}

class_b_path(path) if {
    startswith(path, "/internal/v1/catalog/")
}

class_b_path(path) if {
    startswith(path, "/external/v1/terminology/")
}

class_b_path(path) if {
    startswith(path, "/external/v1/facilities/")
}

# ── Class C: fire-and-forget async endpoints ───────────────────────
data_class := "C" if {
    path := input.attributes.request.http.path
    input.attributes.request.http.method == "POST"
    class_c_path(path)
}

class_c_path(path) if {
    contains(path, "/events/")
}

class_c_path(path) if {
    contains(path, "/audit/")
}

# ── Cache-Control header based on class ────────────────────────────
cache_control := "no-store, no-cache, must-revalidate" if {
    data_class == "A"
}

cache_control := "public, max-age=300" if {
    data_class == "B"
}

cache_control := "no-store" if {
    data_class == "C"
}
