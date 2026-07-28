-- FCV-W6 — Nompilo guidance for the facility claim and completion journey.
--
-- Route-contextual and trigger_condition NULL on purpose: a card gated on a condition key the
-- engine cannot resolve is a card nobody ever sees, which is how guidance seeds quietly become
-- decoration. Every cta_route below is a route that exists.

INSERT INTO guidance.guidance_item
    (guidance_key, domain, guidance_type, applies_route, applies_user_type, min_trust_loa, trigger_condition,
     title, body, cta_label, cta_route, owner_service, required_policy, khuluma_follow_up, fundo_content_ref, severity, priority, audit_required)
VALUES
    ('FACILITY_CLAIM_EXPLAIN', 'facility', 'ORIENTATION', '/facility/claim', NULL, NULL, NULL,
     'What claiming a facility does — and does not do',
     'Claiming a facility lets you maintain its record: its address, contacts, services and leadership. It does not make the facility operational on the platform. Whether a facility may operate is a separate Ministry decision, made by an authorised verifier after the profile is complete.',
     'Find your facility', '/facility/claim', 'tuso', 'nompilo.guidance.facility.view', FALSE, NULL, 'INFO', 20, FALSE),

    ('FACILITY_REGULATED_ROLE', 'facility', 'STATUS_EXPLANATION', '/facility/claim', NULL, NULL, NULL,
     'Some roles need your professional registration',
     'If you are claiming a regulated clinical responsibility — practitioner in charge, responsible pharmacist, nursing or laboratory lead — you will be asked for the registration that proves it. Administrative roles such as records officer, ICT focal person or data steward do not need one.',
     NULL, NULL, 'varapi', 'nompilo.guidance.facility.view', FALSE, NULL, 'INFO', 25, FALSE),

    ('FACILITY_PROFILE_DRAFT', 'facility', 'NEXT_BEST_ACTION', '/facility', 'FACILITY_ADMIN', NULL, NULL,
     'Your changes are a draft until a steward accepts them',
     'What you enter is saved privately and does not change the facility record yet. Submit it when you are ready and a steward will review it — they may accept it or ask you to correct something.',
     'Open facility setup', '/facility/setup', 'tuso', 'nompilo.guidance.facility.view', FALSE, NULL, 'INFO', 30, FALSE)

ON CONFLICT (tenant_id, guidance_key) DO NOTHING;
