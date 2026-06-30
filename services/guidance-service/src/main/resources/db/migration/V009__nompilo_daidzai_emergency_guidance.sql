-- Nompilo guidance seeds for the Daidzai Emergency & Disaster Suite domain (WS#7).
-- Reuses the existing guidance.guidance_item registry (V004). daidzai-service owns the emergency/
-- incident command truth; Nompilo (guidance-service) owns the guidance catalogue. Routes point at
-- real WS#7 surfaces under /emergency (citizen) and /work/daidzai (provider command). Each item
-- routes to a sovereign owner (daidzai / nhume / ndila / pct / khuluma / rito) — Nompilo never owns
-- the underlying transaction and never overrides clinician/responder judgement. Emergency guidance
-- is always reassuring + actionable; no payment is ever mentioned (payment never gates an emergency).

INSERT INTO guidance.guidance_item
    (guidance_key, domain, guidance_type, applies_route, applies_user_type, min_trust_loa, trigger_condition,
     title, body, cta_label, cta_route, owner_service, required_policy, khuluma_follow_up, fundo_content_ref, severity, priority, audit_required)
VALUES
    -- Citizen landing: what an emergency does --------------------------------------------
    ('DAIDZAI_EMERGENCY_WELCOME', 'daidzai', 'ORIENTATION', '/emergency', 'CITIZEN', 1, 'first_time',
     'Emergency help, fast',
     'Tap SOS to ask for emergency help for yourself or someone near you. You do not need to be signed in fully and you will never be asked to pay first. Your location and what you tell us go straight to the response team so help can reach you.',
     'Get emergency help', '/emergency/sos', 'daidzai', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 60, FALSE),

    -- Citizen SOS: what to do while help comes (the key reassurance card) ----------------
    ('DAIDZAI_SOS_WHILE_YOU_WAIT', 'daidzai', 'STATUS_EXPLANATION', '/emergency/sos', 'CITIZEN', 1, 'context_view',
     'What to do while help is on the way',
     'Stay with the person if you can and keep them calm. Do not move someone who may be seriously injured unless they are in danger. Keep your phone nearby — the response team may call you back on this number. We have acknowledged your request and you can track it from here.',
     'Track my request', '/emergency/track', 'daidzai', 'nompilo.guidance.context.view', TRUE, NULL, 'WARN', 20, FALSE),

    -- Citizen tracking: what the statuses mean -------------------------------------------
    ('DAIDZAI_TRACK_STATUS', 'daidzai', 'STATUS_EXPLANATION', '/emergency/track/[id]', 'CITIZEN', 1, 'context_view',
     'Your request status, explained',
     'Received means we have your request. Triaged means a responder has classified how urgent it is. Responding, On scene and Transporting follow the team to and from you. Hand-over means care has passed to the clinical team. The team owns the response — this view keeps you informed.',
     'View status', '/emergency/track/[id]', 'daidzai', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 45, FALSE),

    -- Citizen: finding nearest services (routed to Ndila) --------------------------------
    ('DAIDZAI_NEAREST_SERVICES', 'daidzai', 'ORIENTATION', '/emergency/services', 'CITIZEN', 1, 'context_view',
     'Find the nearest emergency services',
     'This shows nearby facilities able to help right now. Directions and nearest-service routing come from Ndila (maps). For a life-threatening emergency, raise an SOS rather than travelling — the team can come to you.',
     'Find services', '/emergency/services', 'ndila', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 50, FALSE),

    -- Sensitive emergency: locked-state reassurance (GBV / mental-health / child) ---------
    ('DAIDZAI_SENSITIVE_PROTECTED', 'daidzai', 'LOCKED_STATE', '/emergency/track/[id]', 'CITIZEN', 1, 'sensitive_incident',
     'This emergency is handled with extra care',
     'Because of the nature of this emergency, your details are protected and only shared with the people directly helping you. A trained, trusted responder is handling it. You are safe to ask for help here.',
     'Track my request', '/emergency/track/[id]', 'daidzai', 'nompilo.guidance.locked.explain', TRUE, NULL, 'WARN', 15, TRUE),

    -- Provider command: the command board orientation ------------------------------------
    ('DAIDZAI_COMMAND_WELCOME', 'daidzai', 'ORIENTATION', '/work/daidzai', 'PROVIDER', 2, 'first_time',
     'Daidzai is the emergency command board',
     'Daidzai coordinates the response — it does not replace the teams that do the work. Dispatch is executed by Nhume, routing by Ndila, clinical care and hand-over by PCT, after-action review by Rito. Here you triage requests, raise the dispatch need, track missions and run disaster command.',
     'Open command board', '/work/daidzai', 'daidzai', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 55, FALSE),

    -- Provider dispatch: Daidzai raises the need, Nhume executes -------------------------
    ('DAIDZAI_DISPATCH_HANDSHAKE', 'daidzai', 'STATUS_EXPLANATION', '/work/daidzai/dispatch', 'PROVIDER', 2, 'context_view',
     'Dispatch need vs dispatch execution',
     'Raising a dispatch here creates the response need and starts the mission timeline. Nhume executes the mission and returns the authoritative mission reference; Daidzai tracks its status. You are not double-booking a vehicle — you are asking the dispatch system to act.',
     'Open dispatch', '/work/daidzai/dispatch', 'nhume', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 40, FALSE),

    -- Provider disaster command: closure routes after-action to Rito ----------------------
    ('DAIDZAI_DISASTER_CLOSURE', 'daidzai', 'STATUS_EXPLANATION', '/work/daidzai/disasters', 'PROVIDER', 2, 'context_view',
     'Closing a disaster opens an after-action review',
     'A disaster or mass-casualty incident gathers affected sites and resource needs under one command record. When you close it, Daidzai routes an after-action review to Rito (quality & safety) so lessons are captured — Rito owns the review, Daidzai holds the link.',
     'Open disaster command', '/work/daidzai/disasters', 'rito', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 35, FALSE);
