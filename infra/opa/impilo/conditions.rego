# METADATA
# title: Impilo policy-rule condition evaluation (Phase 7 / A1)
# description: |
#   Exact rego mirror of PolicyEngine.evaluateConditions + effectiveLoa + accountVerified
#   (tshepo-authz PolicyEngine.java). Used by impilo.policy.decision (A4) to evaluate the JSONB
#   conditions attached to a policy_rule. The Java semantics are authoritative; this mirror is
#   validated for verdict parity by the Java<->OPA equivalence harness (A5).
#
#   Canonical OPA `input` contract (mirrors AuthzInternalRequest):
#     actor_type, roles[], action, purpose, resource_type, path,
#     loa (int), assurance_level (string "LOA3"/"3"), facility_id, workspace_id,
#     department_id, ward_id, programme_id, subject_id, now_epoch_ms (optional, for grace checks)
package impilo.policy.conditions

import rego.v1

# Effective LoA = max(session ACR loa, propagated identity-assurance loa) — monotonic (G-CZO-01).
effective_loa := max([loa_int, parse_assurance_loa(object.get(input, "assurance_level", ""))])

loa_int := n if {
	is_number(input.loa)
	n := input.loa
} else := 0

# Parse "LOA3" / "3" -> 1..4; anything else -> 0 (fail-safe: contributes nothing).
parse_assurance_loa(raw) := n if {
	is_string(raw)
	s := trim_prefix(upper(trim_space(raw)), "LOA")
	regex.match(`^[0-9]+$`, s)
	n0 := to_number(s)
	n0 >= 1
	n0 <= 4
	n := n0
} else := 0

# A "verified" account: identity-assurance LOA3+, or a legacy verification-state string.
account_verified if parse_assurance_loa(input.assurance_level) >= 3
account_verified if {
	is_string(input.assurance_level)
	v := upper(input.assurance_level)
	contains(v, "VERIFIED")
}
account_verified if {
	is_string(input.assurance_level)
	contains(upper(input.assurance_level), "REGISTRY")
}

# ── path_contains (segment-bounded, normalised) ─────────────────────────────

# Strip the query (?), matrix (;) and fragment (#) tail, then lower-case.
normalise_path(path) := out if {
	is_string(path)
	cut := [path, substr_before(path, "?"), substr_before(path, ";"), substr_before(path, "#")]
	out := lower(min_len(cut))
} else := ""

substr_before(s, delim) := before if {
	i := indexof(s, delim)
	i >= 0
	before := substring(s, 0, i)
} else := s

# the shortest candidate = the most-truncated (i.e. the earliest delimiter cut)
min_len(xs) := out if {
	out := xs[_]
	every x in xs {
		count(out) <= count(x)
	}
}

# segment-bounded: the needle appears followed by "/" or end-of-path.
path_contains_segment(path, needle) if {
	p := normalise_path(path)
	n := lower(needle)
	endswith(p, n)
}
path_contains_segment(path, needle) if {
	p := normalise_path(path)
	n := lower(needle)
	contains(p, concat("", [n, "/"]))
}

# ── conditions_satisfied(conditions) — all present conditions must pass ──────

conditions_satisfied(conditions) if {
	min_loa_ok(conditions)
	allowed_facilities_ok(conditions)
	allowed_actor_types_ok(conditions)
	allowed_scope_refs_ok(conditions)
	path_contains_ok(conditions)
	account_assurance_ok(conditions)
	grace_ok(conditions)
}

min_loa_ok(c) if not c.min_loa
min_loa_ok(c) if effective_loa >= c.min_loa

# Java only checks allowed_facilities when the request carries a facility_id.
allowed_facilities_ok(c) if not c.allowed_facilities
allowed_facilities_ok(c) if not input.facility_id
allowed_facilities_ok(c) if input.facility_id in {f | some f in c.allowed_facilities}

allowed_actor_types_ok(c) if not c.allowed_actor_types
allowed_actor_types_ok(c) if input.actor_type in {t | some t in c.allowed_actor_types}

allowed_scope_refs_ok(c) if not c.allowed_scope_refs
allowed_scope_refs_ok(c) if {
	allowed := {s | some s in c.allowed_scope_refs}
	some scope in request_scopes
	scope in allowed
}

request_scopes := {s |
	some key in ["facility_id", "workspace_id", "department_id", "ward_id", "programme_id", "subject_id"]
	s := object.get(input, key, "")
	is_string(s)
	s != ""
}

path_contains_ok(c) if not c.path_contains
path_contains_ok(c) if {
	is_string(c.path_contains)
	path_contains_segment(input.path, c.path_contains)
}
path_contains_ok(c) if {
	is_array(c.path_contains)
	some needle in c.path_contains
	path_contains_segment(input.path, needle)
}

account_assurance_ok(c) if not c.account_assurance_required
account_assurance_ok(c) if account_verified

# verification grace: if not verified and now is past the expiry, fail. Needs input.now_epoch_ms
# (the equivalence harness pins time on both sides for determinism).
grace_ok(c) if not c.verification_grace_expiry_epoch_ms
grace_ok(c) if account_verified
grace_ok(c) if {
	c.verification_grace_expiry_epoch_ms
	not account_verified
	is_number(input.now_epoch_ms)
	input.now_epoch_ms <= c.verification_grace_expiry_epoch_ms
}
