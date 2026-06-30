package impilo.death

import future.keywords

# ─────────────────────────────────────────────────────────────────────────────
# WS#8 — Death & Post-Death Pathway authorization (pct-service owns the DeathCase;
# ubomi-service owns CRVS). Doctrine for a death:
#   * Death documentation / certification / registration is NEVER gated on payment.
#   * Confirming a death requires an authorised clinical role.
#   * Certifying cause of death requires an eligible certifier role AND a licence number.
#   * Routine certification is denied while medico-legal triggers are present and not cleared.
#   * Body release requires a release-authorising role AND no active legal/coroner/post-mortem hold.
#   * Medico-legal / forensic detail is restricted to medico-legal-access roles (or break-glass).
#   * A deceased person's ordinary self-service access is DISABLED after confirmation.
#   * Forensic / post-mortem content is NOT exposed to the family portal by default.
#
# Input contract (subset):
#   input.action            e.g. "death.confirm", "death.certify", "death.body.release"
#   input.identity.health_id          person anchor (required)
#   input.identity.identity_type      "citizen" | "provider" | "staff" | "system"
#   input.identity.deceased           truthy when this anchor is a confirmed-deceased person
#   input.role_template               e.g. "doctor", "mortuary_officer", "registry_clerk"
#   input.has_licence                 truthy when a certifier licence is presented
#   input.medico_legal_open           truthy when a coroner/medico-legal referral is open + uncleared
#   input.hold_active                 truthy when a legal/coroner/post-mortem body hold is active
#   input.portal                      "family" when the request comes from the family/bereavement portal
#   input.content_forensic            truthy when the requested content is forensic/post-mortem
#   input.break_glass                 truthy when an explicit, justified break-glass is in effect
# ─────────────────────────────────────────────────────────────────────────────

default allow := false

has_anchor if {
	input.identity.health_id != ""
	input.identity.health_id != null
}

# Clinical roles permitted to confirm/pronounce a death.
confirm_role if input.role_template in {"doctor", "medical_officer", "consultant", "registrar", "clinical_officer", "platform_admin"}

# Roles whose licence/scope permits cause-of-death certification.
certifier_role if input.role_template in {"doctor", "medical_officer", "consultant", "registrar", "platform_admin"}

# Roles permitted to receive/hold/release a body.
mortuary_role if input.role_template in {"mortuary_officer", "mortuary_manager", "platform_admin"}

# Roles permitted to view medico-legal / forensic detail.
medico_legal_role if input.role_template in {"medical_examiner", "forensic_pathologist", "coroner_liaison", "platform_admin"}

# Roles permitted to assemble / submit the civil-registration package.
crvs_role if input.role_template in {"doctor", "medical_officer", "registry_clerk", "civil_registrar", "platform_admin"}

# ── Action classes ────────────────────────────────────────────────────────────
confirm_actions := {"death.confirm"}
certify_actions := {"death.certify"}
body_release_actions := {"death.body.release"}
body_manage_actions := {"death.body.receive", "death.body.hold", "death.body.release"}
medico_legal_actions := {"death.medicolegal.view", "death.medicolegal.refer"}
crvs_actions := {"death.crvs.package", "death.crvs.submit"}
read_actions := {"death.case.view", "death.case.list", "death.audit.view"}
self_service_actions := {"citizen.selfservice.access", "citizen.record.view", "citizen.appointment.book"}

# ── DENY rules ────────────────────────────────────────────────────────────────

# A deceased person's ordinary self-service access is disabled after confirmation.
deny if {
	input.identity.deceased == true
	input.action in self_service_actions
}

# Confirming a death requires an authorised clinical role.
deny if {
	input.action in confirm_actions
	not confirm_role
}

# Certifying requires an eligible certifier role.
deny if {
	input.action in certify_actions
	not certifier_role
}

# Certifying requires a presented licence.
deny if {
	input.action in certify_actions
	not input.has_licence
}

# Routine certification is denied while an open medico-legal referral is present (no break-glass
# override for routine certification — the competent authority must clear it).
deny if {
	input.action in certify_actions
	input.medico_legal_open == true
}

# Body management requires a mortuary role.
deny if {
	input.action in body_manage_actions
	not mortuary_role
}

# Body release is denied while any legal/coroner/post-mortem hold is active.
deny if {
	input.action in body_release_actions
	input.hold_active == true
}

# Medico-legal / forensic detail requires a medico-legal-access role unless break-glass.
deny if {
	input.action in medico_legal_actions
	not medico_legal_role
	not input.break_glass
}

# Forensic / post-mortem content is never exposed to the family portal.
deny if {
	input.portal == "family"
	input.content_forensic == true
}

denied if deny

# ── ALLOW rules ───────────────────────────────────────────────────────────────

# 1. Confirm a death — authorised clinical role.
allow if {
	has_anchor
	input.action in confirm_actions
	confirm_role
	not denied
}

# 2. Certify cause of death — eligible certifier, licence, no open medico-legal referral.
allow if {
	has_anchor
	input.action in certify_actions
	certifier_role
	input.has_licence
	not denied
}

# 3. Body receive / hold / release — mortuary role, release additionally hold-free.
allow if {
	has_anchor
	input.action in body_manage_actions
	mortuary_role
	not denied
}

# 4. Medico-legal view/refer — medico-legal role (or break-glass, audited upstream).
allow if {
	has_anchor
	input.action in medico_legal_actions
	medico_legal_role
	not denied
}

allow if {
	has_anchor
	input.action in medico_legal_actions
	input.break_glass
	not denied
}

# 5. CRVS package / submit — crvs role.
allow if {
	has_anchor
	input.action in crvs_actions
	crvs_role
	not denied
}

# 6. Read death case / audit — any authorised staff (non-citizen), subject to forensic masking.
allow if {
	has_anchor
	input.identity.identity_type != "citizen"
	input.action in read_actions
	not denied
}
