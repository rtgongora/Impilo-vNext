-- Nompilo guidance seeds for the Simba wellness domain (WS#3 — Simba Wellness Depth).
-- Reuses the existing guidance.guidance_item registry (V004); Simba owns the wellness data,
-- Nompilo (guidance-service) owns the guidance catalogue. Routes point at real Simba surfaces.

INSERT INTO guidance.guidance_item
    (guidance_key, domain, guidance_type, applies_route, applies_user_type, min_trust_loa, trigger_condition,
     title, body, cta_label, cta_route, owner_service, required_policy, khuluma_follow_up, fundo_content_ref, severity, priority, audit_required)
VALUES
    -- Wellness home: first-time setup ---------------------------------------------------
    ('SIMBA_SETUP', 'simba', 'ORIENTATION', '/wellness', 'CITIZEN', NULL, 'first_time',
     'Set up your wellness companion',
     'Simba is your MY LIFE wellness companion — goals, journeys, habits, and gentle nudges to stay well before you ever need care. Choose what matters to you to begin.',
     'Set up wellness', '/wellness/profile', 'simba', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 60, FALSE),

    -- Journeys: pick a journey ----------------------------------------------------------
    ('SIMBA_PICK_JOURNEY', 'simba', 'NEXT_BEST_ACTION', '/wellness', 'CITIZEN', NULL, 'no_active_journey',
     'Start a wellness journey',
     'A structured journey makes a new habit stick. Try a 30-day walking journey or a diabetes-prevention plan — enrol and follow the steps at your own pace.',
     'Browse journeys', '/wellness/plans', 'simba', 'nompilo.guidance.context.view', FALSE, 'FUNDO-WELLNESS-ACTIVITY-101', 'RECOMMEND', 40, FALSE),

    -- Journeys page: clinical-caution explainer ----------------------------------------
    ('SIMBA_JOURNEY_CAUTION', 'simba', 'LOCKED_STATE', '/wellness/plans', 'CITIZEN', NULL, 'clinical_caution',
     'Some journeys touch a health risk',
     'Journeys marked with a caution touch on a health risk. Enrolling is fine — Simba will also suggest a screening or a chat with a provider. Wellness guidance never replaces clinical advice.',
     'Connect to care', '/wellness/care', 'simba', 'nompilo.guidance.locked.explain', FALSE, NULL, 'WARN', 30, TRUE),

    -- Habit recovery --------------------------------------------------------------------
    ('SIMBA_HABIT_RECOVERY', 'simba', 'NEXT_BEST_ACTION', '/wellness/coaching', 'CITIZEN', NULL, 'habit_missed',
     'Missed a day? Start fresh',
     'Streaks break — that is normal. Check in today and Simba can remind you tomorrow. Small, consistent wins matter more than perfect days.',
     'Check in today', '/wellness/coaching', 'simba', 'nompilo.guidance.context.view', TRUE, NULL, 'INFO', 50, FALSE),

    -- Wellness-to-care routing ----------------------------------------------------------
    ('SIMBA_RED_FLAG', 'simba', 'ROUTING', '/wellness/care', 'CITIZEN', NULL, 'red_flag',
     'A worrying sign should be seen by care',
     'Wellness is not a diagnosis. If something feels wrong, route it to the right care service now. For an emergency, use emergency services immediately.',
     'Connect to care', '/wellness/care', 'pct', 'nompilo.guidance.context.view', TRUE, NULL, 'CRITICAL', 10, TRUE),

    -- Learn: Fundo content link ---------------------------------------------------------
    ('SIMBA_LEARN', 'simba', 'PROTOCOL', '/wellness', 'CITIZEN', NULL, 'always',
     'Learn about staying well',
     'Short, practical lessons on activity, nutrition, sleep and more. Wellness education is owned by Impilo Fundo — Simba points you to it.',
     'Open Fundo', '/learning/catalog', 'fundo', 'nompilo.guidance.fundo.view', FALSE, 'FUNDO-WELLNESS-ACTIVITY-101', 'RECOMMEND', 70, FALSE)
ON CONFLICT (tenant_id, guidance_key) DO NOTHING;
