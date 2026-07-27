-- RB-6: Nompilo guidance for the regulatory workspace picker.
--
-- A person landing on /work/regulatory with no appointment previously hit a dead end — the empty
-- state named the registrar and offered no action. RB-6 adds a real "Request regulatory access"
-- surface (/work/regulatory/request, the RB-3 bootstrap rail); this card routes people to it.
--
-- trigger_condition is left NULL deliberately: the card is route-contextual orientation on the
-- picker itself, not gated on a machine-readable condition the guidance engine may not yet evaluate
-- (a condition key the engine cannot resolve would silently suppress the card — an inert seed). The
-- picker page also renders the CTA directly, so this is supplementary Nompilo guidance, not the
-- only path.

INSERT INTO guidance.guidance_item
    (guidance_key, domain, guidance_type, applies_route, applies_user_type, min_trust_loa, trigger_condition,
     title, body, cta_label, cta_route, owner_service, required_policy, khuluma_follow_up, fundo_content_ref, severity, priority, audit_required)
VALUES
    ('REQUEST_REGULATORY_ACCESS', 'onboarding', 'LOCKED_STATE', '/work/regulatory', NULL, NULL, NULL,
     'How you get access to a regulator workspace',
     'Regulator workspace access follows an appointment: an administrator invites you into a role, or — if a regulator has no administrator yet — you request to found its administration. Founding requests are verified against authoritative records and approved by two separate people.',
     'Request regulatory access', '/work/regulatory/request', 'organization-registry', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 40, FALSE)
ON CONFLICT (tenant_id, guidance_key) DO NOTHING;
